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

import android.annotation.IntDef;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Objects;

/**
 * Class that contains all the real data needed after aggregation, it is not encrypted.
 */
public class CleartextAggregatePayload {
    private String mId;
    private Uri mPublisher;
    private Uri mAttributionDestination;
    private long mSourceRegistrationTime;
    private long mScheduledReportTime;   // triggerTime + random([10min, 1hour])
    private String mPrivacyBudgetKey;
    private Uri mReportingOrigin;
    private String mDebugCleartextPayload;
    private AggregateAttributionData mAggregateAttributionData;
    private @Status int mStatus;

    @IntDef(value = {
            Status.PENDING,
            Status.DELIVERED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
        int PENDING = 0;
        int DELIVERED = 1;
    }

    private CleartextAggregatePayload() {
        mId = null;
        mPublisher = null;
        mAttributionDestination = null;
        mSourceRegistrationTime = 0L;
        mScheduledReportTime = 0L;
        mPrivacyBudgetKey = null;
        mReportingOrigin = null;
        mDebugCleartextPayload = null;
        mAggregateAttributionData = null;
        mStatus = CleartextAggregatePayload.Status.PENDING;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CleartextAggregatePayload)) {
            return false;
        }
        CleartextAggregatePayload aggregatePayload = (CleartextAggregatePayload) obj;
        return  mId.equals(aggregatePayload.mId)
                && Objects.equals(mPublisher, aggregatePayload.mPublisher)
                && Objects.equals(mAttributionDestination, aggregatePayload.mAttributionDestination)
                && mSourceRegistrationTime == aggregatePayload.mSourceRegistrationTime
                && mScheduledReportTime == aggregatePayload.mScheduledReportTime
                && mPrivacyBudgetKey.equals(aggregatePayload.mPrivacyBudgetKey)
                && Objects.equals(mReportingOrigin, aggregatePayload.mReportingOrigin)
                && mDebugCleartextPayload.equals(aggregatePayload.mDebugCleartextPayload)
                && Objects.equals(mAggregateAttributionData,
                aggregatePayload.mAggregateAttributionData)
                && mStatus == aggregatePayload.mStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mPublisher, mAttributionDestination, mSourceRegistrationTime,
                mScheduledReportTime, mPrivacyBudgetKey, mReportingOrigin, mDebugCleartextPayload,
                mAggregateAttributionData, mStatus);
    }

    /**
     * Unique identifier for the {@link CleartextAggregatePayload}.
     */
    public String getId() {
        return mId;
    }

    /**
     * Uri for publisher of this source, primarily an App.
     */
    public Uri getPublisher() {
        return mPublisher;
    }

    /**
     * Uri for attribution destination of source.
     */
    public Uri getAttributionDestination() {
        return mAttributionDestination;
    }

    /**
     * Source registration time.
     */
    public long getSourceRegistrationTime() {
        return mSourceRegistrationTime;
    }

    /**
     * Scheduled report time for aggregate report .
     */
    public long getScheduledReportTime() {
        return mScheduledReportTime;
    }

    /**
     * Privacy budget key for aggregate report.
     */
    public String getPrivacyBudgetKey() {
        return mPrivacyBudgetKey;
    }

    /**
     * Uri for report_to of source.
     */
    public Uri getReportingOrigin() {
        return mReportingOrigin;
    }

    /**
     * Unencrypted aggregate payload string, convert from List of AggregateHistogramContribution.
     */
    public String getDebugCleartextPayload() {
        return mDebugCleartextPayload;
    }

    /**
     * Contains the data specific to the aggregate report.
     */
    public AggregateAttributionData getAggregateAttributionData() {
        return mAggregateAttributionData;
    }

    /**
     * Current {@link Status} of the report.
     */
    public @Status int getStatus() {
        return mStatus;
    }

    /**
     * Generates String for debugCleartextPayload.
     * JSON for format :
     * {
     *     "operation": "histogram",
     *     "data": [{
     *         "bucket": 1369,
     *         "value": 32768
     *     },
     *     {
     *         "bucket": 3461,
     *         "value": 1664
     *     }]
     * }
     */
    public static String generateDebugPayload(
            List<AggregateHistogramContribution> contributions) throws JSONException {
        JSONArray jsonArray = new JSONArray();
        for (AggregateHistogramContribution contribution : contributions) {
            jsonArray.put(contribution.toJSONObject());
        }
        JSONObject debugPayload = new JSONObject();
        debugPayload.put("operation", "histogram");
        debugPayload.put("data", jsonArray);
        return debugPayload.toString();
    }

    /**
     * Builder for {@link CleartextAggregatePayload}.
     */
    public static final class Builder {
        private final CleartextAggregatePayload mAttributionReport;

        public Builder() {
            mAttributionReport = new CleartextAggregatePayload();
        }

        /**
         * See {@link CleartextAggregatePayload#getId()}.
         */
        public Builder setId(String id) {
            mAttributionReport.mId = id;
            return this;
        }

        /**
         * See {@link CleartextAggregatePayload#getPublisher()}.
         */
        public Builder setPublisher(Uri publisher) {
            mAttributionReport.mPublisher = publisher;
            return this;
        }

        /**
         * See {@link CleartextAggregatePayload#getAttributionDestination()}.
         */
        public Builder setAttributionDestination(Uri attributionDestination) {
            mAttributionReport.mAttributionDestination = attributionDestination;
            return this;
        }

        /**
         * See {@link CleartextAggregatePayload#getSourceRegistrationTime()}.
         */
        public Builder setSourceRegistrationTime(long sourceRegistrationTime) {
            mAttributionReport.mSourceRegistrationTime = sourceRegistrationTime;
            return this;
        }

        /**
         * See {@link CleartextAggregatePayload#getScheduledReportTime()}.
         */
        public Builder setScheduledReportTime(long scheduledReportTime) {
            mAttributionReport.mScheduledReportTime = scheduledReportTime;
            return this;
        }

        /**
         * See {@link CleartextAggregatePayload#getPrivacyBudgetKey()}.
         */
        public Builder setPrivacyBudgetKey(String privacyBudgetKey) {
            mAttributionReport.mPrivacyBudgetKey = privacyBudgetKey;
            return this;
        }

        /**
         * See {@link CleartextAggregatePayload#getReportingOrigin()}.
         */
        public Builder setReportingOrigin(Uri reportingOrigin) {
            mAttributionReport.mReportingOrigin = reportingOrigin;
            return this;
        }

        /**
         * See {@link CleartextAggregatePayload#getDebugCleartextPayload()}.
         */
        public Builder setDebugCleartextPayload(String debugCleartextPayload) {
            mAttributionReport.mDebugCleartextPayload = debugCleartextPayload;
            return this;
        }

        /**
         * See {@link CleartextAggregatePayload#getAggregateAttributionData()}.
         */
        public Builder setAggregateAttributionData(
                AggregateAttributionData aggregateAttributionData) {
            mAttributionReport.mAggregateAttributionData = aggregateAttributionData;
            return this;
        }

        /**
         * See {@link CleartextAggregatePayload#getStatus()}
         */
        public Builder setStatus(@Status int status) {
            mAttributionReport.mStatus = status;
            return this;
        }

        /**
         * Build the {@link CleartextAggregatePayload}.
         */
        public CleartextAggregatePayload build() {
            return mAttributionReport;
        }
    }
}
