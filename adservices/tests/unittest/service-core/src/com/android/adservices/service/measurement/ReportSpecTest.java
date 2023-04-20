/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.adservices.service.measurement;

import static com.android.adservices.service.measurement.Source.ONE_HOUR_IN_MILLIS;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.util.Pair;

import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ReportSpecTest {

    public static final long BASE_TIME = System.currentTimeMillis();
    private static final String PRIVACY_PARAMETERS_JSON = "{\"flip_probability\" :0.0024}";

    @Test
    public void reportSpecsConstructor_testEqualsPass() throws JSONException {
        // Assertion
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        JSONArray triggerSpecsJson = new JSONArray(new JSONObject[] {triggerSpecJson});

        assertEquals(
                new ReportSpec(triggerSpecsJson, 3, false),
                new ReportSpec(triggerSpecsJson, 3, false));
    }

    @Test
    public void reportSpecsConstructor_maxBucketIncrementsDifferent_equalFail()
            throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        JSONArray json1 = new JSONArray(new JSONObject[] {triggerSpecJson});
        JSONArray json2 = new JSONArray(new JSONObject[] {triggerSpecJson});

        // Assertion
        assertNotEquals(new ReportSpec(json1, 3, false), new ReportSpec(json2, 4, false));
    }

    @Test
    public void reportSpecsConstructor_TriggerSpecCountDifferent_equalFail() throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        JSONArray json1 = new JSONArray(new JSONObject[] {triggerSpecJson});
        JSONArray json2 = new JSONArray(new JSONObject[] {triggerSpecJson, triggerSpecJson});

        // Assertion
        assertNotEquals(new ReportSpec(json1, 3, false), new ReportSpec(json2, 3, false));
    }

    @Test
    public void reportSpecsConstructor_TriggerSpecContentDifferent_equalFail()
            throws JSONException {
        JSONObject jsonTriggerSpec1 = new JSONObject();
        jsonTriggerSpec1.put("trigger_data", new JSONArray(new int[] {1, 2, 3, 4}));
        JSONObject windows1 = new JSONObject();
        windows1.put("start_time", 0);
        windows1.put("end_times", new JSONArray(new int[] {10000, 20000, 30000, 40000}));
        jsonTriggerSpec1.put("event_report_windows", windows1);
        jsonTriggerSpec1.put("summary_buckets", new JSONArray(new int[] {1, 10, 100}));

        JSONObject jsonTriggerSpec2 = new JSONObject();
        jsonTriggerSpec2.put("trigger_data", new JSONArray(new int[] {1, 2, 3}));
        JSONObject windows2 = new JSONObject();
        windows2.put("start_time", 0);
        windows2.put("end_times", new JSONArray(new int[] {10000, 20000, 30000, 40000}));
        jsonTriggerSpec2.put("event_report_windows", windows2);
        jsonTriggerSpec2.put("summary_buckets", new JSONArray(new int[] {1, 10, 100}));

        // Assertion
        assertNotEquals(
                new ReportSpec(new JSONArray(new JSONObject[] {jsonTriggerSpec1}), 3, false),
                new ReportSpec(new JSONArray(new JSONObject[] {jsonTriggerSpec2}), 3, false));
    }

    @Test
    public void reportSpecsConstructor_completeExpectation_success() throws JSONException {
        JSONObject jsonTriggerSpec = new JSONObject();
        jsonTriggerSpec.put("trigger_data", new JSONArray(new int[] {1, 2, 3, 4}));
        JSONObject windows = new JSONObject();
        windows.put("start_time", 0);
        windows.put("end_times", new JSONArray(new int[] {10000, 20000, 30000, 40000}));
        jsonTriggerSpec.put("event_report_windows", windows);
        jsonTriggerSpec.put("summary_buckets", new JSONArray(new int[] {1, 10, 100}));

        JSONObject jsonTriggerSpec2 = new JSONObject();
        jsonTriggerSpec2.put("trigger_data", new JSONArray(new int[] {5, 6, 7, 8}));
        JSONObject windows2 = new JSONObject();
        windows2.put("start_time", 0);
        windows2.put("end_times", new JSONArray(new int[] {10000, 30000}));
        jsonTriggerSpec2.put("event_report_windows", windows2);
        jsonTriggerSpec2.put("summary_buckets", new JSONArray(new int[] {1, 2, 3}));

        ReportSpec testObject =
                new ReportSpec(
                        new JSONArray(new JSONObject[] {jsonTriggerSpec, jsonTriggerSpec2}),
                        3,
                        false);

        // Assertion
        assertEquals(3, testObject.getMaxReports());
        assertEquals(2, testObject.getTriggerSpecs().length);
        assertEquals(
                new TriggerSpec.Builder(jsonTriggerSpec).build(), testObject.getTriggerSpecs()[0]);
        assertEquals(
                new TriggerSpec.Builder(jsonTriggerSpec2).build(), testObject.getTriggerSpecs()[1]);
    }

    @Test
    public void equals_fourParamConstructor_returnsTrue() throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        JSONArray privacyParam = new JSONArray(new JSONObject[] {triggerSpecJson});
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = new JSONObject();
        triggerRecord1.put("trigger_id", "100");
        triggerRecord1.put("value", 2L);
        triggerRecord1.put("priority", 1L);
        triggerRecord1.put("trigger_time", BASE_TIME);
        triggerRecord1.put("trigger_data", new UnsignedLong(1L).getValue());
        triggerRecord1.put("dedup_key", new UnsignedLong(34567L).getValue());

        JSONObject triggerRecord2 = new JSONObject();
        triggerRecord2.put("trigger_id", "200");
        triggerRecord2.put("value", 3L);
        triggerRecord2.put("priority", 4L);
        triggerRecord2.put("trigger_time", BASE_TIME + 100);
        triggerRecord2.put("trigger_data", new UnsignedLong(1L).getValue());
        triggerRecord2.put("dedup_key", new UnsignedLong(45678L).getValue());
        existingAttributes.put(triggerRecord1);
        existingAttributes.put(triggerRecord2);
        // Assertion
        assertEquals(
                new ReportSpec(
                        privacyParam.toString(),
                        "3",
                        existingAttributes.toString(),
                        PRIVACY_PARAMETERS_JSON),
                new ReportSpec(
                        privacyParam.toString(),
                        "3",
                        existingAttributes.toString(),
                        PRIVACY_PARAMETERS_JSON));
    }

    @Test
    public void equals_attributionStatusEmptyOrNull_returnsTrue() throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        JSONArray privacyParam = new JSONArray(new JSONObject[] {triggerSpecJson});

        // Assertion
        assertEquals(
                new ReportSpec(privacyParam.toString(), "3", null, PRIVACY_PARAMETERS_JSON),
                new ReportSpec(privacyParam.toString(), "3", "", PRIVACY_PARAMETERS_JSON));
    }

    @Test
    public void equals_fourParamConstructorFromRawJSON_returnsTrue() throws JSONException {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}]";
        // Assertion
        assertEquals(
                new ReportSpec(triggerSpecsString, "3", null, PRIVACY_PARAMETERS_JSON),
                new ReportSpec(triggerSpecsString, "3", "", PRIVACY_PARAMETERS_JSON));
    }

    @Test
    public void equals_twoParamConstructorFromRawJSON_returnsTrue() throws JSONException {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}]";
        // Assertion
        assertEquals(
                new ReportSpec(triggerSpecsString, "3"), new ReportSpec(triggerSpecsString, "3"));
    }

    @Test
    public void equals_twoParamConstructorFromRawJSONInvalidArguments_throws() {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}]";
        // Assertion
        assertThrows(NumberFormatException.class, () -> new ReportSpec(triggerSpecsString, "a"));
    }

    @Test
    public void equals_twoParamConstructorFromRawJSONInvalidJSON_throws() {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, b],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}]";
        // Assertion
        assertThrows(NumberFormatException.class, () -> new ReportSpec(triggerSpecsString, "3"));
    }

    @Test
    public void equals_fourParamConstructor_differentAttributions_returnsFalse()
            throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        JSONArray privacyParam = new JSONArray(new JSONObject[] {triggerSpecJson});
        JSONArray existingAttributes = new JSONArray();
        JSONArray existingAttributes2 = new JSONArray();

        JSONObject triggerRecord1 = new JSONObject();
        triggerRecord1.put("trigger_id", "100");
        triggerRecord1.put("value", 2L);
        triggerRecord1.put("priority", 1L);
        triggerRecord1.put("trigger_time", BASE_TIME);
        triggerRecord1.put("trigger_data", new UnsignedLong(1L).getValue());
        triggerRecord1.put("dedup_key", new UnsignedLong(34567L).getValue());

        JSONObject triggerRecord2 = new JSONObject();
        triggerRecord2.put("trigger_id", "200");
        triggerRecord2.put("value", 3L);
        triggerRecord2.put("priority", 4L);
        triggerRecord2.put("trigger_time", BASE_TIME + 100);
        triggerRecord2.put("trigger_data", new UnsignedLong(1L).getValue());
        triggerRecord2.put("dedup_key", new UnsignedLong(45678L).getValue());
        existingAttributes.put(triggerRecord1);
        existingAttributes2.put(triggerRecord2);

        // Assertion
        assertNotEquals(
                new ReportSpec(
                        privacyParam.toString(),
                        "3",
                        existingAttributes.toString(),
                        PRIVACY_PARAMETERS_JSON),
                new ReportSpec(
                        privacyParam.toString(),
                        "3",
                        existingAttributes2.toString(),
                        PRIVACY_PARAMETERS_JSON));
    }

    @Test
    public void encodeTriggerSpecsToJSON_equal() throws JSONException {
        JSONObject jsonTriggerSpec = new JSONObject();
        jsonTriggerSpec.put("trigger_data", new JSONArray(new int[] {1, 2, 3, 4}));
        JSONObject windows = new JSONObject();
        windows.put("start_time", 0);
        windows.put("end_times", new JSONArray(new int[] {10000, 20000, 30000, 40000}));
        jsonTriggerSpec.put("event_report_windows", windows);
        jsonTriggerSpec.put("summary_buckets", new JSONArray(new int[] {1, 10, 100}));

        ReportSpec testObject1 =
                new ReportSpec(
                        new JSONArray(new JSONObject[] {jsonTriggerSpec, jsonTriggerSpec}),
                        3,
                        false);

        String encodedJSON = testObject1.encodeTriggerSpecsToJSON();

        ReportSpec testObject2 = new ReportSpec(new JSONArray(encodedJSON), 3, false);
        // Assertion
        assertEquals(testObject1, testObject2);
    }

    @Test
    public void encodeTriggerStatusToJSON_equal() throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        JSONArray privacyParam = new JSONArray(new JSONObject[] {triggerSpecJson});
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = new JSONObject();
        triggerRecord1.put("trigger_id", "100");
        triggerRecord1.put("value", 2L);
        triggerRecord1.put("priority", 1L);
        triggerRecord1.put("trigger_time", BASE_TIME);
        triggerRecord1.put("trigger_data", new UnsignedLong(1L).getValue());
        triggerRecord1.put("dedup_key", new UnsignedLong(34567L).getValue());

        JSONObject triggerRecord2 = new JSONObject();
        triggerRecord2.put("trigger_id", "200");
        triggerRecord2.put("value", 3L);
        triggerRecord2.put("priority", 4L);
        triggerRecord2.put("trigger_time", BASE_TIME + 100);
        triggerRecord2.put("trigger_data", new UnsignedLong(1L).getValue());
        triggerRecord2.put("dedup_key", new UnsignedLong(45678L).getValue());
        existingAttributes.put(triggerRecord1);
        existingAttributes.put(triggerRecord2);

        ReportSpec raw =
                new ReportSpec(
                        privacyParam.toString(),
                        "3",
                        existingAttributes.toString(),
                        PRIVACY_PARAMETERS_JSON);
        JSONArray encodedTriggerStatus = raw.encodeTriggerStatusToJSON();
        ReportSpec afterDecodingEncoding =
                new ReportSpec(
                        privacyParam.toString(),
                        "3",
                        encodedTriggerStatus.toString(),
                        PRIVACY_PARAMETERS_JSON);

        // Assertion
        for (Iterator<String> it = triggerRecord1.keys(); it.hasNext(); ) {
            String key = it.next();
            assertEquals(
                    triggerRecord1.get(key).toString(),
                    encodedTriggerStatus.getJSONObject(0).get(key).toString());
            assertEquals(
                    triggerRecord2.get(key).toString(),
                    encodedTriggerStatus.getJSONObject(1).get(key).toString());
        }

        for (int i = 0; i < raw.getAttributedTriggers().size(); i++) {
            assertEquals(
                    raw.getAttributedTriggers().get(i),
                    afterDecodingEncoding.getAttributedTriggers().get(i));
        }
        assertEquals(
                raw.getAttributedTriggers().size(),
                afterDecodingEncoding.getAttributedTriggers().size());
    }

    @Test
    public void testInvalidCaseDuplicateTriggerData_throws() throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();

        // Assertion
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReportSpec(
                                new JSONArray(new JSONObject[] {triggerSpecJson, triggerSpecJson}),
                                3,
                                true));
    }

    @Test
    public void validateParameters_testInvalidCaseTotalCardinalityOverLimit_throws()
            throws JSONException {
        JSONObject jsonTriggerSpec1 = new JSONObject();
        jsonTriggerSpec1.put("trigger_data", new JSONArray(new int[] {0, 1, 2, 3, 4}));
        JSONObject windows = new JSONObject();
        windows.put("start_time", 0);
        windows.put("end_times", new JSONArray(new int[] {1}));
        jsonTriggerSpec1.put("event_report_windows", windows);
        jsonTriggerSpec1.put("summary_buckets", new JSONArray(new int[] {1}));
        JSONObject jsonTriggerSpec2 = new JSONObject();
        jsonTriggerSpec2.put("trigger_data", new JSONArray(new int[] {5, 6, 7, 8, 9}));
        jsonTriggerSpec2.put("event_report_windows", windows);
        jsonTriggerSpec2.put("summary_buckets", new JSONArray(new int[] {1}));

        // Assertion
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReportSpec(
                                new JSONArray(
                                        new JSONObject[] {jsonTriggerSpec1, jsonTriggerSpec2}),
                                3,
                                true));
    }

    @Test
    public void testInvalidCaseTotalNumberReportOverLimit_throws() throws JSONException {
        JSONObject jsonTriggerSpec1 = new JSONObject();
        jsonTriggerSpec1.put("trigger_data", new JSONArray(new int[] {0, 1, 2, 3, 4}));
        JSONObject windows = new JSONObject();
        windows.put("start_time", 0);
        windows.put("end_times", new JSONArray(new int[] {1}));
        jsonTriggerSpec1.put("event_report_windows", windows);
        jsonTriggerSpec1.put("summary_buckets", new JSONArray(new int[] {1, 2, 3, 4, 5, 6}));

        // Assertion
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReportSpec(new JSONArray(new JSONObject[] {jsonTriggerSpec1}), 21, true));
    }

    private JSONObject getTestJSONObjectTriggerSpec_4_3_2() throws JSONException {
        JSONObject jsonTriggerSpec = new JSONObject();
        jsonTriggerSpec.put("trigger_data", new JSONArray(new int[] {1, 2, 3, 4}));
        JSONObject windows = new JSONObject();
        windows.put("start_time", 0);
        windows.put("end_times", new JSONArray(new int[] {10000, 20000, 30000}));
        jsonTriggerSpec.put("event_report_windows", windows);
        jsonTriggerSpec.put("summary_buckets", new JSONArray(new int[] {1, 2}));
        return jsonTriggerSpec;
    }

    @Test
    public void getPrivacyParamsForComputation_equal() throws JSONException {
        JSONObject jsonTriggerSpec = getTestJSONObjectTriggerSpec_4_3_2();

        ReportSpec testObject1 =
                new ReportSpec(new JSONArray(new JSONObject[] {jsonTriggerSpec}), 3, true);
        // Assertion
        assertEquals(3, testObject1.getPrivacyParamsForComputation()[0][0]);
        assertArrayEquals(new int[] {3, 3, 3, 3}, testObject1.getPrivacyParamsForComputation()[1]);
        assertArrayEquals(new int[] {2, 2, 2, 2}, testObject1.getPrivacyParamsForComputation()[2]);
    }

    @Test
    public void getPrivacyParamsForComputationV2_equal() throws JSONException {
        ReportSpec testObject1 = SourceFixture.getValidReportSpec();
        // Assertion
        assertEquals(3, testObject1.getPrivacyParamsForComputation()[0][0]);
        assertArrayEquals(new int[] {2, 2}, testObject1.getPrivacyParamsForComputation()[1]);
        assertArrayEquals(new int[] {2, 2}, testObject1.getPrivacyParamsForComputation()[2]);
    }

    @Test
    public void getNumberState_equal() throws JSONException {
        JSONObject jsonTriggerSpec = getTestJSONObjectTriggerSpec_4_3_2();

        ReportSpec testObject1 =
                new ReportSpec(new JSONArray(new JSONObject[] {jsonTriggerSpec}), 3, true);
        // Assertion
        assertEquals(
                415,
                testObject1.getNumberState()); // Privacy parameter is {3, {3,3,3,3}, {2,2,2,2}}
    }

    @Test
    public void getTriggerDataValue_equal() throws JSONException {
        JSONObject jsonTriggerSpec1 = getTestJSONObjectTriggerSpec_4_3_2();

        JSONObject jsonTriggerSpec2 = new JSONObject();
        jsonTriggerSpec2.put("trigger_data", new JSONArray(new int[] {5, 6, 7}));
        JSONObject windows2 = new JSONObject();
        windows2.put("start_time", 0);
        windows2.put("end_times", new JSONArray(new int[] {15000}));
        jsonTriggerSpec2.put("event_report_windows", windows2);
        jsonTriggerSpec2.put("summary_buckets", new JSONArray(new int[] {1, 2}));

        ReportSpec testObject1 =
                new ReportSpec(
                        new JSONArray(new JSONObject[] {jsonTriggerSpec1, jsonTriggerSpec2}),
                        3,
                        true);
        // Assertion
        assertEquals(new UnsignedLong(1L), testObject1.getTriggerDataValue(0));
        assertEquals(new UnsignedLong(3L), testObject1.getTriggerDataValue(2));
        assertEquals(new UnsignedLong(5L), testObject1.getTriggerDataValue(4));
        assertEquals(new UnsignedLong(7L), testObject1.getTriggerDataValue(6));
    }

    @Test
    public void getWindowEndTime_equal() throws JSONException {
        JSONObject jsonTriggerSpec1 = getTestJSONObjectTriggerSpec_4_3_2();

        JSONObject jsonTriggerSpec2 = new JSONObject();
        jsonTriggerSpec2.put("trigger_data", new JSONArray(new int[] {5, 6, 7}));
        JSONObject windows2 = new JSONObject();
        windows2.put("start_time", 0);
        windows2.put("end_times", new JSONArray(new int[] {15000}));
        jsonTriggerSpec2.put("event_report_windows", windows2);
        jsonTriggerSpec2.put("summary_buckets", new JSONArray(new int[] {1, 2}));

        ReportSpec testObject1 =
                new ReportSpec(
                        new JSONArray(new JSONObject[] {jsonTriggerSpec1, jsonTriggerSpec2}),
                        3,
                        true);
        // Assertion
        assertEquals(10000, testObject1.getWindowEndTime(0, 0));
        assertEquals(20000, testObject1.getWindowEndTime(0, 1));
        assertEquals(10000, testObject1.getWindowEndTime(1, 0));
        assertEquals(15000, testObject1.getWindowEndTime(4, 0));
    }

    @Test
    public void processIncomingReport_higherPriority_lowerPriorityReportDeleted()
            throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        List<EventReport> existingReports = new ArrayList<>();
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(1L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(2L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport incomingReport =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        Pair<List<EventReport>, Integer> actualResult;
        actualResult =
                testReportSpec.processIncomingReport(
                        testReportSpec.countBucketIncrements(existingReport_1),
                        existingReport_1,
                        new ArrayList<>());
        assertEquals(0, actualResult.first.size());
        assertEquals(1, actualResult.second.intValue());
        existingReports.add(existingReport_1);
        actualResult =
                testReportSpec.processIncomingReport(
                        testReportSpec.countBucketIncrements(existingReport_2),
                        existingReport_2,
                        existingReports);
        assertEquals(0, actualResult.first.size());
        assertEquals(1, actualResult.second.intValue());
        existingReports.add(existingReport_2);
        actualResult =
                testReportSpec.processIncomingReport(
                        testReportSpec.countBucketIncrements(incomingReport),
                        incomingReport,
                        existingReports);
        // incoming report contains triggerData 2 and priority is 2 so report 1 with triggerData 2
        // has priority reset to 2. Also report 1 comes earlier than report 2 so report 2 is
        // deleted.
        assertEquals(
                new ArrayList<>(Collections.singletonList(existingReport_2)), actualResult.first);
        assertEquals(1, actualResult.second.intValue());
    }

    @Test
    public void processIncomingReport_highValueAndPriority_multipleReportsDeleted()
            throws JSONException {
        int[] triggerData = {1, 2, 3};
        int eventReportWindowsStart = 0;
        long[] eventReportWindowsEnd = {
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
        };
        String summaryWindowOperator = "value_sum";
        long[] summaryBucket = {10, 100};
        JSONObject triggerSpecJson =
                TriggerSpecTest.getJson(
                        triggerData,
                        eventReportWindowsStart,
                        eventReportWindowsEnd,
                        summaryWindowOperator,
                        summaryBucket);
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        List<EventReport> existingReports = new ArrayList<>();
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(1L)
                        .setTriggerValue(10L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(2L)
                        .setTriggerValue(10L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport incomingReport =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(3L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(101L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        assertEquals(
                0,
                testReportSpec
                        .processIncomingReport(
                                testReportSpec.countBucketIncrements(existingReport_1),
                                existingReport_1,
                                new ArrayList<>())
                        .first
                        .size());
        existingReports.add(existingReport_1);
        assertEquals(
                0,
                testReportSpec
                        .processIncomingReport(
                                testReportSpec.countBucketIncrements(existingReport_2),
                                existingReport_2,
                                existingReports)
                        .first
                        .size());
        existingReports.add(existingReport_2);
        Pair<List<EventReport>, Integer> actualResult =
                testReportSpec.processIncomingReport(
                        testReportSpec.countBucketIncrements(incomingReport),
                        incomingReport,
                        existingReports);
        assertEquals(
                new Pair<>(new ArrayList<>(Arrays.asList(existingReport_1, existingReport_2)), 2),
                actualResult);
    }

    @Test
    public void processIncomingReport_highValueAndPriority_lowerPriorityReportDeleted()
            throws JSONException {
        int[] triggerData = {1, 2, 3};
        int eventReportWindowsStart = 0;
        long[] eventReportWindowsEnd = {
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
        };
        String summaryWindowOperator = "value_sum";
        long[] summaryBucket = {10, 100};
        JSONObject triggerSpecJson =
                TriggerSpecTest.getJson(
                        triggerData,
                        eventReportWindowsStart,
                        eventReportWindowsEnd,
                        summaryWindowOperator,
                        summaryBucket);
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        List<EventReport> existingReports = new ArrayList<>();
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(1L)
                        .setTriggerValue(10L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport incomingReport =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(3L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(101L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        assertEquals(
                0,
                testReportSpec
                        .processIncomingReport(
                                testReportSpec.countBucketIncrements(existingReport_1),
                                existingReport_1,
                                new ArrayList<>())
                        .first
                        .size());
        existingReports.add(existingReport_1);
        assertEquals(2, testReportSpec.countBucketIncrements(incomingReport));
        Pair<List<EventReport>, Integer> actualResult =
                testReportSpec.processIncomingReport(
                        testReportSpec.countBucketIncrements(incomingReport),
                        incomingReport,
                        existingReports);
        assertEquals(
                new Pair<>(new ArrayList<>(Collections.singletonList(existingReport_1)), 2),
                actualResult);
    }

    @Test
    public void processIncomingReport_equalPriority_noReportDeleted() throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        List<EventReport> existingReports = new ArrayList<>();
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(2L)
                        .setTriggerValue(1L)
                        .setTriggerTime(BASE_TIME - 6000)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(1L)
                        .setTriggerTime(BASE_TIME - 5000)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport incomingReport =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(3L))
                        .setTriggerPriority(2L)
                        .setTriggerValue(1L)
                        .setTriggerTime(BASE_TIME - 4000)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        assertEquals(
                0,
                testReportSpec
                        .processIncomingReport(
                                testReportSpec.countBucketIncrements(existingReport_1),
                                existingReport_1,
                                new ArrayList<>())
                        .first
                        .size());
        existingReports.add(existingReport_1);
        assertEquals(
                0,
                testReportSpec
                        .processIncomingReport(
                                testReportSpec.countBucketIncrements(existingReport_2),
                                existingReport_2,
                                existingReports)
                        .first
                        .size());
        existingReports.add(existingReport_2);
        Pair<List<EventReport>, Integer> actualResult =
                testReportSpec.processIncomingReport(
                        testReportSpec.countBucketIncrements(incomingReport),
                        incomingReport,
                        existingReports);
        assertEquals(new Pair<>(new ArrayList<>(), 0), actualResult);
    }

    @Test
    public void processIncomingReport_higherPriority_reportWithLaterTriggerTimeDeleted()
            throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        List<EventReport> existingReports = new ArrayList<>();
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(1L)
                        .setTriggerValue(1L)
                        .setTriggerTime(10000L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(1L)
                        .setTriggerValue(1L)
                        .setTriggerTime(10001L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport incomingReport =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(3L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        assertEquals(
                0,
                testReportSpec
                        .processIncomingReport(
                                testReportSpec.countBucketIncrements(existingReport_1),
                                existingReport_1,
                                new ArrayList<>())
                        .first
                        .size());
        existingReports.add(existingReport_1);
        assertEquals(
                0,
                testReportSpec
                        .processIncomingReport(
                                testReportSpec.countBucketIncrements(existingReport_2),
                                existingReport_2,
                                existingReports)
                        .first
                        .size());
        existingReports.add(existingReport_2);
        Pair<List<EventReport>, Integer> actualResult =
                testReportSpec.processIncomingReport(
                        testReportSpec.countBucketIncrements(incomingReport),
                        incomingReport,
                        existingReports);
        assertEquals(
                new Pair<>(new ArrayList<>(Collections.singletonList(existingReport_2)), 1),
                actualResult);
    }

    @Test
    public void processIncomingReport_earlierReportTime_reportWithLaterTimeDeleted()
            throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        List<EventReport> existingReports = new ArrayList<>();
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(7))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(30))
                        .build();

        EventReport incomingReport =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(3L))
                        .setTriggerPriority(1L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        assertEquals(
                0,
                testReportSpec
                        .processIncomingReport(
                                testReportSpec.countBucketIncrements(existingReport_1),
                                existingReport_1,
                                new ArrayList<>())
                        .first
                        .size());
        existingReports.add(existingReport_1);
        assertEquals(
                0,
                testReportSpec
                        .processIncomingReport(
                                testReportSpec.countBucketIncrements(existingReport_2),
                                existingReport_2,
                                existingReports)
                        .first
                        .size());
        existingReports.add(existingReport_2);
        Pair<List<EventReport>, Integer> actualResult =
                testReportSpec.processIncomingReport(
                        testReportSpec.countBucketIncrements(incomingReport),
                        incomingReport,
                        existingReports);
        assertEquals(
                new Pair<>(new ArrayList<>(Collections.singletonList(existingReport_2)), 1),
                actualResult);
    }

    @Test
    public void processIncomingReport_countBasedNoBucketIncrement_noReportsDeleted()
            throws JSONException {
        int[] triggerData = {1, 2, 3};
        int eventReportWindowsStart = 0;
        long[] eventReportWindowsEnd = {
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
        };
        String summaryWindowOperator = "count";
        long[] summaryBucket = {10, 100};
        JSONObject triggerSpecJson =
                TriggerSpecTest.getJson(
                        triggerData,
                        eventReportWindowsStart,
                        eventReportWindowsEnd,
                        summaryWindowOperator,
                        summaryBucket);
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        List<EventReport> existingReports = new ArrayList<>();
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(5L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(6L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport incomingReport =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(3L))
                        .setTriggerPriority(1L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        assertEquals(
                0,
                testReportSpec
                        .processIncomingReport(
                                testReportSpec.countBucketIncrements(existingReport_1),
                                existingReport_1,
                                new ArrayList<>())
                        .first
                        .size());
        assertEquals(
                0,
                testReportSpec
                        .processIncomingReport(
                                testReportSpec.countBucketIncrements(existingReport_2),
                                existingReport_2,
                                new ArrayList<>())
                        .first
                        .size());
        existingReports.add(existingReport_2);

        // Assertion
        Pair<List<EventReport>, Integer> actualResult =
                testReportSpec.processIncomingReport(
                        testReportSpec.countBucketIncrements(incomingReport),
                        incomingReport,
                        existingReports);
        assertEquals(new Pair<>(new ArrayList<>(), 0), actualResult);
    }

    @Test
    public void processIncomingReport_earlierReportTimeLowerPriority_reportWithLaterTimeDeleted()
            throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        List<EventReport> existingReports = new ArrayList<>();
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(7))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(6L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(30))
                        .build();

        EventReport incomingReport =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(3L))
                        .setTriggerPriority(1L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // AssertionR
        assertEquals(
                0,
                testReportSpec
                        .processIncomingReport(
                                testReportSpec.countBucketIncrements(existingReport_1),
                                existingReport_1,
                                new ArrayList<>())
                        .first
                        .size());
        existingReports.add(existingReport_1);
        assertEquals(
                0,
                testReportSpec
                        .processIncomingReport(
                                testReportSpec.countBucketIncrements(existingReport_2),
                                existingReport_2,
                                existingReports)
                        .first
                        .size());
        existingReports.add(existingReport_2);
        Pair<List<EventReport>, Integer> actualResult =
                testReportSpec.processIncomingReport(
                        testReportSpec.countBucketIncrements(incomingReport),
                        incomingReport,
                        existingReports);
        assertEquals(
                new Pair<>(new ArrayList<>(Collections.singletonList(existingReport_2)), 1),
                actualResult);
    }

    @Test
    public void processIncomingReport_earlierReportLowerPriority_oneOfReportDeleted()
            throws JSONException {
        EventReport currentEventReport1 =
                new EventReport.Builder()
                        .setId("100")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(2000L)
                        .setStatus(EventReport.Status.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2) + 3600)
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(121L)
                        .setTriggerValue(101)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .build();

        ReportSpec templateReportSpec = SourceFixture.getValidReportSpecValueSum();
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = new JSONObject();
        triggerRecord1.put("trigger_id", currentEventReport1.getId());
        triggerRecord1.put("value", currentEventReport1.getTriggerValue());
        triggerRecord1.put("priority", currentEventReport1.getTriggerPriority());
        triggerRecord1.put("trigger_time", currentEventReport1.getTriggerTime());
        triggerRecord1.put("trigger_data", currentEventReport1.getTriggerData());
        triggerRecord1.put("dedup_key", currentEventReport1.getTriggerDedupKey());
        existingAttributes.put(triggerRecord1);
        ReportSpec testReportSpec =
                new ReportSpec(
                        templateReportSpec.encodeTriggerSpecsToJSON(),
                        Integer.toString(templateReportSpec.getMaxReports()),
                        existingAttributes.toString(),
                        templateReportSpec.encodePrivacyParametersToJSONString());

        EventReport incomingReport =
                EventReportFixture.getBaseEventReportBuild()
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2) + 3600)
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(123L)
                        .setTriggerValue(105L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        assertEquals(3, testReportSpec.getMaxReports());
        int incrementingBucket = testReportSpec.countBucketIncrements(incomingReport);
        assertEquals(2, incrementingBucket);
        Pair<List<EventReport>, Integer> actualResult =
                templateReportSpec.processIncomingReport(
                        incrementingBucket,
                        incomingReport,
                        new ArrayList<>(Arrays.asList(currentEventReport1, currentEventReport1)));
        assertEquals(
                new Pair<>(new ArrayList<>(Collections.singletonList(currentEventReport1)), 2),
                actualResult);
    }

    @Test
    public void insertAttributedTrigger_threeTriggerDataTypes_findsAccumulatedValues()
            throws JSONException {
        int[] triggerData = {1, 2, 3};
        int eventReportWindowsStart = 0;
        long[] eventReportWindowsEnd = {
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
        };
        String summaryWindowOperator = "value_sum";
        long[] summaryBucket = {10, 100};
        JSONObject triggerSpecJson =
                TriggerSpecTest.getJson(
                        triggerData,
                        eventReportWindowsStart,
                        eventReportWindowsEnd,
                        summaryWindowOperator,
                        summaryBucket);
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(5L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(6L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        EventReport existingReport_3 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(5L)
                        .setTriggerValue(3L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_4 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(3L))
                        .setTriggerPriority(5L)
                        .setTriggerValue(100L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertEquals(5L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));

        testReportSpec.insertAttributedTrigger(existingReport_2);
        assertEquals(5L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));
        assertEquals(6L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(2L)));

        testReportSpec.insertAttributedTrigger(existingReport_3);
        assertEquals(8L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));
        assertEquals(6L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(2L)));

        testReportSpec.insertAttributedTrigger(existingReport_4);
        assertEquals(8L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));
        assertEquals(6L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(2L)));
        assertEquals(100L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(3L)));
    }

    @Test
    public void deleteFromAttributedValue_singleEntry_deletes() throws JSONException {
        int[] triggerData = {1, 2, 3};
        int eventReportWindowsStart = 0;
        long[] eventReportWindowsEnd = {
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
        };
        String summaryWindowOperator = "value_sum";
        long[] summaryBucket = {10, 100};
        JSONObject triggerSpecJson =
                TriggerSpecTest.getJson(
                        triggerData,
                        eventReportWindowsStart,
                        eventReportWindowsEnd,
                        summaryWindowOperator,
                        summaryBucket);
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(5L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(6L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        EventReport existingReport_3 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(3L))
                        .setTriggerPriority(5L)
                        .setTriggerValue(3L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_3_dup =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(3L))
                        .setTriggerPriority(5L)
                        .setTriggerValue(3L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .setId("12345")
                        .build();

        // Assertion
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertEquals(5L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));

        testReportSpec.deleteFromAttributedValue(existingReport_1);
        assertEquals(0L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));

        testReportSpec.insertAttributedTrigger(existingReport_2);
        testReportSpec.deleteFromAttributedValue(existingReport_2);
        assertEquals(0L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(2L)));

        testReportSpec.insertAttributedTrigger(existingReport_3);
        testReportSpec.insertAttributedTrigger(existingReport_3_dup);
        assertEquals(0L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));
    }

    @Test
    public void deleteFromAttributedValue_multipleEntries_deletesForTriggerType()
            throws JSONException {
        int[] triggerData = {1, 2, 3};
        int eventReportWindowsStart = 0;
        long[] eventReportWindowsEnd = {
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
        };
        String summaryWindowOperator = "value_sum";
        long[] summaryBucket = {10, 100};
        JSONObject triggerSpecJson =
                TriggerSpecTest.getJson(
                        triggerData,
                        eventReportWindowsStart,
                        eventReportWindowsEnd,
                        summaryWindowOperator,
                        summaryBucket);
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(5L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(4L)
                        .setTriggerValue(6L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        EventReport existingReport_3 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(5L)
                        .setTriggerValue(11L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertEquals(5L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));

        testReportSpec.insertAttributedTrigger(existingReport_2);
        testReportSpec.insertAttributedTrigger(existingReport_3);
        assertEquals(16L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));

        assertTrue(testReportSpec.deleteFromAttributedValue(existingReport_1));
        assertEquals(11L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));
        assertEquals(6L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(2L)));
    }

    @Test
    public void deleteFromAttributedValue_noTriggerRecord_returnsFalse() throws JSONException {
        int[] triggerData = {1, 2, 3};
        int eventReportWindowsStart = 0;
        long[] eventReportWindowsEnd = {
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
        };
        String summaryWindowOperator = "value_sum";
        long[] summaryBucket = {10, 100};
        JSONObject triggerSpecJson =
                TriggerSpecTest.getJson(
                        triggerData,
                        eventReportWindowsStart,
                        eventReportWindowsEnd,
                        summaryWindowOperator,
                        summaryBucket);
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(5L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(4L)
                        .setTriggerValue(6L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        EventReport existingReport_3 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(5L)
                        .setTriggerValue(11L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertTrue(testReportSpec.deleteFromAttributedValue(existingReport_1));
        assertFalse(testReportSpec.deleteFromAttributedValue(existingReport_2));
        assertFalse(testReportSpec.deleteFromAttributedValue(existingReport_3));
    }

    @Test
    public void countBucketIncrements_singleOrNoIncrements_correctlyCounts() throws JSONException {
        int[] triggerData = {1, 2, 3};
        int eventReportWindowsStart = 0;
        long[] eventReportWindowsEnd = {
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
        };
        String summaryWindowOperator = "value_sum";
        long[] summaryBucket = {10, 100};
        JSONObject triggerSpecJson =
                TriggerSpecTest.getJson(
                        triggerData,
                        eventReportWindowsStart,
                        eventReportWindowsEnd,
                        summaryWindowOperator,
                        summaryBucket);
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(5L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(6L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        EventReport existingReport_3 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(5L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_4 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(5L)
                        .setTriggerValue(100L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        assertEquals(0, testReportSpec.countBucketIncrements(existingReport_1));
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertEquals(1, testReportSpec.countBucketIncrements(existingReport_2));
        testReportSpec.insertAttributedTrigger(existingReport_2);
        assertEquals(0, testReportSpec.countBucketIncrements(existingReport_3));
        testReportSpec.insertAttributedTrigger(existingReport_3);
        assertEquals(1, testReportSpec.countBucketIncrements(existingReport_4));
    }

    @Test
    public void numDecrementingBucket_valueInHighestBucket_correctlyCounts() throws JSONException {
        int[] triggerData = {1, 2, 3};
        int eventReportWindowsStart = 0;
        long[] eventReportWindowsEnd = {
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
        };
        String summaryWindowOperator = "value_sum";
        long[] summaryBucket = {10, 100};
        JSONObject triggerSpecJson =
                TriggerSpecTest.getJson(
                        triggerData,
                        eventReportWindowsStart,
                        eventReportWindowsEnd,
                        summaryWindowOperator,
                        summaryBucket);
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(5L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(6L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        EventReport existingReport_3 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(5L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_4 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(5L)
                        .setTriggerValue(100L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_5 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(5L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        assertEquals(0, testReportSpec.countBucketIncrements(existingReport_1));
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertEquals(1, testReportSpec.countBucketIncrements(existingReport_2));
        testReportSpec.insertAttributedTrigger(existingReport_2);
        assertEquals(0, testReportSpec.countBucketIncrements(existingReport_3));
        testReportSpec.insertAttributedTrigger(existingReport_3);
        assertEquals(1, testReportSpec.countBucketIncrements(existingReport_4));
        testReportSpec.insertAttributedTrigger(existingReport_5);
        assertEquals(0, testReportSpec.numDecrementingBucket(existingReport_5));
    }

    @Test
    public void numDecrementingBucket_valueInFirstBucket_correctlyCounts() throws JSONException {
        int[] triggerData = {1, 2, 3};
        int eventReportWindowsStart = 0;
        long[] eventReportWindowsEnd = {
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
        };
        String summaryWindowOperator = "value_sum";
        long[] summaryBucket = {10, 100};
        JSONObject triggerSpecJson =
                TriggerSpecTest.getJson(
                        triggerData,
                        eventReportWindowsStart,
                        eventReportWindowsEnd,
                        summaryWindowOperator,
                        summaryBucket);
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 2, true);
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(5L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(5L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        assertEquals(0, testReportSpec.countBucketIncrements(existingReport_1));
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertEquals(0, testReportSpec.numDecrementingBucket(existingReport_2));
    }

    @Test
    public void countBucketIncrements_multipleIncrements_correctlyCounts() throws JSONException {
        int[] triggerData = {1, 2, 3};
        int eventReportWindowsStart = 0;
        long[] eventReportWindowsEnd = {
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
        };
        String summaryWindowOperator = "value_sum";
        long[] summaryBucket = {2, 4, 6, 8, 10};
        JSONObject triggerSpecJson =
                TriggerSpecTest.getJson(
                        triggerData,
                        eventReportWindowsStart,
                        eventReportWindowsEnd,
                        summaryWindowOperator,
                        summaryBucket);
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 5, true);
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(1L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        EventReport existingReport_3 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(1L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_4 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(5L)
                        .setTriggerValue(5L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        assertEquals(0, testReportSpec.countBucketIncrements(existingReport_1));
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertEquals(1, testReportSpec.countBucketIncrements(existingReport_2));
        testReportSpec.insertAttributedTrigger(existingReport_2);
        assertEquals(0, testReportSpec.countBucketIncrements(existingReport_3));
        testReportSpec.insertAttributedTrigger(existingReport_3);
        assertEquals(3, testReportSpec.countBucketIncrements(existingReport_4));
    }

    @Test
    public void numDecrementingBucket_multipleDecrements_correctlyCounts() throws JSONException {
        int[] triggerData = {1, 2, 3};
        int eventReportWindowsStart = 0;
        long[] eventReportWindowsEnd = {
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
        };
        String summaryWindowOperator = "value_sum";
        long[] summaryBucket = {2, 4, 6, 8, 10};
        JSONObject triggerSpecJson =
                TriggerSpecTest.getJson(
                        triggerData,
                        eventReportWindowsStart,
                        eventReportWindowsEnd,
                        summaryWindowOperator,
                        summaryBucket);
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 5, true);
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(1L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        EventReport existingReport_3 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(1L)
                        .setTriggerValue(3L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        assertEquals(0, testReportSpec.countBucketIncrements(existingReport_1));
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertEquals(1, testReportSpec.countBucketIncrements(existingReport_2));
        testReportSpec.insertAttributedTrigger(existingReport_2);
        assertEquals(1, testReportSpec.countBucketIncrements(existingReport_3));
        testReportSpec.insertAttributedTrigger(existingReport_3);
        assertEquals(0, testReportSpec.numDecrementingBucket(existingReport_2));
        testReportSpec.deleteFromAttributedValue(existingReport_2);
        assertEquals(2, testReportSpec.numDecrementingBucket(existingReport_3));
    }

    @Test
    public void countBucketIncrements_singleTrigger_correctlyCounts() throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 3, true);
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        // Assertion
        assertEquals(1, testReportSpec.countBucketIncrements(existingReport_1));
    }

    @Test
    public void numDecrementingBucket_countBasedInsertingMultipleTriggers_correctlyCounts()
            throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 3, true);
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerId("12345")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        EventReport existingReport_2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerId("23456")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertEquals(1, testReportSpec.numDecrementingBucket(existingReport_1));
        testReportSpec.insertAttributedTrigger(existingReport_2);
        assertEquals(1, testReportSpec.numDecrementingBucket(existingReport_2));
    }

    @Test
    public void getFlexEventReportingTime_triggerTimeEarlierThanSourceTime_signalsInvalid()
            throws JSONException {
        JSONObject triggerSpecJson = TriggerSpecTest.getValidBaselineTestCase();
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {triggerSpecJson}), 3, true);
        assertEquals(
                -1, testReportSpec.getFlexEventReportingTime(10000, 9999, new UnsignedLong(1L)));
    }

    @Test
    public void getFlexEventReportingTime_triggerTimeEarlierThanReportWindowStart_signalsInvalid()
            throws JSONException {
        JSONObject jsonTriggerSpec1 = new JSONObject();
        jsonTriggerSpec1.put("trigger_data", new JSONArray(new int[] {1, 2, 3, 4}));
        JSONObject windows1 = new JSONObject();
        windows1.put("start_time", 1000);
        windows1.put("end_times", new JSONArray(new int[] {10000, 20000, 30000, 40000}));
        jsonTriggerSpec1.put("event_report_windows", windows1);
        jsonTriggerSpec1.put("summary_buckets", new JSONArray(new int[] {1, 10, 100}));
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {jsonTriggerSpec1}), 3, false);

        // Assertion
        assertEquals(
                -1, testReportSpec.getFlexEventReportingTime(10000, 10999, new UnsignedLong(1L)));
    }

    @Test
    public void getFlexEventReportingTime_variousReportWindows_calculatesCorrectly()
            throws JSONException {
        JSONObject jsonTriggerSpec = new JSONObject();
        jsonTriggerSpec.put("trigger_data", new JSONArray(new int[] {1, 2, 3, 4}));
        JSONObject windows = new JSONObject();
        windows.put("start_time", 1000);
        windows.put("end_times", new JSONArray(new int[] {10000, 20000, 30000, 40000}));
        jsonTriggerSpec.put("event_report_windows", windows);
        jsonTriggerSpec.put("summary_buckets", new JSONArray(new int[] {1, 10, 100}));
        ReportSpec testReportSpec =
                new ReportSpec(new JSONArray(new JSONObject[] {jsonTriggerSpec}), 3, false);

        // Assertion
        assertEquals(
                110000 + ONE_HOUR_IN_MILLIS,
                testReportSpec.getFlexEventReportingTime(100000, 109999, new UnsignedLong(1L)));
        assertEquals(
                120000 + ONE_HOUR_IN_MILLIS,
                testReportSpec.getFlexEventReportingTime(100000, 119999, new UnsignedLong(1L)));
        assertEquals(
                130000 + ONE_HOUR_IN_MILLIS,
                testReportSpec.getFlexEventReportingTime(100000, 129999, new UnsignedLong(1L)));
        assertEquals(
                140000 + ONE_HOUR_IN_MILLIS,
                testReportSpec.getFlexEventReportingTime(100000, 139999, new UnsignedLong(1L)));
        assertEquals(
                110000 + ONE_HOUR_IN_MILLIS,
                testReportSpec.getFlexEventReportingTime(100000, 109999, new UnsignedLong(2L)));
        assertEquals(
                120000 + ONE_HOUR_IN_MILLIS,
                testReportSpec.getFlexEventReportingTime(100000, 119999, new UnsignedLong(2L)));
        assertEquals(
                130000 + ONE_HOUR_IN_MILLIS,
                testReportSpec.getFlexEventReportingTime(100000, 129999, new UnsignedLong(3L)));
        assertEquals(
                140000 + ONE_HOUR_IN_MILLIS,
                testReportSpec.getFlexEventReportingTime(100000, 139999, new UnsignedLong(4L)));
        assertEquals(
                -1, testReportSpec.getFlexEventReportingTime(100000, 149999, new UnsignedLong(1L)));
    }

    @Test
    public void containsTriggerData_variousTriggerDataTypes_correctlyDetermines()
            throws JSONException {
        JSONObject jsonTriggerSpec1 = new JSONObject();
        jsonTriggerSpec1.put("trigger_data", new JSONArray(new int[] {1, 2, 3, 4}));
        JSONObject windows1 = new JSONObject();
        windows1.put("start_time", 1000);
        windows1.put("end_times", new JSONArray(new int[] {10000, 20000, 30000, 40000}));
        jsonTriggerSpec1.put("event_report_windows", windows1);
        jsonTriggerSpec1.put("summary_buckets", new JSONArray(new int[] {1, 10, 100}));
        JSONObject jsonTriggerSpec2 = new JSONObject();
        jsonTriggerSpec2.put("trigger_data", new JSONArray(new int[] {5, 6, 7}));
        JSONObject windows2 = new JSONObject();
        windows2.put("start_time", 0);
        windows2.put("end_times", new JSONArray(new int[] {10000, 20000, 30000, 40000}));
        jsonTriggerSpec2.put("event_report_windows", windows2);
        jsonTriggerSpec2.put("summary_buckets", new JSONArray(new int[] {1, 10, 100}));
        ReportSpec testReportSpec =
                new ReportSpec(
                        new JSONArray(new JSONObject[] {jsonTriggerSpec1, jsonTriggerSpec2}),
                        3,
                        true);

        // Assertion
        assertTrue(testReportSpec.containsTriggerData(new UnsignedLong(1L)));
        assertTrue(testReportSpec.containsTriggerData(new UnsignedLong(2L)));
        assertTrue(testReportSpec.containsTriggerData(new UnsignedLong(3L)));
        assertTrue(testReportSpec.containsTriggerData(new UnsignedLong(4L)));
        assertTrue(testReportSpec.containsTriggerData(new UnsignedLong(5L)));
        assertTrue(testReportSpec.containsTriggerData(new UnsignedLong(6L)));
        assertTrue(testReportSpec.containsTriggerData(new UnsignedLong(7L)));
        assertFalse(testReportSpec.containsTriggerData(new UnsignedLong(0L)));
        assertFalse(testReportSpec.containsTriggerData(new UnsignedLong(8L)));
    }
}
