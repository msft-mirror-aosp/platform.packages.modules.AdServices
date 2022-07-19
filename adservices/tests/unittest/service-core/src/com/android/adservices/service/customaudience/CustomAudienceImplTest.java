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

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;

@RunWith(MockitoJUnitRunner.class)
public class CustomAudienceImplTest {
    private static final CustomAudience VALID_CUSTOM_AUDIENCE =
            CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER).build();

    private static final DBCustomAudience VALID_DB_CUSTOM_AUDIENCE =
            DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER).build();

    @Mock
    private CustomAudienceDao mCustomAudienceDao;
    @Mock private Clock mClock;

    public CustomAudienceImpl mImpl;

    @Before
    public void setup() {
        mImpl = new CustomAudienceImpl(mCustomAudienceDao, mClock);
    }

    @Test
    public void testJoinCustomAudience_runNormally() {

        when(mClock.instant()).thenReturn(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);

        mImpl.joinCustomAudience(VALID_CUSTOM_AUDIENCE);

        verify(mCustomAudienceDao)
                .insertOrOverwriteCustomAudience(
                        VALID_DB_CUSTOM_AUDIENCE,
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER));
        verify(mClock).instant();
        verifyNoMoreInteractions(mClock, mCustomAudienceDao);
    }

    @Test
    public void testLeaveCustomAudience_runNormally() {
        mImpl.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME);

        verify(mCustomAudienceDao)
                .deleteAllCustomAudienceDataByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER,
                        CustomAudienceFixture.VALID_NAME);

        verifyNoMoreInteractions(mClock, mCustomAudienceDao);
    }
}
