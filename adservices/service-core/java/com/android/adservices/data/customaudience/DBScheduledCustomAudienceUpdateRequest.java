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

package com.android.adservices.data.customaudience;

import androidx.annotation.NonNull;

import com.google.auto.value.AutoValue;

import java.util.Collections;
import java.util.List;

/** */
@AutoValue
public abstract class DBScheduledCustomAudienceUpdateRequest {
    /**
     * @return delayed {@link DBScheduledCustomAudienceUpdate} for this update request
     */
    public abstract @NonNull DBScheduledCustomAudienceUpdate getUpdate();

    /**
     * @return overrides for incoming custom audiences
     */
    public abstract @NonNull List<DBPartialCustomAudience> getPartialCustomAudienceList();

    /**
     * @return custom audiences requested to be left with this update request
     */
    public abstract @NonNull List<DBCustomAudienceToLeave> getCustomAudienceToLeaveList();

    /** Builder for creating an object of type {@link DBScheduledCustomAudienceUpdateRequest} */
    public static Builder builder() {
        return new AutoValue_DBScheduledCustomAudienceUpdateRequest.Builder()
                .setPartialCustomAudienceList(Collections.emptyList())
                .setCustomAudienceToLeaveList(Collections.emptyList());
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /** see {@link #getUpdate()} */
        public abstract Builder setUpdate(DBScheduledCustomAudienceUpdate update);

        /** see {@link #getPartialCustomAudienceList()} */
        public abstract Builder setPartialCustomAudienceList(List<DBPartialCustomAudience> list);

        /** see {@link #getCustomAudienceToLeaveList()} */
        public abstract Builder setCustomAudienceToLeaveList(List<DBCustomAudienceToLeave> list);

        /** Builds a {@link DBScheduledCustomAudienceUpdateRequest.Builder} */
        public abstract DBScheduledCustomAudienceUpdateRequest build();
    }
}
