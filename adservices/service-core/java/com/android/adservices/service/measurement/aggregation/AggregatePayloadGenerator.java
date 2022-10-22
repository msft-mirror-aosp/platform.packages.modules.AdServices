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

package com.android.adservices.service.measurement.aggregation;

import com.android.adservices.service.measurement.util.Filter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Class used to generate AggregateReport using AggregatableAttributionSource and
 * AggregatableAttributionTrigger.
 */
public class AggregatePayloadGenerator {

    private AggregatePayloadGenerator() {}

    /**
     * Generates the {@link AggregateReport} from given AggregatableAttributionSource and
     * AggregatableAttributionTrigger.
     *
     * @param attributionSource the aggregate attribution source used for aggregation.
     * @param attributionTrigger the aggregate attribution trigger used for aggregation.
     * @return the aggregate report generated by the given aggregate attribution source and
     *     aggregate attribution trigger.
     */
    public static Optional<List<AggregateHistogramContribution>> generateAttributionReport(
            AggregatableAttributionSource attributionSource,
            AggregatableAttributionTrigger attributionTrigger) {
        AggregateFilterData sourceFilterData = attributionSource.getAggregateFilterData();
        Map<String, BigInteger> aggregateKeys = new HashMap<>();
        Map<String, BigInteger> aggregateSourceMap =
                attributionSource.getAggregatableSource();
        for (String sourceKey : aggregateSourceMap.keySet()) {
            for (AggregateTriggerData triggerData : attributionTrigger.getTriggerData()) {
                Optional<AggregateFilterData> filterData = triggerData.getFilter();
                Optional<AggregateFilterData> notFilterData = triggerData.getNotFilter();
                // Skip this trigger data when filter doesn't match.
                if (filterData.isPresent()
                        && !Filter.isFilterMatch(sourceFilterData, filterData.get(), true)) {
                    continue;
                }
                // Skip this trigger data when not_filters doesn't match.
                if (notFilterData.isPresent()
                        && !Filter.isFilterMatch(
                                sourceFilterData, notFilterData.get(), false)) {
                    continue;
                }
                if (triggerData.getSourceKeys().contains(sourceKey)) {
                    BigInteger currentKey = aggregateSourceMap.get(sourceKey);
                    BigInteger triggerKey = triggerData.getKey();
                    BigInteger currentInt;
                    if (aggregateKeys.containsKey(sourceKey)) {
                        currentInt = aggregateKeys.get(sourceKey);
                    } else {
                        currentInt = currentKey;
                    }
                    aggregateKeys.put(sourceKey, currentInt.or(triggerKey));
                }
            }
        }

        List<AggregateHistogramContribution> contributions = new ArrayList<>();
        for (String key : attributionTrigger.getValues().keySet()) {
            if (aggregateKeys.containsKey(key)) {
                AggregateHistogramContribution contribution =
                        new AggregateHistogramContribution.Builder()
                                .setKey(aggregateKeys.get(key))
                                .setValue(attributionTrigger.getValues().get(key)).build();
                contributions.add(contribution);
            }
        }
        if (contributions.size() > 0) {
            return Optional.of(contributions);
        }
        return Optional.empty();
    }
}
