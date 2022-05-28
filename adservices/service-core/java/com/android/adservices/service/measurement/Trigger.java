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

package com.android.adservices.service.measurement;

import android.annotation.IntDef;
import android.net.Uri;

import com.android.adservices.service.measurement.aggregation.AggregatableAttributionTrigger;
import com.android.adservices.service.measurement.aggregation.AggregateFilterData;
import com.android.adservices.service.measurement.aggregation.AggregateTriggerData;
import com.android.adservices.service.measurement.aggregation.AttributionAggregatableKey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * POJO for Trigger.
 */

public class Trigger {

    private String mId;
    private Long mDedupKey;
    private Uri mAttributionDestination;
    private Uri mAdTechDomain;
    private long mTriggerTime;
    private long mPriority;
    private long mEventTriggerData;
    private @Status int mStatus;
    private Uri mRegistrant;
    private String mAggregateTriggerData;
    private String mAggregateValues;
    private AggregatableAttributionTrigger mAggregatableAttributionTrigger;
    private String mFilters;

    @IntDef(value = {
            Status.PENDING,
            Status.IGNORED,
            Status.ATTRIBUTED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {

        int PENDING = 0;
        int IGNORED = 1;
        int ATTRIBUTED = 2;
    }
    private Trigger() {
        mDedupKey = null;
        mStatus = Status.PENDING;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Trigger)) {
            return false;
        }
        Trigger trigger = (Trigger) obj;
        return Objects.equals(mId, trigger.getId())
                && Objects.equals(mAttributionDestination, trigger.mAttributionDestination)
                && Objects.equals(mAdTechDomain, trigger.mAdTechDomain)
                && mTriggerTime == trigger.mTriggerTime
                && mEventTriggerData == trigger.mEventTriggerData
                && mPriority == trigger.mPriority
                && mStatus == trigger.mStatus
                && Objects.equals(mDedupKey, trigger.mDedupKey)
                && Objects.equals(mRegistrant, trigger.mRegistrant)
                && Objects.equals(mAggregateTriggerData, trigger.mAggregateTriggerData)
                && Objects.equals(mAggregateValues, trigger.mAggregateValues)
                && Objects.equals(
                        mAggregatableAttributionTrigger, trigger.mAggregatableAttributionTrigger)
                && Objects.equals(mFilters, trigger.mFilters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId,
                mAttributionDestination,
                mAdTechDomain,
                mTriggerTime,
                mEventTriggerData,
                mPriority,
                mStatus,
                mDedupKey,
                mAggregateTriggerData,
                mAggregateValues,
                mAggregatableAttributionTrigger,
                mFilters);
    }

    /**
     * Unique identifier for the {@link Trigger}.
     */
    public String getId() {
        return mId;
    }

    /**
     * Deduplication key for distinguishing among different {@link Trigger} types.
     */
    public Long getDedupKey() {
        return mDedupKey;
    }

    /**
     * Destination where {@link Trigger} occurred.
     */
    public Uri getAttributionDestination() {
        return mAttributionDestination;
    }

    /**
     * AdTech report destination domain for generated reports.
     */
    public Uri getAdTechDomain() {
        return mAdTechDomain;
    }

    /**
     * Time when the event occurred.
     */
    public long getTriggerTime() {
        return mTriggerTime;
    }

    /**
     * Current state of the {@link Trigger}.
     */
    public @Status int getStatus() {
        return mStatus;
    }

    /**
     * Set the status.
     */
    public void setStatus(@Status int status) {
        mStatus = status;
    }

    /**
     * Priority used for selecting among {@link Trigger}.
     */
    public long getPriority() {
        return mPriority;
    }

    /**
     * Metadata for the {@link Trigger}.
     */
    public long getEventTriggerData() {
        return mEventTriggerData;
    }

    /**
     * Registrant of this trigger, primarily an App.
     */
    public Uri getRegistrant() {
        return mRegistrant;
    }

    /**
     * Returns aggregate trigger data string used for aggregation. aggregate trigger data json is a
     * JSONArray.
     * example:
     * [
     * // Each dict independently adds pieces to multiple source keys.
     * {
     *   // Conversion type purchase = 2 at a 9 bit offset, i.e. 2 << 9.
     *   // A 9 bit offset is needed because there are 511 possible campaigns, which
     *   // will take up 9 bits in the resulting key.
     *   "key_piece": "0x400",
     *   // Apply this key piece to:
     *   "source_keys": ["campaignCounts"]
     * },
     * {
     *   // Purchase category shirts = 21 at a 7 bit offset, i.e. 21 << 7.
     *   // A 7 bit offset is needed because there are ~100 regions for the geo key,
     *   // which will take up 7 bits of space in the resulting key.
     *   "key_piece": "0xA80",
     *   // Apply this key piece to:
     *   "source_keys": ["geoValue", "nonMatchingKeyIdsAreIgnored"]
     * }
     * ]
     */
    public String getAggregateTriggerData() {
        return mAggregateTriggerData;
    }

    /**
     * Returns aggregate value string used for aggregation. aggregate value json is a JSONObject.
     * example:
     * {
     *   "campaignCounts": 32768,
     *   "geoValue": 1664
     * }
     */
    public String getAggregateValues() {
        return mAggregateValues;
    }

    /**
     * Returns the AggregatableAttributionTrigger object, which is constructed using the aggregate
     * trigger data string and aggregate values string in Trigger.
     */
    public AggregatableAttributionTrigger getAggregatableAttributionTrigger() {
        return mAggregatableAttributionTrigger;
    }

    /**
     * Returns top level filters. The value is in json format.
     *
     * <p>Will be used for deciding if the trigger can be attributed to the source. If the source
     * fails the filtering against these filters then no reports(event/aggregate) are generated.
     * example: { "key1" : ["value11", "value12"], "key2" : ["value21", "value22"] }
     */
    public String getFilters() {
        return mFilters;
    }

    /**
     * Function to truncate trigger data to 3-bit or 1-bit based on {@link Source.SourceType}
     *
     * @param source for which trigger data is being retrieved
     * @return truncated trigger data
     */
    public long getTruncatedTriggerData(Source source) {
        return mEventTriggerData % source.getTriggerDataCardinality();
    }

    /**
     * Generates AggregatableAttributionTrigger from aggregate trigger data string and aggregate
     * values string in Trigger.
     */
    public Optional<AggregatableAttributionTrigger> parseAggregateTrigger()
            throws JSONException, NumberFormatException {
        if (this.mAggregateTriggerData == null || this.mAggregateValues == null) {
            return Optional.empty();
        }
        JSONArray jsonArray = new JSONArray(this.mAggregateTriggerData);
        List<AggregateTriggerData> triggerDataList = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            String hexString = jsonObject.getString("key_piece");
            if (hexString.startsWith("0x")) {
                hexString = hexString.substring(2);
            }
            BigInteger bigInteger = new BigInteger(hexString, 16);
            BigInteger divisor = BigDecimal.valueOf(Math.pow(2, 63)).toBigInteger();
            JSONArray sourceKeys = jsonObject.getJSONArray("source_keys");
            Set<String> sourceKeySet = new HashSet<>();
            for (int j = 0; j < sourceKeys.length(); j++) {
                sourceKeySet.add(sourceKeys.getString(j));
            }
            AggregateTriggerData.Builder builder =
                    new AggregateTriggerData.Builder()
                            .setKey(new AttributionAggregatableKey.Builder()
                                    .setHighBits(bigInteger.divide(divisor).longValue())
                                    .setLowBits(bigInteger.mod(divisor).longValue())
                                    .build())
                            .setSourceKeys(sourceKeySet);
            if (jsonObject.has("filters") && !jsonObject.isNull("filters")) {
                AggregateFilterData filters = new AggregateFilterData.Builder()
                        .buildAggregateFilterData(jsonObject.getJSONObject("filters")).build();
                builder.setFilter(filters);
            }
            if (jsonObject.has("not_filters")
                    && !jsonObject.isNull("not_filters")) {
                AggregateFilterData notFilters = new AggregateFilterData.Builder()
                        .buildAggregateFilterData(
                                jsonObject.getJSONObject("not_filters")).build();
                builder.setNotFilter(notFilters);
            }
            triggerDataList.add(builder.build());
        }
        JSONObject values = new JSONObject(this.mAggregateValues);
        Map<String, Integer> valueMap = new HashMap<>();
        for (String key : values.keySet()) {
            valueMap.put(key, values.getInt(key));
        }
        return Optional.of(new AggregatableAttributionTrigger.Builder()
                .setTriggerData(triggerDataList).setValues(valueMap).build());
    }

    /**
     * Builder for {@link Trigger}.
     */
    public static final class Builder {

        private final Trigger mBuilding;

        public Builder() {
            mBuilding = new Trigger();
        }

        /**
         * See {@link Trigger#getId()}.
         */
        public Builder setId(String id) {
            mBuilding.mId = id;
            return this;
        }

        /**
         * See {@link Trigger#getPriority()}.
         */
        public Builder setPriority(long priority) {
            mBuilding.mPriority = priority;
            return this;
        }

        /**
         * See {@link Trigger#getAttributionDestination()}.
         */
        public Builder setAttributionDestination(Uri attributionDestination) {
            mBuilding.mAttributionDestination = attributionDestination;
            return this;
        }

        /**
         * See {@link Trigger#getAdTechDomain()} ()}.
         */
        public Builder setAdTechDomain(Uri adTechDomain) {
            mBuilding.mAdTechDomain = adTechDomain;
            return this;
        }

        /**
         * See {@link Trigger#getStatus()}.
         */
        public Builder setStatus(@Status int status) {
            mBuilding.mStatus = status;
            return this;
        }

        /**
         * See {@link Trigger#getEventTriggerData()} ()}.
         */
        public Builder setEventTriggerData(long eventTriggerData) {
            mBuilding.mEventTriggerData = eventTriggerData;
            return this;
        }

        /**
         * See {@link Trigger#getDedupKey()}.
         */
        public Builder setDedupKey(Long dedupKey) {
            mBuilding.mDedupKey = dedupKey;
            return this;
        }

        /**
         * See {@link Trigger#getTriggerTime()}.
         */
        public Builder setTriggerTime(long triggerTime) {
            mBuilding.mTriggerTime = triggerTime;
            return this;
        }

        /**
         * See {@link Trigger#getRegistrant()}
         */
        public Builder setRegistrant(Uri registrant) {
            mBuilding.mRegistrant = registrant;
            return this;
        }

        /**
         * See {@link Trigger#getAggregateTriggerData()}.
         */
        public Builder setAggregateTriggerData(String aggregateTriggerData) {
            mBuilding.mAggregateTriggerData = aggregateTriggerData;
            return this;
        }

        /**
         * See {@link Trigger#getAggregateValues()}
         */
        public Builder setAggregateValues(String aggregateValues) {
            mBuilding.mAggregateValues = aggregateValues;
            return this;
        }

        /** See {@link Trigger#getFilters()} */
        public Builder setFilters(String filters) {
            mBuilding.mFilters = filters;
            return this;
        }

        /**
         * See {@link Trigger#getAggregatableAttributionTrigger()}
         */
        public Builder setAggregatableAttributionTrigger(
                AggregatableAttributionTrigger aggregatableAttributionTrigger) {
            mBuilding.mAggregatableAttributionTrigger = aggregatableAttributionTrigger;
            return this;
        }

        /**
         * Build the {@link Trigger}.
         */
        public Trigger build() {
            return mBuilding;
        }
    }
}
