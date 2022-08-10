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

package com.android.adservices.customaudience;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.Flags;
import com.android.adservices.service.customaudience.CustomAudienceServiceImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

public class CustomAudienceServiceTest {

    private final Flags mFlagsWithCustomAudienceSwitchOn = new FlagsWithKillSwitchOn();
    private final Flags mFlagsWithCustomAudienceSwitchOff = new FlagsWithKillSwitchOff();

    @Mock private CustomAudienceServiceImpl mMockCustomAudienceServiceImpl;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBindableCustomAudienceServiceKillSwitchOn() {
        CustomAudienceService customAudienceService =
                new CustomAudienceService(mFlagsWithCustomAudienceSwitchOn);
        customAudienceService.onCreate();
        IBinder binder = customAudienceService.onBind(getIntentForCustomAudienceService());
        assertNull(binder);
    }

    @Test
    public void testBindableCustomAudienceServiceKillSwitchOff() {

        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(CustomAudienceServiceImpl.class)
                        .startMocking();

        ExtendedMockito.doReturn(mMockCustomAudienceServiceImpl)
                .when(() -> CustomAudienceServiceImpl.create(any(Context.class)));

        CustomAudienceService customAudienceService =
                new CustomAudienceService(mFlagsWithCustomAudienceSwitchOff);
        customAudienceService.onCreate();
        IBinder binder = customAudienceService.onBind(getIntentForCustomAudienceService());
        assertNotNull(binder);

        session.finishMocking();
    }

    private Intent getIntentForCustomAudienceService() {
        return new Intent(ApplicationProvider.getApplicationContext(), CustomAudienceService.class);
    }

    private static class FlagsWithKillSwitchOn implements Flags {
        @Override
        public boolean getFledgeCustomAudienceServiceKillSwitch() {
            return true;
        }
    }

    private static class FlagsWithKillSwitchOff implements Flags {
        @Override
        public boolean getFledgeCustomAudienceServiceKillSwitch() {
            return false;
        }
    }
}
