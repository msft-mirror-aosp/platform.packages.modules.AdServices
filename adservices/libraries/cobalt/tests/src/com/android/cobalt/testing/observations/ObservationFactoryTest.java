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

import com.android.cobalt.data.EventVector;

import com.google.cobalt.IntegerObservation;
import com.google.cobalt.Observation;
import com.google.protobuf.ByteString;

import org.junit.Test;

public final class ObservationFactoryTest {
    private static final EventVector EVENT_VECTOR_1 = EventVector.create(1, 5);
    private static final EventVector EVENT_VECTOR_2 = EventVector.create(2, 6);
    private static final ByteString RANDOM_BYTES =
            ByteString.copyFrom(new byte[] {1, 1, 1, 1, 1, 1, 1, 1});

    @Test
    public void testCreateIntegerObservation_oneValue() throws Exception {
        Observation observation =
                ObservationFactory.createIntegerObservation(
                        EVENT_VECTOR_1, /* countValue= */ 5, RANDOM_BYTES);
        assertThat(observation.getRandomId()).isEqualTo(RANDOM_BYTES);
        assertThat(observation.hasInteger()).isTrue();
        assertThat(observation.getInteger().getValuesList())
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
        assertThat(observation.getRandomId()).isEqualTo(RANDOM_BYTES);
        assertThat(observation.hasInteger()).isTrue();
        assertThat(observation.getInteger().getValuesList())
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
}
