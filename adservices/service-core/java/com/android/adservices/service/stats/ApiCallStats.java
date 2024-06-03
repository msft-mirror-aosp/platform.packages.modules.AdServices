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

package com.android.adservices.service.stats;



import android.adservices.common.AdServicesStatusUtils.StatusCode;

import java.util.Objects;

/** Class for Api Call Stats. */
public final class ApiCallStats {
    private int mCode;
    private int mApiClass;
    private int mApiName;
    private String mAppPackageName;
    private String mSdkPackageName;
    private int mLatencyMillisecond;

    private @StatusCode int mResultCode;

    private ApiCallStats() {}

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ApiCallStats)) {
            return false;
        }
        ApiCallStats apiCallStats = (ApiCallStats) obj;
        return mCode == apiCallStats.mCode
                && mApiClass == apiCallStats.mApiClass
                && mApiName == apiCallStats.mApiName
                && Objects.equals(mAppPackageName, apiCallStats.mAppPackageName)
                && Objects.equals(mSdkPackageName, apiCallStats.mSdkPackageName)
                && mLatencyMillisecond == apiCallStats.mLatencyMillisecond
                && mResultCode == apiCallStats.mResultCode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mCode,
                mApiClass,
                mApiName,
                mAppPackageName,
                mSdkPackageName,
                mLatencyMillisecond,
                mResultCode);
    }

    public int getCode() {
        return mCode;
    }

    public int getApiClass() {
        return mApiClass;
    }

    public int getApiName() {
        return mApiName;
    }

    public String getAppPackageName() {
        return mAppPackageName;
    }

    public String getSdkPackageName() {
        return mSdkPackageName;
    }

    public int getLatencyMillisecond() {
        return mLatencyMillisecond;
    }

    public @StatusCode int getResultCode() {
        return mResultCode;
    }

    @Override
    public String toString() {
        return "ApiCallStats{"
                + "mCode="
                + mCode
                + ", mApiClass="
                + mApiClass
                + ", mApiName="
                + mApiName
                + ", mAppPackageName='"
                + mAppPackageName
                + '\''
                + ", mSdkPackageName='"
                + mSdkPackageName
                + '\''
                + ", mLatencyMillisecond="
                + mLatencyMillisecond
                + ", mResultCode="
                + mResultCode
                + '}';
    }

    /** Builder for {@link ApiCallStats}. */
    public static final class Builder {
        private final ApiCallStats mBuilding = new ApiCallStats();

        public Builder() {
        }

        /** See {@link ApiCallStats#getCode()} . */
        public Builder setCode(int code) {
            mBuilding.mCode = code;
            return this;
        }

        /** See {@link ApiCallStats#getApiClass()} . */
        public Builder setApiClass(int apiClass) {
            mBuilding.mApiClass = apiClass;
            return this;
        }

        /** See {@link ApiCallStats#getApiName()} . */
        public Builder setApiName(int apiName) {
            mBuilding.mApiName = apiName;
            return this;
        }

        /** See {@link ApiCallStats#getAppPackageName()} . */
        public Builder setAppPackageName(String appPackageName) {
            mBuilding.mAppPackageName = Objects.requireNonNull(appPackageName);
            return this;
        }

        /** See {@link ApiCallStats#getSdkPackageName()}. */
        public Builder setSdkPackageName(String sdkPackageName) {
            mBuilding.mSdkPackageName = Objects.requireNonNull(sdkPackageName);
            return this;
        }

        /** See {@link ApiCallStats#getLatencyMillisecond()}. */
        public Builder setLatencyMillisecond(int latencyMillisecond) {
            mBuilding.mLatencyMillisecond = latencyMillisecond;
            return this;
        }

        /** See {@link ApiCallStats#getResultCode()}. */
        public Builder setResultCode(@StatusCode int resultCode) {
            mBuilding.mResultCode = resultCode;
            return this;
        }

        /** Build the {@link ApiCallStats}. */
        public ApiCallStats build() {
            if (mBuilding.mAppPackageName == null) {
                throw new IllegalStateException("must call setAppPackageName()");
            }
            if (mBuilding.mSdkPackageName == null) {
                throw new IllegalStateException("must call setSdkPackageName()");
            }
            return mBuilding;
        }
    }
}
