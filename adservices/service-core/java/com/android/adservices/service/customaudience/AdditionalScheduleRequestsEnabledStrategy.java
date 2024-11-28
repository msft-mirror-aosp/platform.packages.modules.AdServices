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

import static com.android.adservices.service.customaudience.AdditionalScheduleRequestsEnabledStrategyHelper.LEAVE_CUSTOM_AUDIENCE_KEY;
import static com.android.adservices.service.customaudience.AdditionalScheduleRequestsEnabledStrategyHelper.PARTIAL_CUSTOM_AUDIENCES_KEY;
import static com.android.adservices.service.customaudience.AdditionalScheduleRequestsEnabledStrategyHelper.SHOULD_REPLACE_PENDING_UPDATES_KEY;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_INTERNAL_ERROR;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_UNKNOWN;

import static com.google.common.util.concurrent.Futures.immediateVoidFuture;

import android.adservices.customaudience.PartialCustomAudience;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudienceToLeave;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdate;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdateRequest;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ScheduledCustomAudienceUpdatePerformedFailureStats;
import com.android.adservices.service.stats.ScheduledCustomAudienceUpdatePerformedStats;
import com.android.adservices.service.stats.ScheduledCustomAudienceUpdateScheduleAttemptedStats;

import com.google.common.util.concurrent.ExecutionSequencer;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RequiresApi(Build.VERSION_CODES.S)
public class AdditionalScheduleRequestsEnabledStrategy
        implements ScheduleCustomAudienceUpdateStrategy {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final int UPDATE_PERFORMED_INTERNAL_FAILURE =
            SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_INTERNAL_ERROR;

    private final CustomAudienceDao mCustomAudienceDao;
    private final ListeningExecutorService mBackgroundExecutor;
    private final ListeningExecutorService mLightWeightExecutor;
    private final AdditionalScheduleRequestsEnabledStrategyHelper
            mAdditionalScheduleRequestsEnabledStrategyHelper;
    private final AdServicesLogger mAdServicesLogger;

    AdditionalScheduleRequestsEnabledStrategy(
            CustomAudienceDao customAudienceDao,
            ListeningExecutorService backgroundExecutor,
            ListeningExecutorService lightWeightExecutor,
            AdditionalScheduleRequestsEnabledStrategyHelper
                    additionalScheduleCustomAudienceUpdateHelper,
            AdServicesLogger adServicesLogger) {
        mCustomAudienceDao = customAudienceDao;
        mBackgroundExecutor = backgroundExecutor;
        mLightWeightExecutor = lightWeightExecutor;
        mAdditionalScheduleRequestsEnabledStrategyHelper =
                additionalScheduleCustomAudienceUpdateHelper;
        mAdServicesLogger = adServicesLogger;
    }

    @Override
    public FluentFuture<Void> scheduleRequests(
            String owner,
            boolean allowScheduleInResponse,
            JSONObject updateResponseJson,
            DevContext devContext,
            ScheduledCustomAudienceUpdatePerformedStats.Builder statsBuilder) {
        if (!allowScheduleInResponse) {
            statsBuilder.setWasInitialHop(false);
            return FluentFuture.from(immediateVoidFuture());
        }
        List<ListenableFuture<Void>> persistScheduleRequestList = new ArrayList<>();
        ExecutionSequencer sequencer = ExecutionSequencer.create();
        List<JSONObject> scheduledRequests =
                mAdditionalScheduleRequestsEnabledStrategyHelper
                        .extractScheduleRequestsFromResponse(updateResponseJson);
        statsBuilder.setNumberOfScheduleUpdatesInResponse(scheduledRequests.size());
        AtomicInteger numberOfUpdatesScheduled = new AtomicInteger(0);
        for (JSONObject scheduleRequest : scheduledRequests) {
            try {
                DBScheduledCustomAudienceUpdate scheduledUpdate =
                        mAdditionalScheduleRequestsEnabledStrategyHelper
                                .validateAndConvertScheduleRequest(
                                        owner, scheduleRequest, Instant.now(), devContext);
                List<PartialCustomAudience> partialCustomAudienceList =
                        mAdditionalScheduleRequestsEnabledStrategyHelper
                                .extractPartialCustomAudiencesFromRequest(scheduleRequest);
                List<String> customAudienceLeave =
                        mAdditionalScheduleRequestsEnabledStrategyHelper
                                .extractCustomAudiencesToLeaveFromRequest(scheduleRequest);
                boolean shouldReplacePendingUpdates =
                        scheduleRequest.getBoolean(SHOULD_REPLACE_PENDING_UPDATES_KEY);
                persistScheduleRequestList.add(
                        sequencer.submitAsync(
                                () -> {
                                    try {
                                        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(
                                                scheduledUpdate,
                                                partialCustomAudienceList,
                                                customAudienceLeave,
                                                shouldReplacePendingUpdates,
                                                ScheduledCustomAudienceUpdateScheduleAttemptedStats
                                                        .builder());
                                        numberOfUpdatesScheduled.getAndIncrement();
                                    } catch (Throwable e) {
                                        sLogger.e(
                                                e,
                                                "Error while inserting Scheduled Custom Audience"
                                                        + " Update");
                                        logScheduleCAUpdateScheduleUpdatesPerformedFailureStats(
                                                UPDATE_PERFORMED_INTERNAL_FAILURE);
                                    }
                                    return immediateVoidFuture();
                                },
                                mBackgroundExecutor));
            } catch (Throwable e) {
                int failureType = SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_UNKNOWN;
                if (e instanceof JSONException) {
                    failureType = SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR;
                } else if (e instanceof IllegalArgumentException) {
                    failureType = SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_INTERNAL_ERROR;
                }
                logScheduleCAUpdateScheduleUpdatesPerformedFailureStats(failureType);
                sLogger.e(e, "Invalid schedule request, skipping scheduling for this request");
            }
        }

        return FluentFuture.from(Futures.successfulAsList(persistScheduleRequestList))
                .transform(
                        ignored -> {
                            statsBuilder.setNumberOfUpdatesScheduled(
                                    numberOfUpdatesScheduled.intValue());
                            return null;
                        },
                        mLightWeightExecutor);
    }

    private void logScheduleCAUpdateScheduleUpdatesPerformedFailureStats(int failureType) {
        ScheduledCustomAudienceUpdatePerformedFailureStats stats =
                ScheduledCustomAudienceUpdatePerformedFailureStats.builder()
                        .setFailureType(failureType)
                        .setFailureAction(
                                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE)
                        .build();
        mAdServicesLogger.logScheduledCustomAudienceUpdatePerformedFailureStats(stats);
    }

    @Override
    public String prepareFetchUpdateRequestBody(
            JSONArray partialCustomAudienceJsonArray,
            List<DBCustomAudienceToLeave> customAudienceToLeaveList)
            throws JSONException {
        JSONArray jsonArray = new JSONArray();

        for (int i = 0; i < customAudienceToLeaveList.size(); i++) {
            jsonArray.put(i, customAudienceToLeaveList.get(i).getName());
        }
        JSONObject jsonObject = new JSONObject();

        jsonObject.put(PARTIAL_CUSTOM_AUDIENCES_KEY, partialCustomAudienceJsonArray);
        jsonObject.put(LEAVE_CUSTOM_AUDIENCE_KEY, jsonArray);

        return jsonObject.toString();
    }

    @Override
    public List<DBScheduledCustomAudienceUpdateRequest> getScheduledCustomAudienceUpdateRequestList(
            Instant beforeTime) {
        return mCustomAudienceDao.getScheduledCustomAudienceUpdateRequestsWithLeave(beforeTime);
    }
}
