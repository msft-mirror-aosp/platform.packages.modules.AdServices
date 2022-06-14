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

package com.android.adservices.service.measurement.reporting;

import android.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * EventReportPayload.
 */
public final class EventReportPayload {

    private String mAttributionDestination;
    private String mSourceEventId;
    private String mTriggerData;
    private String mReportId;
    private String mSourceType;
    private double mRandomizedTriggerRate;

    private EventReportPayload() {};

    private EventReportPayload(EventReportPayload other) {
        this.mAttributionDestination = other.mAttributionDestination;
        this.mSourceEventId = other.mSourceEventId;
        this.mTriggerData = other.mTriggerData;
        this.mReportId = other.mReportId;
        this.mSourceType = other.mSourceType;
        this.mRandomizedTriggerRate = other.mRandomizedTriggerRate;
    }

    /**
     * Generate the JSON serialization of the event report.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject eventPayloadJson = new JSONObject();

        eventPayloadJson.put("attribution_destination", this.mAttributionDestination);
        eventPayloadJson.put("source_event_id", this.mSourceEventId);
        eventPayloadJson.put("trigger_data", this.mTriggerData);
        eventPayloadJson.put("report_id", this.mReportId);
        eventPayloadJson.put("source_type", this.mSourceType);
        eventPayloadJson.put("randomized_trigger_rate", this.mRandomizedTriggerRate);

        return eventPayloadJson;
    }

    /**
     * Builder class for EventPayloadGenerator.
     */
    public static final class Builder {
        private EventReportPayload mBuilding;

        public Builder() {
            mBuilding = new EventReportPayload();
        }

        /**
         * The attribution destination set on the source.
         */
        public @NonNull Builder setAttributionDestination(@NonNull String attributionDestination) {
            mBuilding.mAttributionDestination = attributionDestination;
            return this;
        }

        /**
         * 64-bit event id set on the attribution source.
         */
        public @NonNull Builder setSourceEventId(@NonNull String sourceEventId) {
            mBuilding.mSourceEventId = sourceEventId;
            return this;
        }

        /**
         * Course data set in the attribution trigger registration.
         */
        public @NonNull Builder setTriggerData(@NonNull String triggerData) {
            mBuilding.mTriggerData = triggerData;
            return this;
        }

        /**
         * A unique id for this report which can be used to prevent double counting.
         */
        public @NonNull Builder setReportId(@NonNull String reportId) {
            mBuilding.mReportId = reportId;
            return this;
        }

        /**
         * Either "navigation" or "event", indicates whether this source was associated with a
         * navigation.
         */
        public @NonNull Builder setSourceType(@NonNull String sourceType) {
            mBuilding.mSourceType = sourceType;
            return this;
        }

        /**
         * Decimal number between 0 and 1 indicating how often noise is applied.
         */
        public @NonNull Builder setRandomizedTriggerRate(@NonNull double randomizedTriggerRate) {
            mBuilding.mRandomizedTriggerRate = randomizedTriggerRate;
            return this;
        }

        /**
         * Build the EventReportPayload.
         */
        public @NonNull EventReportPayload build() {
            return new EventReportPayload(mBuilding);
        }
    }
}
