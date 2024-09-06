/*
 * Copyright (C) 2023 The Android Open Source Project
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

/** class for measurement ODP registration stats. */

/*
 optional android.adservices.service.measurement.OdpRegistrationType odp_registration_type = 1;
 optional android.adservices.service.measurement.OdpRegistrationStatus odp_registration_status = 2;
*/
public class MeasurementOdpRegistrationStats {
    private int mCode;
    private int mRegistrationType;
    private int mRegistrationStatus;

    public MeasurementOdpRegistrationStats() {}

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MeasurementOdpRegistrationStats)) {
            return false;
        }
        MeasurementOdpRegistrationStats measurementOdpRegistrationStats =
                (MeasurementOdpRegistrationStats) obj;
        return mCode == measurementOdpRegistrationStats.getCode()
                && mRegistrationType == measurementOdpRegistrationStats.getRegistrationType()
                && mRegistrationStatus == measurementOdpRegistrationStats.getRegistrationStatus();
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCode, mRegistrationType, mRegistrationStatus);
    }

    public int getCode() {
        return mCode;
    }

    public int getRegistrationType() {
        return mRegistrationType;
    }

    public int getRegistrationStatus() {
        return mRegistrationStatus;
    }

    /** Builder for {@link MeasurementOdpRegistrationStats}. */
    public static final class Builder {
        private final MeasurementOdpRegistrationStats mBuilding;

        public Builder() {
            mBuilding = new MeasurementOdpRegistrationStats();
        }

        /** See {@link MeasurementOdpRegistrationStats#getCode()} . */
        public @NonNull MeasurementOdpRegistrationStats.Builder setCode(int code) {
            mBuilding.mCode = code;
            return this;
        }

        /** See {@link MeasurementOdpRegistrationStats#getRegistrationType()} . */
        public @NonNull MeasurementOdpRegistrationStats.Builder setRegistrationType(
                int registrationType) {
            mBuilding.mRegistrationType = registrationType;
            return this;
        }

        /** See {@link MeasurementOdpRegistrationStats#getRegistrationStatus()} . */
        public @NonNull MeasurementOdpRegistrationStats.Builder setRegistrationStatus(
                int registrationStatus) {
            mBuilding.mRegistrationStatus = registrationStatus;
            return this;
        }

        /** Build the {@link MeasurementOdpRegistrationStats}. */
        public @NonNull MeasurementOdpRegistrationStats build() {
            return mBuilding;
        }
    }
}
