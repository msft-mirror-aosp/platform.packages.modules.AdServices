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

package com.android.adservices.service.customaudience;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdDataFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;

import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;
import java.time.Instant;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class CustomAudienceManagementImplTest {

    private static final Instant NOW = Instant.now();

    private static final CustomAudience VALID_CUSTOM_AUDIENCE = new CustomAudience.Builder()
            .setOwner(CustomAudienceFixture.VALID_OWNER)
            .setBuyer(CustomAudienceFixture.VALID_BUYER)
            .setName(CustomAudienceFixture.VALID_NAME)
            .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
            .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
            .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
            .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
            .setTrustedBiddingData(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA)
            .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
            .setAds(AdDataFixture.VALID_ADS)
            .build();

    private static final DBCustomAudience VALID_DB_CUSTOM_AUDIENCE = new DBCustomAudience.Builder()
            .setOwner(CustomAudienceFixture.VALID_OWNER)
            .setBuyer(CustomAudienceFixture.VALID_BUYER)
            .setName(CustomAudienceFixture.VALID_NAME)
            .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
            .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
            .setCreationTime(NOW)
            .setLastUpdatedTime(NOW)
            .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
            .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
            .setTrustedBiddingData(new DBTrustedBiddingData.Builder()
                    .setUrl(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_URL)
                    .setKeys(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_KEYS)
                    .build())
            .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
            .setAds(AdDataFixture.VALID_ADS.stream()
                    .map(DBAdData::fromServiceObject)
                    .collect(Collectors.toList()))
            .build();

    @Mock
    private CustomAudienceDao mCustomAudienceDao;
    @Mock
    private Clock mClock;

    @InjectMocks
    public CustomAudienceManagementImpl mManagement;

    @Test
    public void testJoinCustomAudience_runNormally() throws JSONException {
        when(mClock.instant()).thenReturn(NOW);

        mManagement.joinCustomAudience(VALID_CUSTOM_AUDIENCE);

        verify(mCustomAudienceDao).insertOrOverrideCustomAudience(VALID_DB_CUSTOM_AUDIENCE);
        verify(mClock).instant();
        verifyNoMoreInteractions(mClock, mCustomAudienceDao);
    }

    @Test
    public void testLeaveCustomAudience_runNormally() {
        mManagement.leaveCustomAudience(CustomAudienceFixture.VALID_OWNER,
                CustomAudienceFixture.VALID_BUYER, CustomAudienceFixture.VALID_NAME);

        verify(mCustomAudienceDao).deleteCustomAudienceByPrimaryKey(
                CustomAudienceFixture.VALID_OWNER, CustomAudienceFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME);
        verifyNoMoreInteractions(mClock, mCustomAudienceDao);
    }
}
