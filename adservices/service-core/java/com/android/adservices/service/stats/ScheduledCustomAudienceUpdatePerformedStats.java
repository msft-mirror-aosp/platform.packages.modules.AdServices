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

/** Class for recording Custom Audience Update stats when a scheduled update is performed. */
@AutoValue
public abstract class ScheduledCustomAudienceUpdatePerformedStats {

    /** Returns the number of partial Custom Audiences in the update request. */
    public abstract int getNumberOfPartialCustomAudienceInRequest();

    /** Returns the number of leave Custom Audience operations in the update request. */
    public abstract int getNumberOfLeaveCustomAudienceInRequest();

    /** Returns the number of join Custom Audience operations in the update response. */
    public abstract int getNumberOfJoinCustomAudienceInResponse();

    /** Returns the number of leave Custom Audience operations in the update response. */
    public abstract int getNumberOfLeaveCustomAudienceInResponse();

    /** Returns the number of Custom Audiences successfully joined during the update. */
    public abstract int getNumberOfCustomAudienceJoined();

    /** Returns the number of Custom Audiences successfully left during the update. */
    public abstract int getNumberOfCustomAudienceLeft();

    /** Returns whether this update was the initial hop in the scheduled updates. */
    public abstract boolean getWasInitialHop();

    /** Returns the number of scheduled updates received in the update response. */
    public abstract int getNumberOfScheduleUpdatesInResponse();

    /** Returns the number of updates successfully scheduled during this update. */
    public abstract int getNumberOfUpdatesScheduled();

    /**
     * Returns a new builder for creating an instance of {@link
     * ScheduledCustomAudienceUpdatePerformedStats}.
     */
    public static Builder builder() {
        return new AutoValue_ScheduledCustomAudienceUpdatePerformedStats.Builder()
                .setWasInitialHop(true)
                .setNumberOfScheduleUpdatesInResponse(0)
                .setNumberOfUpdatesScheduled(0)
                .setNumberOfLeaveCustomAudienceInRequest(0);
    }

    /** Builder for {@link ScheduledCustomAudienceUpdatePerformedStats} objects. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the number of partial Custom Audiences in the update request. */
        public abstract Builder setNumberOfPartialCustomAudienceInRequest(int value);

        /** Sets the number of leave Custom Audience operations in the update request. */
        public abstract Builder setNumberOfLeaveCustomAudienceInRequest(int value);

        /** Sets the number of join Custom Audience operations in the update response. */
        public abstract Builder setNumberOfJoinCustomAudienceInResponse(int value);

        /** Sets the number of leave Custom Audience operations in the update response. */
        public abstract Builder setNumberOfLeaveCustomAudienceInResponse(int value);

        /** Sets the number of Custom Audiences successfully joined during the update. */
        public abstract Builder setNumberOfCustomAudienceJoined(int value);

        /** Sets the number of Custom Audiences successfully left during the update. */
        public abstract Builder setNumberOfCustomAudienceLeft(int value);

        /** Sets whether this update was the initial hop in a chain of scheduled updates. */
        public abstract Builder setWasInitialHop(boolean value);

        /** Sets the number of scheduled updates received in the update response. */
        public abstract Builder setNumberOfScheduleUpdatesInResponse(int value);

        /** Sets the number of updates successfully scheduled during this update. */
        public abstract Builder setNumberOfUpdatesScheduled(int value);

        /**
         * Returns a new {@link ScheduledCustomAudienceUpdatePerformedStats} instance built from the
         * values set on this builder.
         */
        public abstract ScheduledCustomAudienceUpdatePerformedStats build();
    }
}
