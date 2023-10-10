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
import com.android.adservices.errorlogging.AdServicesErrorLogger;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
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
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

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
    @Mock private Flags mFlags;
    @Mock private AdServicesErrorLogger mErrorLogger;
    @Mock private AdServicesLogger mLogger;

    private MeasurementDataDeleter mMeasurementDataDeleter;
    private MockitoSession mStaticMockSession;

    private class FakeDatastoreManager extends DatastoreManager {
        private FakeDatastoreManager() {
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
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .startMocking();
        ExtendedMockito.doReturn(mFlags).when(FlagsFactory::getFlags);
        when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(true);
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(false);
        mMeasurementDataDeleter =
                spy(new MeasurementDataDeleter(new FakeDatastoreManager(), mFlags, mLogger));
    }

    @After
    public void cleanup() {
        mStaticMockSession.finishMocking();
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
    public void resetDedupKeys_matchingReports_removesDedupKeysFromSource()
            throws DatastoreException, JSONException {
        String attributionStatus1 = getAttributionStatus(
                List.of("trigger1", "trigger2", "trigger3"),
                List.of("4", "5", "6"),
                List.of("1", "2", "3"));
        // Setup
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("sourceId1")
                        .setEventAttributionStatus(attributionStatus1)
                        .build();
        String attributionStatus2 = getAttributionStatus(
                List.of("trigger11", "trigger22", "trigger33"),
                List.of("44", "55", "66"),
                List.of("11", "22", "33"));
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("sourceId2")
                        .setEventAttributionStatus(attributionStatus2)
                        .build();

        when(mEventReport1.getTriggerDedupKey()).thenReturn(new UnsignedLong("1")); // S1 - T1
        when(mEventReport1.getTriggerId()).thenReturn("trigger1"); // S1 - T1
        when(mEventReport2.getTriggerDedupKey()).thenReturn(new UnsignedLong("22")); // S2 - T2
        when(mEventReport2.getTriggerId()).thenReturn("trigger22"); // S2 - T2
        when(mEventReport3.getTriggerDedupKey()).thenReturn(new UnsignedLong("3")); // S1 - T3
        when(mEventReport3.getTriggerId()).thenReturn("trigger3"); // S3 - T3
        when(mEventReport1.getSourceId()).thenReturn(source1.getId());
        when(mEventReport2.getSourceId()).thenReturn(source2.getId());
        when(mEventReport3.getSourceId()).thenReturn(source1.getId());

        when(mMeasurementDao.getSource(source1.getId())).thenReturn(source1);
        when(mMeasurementDao.getSource(source2.getId())).thenReturn(source2);

        // Execution
        mMeasurementDataDeleter.resetDedupKeys(
                mMeasurementDao, List.of(mEventReport1, mEventReport2, mEventReport3));

        // Verification
        ArgumentCaptor<String> attributionStatusArg = ArgumentCaptor.forClass(String.class);
        verify(mMeasurementDao, times(2)).updateSourceAttributedTriggers(
                eq(source1.getId()), attributionStatusArg.capture());
        List<String> attributionStatuses = attributionStatusArg.getAllValues();
        assertEquals(
                getAttributionStatus(List.of("trigger2", "trigger3"),
                      List.of("5", "6"), List.of("2", "3")),
                attributionStatuses.get(0));
        assertEquals(
                getAttributionStatus(List.of("trigger2"), List.of("5"), List.of("2")),
                attributionStatuses.get(1));
        String expectedAttributionStatus2 = getAttributionStatus(
                List.of("trigger11", "trigger33"), List.of("44", "66"), List.of("11", "33"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source2.getId()), eq(expectedAttributionStatus2));
    }

    @Test
    public void resetDedupKeys_matchingReportsDedupAlignFlagOff_removesDedupKeysFromSource()
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

        when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(false);

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
    public void resetDedupKeys_eventReportHasNullSourceIdDedup_ignoresRemoval()
            throws DatastoreException {
        // Setup
        when(mEventReport1.getSourceId()).thenReturn(null);
        when(mEventReport1.getTriggerDedupKey()).thenReturn(new UnsignedLong("1")); // S1 - T1

        // Execution
        mMeasurementDataDeleter.resetDedupKeys(mMeasurementDao, List.of(mEventReport1));

        // Verification
        verify(mMeasurementDao, never()).getSource(anyString());
        verify(mMeasurementDao, never()).updateSourceAttributedTriggers(anyString(), anyString());
    }

    @Test
    public void resetDedupKeys_eventReportHasNullSourceIdDedupFlagOff_ignoresRemoval()
            throws DatastoreException {
        // Setup
        when(mEventReport1.getSourceId()).thenReturn(null);
        when(mEventReport1.getTriggerDedupKey()).thenReturn(new UnsignedLong("1")); // S1 - T1

        when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(false);

        // Execution
        mMeasurementDataDeleter.resetDedupKeys(mMeasurementDao, List.of(mEventReport1));

        // Verification
        verify(mMeasurementDao, never()).getSource(anyString());
        verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
    }

    @Test
    public void resetAggregateReportDedupKeys_matchingReports_removesDedupKeysFromSource()
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
    public void resetAggregateReportDedupKeys_aggregateReportHasNullSourceId_ignoresRemoval()
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
    public void resetAggregateReportDedupKeys_aggregateReportHasNullDedupKey_ignoresRemoval()
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

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        doNothing()
                .when(mMeasurementDataDeleter)
                .resetAggregateContributions(
                        mMeasurementDao, List.of(mAggregateReport1, mAggregateReport2));
        doNothing()
                .when(mMeasurementDataDeleter)
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
        boolean result = mMeasurementDataDeleter.delete(deletionParam);

        // Assertions
        assertTrue(result);
        verify(mMeasurementDataDeleter)
                .resetAggregateContributions(
                        mMeasurementDao, List.of(mAggregateReport1, mAggregateReport2));
        verify(mMeasurementDataDeleter)
                .resetDedupKeys(mMeasurementDao, List.of(mEventReport1, mEventReport2));
        verify(mMeasurementDao).deleteAsyncRegistrations(asyncRegistrationIds);
        verify(mMeasurementDao).deleteSources(sourceIds);
        verify(mMeasurementDao).deleteTriggers(triggerIds);
        verify(mMeasurementDataDeleter)
                .resetAggregateReportDedupKeys(
                        mMeasurementDao, List.of(mAggregateReport1, mAggregateReport2));
    }

    @Test
    public void delete_deletionModeExcludeInternalData_success() throws DatastoreException {
        // Setup
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
                .when(mMeasurementDataDeleter)
                .resetAggregateContributions(
                        mMeasurementDao, List.of(mAggregateReport1, mAggregateReport2));
        doNothing()
                .when(mMeasurementDataDeleter)
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

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        // Execution
        boolean result = mMeasurementDataDeleter.delete(deletionParam);

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

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        // Execution
        List<String> sourceIds = new ArrayList<>(Collections.singletonList(source.getId()));
        List<EventReport> toBeDeleted =
                mMeasurementDataDeleter.filterReportFlexibleEventsAPI(
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
        Source sourceToGenerateTriggerStatus =
                SourceFixture.getValidSourceBuilderWithFlexEventReport()
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilderWithFlexEventReport()
                        .setId(sourceId)
                        .setEventAttributionStatus(
                                getTriggerStatusJson(
                                        sourceToGenerateTriggerStatus,
                                        List.of(eventReport1, eventReport2)))
                        .build();

        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        // Execution

        List<EventReport> toBeDeleted =
                mMeasurementDataDeleter.filterReportFlexibleEventsAPI(
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
        Source sourceToGenerateTriggerStatus =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setEventAttributionStatus(
                                getTriggerStatusJson(
                                        sourceToGenerateTriggerStatus,
                                        List.of(eventReport1, eventReport2)))
                        .build();

        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);

        // Execution
        List<EventReport> toBeDeleted =
                mMeasurementDataDeleter.filterReportFlexibleEventsAPI(
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
        Source sourceToGenerateTriggerStatus =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setEventAttributionStatus(
                                getTriggerStatusJson(
                                        sourceToGenerateTriggerStatus,
                                        List.of(eventReport1, eventReport2)))
                        .build();

        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);

        // Execution
        List<EventReport> toBeDeleted =
                mMeasurementDataDeleter.filterReportFlexibleEventsAPI(
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
        Source sourceToGenerateTriggerStatus =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setEventAttributionStatus(
                                getTriggerStatusJson(
                                        sourceToGenerateTriggerStatus,
                                        List.of(eventReport1, eventReport2)))
                        .build();

        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);

        // Execution
        List<EventReport> toBeDeleted =
                mMeasurementDataDeleter.filterReportFlexibleEventsAPI(
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
        Source sourceToGenerateTriggerStatus =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setEventAttributionStatus(
                                getTriggerStatusJson(
                                        sourceToGenerateTriggerStatus,
                                        List.of(eventReport1, eventReport2)))
                        .build();

        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);

        // Execution
        List<EventReport> toBeDeleted =
                mMeasurementDataDeleter.filterReportFlexibleEventsAPI(
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
        Source sourceToGenerateTriggerStatus =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setEventAttributionStatus(
                                getTriggerStatusJson(
                                        sourceToGenerateTriggerStatus,
                                        List.of(eventReport1, eventReport2)))
                        .build();

        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);

        // Execution
        List<EventReport> toBeDeleted =
                mMeasurementDataDeleter.filterReportFlexibleEventsAPI(
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
        Source sourceToGenerateTriggerStatus =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setEventAttributionStatus(
                                getTriggerStatusJson(
                                        sourceToGenerateTriggerStatus,
                                        List.of(eventReport1, eventReport2)))
                        .build();

        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);

        // Execution
        List<EventReport> toBeDeleted =
                mMeasurementDataDeleter.filterReportFlexibleEventsAPI(
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
        Source sourceToGenerateTriggerStatus =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setEventAttributionStatus(
                                getTriggerStatusJson(
                                        sourceToGenerateTriggerStatus,
                                        List.of(eventReport1, eventReport2)))
                        .build();

        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);

        // Execution
        List<EventReport> toBeDeleted =
                mMeasurementDataDeleter.filterReportFlexibleEventsAPI(
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
        EventReport.Builder eventReportBuilder =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerId("12345")
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerValue(100L)
                        .setSourceId(sourceId);
        EventReport eventReport1 =
                eventReportBuilder
                        .setId("reportId1")
                        .build();
        EventReport eventReport2 =
                eventReportBuilder
                        .setId("reportId2")
                        .build();
        Source sourceToGenerateTriggerStatus =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilderWithFlexEventReportValueSum()
                        .setId(sourceId)
                        .setEventAttributionStatus(
                                getTriggerStatusJson(
                                        sourceToGenerateTriggerStatus,
                                        List.of(eventReport1)))
                        .build();

        when(mMeasurementDao.getSource(source.getId())).thenReturn(source);

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);

        // Execution
        List<EventReport> toBeDeleted =
                mMeasurementDataDeleter.filterReportFlexibleEventsAPI(
                        mMeasurementDao,
                        new ArrayList<>(),
                        new ArrayList<>(Arrays.asList(eventReport1, eventReport2)));
        // Assertion
        // The report causes decrements of the report
        assertEquals(2, toBeDeleted.size());
        assertEquals(new ArrayList<>(Arrays.asList(eventReport1, eventReport2)), toBeDeleted);
    }

    private static String getTriggerStatusJson(Source source, List<EventReport> eventReports)
            throws JSONException {
        source.buildFlexibleEventReportApi();
        ReportSpec reportSpec = source.getFlexEventReportSpec();
        for (EventReport eventReport : eventReports) {
            reportSpec.insertAttributedTrigger(eventReport);
        }
        return source.attributedTriggersToJsonFlexApi();
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
