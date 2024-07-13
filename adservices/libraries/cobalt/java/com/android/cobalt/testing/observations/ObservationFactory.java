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

import com.android.cobalt.data.EventVector;

import com.google.cobalt.IntegerObservation;
import com.google.cobalt.Observation;
import com.google.protobuf.ByteString;

public final class ObservationFactory {
    /** Creates an IntegerObservation for the eventVector and countValue. */
    public static Observation createIntegerObservation(
            EventVector eventVector, long countValue, ByteString randomId) {
        return Observation.newBuilder()
                .setRandomId(randomId)
                .setInteger(
                        IntegerObservation.newBuilder()
                                .addValues(
                                        IntegerObservation.Value.newBuilder()
                                                .addAllEventCodes(eventVector.eventCodes())
                                                .setValue(countValue)))
                .build();
    }

    /** Creates an IntegerObservation containing 2 eventVectors and countValues. */
    public static Observation createIntegerObservation(
            EventVector eventVector1,
            long countValue1,
            EventVector eventVector2,
            long countValue2,
            ByteString randomId) {
        return Observation.newBuilder()
                .setRandomId(randomId)
                .setInteger(
                        IntegerObservation.newBuilder()
                                .addValues(
                                        IntegerObservation.Value.newBuilder()
                                                .addAllEventCodes(eventVector1.eventCodes())
                                                .setValue(countValue1))
                                .addValues(
                                        IntegerObservation.Value.newBuilder()
                                                .addAllEventCodes(eventVector2.eventCodes())
                                                .setValue(countValue2)))
                .build();
    }
}
