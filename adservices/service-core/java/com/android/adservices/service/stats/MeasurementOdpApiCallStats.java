/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.annotation.NonNull;

import java.util.Objects;

public class MeasurementOdpApiCallStats {
    private int mCode;
    private long mLatency;
    private int mApiCallStatus;

    public MeasurementOdpApiCallStats() {}

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MeasurementOdpApiCallStats)) {
            return false;
        }
        MeasurementOdpApiCallStats MeasurementOdpApiCallStats = (MeasurementOdpApiCallStats) obj;
        return mCode == MeasurementOdpApiCallStats.getCode()
                && mLatency == MeasurementOdpApiCallStats.getLatency()
                && mApiCallStatus == MeasurementOdpApiCallStats.getApiCallStatus();
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCode, mLatency, mApiCallStatus);
    }

    public int getCode() {
        return mCode;
    }

    public long getLatency() {
        return mLatency;
    }

    public int getApiCallStatus() {
        return mApiCallStatus;
    }

    /** Builder for {@link MeasurementOdpApiCallStats}. */
    public static final class Builder {
        private final MeasurementOdpApiCallStats mBuilding;

        public Builder() {
            mBuilding = new MeasurementOdpApiCallStats();
        }

        /** See {@link MeasurementOdpApiCallStats#getCode()} . */
        public @NonNull MeasurementOdpApiCallStats.Builder setCode(int code) {
            mBuilding.mCode = code;
            return this;
        }

        /** See {@link MeasurementOdpApiCallStats#getLatency()} . */
        public @NonNull MeasurementOdpApiCallStats.Builder setLatency(long latency) {
            mBuilding.mLatency = latency;
            return this;
        }

        /** See {@link MeasurementOdpApiCallStats#getApiCallStatus()} . */
        public @NonNull MeasurementOdpApiCallStats.Builder setApiCallStatus(int apiCallStatus) {
            mBuilding.mApiCallStatus = apiCallStatus;
            return this;
        }

        /** Build the {@link MeasurementOdpApiCallStats}. */
        public @NonNull MeasurementOdpApiCallStats build() {
            return mBuilding;
        }
    }
}
