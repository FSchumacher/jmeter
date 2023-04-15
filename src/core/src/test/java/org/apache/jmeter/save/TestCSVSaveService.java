/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jmeter.save;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.junit.JMeterTestCase;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.SampleSaveConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TestCSVSaveService extends JMeterTestCase {

    private static final String[] EMPTY_STRING_ARRAY = {};

    private void checkSplitString(String input, char delim, String[] expected) throws Exception {
        String[] out = CSVSaveService.csvSplitString(input, delim);
        checkStrings(expected, out);
    }

    private void checkStrings(String[] expected, String[] out) {
        assertEquals("Incorrect number of strings returned",expected.length, out.length);
        for(int i = 0; i < out.length; i++){
           assertEquals("Incorrect entry returned",expected[i], out[i]);
        }
    }

    // This is what JOrphanUtils.split() does
    @Test
    public void testSplitEmpty() throws Exception {
        checkSplitString("",         ',', EMPTY_STRING_ARRAY);
    }

    // These tests should agree with those for JOrphanUtils.split() as far as possible

    @Test
    public void testSplitUnquoted() throws Exception {
        checkSplitString("a",         ',', new String[]{"a"});
        checkSplitString("a,bc,d,e", ',', new String[]{"a","bc","d","e"});
        checkSplitString(",bc,d,e",  ',', new String[]{"","bc","d","e"});
        checkSplitString("a,,d,e",   ',', new String[]{"a","","d","e"});
        checkSplitString("a,bc, ,e", ',', new String[]{"a","bc"," ","e"});
        checkSplitString("a,bc,d, ", ',', new String[]{"a","bc","d"," "});
        checkSplitString("a,bc,d,",  ',', new String[]{"a","bc","d",""});
        checkSplitString("a,bc,,",   ',', new String[]{"a","bc","",""});
        checkSplitString("a,,,",     ',', new String[]{"a","","",""});
        checkSplitString("a,bc,d,\n",',', new String[]{"a","bc","d",""});

        // \u00e7 = LATIN SMALL LETTER C WITH CEDILLA
        // \u00e9 = LATIN SMALL LETTER E WITH ACUTE
        checkSplitString("a,b\u00e7,d,\u00e9", ',', new String[]{"a","b\u00e7","d","\u00e9"});
    }

    @Test
    public void testSplitQuoted() throws Exception {
        checkSplitString("a,bc,d,e",     ',', new String[]{"a","bc","d","e"});
        checkSplitString(",bc,d,e",      ',', new String[]{"","bc","d","e"});
        checkSplitString("\"\",bc,d,e",  ',', new String[]{"","bc","d","e"});
        checkSplitString("a,,d,e",       ',', new String[]{"a","","d","e"});
        checkSplitString("a,\"\",d,e",   ',', new String[]{"a","","d","e"});
        checkSplitString("a,bc, ,e",     ',', new String[]{"a","bc"," ","e"});
        checkSplitString("a,bc,\" \",e", ',', new String[]{"a","bc"," ","e"});
        checkSplitString("a,bc,d, ",     ',', new String[]{"a","bc","d"," "});
        checkSplitString("a,bc,d,\" \"", ',', new String[]{"a","bc","d"," "});
        checkSplitString("a,bc,d,",      ',', new String[]{"a","bc","d",""});
        checkSplitString("a,bc,d,\"\"",  ',', new String[]{"a","bc","d",""});
        checkSplitString("a,bc,d,\"\"\n",',', new String[]{"a","bc","d",""});
        checkSplitString("a\\nx,bc,d,\"\"\n",',', new String[]{"a\nx","bc","d",""});

        // \u00e7 = LATIN SMALL LETTER C WITH CEDILLA
        // \u00e9 = LATIN SMALL LETTER E WITH ACUTE
        checkSplitString("\"a\",\"b\u00e7\",\"d\",\"\u00e9\"", ',', new String[]{"a","b\u00e7","d","\u00e9"});
    }

    @Test
    public void testSplitBadQuote() throws Exception {
        assertThrows(IOException.class, () -> checkSplitString("a\"b", ',', EMPTY_STRING_ARRAY));
    }

    @Test
    public void testSplitMultiLine() throws Exception  {
        String line="a,,\"c\nd\",e\n,,f,g,\n\n";
        String[] out;
        BufferedReader br = new BufferedReader(new StringReader(line));
        out = CSVSaveService.csvReadFile(br, ',');
        checkStrings(new String[]{"a","","c\nd","e"}, out);
        out = CSVSaveService.csvReadFile(br, ',');
        checkStrings(new String[]{"","","f","g",""}, out);
        out = CSVSaveService.csvReadFile(br, ',');
        checkStrings(new String[]{""}, out); // Blank line
        assertEquals("Expected to be at EOF",-1,br.read());
        // Empty strings at EOF
        out = CSVSaveService.csvReadFile(br, ',');
        checkStrings(EMPTY_STRING_ARRAY, out);
        out = CSVSaveService.csvReadFile(br, ',');
        checkStrings(EMPTY_STRING_ARRAY, out);
    }

    @Test
    public void testBlankLine() throws Exception  {
        BufferedReader br = new BufferedReader(new StringReader("\n"));
        String[] out = CSVSaveService.csvReadFile(br, ',');
        checkStrings(new String[]{""}, out);
        assertEquals("Expected to be at EOF",-1,br.read());
    }

    @Test
    public void testBlankLineQuoted() throws Exception  {
        BufferedReader br = new BufferedReader(new StringReader("\"\"\n"));
        String[] out = CSVSaveService.csvReadFile(br, ',');
        checkStrings(new String[]{""}, out);
        assertEquals("Expected to be at EOF",-1,br.read());
    }

    @Test
    public void testEmptyFile() throws Exception  {
        BufferedReader br = new BufferedReader(new StringReader(""));
        String[] out = CSVSaveService.csvReadFile(br, ',');
        checkStrings(EMPTY_STRING_ARRAY, out);
        assertEquals("Expected to be at EOF",-1,br.read());
    }

    @Test
    public void testShortFile() throws Exception  {
        BufferedReader br = new BufferedReader(new StringReader("a"));
        String[] out = CSVSaveService.csvReadFile(br, ',');
        checkStrings(new String[]{"a"}, out);
        assertEquals("Expected to be at EOF",-1,br.read());
    }

    @Test
    // header text should not change unexpectedly
    // if this test fails, check whether the default was intentionally changed or not
    public void testHeader() {
        final String HDR = "timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,"
                + "failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect";
        assertEquals("Header text has changed", HDR, CSVSaveService.printableFieldNamesToString());
    }

    static Stream<Arguments> sampleData() throws MalformedURLException {
        List<Arguments> args = new ArrayList<>();
        for (String message: Arrays.asList(
                "normal message with no new line",
                "Something happened:\nmessage with new line",
                "message with comma, really",
                "message with comma,\nand a new line",
                "",
                "message with \r\nCR and NL"
                )
        ) {

            SampleResult result = new SampleResult();
            result.setSaveConfig(new SampleSaveConfiguration());
            result.setStampAndTime(1, 2);
            result.setSampleLabel("3");
            result.setResponseCode("4");
            result.setResponseMessage(message);
            result.setThreadName("6");
            result.setDataType("7");
            result.setSuccessful(true);
            result.setBytes(8L);
            result.setURL(new URL("https://jmeter.apache.org"));
            result.setSentBytes(9);
            result.setGroupThreads(10);
            result.setAllThreads(11);
            result.setLatency(12);
            result.setIdleTime(13);
            result.setConnectTime(14);
            String csvLine = String.format(
                    "1,2,3,4,%s,6,7,true,,8,9,10,11,https://jmeter.apache.org,12,13,14",
                    escapeString(message)
            );
            args.add(Arguments.of(new SampleEvent(result, ""), csvLine));
        }
        return args.stream();
    }

    private static String escapeString(String message) {
        if (StringUtils.containsAny("\"\r\n,", message)) {
            return String.format(
                    "\"%s\"",
                    message
                            .replace("\"", "\"\"")
                            .replace("\r", "\\r")
                            .replace("\n", "\\n"));
        }
        return message;
    }

    @ParameterizedTest
    @MethodSource("sampleData")
    // sample format should not change unexpectedly
    // if this test fails, check whether the default was intentionally changed or not
    public void testSample(SampleEvent event, String csvLine) throws MalformedURLException {
        assertEquals("Result text has changed", csvLine, CSVSaveService.resultToDelimitedString(event));
    }
}
