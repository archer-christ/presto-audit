/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jp.co.yahoo.presto.audit;

import io.airlift.log.Logger;
import jp.co.yahoo.presto.audit.serializer.SerializedLog;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.stream.Collectors;

import static jp.co.yahoo.presto.audit.AuditLogFileWriter.WriterFactory;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Test(singleThreaded = true, threadPoolSize = 1)
public class TestAuditLogFileWriter
{
    private static final String QUERY_ID = "20170606_044544_00024_abcde";
    private void pause()
    {
        try {
            Thread.sleep(6000);
        }
        catch (Exception e) {
        }
    }

    private void initTest()
    {
        pause();
        StackTraceElement[] stackTrace = Thread.currentThread()
                .getStackTrace();
        System.out.println("===========================");
        System.out.println(stackTrace[2].getMethodName());
        System.out.flush();
    }

    private AuditLogFileWriter getNewAuditLogFileWriter(WriterFactory writerFactory, Logger logger) throws Exception
    {
        Constructor<AuditLogFileWriter> constructor = AuditLogFileWriter.class.getDeclaredConstructor(WriterFactory.class, Logger.class);
        constructor.setAccessible(true);
        AuditLogFileWriter auditLogFileWriter = constructor.newInstance(writerFactory, logger);
        auditLogFileWriter.start();
        return auditLogFileWriter;
    }

    @Test
    public void testSingleton()
    {
        AuditLogFileWriter auditLogFileWriter_1 = AuditLogFileWriter.getInstance();
        AuditLogFileWriter auditLogFileWriter_2 = AuditLogFileWriter.getInstance();
        assert(auditLogFileWriter_1==auditLogFileWriter_2);
    }

    @Test
    public void testNormalWrite()
    {
        initTest();
        final String FILE_NAME = "/tmp/file1";
        final String DATA = "{\"data\":\"value\"}";

        AuditLogFileWriter auditLogFileWriter = AuditLogFileWriter.getInstance();
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA));
    }

    @Test
    public void testWriteAutoCloseFile() throws Exception
    {
        initTest();
        final String FILE_NAME = "/tmp/file1";
        final String DATA = "{\"data\":\"value\"}";

        // Setup Spy FileWriter
        WriterFactory writerFactoryMock = mock(WriterFactory.class);
        final FileWriter[] spyFileWriter = new FileWriter[10];
        when(writerFactoryMock.getFileWriter(any(String .class))).thenAnswer(i -> {
            String filename = i.getArgument(0);
            assert(filename.equals(FILE_NAME));
            FileWriter fileWriter = new FileWriter(filename, true);
            spyFileWriter[0] = spy(fileWriter);
            doAnswer((Answer<String>) var1 -> {
                System.out.println("WRITING: " + filename + " -- " + var1.getArgument(0).toString().replaceAll("\n", "\\\\n"));
                return "";
            }).when(spyFileWriter[0]).write(anyString());
            return spyFileWriter[0];
        });

        // Test write
        AuditLogFileWriter auditLogFileWriter = getNewAuditLogFileWriter(writerFactoryMock, Logger.get("testWriteAutoCloseFile"));
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA));

        // Verify
        pause();
        verify(spyFileWriter[0]).write(DATA);
        verify(spyFileWriter[0]).close();
    }

    @Test
    public void testWriteCloseFileException() throws Exception
    {
        initTest();
        final String FILE_NAME = "/tmp/file1";
        final String DATA = "{\"data\":\"value\"}";

        // Setup Spy FileWriter
        WriterFactory writerFactoryMock = mock(WriterFactory.class);
        final FileWriter[] spyFileWriter = new FileWriter[10];
        when(writerFactoryMock.getFileWriter(any(String .class))).thenAnswer(i -> {
            String filename = i.getArgument(0);
            assert(filename.equals(FILE_NAME));
            FileWriter fileWriter = new FileWriter(filename, true);
            spyFileWriter[0] = spy(fileWriter);
            doAnswer((Answer<String>) var1 -> {
                System.out.println("WRITING: " + filename + " -- " + var1.getArgument(0).toString().replaceAll("\n", "\\\\n"));
                return "";
            }).when(spyFileWriter[0]).write(anyString());
            doThrow(new IOException("Mock close file exception")).when(spyFileWriter[0]).close();
            return spyFileWriter[0];
        });

        // Test write
        Logger logger = spy(Logger.get("testWriteCloseFileException"));
        AuditLogFileWriter auditLogFileWriter = getNewAuditLogFileWriter(writerFactoryMock, logger);
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA));

        // Verify writer
        pause();
        verify(spyFileWriter[0]).write(DATA);

        // Verify logger
        verify(logger, atLeastOnce()).error(anyString());
    }

    @Test
    public void testOpenFileException() throws Exception
    {
//        initTest();
        final String FILE_NAME = "/tmp/file1";
        final String DATA = "{\"data\":\"value\"}";

        // Setup Spy FileWriter
        WriterFactory writerFactoryMock = mock(WriterFactory.class);
        when(writerFactoryMock.getFileWriter(any(String .class))).thenAnswer(i -> {
            throw new IOException("Mock open file error exception");
        });

        // Test write
        Logger logger = spy(Logger.get("testOpenFileException"));
        AuditLogFileWriter auditLogFileWriter = getNewAuditLogFileWriter(writerFactoryMock, logger);
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA));

        pause();
        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(logger, atLeastOnce()).error(argument.capture());
        List<String> values = argument.getAllValues();
        assert(values.stream().filter(k -> k.contains(QUERY_ID))
                .collect(Collectors.toList()).size() > 0);
    }

    @Test
    public void testWriteException() throws Exception
    {
        initTest();
        final String FILE_NAME = "/tmp/file1";
        final String DATA = "{\"data\":\"value\"}";

        // Setup Spy FileWriter
        WriterFactory writerFactoryMock = mock(WriterFactory.class);
        final FileWriter[] spyFileWriter = new FileWriter[10];
        when(writerFactoryMock.getFileWriter(any(String .class))).thenAnswer(i -> {
            String filename = i.getArgument(0);
            assert(filename.equals(FILE_NAME));
            FileWriter fileWriter = new FileWriter(filename, true);
            spyFileWriter[0] = spy(fileWriter);
            doThrow(new IOException("Mock write file exception")).when(spyFileWriter[0]).write(anyString());
            return spyFileWriter[0];
        });

        // Test write
        Logger logger = spy(Logger.get("testWriteException"));
        AuditLogFileWriter auditLogFileWriter = getNewAuditLogFileWriter(writerFactoryMock, logger);
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA));

        pause();
        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(logger, atLeastOnce()).error(argument.capture());
        List<String> values = argument.getAllValues();
        assert(values.stream().filter(k -> k.contains(QUERY_ID))
                .collect(Collectors.toList()).size() > 0);
    }

    @Test
    public void testFullCapacityWrite() throws Exception
    {
        initTest();
        WriterFactory writerFactoryMock = mock(WriterFactory.class);
        final FileWriter[] spyFileWriter = new FileWriter[10];
        when(writerFactoryMock.getFileWriter(any(String .class))).thenAnswer(i -> {
            String filename = i.getArgument(0);
            FileWriter fileWriter = new FileWriter(filename, true);
            spyFileWriter[0] = spy(fileWriter);
            return spyFileWriter[0];
        });

        // Should log Queue full error
        Logger logger = spy(Logger.get("testFullCapacityWrite"));
        AuditLogFileWriter auditLogFileWriter = getNewAuditLogFileWriter(writerFactoryMock, logger);
        auditLogFileWriter.stop();
        for(int i=0; i<10005; i++) {
            auditLogFileWriter.write("/tmp/file1", new SerializedLog(QUERY_ID, "data1"));
        }
    }

    @Test
    public void testMultipleWriteThenClose() throws Exception
    {
        initTest();
        final String FILE_NAME = "/tmp/file1";
        final String DATA = "{\"data\":\"value\"}";

        // Setup Spy FileWriter
        WriterFactory writerFactoryMock = mock(WriterFactory.class);
        final FileWriter[] spyFileWriter = new FileWriter[10];
        when(writerFactoryMock.getFileWriter(any(String .class))).thenAnswer(i -> {
            String filename = i.getArgument(0);
            assert(filename.equals(FILE_NAME));
            FileWriter fileWriter = new FileWriter(filename, true);
            spyFileWriter[0] = spy(fileWriter);
            doAnswer((Answer<String>) var1 -> {
                System.out.println("WRITING: " + filename + " -- " + var1.getArgument(0).toString().replaceAll("\n", "\\\\n"));
                return "";
            }).when(spyFileWriter[0]).write(anyString());
            return spyFileWriter[0];
        });

        // Test write
        AuditLogFileWriter auditLogFileWriter = getNewAuditLogFileWriter(writerFactoryMock, Logger.get("testMultipleWriteThenClose"));
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA));
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA));
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA));

        // Verify
        pause();
        verify(spyFileWriter[0], times(3)).write(DATA);
        verify(spyFileWriter[0], times(1)).close();
    }

    @Test
    public void testTimeoutReopenFile() throws Exception
    {
        initTest();
        final String FILE_NAME = "/tmp/file1";
        final String DATA = "{\"data\":\"value\"}";
        final String DATA2 = "{\"data2\":\"value\"}";

        // Setup Spy FileWriter
        WriterFactory writerFactoryMock = mock(WriterFactory.class);
        final FileWriter[] spyFileWriter = new FileWriter[10];
        when(writerFactoryMock.getFileWriter(any(String .class))).thenAnswer(i -> {
            String filename = i.getArgument(0);
            assert(filename.equals(FILE_NAME));
            FileWriter fileWriter = new FileWriter(filename, true);
            spyFileWriter[0] = spy(fileWriter);
            doAnswer((Answer<String>) var1 -> {
                System.out.println("WRITING: " + filename + " -- " + var1.getArgument(0).toString().replaceAll("\n", "\\\\n"));
                return "";
            }).when(spyFileWriter[0]).write(anyString());
            return spyFileWriter[0];
        });

        // Test write
        AuditLogFileWriter auditLogFileWriter = getNewAuditLogFileWriter(writerFactoryMock, Logger.get("testTimeoutReopenFile"));
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA));
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA));
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA));

        // Verify
        pause();
        verify(spyFileWriter[0], times(3)).write(DATA);
        verify(spyFileWriter[0], times(1)).close();

        // Write again after timeout
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA2));
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA2));
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA2));
        verify(spyFileWriter[0], times(3)).write(DATA);
        verify(spyFileWriter[0], times(1)).close();
    }

    @Test
    public void testMultiFileMultiWriteThenClose() throws Exception
    {
        initTest();
        final String FILE_NAME = "/tmp/file1";
        final String DATA_A1 = "{\"dataA1\":\"value\"}";
        final String DATA_A2 = "{\"dataA2\":\"value\"}";

        final String FILE_NAME_2 = "/tmp/file2";
        final String DATA_B1 = "{\"data2B1\":\"value2\"}";
        final String DATA_B2 = "{\"data2B2\":\"value2\"}";

        // Setup Spy FileWriter
        WriterFactory writerFactoryMock = mock(WriterFactory.class);
        final FileWriter[] spyFileWriter = new FileWriter[10];
        when(writerFactoryMock.getFileWriter(eq(FILE_NAME))).thenAnswer(i -> {
            String filename = i.getArgument(0);
            assert(filename.equals(FILE_NAME));
            FileWriter fileWriter = new FileWriter(filename, true);
            spyFileWriter[0] = spy(fileWriter);
            doAnswer((Answer<String>) var1 -> {
                System.out.println("WRITING: " + filename + " -- " + var1.getArgument(0).toString().replaceAll("\n", "\\\\n"));
                return "";
            }).when(spyFileWriter[0]).write(anyString());
            return spyFileWriter[0];
        });
        when(writerFactoryMock.getFileWriter(eq(FILE_NAME_2))).thenAnswer(i -> {
            String filename = i.getArgument(0);
            assert(filename.equals(FILE_NAME_2));
            FileWriter fileWriter = new FileWriter(filename, true);
            spyFileWriter[1] = spy(fileWriter);
            doAnswer((Answer<String>) var1 -> {
                System.out.println("WRITING: " + filename + " -- " + var1.getArgument(0).toString().replaceAll("\n", "\\\\n"));
                return "";
            }).when(spyFileWriter[1]).write(anyString());
            return spyFileWriter[1];
        });

        // Test write
        AuditLogFileWriter auditLogFileWriter = getNewAuditLogFileWriter(writerFactoryMock, Logger.get("testMultiFileMultiWriteThenClose"));
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA_A1));
        auditLogFileWriter.write(FILE_NAME, new SerializedLog(QUERY_ID, DATA_A2));
        auditLogFileWriter.write(FILE_NAME_2, new SerializedLog(QUERY_ID, DATA_B1));
        auditLogFileWriter.write(FILE_NAME_2, new SerializedLog(QUERY_ID, DATA_B2));

        // Verify
        pause();
        verify(spyFileWriter[0], times(1)).write(DATA_A1);
        verify(spyFileWriter[0], times(1)).write(DATA_A2);
        verify(spyFileWriter[0], times(1)).close();
        verify(spyFileWriter[1], times(1)).write(DATA_B1);
        verify(spyFileWriter[1], times(1)).write(DATA_B2);
        verify(spyFileWriter[1], times(1)).close();
    }
}
