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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;

import com.android.adservices.customaudience.DBCustomAudienceBackgroundFetchDataFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;

import java.time.Instant;

public class BackgroundFetchRunnerTest {
    private final Flags mFlags = FlagsFactory.getFlagsForTest();

    private MockitoSession mStaticMockSession = null;

    @Mock private CustomAudienceDao mCustomAudienceDaoMock;

    private BackgroundFetchRunner mBackgroundFetchRunnerSpy;

    @Before
    public void setup() {
        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .initMocks(this)
                        .startMocking();

        mBackgroundFetchRunnerSpy = new BackgroundFetchRunner(mCustomAudienceDaoMock, mFlags);
        spyOn(mBackgroundFetchRunnerSpy);
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testBackgroundFetchRunnerNullInputsCauseFailure() {
        assertThrows(NullPointerException.class, () -> new BackgroundFetchRunner(null, mFlags));
        assertThrows(
                NullPointerException.class,
                () -> new BackgroundFetchRunner(mCustomAudienceDaoMock, null));
    }

    @Test
    public void testBackgroundFetchRunnerUpdateCustomAudienceSuccessfulFetch() {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptySuccessfulResponse().build();
        doReturn(updatableData)
                .when(mBackgroundFetchRunnerSpy)
                .fetchAndValidateCustomAudienceUpdatableData(any(), any());

        Instant originalEligibleUpdateTime = CommonFixture.FIXED_NOW.minusMillis(60L * 1000L);
        Instant expectedEligibleUpdateTime =
                DBCustomAudienceBackgroundFetchData
                        .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                CommonFixture.FIXED_NOW, mFlags);
        DBCustomAudienceBackgroundFetchData originalFetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilder()
                        .setEligibleUpdateTime(originalEligibleUpdateTime)
                        .build();
        DBCustomAudienceBackgroundFetchData expectedFetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilder()
                        .setEligibleUpdateTime(expectedEligibleUpdateTime)
                        .build();

        mBackgroundFetchRunnerSpy.updateCustomAudience(CommonFixture.FIXED_NOW, originalFetchData);

        verify(mCustomAudienceDaoMock)
                .updateCustomAudienceAndBackgroundFetchData(
                        eq(expectedFetchData), eq(updatableData));
        verify(mCustomAudienceDaoMock, never()).persistCustomAudienceBackgroundFetchData(any());
    }

    @Test
    public void testBackgroundFetchRunnerUpdateCustomAudienceFailedFetch() {
        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptyFailedResponse().build();
        doReturn(updatableData)
                .when(mBackgroundFetchRunnerSpy)
                .fetchAndValidateCustomAudienceUpdatableData(any(), any());

        Instant originalEligibleUpdateTime = CommonFixture.FIXED_NOW.minusMillis(60L * 1000L);
        DBCustomAudienceBackgroundFetchData originalFetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilder()
                        .setEligibleUpdateTime(originalEligibleUpdateTime)
                        .build();
        DBCustomAudienceBackgroundFetchData expectedFetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilder()
                        .setEligibleUpdateTime(originalEligibleUpdateTime)
                        .setNumValidationFailures(1)
                        .build();

        mBackgroundFetchRunnerSpy.updateCustomAudience(CommonFixture.FIXED_NOW, originalFetchData);

        verify(mCustomAudienceDaoMock, never())
                .updateCustomAudienceAndBackgroundFetchData(any(), any());
        verify(mCustomAudienceDaoMock)
                .persistCustomAudienceBackgroundFetchData(eq(expectedFetchData));
    }
}
