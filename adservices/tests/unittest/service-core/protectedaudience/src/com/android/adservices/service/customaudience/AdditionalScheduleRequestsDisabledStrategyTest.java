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

import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.DB_CUSTOM_AUDIENCE_TO_LEAVE_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.DB_CUSTOM_AUDIENCE_TO_LEAVE_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.OWNER;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PACKAGE;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createRequestBodyWithOnlyPartialCustomAudiences;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.getPartialCustomAudienceJsonArray;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.getScheduleRequest_1;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudienceToLeave;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.ScheduledCustomAudienceUpdatePerformedStats;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class AdditionalScheduleRequestsDisabledStrategyTest
        extends AdServicesExtendedMockitoTestCase {
    @Captor private ArgumentCaptor<Instant> mBeforeTimeArgumentCaptor;
    @Mock private CustomAudienceDao mCustomAudienceDao;
    private AdditionalScheduleRequestsDisabledStrategy mStrategy;
    @Mock private ScheduledCustomAudienceUpdatePerformedStats.Builder mStatsBuilderMock;

    @Before
    public void setup() {
        mStrategy = new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao);
    }

    @Test
    public void testScheduleRequests_DoesNothing()
            throws JSONException, ExecutionException, InterruptedException {
        mStrategy
                .scheduleRequests(
                        OWNER,
                        true,
                        getScheduleRequest_1(),
                        DevContext.builder(PACKAGE).setDeviceDevOptionsEnabled(false).build(),
                        mStatsBuilderMock)
                .get();

        verify(mCustomAudienceDao, times(0))
                .insertScheduledCustomAudienceUpdate(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    public void testPrepareFetchUpdateRequestBody_ReturnsCorrectValue() throws JSONException {
        List<DBCustomAudienceToLeave> dbCustomAudienceToLeaveList =
                List.of(DB_CUSTOM_AUDIENCE_TO_LEAVE_1, DB_CUSTOM_AUDIENCE_TO_LEAVE_2);

        String result =
                mStrategy.prepareFetchUpdateRequestBody(
                        getPartialCustomAudienceJsonArray(), dbCustomAudienceToLeaveList);

        assertThat(result)
                .isEqualTo(
                        createRequestBodyWithOnlyPartialCustomAudiences(
                                getPartialCustomAudienceJsonArray()));
    }

    @Test
    public void testGetScheduledCustomAudienceUpdateRequestList_Success() {
        Instant beforeTime = Instant.now();

        mStrategy.getScheduledCustomAudienceUpdateRequestList(beforeTime);

        verify(mCustomAudienceDao, times(0))
                .getScheduledCustomAudienceUpdateRequestsWithLeave(any());
        verify(mCustomAudienceDao)
                .getScheduledCustomAudienceUpdateRequests(mBeforeTimeArgumentCaptor.capture());
        assertThat(mBeforeTimeArgumentCaptor.getValue()).isEqualTo(beforeTime);
    }
}
