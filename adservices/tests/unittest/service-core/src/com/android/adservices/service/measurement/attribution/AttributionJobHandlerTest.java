/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement.attribution;

import static com.android.adservices.service.Flags.MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LogUtil;
import com.android.adservices.common.WebUtil;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.AttributionConfig;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.ReportSpec;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.aggregation.AggregateAttributionData;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.noising.SourceNoiseHandler;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.reporting.EventReportWindowCalcDelegate;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementAttributionStats;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Unit test for {@link AttributionJobHandler}
 */
@RunWith(MockitoJUnitRunner.class)
public class AttributionJobHandlerTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    private static final long SOURCE_TIME = 1690000000000L;
    private static final long TRIGGER_TIME = 1690000001000L;
    private static final long EXPIRY_TIME = 1692592000000L;
    private static final long LOOKBACK_WINDOW = 1000L;
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.example.app");
    private static final Uri WEB_DESTINATION = WebUtil.validUri("https://web.example.test");
    private static final Uri PUBLISHER = Uri.parse("android-app://publisher.app");
    private static final Uri REGISTRATION_URI = WebUtil.validUri("https://subdomain.example.test");
    private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(111111L);
    private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(222222L);
    private static final String TRIGGER_ID = "triggerId1";
    private static final String EVENT_TRIGGERS =
            "[\n"
                    + "{\n"
                    + "  \"trigger_data\": \"5\",\n"
                    + "  \"priority\": \"123\",\n"
                    + "  \"deduplication_key\": \"2\",\n"
                    + "  \"filters\": [{\n"
                    + "    \"source_type\": [\"event\"],\n"
                    + "    \"key_1\": [\"value_1\"] \n"
                    + "   }]\n"
                    + "}"
                    + "]\n";

    private static final String AGGREGATE_DEDUPLICATION_KEYS_1 =
            "[{\"deduplication_key\": \"" + 10 + "\"" + " }" + "]";

    private static Trigger createAPendingTrigger() {
        return TriggerFixture.getValidTriggerBuilder()
                .setId(TRIGGER_ID)
                .setTriggerTime(TRIGGER_TIME)
                .setStatus(Trigger.Status.PENDING)
                .setEventTriggers(EVENT_TRIGGERS)
                .build();
    }

    DatastoreManager mDatastoreManager;

    AttributionJobHandler mHandler;

    EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;

    SourceNoiseHandler mSourceNoiseHandler;

    @Mock
    IMeasurementDao mMeasurementDao;

    @Mock
    ITransaction mTransaction;

    @Mock Flags mFlags;

    @Mock AdServicesLogger mLogger;
    @Mock AdServicesErrorLogger mErrorLogger;
    @Mock DebugReportApi mDebugReportApi;

    class FakeDatastoreManager extends DatastoreManager {

        FakeDatastoreManager() {
            super(mErrorLogger);
        }

        @Override
        public ITransaction createNewTransaction() {
            return mTransaction;
        }

        @Override
        public IMeasurementDao getMeasurementDao() {
            return mMeasurementDao;
        }

        @Override
        protected int getDataStoreVersion() {
            return 0;
        }
    }

    @Before
    public void before() {
        mDatastoreManager = new FakeDatastoreManager();
        mEventReportWindowCalcDelegate = spy(new EventReportWindowCalcDelegate(mFlags));
        mSourceNoiseHandler = spy(new SourceNoiseHandler(mFlags));
        mLogger = spy(AdServicesLoggerImpl.getInstance());
        mHandler =
                new AttributionJobHandler(
                        mDatastoreManager,
                        mFlags,
                        mDebugReportApi,
                        mEventReportWindowCalcDelegate,
                        mSourceNoiseHandler,
                        mLogger,
                        new XnaSourceCreator(mFlags));
        when(mFlags.getMeasurementEnableXNA()).thenReturn(false);
        when(mFlags.getMeasurementMaxAttributionPerRateLimitWindow()).thenReturn(100);
        when(mFlags.getMeasurementMaxDistinctEnrollmentsInAttribution()).thenReturn(10);
        when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(true);
        when(mFlags.getMeasurementMaxAttributionsPerInvocation())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTIONS_PER_INVOCATION);
        when(mFlags.getMeasurementMaxEventReportsPerDestination())
                .thenReturn(Flags.MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION);
        when(mFlags.getMeasurementMaxAggregateReportsPerDestination())
                .thenReturn(Flags.MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION);
    }

    @Test
    public void shouldIgnoreNonPendingTrigger() throws DatastoreException {
        Trigger trigger = TriggerFixture.getValidTriggerBuilder()
                .setId("triggerId1")
                .setStatus(Trigger.Status.IGNORED).build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao).getTrigger(trigger.getId());
        verify(mMeasurementDao, never()).updateTriggerStatus(any(), anyInt());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldIgnoreIfNoSourcesFound() throws DatastoreException {
        Trigger trigger = createAPendingTrigger();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(new ArrayList<>());
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldRejectBasedOnDedupKey() throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"event\"],\n"
                                        + "    \"key_1\": [\"value_1\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .build();
        String attributionStatus = getAttributionStatus(
                List.of("triggerId2", "triggerId3"),
                List.of("1", "2"),
                List.of("1", "2"));
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventAttributionStatus(attributionStatus)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldRejectBasedOnDedupKey_dedupAlignFlagOff() throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"event\"],\n"
                                        + "    \"key_1\": [\"value_1\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(
                                Arrays.asList(new UnsignedLong(1L), new UnsignedLong(2L)))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(false);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotCreateEventReportAfterEventReportWindow()
            throws DatastoreException {
        long eventTime = System.currentTimeMillis();
        long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[{\"trigger_data\":\"1\","
                                        + "\"filters\":[{\"source_type\": [\"event\"]}]"
                                        + "}]")
                        .setTriggerTime(triggerTime)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setEventId(new UnsignedLong(1L))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(eventTime)
                        .setEventReportWindow(triggerTime - 1)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotCreateEventReportAfterEventReportWindow_secondTrigger()
            throws DatastoreException, JSONException {
        long eventTime = System.currentTimeMillis();
        long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
        Trigger trigger1 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers("[{\"trigger_data\":\"1\"}]")
                        .setTriggerTime(triggerTime)
                        .build();
        Trigger trigger2 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId2")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers("[{\"trigger_data\":\"0\"}]")
                        .setTriggerTime(triggerTime + 1L)
                        .build();
        List<Trigger> triggers = new ArrayList<>();
        triggers.add(trigger1);
        triggers.add(trigger2);
        List<Source> matchingSourceList = new ArrayList<>();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(eventTime)
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                        .setEventReportWindow(triggerTime + 1L)
                        .build();
        matchingSourceList.add(source);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Arrays.asList(trigger1.getId(), trigger2.getId()));
        when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
        when(mMeasurementDao.getTrigger(trigger2.getId())).thenReturn(trigger2);
        when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getMatchingActiveSources(trigger2)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);

        assertTrue(mHandler.performPendingAttributions());
        // Verify trigger status updates.
        verify(mMeasurementDao).updateTriggerStatus(
                eq(Collections.singletonList(trigger1.getId())), eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao).updateTriggerStatus(
                eq(Collections.singletonList(trigger2.getId())), eq(Trigger.Status.IGNORED));
        // Verify new event report insertion.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao, times(1))
                .insertEventReport(reportArg.capture());
        List<EventReport> newReportArgs = reportArg.getAllValues();
        assertEquals(1, newReportArgs.size());
        assertEquals(
                newReportArgs.get(0).getTriggerData(),
                triggers.get(0).parseEventTriggers(mFlags).get(0).getTriggerData());
    }

    @Test
    public void shouldNotCreateEventReportAfterEventReportWindow_prioritisedSource()
            throws DatastoreException {
        String eventTriggers =
                "[{\"trigger_data\": \"5\","
                        + "\"priority\": \"123\","
                        + "\"filters\":[{\"key_1\":[\"value_1\"]}]"
                        + "}]";
        long eventTime = System.currentTimeMillis();
        long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(eventTriggers)
                        .setTriggerTime(triggerTime)
                        .build();
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setPriority(100L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(eventTime)
                        .setEventReportWindow(triggerTime + 1L)
                        .build();
        // Second source has higher priority but the event report window ends before trigger time
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source2")
                        .setPriority(200L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(eventTime + 1000)
                        .setEventReportWindow(triggerTime - 1)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source1);
        matchingSourceList.add(source2);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        mHandler.performPendingAttributions();
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
        assertEquals(1, matchingSourceList.size());
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotAddIfRateLimitExceeded() throws DatastoreException {
        Trigger trigger = createAPendingTrigger();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(105L);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao).getAttributionsPerRateLimitWindow(source, trigger);
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotAddIfAdTechPrivacyBoundExceeded() throws DatastoreException {
        Trigger trigger = createAPendingTrigger();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestInAttribution(
                        any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(10);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .countDistinctReportingOriginsPerPublisherXDestInAttribution(
                        any(), any(), any(), anyLong(), anyLong());
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performPendingAttributions_vtcWithConfiguredReportsCount_attributeUptoConfigLimit()
            throws DatastoreException {
        // Setup
        doReturn(true).when(mFlags).getMeasurementEnableVtcConfigurableMaxEventReports();
        doReturn(3).when(mFlags).getMeasurementVtcConfigurableMaxEventReportsCount();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        doReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()))
                .when(mMeasurementDao)
                .getSourceDestinations(source.getId());
        // 2 event reports already present for the source
        doReturn(Arrays.asList(mock(EventReport.class), mock(EventReport.class)))
                .when(mMeasurementDao)
                .getSourceEventReports(source);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        Trigger trigger = createAPendingTrigger();
        doReturn(trigger).when(mMeasurementDao).getTrigger(trigger.getId());
        doReturn(matchingSourceList).when(mMeasurementDao).getMatchingActiveSources(trigger);
        doReturn(Collections.singletonList(trigger.getId()))
                .when(mMeasurementDao)
                .getPendingTriggerIds();

        // Execution
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        // Assertion
        verify(mMeasurementDao, times(1)).insertEventReport(any());
    }

    @Test
    public void shouldIgnoreForMaxReportsPerSource() throws DatastoreException {
        Trigger trigger = createAPendingTrigger();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        EventReport eventReport1 = new EventReport.Builder()
                .setStatus(EventReport.Status.DELIVERED)
                .build();
        EventReport eventReport2 = new EventReport.Builder()
                .setStatus(EventReport.Status.DELIVERED)
                .build();
        EventReport eventReport3 = new EventReport.Builder().setStatus(
                EventReport.Status.DELIVERED).build();
        List<EventReport> matchingReports = new ArrayList<>();
        matchingReports.add(eventReport1);
        matchingReports.add(eventReport2);
        matchingReports.add(eventReport3);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotReplaceHighPriorityReports() throws DatastoreException {
        String eventTriggers =
                "[\n"
                        + "{\n"
                        + "  \"trigger_data\": \"5\",\n"
                        + "  \"priority\": \"100\",\n"
                        + "  \"deduplication_key\": \"2\"\n"
                        + "}"
                        + "]\n";
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setEventTriggers(eventTriggers)
                        .setStatus(Trigger.Status.PENDING)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        EventReport eventReport1 =
                new EventReport.Builder()
                        .setStatus(EventReport.Status.PENDING)
                        .setTriggerPriority(200L)
                        .build();
        EventReport eventReport2 = new EventReport.Builder()
                .setStatus(EventReport.Status.DELIVERED)
                .build();
        EventReport eventReport3 = new EventReport.Builder()
                .setStatus(EventReport.Status.DELIVERED)
                .build();
        List<EventReport> matchingReports = new ArrayList<>();
        matchingReports.add(eventReport1);
        matchingReports.add(eventReport2);
        matchingReports.add(eventReport3);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldDoSimpleAttribution() throws DatastoreException {
        Trigger trigger = createAPendingTrigger();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("2"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(any());

        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());

        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldDoSimpleAttribution_dedupAlignFlagOff() throws DatastoreException {
        Trigger trigger = createAPendingTrigger();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(false);

        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
        assertEquals(
                sourceArg.getValue().getEventReportDedupKeys(),
                Collections.singletonList(new UnsignedLong(2L)));
        verify(mMeasurementDao).insertEventReport(any());

        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());

        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldIgnoreLowPrioritySourceWhileAttribution() throws DatastoreException {
        String eventTriggers =
                "[\n"
                        + "{\n"
                        + "  \"trigger_data\": \"5\",\n"
                        + "  \"priority\": \"123\",\n"
                        + "  \"deduplication_key\": \"2\",\n"
                        + "  \"filters\": [{\n"
                        + "    \"key_1\": [\"value_1\"] \n"
                        + "   }]\n"
                        + "}"
                        + "]\n";
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(eventTriggers)
                        .setTriggerTime(3)
                        .build();
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setPriority(100L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(1L)
                        .setExpiryTime(30)
                        .build();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source2")
                        .setPriority(200L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(2L)
                        .setExpiryTime(30)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source1);
        matchingSourceList.add(source2);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(Pair.create(
                        source1.getAppDestinations(),
                        source1.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source2.getId()))
                .thenReturn(Pair.create(
                        source2.getAppDestinations(),
                        source2.getWebDestinations()));


        mHandler.performPendingAttributions();
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        verify(mMeasurementDao)
                .updateSourceStatus(eq(List.of(source1.getId())), eq(Source.Status.IGNORED));
        assertEquals(1, matchingSourceList.size());
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("2"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source2.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());

        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void
            performAttributions_sourceDeactivationAfterFilteringFlagOn_ignoresWithoutAttribution()
                    throws DatastoreException {
        String eventTriggers =
                "[{"
                        + "  \"trigger_data\": \"5\","
                        + "  \"priority\": \"123\","
                        + "  \"deduplication_key\": \"2\","
                        + "  \"filters\": [{"
                        + "    \"key_1\": [\"value_1\"] "
                        + "   }]"
                        + "}]";
        // Missing top level filters match anything. Competing sources are ignored directly after.
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(eventTriggers)
                        .setTriggerTime(3)
                        .build();
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setPriority(100L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(1L)
                        .setExpiryTime(30)
                        // Filters match the trigger's event triggers but the Source is not matched
                        // since it has lower priority.
                        .setFilterData("{\"key_1\":[\"1234\",\"value_1\"]}")
                        .build();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source2")
                        .setPriority(200L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(2L)
                        .setExpiryTime(30)
                        // Filters do not match the trigger's event triggers so a report is not
                        // generated.
                        .setFilterData("{\"key_1\":[\"no_match\"]}")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source1);
        matchingSourceList.add(source2);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(Pair.create(
                        source1.getAppDestinations(),
                        source1.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source2.getId()))
                .thenReturn(Pair.create(
                        source2.getAppDestinations(),
                        source2.getWebDestinations()));

        when(mFlags.getMeasurementEnableSourceDeactivationAfterFiltering()).thenReturn(true);

        mHandler.performPendingAttributions();
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        verify(mMeasurementDao)
                .updateSourceStatus(eq(List.of(source1.getId())), eq(Source.Status.IGNORED));
        assertEquals(1, matchingSourceList.size());
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceAttributedTriggers(anyString(), any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void
            performAttributions_sourceDeactivationAfterFilteringFlagOff_doesNotIgnoreWithoutAttr()
                    throws DatastoreException {
        String eventTriggers =
                "[{"
                        + "  \"trigger_data\": \"5\","
                        + "  \"priority\": \"123\","
                        + "  \"deduplication_key\": \"2\","
                        + "  \"filters\": [{"
                        + "    \"key_1\": [\"value_1\"] "
                        + "   }]"
                        + "}]";
        // Missing top level filters match anything.
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(eventTriggers)
                        .setTriggerTime(3)
                        .build();
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setPriority(100L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(1L)
                        .setExpiryTime(30)
                        // Filters match but the Source is not matched since it has lower priority.
                        .setFilterData("{\"key_1\":[\"1234\",\"value_1\"]}")
                        .build();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source2")
                        .setPriority(200L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(2L)
                        .setExpiryTime(30)
                        // Filters do not match, attribution flow returns and does not ignore
                        // competing sources.
                        .setFilterData("{\"key_1\":[\"1234\",\"no_match\"]}")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source1);
        matchingSourceList.add(source2);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(Pair.create(
                        source1.getAppDestinations(),
                        source1.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source2.getId()))
                .thenReturn(Pair.create(
                        source2.getAppDestinations(),
                        source2.getWebDestinations()));

        when(mFlags.getMeasurementEnableSourceDeactivationAfterFiltering()).thenReturn(false);

        mHandler.performPendingAttributions();
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        // Attribution did not occur and competing sources are not ignored.
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
        assertEquals(1, matchingSourceList.size());
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceAttributedTriggers(anyString(), any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldReplaceLowPriorityReportWhileAttribution() throws DatastoreException {
        String eventTriggers =
                "[\n"
                        + "{\n"
                        + "  \"trigger_data\": \"5\",\n"
                        + "  \"priority\": \"200\",\n"
                        + "  \"deduplication_key\": \"2\"\n"
                        + "}"
                        + "]\n";
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setEventTriggers(eventTriggers)
                        .setStatus(Trigger.Status.PENDING)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(new ArrayList<>())
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAppDestinations(List.of(APP_DESTINATION))
                        .setPublisherType(EventSurfaceType.APP)
                        .setPublisher(PUBLISHER)
                        .build();
        doReturn(5L)
                .when(mEventReportWindowCalcDelegate)
                .getReportingTime(any(Source.class), anyLong(), anyInt());
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        EventReport eventReport1 =
                new EventReport.Builder()
                        .setStatus(EventReport.Status.PENDING)
                        .setTriggerPriority(100L)
                        .setReportTime(5L)
                        .setAttributionDestinations(List.of(APP_DESTINATION))
                        .build();
        EventReport eventReport2 =
                new EventReport.Builder()
                        .setStatus(EventReport.Status.DELIVERED)
                        .setReportTime(5L)
                        .setAttributionDestinations(source.getAppDestinations())
                        .build();
        EventReport eventReport3 =
                new EventReport.Builder()
                        .setStatus(EventReport.Status.DELIVERED)
                        .setReportTime(5L)
                        .setAttributionDestinations(source.getAppDestinations())
                        .build();
        List<EventReport> matchingReports = new ArrayList<>();
        matchingReports.add(eventReport1);
        matchingReports.add(eventReport2);
        matchingReports.add(eventReport3);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        List.of(APP_DESTINATION),
                        List.of()));
        mHandler.performPendingAttributions();
        verify(mMeasurementDao).deleteEventReport(eventReport1);
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldRollbackOnFailure() throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(EVENT_TRIGGERS)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(anyString())).thenReturn(trigger);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        // Failure
        doThrow(new DatastoreException("Simulating failure"))
                .when(mMeasurementDao)
                .insertEventReport(any(EventReport.class));
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        mHandler.performPendingAttributions();
        verify(mMeasurementDao).getTrigger(anyString());
        verify(mMeasurementDao).getMatchingActiveSources(any());
        verify(mMeasurementDao).getAttributionsPerRateLimitWindow(any(), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any());
        verify(mMeasurementDao, never()).updateTriggerStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction).rollback();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldRollbackOnFailure_dedupAlignFlagOff() throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(EVENT_TRIGGERS)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(anyString())).thenReturn(trigger);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        // Failure
        when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(false);
        doThrow(new DatastoreException("Simulating failure"))
                .when(mMeasurementDao)
                .updateSourceEventReportDedupKeys(any());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        mHandler.performPendingAttributions();
        verify(mMeasurementDao).getTrigger(anyString());
        verify(mMeasurementDao).getMatchingActiveSources(any());
        verify(mMeasurementDao).getAttributionsPerRateLimitWindow(any(), any());
        verify(mMeasurementDao, never()).updateTriggerStatus(any(), anyInt());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction).rollback();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldPerformMultipleAttributions() throws DatastoreException, JSONException {
        Trigger trigger1 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .build();
        Trigger trigger2 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId2")
                        .setTriggerTime(TRIGGER_TIME + 1000L)
                        .setStatus(Trigger.Status.PENDING)
                        .setRegistrationOrigin(WebUtil.validUri("https://subdomain2.example.test"))
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"2\"\n"
                                        + "}"
                                        + "]\n")
                        .build();
        List<Trigger> triggers = new ArrayList<>();
        triggers.add(trigger1);
        triggers.add(trigger2);
        List<Source> matchingSourceList1 = new ArrayList<>();
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        matchingSourceList1.add(source1);
        List<Source> matchingSourceList2 = new ArrayList<>();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME + 500L)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setRegistrationOrigin(WebUtil.validUri("https://subdomain2.example.test"))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        matchingSourceList2.add(source2);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Arrays.asList(trigger1.getId(), trigger2.getId()));
        when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
        when(mMeasurementDao.getTrigger(trigger2.getId())).thenReturn(trigger2);
        when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList1);
        when(mMeasurementDao.getMatchingActiveSources(trigger2)).thenReturn(matchingSourceList2);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(Pair.create(
                        source1.getAppDestinations(),
                        source1.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source2.getId()))
                .thenReturn(Pair.create(
                        source2.getAppDestinations(),
                        source2.getWebDestinations()));

        assertTrue(mHandler.performPendingAttributions());
        // Verify trigger status updates.
        verify(mMeasurementDao, times(2)).updateTriggerStatus(any(), eq(Trigger.Status.ATTRIBUTED));
        // Verify source dedup key updates.
        String expectedAttributionStatus1 = getAttributionStatus(
                List.of(trigger1.getId()), List.of("5"), List.of("1"));
        String expectedAttributionStatus2 = getAttributionStatus(
                List.of(trigger2.getId()), List.of("5"), List.of("2"));
        ArgumentCaptor<String> sourceIdArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> attributionStatusArg = ArgumentCaptor.forClass(String.class);
        verify(mMeasurementDao, times(2)).updateSourceAttributedTriggers(
                sourceIdArg.capture(), attributionStatusArg.capture());
        List<String> sourceIds = sourceIdArg.getAllValues();
        List<String> attributionStatuses = attributionStatusArg.getAllValues();
        assertEquals(source1.getId(), sourceIds.get(0));
        assertEquals(expectedAttributionStatus1, attributionStatuses.get(0));
        assertEquals(source2.getId(), sourceIds.get(1));
        assertEquals(expectedAttributionStatus2, attributionStatuses.get(1));
        // Verify new event report insertion.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao, times(2)).insertEventReport(reportArg.capture());
        List<EventReport> newReportArgs = reportArg.getAllValues();
        boolean flexEventReportFlag = true;
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        for (int i = 0; i < newReportArgs.size(); i++) {
            assertEquals(
                    newReportArgs.get(i).getTriggerDedupKey(),
                    triggers.get(i).parseEventTriggers(mFlags).get(0).getDedupKey());
            assertEquals(
                    newReportArgs.get(i).getRegistrationOrigin(),
                    triggers.get(i).getRegistrationOrigin());
        }
    }

    @Test
    public void shouldAttributedToInstallAttributedSource() throws DatastoreException {
        long eventTime = System.currentTimeMillis();
        long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("trigger1")
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(triggerTime)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"2\"\n"
                                        + "}"
                                        + "]\n")
                        .build();
        // Lower priority and older priority source.
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setEventId(new UnsignedLong(1L))
                        .setPriority(100L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setInstallAttributed(true)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(10))
                        .setEventTime(eventTime - TimeUnit.DAYS.toMillis(2))
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source2")
                        .setEventId(new UnsignedLong(2L))
                        .setPriority(200L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(eventTime)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source2);
        matchingSourceList.add(source1);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(Pair.create(
                        source1.getAppDestinations(),
                        source1.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source2.getId()))
                .thenReturn(Pair.create(
                        source2.getAppDestinations(),
                        source2.getWebDestinations()));
        mHandler.performPendingAttributions();
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        verify(mMeasurementDao)
                .updateSourceStatus(eq(List.of(source2.getId())), eq(Source.Status.IGNORED));
        assertEquals(1, matchingSourceList.size());
        assertEquals(source2.getEventId(), matchingSourceList.get(0).getEventId());
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("2"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source1.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    }

    @Test
    public void shouldNotAttributeToOldInstallAttributedSource() throws DatastoreException {
        // Setup
        long eventTime = System.currentTimeMillis();
        long triggerTime = eventTime + TimeUnit.DAYS.toMillis(10);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("trigger1")
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(triggerTime)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"2\"\n"
                                        + "}"
                                        + "]\n")
                        .build();
        // Lower Priority. Install cooldown Window passed.
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setEventId(new UnsignedLong(1L))
                        .setPriority(100L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setInstallAttributed(true)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(3))
                        .setEventTime(eventTime - TimeUnit.DAYS.toMillis(2))
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source2")
                        .setEventId(new UnsignedLong(2L))
                        .setPriority(200L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(eventTime)
                        .setEventReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source2);
        matchingSourceList.add(source1);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(Pair.create(
                        source1.getAppDestinations(),
                        source1.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source2.getId()))
                .thenReturn(Pair.create(
                        source2.getAppDestinations(),
                        source2.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();
        trigger.setStatus(Trigger.Status.ATTRIBUTED);

        // Assertion
        verify(mMeasurementDao)
                .updateSourceStatus(eq(List.of(source1.getId())), eq(Source.Status.IGNORED));
        assertEquals(1, matchingSourceList.size());
        assertEquals(source1.getEventId(), matchingSourceList.get(0).getEventId());
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        ArgumentCaptor<String> sourceIdArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> attributionStatusArg = ArgumentCaptor.forClass(String.class);
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("2"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                sourceIdArg.capture(), attributionStatusArg.capture());
        assertEquals(source2.getId(), sourceIdArg.getValue());
        assertEquals(
                expectedAttributionStatus,
                attributionStatusArg.getValue());
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    }

    @Test
    public void shouldNotGenerateReportForAttributionModeFalsely() throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.FALSELY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotGenerateReportForAttributionModeNever() throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.NEVER)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performPendingAttributions_GeneratesEventReport_WithReportingOriginOfTrigger()
            throws DatastoreException {
        Trigger trigger1 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
                        .build();
        List<Source> matchingSourceList1 = new ArrayList<>();
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setRegistrationOrigin(WebUtil.validUri("https://source.example.test"))
                        .build();
        matchingSourceList1.add(source1);
        when(mMeasurementDao.getPendingTriggerIds()).thenReturn(Arrays.asList(trigger1.getId()));
        when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
        when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList1);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(
                        Pair.create(source1.getAppDestinations(), source1.getWebDestinations()));

        assertTrue(mHandler.performPendingAttributions());
        // Verify trigger status updates.
        verify(mMeasurementDao).updateTriggerStatus(any(), eq(Trigger.Status.ATTRIBUTED));
        // Verify new event report insertion.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(
                WebUtil.validUri("https://trigger.example.test"),
                eventReport.getRegistrationOrigin());
    }

    @Test
    public void performPendingAttributions_GeneratesEventReport_WithCoarseDestinations()
            throws DatastoreException {
        Trigger trigger1 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
                        .build();
        List<Source> matchingSourceList1 = new ArrayList<>();
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setRegistrationOrigin(WebUtil.validUri("https://source.example.test"))
                        .setAppDestinations(Collections.singletonList(APP_DESTINATION))
                        .setWebDestinations(Collections.singletonList(WEB_DESTINATION))
                        .setCoarseEventReportDestinations(true)
                        .build();
        matchingSourceList1.add(source1);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger1.getId()));
        when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
        when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList1);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(
                        Pair.create(source1.getAppDestinations(), source1.getWebDestinations()));
        when(mFlags.getMeasurementEnableCoarseEventReportDestinations()).thenReturn(true);

        assertTrue(mHandler.performPendingAttributions());
        // Verify trigger status updates.
        verify(mMeasurementDao).updateTriggerStatus(any(), eq(Trigger.Status.ATTRIBUTED));
        // Verify new event report insertion.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(
                WebUtil.validUri("https://trigger.example.test"),
                eventReport.getRegistrationOrigin());
        List<Uri> reportDestinations = eventReport.getAttributionDestinations();
        assertEquals(2, reportDestinations.size());
        assertEquals(APP_DESTINATION, reportDestinations.get(0));
        assertEquals(WEB_DESTINATION, reportDestinations.get(1));
    }

    @Test
    public void shouldObserveFlagOverriddenAggregateReportDelay()
            throws DatastoreException, JSONException {
        Trigger trigger = getAggregateTrigger();
        Source source = getAggregateSource();

        long reportMinDelay = TimeUnit.MINUTES.toMillis(61);
        long reportDelaySpan = TimeUnit.MINUTES.toMillis(10);
        when(mFlags.getMeasurementEnableConfigurableAggregateReportDelay()).thenReturn(true);
        when(mFlags.getMeasurementAggregateReportDelayConfig()).thenReturn(
                String.valueOf(reportMinDelay) + "," + String.valueOf(reportDelaySpan));

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

        // Assert expected aggregate report time range
        long lowerBound = TRIGGER_TIME + reportMinDelay;
        // Add slightly more delay to upper bound to account for execution.
        long upperBound = TRIGGER_TIME + reportMinDelay + reportDelaySpan + 1000L;
        AggregateReport capturedReport = aggregateReportCaptor.getValue();
        assertTrue(capturedReport.getScheduledReportTime() > lowerBound
                && capturedReport.getScheduledReportTime() < upperBound);
    }

    @Test
    public void shouldObserveDefaultAggregateReportDelay()
            throws DatastoreException, JSONException {
        Trigger trigger = getAggregateTrigger();
        Source source = getAggregateSource();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

        // Assert expected aggregate report time range
        long lowerBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
        // Add slightly more delay to upper bound to account for execution.
        long upperBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY
                + PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN + 1000L;
        AggregateReport capturedReport = aggregateReportCaptor.getValue();
        assertTrue(capturedReport.getScheduledReportTime() > lowerBound
                && capturedReport.getScheduledReportTime() < upperBound);
    }

    @Test
    public void shouldObserveDefaultAggregateReportDelayWhenFlagOverrideIsNull()
            throws DatastoreException, JSONException {
        Trigger trigger = getAggregateTrigger();
        Source source = getAggregateSource();

        when(mFlags.getMeasurementEnableConfigurableAggregateReportDelay()).thenReturn(true);
        when(mFlags.getMeasurementAggregateReportDelayConfig()).thenReturn(null);

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

        // Assert expected aggregate report time range
        long lowerBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
        // Add slightly more delay to upper bound to account for execution.
        long upperBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY
                + PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN + 1000L;
        AggregateReport capturedReport = aggregateReportCaptor.getValue();
        assertTrue(capturedReport.getScheduledReportTime() > lowerBound
                && capturedReport.getScheduledReportTime() < upperBound);
    }

    @Test
    public void shouldObserveDefaultAggregateReportDelayWhenFlagOverrideSizeIsInvalid()
            throws DatastoreException, JSONException {
        Trigger trigger = getAggregateTrigger();
        Source source = getAggregateSource();

        when(mFlags.getMeasurementEnableConfigurableAggregateReportDelay()).thenReturn(true);
        when(mFlags.getMeasurementAggregateReportDelayConfig()).thenReturn("12");

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

        // Assert expected aggregate report time range
        long lowerBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
        // Add slightly more delay to upper bound to account for execution.
        long upperBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY
                + PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN + 1000L;
        AggregateReport capturedReport = aggregateReportCaptor.getValue();
        assertTrue(capturedReport.getScheduledReportTime() > lowerBound
                && capturedReport.getScheduledReportTime() < upperBound);
    }

    @Test
    public void shouldObserveDefaultAggregateReportDelayWhenFlagOverrideValueIsInvalid()
            throws DatastoreException, JSONException {
        Trigger trigger = getAggregateTrigger();
        Source source = getAggregateSource();

        when(mFlags.getMeasurementEnableConfigurableAggregateReportDelay()).thenReturn(true);
        when(mFlags.getMeasurementAggregateReportDelayConfig()).thenReturn("1200u0000r,3600000");

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

        // Assert expected aggregate report time range
        long lowerBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
        // Add slightly more delay to upper bound to account for execution.
        long upperBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY
                + PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN + 1000L;
        AggregateReport capturedReport = aggregateReportCaptor.getValue();
        assertTrue(capturedReport.getScheduledReportTime() > lowerBound
                && capturedReport.getScheduledReportTime() < upperBound);
    }

    @Test
    public void shouldDoSimpleAttributionGenerateUnencryptedAggregateReport()
            throws DatastoreException, JSONException {
        JSONArray triggerDatas = new JSONArray();
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("key_piece", "0x400");
        jsonObject1.put("source_keys", new JSONArray(Arrays.asList("campaignCounts")));
        JSONObject jsonObject2 = new JSONObject();
        jsonObject2.put("key_piece", "0xA80");
        jsonObject2.put("source_keys", new JSONArray(Arrays.asList("geoValue", "noMatch")));
        triggerDatas.put(jsonObject1);
        triggerDatas.put(jsonObject2);

        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(triggerDatas.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("sourceId1")
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData("{\"product\":[\"1234\",\"2345\"]}")
                        .build();

        AggregateReport expectedAggregateReport =
                new AggregateReport.Builder()
                        .setApiVersion("0.1")
                        .setAttributionDestination(trigger.getAttributionDestination())
                        .setDebugCleartextPayload(
                                "{\"operation\":\"histogram\","
                                        + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                                        + "{\"bucket\":\"2693\",\"value\":1644}]}")
                        .setEnrollmentId(source.getEnrollmentId())
                        .setPublisher(source.getRegistrant())
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .setAggregateAttributionData(
                                new AggregateAttributionData.Builder()
                                        .setContributions(
                                                Arrays.asList(
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("1369"))
                                                                .setValue(32768)
                                                                .build(),
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("2693"))
                                                                .setValue(1644)
                                                                .build()))
                                        .build())
                        .build();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();
        ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao).updateSourceAggregateContributions(sourceArg.capture());
        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

        assertAggregateReportsEqual(expectedAggregateReport, aggregateReportCaptor.getValue());
        assertEquals(sourceArg.getValue().getAggregateContributions(), 32768 + 1644);
    }

    @Test
    public void performPendingAttributions_GeneratesAggregateReport_WithReportingOriginOfTrigger()
            throws JSONException, DatastoreException {
        JSONArray triggerDatas = new JSONArray();
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("key_piece", "0x400");
        jsonObject1.put("source_keys", new JSONArray(Arrays.asList("campaignCounts")));
        JSONObject jsonObject2 = new JSONObject();
        jsonObject2.put("key_piece", "0xA80");
        jsonObject2.put("source_keys", new JSONArray(Arrays.asList("geoValue", "noMatch")));
        triggerDatas.put(jsonObject1);
        triggerDatas.put(jsonObject2);

        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(triggerDatas.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
                        .build();

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("sourceId1")
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData("{\"product\":[\"1234\",\"2345\"]}")
                        .setRegistrationOrigin(WebUtil.validUri("https://source.example.test"))
                        .build();

        AggregateReport expectedAggregateReport =
                new AggregateReport.Builder()
                        .setApiVersion("0.1")
                        .setAttributionDestination(trigger.getAttributionDestination())
                        .setDebugCleartextPayload(
                                "{\"operation\":\"histogram\","
                                        + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                                        + "{\"bucket\":\"2693\",\"value\":1644}]}")
                        .setEnrollmentId(source.getEnrollmentId())
                        .setPublisher(source.getRegistrant())
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
                        .setAggregateAttributionData(
                                new AggregateAttributionData.Builder()
                                        .setContributions(
                                                Arrays.asList(
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("1369"))
                                                                .setValue(32768)
                                                                .build(),
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("2693"))
                                                                .setValue(1644)
                                                                .build()))
                                        .build())
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());
        assertAggregateReportsEqual(expectedAggregateReport, aggregateReportCaptor.getValue());
    }

    @Test
    public void shouldDoSimpleAttributionGenerateUnencryptedAggregateReportWithDedupKey()
            throws DatastoreException, JSONException {
        JSONArray triggerDatas = new JSONArray();
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("key_piece", "0x400");
        jsonObject1.put("source_keys", new JSONArray(Arrays.asList("campaignCounts")));
        JSONObject jsonObject2 = new JSONObject();
        jsonObject2.put("key_piece", "0xA80");
        jsonObject2.put("source_keys", new JSONArray(Arrays.asList("geoValue", "noMatch")));
        triggerDatas.put(jsonObject1);
        triggerDatas.put(jsonObject2);

        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(triggerDatas.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setAggregateDeduplicationKeys(AGGREGATE_DEDUPLICATION_KEYS_1)
                        .build();

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("sourceId1")
                        .setEventTime(SOURCE_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData("{\"product\":[\"1234\",\"2345\"]}")
                        .build();
        AggregateReport expectedAggregateReport =
                new AggregateReport.Builder()
                        .setApiVersion("0.1")
                        .setAttributionDestination(trigger.getAttributionDestination())
                        .setDebugCleartextPayload(
                                "{\"operation\":\"histogram\","
                                        + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                                        + "{\"bucket\":\"2693\",\"value\":1644}]}")
                        .setEnrollmentId(source.getEnrollmentId())
                        .setPublisher(source.getRegistrant())
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .setAggregateAttributionData(
                                new AggregateAttributionData.Builder()
                                        .setContributions(
                                                Arrays.asList(
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("1369"))
                                                                .setValue(32768)
                                                                .build(),
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("2693"))
                                                                .setValue(1644)
                                                                .build()))
                                        .build())
                        .build();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        mHandler.performPendingAttributions();
        ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao).updateSourceAggregateContributions(sourceArg.capture());
        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

        assertAggregateReportsEqual(expectedAggregateReport, aggregateReportCaptor.getValue());
        assertEquals(sourceArg.getValue().getAggregateContributions(), 32768 + 1644);
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao).updateSourceAggregateReportDedupKeys(sourceCaptor.capture());

        assertEquals(sourceCaptor.getValue().getAggregateReportDedupKeys().size(), 1);
        assertEquals(
                sourceCaptor.getValue().getAggregateReportDedupKeys().get(0),
                new UnsignedLong(10L));
    }

    @Test
    public void shouldNotGenerateAggregateReportWhenExceedingAggregateContributionsLimit()
            throws DatastoreException, JSONException {
        JSONArray triggerDatas = new JSONArray();
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("key_piece", "0x400");
        jsonObject1.put("source_keys", new JSONArray(Arrays.asList("campaignCounts")));
        JSONObject jsonObject2 = new JSONObject();
        jsonObject2.put("key_piece", "0xA80");
        jsonObject2.put("source_keys", new JSONArray(Arrays.asList("geoValue", "noMatch")));
        triggerDatas.put(jsonObject1);
        triggerDatas.put(jsonObject2);

        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setAggregateTriggerData(triggerDatas.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setEventTriggers(EVENT_TRIGGERS)
                        .build();

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData("{\"product\":[\"1234\",\"2345\"]}")
                        .setAggregateContributions(65536 - 32768 - 1644 + 1)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        mHandler.performPendingAttributions();
        verify(mMeasurementDao, never()).updateSourceAggregateContributions(any());
        verify(mMeasurementDao, never()).insertAggregateReport(any());
    }

    @Test
    public void performAttributions_triggerFilterSet_commonKeysDontIntersect_ignoreTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"]}, {\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1_x\", \"value_2_x\"],\n"
                                        + "  \"key_2\": [\"value_1_x\", \"value_2_x\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_triggerFilters_commonKeysDontIntersect_ignoreTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1_x\", \"value_2_x\"],\n"
                                        + "  \"key_2\": [\"value_1_x\", \"value_2_x\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_triggerNotFilterSet_commonKeysIntersect_ignoreTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setNotFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"]}, {\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2_x\"],\n"
                                        + "  \"key_2\": [\"value_1_x\", \"value_2\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_triggerNotFilters_commonKeysIntersect_ignoreTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setNotFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2_x\"],\n"
                                        + "  \"key_2\": [\"value_1_x\", \"value_2\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_triggerNotFilterSet_commonKeysDontIntersect_attributeTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setNotFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11_x\", \"value_12\"]}, {\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22_x\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                                        + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());

        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_triggerNotFiltersWithCommonKeysDontIntersect_attributeTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setNotFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11_x\", \"value_12\"],\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22_x\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                                        + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_topLevelFilterSetMatch_attributeTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11_x\", \"value_12\"]}, {\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                                        + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_triggerSourceFiltersWithCommonKeysIntersect_attributeTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                                        + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_commonKeysIntersect_attributeTrigger_debugApi()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                                        + "}]\n")
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                                        + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                                        + "}\n")
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));


        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_commonKeysIntersect_attributeTrigger_debugApi_sourceKey()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                                        + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                                        + "}\n")
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));


        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_commonKeysIntersect_attributeTrigger_debugApi_triggerKey()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                                        + "}]\n")
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                                        + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));


        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_triggerSourceFiltersWithNoCommonKeys_attributeTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1x\": [\"value_11_x\", \"value_12_x\"],\n"
                                        + "  \"key_2x\": [\"value_21_x\", \"value_22_x\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));


        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_eventLevelFilters_filterSet_attributeFirstMatchingTrigger()
            throws DatastoreException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"key_1\": [\"unmatched\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"ignored\": [\"ignored\"]}, {\n"
                                        + "    \"key_1\": [\"unmatched\"]}, {\n"
                                        + "    \"key_1\": [\"matched\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(triggerTime)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"matched\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setId("sourceId")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        EventReport expectedEventReport =
                new EventReport.Builder()
                        .setTriggerPriority(3L)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerTime(triggerTime)
                        .setSourceEventId(source.getEventId())
                        .setStatus(EventReport.Status.PENDING)
                        .setAttributionDestinations(source.getAppDestinations())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setReportTime(
                                mEventReportWindowCalcDelegate.getReportingTime(
                                        source,
                                        trigger.getTriggerTime(),
                                        trigger.getDestinationType()))
                        .setSourceType(source.getSourceType())
                        .setRandomizedTriggerRate(
                                mSourceNoiseHandler.getRandomAttributionProbability(source))
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("3"), List.of("3"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_eventLevelFilters_attributeFirstMatchingTrigger()
            throws DatastoreException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"key_1\": [\"value_1_x\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"key_1\": [\"value_1\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(triggerTime)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setId("sourceId")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        EventReport expectedEventReport =
                new EventReport.Builder()
                        .setTriggerPriority(3L)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerTime(triggerTime)
                        .setSourceEventId(source.getEventId())
                        .setStatus(EventReport.Status.PENDING)
                        .setAttributionDestinations(source.getAppDestinations())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setReportTime(
                                mEventReportWindowCalcDelegate.getReportingTime(
                                        source,
                                        trigger.getTriggerTime(),
                                        trigger.getDestinationType()))
                        .setSourceType(source.getSourceType())
                        .setRandomizedTriggerRate(
                                mSourceNoiseHandler.getRandomAttributionProbability(source))
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("3"), List.of("3"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_filterSet_eventLevelNotFilters_attributeFirstMatchingTrigger()
            throws DatastoreException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"not_filters\": [{\n"
                                        + "    \"key_1\": [\"value_1\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"not_filters\": [{\n"
                                        + "    \"key_1\": [\"value_1\"]}, {\n"
                                        + "    \"key_2\": [\"value_2\"]}, {\n"
                                        + "    \"key_1\": [\"matches_when_negated\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(triggerTime)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setId("sourceId")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        EventReport expectedEventReport =
                new EventReport.Builder()
                        .setTriggerPriority(3L)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerTime(triggerTime)
                        .setSourceEventId(source.getEventId())
                        .setStatus(EventReport.Status.PENDING)
                        .setAttributionDestinations(source.getAppDestinations())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setReportTime(
                                mEventReportWindowCalcDelegate.getReportingTime(
                                        source,
                                        trigger.getTriggerTime(),
                                        trigger.getDestinationType()))
                        .setSourceType(source.getSourceType())
                        .setRandomizedTriggerRate(
                                mSourceNoiseHandler.getRandomAttributionProbability(source))
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("3"), List.of("3"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_eventLevelNotFilters_attributeFirstMatchingTrigger()
            throws DatastoreException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"not_filters\": [{\n"
                                        + "    \"key_1\": [\"value_1\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"not_filters\": [{\n"
                                        + "    \"key_1\": [\"value_1_x\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(triggerTime)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setId("sourceId")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        EventReport expectedEventReport =
                new EventReport.Builder()
                        .setTriggerPriority(3L)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerTime(triggerTime)
                        .setSourceEventId(source.getEventId())
                        .setStatus(EventReport.Status.PENDING)
                        .setAttributionDestinations(source.getAppDestinations())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setReportTime(
                                mEventReportWindowCalcDelegate.getReportingTime(
                                        source,
                                        trigger.getTriggerTime(),
                                        trigger.getDestinationType()))
                        .setSourceType(source.getSourceType())
                        .setRandomizedTriggerRate(
                                mSourceNoiseHandler.getRandomAttributionProbability(source))
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("3"), List.of("3"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_eventLevelFiltersWithSourceType_attributeFirstMatchingTrigger()
            throws DatastoreException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"event\"], \n"
                                        + "    \"dummy_key\": [\"dummy_value\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"navigation\"], \n"
                                        + "    \"dummy_key\": [\"dummy_value\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(triggerTime)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setId("sourceId")
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        EventReport expectedEventReport =
                new EventReport.Builder()
                        .setTriggerPriority(3L)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setTriggerData(new UnsignedLong(3L))
                        .setTriggerTime(234324L)
                        .setSourceEventId(source.getEventId())
                        .setStatus(EventReport.Status.PENDING)
                        .setAttributionDestinations(source.getAppDestinations())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setReportTime(
                                mEventReportWindowCalcDelegate.getReportingTime(
                                        source,
                                        trigger.getTriggerTime(),
                                        trigger.getDestinationType()))
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRandomizedTriggerRate(
                                mSourceNoiseHandler.getRandomAttributionProbability(source))
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("3"), List.of("3"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_filterSet_eventLevelFiltersFailToMatch_aggregateReportOnly()
            throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"product\": [\"value_11\"]}, {\n"
                                        + "    \"key_1\": [\"value_11\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"product\": [\"value_21\"]}, {\n"
                                        + "    \"key_1\": [\"value_21\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\"product\":[\"1234\", \"2345\"],"
                                        + "\"key_1\": [\"value_1_y\", \"value_2_y\"]}")
                        .setId("sourceId")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        // Verify aggregate report registration origin.
        ArgumentCaptor<AggregateReport> reportArg = ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(reportArg.capture());
        AggregateReport aggregateReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, aggregateReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_eventLevelFiltersFailToMatch_generateAggregateReportOnly()
            throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"key_1\": [\"value_11\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"key_1\": [\"value_21\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\"product\":[\"1234\",\"2345\"], \"key_1\": "
                                        + "[\"value_1_y\", \"value_2_y\"]}")
                        .setId("sourceId")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void
            performAttribution_aggregateReportsExceedsLimitPerDestination_insertsOnlyEventReport()
                    throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1024;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao, never()).insertAggregateReport(any());
        verify(mMeasurementDao).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttribution_maxAggregateReportsPerSourceFlagDisabled_shouldGenerateReport()
            throws DatastoreException, JSONException {
        // Disable flag for max aggregate reports per source.
        when(mFlags.getMeasurementEnableMaxAggregateReportsPerSource()).thenReturn(false);
        when(mFlags.getMeasurementMaxAggregateReportsPerSource()).thenReturn(20);
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 10;
        int numEventReportPerDestination = 10;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mMeasurementDao.getNumAggregateReportsPerSource(source.getId())).thenReturn(21);

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttribution_aggregateReportsExceedsLimitPerSource_insertsOnlyEventReport()
            throws DatastoreException, JSONException {
        // Enable flag for max aggregate reports per source.
        when(mFlags.getMeasurementEnableMaxAggregateReportsPerSource()).thenReturn(true);
        when(mFlags.getMeasurementMaxAggregateReportsPerSource()).thenReturn(20);
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 10;
        int numEventReportPerDestination = 10;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mMeasurementDao.getNumAggregateReportsPerSource(source.getId())).thenReturn(21);

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao, never()).insertAggregateReport(any());
        verify(mDebugReportApi)
                .scheduleTriggerDebugReport(
                        any(),
                        any(),
                        eq("21"),
                        any(),
                        eq(DebugReportApi.Type.TRIGGER_AGGREGATE_EXCESSIVE_REPORTS));
        verify(mMeasurementDao).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttribution_eventReportsExceedsLimit_insertsOnlyAggregateReport()
            throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1024;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttribution_aggregateAndEventReportsExceedsLimit_noReportInsertion()
            throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1024;
        int numEventReportPerDestination = 1024;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertAggregateReport(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttribution_aggregateAndEventReportsDoNotExceedsLimit_ReportInsertion()
            throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao).insertAggregateReport(any());
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_withXnaConfig_originalSourceWinsAndOtherIgnored()
            throws DatastoreException {
        // Setup
        String adtechEnrollment = "AdTech1-Ads";
        AttributionConfig attributionConfig =
                new AttributionConfig.Builder()
                        .setSourceAdtech(adtechEnrollment)
                        .setSourcePriorityRange(new Pair<>(1L, 1000L))
                        .setSourceFilters(null)
                        .setPriority(1L)
                        .setExpiry(604800L)
                        .setFilterData(null)
                        .build();
        Trigger trigger =
                getXnaTriggerBuilder()
                        .setFilters(null)
                        .setNotFilters(null)
                        .setAttributionConfig(
                                new JSONArray(
                                                Collections.singletonList(
                                                        attributionConfig.serializeAsJson(mFlags)))
                                        .toString())
                        .build();

        String aggregatableSource = SourceFixture.ValidSourceParams.buildAggregateSource();
        Source xnaSource =
                createXnaSourceBuilder()
                        .setEnrollmentId(adtechEnrollment)
                        // Priority changes to 1 for derived source
                        .setPriority(100L)
                        .setAggregateSource(aggregatableSource)
                        .setFilterData(null)
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("campaignCounts", "geoValue"))
                                        .toString())
                        .build();
        // winner due to install attribution and higher priority
        Source triggerEnrollmentSource1 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterData(null)
                        .setAggregateSource(aggregatableSource)
                        .build();

        Source triggerEnrollmentSource2 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterData(null)
                        .setAggregateSource(aggregatableSource)
                        .setInstallAttributed(false)
                        .build();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(xnaSource);
        matchingSourceList.add(triggerEnrollmentSource1);
        matchingSourceList.add(triggerEnrollmentSource2);
        when(mMeasurementDao.fetchTriggerMatchingSourcesForXna(any(), any()))
                .thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(triggerEnrollmentSource1.getId()))
                .thenReturn(
                        Pair.create(
                                triggerEnrollmentSource1.getAppDestinations(),
                                triggerEnrollmentSource1.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

        // Execution
        boolean result = mHandler.performPendingAttributions();

        // Assertion
        assertTrue(result);
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao).insertEventReport(any());
        verify(mMeasurementDao).insertAttribution(any());
        verify(mMeasurementDao)
                .insertIgnoredSourceForEnrollment(xnaSource.getId(), trigger.getEnrollmentId());
        verify(mMeasurementDao)
                .updateSourceStatus(
                        eq(Collections.singletonList(triggerEnrollmentSource2.getId())),
                        eq(Source.Status.IGNORED));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_withXnaConfig_derivedSourceWinsAndOtherIgnored()
            throws DatastoreException {
        // Setup
        String adtechEnrollment = "AdTech1-Ads";
        AttributionConfig attributionConfig =
                new AttributionConfig.Builder()
                        .setExpiry(604800L)
                        .setSourceAdtech(adtechEnrollment)
                        .setSourcePriorityRange(new Pair<>(1L, 1000L))
                        .setSourceFilters(null)
                        .setPriority(50L)
                        .setExpiry(604800L)
                        .setFilterData(null)
                        .build();
        Trigger trigger =
                getXnaTriggerBuilder()
                        .setFilters(null)
                        .setNotFilters(null)
                        .setAttributionConfig(
                                new JSONArray(
                                                Collections.singletonList(
                                                        attributionConfig.serializeAsJson(mFlags)))
                                        .toString())
                        .build();

        String aggregatableSource = SourceFixture.ValidSourceParams.buildAggregateSource();
        // Its derived source will be winner due to install attribution and higher priority
        Source xnaSource =
                createXnaSourceBuilder()
                        .setEnrollmentId(adtechEnrollment)
                        // Priority changes to 50 for derived source
                        .setPriority(1L)
                        .setAggregateSource(aggregatableSource)
                        .setFilterData(null)
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("campaignCounts", "geoValue"))
                                        .toString())
                        .build();
        Source triggerEnrollmentSource1 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterData(null)
                        .setAggregateSource(aggregatableSource)
                        .build();

        Source triggerEnrollmentSource2 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterData(null)
                        .setAggregateSource(aggregatableSource)
                        .setInstallAttributed(false)
                        .build();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(xnaSource);
        matchingSourceList.add(triggerEnrollmentSource1);
        matchingSourceList.add(triggerEnrollmentSource2);
        when(mMeasurementDao.fetchTriggerMatchingSourcesForXna(any(), any()))
                .thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

        // Execution
        boolean result = mHandler.performPendingAttributions();

        // Assertion
        assertTrue(result);
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        // Verify aggregate report registration origin.
        ArgumentCaptor<AggregateReport> reportArg = ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(reportArg.capture());
        AggregateReport aggregateReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, aggregateReport.getRegistrationOrigin());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao).insertAttribution(any());
        verify(mMeasurementDao)
                .updateSourceStatus(
                        eq(
                                Arrays.asList(
                                        triggerEnrollmentSource1.getId(),
                                        triggerEnrollmentSource2.getId())),
                        eq(Source.Status.IGNORED));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_xnaDisabled_derivedSourceIgnored() throws DatastoreException {
        // Setup
        String adtechEnrollment = "AdTech1-Ads";
        AttributionConfig attributionConfig =
                new AttributionConfig.Builder()
                        .setExpiry(604800L)
                        .setSourceAdtech(adtechEnrollment)
                        .setSourcePriorityRange(new Pair<>(1L, 1000L))
                        .setSourceFilters(null)
                        .setPriority(50L)
                        .setExpiry(604800L)
                        .setFilterData(null)
                        .build();
        Trigger trigger =
                getXnaTriggerBuilder()
                        .setFilters(null)
                        .setNotFilters(null)
                        .setAttributionConfig(
                                new JSONArray(
                                                Collections.singletonList(
                                                        attributionConfig.serializeAsJson(mFlags)))
                                        .toString())
                        .build();

        String aggregatableSource = SourceFixture.ValidSourceParams.buildAggregateSource();
        // Its derived source will be winner due to install attribution and higher priority
        Source xnaSource =
                createXnaSourceBuilder()
                        .setEnrollmentId(adtechEnrollment)
                        // Priority changes to 50 for derived source
                        .setPriority(1L)
                        .setAggregateSource(aggregatableSource)
                        .setFilterData(null)
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("campaignCounts", "geoValue"))
                                        .toString())
                        .build();
        Source triggerEnrollmentSource1 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterData(null)
                        .setAggregateSource(aggregatableSource)
                        .build();

        Source triggerEnrollmentSource2 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterData(null)
                        .setAggregateSource(aggregatableSource)
                        .setInstallAttributed(false)
                        .build();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(xnaSource);
        matchingSourceList.add(triggerEnrollmentSource1);
        matchingSourceList.add(triggerEnrollmentSource2);
        when(mMeasurementDao.fetchTriggerMatchingSourcesForXna(any(), any()))
                .thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mFlags.getMeasurementEnableXNA()).thenReturn(false);

        // Execution
        boolean result = mHandler.performPendingAttributions();

        // Assertion
        assertTrue(result);
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertAggregateReport(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, never()).insertAttribution(any());
        verify(mMeasurementDao, never())
                .updateSourceStatus(
                        eq(
                                Arrays.asList(
                                        triggerEnrollmentSource1.getId(),
                                        triggerEnrollmentSource2.getId())),
                        eq(Source.Status.IGNORED));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttribution_flexEventReport_oneTriggerGenerateTwoReports()
            throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"1\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"1000\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        ReportSpec reportSpec = SourceFixture.getValidReportSpecValueSum();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .setTriggerSpecs(reportSpec.encodeTriggerSpecsToJson())
                        .setMaxEventLevelReports(reportSpec.getMaxReports())
                        .setPrivacyParameters(reportSpec.encodePrivacyParametersToJSONString())
                        .build();
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, times(2)).insertEventReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, times(1)).getSourceEventReports(any());
        ArgumentCaptor<ReportSpec> updatedReportSpec = ArgumentCaptor.forClass(ReportSpec.class);
        verify(mMeasurementDao, times(1)).updateSourceAttributedTriggers(
                eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
        assertEquals(1, source.getFlexEventReportSpec().getAttributedTriggers().size());
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    /**
     * The triggerData in the trigger didn't match any one of the trigger data in the source
     * registration. No report generated
     */
    public void performAttribution_flexEventReport_triggerDataMismatch()
            throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"6\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"1000\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        ReportSpec reportSpec = SourceFixture.getValidReportSpecValueSum();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .setTriggerSpecs(reportSpec.encodeTriggerSpecsToJson())
                        .setMaxEventLevelReports(reportSpec.getMaxReports())
                        .setPrivacyParameters(reportSpec.encodePrivacyParametersToJSONString())
                        .build();
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, never()).getSourceEventReports(any());
        verify(mMeasurementDao, never()).updateSourceAttributedTriggers(anyString(), anyString());
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    /**
     * Status before attribution: 1 trigger attributed and 1 report generated; Incoming trigger
     * status: 2 reports should be generated; Result: 2 reports written into DB and no competition
     * condition.
     */
    public void performAttribution_flexEventReport_insertSecondTriggerNoCompeting()
            throws DatastoreException, JSONException {

        // Setup
        long baseTime = System.currentTimeMillis();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"105\",\n"
                                        + "  \"deduplication_key\": \"123\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(1)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        final EventReport currentEventReport1 =
                new EventReport.Builder()
                        .setId("100")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + 3000)
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(123L)
                        .setTriggerValue(30)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .build();

        ReportSpec templateReportSpec = SourceFixture.getValidReportSpecValueSum();
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = generateTriggerJSONFromEventReport(currentEventReport1);

        existingAttributes.put(triggerRecord1);

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecs(templateReportSpec.encodeTriggerSpecsToJson())
                        .setMaxEventLevelReports(templateReportSpec.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateReportSpec.encodePrivacyParametersToJSONString())
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any()))
                .thenReturn(new ArrayList<>(Collections.singletonList(currentEventReport1)));
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, times(2)).insertEventReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, never()).deleteEventReport(any());
        verify(mMeasurementDao, times(1)).getSourceEventReports(any());
        ArgumentCaptor<ReportSpec> updatedReportSpec = ArgumentCaptor.forClass(ReportSpec.class);
        verify(mMeasurementDao, times(1)).updateSourceAttributedTriggers(
                eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
        assertEquals(2, source.getFlexEventReportSpec().getAttributedTriggers().size());
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    /**
     * Status before attribution: 1 trigger attributed and 2 report generated with triggerData 1;
     * Incoming trigger status: 2 reports should be generated for triggerData 2 Result: incoming
     * trigger has higher priority and one previous report should be deleted
     */
    public void performAttribution_flexEventReport_insertSecondTriggerCompetingHigherPriority()
            throws DatastoreException, JSONException {

        // Setup
        long baseTime = System.currentTimeMillis();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"105\",\n"
                                        + "  \"deduplication_key\": \"1234\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1) + 4800000)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        final EventReport currentEventReport1 =
                new EventReport.Builder()
                        .setId("100")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(121L)
                        .setTriggerValue(101)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .build();

        ReportSpec templateReportSpec = SourceFixture.getValidReportSpecValueSum();
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = generateTriggerJSONFromEventReport(currentEventReport1);

        existingAttributes.put(triggerRecord1);

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(
                                new ArrayList<>(Collections.singletonList(new UnsignedLong(3L))))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventTime(baseTime)
                        .setEventReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecs(templateReportSpec.encodeTriggerSpecsToJson())
                        .setMaxEventLevelReports(templateReportSpec.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateReportSpec.encodePrivacyParametersToJSONString())
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any()))
                .thenReturn(
                        new ArrayList<>(Arrays.asList(currentEventReport1, currentEventReport1)));
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, times(1)).getSourceEventReports(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        ArgumentCaptor<EventReport> reportArgInsertedReport =
                ArgumentCaptor.forClass(EventReport.class);
        ArgumentCaptor<EventReport> reportArgDeletedReport =
                ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao, times(2)).insertEventReport(reportArgInsertedReport.capture());
        verify(mMeasurementDao).deleteEventReport(reportArgDeletedReport.capture());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
        assertEquals(
                2L,
                reportArgInsertedReport
                        .getAllValues()
                        .get(0)
                        .getTriggerData()
                        .getValue()
                        .longValue());

        assertEquals(105L, reportArgInsertedReport.getAllValues().get(0).getTriggerValue());
        assertEquals(123L, reportArgInsertedReport.getAllValues().get(0).getTriggerPriority());
        assertEquals(2, reportArgInsertedReport.getAllValues().size());
        assertEquals(1, reportArgDeletedReport.getAllValues().size());
        assertEquals(
                1L,
                reportArgDeletedReport
                        .getAllValues()
                        .get(0)
                        .getTriggerData()
                        .getValue()
                        .longValue());
        assertEquals(
                TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                reportArgDeletedReport.getAllValues().get(0).getReportTime() - baseTime);
        assertEquals(
                TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                reportArgInsertedReport.getAllValues().get(0).getReportTime() - baseTime);
        ArgumentCaptor<ReportSpec> updatedReportSpec = ArgumentCaptor.forClass(ReportSpec.class);
        verify(mMeasurementDao, times(1)).updateSourceAttributedTriggers(
                eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
        assertEquals(2, source.getFlexEventReportSpec().getAttributedTriggers().size());
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    /**
     * Status before attribution: 1 trigger attributed and 2 report generated with triggerData 1;
     * Incoming trigger status: 2 reports should be generated for triggerData 2 Result: incoming
     * trigger has lower priority, no previous report should be deleted and only 1 new report
     * inserted into DB
     */
    public void performAttribution_flexEventReport_insertSecondTriggerCompetingLowerPriority()
            throws DatastoreException, JSONException {

        // Setup
        long baseTime = System.currentTimeMillis();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"105\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(1)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        final EventReport currentEventReport1 =
                new EventReport.Builder()
                        .setId("100")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + 3000)
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(124L)
                        .setTriggerValue(103)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .build();

        ReportSpec templateReportSpec = SourceFixture.getValidReportSpecValueSum();
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = generateTriggerJSONFromEventReport(currentEventReport1);

        existingAttributes.put(triggerRecord1);

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(
                                new ArrayList<>(Collections.singleton(new UnsignedLong(3L))))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventTime(baseTime)
                        .setEventReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecs(templateReportSpec.encodeTriggerSpecsToJson())
                        .setMaxEventLevelReports(templateReportSpec.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateReportSpec.encodePrivacyParametersToJSONString())
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any()))
                .thenReturn(
                        new ArrayList<>(Arrays.asList(currentEventReport1, currentEventReport1)));
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).deleteEventReport(any());
        verify(mMeasurementDao, times(1)).insertEventReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, times(1)).getSourceEventReports(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
        ArgumentCaptor<EventReport> reportArgInsertedReport =
                ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao, times(1)).insertEventReport(reportArgInsertedReport.capture());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
        assertEquals(
                2L,
                reportArgInsertedReport
                        .getAllValues()
                        .get(0)
                        .getTriggerData()
                        .getValue()
                        .longValue());
        assertEquals(
                TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                reportArgInsertedReport.getAllValues().get(0).getReportTime() - baseTime);
        verify(mMeasurementDao, times(1)).updateSourceAttributedTriggers(
                eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
        assertEquals(2, source.getFlexEventReportSpec().getAttributedTriggers().size());
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    /**
     * Status before attribution: 2 trigger attributed and 2 report generated with triggerData 1 and
     * 2, respectively; Incoming trigger status: 2 reports should be generated for triggerData 1
     * Result: incoming trigger has higher priority so previous report with triggerData 1 is reset
     * to higher priority so the report with triggerData 2 is deleted.
     */
    public void performAttribution_flexEventReport_insertThirdTriggerPriorityReset()
            throws DatastoreException, JSONException {

        // Setup
        long baseTime = System.currentTimeMillis();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"1\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"105\",\n"
                                        + "  \"deduplication_key\": \"111\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(1)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        final EventReport currentEventReport1 =
                new EventReport.Builder()
                        .setId("100")
                        .setTriggerId("01234")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(50)
                        .setTriggerValue(20)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .build();
        final EventReport currentEventReport2 =
                new EventReport.Builder()
                        .setId("101")
                        .setTriggerId("12345")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1) + 1000)
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(60)
                        .setTriggerValue(30)
                        .setTriggerDedupKey(new UnsignedLong(1233L))
                        .build();

        ReportSpec templateReportSpec = new ReportSpec(
                SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(), "2", null);

        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = generateTriggerJSONFromEventReport(currentEventReport1);
        JSONObject triggerRecord2 = generateTriggerJSONFromEventReport(currentEventReport2);

        existingAttributes.put(triggerRecord1);
        existingAttributes.put(triggerRecord2);

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(
                                new ArrayList<>(
                                        Arrays.asList(
                                                new UnsignedLong(3L), new UnsignedLong(1233L))))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventTime(baseTime)
                        .setEventReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecs(templateReportSpec.encodeTriggerSpecsToJson())
                        .setMaxEventLevelReports(templateReportSpec.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateReportSpec.encodePrivacyParametersToJSONString())
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any()))
                .thenReturn(
                        new ArrayList<>(Arrays.asList(currentEventReport1, currentEventReport2)));
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, times(1)).getSourceEventReports(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();

        ArgumentCaptor<EventReport> reportArgInsertedReport =
                ArgumentCaptor.forClass(EventReport.class);
        ArgumentCaptor<EventReport> reportArgDeletedReport =
                ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao, times(1)).insertEventReport(reportArgInsertedReport.capture());
        verify(mMeasurementDao, times(1)).deleteEventReport(reportArgDeletedReport.capture());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
        assertEquals(
                1L,
                reportArgInsertedReport
                        .getAllValues()
                        .get(0)
                        .getTriggerData()
                        .getValue()
                        .longValue());
        assertEquals(
                2L,
                reportArgDeletedReport
                        .getAllValues()
                        .get(0)
                        .getTriggerData()
                        .getValue()
                        .longValue());
        assertEquals(
                TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                reportArgDeletedReport.getAllValues().get(0).getReportTime() - baseTime);
        assertEquals(
                TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                reportArgInsertedReport.getAllValues().get(0).getReportTime() - baseTime);
        verify(mMeasurementDao, times(1)).updateSourceAttributedTriggers(
                eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
        assertEquals(3, source.getFlexEventReportSpec().getAttributedTriggers().size());
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    public void performAttribution_flexEventReport_notReportDueToDedup()
            throws DatastoreException, JSONException {

        // Setup
        long baseTime = System.currentTimeMillis();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"1\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"105\",\n"
                                        + "  \"deduplication_key\": \"111\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(1)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        final EventReport currentEventReport1 =
                new EventReport.Builder()
                        .setId("100")
                        .setTriggerId("01234")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(50)
                        .setTriggerValue(20)
                        .setTriggerDedupKey(new UnsignedLong(111L))
                        .build();
        final EventReport currentEventReport2 =
                new EventReport.Builder()
                        .setId("101")
                        .setTriggerId("12345")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1) + 1000)
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(60)
                        .setTriggerValue(30)
                        .setTriggerDedupKey(new UnsignedLong(123L))
                        .build();

        ReportSpec templateReportSpec = new ReportSpec(
                SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(), "2", null);
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = generateTriggerJSONFromEventReport(currentEventReport1);
        JSONObject triggerRecord2 = generateTriggerJSONFromEventReport(currentEventReport2);

        existingAttributes.put(triggerRecord1);
        existingAttributes.put(triggerRecord2);

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(
                                new ArrayList<>(
                                        Arrays.asList(
                                                new UnsignedLong(111L), new UnsignedLong(123L))))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventTime(baseTime)
                        .setEventReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecs(templateReportSpec.encodeTriggerSpecsToJson())
                        .setMaxEventLevelReports(templateReportSpec.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateReportSpec.encodePrivacyParametersToJSONString())
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any()))
                .thenReturn(
                        new ArrayList<>(Arrays.asList(currentEventReport1, currentEventReport2)));
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, never()).getSourceEventReports(any());

        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, never()).deleteEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();

        verify(mMeasurementDao, never()).updateSourceAttributedTriggers(anyString(), anyString());
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    public void performAttribution_flexEventReport_dedupKeyInserted()
            throws DatastoreException, JSONException {

        // Setup
        long baseTime = System.currentTimeMillis();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"1\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"1\",\n"
                                        + "  \"deduplication_key\": \"111\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(1)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        ReportSpec templateReportSpec = new ReportSpec(
                SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(), "2", null);
        JSONArray existingAttributes = new JSONArray();

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(new ArrayList<>())
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventTime(baseTime)
                        .setEventReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecs(templateReportSpec.encodeTriggerSpecsToJson())
                        .setMaxEventLevelReports(templateReportSpec.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateReportSpec.encodePrivacyParametersToJSONString())
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, never()).getSourceEventReports(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, never()).deleteEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();

        verify(mMeasurementDao, times(1)).updateSourceAttributedTriggers(
                eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    public void performAttribution_flexEventReport_dedupKeyInserted_dedupAlignflagOff()
            throws DatastoreException, JSONException {

        // Setup
        long baseTime = System.currentTimeMillis();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"1\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"1\",\n"
                                        + "  \"deduplication_key\": \"111\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(1)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        ReportSpec templateReportSpec = new ReportSpec(
                SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(), "2", null);
        JSONArray existingAttributes = new JSONArray();

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(new ArrayList<>())
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterData(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventTime(baseTime)
                        .setEventReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecs(templateReportSpec.encodeTriggerSpecsToJson())
                        .setMaxEventLevelReports(templateReportSpec.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateReportSpec.encodePrivacyParametersToJSONString())
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(false);

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, never()).getSourceEventReports(any());
        verify(mMeasurementDao, times(1)).updateSourceEventReportDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, never()).deleteEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();

        verify(mMeasurementDao, times(1)).updateSourceAttributedTriggers(
                eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    public void performAttributions_withXnaConfig_derivedSourceWinsAndIsLogged()
            throws DatastoreException {
        // Setup
        String adtechEnrollment = "AdTech1-Ads";
        AttributionConfig attributionConfig =
                new AttributionConfig.Builder()
                        .setExpiry(604800L)
                        .setSourceAdtech(adtechEnrollment)
                        .setSourcePriorityRange(new Pair<>(1L, 1000L))
                        .setSourceFilters(null)
                        .setPriority(50L)
                        .setExpiry(604800L)
                        .setFilterData(null)
                        .build();
        Trigger trigger =
                getXnaTriggerBuilder()
                        .setFilters(null)
                        .setNotFilters(null)
                        .setAttributionConfig(
                                new JSONArray(
                                                Collections.singletonList(
                                                        attributionConfig.serializeAsJson(mFlags)))
                                        .toString())
                        .build();

        String aggregatableSource = SourceFixture.ValidSourceParams.buildAggregateSource();
        // Its derived source will be winner due to install attribution and higher priority
        Source xnaSource =
                createXnaSourceBuilder()
                        .setEnrollmentId(adtechEnrollment)
                        // Priority changes to 50 for derived source
                        .setPriority(1L)
                        .setAggregateSource(aggregatableSource)
                        .setFilterData(null)
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("campaignCounts", "geoValue"))
                                        .toString())
                        .build();
        Source triggerEnrollmentSource1 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterData(null)
                        .setAggregateSource(aggregatableSource)
                        .build();

        Source triggerEnrollmentSource2 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterData(null)
                        .setAggregateSource(aggregatableSource)
                        .setInstallAttributed(false)
                        .build();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(xnaSource);
        matchingSourceList.add(triggerEnrollmentSource1);
        matchingSourceList.add(triggerEnrollmentSource2);
        when(mMeasurementDao.fetchTriggerMatchingSourcesForXna(any(), any()))
                .thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

        // Execution
        boolean result = mHandler.performPendingAttributions();

        // Assertion
        assertTrue(result);
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        ArgumentCaptor<MeasurementAttributionStats> statusArg =
                ArgumentCaptor.forClass(MeasurementAttributionStats.class);
        verify(mLogger).logMeasurementAttributionStats(statusArg.capture());
        MeasurementAttributionStats measurementAttributionStats = statusArg.getValue();
        assertTrue(measurementAttributionStats.isSourceDerived());
    }

    @Test
    public void performAttributions_withinLookbackWindow_attributeTrigger()
            throws DatastoreException {
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"event\"], \n"
                                        + "    \"dummy_key\": [\"dummy_value\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"navigation\"], \n"
                                        + "    \"dummy_key\": [\"dummy_value\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(TRIGGER_TIME)
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                                        // Set Lookback window to be greater than duration from
                                        // source to trigger time.
                                        + "  \"_lookback_window\": 1000\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n" + "  \"key_1\": [\"value_11\", \"value_12\"]\n" + "}\n")
                        .setId("sourceId")
                        .setEventTime(TRIGGER_TIME - TimeUnit.SECONDS.toMillis(LOOKBACK_WINDOW - 1))
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(TRIGGER_TIME + 1)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1)
                        .build();
        EventReport expectedEventReport =
                new EventReport.Builder()
                        .setTriggerPriority(3L)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setTriggerData(new UnsignedLong(3L))
                        .setTriggerTime(trigger.getTriggerTime())
                        .setSourceEventId(source.getEventId())
                        .setStatus(EventReport.Status.PENDING)
                        .setAttributionDestinations(source.getAppDestinations())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setReportTime(
                                mEventReportWindowCalcDelegate.getReportingTime(
                                        source,
                                        trigger.getTriggerTime(),
                                        trigger.getDestinationType()))
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRandomizedTriggerRate(
                                mSourceNoiseHandler.getRandomAttributionProbability(source))
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus =
                getAttributionStatus(List.of(trigger.getId()), List.of("3"), List.of("3"));
        verify(mMeasurementDao)
                .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
    }

    @Test
    public void performAttributions_outsideLookbackWindow_noAttributionTrigger()
            throws DatastoreException {
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"event\"], \n"
                                        + "    \"dummy_key\": [\"dummy_value\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"navigation\"], \n"
                                        + "    \"dummy_key\": [\"dummy_value\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(TRIGGER_TIME)
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                                        // Set Lookback window to be smaller than duration from
                                        // source to trigger time.
                                        + "  \"_lookback_window\": 1000\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterData(
                                "{\n" + "  \"key_1\": [\"value_11\", \"value_12\"]\n" + "}\n")
                        .setId("sourceId")
                        .setEventTime(TRIGGER_TIME - TimeUnit.SECONDS.toMillis(LOOKBACK_WINDOW + 1))
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(TRIGGER_TIME + 1)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1)
                        .build();
        EventReport expectedEventReport =
                new EventReport.Builder()
                        .setTriggerPriority(2L)
                        .setTriggerDedupKey(new UnsignedLong(2L))
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerTime(trigger.getTriggerTime())
                        .setSourceEventId(source.getEventId())
                        .setStatus(EventReport.Status.PENDING)
                        .setAttributionDestinations(source.getAppDestinations())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setReportTime(
                                mEventReportWindowCalcDelegate.getReportingTime(
                                        source,
                                        trigger.getTriggerTime(),
                                        trigger.getDestinationType()))
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRandomizedTriggerRate(
                                mSourceNoiseHandler.getRandomAttributionProbability(source))
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceAttributedTriggers(any(), any());
        verify(mMeasurementDao, never()).insertEventReport(eq(expectedEventReport));
    }

    public static Trigger.Builder getXnaTriggerBuilder() {
        return new Trigger.Builder()
                .setId(UUID.randomUUID().toString())
                .setAttributionDestination(
                        TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION)
                .setEnrollmentId(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID)
                .setRegistrant(TriggerFixture.ValidTriggerParams.REGISTRANT)
                .setTriggerTime(TriggerFixture.ValidTriggerParams.TRIGGER_TIME)
                .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                .setAggregateTriggerData(TriggerFixture.ValidTriggerParams.AGGREGATE_TRIGGER_DATA)
                .setAggregateValues(TriggerFixture.ValidTriggerParams.AGGREGATE_VALUES)
                .setFilters(TriggerFixture.ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING)
                .setNotFilters(TriggerFixture.ValidTriggerParams.TOP_LEVEL_NOT_FILTERS_JSON_STRING)
                .setAttributionConfig(TriggerFixture.ValidTriggerParams.ATTRIBUTION_CONFIGS_STRING)
                .setAdtechBitMapping(TriggerFixture.ValidTriggerParams.X_NETWORK_KEY_MAPPING)
                .setRegistrationOrigin(TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN);
    }

    private Source.Builder createXnaSourceBuilder() {
        return new Source.Builder()
                .setId(UUID.randomUUID().toString())
                .setEventId(SourceFixture.ValidSourceParams.SOURCE_EVENT_ID)
                .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                .setAppDestinations(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                .setEnrollmentId(SourceFixture.ValidSourceParams.ENROLLMENT_ID)
                .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
                .setEventTime(SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME)
                .setExpiryTime(SourceFixture.ValidSourceParams.EXPIRY_TIME)
                .setPriority(SourceFixture.ValidSourceParams.PRIORITY)
                .setSourceType(SourceFixture.ValidSourceParams.SOURCE_TYPE)
                .setInstallAttributionWindow(
                        SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW)
                .setInstallCooldownWindow(SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW)
                .setAttributionMode(SourceFixture.ValidSourceParams.ATTRIBUTION_MODE)
                .setAggregateSource(SourceFixture.ValidSourceParams.buildAggregateSource())
                .setFilterData(buildMatchingFilterData())
                .setIsDebugReporting(true)
                .setRegistrationId(SourceFixture.ValidSourceParams.REGISTRATION_ID)
                .setSharedAggregationKeys(SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS)
                .setInstallTime(SourceFixture.ValidSourceParams.INSTALL_TIME)
                .setAggregatableReportWindow(SourceFixture.ValidSourceParams.EXPIRY_TIME)
                .setRegistrationOrigin(SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN)
                .setInstallAttributed(true);
    }

    private String buildMatchingFilterData() {
        try {
            JSONObject filterMap = new JSONObject();
            filterMap.put(
                    "conversion_subdomain",
                    new JSONArray(Collections.singletonList("electronics.megastore")));
            return filterMap.toString();
        } catch (JSONException e) {
            LogUtil.e("JSONException when building aggregate filter data.");
        }
        return null;
    }

    private JSONArray buildAggregateTriggerData() throws JSONException {
        JSONArray triggerDatas = new JSONArray();
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("key_piece", "0x400");
        jsonObject1.put("source_keys", new JSONArray(Arrays.asList("campaignCounts")));
        jsonObject1.put("filters", createFilterJSONArray());
        jsonObject1.put("not_filters", createFilterJSONArray());
        JSONObject jsonObject2 = new JSONObject();
        jsonObject2.put("key_piece", "0xA80");
        jsonObject2.put("source_keys", new JSONArray(Arrays.asList("geoValue", "noMatch")));
        triggerDatas.put(jsonObject1);
        triggerDatas.put(jsonObject2);
        return triggerDatas;
    }

    private JSONArray createFilterJSONArray() throws JSONException {
        JSONObject filterMap = new JSONObject();
        filterMap.put("conversion_subdomain",
                new JSONArray(Arrays.asList("electronics.megastore")));
        filterMap.put("product", new JSONArray(Arrays.asList("1234", "2345")));
        JSONArray filterSet = new JSONArray();
        filterSet.put(filterMap);
        return filterSet;
    }

    private void assertAggregateReportsEqual(
            AggregateReport expectedReport, AggregateReport actualReport) {
        // Avoids checking report time because there is randomization
        assertEquals(expectedReport.getApiVersion(), actualReport.getApiVersion());
        assertEquals(
                expectedReport.getAttributionDestination(),
                actualReport.getAttributionDestination());
        assertEquals(
                expectedReport.getDebugCleartextPayload(), actualReport.getDebugCleartextPayload());
        assertEquals(expectedReport.getEnrollmentId(), actualReport.getEnrollmentId());
        assertEquals(expectedReport.getPublisher(), actualReport.getPublisher());
        assertEquals(expectedReport.getSourceId(), actualReport.getSourceId());
        assertEquals(expectedReport.getTriggerId(), actualReport.getTriggerId());
        assertEquals(
                expectedReport.getAggregateAttributionData(),
                actualReport.getAggregateAttributionData());
        assertEquals(expectedReport.getSourceDebugKey(), actualReport.getSourceDebugKey());
        assertEquals(expectedReport.getTriggerDebugKey(), actualReport.getTriggerDebugKey());
        assertEquals(expectedReport.getRegistrationOrigin(), actualReport.getRegistrationOrigin());
    }

    private static Trigger getAggregateTrigger() throws JSONException {
        JSONArray triggerDatas = new JSONArray();
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("key_piece", "0x400");
        jsonObject1.put("source_keys", new JSONArray(Arrays.asList("campaignCounts")));
        JSONObject jsonObject2 = new JSONObject();
        jsonObject2.put("key_piece", "0xA80");
        jsonObject2.put("source_keys", new JSONArray(Arrays.asList("geoValue", "noMatch")));
        triggerDatas.put(jsonObject1);
        triggerDatas.put(jsonObject2);

        return TriggerFixture.getValidTriggerBuilder()
                .setId("triggerId1")
                .setTriggerTime(TRIGGER_TIME)
                .setStatus(Trigger.Status.PENDING)
                .setEventTriggers(
                        "[\n"
                                + "{\n"
                                + "  \"trigger_data\": \"5\",\n"
                                + "  \"priority\": \"123\",\n"
                                + "  \"deduplication_key\": \"1\"\n"
                                + "}"
                                + "]\n")
                .setAggregateTriggerData(triggerDatas.toString())
                .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                .build();
    }

    private static Source getAggregateSource() {
        return SourceFixture.getMinimalValidSourceBuilder()
                .setId("sourceId1")
                .setEventTime(SOURCE_TIME)
                .setExpiryTime(EXPIRY_TIME)
                .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                .setFilterData("{\"product\":[\"1234\",\"2345\"]}")
                .build();
    }

    private static JSONObject generateTriggerJSONFromEventReport(EventReport eventReport)
            throws JSONException {
        JSONObject triggerRecord = new JSONObject();
        triggerRecord.put("trigger_id", eventReport.getId());
        triggerRecord.put("value", eventReport.getTriggerValue());
        triggerRecord.put("priority", eventReport.getTriggerPriority());
        triggerRecord.put("trigger_time", eventReport.getTriggerTime());
        triggerRecord.put("trigger_data", eventReport.getTriggerData());
        triggerRecord.put("dedup_key", eventReport.getTriggerDedupKey());
        return triggerRecord;
    }

    private static String getAttributionStatus(List<String> triggerIds, List<String> triggerData,
            List<String> dedupKeys) {
        try {
            JSONArray attributionStatus = new JSONArray();
            for (int i = 0; i < triggerIds.size(); i++) {
                attributionStatus.put(
                        new JSONObject()
                                .put("trigger_id", triggerIds.get(i))
                                .put("trigger_data", triggerData.get(i))
                                .put("dedup_key", dedupKeys.get(i)));
            }
            return attributionStatus.toString();
        } catch (JSONException ignored) {
            return null;
        }
    }
}
