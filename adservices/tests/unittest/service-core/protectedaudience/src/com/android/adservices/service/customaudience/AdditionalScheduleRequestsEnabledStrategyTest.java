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
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.BUYER;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.BUYER_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.LEAVE_CA_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.LEAVE_CA_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.MIN_DELAY;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.OWNER;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PACKAGE;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CUSTOM_AUDIENCE_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CUSTOM_AUDIENCE_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CUSTOM_AUDIENCE_3;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.UPDATE_ID;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.convertScheduleRequestToDBScheduledCustomAudienceUpdate;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createJsonResponsePayloadWithScheduleRequests;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createScheduleRequest;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createScheduleRequestWithUpdateUri;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.getPartialCustomAudienceJsonArray;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.getScheduleRequest_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.partialCustomAudienceListToJsonArray;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.adservices.customaudience.PartialCustomAudience;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudienceToLeave;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdate;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ScheduledCustomAudienceUpdatePerformedStats;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class AdditionalScheduleRequestsEnabledStrategyTest
        extends AdServicesExtendedMockitoTestCase {
    @Captor private ArgumentCaptor<Instant> mBeforeTimeArgumentCaptor;
    @Captor private ArgumentCaptor<DBScheduledCustomAudienceUpdate> mScheduledUpdateArgumentCaptor;
    @Captor private ArgumentCaptor<List<PartialCustomAudience>> mPartialCAListArgumentCaptor;
    @Captor private ArgumentCaptor<List<String>> mCAToLeaveListArgumentCaptor;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private ScheduledCustomAudienceUpdatePerformedStats.Builder mStatsBuilderMock;
    private AdditionalScheduleRequestsEnabledStrategyHelper mHelper;
    private AdditionalScheduleRequestsEnabledStrategy mStrategy;
    private DevContext mDevContext;
    private static final Instant NOW = Instant.now();

    @Before
    public void setup() {
        mHelper =
                new AdditionalScheduleRequestsEnabledStrategyHelper(
                        mContext,
                        FledgeAuthorizationFilter.create(
                                mContext, AdServicesLoggerImpl.getInstance()),
                        MIN_DELAY,
                        /* disableFledgeEnrollmentCheck= */ true);

        mStrategy =
                new AdditionalScheduleRequestsEnabledStrategy(
                        mCustomAudienceDaoMock,
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mHelper,
                        mAdServicesLoggerMock);

        mDevContext = DevContext.builder(PACKAGE).setDeviceDevOptionsEnabled(false).build();
    }

    @Test
    public void testScheduleRequests_AllowScheduleInResponseTrue_Success()
            throws JSONException, ExecutionException, InterruptedException {
        List<PartialCustomAudience> partialCustomAudienceList =
                List.of(PARTIAL_CUSTOM_AUDIENCE_1, PARTIAL_CUSTOM_AUDIENCE_2);
        List<String> customAudienceToLeaveList = List.of(LEAVE_CA_1);

        JSONObject scheduleRequest =
                createScheduleRequest(
                        BUYER,
                        MIN_DELAY,
                        partialCustomAudienceListToJsonArray(partialCustomAudienceList),
                        new JSONArray(customAudienceToLeaveList),
                        true);

        List<PartialCustomAudience> partialCustomAudienceList_2 =
                List.of(PARTIAL_CUSTOM_AUDIENCE_3);
        List<String> customAudienceToLeaveList_2 = List.of(LEAVE_CA_2);

        JSONObject scheduleRequest_2 =
                createScheduleRequest(
                        BUYER_2,
                        MIN_DELAY,
                        partialCustomAudienceListToJsonArray(partialCustomAudienceList_2),
                        new JSONArray(customAudienceToLeaveList_2),
                        true);

        JSONArray scheduleRequests = new JSONArray(List.of(scheduleRequest, scheduleRequest_2));

        mStrategy
                .scheduleRequests(
                        OWNER,
                        true,
                        createJsonResponsePayloadWithScheduleRequests(scheduleRequests),
                        mDevContext,
                        mStatsBuilderMock)
                .get();

        verify(mCustomAudienceDaoMock, times(2))
                .insertScheduledCustomAudienceUpdate(
                        mScheduledUpdateArgumentCaptor.capture(),
                        mPartialCAListArgumentCaptor.capture(),
                        mCAToLeaveListArgumentCaptor.capture(),
                        eq(true),
                        any());

        assertInsertScheduledCAUpdateArgumentCaptors(
                0, scheduleRequest, partialCustomAudienceList, customAudienceToLeaveList);
        assertInsertScheduledCAUpdateArgumentCaptors(
                1, scheduleRequest_2, partialCustomAudienceList_2, customAudienceToLeaveList_2);

        verify(mStatsBuilderMock, times(1)).setNumberOfScheduleUpdatesInResponse(2);
        verify(mStatsBuilderMock, times(1)).setNumberOfUpdatesScheduled(2);
    }

    @Test
    public void testScheduleRequests_AllowScheduleInResponseTrue_OnError_SkippingSchedule()
            throws JSONException, ExecutionException, InterruptedException {

        List<PartialCustomAudience> partialCustomAudienceList =
                List.of(PARTIAL_CUSTOM_AUDIENCE_1, PARTIAL_CUSTOM_AUDIENCE_2);
        List<String> customAudienceToLeaveList = List.of(LEAVE_CA_1);

        JSONObject scheduleRequest =
                createScheduleRequestWithUpdateUri(
                        null,
                        MIN_DELAY,
                        partialCustomAudienceListToJsonArray(partialCustomAudienceList),
                        new JSONArray(customAudienceToLeaveList),
                        true);

        JSONObject scheduleRequest2 =
                createScheduleRequest(
                        BUYER,
                        MIN_DELAY,
                        partialCustomAudienceListToJsonArray(partialCustomAudienceList),
                        new JSONArray(customAudienceToLeaveList),
                        true);

        JSONArray scheduleRequests = new JSONArray(List.of(scheduleRequest, scheduleRequest2));

        JSONObject jsonPayload = createJsonResponsePayloadWithScheduleRequests(scheduleRequests);

        mStrategy.scheduleRequests(OWNER, true, jsonPayload, mDevContext, mStatsBuilderMock).get();

        verify(mCustomAudienceDaoMock, times(1))
                .insertScheduledCustomAudienceUpdate(
                        mScheduledUpdateArgumentCaptor.capture(),
                        mPartialCAListArgumentCaptor.capture(),
                        mCAToLeaveListArgumentCaptor.capture(),
                        eq(true),
                        any());

        assertInsertScheduledCAUpdateArgumentCaptors(
                0, scheduleRequest2, partialCustomAudienceList, customAudienceToLeaveList);

        verify(mStatsBuilderMock, times(1)).setNumberOfScheduleUpdatesInResponse(2);
        verify(mStatsBuilderMock, times(1)).setNumberOfUpdatesScheduled(1);
    }

    @Test
    public void testScheduleRequests_AllowScheduleInResponseFalse_DoesNothing()
            throws JSONException, ExecutionException, InterruptedException {
        mStrategy
                .scheduleRequests(
                        OWNER,
                        false,
                        createJsonResponsePayloadWithScheduleRequests(
                                new JSONArray(List.of(getScheduleRequest_1()))),
                        mDevContext,
                        mStatsBuilderMock)
                .get();

        verify(mCustomAudienceDaoMock, never())
                .insertScheduledCustomAudienceUpdate(any(), any(), any(), anyBoolean(), any());

        verifyNoMoreInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void testPrepareFetchUpdateRequestBody_Success() throws JSONException {
        JSONArray paListJsonArray = getPartialCustomAudienceJsonArray();
        DBCustomAudienceToLeave caToLeave =
                DBCustomAudienceToLeave.builder()
                        .setUpdateId(UPDATE_ID)
                        .setName(LEAVE_CA_1)
                        .build();
        DBCustomAudienceToLeave caToLeave2 =
                DBCustomAudienceToLeave.builder()
                        .setUpdateId(UPDATE_ID)
                        .setName(LEAVE_CA_2)
                        .build();

        String result =
                mStrategy.prepareFetchUpdateRequestBody(
                        getPartialCustomAudienceJsonArray(), List.of(caToLeave, caToLeave2));

        JSONObject expectedJSONObject = new JSONObject();
        expectedJSONObject.put(PARTIAL_CUSTOM_AUDIENCES_KEY, paListJsonArray);
        expectedJSONObject.put(
                LEAVE_CUSTOM_AUDIENCE_KEY, new JSONArray(List.of(LEAVE_CA_1, LEAVE_CA_2)));

        assertThat(result).isEqualTo(expectedJSONObject.toString());
    }

    @Test
    public void testGetScheduledCustomAudienceUpdateRequestList_Success() {
        mStrategy.getScheduledCustomAudienceUpdateRequestList(NOW);

        verify(mCustomAudienceDaoMock)
                .getScheduledCustomAudienceUpdateRequestsWithLeave(
                        mBeforeTimeArgumentCaptor.capture());
        assertThat(mBeforeTimeArgumentCaptor.getValue()).isEqualTo(NOW);
    }

    private void assertInsertScheduledCAUpdateArgumentCaptors(
            int index,
            JSONObject scheduleRequest,
            List<PartialCustomAudience> paCaList,
            List<String> caToLeaveList)
            throws JSONException {
        DBScheduledCustomAudienceUpdate update =
                convertScheduleRequestToDBScheduledCustomAudienceUpdate(
                        OWNER, scheduleRequest, Instant.now());

        assertThat(mScheduledUpdateArgumentCaptor.getAllValues().get(index).getUpdateUri())
                .isEqualTo(update.getUpdateUri());
        assertThat(mScheduledUpdateArgumentCaptor.getAllValues().get(index).getBuyer())
                .isEqualTo(update.getBuyer());
        assertThat(mPartialCAListArgumentCaptor.getAllValues().get(index)).isEqualTo(paCaList);
        assertThat(mCAToLeaveListArgumentCaptor.getAllValues().get(index)).isEqualTo(caToLeaveList);
    }
}
