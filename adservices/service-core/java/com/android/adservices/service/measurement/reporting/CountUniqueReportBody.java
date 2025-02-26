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

package com.android.adservices.service.measurement.reporting;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoConverter;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/** Class for constructing the report body of a Count Unique report. */
public class CountUniqueReportBody {

    private String mApiVersion;
    private String mApi;
    private String mReportId;
    private Uri mReportingOrigin;
    private Long mScheduledReportTime;
    private boolean mDebugMode;
    private String mDebugCleartextPayload;
    @Nullable private String mContextId;

    @Nullable private String mDebugKey;
    private Uri mAggregationCoordinatorOrigin;

    interface PayloadBodyKeys {
        String SHARED_INFO = "shared_info";
        String AGGREGATION_SERVICE_PAYLOADS = "aggregation_service_payloads";
        String AGGREGATION_COORDINATOR_ORIGIN = "aggregation_coordinator_origin";
        String DEBUG_KEY = "debug_key";
        String CONTEXT_ID = "context_id";
    }

    interface AggregationServicePayloadKeys {
        String PAYLOAD = "payload";
        String KEY_ID = "key_id";
        String DEBUG_CLEARTEXT_PAYLOAD = "debug_cleartext_payload";
    }

    @VisibleForTesting
    interface SharedInfoKeys {
        String API_NAME = "api";
        String REPORT_ID = "report_id";
        String REPORTING_ORIGIN = "reporting_origin";
        String SCHEDULED_REPORT_TIME = "scheduled_report_time";
        String API_VERSION = "version";
        String DEBUG_MODE = "debug_mode";
    }

    private static final String DEBUG_MODE_ENABLED = "enabled";

    private CountUniqueReportBody() {}

    private CountUniqueReportBody(CountUniqueReportBody other) {
        mApiVersion = other.mApiVersion;
        mApi = other.mApi;
        mReportId = other.mReportId;
        mReportingOrigin = other.mReportingOrigin;
        mScheduledReportTime = other.mScheduledReportTime;
        mDebugMode = other.mDebugMode;
        mDebugCleartextPayload = other.mDebugCleartextPayload;
        mDebugKey = other.mDebugKey;
        mAggregationCoordinatorOrigin = other.mAggregationCoordinatorOrigin;
        mContextId = other.mContextId;
    }

    /** Generate the JSON serialization of the Count Unique report. */
    public JSONObject toJson(AggregateEncryptionKey key, Flags flags) throws JSONException {
        JSONObject countUniqueBodyJson = new JSONObject();

        final String sharedInfo = sharedInfoToJson().toString();
        countUniqueBodyJson.put(PayloadBodyKeys.SHARED_INFO, sharedInfo);
        countUniqueBodyJson.put(
                PayloadBodyKeys.AGGREGATION_SERVICE_PAYLOADS,
                aggregationServicePayloadsToJson(sharedInfo, key));

        if (mDebugKey != null) {
            countUniqueBodyJson.put(PayloadBodyKeys.DEBUG_KEY, mDebugKey);
        }

        if (flags.getMeasurementAggregationCoordinatorOriginEnabled()) {
            countUniqueBodyJson.put(
                    PayloadBodyKeys.AGGREGATION_COORDINATOR_ORIGIN,
                    mAggregationCoordinatorOrigin.toString());
        }

        if (mContextId != null) {
            countUniqueBodyJson.put(PayloadBodyKeys.CONTEXT_ID, mContextId);
        }

        return countUniqueBodyJson;
    }

    /** Generate the JSON serialization of the shared_info field of the Count Unique report. */
    @VisibleForTesting
    JSONObject sharedInfoToJson() throws JSONException {
        JSONObject sharedInfoJson = new JSONObject();

        sharedInfoJson.put(SharedInfoKeys.API_NAME, mApi);
        sharedInfoJson.put(SharedInfoKeys.REPORT_ID, mReportId);
        sharedInfoJson.put(SharedInfoKeys.REPORTING_ORIGIN, mReportingOrigin.toString());
        sharedInfoJson.put(
                SharedInfoKeys.SCHEDULED_REPORT_TIME, String.valueOf(mScheduledReportTime));
        sharedInfoJson.put(SharedInfoKeys.API_VERSION, mApiVersion);

        if (mDebugMode) {
            sharedInfoJson.put(SharedInfoKeys.DEBUG_MODE, DEBUG_MODE_ENABLED);
        }

        return sharedInfoJson;
    }

    /**
     * Encrypt the payload and generate the JSON serialization of the aggregation service payloads
     * field.
     */
    @VisibleForTesting
    JSONArray aggregationServicePayloadsToJson(String sharedInfo, AggregateEncryptionKey key)
            throws JSONException {
        JSONArray aggregationServicePayloadsJson = new JSONArray();
        final String encryptedPayload =
                AggregateCryptoConverter.encrypt(
                        key.getPublicKey(), mDebugCleartextPayload, sharedInfo, null);

        final JSONObject aggregationServicePayload = new JSONObject();
        aggregationServicePayload.put(AggregationServicePayloadKeys.PAYLOAD, encryptedPayload);
        aggregationServicePayload.put(AggregationServicePayloadKeys.KEY_ID, key.getKeyId());

        if (mDebugKey != null) {
            aggregationServicePayload.put(
                    AggregationServicePayloadKeys.DEBUG_CLEARTEXT_PAYLOAD,
                    AggregateCryptoConverter.encode(mDebugCleartextPayload, null));
        }

        aggregationServicePayloadsJson.put(aggregationServicePayload);

        return aggregationServicePayloadsJson;
    }

    /** Builder class for CountUniqueReportBody. */
    public static final class Builder {
        private CountUniqueReportBody mBuilding;

        public Builder() {
            mBuilding = new CountUniqueReportBody();
        }

        /** The API name, e.g. "count-unique" used to generate the report. */
        @NonNull
        public Builder setApi(@NonNull String api) {
            mBuilding.mApi = api;
            return this;
        }

        /** The version of the API used to generate the report. */
        public @NonNull Builder setApiVersion(@NonNull String apiVersion) {
            mBuilding.mApiVersion = apiVersion;
            return this;
        }

        /** The unique ID for this report. */
        public @NonNull Builder setReportId(@NonNull String reportId) {
            mBuilding.mReportId = reportId;
            return this;
        }

        /** The initial scheduled report time for the report to be sent. */
        public @NonNull Builder setScheduledReportTime(long scheduledReportTime) {
            mBuilding.mScheduledReportTime = scheduledReportTime;
            return this;
        }

        /** The ad tech domain for the report to be sent to. */
        public @NonNull Builder setReportingOrigin(@NonNull Uri reportingOrigin) {
            mBuilding.mReportingOrigin = reportingOrigin;
            return this;
        }

        /** Whether debug mode is enabled. */
        public @NonNull Builder setDebugMode(boolean debugMode) {
            mBuilding.mDebugMode = debugMode;
            return this;
        }

        /** The provided debug key for the report. */
        public @NonNull Builder setDebugKey(String debugKey) {
            mBuilding.mDebugKey = debugKey;
            return this;
        }

        /** The cleartext payload for the report. */
        public @NonNull Builder setDebugCleartextPayload(@NonNull String debugCleartextPayload) {
            mBuilding.mDebugCleartextPayload = debugCleartextPayload;
            return this;
        }

        /** The origin of the aggregation coordinator used for this report. */
        public @NonNull Builder setAggregationCoordinatorOrigin(Uri aggregationCoordinatorOrigin) {
            mBuilding.mAggregationCoordinatorOrigin = aggregationCoordinatorOrigin;
            return this;
        }

        /** The context id set for the report. */
        public @NonNull Builder setContextId(String contextId) {
            mBuilding.mContextId = contextId;
            return this;
        }

        /** Build the CountUniqueReportBody. */
        public CountUniqueReportBody build() {
            Objects.requireNonNull(mBuilding.mApi);
            Objects.requireNonNull(mBuilding.mApiVersion);
            Objects.requireNonNull(mBuilding.mReportId);
            Objects.requireNonNull(mBuilding.mScheduledReportTime);
            Objects.requireNonNull(mBuilding.mReportingOrigin);
            Objects.requireNonNull(mBuilding.mDebugCleartextPayload);

            return new CountUniqueReportBody(mBuilding);
        }
    }
}
