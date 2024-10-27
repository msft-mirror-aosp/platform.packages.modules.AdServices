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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_UNKNOWN;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ScheduledCustomAudienceUpdateScheduleAttemptedStats {

    /** Returns the number of partial Custom Audiences. */
    public abstract int getNumberOfPartialCustomAudiences();

    /** Returns the number of leave Custom Audiences. */
    public abstract int getNumberOfLeaveCustomAudiences();

    /** Returns the minimum delay in seconds. */
    public abstract int getMinimumDelayInMinutes();

    /** Returns whether this was the initial hop. */
    public abstract boolean isInitialHop();

    /** Returns the existing update status. */
    @AdsRelevanceStatusUtils.ScheduleCustomAudienceUpdateExistingUpdateStatus
    public abstract int getExistingUpdateStatus();

    /**
     * Returns a new builder for creating an instance of {@link
     * ScheduledCustomAudienceUpdateScheduleAttemptedStats}.
     */
    public static Builder builder() {
        return new AutoValue_ScheduledCustomAudienceUpdateScheduleAttemptedStats.Builder()
                .setNumberOfLeaveCustomAudiences(0)
                .setInitialHop(true)
                .setExistingUpdateStatus(SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_UNKNOWN);
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the number of partial Custom Audiences. */
        public abstract Builder setNumberOfPartialCustomAudiences(
                int numberOfPartialCustomAudiences);

        /** Sets the number of partial Custom Audiences. */
        public abstract Builder setNumberOfLeaveCustomAudiences(int numberOfLeaveCustomAudiences);

        /** Sets the number of partial Custom Audiences. */
        public abstract Builder setMinimumDelayInMinutes(int minimumDelayInMinutes);

        /** Sets the number of partial Custom Audiences. */
        public abstract Builder setInitialHop(boolean initialHop);

        /** Sets the existing schedule CA update status in DB. */
        public abstract Builder setExistingUpdateStatus(
                @AdsRelevanceStatusUtils.ScheduleCustomAudienceUpdateExistingUpdateStatus
                        int value);

        /**
         * Returns a new {@link ScheduledCustomAudienceUpdateScheduleAttemptedStats} instance built
         * from the values set on this builder.
         */
        public abstract ScheduledCustomAudienceUpdateScheduleAttemptedStats build();
    }
}
