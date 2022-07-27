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
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.service.measurement.aggregation.AggregatableAttributionTrigger;
import com.android.adservices.service.measurement.aggregation.AggregateFilterData;
import com.android.adservices.service.measurement.aggregation.AggregateTriggerData;
import com.android.adservices.service.measurement.util.Validation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
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
    private Uri mAttributionDestination;
    private Uri mAdTechDomain;
    private long mTriggerTime;
    private String mEventTriggers;
    @Status private int mStatus;
    private Uri mRegistrant;
    private String mAggregateTriggerData;
    private String mAggregateValues;
    private AggregatableAttributionTrigger mAggregatableAttributionTrigger;
    private String mFilters;
    private @Nullable Long mDebugKey;

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
                && Objects.equals(mDebugKey, trigger.mDebugKey)
                && Objects.equals(mEventTriggers, trigger.mEventTriggers)
                && mStatus == trigger.mStatus
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
                mEventTriggers,
                mStatus,
                mAggregateTriggerData,
                mAggregateValues,
                mAggregatableAttributionTrigger,
                mFilters,
                mDebugKey);
    }

    /**
     * Unique identifier for the {@link Trigger}.
     */
    public String getId() {
        return mId;
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
     * Event triggers containing priority, de-dup key, trigger data and event-level filters info.
     */
    public String getEventTriggers() {
        return mEventTriggers;
    }

    /** Current state of the {@link Trigger}. */
    @Status
    public int getStatus() {
        return mStatus;
    }

    /**
     * Set the status.
     */
    public void setStatus(@Status int status) {
        mStatus = status;
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

    /** Debug key of {@link Trigger}. */
    public @Nullable Long getDebugKey() {
        return mDebugKey;
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
            // Do not process trigger if a key exceeds 128 bits.
            if (hexString.length() > 32) {
                return Optional.empty();
            }
            BigInteger bigInteger = new BigInteger(hexString, 16);
            JSONArray sourceKeys = jsonObject.getJSONArray("source_keys");
            Set<String> sourceKeySet = new HashSet<>();
            for (int j = 0; j < sourceKeys.length(); j++) {
                sourceKeySet.add(sourceKeys.getString(j));
            }
            AggregateTriggerData.Builder builder =
                    new AggregateTriggerData.Builder()
                            .setKey(bigInteger)
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
     * Parses the json array under {@link #mEventTriggers} to form a list of {@link EventTrigger}s.
     *
     * @return list of {@link EventTrigger}s
     * @throws JSONException if JSON parsing fails
     */
    public List<EventTrigger> parseEventTriggers() throws JSONException {
        JSONArray jsonArray = new JSONArray(this.mEventTriggers);
        List<EventTrigger> eventTriggers = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            EventTrigger.Builder eventTriggerBuilder = new EventTrigger.Builder();
            JSONObject eventTriggersJsonString = jsonArray.getJSONObject(i);

            if (!eventTriggersJsonString.isNull(EventTriggerContract.TRIGGER_DATA)) {
                eventTriggerBuilder.setTriggerData(
                        eventTriggersJsonString.getLong(EventTriggerContract.TRIGGER_DATA));
            }

            if (!eventTriggersJsonString.isNull(EventTriggerContract.PRIORITY)) {
                eventTriggerBuilder.setTriggerPriority(
                        eventTriggersJsonString.getLong(EventTriggerContract.PRIORITY));
            }

            if (!eventTriggersJsonString.isNull(EventTriggerContract.DEDUPLICATION_KEY)) {
                eventTriggerBuilder.setDedupKey(
                        eventTriggersJsonString.getLong(EventTriggerContract.DEDUPLICATION_KEY));
            }

            if (!eventTriggersJsonString.isNull(EventTriggerContract.FILTERS)) {
                AggregateFilterData filters =
                        new AggregateFilterData.Builder()
                                .buildAggregateFilterData(
                                        eventTriggersJsonString.getJSONObject(
                                                EventTriggerContract.FILTERS))
                                .build();
                eventTriggerBuilder.setFilter(filters);
            }

            if (!eventTriggersJsonString.isNull(EventTriggerContract.NOT_FILTERS)) {
                AggregateFilterData notFilters =
                        new AggregateFilterData.Builder()
                                .buildAggregateFilterData(
                                        eventTriggersJsonString.getJSONObject(
                                                EventTriggerContract.NOT_FILTERS))
                                .build();
                eventTriggerBuilder.setNotFilter(notFilters);
            }
            eventTriggers.add(eventTriggerBuilder.build());
        }

        return eventTriggers;
    }

    public DestinationType getDestinationType() {
        return DestinationType.getDestinationType(mAttributionDestination);
    }

    /**
     * Builder for {@link Trigger}.
     */
    public static final class Builder {

        private final Trigger mBuilding;

        public Builder() {
            mBuilding = new Trigger();
        }

        public Builder(Trigger trigger) {
            mBuilding = trigger;
        }

        /** See {@link Trigger#getId()}. */
        @NonNull
        public Builder setId(String id) {
            mBuilding.mId = id;
            return this;
        }

        /** See {@link Trigger#getAttributionDestination()}. */
        @NonNull
        public Builder setAttributionDestination(Uri attributionDestination) {
            Validation.validateUri(attributionDestination);
            mBuilding.mAttributionDestination = attributionDestination;
            return this;
        }

        /** See {@link Trigger#getAdTechDomain()} ()}. */
        @NonNull
        public Builder setAdTechDomain(Uri adTechDomain) {
            Validation.validateUri(adTechDomain);
            mBuilding.mAdTechDomain = adTechDomain;
            return this;
        }

        /** See {@link Trigger#getStatus()}. */
        @NonNull
        public Builder setStatus(@Status int status) {
            mBuilding.mStatus = status;
            return this;
        }

        /** See {@link Trigger#getTriggerTime()}. */
        @NonNull
        public Builder setTriggerTime(long triggerTime) {
            mBuilding.mTriggerTime = triggerTime;
            return this;
        }

        /** See {@link Trigger#getEventTriggers()}. */
        @NonNull
        public Builder setEventTriggers(@NonNull String eventTriggers) {
            Validation.validateNonNull(eventTriggers);
            mBuilding.mEventTriggers = eventTriggers;
            return this;
        }

        /** See {@link Trigger#getRegistrant()} */
        @NonNull
        public Builder setRegistrant(@NonNull Uri registrant) {
            Validation.validateUri(registrant);
            mBuilding.mRegistrant = registrant;
            return this;
        }

        /** See {@link Trigger#getAggregateTriggerData()}. */
        @NonNull
        public Builder setAggregateTriggerData(@Nullable String aggregateTriggerData) {
            mBuilding.mAggregateTriggerData = aggregateTriggerData;
            return this;
        }

        /** See {@link Trigger#getAggregateValues()} */
        @NonNull
        public Builder setAggregateValues(@Nullable String aggregateValues) {
            mBuilding.mAggregateValues = aggregateValues;
            return this;
        }

        /** See {@link Trigger#getFilters()} */
        @NonNull
        public Builder setFilters(@Nullable String filters) {
            mBuilding.mFilters = filters;
            return this;
        }

        /** See {@link Trigger#getDebugKey()} ()} */
        public Builder setDebugKey(@Nullable Long debugKey) {
            mBuilding.mDebugKey = debugKey;
            return this;
        }

        /** See {@link Trigger#getAggregatableAttributionTrigger()} */
        @NonNull
        public Builder setAggregatableAttributionTrigger(
                @Nullable AggregatableAttributionTrigger aggregatableAttributionTrigger) {
            mBuilding.mAggregatableAttributionTrigger = aggregatableAttributionTrigger;
            return this;
        }

        /** Build the {@link Trigger}. */
        @NonNull
        public Trigger build() {
            Validation.validateNonNull(
                    mBuilding.mAttributionDestination,
                    mBuilding.mAdTechDomain,
                    mBuilding.mRegistrant);

            return mBuilding;
        }
    }

    /** Event trigger field keys. */
    public interface EventTriggerContract {
        String TRIGGER_DATA = "trigger_data";
        String PRIORITY = "priority";
        String DEDUPLICATION_KEY = "deduplication_key";
        String FILTERS = "filters";
        String NOT_FILTERS = "not_filters";
    }
}
