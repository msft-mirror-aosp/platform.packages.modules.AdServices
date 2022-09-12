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

import android.annotation.NonNull;
import android.annotation.Nullable;

import java.util.Objects;

/** Class for measurement registration response Stats. */
public class MeasurementRegistrationResponseStats {
    private final int mCode;
    private final int mRegistrationType;
    private final long mResponseSize;
    private final String mAdTechDomain;

    private MeasurementRegistrationResponseStats(Builder builder) {
        mCode = builder.mCode;
        mRegistrationType = builder.mRegistrationType;
        mResponseSize = builder.mResponseSize;
        mAdTechDomain = builder.mAdTechDomain;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MeasurementRegistrationResponseStats)) return false;
        MeasurementRegistrationResponseStats that = (MeasurementRegistrationResponseStats) o;
        return mCode == that.mCode
                && mRegistrationType == that.mRegistrationType
                && mResponseSize == that.mResponseSize
                && Objects.equals(mAdTechDomain, that.mAdTechDomain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCode, mRegistrationType, mResponseSize, mAdTechDomain);
    }

    @Override
    public String toString() {
        return "MeasurementRegistrationResponseStats{"
                + "mCode="
                + mCode
                + ", mRegistrationType="
                + mRegistrationType
                + ", mResponseSize="
                + mResponseSize
                + ", mAdTechDomain='"
                + mAdTechDomain
                + '}';
    }

    public int getCode() {
        return mCode;
    }

    public int getRegistrationType() {
        return mRegistrationType;
    }

    public long getResponseSize() {
        return mResponseSize;
    }

    @Nullable
    public String getAdTechDomain() {
        return mAdTechDomain;
    }

    /** Builder for {@link MeasurementRegistrationResponseStats}. */
    public static final class Builder {
        private final int mCode;
        private final int mRegistrationType;
        private final long mResponseSize;
        private String mAdTechDomain;

        public Builder(int code, int registrationType, long responseSize) {
            mCode = code;
            mRegistrationType = registrationType;
            mResponseSize = responseSize;
        }

        /** See {@link MeasurementRegistrationResponseStats#getAdTechDomain()} . */
        @NonNull
        public MeasurementRegistrationResponseStats.Builder setAdTechDomain(
                @Nullable String adTechDomain) {
            mAdTechDomain = adTechDomain;
            return this;
        }

        /** Build the {@link MeasurementRegistrationResponseStats}. */
        @NonNull
        public MeasurementRegistrationResponseStats build() {
            return new MeasurementRegistrationResponseStats(this);
        }
    }
}
