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

import com.google.cobalt.IndexHistogram;
import com.google.cobalt.IntegerObservation;
import com.google.cobalt.Observation;
import com.google.cobalt.PrivateIndexObservation;
import com.google.cobalt.ReportParticipationObservation;
import com.google.cobalt.StringHistogramObservation;
import com.google.common.hash.HashCode;
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

    /** Create a PrivateIndexObservation for the privateIndex. */
    public static Observation createPrivateIndexObservation(
            long privateIndex, ByteString randomId) {
        return Observation.newBuilder()
                .setRandomId(randomId)
                .setPrivateIndex(PrivateIndexObservation.newBuilder().setIndex(privateIndex))
                .build();
    }

    /** Create a ReportParticipationObservation. */
    public static Observation createReportParticipationObservation(ByteString randomId) {
        return Observation.newBuilder()
                .setRandomId(randomId)
                .setReportParticipation(ReportParticipationObservation.getDefaultInstance())
                .build();
    }

    /** Creates an IndexHistogram for eventVector with index and count pair. */
    public static IndexHistogram createIndexHistogram(
            EventVector eventVector, int index, long count) {
        return IndexHistogram.newBuilder()
                .addAllEventCodes(eventVector.eventCodes())
                .addBucketIndices(index)
                .addBucketCounts(count)
                .build();
    }

    /** Creates an IndexHistogram for eventVector containing 2 index and count pairs. */
    public static IndexHistogram createIndexHistogram(
            EventVector eventVector, int index1, long count1, int index2, long count2) {
        return IndexHistogram.newBuilder()
                .addAllEventCodes(eventVector.eventCodes())
                .addBucketIndices(index1)
                .addBucketCounts(count1)
                .addBucketIndices(index2)
                .addBucketCounts(count2)
                .build();
    }

    /** Copy stringHistogramObservation with hashCodes. */
    public static StringHistogramObservation copyWithStringHashesFf64(
            StringHistogramObservation stringHistogramObservation, HashCode... hashCodes) {
        StringHistogramObservation.Builder builder = stringHistogramObservation.toBuilder();
        for (int i = 0; i < hashCodes.length; ++i) {
            builder.addStringHashesFf64(ByteString.copyFrom(hashCodes[i].asBytes()));
        }
        return builder.build();
    }

    /** Copy stringHistogramObservation with indexHistograms. */
    public static StringHistogramObservation copyWithStringHistograms(
            StringHistogramObservation stringHistogramObservation,
            IndexHistogram... indexHistograms) {
        StringHistogramObservation.Builder builder = stringHistogramObservation.toBuilder();
        for (int i = 0; i < indexHistograms.length; ++i) {
            builder.addStringHistograms(indexHistograms[i]);
        }
        return builder.build();
    }

    /** Creates a StringHistogramObservation for the stringHistogramObservation. */
    public static Observation createStringHistogramObservation(
            StringHistogramObservation stringHistogramObservation, ByteString randomId) {
        return Observation.newBuilder()
                .setStringHistogram(stringHistogramObservation)
                .setRandomId(randomId)
                .build();
    }
}
