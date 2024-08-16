/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.cobalt.testing.observations;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.cobalt.data.EventVector;

import com.google.cobalt.IndexHistogram;
import com.google.cobalt.IntegerObservation;
import com.google.cobalt.Observation;
import com.google.cobalt.StringHistogramObservation;
import com.google.common.hash.HashCode;
import com.google.protobuf.ByteString;

import org.junit.Test;

public final class ObservationFactoryTest extends AdServicesUnitTestCase {
    private static final EventVector EVENT_VECTOR_1 = EventVector.create(1, 5);
    private static final EventVector EVENT_VECTOR_2 = EventVector.create(2, 6);
    private static final ByteString RANDOM_BYTES =
            ByteString.copyFrom(new byte[] {1, 1, 1, 1, 1, 1, 1, 1});

    @Test
    public void testCreateIntegerObservation_oneValue() throws Exception {
        Observation observation =
                ObservationFactory.createIntegerObservation(
                        EVENT_VECTOR_1, /* countValue= */ 5, RANDOM_BYTES);
        expect.withMessage("observation.getRandomId()")
                .that(observation.getRandomId())
                .isEqualTo(RANDOM_BYTES);
        assertThat(observation.hasInteger()).isTrue();
        expect.withMessage("observation.getInteger().getValuesList()")
                .that(observation.getInteger().getValuesList())
                .containsExactly(
                        IntegerObservation.Value.newBuilder()
                                .addAllEventCodes(EVENT_VECTOR_1.eventCodes())
                                .setValue(5)
                                .build());
    }

    @Test
    public void testCreateIntegerObservation_twoValues() throws Exception {
        Observation observation =
                ObservationFactory.createIntegerObservation(
                        EVENT_VECTOR_1,
                        /* countValue1= */ 5,
                        EVENT_VECTOR_2,
                        /* countValue2= */ 7,
                        RANDOM_BYTES);
        expect.withMessage("observation.getRandomId()")
                .that(observation.getRandomId())
                .isEqualTo(RANDOM_BYTES);
        assertThat(observation.hasInteger()).isTrue();
        expect.withMessage("observation.getInteger().getValuesList()")
                .that(observation.getInteger().getValuesList())
                .containsExactly(
                        IntegerObservation.Value.newBuilder()
                                .addAllEventCodes(EVENT_VECTOR_1.eventCodes())
                                .setValue(5)
                                .build(),
                        IntegerObservation.Value.newBuilder()
                                .addAllEventCodes(EVENT_VECTOR_2.eventCodes())
                                .setValue(7)
                                .build());
    }

    @Test
    public void testCreatePrivateIndexObservation() throws Exception {
        Observation observation =
                ObservationFactory.createPrivateIndexObservation(
                        /* privateIndex= */ 5, RANDOM_BYTES);
        expect.withMessage("observation.getRandomId()")
                .that(observation.getRandomId())
                .isEqualTo(RANDOM_BYTES);
        assertThat(observation.hasPrivateIndex()).isTrue();
        expect.withMessage("observation.getPrivateIndex().getIndex()")
                .that(observation.getPrivateIndex().getIndex())
                .isEqualTo(5);
    }

    @Test
    public void testCreateReportParticipationObservation() throws Exception {
        Observation observation =
                ObservationFactory.createReportParticipationObservation(RANDOM_BYTES);
        expect.withMessage("observation.getRandomId()")
                .that(observation.getRandomId())
                .isEqualTo(RANDOM_BYTES);
        expect.withMessage("observation.hasReportParticipation()")
                .that(observation.hasReportParticipation())
                .isTrue();
    }

    @Test
    public void testCreateIndexHistogram_oneIndexCountPair() throws Exception {
        IndexHistogram indexHistogram =
                ObservationFactory.createIndexHistogram(
                        EVENT_VECTOR_1, /* index= */ 0, /* count= */ 1L);

        expect.withMessage("indexHistogram.getEventCodesList()")
                .that(indexHistogram.getEventCodesList())
                .isEqualTo(EVENT_VECTOR_1.eventCodes());
        assertThat(indexHistogram.getBucketIndicesList()).hasSize(1);
        expect.withMessage("indexHistogram.getBucketIndices(0)")
                .that(indexHistogram.getBucketIndices(0))
                .isEqualTo(0);
        assertThat(indexHistogram.getBucketCountsList()).hasSize(1);
        expect.withMessage("indexHistogram.getBucketCounts(0)")
                .that(indexHistogram.getBucketCounts(0))
                .isEqualTo(1L);
    }

    @Test
    public void testCreateIndexHistogram_twoIndexCountPair() throws Exception {
        IndexHistogram indexHistogram =
                ObservationFactory.createIndexHistogram(
                        EVENT_VECTOR_1,
                        /* index1= */ 0,
                        /* count1= */ 1L,
                        /* index2= */ 1,
                        /* count2= */ 2L);

        expect.withMessage("indexHistogram.getEventCodesList()")
                .that(indexHistogram.getEventCodesList())
                .isEqualTo(EVENT_VECTOR_1.eventCodes());
        assertThat(indexHistogram.getBucketIndicesList()).hasSize(2);
        expect.withMessage("indexHistogram.getBucketIndices(0)")
                .that(indexHistogram.getBucketIndices(0))
                .isEqualTo(0);
        expect.withMessage("indexHistogram.getBucketIndices(1)")
                .that(indexHistogram.getBucketIndices(1))
                .isEqualTo(1);
        assertThat(indexHistogram.getBucketCountsList()).hasSize(2);
        expect.withMessage("indexHistogram.getBucketCounts(0)")
                .that(indexHistogram.getBucketCounts(0))
                .isEqualTo(1L);
        expect.withMessage("indexHistogram.getBucketCounts(1)")
                .that(indexHistogram.getBucketCounts(1))
                .isEqualTo(2L);
    }

    @Test
    public void testCopyWithStringHashesFf64() throws Exception {
        StringHistogramObservation observation = StringHistogramObservation.getDefaultInstance();
        HashCode hashCode1 = HashCode.fromBytes(new byte[] {0x0a});
        HashCode hashCode2 = HashCode.fromBytes(new byte[] {0x0b});
        observation =
                ObservationFactory.copyWithStringHashesFf64(observation, hashCode1, hashCode2);

        assertThat(observation.getStringHashesFf64List()).hasSize(2);
        expect.withMessage("observation.getStringHashesFf64(0)")
                .that(observation.getStringHashesFf64(0))
                .isEqualTo(ByteString.copyFrom(hashCode1.asBytes()));
        expect.withMessage("observation.getStringHashesFf64(1)")
                .that(observation.getStringHashesFf64(1))
                .isEqualTo(ByteString.copyFrom(hashCode2.asBytes()));
        expect.withMessage("observation.getStringHistogramsList()")
                .that(observation.getStringHistogramsList())
                .hasSize(0);
    }

    @Test
    public void testCopyWithStringHistograms() throws Exception {
        StringHistogramObservation observation = StringHistogramObservation.getDefaultInstance();
        IndexHistogram indexHistogram1 =
                ObservationFactory.createIndexHistogram(
                        EVENT_VECTOR_1, /* index= */ 0, /* count= */ 1L);
        IndexHistogram indexHistogram2 =
                ObservationFactory.createIndexHistogram(
                        EVENT_VECTOR_2, /* index= */ 1, /* count= */ 2L);
        observation =
                ObservationFactory.copyWithStringHistograms(
                        observation, indexHistogram1, indexHistogram2);

        assertThat(observation.getStringHistogramsList()).hasSize(2);
        expect.withMessage("observation.getStringHistograms(0).getEventCodesList()")
                .that(observation.getStringHistograms(0).getEventCodesList())
                .isEqualTo(EVENT_VECTOR_1.eventCodes());
        assertThat(observation.getStringHistograms(0).getBucketIndicesList()).hasSize(1);
        expect.withMessage("observation.getStringHistograms(0).getBucketIndices(0)")
                .that(observation.getStringHistograms(0).getBucketIndices(0))
                .isEqualTo(0);
        assertThat(observation.getStringHistograms(0).getBucketCountsList()).hasSize(1);
        expect.withMessage("observation.getStringHistograms(0).getBucketCounts(0)")
                .that(observation.getStringHistograms(0).getBucketCounts(0))
                .isEqualTo(1L);

        expect.withMessage("observation.getStringHistograms(1).getEventCodesList()")
                .that(observation.getStringHistograms(1).getEventCodesList())
                .isEqualTo(EVENT_VECTOR_2.eventCodes());
        assertThat(observation.getStringHistograms(1).getBucketIndicesList()).hasSize(1);
        expect.withMessage("observation.getStringHistograms(1).getBucketIndices(0)")
                .that(observation.getStringHistograms(1).getBucketIndices(0))
                .isEqualTo(1);
        assertThat(observation.getStringHistograms(1).getBucketCountsList()).hasSize(1);
        expect.withMessage("observation.getStringHistograms(1).getBucketCounts(0)")
                .that(observation.getStringHistograms(1).getBucketCounts(0))
                .isEqualTo(2L);
    }

    @Test
    public void testCreateStringHistogramObservation() throws Exception {
        Observation observation =
                ObservationFactory.createStringHistogramObservation(
                        StringHistogramObservation.getDefaultInstance(), RANDOM_BYTES);
        expect.withMessage("observation.getRandomId()")
                .that(observation.getRandomId())
                .isEqualTo(RANDOM_BYTES);
        expect.withMessage("observation.hasStringHistogram()")
                .that(observation.hasStringHistogram())
                .isTrue();
    }
}
