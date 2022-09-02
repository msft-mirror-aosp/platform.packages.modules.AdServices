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

package com.android.adservices.adselection;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.AdSelectionServiceImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

/** Unit test for {@link AdSelectionService} */
public class AdSelectionServiceTest {

    private final Flags mFlagsWithAdSelectionSwitchOn = new FlagsWithKillSwitchOn();
    private final Flags mFlagsWithAdSelectionSwitchOff = new FlagsWithKillSwitchOff();

    @Mock private AdSelectionServiceImpl mMockAdSelectionServiceImpl;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBindableAdSelectionServiceKillSwitchOn() {
        AdSelectionService adSelectionService =
                new AdSelectionService(mFlagsWithAdSelectionSwitchOn);
        adSelectionService.onCreate();
        IBinder binder = adSelectionService.onBind(getIntentForAdSelectionService());
        assertNull(binder);
    }

    @Test
    public void testBindableAdSelectionServiceKillSwitchOff() {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(AdSelectionServiceImpl.class)
                        .startMocking();

        ExtendedMockito.doReturn(mMockAdSelectionServiceImpl)
                .when(() -> AdSelectionServiceImpl.create(any(Context.class)));

        AdSelectionService adSelectionService =
                new AdSelectionService(mFlagsWithAdSelectionSwitchOff);
        adSelectionService.onCreate();
        IBinder binder = adSelectionService.onBind(getIntentForAdSelectionService());
        assertNotNull(binder);

        session.finishMocking();
    }

    private Intent getIntentForAdSelectionService() {
        return new Intent(ApplicationProvider.getApplicationContext(), AdSelectionService.class);
    }

    private static class FlagsWithKillSwitchOn implements Flags {
        @Override
        public boolean getFledgeSelectAdsKillSwitch() {
            return true;
        }
    }

    private static class FlagsWithKillSwitchOff implements Flags {
        @Override
        public boolean getFledgeSelectAdsKillSwitch() {
            return false;
        }
    }
}
