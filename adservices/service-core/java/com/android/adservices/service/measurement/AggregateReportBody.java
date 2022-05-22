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

import android.annotation.NonNull;

import com.google.common.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Class for constructing the report body of an aggregate report.
 */
public class AggregateReportBody {
    private String mSourceSite;
    private String mAttributionDestination;
    private String mSourceRegistrationTime;
    private String mScheduledReportTime;
    private String mPrivacyBudgetKey;
    private String mVersion;
    private String mReportId;
    private String mReportingOrigin;
    private String mDebugCleartextPayload;

    private static final String API_VERSION = "1";
    private static final String SOURCE_SITE_KEY = "source_site";
    private static final String ATTRIBUTION_DESTINATION = "attribution_destination";
    private static final String SOURCE_REGISTRATION_TIME = "source_registration_time";
    private static final String SHARED_INFO_KEY = "shared_info";
    private static final String SCHEDULED_REPORT_TIME_KEY = "scheduled_report_time";
    private static final String PRIVACY_BUDGET_KEY_KEY = "privacy_budget_key";
    private static final String VERSION_KEY = "version";
    private static final String REPORT_ID_KEY = "report_id";
    private static final String REPORTING_ORIGIN_KEY = "reporting_origin";
    private static final String AGGREGATION_SERVICE_PAYLOADS_KEY = "aggregation_service_payloads";
    private static final String DEBUG_CLEARTEXT_PAYLOAD_KEY = "debug_cleartext_payload";

    private AggregateReportBody() {

    };

    private AggregateReportBody(AggregateReportBody other) {
        this.mSourceSite = other.mSourceSite;
        this.mAttributionDestination = other.mAttributionDestination;
        this.mSourceRegistrationTime = other.mSourceRegistrationTime;
        this.mScheduledReportTime = other.mScheduledReportTime;
        this.mPrivacyBudgetKey = other.mPrivacyBudgetKey;
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

        aggregateBodyJson.put(SOURCE_SITE_KEY, this.mSourceSite);
        aggregateBodyJson.put(ATTRIBUTION_DESTINATION, this.mAttributionDestination);
        aggregateBodyJson.put(SOURCE_REGISTRATION_TIME, this.mSourceRegistrationTime);

        aggregateBodyJson.put(SHARED_INFO_KEY, sharedInfoToJson().toString());

        aggregateBodyJson.put(AGGREGATION_SERVICE_PAYLOADS_KEY, aggregationServicePayloadsToJson());

        return aggregateBodyJson;
    }

    /**
     * Generate the JSON serialization of the shared_info field of the aggregate report.
     */
    @VisibleForTesting
    JSONObject sharedInfoToJson() throws JSONException {
        JSONObject sharedInfoJson = new JSONObject();

        sharedInfoJson.put(SCHEDULED_REPORT_TIME_KEY, this.mScheduledReportTime);
        sharedInfoJson.put(PRIVACY_BUDGET_KEY_KEY, this.mPrivacyBudgetKey);
        sharedInfoJson.put(VERSION_KEY, API_VERSION);
        sharedInfoJson.put(REPORT_ID_KEY, this.mReportId);
        sharedInfoJson.put(REPORTING_ORIGIN_KEY, this.mReportingOrigin);

        return sharedInfoJson;
    }

    /**
     * Generate the JSON array serialization of the aggregation service payloads field.
     */
    @VisibleForTesting
    JSONArray aggregationServicePayloadsToJson() throws JSONException {
        JSONArray aggregationServicePayloadsJson = new JSONArray();


        JSONObject debugCleartextPayloadJson = new JSONObject();
        debugCleartextPayloadJson.put(DEBUG_CLEARTEXT_PAYLOAD_KEY, this.mDebugCleartextPayload);

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
         * The attribution source.
         */
        public @NonNull Builder setSourceSite(@NonNull String sourceSite) {
            mBuilding.mSourceSite = sourceSite;
            return this;
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
         * The privacy budget key for the report.
         */
        public @NonNull Builder setPrivacyBudgetKey(@NonNull String privacyBudgetKey) {
            mBuilding.mPrivacyBudgetKey = privacyBudgetKey;
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
