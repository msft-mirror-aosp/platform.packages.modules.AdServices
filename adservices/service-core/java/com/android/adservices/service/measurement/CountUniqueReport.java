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

package com.android.adservices.service.measurement;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/** POJO for Count Unique Report. */
public class CountUniqueReport {

    private String mReportId;
    private String mPayload;
    private Uri mReportingOrigin;

    @ReportDeliveryStatus private int mStatus;
    @ReportDeliveryStatus private int mDebugReportStatus;
    private Long mScheduledReportTime; // async registration time + random([0min, 10 min])

    private String mApiVersion;

    @Nullable private String mDebugKey;
    @Nullable private String mContextId;
    private String mEnrollmentId;
    private Integer mContributionValue;
    private Long mContributionTime;

    @IntDef(
            value = {
                ReportDeliveryStatus.NONE,
                ReportDeliveryStatus.PENDING,
                ReportDeliveryStatus.DELIVERED
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ReportDeliveryStatus {
        int NONE = 0;
        int PENDING = 1;
        int DELIVERED = 2;
    }

    private CountUniqueReport() {
        mReportId = null;
        mPayload = null;
        mReportingOrigin = null;
        mStatus = ReportDeliveryStatus.PENDING;
        mDebugReportStatus = ReportDeliveryStatus.NONE;
        mScheduledReportTime = null;
        mApiVersion = null;
        mDebugKey = null;
        mContextId = null;
        mEnrollmentId = null;
        mContributionValue = null;
        mContributionTime = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CountUniqueReport countUniqueReport)) {
            return false;
        }
        return Objects.equals(mReportId, countUniqueReport.mReportId)
                && Objects.equals(mPayload, countUniqueReport.mPayload)
                && Objects.equals(mReportingOrigin, countUniqueReport.mReportingOrigin)
                && mStatus == countUniqueReport.mStatus
                && mScheduledReportTime.equals(countUniqueReport.mScheduledReportTime)
                && Objects.equals(mApiVersion, countUniqueReport.mApiVersion)
                && Objects.equals(mDebugKey, countUniqueReport.mDebugKey)
                && Objects.equals(mContextId, countUniqueReport.mContextId)
                && mDebugReportStatus == countUniqueReport.mDebugReportStatus
                && Objects.equals(mEnrollmentId, countUniqueReport.mEnrollmentId)
                && Objects.equals(mContributionValue, countUniqueReport.mContributionValue)
                && Objects.equals(mContributionTime, countUniqueReport.mContributionTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mReportId,
                mPayload,
                mReportingOrigin,
                mStatus,
                mScheduledReportTime,
                mApiVersion,
                mDebugKey,
                mContextId,
                mDebugReportStatus,
                mEnrollmentId,
                mContributionValue,
                mContributionTime);
    }

    /** Report id for the report */
    public String getReportId() {
        return mReportId;
    }

    /** Clear text payload containing the histogram contribution */
    public String getPayload() {
        return mPayload;
    }

    /** Reporting origin for sending reports */
    public Uri getReportingOrigin() {
        return mReportingOrigin;
    }

    /** Time when the report is scheduled to be sent */
    public Long getScheduledReportTime() {
        return mScheduledReportTime;
    }

    /** Version of the API */
    public String getApiVersion() {
        return mApiVersion;
    }

    /** Status of the report */
    @ReportDeliveryStatus
    public int getStatus() {
        return mStatus;
    }

    /** Debug key for the report */
    public String getDebugKey() {
        return mDebugKey;
    }

    /** ContextId for the report */
    public String getContextId() {
        return mContextId;
    }

    /** Debug Report Status of the report */
    @ReportDeliveryStatus
    public int getDebugReportStatus() {
        return mDebugReportStatus;
    }

    /** EnrollmentId for the report */
    public String getEnrollmentId() {
        return mEnrollmentId;
    }

    /** Contribution value for the histogram in report */
    public Integer getContributionValue() {
        return mContributionValue;
    }

    /** Timestamp at which contribution is made */
    public Long getContributionTime() {
        return mContributionTime;
    }

    public static class Builder {

        private final CountUniqueReport mReport;

        public Builder() {
            mReport = new CountUniqueReport();
        }

        /** See {@link CountUniqueReport#getReportId()} */
        public Builder setReportId(@NonNull String reportId) {
            mReport.mReportId = reportId;
            return this;
        }

        /** See {@link CountUniqueReport#getPayload()} */
        public Builder setPayload(@NonNull String payload) {
            mReport.mPayload = payload;
            return this;
        }

        /** See {@link CountUniqueReport#getReportingOrigin()} */
        public Builder setReportingOrigin(@NonNull Uri reportingOrigin) {
            mReport.mReportingOrigin = reportingOrigin;
            return this;
        }

        /** See {@link CountUniqueReport#getStatus()} */
        public Builder setStatus(@ReportDeliveryStatus int status) {
            mReport.mStatus = status;
            return this;
        }

        /** See {@link CountUniqueReport#getScheduledReportTime()} */
        public Builder setScheduledReportTime(@NonNull Long scheduledReportTime) {
            mReport.mScheduledReportTime = scheduledReportTime;
            return this;
        }

        /** See {@link CountUniqueReport#getApiVersion()} */
        public Builder setApiVersion(@NonNull String apiVersion) {
            mReport.mApiVersion = apiVersion;
            return this;
        }

        /** See {@link CountUniqueReport#getDebugKey()} */
        public Builder setDebugKey(@Nullable String debugKey) {
            mReport.mDebugKey = debugKey;
            return this;
        }

        /** See {@link CountUniqueReport#getContextId()} */
        public Builder setContextId(@Nullable String contextId) {
            mReport.mContextId = contextId;
            return this;
        }

        /** See {@link CountUniqueReport#getDebugReportStatus()} */
        public Builder setDebugReportStatus(@ReportDeliveryStatus int debugReportStatus) {
            mReport.mDebugReportStatus = debugReportStatus;
            return this;
        }

        /** See {@link CountUniqueReport#getEnrollmentId()} */
        public Builder setEnrollmentId(@NonNull String enrollmentId) {
            mReport.mEnrollmentId = enrollmentId;
            return this;
        }

        /** See {@link CountUniqueReport#getContributionValue()} */
        public Builder setContributionValue(@NonNull Integer contributionValue) {
            mReport.mContributionValue = contributionValue;
            return this;
        }

        /** See {@link CountUniqueReport#getContributionTime()} */
        public Builder setContributionTime(@NonNull Long contributionTime) {
            mReport.mContributionTime = contributionTime;
            return this;
        }

        /** Builds a CountUniqueReport */
        public CountUniqueReport build() {
            return mReport;
        }
    }
}
