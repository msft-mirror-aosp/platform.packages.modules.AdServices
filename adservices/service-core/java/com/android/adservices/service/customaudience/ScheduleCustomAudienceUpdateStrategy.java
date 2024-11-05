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

package com.android.adservices.service.customaudience;

import com.android.adservices.data.customaudience.DBCustomAudienceToLeave;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdateRequest;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.ScheduledCustomAudienceUpdatePerformedStats;

import com.google.common.util.concurrent.FluentFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.List;

/**
 * Interface for preparing the request body and parsing the schedule request when fetching updates
 * for scheduled custom audience updates
 */
public interface ScheduleCustomAudienceUpdateStrategy {

    /** Handles the schedule request from the response */
    FluentFuture<Void> scheduleRequests(
            String owner,
            boolean allowScheduleInResponse,
            JSONObject updateResponseJson,
            DevContext devContext,
            ScheduledCustomAudienceUpdatePerformedStats.Builder statsBuilder);

    /** Prepares the request body of the fetch update call */
    String prepareFetchUpdateRequestBody(
            JSONArray partialCustomAudienceJsonArray,
            List<DBCustomAudienceToLeave> customAudienceToLeaveList)
            throws JSONException;

    /** Returns the scheduled custom audience update request list */
    List<DBScheduledCustomAudienceUpdateRequest> getScheduledCustomAudienceUpdateRequestList(
            Instant beforeTime);
}
