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

import static com.android.adservices.service.Flags.MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ReportSpecUtilTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final long BASE_TIME = System.currentTimeMillis();
    private MockitoSession mStaticMockSession;
    @Mock Flags mFlags;

    @Mock IMeasurementDao mMeasurementDao;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .startMocking();
        ExtendedMockito.doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
    }

    @After
    public void cleanup() throws InterruptedException {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void processIncomingReport_higherPriority_lowerPriorityReportDeleted()
            throws JSONException {
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec = new ReportSpec(
                SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), "2", source);
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
                ReportSpecUtil.processIncomingReport(
                        testReportSpec,
                        ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_1),
                        existingReport_1,
                        new ArrayList<>());
        assertEquals(0, actualResult.first.size());
        assertEquals(1, actualResult.second.intValue());
        existingReports.add(existingReport_1);
        actualResult =
                ReportSpecUtil.processIncomingReport(
                        testReportSpec,
                        ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_2),
                        existingReport_2,
                        existingReports);
        assertEquals(0, actualResult.first.size());
        assertEquals(1, actualResult.second.intValue());
        existingReports.add(existingReport_2);
        actualResult =
                ReportSpecUtil.processIncomingReport(
                        testReportSpec,
                        ReportSpecUtil.countBucketIncrements(testReportSpec, incomingReport),
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
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7))
                        + "\"summary_window_operator\": \"value_sum\", "
                        + "\"summary_buckets\": [10, 100]}]";
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec = new ReportSpec(triggerSpecsString, "2", source);
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
                ReportSpecUtil.processIncomingReport(
                                testReportSpec,
                                ReportSpecUtil.countBucketIncrements(
                                        testReportSpec, existingReport_1),
                                existingReport_1,
                                new ArrayList<>())
                        .first
                        .size());
        existingReports.add(existingReport_1);
        assertEquals(
                0,
                ReportSpecUtil.processIncomingReport(
                                testReportSpec,
                                ReportSpecUtil.countBucketIncrements(
                                        testReportSpec, existingReport_2),
                                existingReport_2,
                                existingReports)
                        .first
                        .size());
        existingReports.add(existingReport_2);
        Pair<List<EventReport>, Integer> actualResult =
                ReportSpecUtil.processIncomingReport(
                        testReportSpec,
                        ReportSpecUtil.countBucketIncrements(testReportSpec, incomingReport),
                        incomingReport,
                        existingReports);
        assertEquals(new Pair<>(List.of(existingReport_1, existingReport_2), 2), actualResult);
    }

    @Test
    public void processIncomingReport_highValueAndPriority_lowerPriorityReportDeleted()
            throws JSONException {
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec = new ReportSpec(
                SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(), "2", source);
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
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(101L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();

        // Assertion
        assertEquals(
                0,
                ReportSpecUtil.processIncomingReport(
                                testReportSpec,
                                ReportSpecUtil.countBucketIncrements(
                                        testReportSpec, existingReport_1),
                                existingReport_1,
                                new ArrayList<>())
                        .first
                        .size());
        existingReports.add(existingReport_1);
        assertEquals(2, ReportSpecUtil.countBucketIncrements(testReportSpec, incomingReport));
        Pair<List<EventReport>, Integer> actualResult =
                ReportSpecUtil.processIncomingReport(
                        testReportSpec,
                        ReportSpecUtil.countBucketIncrements(testReportSpec, incomingReport),
                        incomingReport,
                        existingReports);
        assertEquals(
                new Pair<>(new ArrayList<>(Collections.singletonList(existingReport_1)), 2),
                actualResult);
    }

    @Test
    public void processIncomingReport_equalPriority_noReportDeleted() throws JSONException {
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec = new ReportSpec(
                SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), "2", source);
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
                ReportSpecUtil.processIncomingReport(
                                testReportSpec,
                                ReportSpecUtil.countBucketIncrements(
                                        testReportSpec, existingReport_1),
                                existingReport_1,
                                new ArrayList<>())
                        .first
                        .size());
        existingReports.add(existingReport_1);
        assertEquals(
                0,
                ReportSpecUtil.processIncomingReport(
                                testReportSpec,
                                ReportSpecUtil.countBucketIncrements(
                                        testReportSpec, existingReport_2),
                                existingReport_2,
                                existingReports)
                        .first
                        .size());
        existingReports.add(existingReport_2);
        Pair<List<EventReport>, Integer> actualResult =
                ReportSpecUtil.processIncomingReport(
                        testReportSpec,
                        ReportSpecUtil.countBucketIncrements(testReportSpec, incomingReport),
                        incomingReport,
                        existingReports);
        assertEquals(new Pair<>(new ArrayList<>(), 0), actualResult);
    }

    @Test
    public void processIncomingReport_higherPriority_reportWithLaterTriggerTimeDeleted()
            throws JSONException {
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec = new ReportSpec(
                SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), "2", source);
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
                ReportSpecUtil.processIncomingReport(
                                testReportSpec,
                                ReportSpecUtil.countBucketIncrements(
                                        testReportSpec, existingReport_1),
                                existingReport_1,
                                new ArrayList<>())
                        .first
                        .size());
        existingReports.add(existingReport_1);
        assertEquals(
                0,
                ReportSpecUtil.processIncomingReport(
                                testReportSpec,
                                ReportSpecUtil.countBucketIncrements(
                                        testReportSpec, existingReport_2),
                                existingReport_2,
                                existingReports)
                        .first
                        .size());
        existingReports.add(existingReport_2);
        Pair<List<EventReport>, Integer> actualResult =
                ReportSpecUtil.processIncomingReport(
                        testReportSpec,
                        ReportSpecUtil.countBucketIncrements(testReportSpec, incomingReport),
                        incomingReport,
                        existingReports);
        assertEquals(
                new Pair<>(new ArrayList<>(Collections.singletonList(existingReport_2)), 1),
                actualResult);
    }

    @Test
    public void processIncomingReport_earlierReportTime_reportWithLaterTimeDeleted()
            throws JSONException {
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec = new ReportSpec(
                SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), "2", source);
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
                ReportSpecUtil.processIncomingReport(
                                testReportSpec,
                                ReportSpecUtil.countBucketIncrements(
                                        testReportSpec, existingReport_1),
                                existingReport_1,
                                new ArrayList<>())
                        .first
                        .size());
        existingReports.add(existingReport_1);
        assertEquals(
                0,
                ReportSpecUtil.processIncomingReport(
                                testReportSpec,
                                ReportSpecUtil.countBucketIncrements(
                                        testReportSpec, existingReport_2),
                                existingReport_2,
                                existingReports)
                        .first
                        .size());
        existingReports.add(existingReport_2);
        Pair<List<EventReport>, Integer> actualResult =
                ReportSpecUtil.processIncomingReport(
                        testReportSpec,
                        ReportSpecUtil.countBucketIncrements(testReportSpec, incomingReport),
                        incomingReport,
                        existingReports);
        assertEquals(
                new Pair<>(new ArrayList<>(Collections.singletonList(existingReport_2)), 1),
                actualResult);
    }

    @Test
    public void processIncomingReport_countBasedNoBucketIncrement_noReportsDeleted()
            throws JSONException {
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec = new ReportSpec(
                SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(), "2", source);
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
                ReportSpecUtil.processIncomingReport(
                                testReportSpec,
                                ReportSpecUtil.countBucketIncrements(
                                        testReportSpec, existingReport_1),
                                existingReport_1,
                                new ArrayList<>())
                        .first
                        .size());
        assertEquals(
                0,
                ReportSpecUtil.processIncomingReport(
                                testReportSpec,
                                ReportSpecUtil.countBucketIncrements(
                                        testReportSpec, existingReport_2),
                                existingReport_2,
                                new ArrayList<>())
                        .first
                        .size());
        existingReports.add(existingReport_2);

        // Assertion
        Pair<List<EventReport>, Integer> actualResult =
                ReportSpecUtil.processIncomingReport(
                        testReportSpec,
                        ReportSpecUtil.countBucketIncrements(testReportSpec, incomingReport),
                        incomingReport,
                        existingReports);
        assertEquals(new Pair<>(new ArrayList<>(), 0), actualResult);
    }

    @Test
    public void processIncomingReport_earlierReportTimeLowerPriority_reportWithLaterTimeDeleted()
            throws JSONException {
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec =
                new ReportSpec(
                        SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), "2", source);
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
                ReportSpecUtil.processIncomingReport(
                                testReportSpec,
                                ReportSpecUtil.countBucketIncrements(
                                        testReportSpec, existingReport_1),
                                existingReport_1,
                                new ArrayList<>())
                        .first
                        .size());
        existingReports.add(existingReport_1);
        assertEquals(
                0,
                ReportSpecUtil.processIncomingReport(
                                testReportSpec,
                                ReportSpecUtil.countBucketIncrements(
                                        testReportSpec, existingReport_2),
                                existingReport_2,
                                existingReports)
                        .first
                        .size());
        existingReports.add(existingReport_2);
        Pair<List<EventReport>, Integer> actualResult =
                ReportSpecUtil.processIncomingReport(
                        testReportSpec,
                        ReportSpecUtil.countBucketIncrements(testReportSpec, incomingReport),
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
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setAttributedTriggers(null)
                        .build();
        source.buildAttributedTriggers();
        ReportSpec testReportSpec =
                new ReportSpec(
                        templateReportSpec.encodeTriggerSpecsToJson(),
                        Integer.toString(templateReportSpec.getMaxReports()),
                        source,
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
        int incrementingBucket =
                ReportSpecUtil.countBucketIncrements(testReportSpec, incomingReport);
        assertEquals(2, incrementingBucket);
        Pair<List<EventReport>, Integer> actualResult =
                ReportSpecUtil.processIncomingReport(
                        templateReportSpec,
                        incrementingBucket,
                        incomingReport,
                        List.of(currentEventReport1, currentEventReport1));
        assertEquals(
                new Pair<>(new ArrayList<>(Collections.singletonList(currentEventReport1)), 2),
                actualResult);
    }

    @Test
    public void numDecrementingBucket_valueInHighestBucket_correctlyCounts() throws JSONException {
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec = new ReportSpec(
                SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(), "2", source);
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
        assertEquals(0, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_1));
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertEquals(1, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_2));
        testReportSpec.insertAttributedTrigger(existingReport_2);
        assertEquals(0, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_3));
        testReportSpec.insertAttributedTrigger(existingReport_3);
        assertEquals(1, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_4));
        testReportSpec.insertAttributedTrigger(existingReport_5);
        assertEquals(0, ReportSpecUtil.numDecrementingBucket(testReportSpec, existingReport_5));
    }

    @Test
    public void numDecrementingBucket_valueInFirstBucket_correctlyCounts() throws JSONException {
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec = new ReportSpec(
                SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(), "2", source);
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
        assertEquals(0, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_1));
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertEquals(0, ReportSpecUtil.numDecrementingBucket(testReportSpec, existingReport_2));
    }

    @Test
    public void numDecrementingBucket_multipleDecrements_correctlyCounts() throws JSONException {
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
                        + "\"summary_buckets\": [2, 4, 6, 8, 10]}]";
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec = new ReportSpec(triggerSpecsString, "5", source);
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
        assertEquals(0, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_1));
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertEquals(1, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_2));
        testReportSpec.insertAttributedTrigger(existingReport_2);
        assertEquals(1, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_3));
        testReportSpec.insertAttributedTrigger(existingReport_3);
        assertEquals(0, ReportSpecUtil.numDecrementingBucket(testReportSpec, existingReport_2));
        testReportSpec.deleteFromAttributedValue(existingReport_2);
        assertEquals(2, ReportSpecUtil.numDecrementingBucket(testReportSpec, existingReport_3));
    }

    @Test
    public void countBucketIncrements_singleTrigger_correctlyCounts() throws JSONException {
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec =
                new ReportSpec(
                        SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), "3", source);
        EventReport existingReport_1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(3L)
                        .setTriggerValue(1L)
                        .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
                        .build();
        // Assertion
        assertEquals(1, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_1));
    }

    @Test
    public void numDecrementingBucket_countBasedInsertingMultipleTriggers_correctlyCounts()
            throws JSONException {
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec =
                new ReportSpec(
                        SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), "3", source);
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
        assertEquals(1, ReportSpecUtil.numDecrementingBucket(testReportSpec, existingReport_1));
        testReportSpec.insertAttributedTrigger(existingReport_2);
        assertEquals(1, ReportSpecUtil.numDecrementingBucket(testReportSpec, existingReport_2));
    }

    @Test
    public void getFlexEventReportingTime_triggerTimeEarlierThanSourceTime_signalsInvalid()
            throws JSONException {
        ReportSpec testReportSpec =
                new ReportSpec(
                        SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), "3", null);
        assertEquals(
                -1,
                ReportSpecUtil.getFlexEventReportingTime(
                        testReportSpec, 10000, 9999, new UnsignedLong(1L)));
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
                new ReportSpec(
                        new JSONArray(new JSONObject[] {jsonTriggerSpec1}).toString(), "3", null);

        // Assertion
        assertEquals(
                -1,
                ReportSpecUtil.getFlexEventReportingTime(
                        testReportSpec, 10000, 10999, new UnsignedLong(1L)));
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
                new ReportSpec(
                        new JSONArray(new JSONObject[] {jsonTriggerSpec}).toString(), "3", null);

        // Assertion
        assertEquals(
                110000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                ReportSpecUtil.getFlexEventReportingTime(
                        testReportSpec, 100000, 109999, new UnsignedLong(1L)));
        assertEquals(
                120000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                ReportSpecUtil.getFlexEventReportingTime(
                        testReportSpec, 100000, 119999, new UnsignedLong(1L)));
        assertEquals(
                130000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                ReportSpecUtil.getFlexEventReportingTime(
                        testReportSpec, 100000, 129999, new UnsignedLong(1L)));
        assertEquals(
                140000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                ReportSpecUtil.getFlexEventReportingTime(
                        testReportSpec, 100000, 139999, new UnsignedLong(1L)));
        assertEquals(
                110000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                ReportSpecUtil.getFlexEventReportingTime(
                        testReportSpec, 100000, 109999, new UnsignedLong(2L)));
        assertEquals(
                120000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                ReportSpecUtil.getFlexEventReportingTime(
                        testReportSpec, 100000, 119999, new UnsignedLong(2L)));
        assertEquals(
                130000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                ReportSpecUtil.getFlexEventReportingTime(
                        testReportSpec, 100000, 129999, new UnsignedLong(3L)));
        assertEquals(
                140000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                ReportSpecUtil.getFlexEventReportingTime(
                        testReportSpec, 100000, 139999, new UnsignedLong(4L)));
        assertEquals(
                -1,
                ReportSpecUtil.getFlexEventReportingTime(
                        testReportSpec, 100000, 149999, new UnsignedLong(1L)));
    }

    @Test
    public void getFlexEventReportingTime_overridesMinEventReportDelay() throws JSONException {
        JSONObject jsonTriggerSpec = new JSONObject();
        jsonTriggerSpec.put("trigger_data", new JSONArray(new int[] {1, 2, 3, 4}));
        JSONObject windows = new JSONObject();
        windows.put("start_time", 1000);
        windows.put("end_times", new JSONArray(new int[] {10000, 20000, 30000, 40000}));
        jsonTriggerSpec.put("event_report_windows", windows);
        jsonTriggerSpec.put("summary_buckets", new JSONArray(new int[] {1, 10, 100}));
        ReportSpec testReportSpec =
                new ReportSpec(
                        new JSONArray(new JSONObject[] {jsonTriggerSpec}).toString(), "3", null);

        long minReportDelay = 23000L;
        doReturn(minReportDelay).when(mFlags).getMeasurementMinEventReportDelayMillis();
        ExtendedMockito.doReturn(mFlags).when(FlagsFactory::getFlags);

        long expectedReportTimeWithoutDelay = 110000L;
        long sourceRegistrationTime = 100000L;
        long triggerTime = 109999L;

        // Assertion
        assertEquals(
                expectedReportTimeWithoutDelay + minReportDelay,
                ReportSpecUtil.getFlexEventReportingTime(
                        testReportSpec, sourceRegistrationTime, triggerTime, new UnsignedLong(1L)));
    }

    @Test
    public void countBucketIncrements_singleOrNoIncrements_correctlyCounts() throws JSONException {
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec = new ReportSpec(
                SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(), "2", source);
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
        assertEquals(0, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_1));
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertEquals(1, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_2));
        testReportSpec.insertAttributedTrigger(existingReport_2);
        assertEquals(0, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_3));
        testReportSpec.insertAttributedTrigger(existingReport_3);
        assertEquals(1, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_4));
    }

    @Test
    public void countBucketIncrements_multipleIncrements_correctlyCounts() throws JSONException {
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
                        + "\"summary_buckets\": [2, 4, 6, 8, 10]}]";
        Source source = SourceFixture.getValidSource();
        ReportSpec testReportSpec = new ReportSpec(triggerSpecsString, "5", source);
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
        assertEquals(0, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_1));
        testReportSpec.insertAttributedTrigger(existingReport_1);
        assertEquals(1, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_2));
        testReportSpec.insertAttributedTrigger(existingReport_2);
        assertEquals(0, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_3));
        testReportSpec.insertAttributedTrigger(existingReport_3);
        assertEquals(3, ReportSpecUtil.countBucketIncrements(testReportSpec, existingReport_4));
    }

    @Test
    public void getSummaryBucketFromIndex_baseline_equal() throws JSONException {
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
                        + "\"summary_buckets\": [2, 4, 6, 8, 10]}]";
        ReportSpec testReportSpec = new ReportSpec(triggerSpecsString, "5", null);

        // Assertion
        List<Long> summaryBucket =
                ReportSpecUtil.getSummaryBucketsForTriggerData(
                        testReportSpec, new UnsignedLong(1L));
        assertEquals(
                new Pair<>(2L, 3L), ReportSpecUtil.getSummaryBucketFromIndex(0, summaryBucket));
        assertEquals(
                new Pair<>(4L, 5L), ReportSpecUtil.getSummaryBucketFromIndex(1, summaryBucket));
        assertEquals(
                new Pair<>(10L, 2147483646L),
                ReportSpecUtil.getSummaryBucketFromIndex(4, summaryBucket));
    }

    @Test
    public void resetSummaryBucketForAllEventReport_multiTriggerDataAndWindow_equal()
            throws DatastoreException {
        Source source =
                SourceFixture.getValidFullSourceBuilderWithFlexEventReportValueSum()
                        .setId("1234")
                        .build();
        long baseTime = System.currentTimeMillis();
        List<EventReport> reportList1 = new ArrayList<>();
        reportList1.add(
                getEventReportBuilder(source)
                        .setId("1")
                        .setStatus(EventReport.Status.PENDING)
                        .setTriggerId("101")
                        .setTriggerTime(baseTime)
                        .setTriggerDebugKey(new UnsignedLong(9999L))
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerSummaryBucket("10,99")
                        .build());
        reportList1.add(
                getEventReportBuilder(source)
                        .setId("7")
                        .setStatus(EventReport.Status.PENDING)
                        .setTriggerId("102")
                        .setTriggerTime(baseTime + 36000000)
                        .setTriggerDebugKey(new UnsignedLong(9998L))
                        .setTriggerData(new UnsignedLong(2L))
                        .build());

        reportList1.add(
                getEventReportBuilder(source)
                        .setId("17")
                        .setStatus(EventReport.Status.PENDING)
                        .setTriggerId("103")
                        .setTriggerTime(baseTime + 72000000)
                        .setTriggerDebugKey(new UnsignedLong(9979L))
                        .setTriggerData(new UnsignedLong(1L))
                        .build());

        when(mMeasurementDao.getSourceEventReports(source)).thenReturn(reportList1);

        ReportSpecUtil.resetSummaryBucketForAllEventReport(source, mMeasurementDao);
        ArgumentCaptor<String> statusArgEventReportID = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusArgSummaryBucket = ArgumentCaptor.forClass(String.class);
        verify(mMeasurementDao, times(2))
                .updateEventReportSummaryBucket(
                        statusArgEventReportID.capture(), statusArgSummaryBucket.capture());

        assertEquals(List.of("17", "7"), statusArgEventReportID.getAllValues());
        assertEquals(List.of("10,99", "100,2147483646"), statusArgSummaryBucket.getAllValues());
    }

    @Test
    public void resetSummaryBucketForAllEventReport_involveDeliveredReport_equal()
            throws DatastoreException {
        Source source =
                SourceFixture.getValidFullSourceBuilderWithFlexEventReportValueSum()
                        .setId("1234")
                        .build();
        long baseTime = System.currentTimeMillis();
        List<EventReport> reportList1 = new ArrayList<>();
        reportList1.add(
                getEventReportBuilder(source)
                        .setId("1")
                        .setStatus(EventReport.Status.DELIVERED)
                        .setTriggerId("101")
                        .setTriggerTime(baseTime)
                        .setTriggerDebugKey(new UnsignedLong(9999L))
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerSummaryBucket("10,99")
                        .build());
        reportList1.add(
                getEventReportBuilder(source)
                        .setId("7")
                        .setStatus(EventReport.Status.PENDING)
                        .setTriggerId("102")
                        .setTriggerTime(baseTime + 36000000)
                        .setTriggerDebugKey(new UnsignedLong(9998L))
                        .setTriggerData(new UnsignedLong(2L))
                        .build());

        reportList1.add(
                getEventReportBuilder(source)
                        .setId("17")
                        .setStatus(EventReport.Status.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setTriggerId("103")
                        .setTriggerTime(baseTime + 72000000)
                        .setTriggerDebugKey(new UnsignedLong(9979L))
                        .setTriggerData(new UnsignedLong(1L))
                        .build());

        when(mMeasurementDao.getSourceEventReports(source)).thenReturn(reportList1);

        ReportSpecUtil.resetSummaryBucketForAllEventReport(source, mMeasurementDao);
        ArgumentCaptor<String> statusArgEventReportID = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusArgSummaryBucket = ArgumentCaptor.forClass(String.class);
        verify(mMeasurementDao, times(2))
                .updateEventReportSummaryBucket(
                        statusArgEventReportID.capture(), statusArgSummaryBucket.capture());

        assertEquals(List.of("17", "7"), statusArgEventReportID.getAllValues());
        assertEquals(List.of("10,99", "100,2147483646"), statusArgSummaryBucket.getAllValues());
    }

    @Test
    public void resetSummaryBucketForAllEventReport_involveDeletedReport_equal()
            throws DatastoreException {
        Source source =
                SourceFixture.getValidFullSourceBuilderWithFlexEventReportValueSum()
                        .setId("1234")
                        .build();
        long baseTime = System.currentTimeMillis();
        List<EventReport> reportList1 = new ArrayList<>();
        reportList1.add(
                getEventReportBuilder(source)
                        .setId("1")
                        .setStatus(EventReport.Status.MARKED_TO_DELETE)
                        .setTriggerId("101")
                        .setTriggerTime(baseTime)
                        .setTriggerDebugKey(new UnsignedLong(9999L))
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerSummaryBucket("10,99")
                        .build());
        reportList1.add(
                getEventReportBuilder(source)
                        .setId("7")
                        .setStatus(EventReport.Status.PENDING)
                        .setTriggerId("102")
                        .setTriggerTime(baseTime + 36000000)
                        .setTriggerDebugKey(new UnsignedLong(9998L))
                        .setTriggerData(new UnsignedLong(2L))
                        .build());

        reportList1.add(
                getEventReportBuilder(source)
                        .setId("17")
                        .setStatus(EventReport.Status.PENDING)
                        .setTriggerId("103")
                        .setTriggerTime(baseTime + 72000000)
                        .setTriggerDebugKey(new UnsignedLong(9979L))
                        .setTriggerData(new UnsignedLong(1L))
                        .build());

        when(mMeasurementDao.getSourceEventReports(source)).thenReturn(reportList1);

        ReportSpecUtil.resetSummaryBucketForAllEventReport(source, mMeasurementDao);
        ArgumentCaptor<String> statusArgEventReportID = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusArgSummaryBucket = ArgumentCaptor.forClass(String.class);
        verify(mMeasurementDao, times(2))
                .updateEventReportSummaryBucket(
                        statusArgEventReportID.capture(), statusArgSummaryBucket.capture());

        assertEquals(List.of("17", "7"), statusArgEventReportID.getAllValues());
        assertEquals(List.of("10,99", "10,99"), statusArgSummaryBucket.getAllValues());
    }

    private static EventReport.Builder getEventReportBuilder(Source source) {
        return EventReportFixture.getBaseEventReportBuild()
                .setSourceType(source.getSourceType())
                .setRegistrationOrigin(source.getRegistrationOrigin())
                .setSourceEventId(source.getEventId())
                .setEnrollmentId(source.getEnrollmentId())
                // Event report attribution destination derivation skipped since it's not relevant.
                .setSourceType(source.getSourceType())
                .setSourceId(source.getId());
    }
}
