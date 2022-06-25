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

import com.google.common.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Class for constructing the report body of an aggregate report.
 */
public class AggregateReportBody {
    private String mAttributionDestination;
    private String mSourceRegistrationTime;
    private String mScheduledReportTime;
    private String mVersion;
    private String mReportId;
    private String mReportingOrigin;
    private String mDebugCleartextPayload;

    private static final String API_NAME = "attribution-reporting";
    private static final String API_VERSION = "1";

    private interface PayloadBodyKeys {
        String SHARED_INFO = "shared_info";
        String AGGREGATION_SERVICE_PAYLOADS = "aggregation_service_payloads";
    }

    private interface DebugCleartextPayloadKeys {
        String DEBUG_CLEARTEXT_PAYLOAD = "debug_cleartext_payload";
    }

    private interface SharedInfoKeys {
        String API_NAME = "api";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String REPORT_ID = "report_id";
        String REPORTING_ORIGIN = "reporting_origin";
        String SCHEDULED_REPORT_TIME = "scheduled_report_time";
        String SOURCE_REGISTRATION_TIME = "source_registration_time";
        String API_VERSION = "version";
    }

    private AggregateReportBody() { };

    private AggregateReportBody(AggregateReportBody other) {
        this.mAttributionDestination = other.mAttributionDestination;
        this.mSourceRegistrationTime = other.mSourceRegistrationTime;
        this.mScheduledReportTime = other.mScheduledReportTime;
        this.mVersion = other.mVersion;
        this.mReportId = other.mReportId;
        this.mReportingOrigin = other.mReportingOrigin;
        this.mDebugCleartextPayload = other.mDebugCleartextPayload;
    }

    /**
     * Generate the JSON serialization of the aggregate report.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject aggregateBodyJson = new JSONObject();
        aggregateBodyJson.put(PayloadBodyKeys.SHARED_INFO, sharedInfoToJson().toString());
        aggregateBodyJson.put(PayloadBodyKeys.AGGREGATION_SERVICE_PAYLOADS,
                aggregationServicePayloadsToJson());
        return aggregateBodyJson;
    }

    /**
     * Generate the JSON serialization of the shared_info field of the aggregate report.
     */
    @VisibleForTesting
    JSONObject sharedInfoToJson() throws JSONException {
        JSONObject sharedInfoJson = new JSONObject();

        sharedInfoJson.put(SharedInfoKeys.API_NAME, API_NAME);
        sharedInfoJson.put(SharedInfoKeys.ATTRIBUTION_DESTINATION, this.mAttributionDestination);
        sharedInfoJson.put(SharedInfoKeys.REPORT_ID, this.mReportId);
        sharedInfoJson.put(SharedInfoKeys.REPORTING_ORIGIN, this.mReportingOrigin);
        sharedInfoJson.put(SharedInfoKeys.SCHEDULED_REPORT_TIME, this.mScheduledReportTime);
        sharedInfoJson.put(SharedInfoKeys.SOURCE_REGISTRATION_TIME, this.mSourceRegistrationTime);
        sharedInfoJson.put(SharedInfoKeys.API_VERSION, API_VERSION);

        return sharedInfoJson;
    }

    /**
     * Generate the JSON array serialization of the aggregation service payloads field.
     */
    @VisibleForTesting
    JSONArray aggregationServicePayloadsToJson() throws JSONException {
        JSONArray aggregationServicePayloadsJson = new JSONArray();

        JSONObject debugCleartextPayloadJson = new JSONObject();
        debugCleartextPayloadJson.put(DebugCleartextPayloadKeys.DEBUG_CLEARTEXT_PAYLOAD,
                this.mDebugCleartextPayload);

        aggregationServicePayloadsJson.put(debugCleartextPayloadJson);

        return aggregationServicePayloadsJson;
    }

    /**
     * Builder class for AggregateReportBody.
     */
    public static final class Builder {
        private AggregateReportBody mBuilding;

        public Builder() {
            mBuilding = new AggregateReportBody();
        }

        /**
         * The attribution destination set on the source.
         */
        public @NonNull Builder setAttributionDestination(@NonNull String attributionDestination) {
            mBuilding.mAttributionDestination = attributionDestination;
            return this;
        }

        /**
         * The registration time of the source.
         */
        public @NonNull Builder setSourceRegistrationTime(@NonNull String sourceRegistrationTime) {
            mBuilding.mSourceRegistrationTime = sourceRegistrationTime;
            return this;
        }

        /**
         * The initial scheduled report time for the report.
         */
        public @NonNull Builder setScheduledReportTime(@NonNull String scheduledReportTime) {
            mBuilding.mScheduledReportTime = scheduledReportTime;
            return this;
        }

        /**
         * The version of the API used to generate the aggregate report.
         */
        public @NonNull Builder setVersion(@NonNull String version) {
            mBuilding.mVersion = version;
            return this;
        }

        /**
         * The ad tech domain for the report.
         */
        public @NonNull Builder setReportingOrigin(@NonNull String reportingOrigin) {
            mBuilding.mReportingOrigin = reportingOrigin;
            return this;
        }

        /**
         * The unique id for this report.
         */
        public @NonNull Builder setReportId(@NonNull String reportId) {
            mBuilding.mReportId = reportId;
            return this;
        }

        /**
         * The cleartext payload for debug.
         */
        public @NonNull Builder setDebugCleartextPayload(@NonNull String debugCleartextPayload) {
            mBuilding.mDebugCleartextPayload = debugCleartextPayload;
            return this;
        }

        /**
         * Build the AggregateReportBody.
         */
        public @NonNull AggregateReportBody build() {
            return new AggregateReportBody(mBuilding);
        }
    }

}
