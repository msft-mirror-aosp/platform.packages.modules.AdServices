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

package com.android.cobalt.observations;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.runner.AndroidJUnit4;

import com.android.cobalt.data.EventRecordAndSystemProfile;
import com.android.cobalt.data.EventVector;
import com.android.cobalt.testing.logging.FakeCobaltOperationLogger;
import com.android.cobalt.testing.observations.FakeSecureRandom;
import com.android.cobalt.testing.observations.ObservationFactory;

import com.google.cobalt.AggregateValue;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.MetricType;
import com.google.cobalt.MetricDefinition.TimeZonePolicy;
import com.google.cobalt.Observation;
import com.google.cobalt.ObservationMetadata;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.PrivacyMechanism;
import com.google.cobalt.ReportDefinition.ReportType;
import com.google.cobalt.SystemProfile;
import com.google.cobalt.UnencryptedObservationBatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.protobuf.ByteString;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.SecureRandom;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class NonPrivateObservationGeneratorTest {
    private static final int DAY_INDEX = 19201; // 2022-07-28
    private static final int CUSTOMER = 1;
    private static final int PROJECT = 2;
    private static final int METRIC_ID = 3;
    private static final int REPORT_ID = 4;
    private static final SystemProfile SYSTEM_PROFILE_1 =
            SystemProfile.newBuilder().setAppVersion("1.2.3").build();
    private static final SystemProfile SYSTEM_PROFILE_2 =
            SystemProfile.newBuilder().setAppVersion("2.4.8").build();
    private static final ObservationMetadata METADATA_1 =
            ObservationMetadata.newBuilder()
                    .setCustomerId(CUSTOMER)
                    .setProjectId(PROJECT)
                    .setMetricId(METRIC_ID)
                    .setReportId(REPORT_ID)
                    .setDayIndex(DAY_INDEX)
                    .setSystemProfile(SYSTEM_PROFILE_1)
                    .build();
    private static final ObservationMetadata METADATA_2 =
            ObservationMetadata.newBuilder()
                    .setCustomerId(CUSTOMER)
                    .setProjectId(PROJECT)
                    .setMetricId(METRIC_ID)
                    .setReportId(REPORT_ID)
                    .setDayIndex(DAY_INDEX)
                    .setSystemProfile(SYSTEM_PROFILE_2)
                    .build();
    private static final int EVENT_COUNT_1 = 3;
    private static final int EVENT_COUNT_2 = 17;
    private static final EventVector EVENT_VECTOR_1 = EventVector.create(ImmutableList.of(1, 5));
    private static final EventVector EVENT_VECTOR_2 = EventVector.create(ImmutableList.of(2, 6));
    private static final EventRecordAndSystemProfile EVENT_1 =
            createEvent(EVENT_VECTOR_1, EVENT_COUNT_1);
    private static final EventRecordAndSystemProfile EVENT_2 =
            createEvent(EVENT_VECTOR_2, EVENT_COUNT_2);

    // Deterministic randomly generated bytes due to the FakeSecureRandom.
    private static final ByteString RANDOM_BYTES_1 =
            ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 0, 0});
    private static final ByteString RANDOM_BYTES_2 =
            ByteString.copyFrom(new byte[] {1, 1, 1, 1, 1, 1, 1, 1});
    private static final ByteString RANDOM_BYTES_3 =
            ByteString.copyFrom(new byte[] {2, 2, 2, 2, 2, 2, 2, 2});
    private static final ByteString RANDOM_BYTES_4 =
            ByteString.copyFrom(new byte[] {3, 3, 3, 3, 3, 3, 3, 3});
    private static final Observation OBSERVATION_1 =
            ObservationFactory.createIntegerObservation(
                    EVENT_VECTOR_1, EVENT_COUNT_1, RANDOM_BYTES_1);
    private static final Observation OBSERVATION_1_AND_2 =
            ObservationFactory.createIntegerObservation(
                    EVENT_VECTOR_1, EVENT_COUNT_1, EVENT_VECTOR_2, EVENT_COUNT_2, RANDOM_BYTES_1);
    private static final Observation OBSERVATION_2 =
            ObservationFactory.createIntegerObservation(
                    EVENT_VECTOR_2, EVENT_COUNT_2, RANDOM_BYTES_3);
    private static final Observation NO_EVENT_CODES_OBSERVATION =
            ObservationFactory.createIntegerObservation(
                    EventVector.create(), /* countValue= */ 7, RANDOM_BYTES_1);

    private static final MetricDefinition METRIC =
            MetricDefinition.newBuilder()
                    .setId(METRIC_ID)
                    .setMetricType(MetricType.OCCURRENCE)
                    .setTimeZonePolicy(TimeZonePolicy.OTHER_TIME_ZONE)
                    .setOtherTimeZone("America/Los_Angeles")
                    .build();
    private static final ReportDefinition REPORT =
            ReportDefinition.newBuilder()
                    .setId(REPORT_ID)
                    .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                    .setPrivacyMechanism(PrivacyMechanism.DE_IDENTIFICATION)
                    .build();

    private final SecureRandom mSecureRandom;
    private NonPrivateObservationGenerator mGenerator;
    private FakeCobaltOperationLogger mOperationLogger;

    public NonPrivateObservationGeneratorTest() {
        mSecureRandom = new FakeSecureRandom();
        mGenerator = null;
        mOperationLogger = new FakeCobaltOperationLogger();
    }

    private NonPrivateObservationGenerator createObservationGenerator(
            int customerId, int projectId, MetricDefinition metric, ReportDefinition report) {
        return new NonPrivateObservationGenerator(
                mSecureRandom,
                new IntegerEncoder(mSecureRandom),
                mOperationLogger,
                customerId,
                projectId,
                metric.getId(),
                report);
    }

    private static EventRecordAndSystemProfile createEvent(
            List<Integer> eventCodes, int aggregateValue) {
        // System profile fields are ignored during observation generation and can be anything.
        return EventRecordAndSystemProfile.create(
                /* systemProfile= */ SystemProfile.getDefaultInstance(),
                EventVector.create(eventCodes),
                AggregateValue.newBuilder().setIntegerValue(aggregateValue).build());
    }

    private static EventRecordAndSystemProfile createEvent(
            EventVector eventVector, int aggregateValue) {
        // System profile fields are ignored during observation generation and can be anything.
        return EventRecordAndSystemProfile.create(
                /* systemProfile= */ SystemProfile.getDefaultInstance(),
                eventVector,
                AggregateValue.newBuilder().setIntegerValue(aggregateValue).build());
    }

    @Test
    public void testGenerateObservations_noEvents_nothingGenerated() throws Exception {
        mGenerator = createObservationGenerator(CUSTOMER, PROJECT, METRIC, REPORT);
        List<UnencryptedObservationBatch> result =
                mGenerator.generateObservations(DAY_INDEX, ImmutableListMultimap.of());
        assertThat(result).isEmpty();
    }

    @Test
    public void testGenerateObservations_oneEvent_generated() throws Exception {
        mGenerator = createObservationGenerator(CUSTOMER, PROJECT, METRIC, REPORT);
        List<UnencryptedObservationBatch> result =
                mGenerator.generateObservations(
                        DAY_INDEX, ImmutableListMultimap.of(SYSTEM_PROFILE_1, EVENT_1));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMetadata()).isEqualTo(METADATA_1);
        assertThat(result.get(0).getUnencryptedObservationsList()).hasSize(1);
        assertThat(result.get(0).getUnencryptedObservations(0).getContributionId())
                .isEqualTo(RANDOM_BYTES_2);
        assertThat(result.get(0).getUnencryptedObservations(0).getObservation())
                .isEqualTo(OBSERVATION_1);
    }

    @Test
    public void testGenerateObservations_oneEventWithNoEventCodes_generated() throws Exception {
        mGenerator = createObservationGenerator(CUSTOMER, PROJECT, METRIC, REPORT);
        List<UnencryptedObservationBatch> result =
                mGenerator.generateObservations(
                        DAY_INDEX,
                        ImmutableListMultimap.of(
                                SYSTEM_PROFILE_1, createEvent(ImmutableList.of(), 7)));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMetadata()).isEqualTo(METADATA_1);
        assertThat(result.get(0).getUnencryptedObservationsList()).hasSize(1);
        assertThat(result.get(0).getUnencryptedObservations(0).getContributionId())
                .isEqualTo(RANDOM_BYTES_2);
        assertThat(result.get(0).getUnencryptedObservations(0).getObservation())
                .isEqualTo(NO_EVENT_CODES_OBSERVATION);
    }

    @Test
    public void testGenerateObservations_twoEvents_oneObservationGenerated() throws Exception {
        mGenerator = createObservationGenerator(CUSTOMER, PROJECT, METRIC, REPORT);
        List<UnencryptedObservationBatch> result =
                mGenerator.generateObservations(
                        DAY_INDEX,
                        ImmutableListMultimap.of(
                                SYSTEM_PROFILE_1, EVENT_1, SYSTEM_PROFILE_1, EVENT_2));

        // Verify both event vectors are aggregated into one observation.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMetadata()).isEqualTo(METADATA_1);
        assertThat(result.get(0).getUnencryptedObservationsList()).hasSize(1);
        assertThat(result.get(0).getUnencryptedObservations(0).getContributionId())
                .isEqualTo(RANDOM_BYTES_2);
        assertThat(result.get(0).getUnencryptedObservations(0).getObservation())
                .isEqualTo(OBSERVATION_1_AND_2);
    }

    @Test
    public void testGenerateObservations_twoEventsInTwoSystemProfiles_separateObservations()
            throws Exception {
        mGenerator = createObservationGenerator(CUSTOMER, PROJECT, METRIC, REPORT);
        List<UnencryptedObservationBatch> result =
                mGenerator.generateObservations(
                        DAY_INDEX,
                        ImmutableListMultimap.of(
                                SYSTEM_PROFILE_1, EVENT_1, SYSTEM_PROFILE_2, EVENT_2));

        // Verify that separate system profiles are aggregated into separate batches.
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMetadata()).isEqualTo(METADATA_1);
        assertThat(result.get(0).getUnencryptedObservationsList()).hasSize(1);
        assertThat(result.get(0).getUnencryptedObservations(0).getContributionId())
                .isEqualTo(RANDOM_BYTES_2);
        assertThat(result.get(0).getUnencryptedObservations(0).getObservation())
                .isEqualTo(OBSERVATION_1);
        assertThat(result.get(1).getMetadata()).isEqualTo(METADATA_2);
        assertThat(result.get(1).getUnencryptedObservationsList()).hasSize(1);
        assertThat(result.get(1).getUnencryptedObservations(0).getContributionId())
                .isEqualTo(RANDOM_BYTES_4);
        assertThat(result.get(1).getUnencryptedObservations(0).getObservation())
                .isEqualTo(OBSERVATION_2);
    }

    @Test
    public void testGenerateObservations_eventVectorBufferMax_oneEventSent() throws Exception {
        mGenerator =
                createObservationGenerator(
                        CUSTOMER,
                        PROJECT,
                        METRIC,
                        REPORT.toBuilder().setEventVectorBufferMax(1).build());
        List<UnencryptedObservationBatch> result =
                mGenerator.generateObservations(
                        DAY_INDEX,
                        ImmutableListMultimap.of(
                                SYSTEM_PROFILE_1, EVENT_1, SYSTEM_PROFILE_1, EVENT_2));

        // Verify only the first event vector is aggregated into an observation.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMetadata()).isEqualTo(METADATA_1);
        assertThat(result.get(0).getUnencryptedObservationsList()).hasSize(1);
        assertThat(result.get(0).getUnencryptedObservations(0).getContributionId())
                .isEqualTo(RANDOM_BYTES_2);
        assertThat(result.get(0).getUnencryptedObservations(0).getObservation())
                .isEqualTo(OBSERVATION_1);
    }

    @Test
    public void
            testGenerateObservations_eventVectorBufferMaxLimit_eventVectorBufferMaxExceededLogged()
                    throws Exception {
        mGenerator =
                createObservationGenerator(
                        CUSTOMER,
                        PROJECT,
                        METRIC,
                        REPORT.toBuilder().setEventVectorBufferMax(1).build());
        mGenerator.generateObservations(
                DAY_INDEX,
                ImmutableListMultimap.of(SYSTEM_PROFILE_1, EVENT_1, SYSTEM_PROFILE_1, EVENT_2));

        // Check that it was recorded that event vector buffer max was exceeded for the metric id
        // and report id.
        assertThat(
                        mOperationLogger.getNumEventVectorBufferMaxExceededOccurrences(
                                METRIC_ID, REPORT_ID))
                .isEqualTo(1);
    }

    @Test
    public void
            testGenerateObservations_underEventVectorBufferMaxLimit_noEventVectorBufferMaxExceededLogged()
                    throws Exception {
        mGenerator =
                createObservationGenerator(
                        CUSTOMER,
                        PROJECT,
                        METRIC,
                        REPORT.toBuilder().setEventVectorBufferMax(1).build());
        mGenerator.generateObservations(
                DAY_INDEX,
                ImmutableListMultimap.of(SYSTEM_PROFILE_1, EVENT_1, SYSTEM_PROFILE_2, EVENT_2));

        // Check that no event vector buffer max exceeded recorded for the metric id and report id.
        assertThat(
                        mOperationLogger.getNumEventVectorBufferMaxExceededOccurrences(
                                METRIC_ID, REPORT_ID))
                .isEqualTo(0);
    }
}
