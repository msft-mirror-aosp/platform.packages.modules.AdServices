/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.shared.metriclogger.logsampler;

import com.android.adservices.shared.proto.DimensionMatcher;
import com.android.adservices.shared.proto.DimensionName;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.function.Function;

/** Static methods to calculate sampling rate when dimension matching is satisfied. */
public final class DimensionMatcherHelper {
    private DimensionMatcherHelper() {
        throw new IllegalArgumentException("static methods");
    }

    /**
     * Determines the adjusted sampling rate for a given event based on matching against a list of
     * dimension matchers.
     *
     * @param dimensionMatchers A list of {@link DimensionMatcher} rules to evaluate against the
     *     event.
     * @param dimensionNameToValueExtractor A map providing functions to extract dimension value
     *     from the event based on dimension name.
     * @param eventSupplier The event object from which dimension values will be extracted.
     * @param defaultSamplingRate The sampling rate to return if no dimension matcher is satisfied.
     * @param <L> the type of the log event.
     * @return the adjusted sample rate or the default.
     */
    public static <L> double getDimensionSampleRateOrDefault(
            List<DimensionMatcher> dimensionMatchers,
            ImmutableMap<DimensionName, Function<L, Integer>> dimensionNameToValueExtractor,
            Supplier<L> eventSupplier,
            double defaultSamplingRate) {
        if (dimensionMatchers == null || dimensionMatchers.isEmpty()) {
            return defaultSamplingRate;
        }

        L event = eventSupplier.get();
        for (DimensionMatcher dimensionMatcher : dimensionMatchers) {
            boolean matchFound =
                    dimensionMatcher.getDimensionList().stream()
                            .allMatch(
                                    dimension -> {
                                        DimensionName name = dimension.getName();
                                        Function<L, Integer> valueExtractor =
                                                dimensionNameToValueExtractor.get(name);
                                        Integer extractedValue =
                                                (valueExtractor != null)
                                                        ? valueExtractor.apply(event)
                                                        : null;
                                        return valueExtractor != null
                                                && extractedValue != null
                                                && dimension
                                                        .getValueList()
                                                        .contains(extractedValue);
                                    });
            if (matchFound) {
                return dimensionMatcher.getSamplingRate();
            }
        }

        return defaultSamplingRate;
    }
}
