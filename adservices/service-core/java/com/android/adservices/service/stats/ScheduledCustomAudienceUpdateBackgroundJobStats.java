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

import com.google.auto.value.AutoValue;

/**
 * Class for recording Custom Audience Update stats from a background job. This records information
 * about how many updates were found and how many were successfully processed.
 */
@AutoValue
public abstract class ScheduledCustomAudienceUpdateBackgroundJobStats {

    /**
     * Returns the number of scheduled Custom Audience updates that were found to be performed by
     * the background job.
     */
    public abstract int getNumberOfUpdatesFound();

    /**
     * Returns the number of scheduled Custom Audience updates that were successfully performed by
     * the background job.
     */
    public abstract int getNumberOfSuccessfulUpdates();

    /**
     * Returns a new builder for creating an instance of {@link
     * ScheduledCustomAudienceUpdateBackgroundJobStats}.
     */
    public static Builder builder() {
        return new AutoValue_ScheduledCustomAudienceUpdateBackgroundJobStats.Builder();
    }

    /** Builder for {@link ScheduledCustomAudienceUpdateBackgroundJobStats} objects. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the number of scheduled Custom Audience updates that were found to be performed. */
        public abstract Builder setNumberOfUpdatesFound(int value);

        /**
         * Sets the number of scheduled Custom Audience updates that were successfully performed.
         */
        public abstract Builder setNumberOfSuccessfulUpdates(int value);

        /**
         * Returns a new {@link ScheduledCustomAudienceUpdateBackgroundJobStats} instance built from
         * the values set on this builder.
         */
        public abstract ScheduledCustomAudienceUpdateBackgroundJobStats build();
    }
}
