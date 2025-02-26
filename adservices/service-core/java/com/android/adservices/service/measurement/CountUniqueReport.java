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
import android.net.Uri;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/** POJO for Count Unique Report. */
public class CountUniqueReport {

    private String mReportId;
    private String mPayload;
    private Uri mReportingOrigin;

    @Status private int mStatus;
    private Long mScheduledReportTime; // async registration time + random([0min, 10 min])

    private String mApiVersion;

    private String mDebugKey;
    private String mContextId;

    @IntDef(value = {CountUniqueReport.Status.PENDING, CountUniqueReport.Status.DELIVERED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
        int PENDING = 0;
        int DELIVERED = 1;
    }

    private CountUniqueReport() {
        mReportId = null;
        mPayload = null;
        mReportingOrigin = null;
        mStatus = CountUniqueReport.Status.PENDING;
        mScheduledReportTime = null;
        mApiVersion = null;
        mDebugKey = null;
        mContextId = null;
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
                && Objects.equals(mContextId, countUniqueReport.mContextId);
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
                mContextId);
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
        public Builder setPayload(String payload) {
            mReport.mPayload = payload;
            return this;
        }

        /** See {@link CountUniqueReport#getReportingOrigin()} */
        public Builder setReportingOrigin(Uri reportingOrigin) {
            mReport.mReportingOrigin = reportingOrigin;
            return this;
        }

        /** See {@link CountUniqueReport#getStatus()} */
        public Builder setStatus(@Status int status) {
            mReport.mStatus = status;
            return this;
        }

        /** See {@link CountUniqueReport#getScheduledReportTime()} */
        public Builder setScheduledReportTime(long scheduledReportTime) {
            mReport.mScheduledReportTime = scheduledReportTime;
            return this;
        }

        /** See {@link CountUniqueReport#getApiVersion()} */
        public Builder setApiVersion(String apiVersion) {
            mReport.mApiVersion = apiVersion;
            return this;
        }

        /** See {@link CountUniqueReport#getDebugKey()} */
        public Builder setDebugKey(String debugKey) {
            mReport.mDebugKey = debugKey;
            return this;
        }

        /** See {@link CountUniqueReport#getContextId()} ()} */
        public Builder setContextId(String contextId) {
            mReport.mContextId = contextId;
            return this;
        }

        /** Builds a CountUniqueReport */
        public CountUniqueReport build() {
            return mReport;
        }
    }
}
