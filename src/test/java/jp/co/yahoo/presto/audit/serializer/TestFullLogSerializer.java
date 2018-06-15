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
/*
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
package jp.co.yahoo.presto.audit.serializer;

import com.facebook.presto.spi.eventlistener.QueryCompletedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import jp.co.yahoo.presto.audit.TestHelper;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;

@Test(singleThreaded = true)
public class TestFullLogSerializer
{
    private FullLogSerializer fullLogSerializer = new FullLogSerializer(Optional.empty());
    private TestHelper testHelper = new TestHelper();

    @Test
    public void testSerializeNormal() throws JsonProcessingException
    {
        SerializedLog record = fullLogSerializer.serialize(testHelper.createNormalEvent());
        assertThat(record.getQueryId())
                .matches("20170606_044544_00024_nfhe3");
        assertThat(record.getSerializedLog())
                .contains("\"queryId\":\"20170606_044544_00024_nfhe3\"")
                .contains("\"query\":\"select * from airdelays_s3_csv WHERE kw = 'presto-kw-example' limit 5\"")
                .contains("\"userAgent\":\"StatementClient 0.167\"")
                .contains("\"source\":\"presto-cli\"")
                .contains("\"createTime\":\"2017-07-15T01:00:00Z\"")
                .contains("\"executionStartTime\":\"2017-07-15T01:00:01Z\"")
                .doesNotContain("\"failureMessage\"")
                .doesNotContain("\"code\"");
    }

    @Test
    public void testSerializeFailure() throws JsonProcessingException
    {
        SerializedLog record = fullLogSerializer.serialize(testHelper.createFailureEvent());
        assertThat(record.getSerializedLog())
                .contains("\"failureInfo\"")
                .contains("\"code\":1")
                .contains("\"name\":\"SYNTAX_ERROR\"")
                .contains("\"failureMessage\":\"line 1:15: mismatched input '0' expecting ')'\"")
                .contains("\"failureType\":\"com.facebook.presto.sql.parser.ParsingException\"");
    }

    @DataProvider(name="filter-provider")
    public Object[][] testData(){
        String filter1 = "sre_system";
        String filter2 = "(sre_system|presto-cli)";
        return new Object[][]{
                // No filter
                {Optional.empty(), Optional.empty(), true},
                {Optional.empty(), Optional.of("presto-cli"), true},
                {Optional.empty(), Optional.of("sre_system"), true},
                // Filter1
                {Optional.of(filter1), Optional.empty(), true},
                {Optional.of(filter1), Optional.of("presto-cli"), true},
                {Optional.of(filter1), Optional.of("sre_system"), false},
                // Filter2
                {Optional.of(filter2), Optional.empty(), true},
                {Optional.of(filter2), Optional.of("presto-cli"), false},
                {Optional.of(filter2), Optional.of("sre_system"), false}
        };
    }

    @Test(dataProvider = "filter-provider")
    public void testShouldOutput(Optional<String> filter, Optional<String> source, boolean result) {
        FullLogSerializer serializer = new FullLogSerializer(filter);
        QueryCompletedEvent event = testHelper.createQueryWithSource(source);
        boolean shouldOutput = serializer.shouldOutput(event);
        assertEquals(shouldOutput, result);
    }
}
