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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Class that contains all the real data needed after aggregation, it is not encrypted.
 */
public class CleartextAggregatePayload {
    private AttributionInfo mAttributionInfo;
    private long mReportTime;
    private long mExternalReportId;
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
        mAttributionInfo = null;
        mReportTime = 0L;
        mExternalReportId = 0L;
        mAggregateAttributionData = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CleartextAggregatePayload)) {
            return false;
        }
        CleartextAggregatePayload attributionReport = (CleartextAggregatePayload) obj;
        return mStatus == attributionReport.mStatus
                && Objects.equals(mAttributionInfo, attributionReport.mAttributionInfo)
                && Objects.equals(mReportTime, attributionReport.mReportTime)
                && Objects.equals(mExternalReportId, attributionReport.mExternalReportId)
                && Objects.equals(mAggregateAttributionData,
                attributionReport.mAggregateAttributionData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStatus, mAttributionInfo, mReportTime, mExternalReportId,
                mAggregateAttributionData);
    }

    /**
     * Attribution info for the attribution report.
     */
    public AttributionInfo getAttributionInfo() {
        return mAttributionInfo;
    }

    /**
     * Time this conversion report should be sent for the attribution report.
     */
    public long getReportTime() {
        return mReportTime;
    }

    /**
     * External report ID for deduplicating reports received by the reporting origin.
     */
    public long getExternalReportId() {
        return mExternalReportId;
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
     * Builder for {@link CleartextAggregatePayload}.
     */
    public static final class Builder {
        private final CleartextAggregatePayload mAttributionReport;

        public Builder() {
            mAttributionReport = new CleartextAggregatePayload();
        }

        /**
         * See {@link CleartextAggregatePayload#getAttributionInfo()}.
         */
        public Builder setAttributionInfo(AttributionInfo attributionInfo) {
            mAttributionReport.mAttributionInfo = attributionInfo;
            return this;
        }

        /**
         * See {@link CleartextAggregatePayload#getReportTime()}.
         */
        public Builder setReportTime(long reportTime) {
            mAttributionReport.mReportTime = reportTime;
            return this;
        }

        /**
         * See {@link CleartextAggregatePayload#getExternalReportId()}.
         */
        public Builder setExternalReportId(long externalReportId) {
            mAttributionReport.mExternalReportId = externalReportId;
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

    /**
     * Attribution info for the attribution report.
     */
    public static class AttributionInfo {
        // TODO: Add StoredSource object here later.

        private long mTime;

        private AttributionInfo() {
            mTime = 0L;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AttributionInfo)) {
                return false;
            }
            AttributionInfo attributionInfo = (AttributionInfo) obj;
            return Objects.equals(mTime, attributionInfo.mTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mTime);
        }

        /**
         * Time the trigger occurred for attribution info.
         */
        public long getTime() {
            return mTime;
        }

        /**
         * Builder for {@link AttributionInfo}.
         */
        public static final class Builder {
            private final AttributionInfo mAttributionInfo;

            public Builder() {
                mAttributionInfo = new AttributionInfo();
            }

            /**
             * See {@link AttributionInfo#getTime()}.
             */
            public Builder setTime(long time) {
                mAttributionInfo.mTime = time;
                return this;
            }

            /**
             * Build the {@link AttributionInfo}.
             */
            public AttributionInfo build() {
                return mAttributionInfo;
            }
        }
    }
}
