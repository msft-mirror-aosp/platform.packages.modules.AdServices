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

import android.annotation.Nullable;

import java.util.Objects;

/**
 * Class that contains all the data needed to serialize and send an attribution report. This class
 * can represent multiple different types of reports.
 */
public class AttributionReport {
    private AttributionInfo mAttributionInfo;
    private long mReportTime;
    private long mExternalReportId;
    private AggregateAttributionData mAggregateAttributionData;

    private AttributionReport() {
        mAttributionInfo = null;
        mReportTime = 0L;
        mExternalReportId = 0L;
        mAggregateAttributionData = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AttributionReport)) {
            return false;
        }
        AttributionReport attributionReport = (AttributionReport) obj;
        return Objects.equals(mAttributionInfo, attributionReport.mAttributionInfo)
                && Objects.equals(mReportTime, attributionReport.mReportTime)
                && Objects.equals(mExternalReportId, attributionReport.mExternalReportId)
                && Objects.equals(mAggregateAttributionData,
                attributionReport.mAggregateAttributionData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAttributionInfo, mReportTime, mExternalReportId,
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
     * Builder for {@link AttributionReport}.
     */
    public static final class Builder {
        private final AttributionReport mAttributionReport;

        public Builder() {
            mAttributionReport = new AttributionReport();
        }

        /**
         * See {@link AttributionReport#getAttributionInfo()}.
         */
        public Builder setAttributionInfo(AttributionInfo attributionInfo) {
            mAttributionReport.mAttributionInfo = attributionInfo;
            return this;
        }

        /**
         * See {@link AttributionReport#getReportTime()}.
         */
        public Builder setReportTime(long reportTime) {
            mAttributionReport.mReportTime = reportTime;
            return this;
        }

        /**
         * See {@link AttributionReport#getExternalReportId()}.
         */
        public Builder setExternalReportId(long externalReportId) {
            mAttributionReport.mExternalReportId = externalReportId;
            return this;
        }

        /**
         * See {@link AttributionReport#getAggregateAttributionData()}.
         */
        public Builder setAggregateAttributionData(
                AggregateAttributionData aggregateAttributionData) {
            mAttributionReport.mAggregateAttributionData = aggregateAttributionData;
            return this;
        }

        /**
         * Build the {@link AttributionReport}.
         */
        public AttributionReport build() {
            return mAttributionReport;
        }
    }

    /**
     * Attribution info for the attribution report.
     */
    public static class AttributionInfo {
        // TODO: Add StoredSource object here later.

        private long mTime;
        @Nullable
        private Long mDebugkey;

        private AttributionInfo() {
            mTime = 0L;
            mDebugkey = 0L;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AttributionInfo)) {
                return false;
            }
            AttributionInfo attributionInfo = (AttributionInfo) obj;
            return Objects.equals(mTime, attributionInfo.mTime)
                    && Objects.equals(mDebugkey, attributionInfo.mDebugkey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mTime, mDebugkey);
        }

        /**
         * Time the trigger occurred for attribution info.
         */
        public long getTime() {
            return mTime;
        }

        /**
         * Debug key for the attribution info.
         */
        @Nullable
        public Long getDebugkey() {
            return mDebugkey;
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
             * See {@link AttributionInfo#getDebugkey()}.
             */
            public Builder setDebugkey(@Nullable Long debugkey) {
                mAttributionInfo.mDebugkey = debugkey;
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
