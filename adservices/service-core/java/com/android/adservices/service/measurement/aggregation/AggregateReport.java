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

import androidx.annotation.Nullable;

import com.android.adservices.service.measurement.util.UnsignedLong;

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
public class AggregateReport {
    private String mId;
    private Uri mPublisher;
    private Uri mAttributionDestination;
    private long mSourceRegistrationTime;
    private long mScheduledReportTime;   // triggerTime + random([10min, 1hour])
    private String mEnrollmentId;
    private String mDebugCleartextPayload;
    private AggregateAttributionData mAggregateAttributionData;
    private @Status int mStatus;
    private String mApiVersion;
    @Nullable private UnsignedLong mSourceDebugKey;
    @Nullable private UnsignedLong mTriggerDebugKey;
    private String mSourceId;
    private String mTriggerId;

    @IntDef(value = {
            Status.PENDING,
            Status.DELIVERED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
        int PENDING = 0;
        int DELIVERED = 1;
    }

    private AggregateReport() {
        mId = null;
        mPublisher = null;
        mAttributionDestination = null;
        mSourceRegistrationTime = 0L;
        mScheduledReportTime = 0L;
        mEnrollmentId = null;
        mDebugCleartextPayload = null;
        mAggregateAttributionData = null;
        mStatus = AggregateReport.Status.PENDING;
        mSourceDebugKey = null;
        mTriggerDebugKey = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregateReport)) {
            return false;
        }
        AggregateReport aggregateReport = (AggregateReport) obj;
        return Objects.equals(mPublisher, aggregateReport.mPublisher)
                && Objects.equals(mAttributionDestination, aggregateReport.mAttributionDestination)
                && mSourceRegistrationTime == aggregateReport.mSourceRegistrationTime
                && mScheduledReportTime == aggregateReport.mScheduledReportTime
                && Objects.equals(mEnrollmentId, aggregateReport.mEnrollmentId)
                && Objects.equals(mDebugCleartextPayload, aggregateReport.mDebugCleartextPayload)
                && Objects.equals(
                        mAggregateAttributionData, aggregateReport.mAggregateAttributionData)
                && mStatus == aggregateReport.mStatus
                && Objects.equals(mApiVersion, aggregateReport.mApiVersion)
                && Objects.equals(mSourceDebugKey, aggregateReport.mSourceDebugKey)
                && Objects.equals(mTriggerDebugKey, aggregateReport.mTriggerDebugKey)
                && Objects.equals(mSourceId, aggregateReport.mSourceId)
                && Objects.equals(mTriggerId, aggregateReport.mTriggerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId,
                mPublisher,
                mAttributionDestination,
                mSourceRegistrationTime,
                mScheduledReportTime,
                mEnrollmentId,
                mDebugCleartextPayload,
                mAggregateAttributionData,
                mStatus,
                mSourceDebugKey,
                mTriggerDebugKey,
                mSourceId,
                mTriggerId);
    }

    /**
     * Unique identifier for the {@link AggregateReport}.
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
     * Ad-tech enrollment ID.
     */
    public String getEnrollmentId() {
        return mEnrollmentId;
    }

    /**
     * Unencrypted aggregate payload string, convert from List of AggregateHistogramContribution.
     */
    public String getDebugCleartextPayload() {
        return mDebugCleartextPayload;
    }

    /** Source Debug Key */
    @Nullable
    public UnsignedLong getSourceDebugKey() {
        return mSourceDebugKey;
    }

    /** Trigger Debug Key */
    @Nullable
    public UnsignedLong getTriggerDebugKey() {
        return mTriggerDebugKey;
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
     * Api version when the report was issued.
     */
    public String getApiVersion() {
        return mApiVersion;
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

    /** Source ID */
    public String getSourceId() {
        return mSourceId;
    }

    /** Trigger ID */
    public String getTriggerId() {
        return mTriggerId;
    }

    /**
     * Builder for {@link AggregateReport}.
     */
    public static final class Builder {
        private final AggregateReport mAttributionReport;

        public Builder() {
            mAttributionReport = new AggregateReport();
        }

        /**
         * See {@link AggregateReport#getId()}.
         */
        public Builder setId(String id) {
            mAttributionReport.mId = id;
            return this;
        }

        /**
         * See {@link AggregateReport#getPublisher()}.
         */
        public Builder setPublisher(Uri publisher) {
            mAttributionReport.mPublisher = publisher;
            return this;
        }

        /**
         * See {@link AggregateReport#getAttributionDestination()}.
         */
        public Builder setAttributionDestination(Uri attributionDestination) {
            mAttributionReport.mAttributionDestination = attributionDestination;
            return this;
        }

        /**
         * See {@link AggregateReport#getSourceRegistrationTime()}.
         */
        public Builder setSourceRegistrationTime(long sourceRegistrationTime) {
            mAttributionReport.mSourceRegistrationTime = sourceRegistrationTime;
            return this;
        }

        /**
         * See {@link AggregateReport#getScheduledReportTime()}.
         */
        public Builder setScheduledReportTime(long scheduledReportTime) {
            mAttributionReport.mScheduledReportTime = scheduledReportTime;
            return this;
        }

        /**
         * See {@link AggregateReport#getEnrollmentId()}.
         */
        public Builder setEnrollmentId(String enrollmentId) {
            mAttributionReport.mEnrollmentId = enrollmentId;
            return this;
        }

        /**
         * See {@link AggregateReport#getDebugCleartextPayload()}.
         */
        public Builder setDebugCleartextPayload(String debugCleartextPayload) {
            mAttributionReport.mDebugCleartextPayload = debugCleartextPayload;
            return this;
        }

        /**
         * See {@link AggregateReport#getAggregateAttributionData()}.
         */
        public Builder setAggregateAttributionData(
                AggregateAttributionData aggregateAttributionData) {
            mAttributionReport.mAggregateAttributionData = aggregateAttributionData;
            return this;
        }

        /**
         * See {@link AggregateReport#getStatus()}
         */
        public Builder setStatus(@Status int status) {
            mAttributionReport.mStatus = status;
            return this;
        }

        /**
         * See {@link AggregateReport#getApiVersion()}
         */
        public Builder setApiVersion(String version) {
            mAttributionReport.mApiVersion = version;
            return this;
        }

        /** See {@link AggregateReport#getSourceDebugKey()} ()} */
        public Builder setSourceDebugKey(UnsignedLong sourceDebugKey) {
            mAttributionReport.mSourceDebugKey = sourceDebugKey;
            return this;
        }

        /** See {@link AggregateReport#getTriggerDebugKey()} ()} */
        public Builder setTriggerDebugKey(UnsignedLong triggerDebugKey) {
            mAttributionReport.mTriggerDebugKey = triggerDebugKey;
            return this;
        }

        /** See {@link AggregateReport#getSourceId()} */
        public AggregateReport.Builder setSourceId(String sourceId) {
            mAttributionReport.mSourceId = sourceId;
            return this;
        }

        /** See {@link AggregateReport#getTriggerId()} */
        public AggregateReport.Builder setTriggerId(String triggerId) {
            mAttributionReport.mTriggerId = triggerId;
            return this;
        }

        /**
         * Build the {@link AggregateReport}.
         */
        public AggregateReport build() {
            return mAttributionReport;
        }
    }
}
