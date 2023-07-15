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

package com.android.adservices.data.measurement.deletion;

import static com.android.adservices.data.measurement.deletion.MeasurementDataDeleter.ANDROID_APP_SCHEME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.net.Uri;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventReportFixture;
import com.android.adservices.service.measurement.ReportSpec;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.aggregation.AggregatableAttributionSource;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.aggregation.AggregateReportFixture;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDataDeleterTest {
    private static final List<AggregateHistogramContribution> CONTRIBUTIONS_1 =
            Arrays.asList(
                    new AggregateHistogramContribution.Builder()
                            .setKey(new BigInteger("10"))
                            .setValue(45)
                            .build(),
                    new AggregateHistogramContribution.Builder()
                            .setKey(new BigInteger("100"))
                            .setValue(87)
                            .build());


    private static final List<AggregateHistogramContribution> CONTRIBUTIONS_2 =
            Arrays.asList(
                    new AggregateHistogramContribution.Builder()
                            .setKey(new BigInteger("500"))
                            .setValue(2000)
                            .build(),
                    new AggregateHistogramContribution.Builder()
                            .setKey(new BigInteger("10000"))
                            .setValue(3454)
                            .build());

    private static final AggregateReport AGGREGATE_REPORT_1;
    private static final AggregateReport AGGREGATE_REPORT_2;

    static {
        AggregateReport localAggregateReport1;
        AggregateReport localAggregateReport2;
        try {
            localAggregateReport1 =
                    AggregateReportFixture.getValidAggregateReportBuilder()
                            .setId("reportId1")
                            .setDebugCleartextPayload(
                                    AggregateReport.generateDebugPayload(CONTRIBUTIONS_1))
                            .setSourceId("source1")
                            .setTriggerId("trigger1")
                            .build();
            localAggregateReport2 =
                    AggregateReportFixture.getValidAggregateReportBuilder()
                            .setId("reportId2")
                            .setDebugCleartextPayload(
                                    AggregateReport.generateDebugPayload(CONTRIBUTIONS_2))
                            .setSourceId("source2")
                            .setTriggerId("trigger2")
                            .build();
        } catch (JSONException e) {
            localAggregateReport1 = null;
            localAggregateReport2 = null;
            fail("Failed to create aggregate report.");
        }
        AGGREGATE_REPORT_1 = localAggregateReport1;
        AGGREGATE_REPORT_2 = localAggregateReport2;
    }

    private static final Instant START = Instant.ofEpochMilli(5000);
    private static final Instant END = Instant.ofEpochMilli(10000);
    private static final String APP_PACKAGE_NAME = "app.package.name";
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";

    @Mock private IMeasurementDao mMeasurementDao;
    @Mock private ITransaction mTransaction;
    @Mock private AggregatableAttributionSource mAggregatableAttributionSource1;
    @Mock private AggregatableAttributionSource mAggregatableAttributionSource2;
    @Mock private EventReport mEventReport1;
    @Mock private EventReport mEventReport2;
    @Mock private EventReport mEventReport3;
    @Mock private AggregateReport mAggregateReport1;
    @Mock private AggregateReport mAggregateReport2;
    @Mock private AggregateReport mAggregateReport3;
    @Mock private List<Uri> mOriginUris;
    @Mock private List<Uri> mDomainUris;

    private MeasurementDataDeleter mMeasurementDataDeleter;

    private class FakeDatastoreManager extends DatastoreManager {
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
    public void setUp() throws Exception {
        mMeasurementDataDeleter = new MeasurementDataDeleter(new FakeDatastoreManager());
    }

    @Test
    public void resetAggregateContributions_hasMatchingReports_resetsContributions()
            throws DatastoreException {
        // Setup
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setAggregatableAttributionSource(mAggregatableAttributionSource1)
                        .setAggregateContributions(32666)
                        .build();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source2")
                        .setAggregatableAttributionSource(mAggregatableAttributionSource2)
                        .setAggregateContributions(6235)
                        .build();

        when(mMeasurementDao.getSource(source1.getId())).thenReturn(source1);
        when(mMeasurementDao.getSource(source2.getId())).thenReturn(source2);

        // Execute
        mMeasurementDataDeleter.resetAggregateContributions(
                mMeasurementDao, Arrays.asList(AGGREGATE_REPORT_1, AGGREGATE_REPORT_2));

        // Verify
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao, times(2))
                .updateSourceAggregateContributions(sourceCaptor.capture());
        assertEquals(2, sourceCaptor.getAllValues().size());
        assertEquals(
                32534,
                sourceCaptor.getAllValues().get(0).getAggregateContributions()); // 32666-87-45
        assertEquals(
                781,
                sourceCaptor.getAllValues().get(1).getAggregateContributions()); // 6235-3454-2000
    }

    @Test
    public void resetAggregateContributions_withSourceContributionsGoingBelowZero_resetsToZero()
            throws DatastoreException {
        // Setup
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setAggregatableAttributionSource(mAggregatableAttributionSource1)
                        .setAggregateContributions(10)
                        .build();

        when(mMeasurementDao.getSource(source1.getId())).thenReturn(source1);

        // Execute
        mMeasurementDataDeleter.resetAggregateContributions(
                mMeasurementDao, Collections.singletonList(AGGREGATE_REPORT_1));

        // Verify
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao, times(1))
                .updateSourceAggregateContributions(sourceCaptor.capture());
        assertEquals(1, sourceCaptor.getAllValues().size());
        assertEquals(0, sourceCaptor.getValue().getAggregateContributions());
    }

    @Test
    public void resetDedupKeys_hasMatchingEventReports_removesTriggerDedupKeysFromSource()
            throws DatastoreException {
        // Setup
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("sourceId1")
                        .setEventReportDedupKeys(
                                new ArrayList<>(
                                        Arrays.asList(
                                                new UnsignedLong("1"),
                                                new UnsignedLong("2"),
                                                new UnsignedLong("3"))))
                        .build();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("sourceId2")
                        .setEventReportDedupKeys(
                                new ArrayList<>(
                                        Arrays.asList(
                                                new UnsignedLong("11"),
                                                new UnsignedLong("22"),
                                                new UnsignedLong("33"))))
                        .build();

        when(mEventReport1.getTriggerDedupKey()).thenReturn(new UnsignedLong("1")); // S1 - T1
        when(mEventReport2.getTriggerDedupKey()).thenReturn(new UnsignedLong("22")); // S2 - T2
        when(mEventReport3.getTriggerDedupKey()).thenReturn(new UnsignedLong("3")); // S1 - T3
        when(mEventReport1.getSourceId()).thenReturn(source1.getId());
        when(mEventReport2.getSourceId()).thenReturn(source2.getId());
        when(mEventReport3.getSourceId()).thenReturn(source1.getId());

        when(mMeasurementDao.getSource(source1.getId())).thenReturn(source1);
        when(mMeasurementDao.getSource(source2.getId())).thenReturn(source2);

        // Execution
        mMeasurementDataDeleter.resetDedupKeys(
                mMeasurementDao, List.of(mEventReport1, mEventReport2, mEventReport3));

        // Verification
        verify(mMeasurementDao, times(2)).updateSourceEventReportDedupKeys(source1);
        verify(mMeasurementDao).updateSourceEventReportDedupKeys(source2);
        assertEquals(
                Collections.singletonList(new UnsignedLong("2")),
                source1.getEventReportDedupKeys());
        assertEquals(
                Arrays.asList(new UnsignedLong("11"), new UnsignedLong("33")),
                source2.getEventReportDedupKeys());
    }

    @Test
    public void resetDedupKeys_eventReportHasNullSourceId_ignoresRemoval()
            throws DatastoreException {
        // Setup
        when(mEventReport1.getSourceId()).thenReturn(null);
        when(mEventReport1.getTriggerDedupKey()).thenReturn(new UnsignedLong("1")); // S1 - T1

        // Execution
        mMeasurementDataDeleter.resetDedupKeys(mMeasurementDao, List.of(mEventReport1));

        // Verification
        verify(mMeasurementDao, never()).getSource(anyString());
        verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
    }

    @Test
    public void resetDedupKeys_hasMatchingAggregateReports_removesTriggerDedupKeysFromSource()
            throws DatastoreException {
        // Setup
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("sourceId1")
                        .setAggregateReportDedupKeys(
                                new ArrayList<>(
                                        Arrays.asList(
                                                new UnsignedLong("1"),
                                                new UnsignedLong("2"),
                                                new UnsignedLong("3"))))
                        .build();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("sourceId2")
                        .setAggregateReportDedupKeys(
                                new ArrayList<>(
                                        Arrays.asList(
                                                new UnsignedLong("11"),
                                                new UnsignedLong("22"),
                                                new UnsignedLong("33"))))
                        .build();

        when(mAggregateReport1.getDedupKey()).thenReturn(new UnsignedLong("1")); // S1 - T1
        when(mAggregateReport2.getDedupKey()).thenReturn(new UnsignedLong("22")); // S2 - T2
        when(mAggregateReport3.getDedupKey()).thenReturn(new UnsignedLong("3")); // S1 - T3
        when(mAggregateReport1.getSourceId()).thenReturn(source1.getId());
        when(mAggregateReport2.getSourceId()).thenReturn(source2.getId());
        when(mAggregateReport3.getSourceId()).thenReturn(source1.getId());

        when(mMeasurementDao.getSource(source1.getId())).thenReturn(source1);
        when(mMeasurementDao.getSource(source2.getId())).thenReturn(source2);

        // Execution
        mMeasurementDataDeleter.resetAggregateReportDedupKeys(
                mMeasurementDao, List.of(mAggregateReport1, mAggregateReport2, mAggregateReport3));

        // Verification
        verify(mMeasurementDao, times(2)).updateSourceAggregateReportDedupKeys(source1);
        verify(mMeasurementDao).updateSourceAggregateReportDedupKeys(source2);
        assertEquals(
                Collections.singletonList(new UnsignedLong("2")),
                source1.getAggregateReportDedupKeys());
        assertEquals(
                Arrays.asList(new UnsignedLong("11"), new UnsignedLong("33")),
                source2.getAggregateReportDedupKeys());
    }

    @Test
    public void resetDedupKeys_AggregateReportHasNullSourceId_ignoresRemoval()
            throws DatastoreException {
        // Setup
        when(mAggregateReport1.getSourceId()).thenReturn(null);
        when(mAggregateReport1.getDedupKey()).thenReturn(new UnsignedLong("1")); // S1 - T1

        // Execution
        mMeasurementDataDeleter.resetAggregateReportDedupKeys(
                mMeasurementDao, List.of(mAggregateReport1));

        // Verification
        verify(mMeasurementDao, never()).getSource(anyString());
        verify(mMeasurementDao, never()).updateSourceAggregateReportDedupKeys(any());
    }

    @Test
    public void resetDedupKeys_AggregateReportHasNullDedupKey_ignoresRemoval()
            throws DatastoreException {
        // Setup
        when(mAggregateReport1.getSourceId()).thenReturn(null);
        when(mAggregateReport1.getDedupKey()).thenReturn(null); // S1 - T1

        // Execution
        mMeasurementDataDeleter.resetAggregateReportDedupKeys(
                mMeasurementDao, List.of(mAggregateReport1));

        // Verification
        verify(mMeasurementDao, never()).getSource(anyString());
        verify(mMeasurementDao, never()).updateSourceAggregateReportDedupKeys(any());
    }

    @Test
    public void delete_deletionModeAll_success() throws DatastoreException {
        // Setup
        MeasurementDataDeleter subjectUnderTest = spy(mMeasurementDataDeleter);
        List<String> triggerIds = List.of("triggerId1", "triggerId2");
        List<String> sourceIds = List.of("sourceId1", "sourceId2");
        List<String> asyncRegistrationIds = List.of("asyncRegId1", "asyncRegId2");
        Source source1 = SourceFixture.getMinimalValidSourceBuilder().setId("sourceId1").build();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("sourceId2")
                        .setAggregateReportDedupKeys(
                                List.of(new UnsignedLong(1L), new UnsignedLong(2L)))
                        .build();
        Trigger trigger1 = TriggerFixture.getValidTriggerBuilder().setId("triggerId1").build();
        Trigger trigger2 = TriggerFixture.getValidTriggerBuilder().setId("triggerId2").build();
        when(mEventReport1.getId()).thenReturn("eventReportId1");
        when(mEventReport2.getId()).thenReturn("eventReportId2");
        when(mAggregateReport1.getId()).thenReturn("aggregateReportId1");
        when(mAggregateReport2.getId()).thenReturn("aggregateReportId2");
        DeletionParam deletionParam =
                new DeletionParam.Builder(
                                mOriginUris,
                                mDomainUris,
                                START,
                                END,
                                APP_PACKAGE_NAME,
                                SDK_PACKAGE_NAME)
                        .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                        .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                        .build();

        ExtendedMockito.doReturn(true).when(subjectUnderTest).getFlexibleEventAPIFlag();
        doNothing()
                .when(subjectUnderTest)
                .resetAggregateContributions(
                        mMeasurementDao, List.of(mAggregateReport1, mAggregateReport2));
        doNothing()
                .when(subjectUnderTest)
                .resetDedupKeys(mMeasurementDao, List.of(mEventReport1, mEventReport2));
        when(mMeasurementDao.fetchMatchingEventReports(sourceIds, triggerIds))
                .thenReturn(List.of(mEventReport1, mEventReport2));
        when(mMeasurementDao.fetchMatchingAggregateReports(sourceIds, triggerIds))
                .thenReturn(List.of(mAggregateReport1, mAggregateReport2));
        when(mMeasurementDao.fetchMatchingSources(
                        Uri.parse(ANDROID_APP_SCHEME + "://" + APP_PACKAGE_NAME),
                        START,
                        END,
                        mOriginUris,
                        mDomainUris,
                        DeletionRequest.MATCH_BEHAVIOR_DELETE))
                .thenReturn(Arrays.asList(source1.getId(), source2.getId()));
        when(mMeasurementDao.fetchMatchingAsyncRegistrations(
                        Uri.parse(ANDROID_APP_SCHEME + "://" + APP_PACKAGE_NAME),
                        START,
                        END,
                        mOriginUris,
                        mDomainUris,
                        DeletionRequest.MATCH_BEHAVIOR_DELETE))
                .thenReturn(asyncRegistrationIds);
        when(mMeasurementDao.fetchMatchingTriggers(
                        Uri.parse(ANDROID_APP_SCHEME + "://" + APP_PACKAGE_NAME),
                        START,
                        END,
                        mOriginUris,
                        mDomainUris,
                        DeletionRequest.MATCH_BEHAVIOR_DELETE))
                .thenReturn(Arrays.asList(trigger1.getId(), trigger2.getId()));
        when(mEventReport1.getSourceId()).thenReturn("sourceId1");
        when(mEventReport2.getSourceId()).thenReturn("sourceId2");
        when(mMeasurementDao.getSource(source1.getId())).thenReturn(source1);
        when(mMeasurementDao.getSource(source2.getId())).thenReturn(source2);

        // Execution
        boolean result = subjectUnderTest.delete(deletionParam);

        // Assertions
        assertTrue(result);
        verify(subjectUnderTest)
                .resetAggregateContributions(
                        mMeasurementDao, List.of(mAggregateReport1, mAggregateReport2));
        verify(subjectUnderTest)
                .resetDedupKeys(mMeasurementDao, List.of(mEventReport1, mEventReport2));
        verify(mMeasurementDao).deleteAsyncRegistrations(asyncRegistrationIds);
        verify(mMeasurementDao).deleteSources(sourceIds);
        verify(mMeasurementDao).deleteTriggers(triggerIds);
        verify(subjectUnderTest)
                .resetAggregateReportDedupKeys(
                        mMeasurementDao, List.of(mAggregateReport1, mAggregateReport2));
    }

    @Test
    public void delete_deletionModeExcludeInternalData_success() throws DatastoreException {
        // Setup
        MeasurementDataDeleter subjectUnderTest = spy(mMeasurementDataDeleter);
        List<String> triggerIds = List.of("triggerId1", "triggerId2");
        List<String> sourceIds = List.of("sourceId1", "sourceId2");
        Source source1 = SourceFixture.getMinimalValidSourceBuilder().setId("sourceId1").build();
        Source source2 = SourceFixture.getMinimalValidSourceBuilder().setId("sourceId2").build();
        Trigger trigger1 = TriggerFixture.getValidTriggerBuilder().setId("triggerId1").build();
        Trigger trigger2 = TriggerFixture.getValidTriggerBuilder().setId("triggerId2").build();
        when(mEventReport1.getId()).thenReturn("eventReportId1");
        when(mEventReport2.getId()).thenReturn("eventReportId2");
        when(mEventReport1.getSourceId()).thenReturn("sourceId1");
        when(mEventReport2.getSourceId()).thenReturn("sourceId2");
        when(mAggregateReport1.getId()).thenReturn("aggregateReportId1");
        when(mAggregateReport2.getId()).thenReturn("aggregateReportId2");
        DeletionParam deletionParam =
                new DeletionParam.Builder(
                                mOriginUris,
                                mDomainUris,
                                START,
                                END,
                                APP_PACKAGE_NAME,
                                SDK_PACKAGE_NAME)
                        .setDeletionMode(DeletionRequest.DELETION_MODE_EXCLUDE_INTERNAL_DATA)
                        .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                        .build();

        doNothing()
                .when(subjectUnderTest)
                .resetAggregateContributions(
                        mMeasurementDao, List.of(mAggregateReport1, mAggregateReport2));
        doNothing()
                .when(subjectUnderTest)
                .resetDedupKeys(mMeasurementDao, List.of(mEventReport1, mEventReport2));
        when(mMeasurementDao.fetchMatchingEventReports(sourceIds, triggerIds))
                .thenReturn(List.of(mEventReport1, mEventReport2));
        when(mMeasurementDao.fetchMatchingAggregateReports(sourceIds, triggerIds))
                .thenReturn(List.of(mAggregateReport1, mAggregateReport2));
        when(mMeasurementDao.fetchMatchingSources(
                        Uri.parse(ANDROID_APP_SCHEME + "://" + APP_PACKAGE_NAME),
                        START,
                        END,
                        mOriginUris,
                        mDomainUris,
                        DeletionRequest.MATCH_BEHAVIOR_DELETE))
                .thenReturn(Arrays.asList(source1.getId(), source2.getId()));
        when(mMeasurementDao.fetchMatchingTriggers(
                        Uri.parse(ANDROID_APP_SCHEME + "://" + APP_PACKAGE_NAME),
                        START,
                        END,
                        mOriginUris,
                        mDomainUris,
                        DeletionRequest.MATCH_BEHAVIOR_DELETE))
                .thenReturn(Arrays.asList(trigger1.getId(), trigger2.getId()));

        when(mMeasurementDao.getSource(source1.getId())).thenReturn(source1);
        when(mMeasurementDao.getSource(source2.getId())).thenReturn(source2);

        ExtendedMockito.doReturn(true).when(subjectUnderTest).getFlexibleEventAPIFlag();
        // Execution
        boolean result = subjectUnderTest.delete(deletionParam);

        // Assertions
        assertTrue(result);
        verify(mMeasurementDao)
                .markEventReportStatus(
                        eq("eventReportId1"), eq(EventReport.Status.MARKED_TO_DELETE));
        verify(mMeasurementDao)
                .markEventReportStatus(
                        eq("eventReportId2"), eq(EventReport.Status.MARKED_TO_DELETE));
        verify(mMeasurementDao)
                .markAggregateReportStatus(
                        eq("aggregateReportId2"), eq(AggregateReport.Status.MARKED_TO_DELETE));
        verify(mMeasurementDao)
                .markAggregateReportStatus(
                        eq("aggregateReportId2"), eq(AggregateReport.Status.MARKED_TO_DELETE));
        verify(mMeasurementDao)
                .updateSourceStatus(
                        eq(List.of(source1.getId(), source2.getId())),
                        eq(Source.Status.MARKED_TO_DELETE));
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(List.of(trigger1.getId(), trigger2.getId())),
                        eq(Trigger.Status.MARKED_TO_DELETE));
    }

    @Test
    public void filterReportFlexibleEventsAPI_deletionBasedSource_noReportFilteredFromDeletion()
            throws DatastoreException {
        // Setup
        Source source = SourceFixture.getValidSourceWithFlexEventReport();
        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);
        MeasurementDataDeleter subjectUnderTest = spy(mMeasurementDataDeleter);

        EventReport eventReport1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("12345")
                        .setTriggerValue(1L)
                        .setSourceId(source.getId())
                        .build();
        EventReport eventReport2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("23456")
                        .setTriggerValue(1L)
                        .setSourceId(source.getId())
                        .build();

        source.getFlexEventReportSpec().insertAttributedTrigger(eventReport1);
        source.getFlexEventReportSpec().insertAttributedTrigger(eventReport2);

        ExtendedMockito.doReturn(true).when(subjectUnderTest).getFlexibleEventAPIFlag();
        // Execution
        List<String> sourceIds = new ArrayList<>(Collections.singletonList(source.getId()));
        List<EventReport> toBeDeleted =
                subjectUnderTest.filterReportFlexibleEventsAPI(
                        mMeasurementDao,
                        sourceIds,
                        new ArrayList<>(Arrays.asList(eventReport1, eventReport2)));
        // Assertion
        // both reports are deleted because the source was deleted so both deletion list are the
        // same
        assertEquals(new ArrayList<>(Arrays.asList(eventReport1, eventReport2)), toBeDeleted);
    }

    @Test
    public void filterReportFlexibleEventsAPICountBase_baseCase_noReportFilteredFromDeletion()
            throws DatastoreException, JSONException {
        // Setup
        String sourceId = UUID.randomUUID().toString();
        EventReport eventReport1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("12345")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(1L)
                        .setSourceId(sourceId)
                        .build();
        EventReport eventReport2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("23456")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(1L)
                        .setSourceId(sourceId)
                        .build();
        ReportSpec reportSpec = SourceFixture.getValidReportSpecCountBased();
        reportSpec.insertAttributedTrigger(eventReport1);
        reportSpec.insertAttributedTrigger(eventReport2);
        Source.Builder sourceBuilder =
                SourceFixture.getValidSourceBuilderWithFlexEventReport()
                        .setId(sourceId)
                        .setTriggerSpecs(reportSpec.encodeTriggerSpecsToJSON())
                        .setMaxEventLevelReports(reportSpec.getMaxReports())
                        .setEventAttributionStatus(
                                reportSpec.encodeTriggerStatusToJSON().toString())
                        .setPrivacyParameters(reportSpec.encodePrivacyParametersToJSONString());
        Source source = sourceBuilder.build();
        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);
        MeasurementDataDeleter subjectUnderTest = spy(mMeasurementDataDeleter);

        ExtendedMockito.doReturn(true).when(subjectUnderTest).getFlexibleEventAPIFlag();
        // Execution

        List<EventReport> toBeDeleted =
                subjectUnderTest.filterReportFlexibleEventsAPI(
                        mMeasurementDao,
                        new ArrayList<>(),
                        new ArrayList<>(Arrays.asList(eventReport1, eventReport2)));
        // Assertion
        // both reports are deleted since each trigger generate one report in the count case. (the
        // simplest cases in deletion)
        assertEquals(2, toBeDeleted.size());
        assertEquals(new ArrayList<>(Arrays.asList(eventReport1, eventReport2)), toBeDeleted);
    }

    @Test
    public void filterReportFlexibleEventsAPI_nonDecrementalTrigger_allFilteredFromDeletion()
            throws DatastoreException, JSONException {
        // This test case is for value-based trigger specifications
        // Setup
        String sourceId = UUID.randomUUID().toString();
        EventReport eventReport1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("12345")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(1L)
                        .setSourceId(sourceId)
                        .build();
        EventReport eventReport2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("23456")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(1L)
                        .setSourceId(sourceId)
                        .build();
        ReportSpec reportSpec = SourceFixture.getValidReportSpecValueSum();
        reportSpec.insertAttributedTrigger(eventReport1);
        reportSpec.insertAttributedTrigger(eventReport2);

        Source.Builder sourceBuilder =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setTriggerSpecs(reportSpec.encodeTriggerSpecsToJSON())
                        .setMaxEventLevelReports(reportSpec.getMaxReports())
                        .setEventAttributionStatus(
                                reportSpec.encodeTriggerStatusToJSON().toString())
                        .setPrivacyParameters(reportSpec.encodePrivacyParametersToJSONString());
        Source source = sourceBuilder.build();
        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);
        MeasurementDataDeleter subjectUnderTest = spy(mMeasurementDataDeleter);

        ExtendedMockito.doReturn(true).when(subjectUnderTest).getFlexibleEventAPIFlag();
        // Execution
        List<EventReport> toBeDeleted =
                subjectUnderTest.filterReportFlexibleEventsAPI(
                        mMeasurementDao,
                        new ArrayList<>(),
                        new ArrayList<>(Arrays.asList(eventReport1, eventReport2)));
        // Assertion
        // To-be-deleted reports don't cause decrements in bucket so nothing should be deleted
        assertEquals(0, toBeDeleted.size());
        assertEquals(new ArrayList<>(), toBeDeleted);
    }

    @Test
    public void filterReportFlexibleEventsAPI_TriggerContributingToReport_oneFilteredFromDeletion()
            throws DatastoreException, JSONException {
        // This test case is for value-based trigger specifications
        // Setup
        String sourceId = UUID.randomUUID().toString();
        EventReport eventReport1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("12345")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(6L)
                        .setSourceId(sourceId)
                        .build();
        EventReport eventReport2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("23456")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(7L)
                        .setSourceId(sourceId)
                        .build();
        ReportSpec reportSpec = SourceFixture.getValidReportSpecValueSum();
        reportSpec.insertAttributedTrigger(eventReport1);
        reportSpec.insertAttributedTrigger(eventReport2);

        Source.Builder sourceBuilder =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setTriggerSpecs(reportSpec.encodeTriggerSpecsToJSON())
                        .setMaxEventLevelReports(reportSpec.getMaxReports())
                        .setEventAttributionStatus(
                                reportSpec.encodeTriggerStatusToJSON().toString())
                        .setPrivacyParameters(reportSpec.encodePrivacyParametersToJSONString());
        Source source = sourceBuilder.build();
        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);
        MeasurementDataDeleter subjectUnderTest = spy(mMeasurementDataDeleter);

        ExtendedMockito.doReturn(true).when(subjectUnderTest).getFlexibleEventAPIFlag();
        // Execution
        List<EventReport> toBeDeleted =
                subjectUnderTest.filterReportFlexibleEventsAPI(
                        mMeasurementDao,
                        new ArrayList<>(),
                        new ArrayList<>(Collections.singletonList(eventReport1)));
        // Assertion
        // Two triggers combined together to create a report. One trigger is deleted so the value of
        // another trigger cannot create a report. Therefore, the report is deleted.
        assertEquals(1, toBeDeleted.size());
        assertEquals(new ArrayList<>(Collections.singletonList(eventReport1)), toBeDeleted);
    }

    @Test
    public void filterReportFlexibleEventsAPI_noDecrements_oneFilteredFromDeletion()
            throws DatastoreException, JSONException {
        // This test case is for value-based trigger specifications
        // Setup
        String sourceId = UUID.randomUUID().toString();
        EventReport eventReport1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("12345")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(50L)
                        .setSourceId(sourceId)
                        .build();
        EventReport eventReport2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("23456")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(20L)
                        .setSourceId(sourceId)
                        .build();
        ReportSpec reportSpec = SourceFixture.getValidReportSpecValueSum();
        reportSpec.insertAttributedTrigger(eventReport1);
        reportSpec.insertAttributedTrigger(eventReport2);
        Source.Builder sourceBuilder =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setTriggerSpecs(reportSpec.encodeTriggerSpecsToJSON())
                        .setMaxEventLevelReports(reportSpec.getMaxReports())
                        .setEventAttributionStatus(
                                reportSpec.encodeTriggerStatusToJSON().toString())
                        .setPrivacyParameters(reportSpec.encodePrivacyParametersToJSONString());
        Source source = sourceBuilder.build();
        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);
        MeasurementDataDeleter subjectUnderTest = spy(mMeasurementDataDeleter);

        ExtendedMockito.doReturn(true).when(subjectUnderTest).getFlexibleEventAPIFlag();
        // Execution
        List<EventReport> toBeDeleted =
                subjectUnderTest.filterReportFlexibleEventsAPI(
                        mMeasurementDao,
                        new ArrayList<>(),
                        new ArrayList<>(Collections.singletonList(eventReport2)));
        // Assertion
        // no report should be deleted. No decremental of the reports
        assertEquals(0, toBeDeleted.size());
    }

    @Test
    public void filterReportFlexibleEventsAPI_exceedUpperLimit_oneFilteredFromDeletion()
            throws DatastoreException, JSONException {
        // This test case is for value-based trigger specifications
        // Setup
        String sourceId = UUID.randomUUID().toString();
        EventReport eventReport1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("12345")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(101L)
                        .setSourceId(sourceId)
                        .build();
        EventReport eventReport2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("23456")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(50L)
                        .setSourceId(sourceId)
                        .build();
        ReportSpec reportSpec = SourceFixture.getValidReportSpecValueSum();
        reportSpec.insertAttributedTrigger(eventReport1);
        reportSpec.insertAttributedTrigger(eventReport2);

        Source.Builder sourceBuilder =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setTriggerSpecs(reportSpec.encodeTriggerSpecsToJSON())
                        .setMaxEventLevelReports(reportSpec.getMaxReports())
                        .setEventAttributionStatus(
                                reportSpec.encodeTriggerStatusToJSON().toString())
                        .setPrivacyParameters(reportSpec.encodePrivacyParametersToJSONString());
        Source source = sourceBuilder.build();
        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);
        MeasurementDataDeleter subjectUnderTest = spy(mMeasurementDataDeleter);

        ExtendedMockito.doReturn(true).when(subjectUnderTest).getFlexibleEventAPIFlag();
        // Execution
        List<EventReport> toBeDeleted =
                subjectUnderTest.filterReportFlexibleEventsAPI(
                        mMeasurementDao,
                        new ArrayList<>(),
                        new ArrayList<>(Collections.singletonList(eventReport2)));
        // Assertion
        // Trigger 1 make the value greater than highest bucket, whether or not deleting trigger 2
        // doesn't change number of report. Therefore, no reports are deleted.
        assertEquals(0, toBeDeleted.size());
    }

    @Test
    public void filterReportFlexibleEventsAPI_earlierLargeTrigger_noneFilteredFromDeletion()
            throws DatastoreException, JSONException {
        // This test case is for value-based trigger specifications
        // Setup
        String sourceId = UUID.randomUUID().toString();
        EventReport eventReport1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("12345")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(101L)
                        .setSourceId(sourceId)
                        .build();
        EventReport eventReport2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("23456")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(50L)
                        .setSourceId(sourceId)
                        .build();
        ReportSpec reportSpec = SourceFixture.getValidReportSpecValueSum();
        reportSpec.insertAttributedTrigger(eventReport1);
        reportSpec.insertAttributedTrigger(eventReport2);

        Source.Builder sourceBuilder =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setTriggerSpecs(reportSpec.encodeTriggerSpecsToJSON())
                        .setMaxEventLevelReports(reportSpec.getMaxReports())
                        .setEventAttributionStatus(
                                reportSpec.encodeTriggerStatusToJSON().toString())
                        .setPrivacyParameters(reportSpec.encodePrivacyParametersToJSONString());
        Source source = sourceBuilder.build();
        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);
        MeasurementDataDeleter subjectUnderTest = spy(mMeasurementDataDeleter);

        ExtendedMockito.doReturn(true).when(subjectUnderTest).getFlexibleEventAPIFlag();
        // Execution
        List<EventReport> toBeDeleted =
                subjectUnderTest.filterReportFlexibleEventsAPI(
                        mMeasurementDao,
                        new ArrayList<>(),
                        new ArrayList<>(Collections.singletonList(eventReport1)));
        // Assertion
        // The eariler trigger with 101 value is deleted and the later trigger has value of 50 so
        // only 1 bucket decrement.
        assertEquals(1, toBeDeleted.size());
    }

    @Test
    public void filterReportFlexibleEventsAPI_noDecrementsDeletePrevious_allFilteredFromDeletion()
            throws DatastoreException, JSONException {
        // This test case is for value-based trigger specifications
        // Setup
        String sourceId = UUID.randomUUID().toString();
        EventReport eventReport1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("12345")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(50L)
                        .setSourceId(sourceId)
                        .build();
        EventReport eventReport2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("23456")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(30L)
                        .setSourceId(sourceId)
                        .build();
        ReportSpec reportSpec = SourceFixture.getValidReportSpecValueSum();
        reportSpec.insertAttributedTrigger(eventReport1);
        reportSpec.insertAttributedTrigger(eventReport2);

        Source.Builder sourceBuilder =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setTriggerSpecs(reportSpec.encodeTriggerSpecsToJSON())
                        .setMaxEventLevelReports(reportSpec.getMaxReports())
                        .setEventAttributionStatus(
                                reportSpec.encodeTriggerStatusToJSON().toString())
                        .setPrivacyParameters(reportSpec.encodePrivacyParametersToJSONString());
        Source source = sourceBuilder.build();
        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);
        MeasurementDataDeleter subjectUnderTest = spy(mMeasurementDataDeleter);

        ExtendedMockito.doReturn(true).when(subjectUnderTest).getFlexibleEventAPIFlag();
        // Execution
        List<EventReport> toBeDeleted =
                subjectUnderTest.filterReportFlexibleEventsAPI(
                        mMeasurementDao,
                        new ArrayList<>(),
                        new ArrayList<>(Collections.singletonList(eventReport1)));
        // Assertion
        // no report should be deleted. No decremental of the reports
        assertEquals(0, toBeDeleted.size());
    }

    @Test
    public void filterReportFlexibleEventsAPI_deletingWithDecrements_noneFilteredFromDeletion()
            throws DatastoreException, JSONException {
        // This test case is for value-based trigger specifications
        // Setup
        String sourceId = UUID.randomUUID().toString();
        EventReport eventReport1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("12345")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(50L)
                        .setSourceId(sourceId)
                        .build();
        EventReport eventReport2 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("23456")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(60L)
                        .setSourceId(sourceId)
                        .build();
        ReportSpec reportSpec = SourceFixture.getValidReportSpecValueSum();
        reportSpec.insertAttributedTrigger(eventReport1);
        reportSpec.insertAttributedTrigger(eventReport2);

        Source.Builder sourceBuilder =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setTriggerSpecs(reportSpec.encodeTriggerSpecsToJSON())
                        .setMaxEventLevelReports(reportSpec.getMaxReports())
                        .setEventAttributionStatus(
                                reportSpec.encodeTriggerStatusToJSON().toString())
                        .setPrivacyParameters(reportSpec.encodePrivacyParametersToJSONString());
        Source source = sourceBuilder.build();
        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);
        MeasurementDataDeleter subjectUnderTest = spy(mMeasurementDataDeleter);

        ExtendedMockito.doReturn(true).when(subjectUnderTest).getFlexibleEventAPIFlag();
        // Execution
        List<EventReport> toBeDeleted =
                subjectUnderTest.filterReportFlexibleEventsAPI(
                        mMeasurementDao,
                        new ArrayList<>(),
                        new ArrayList<>(Collections.singletonList(eventReport1)));
        // Assertion
        // The report causes decrements of the report
        assertEquals(1, toBeDeleted.size());
        assertEquals(new ArrayList<>(Collections.singletonList(eventReport1)), toBeDeleted);
    }

    @Test
    public void filterReportFlexibleEventsAPI_moreThanOneReportPerTrigger_noneFilteredFromDeletion()
            throws DatastoreException, JSONException {
        // This test case is for value-based trigger specifications
        // Setup
        String sourceId = UUID.randomUUID().toString();
        ReportSpec reportSpec = SourceFixture.getValidReportSpecValueSum();
        EventReport eventReport1 =
                EventReportFixture.getBaseEventReportBuild()
                        .setId(UUID.randomUUID().toString())
                        .setTriggerId("12345")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(100L)
                        .setSourceId(sourceId)
                        .build();

        reportSpec.insertAttributedTrigger(eventReport1);
        Source.Builder sourceBuilder =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setTriggerSpecs(reportSpec.encodeTriggerSpecsToJSON())
                        .setMaxEventLevelReports(reportSpec.getMaxReports())
                        .setEventAttributionStatus(
                                reportSpec.encodeTriggerStatusToJSON().toString())
                        .setPrivacyParameters(reportSpec.encodePrivacyParametersToJSONString());
        Source source = sourceBuilder.build();

        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);
        MeasurementDataDeleter subjectUnderTest = spy(mMeasurementDataDeleter);

        ExtendedMockito.doReturn(true).when(subjectUnderTest).getFlexibleEventAPIFlag();
        // Execution
        List<EventReport> toBeDeleted =
                subjectUnderTest.filterReportFlexibleEventsAPI(
                        mMeasurementDao,
                        new ArrayList<>(),
                        new ArrayList<>(Arrays.asList(eventReport1, eventReport1)));
        // Assertion
        // The report causes decrements of the report
        assertEquals(2, toBeDeleted.size());
        assertEquals(new ArrayList<>(Arrays.asList(eventReport1, eventReport1)), toBeDeleted);
    }
}
