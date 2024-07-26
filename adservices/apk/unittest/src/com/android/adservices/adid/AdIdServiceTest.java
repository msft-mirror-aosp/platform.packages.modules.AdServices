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

package com.android.adservices.adid;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adid.AdIdWorker;
import com.android.adservices.service.common.AppImportanceFilter;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.function.Supplier;

/** Unit test for {@link com.android.adservices.adid.AdIdService}. */
@SpyStatic(FlagsFactory.class)
public final class AdIdServiceTest extends AdServicesExtendedMockitoTestCase {

    @Mock private Flags mMockFlags;
    @Mock private AdIdWorker mMockAdIdWorker;
    @Mock private AppImportanceFilter mMockAppImportanceFilter;
    @Mock private PackageManager mMockPackageManager;

    @Before
    public void setFixtures() {
        mocker.mockGetFlags(mMockFlags);
    }

    /** Test adId api level behavior with killswitch off. */
    @SpyStatic(AdIdWorker.class)
    @SpyStatic(AppImportanceFilter.class)
    @Test
    public void testBindableAdIdService_killswitchOff() {
        // Killswitch is off.
        mockAdIdKillSwitch(false);

        doReturn(mMockAdIdWorker).when(() -> AdIdWorker.getInstance());

        AdIdService spyAdIdService = spy(new AdIdService());
        doReturn(mMockPackageManager).when(spyAdIdService).getPackageManager();
        doReturn(mMockAppImportanceFilter)
                .when(() -> AppImportanceFilter.create(any(Context.class), any(Supplier.class)));

        spyAdIdService.onCreate();
        IBinder binder = spyAdIdService.onBind(getIntentForAdIdService());
        expect.withMessage("onBind()").that(binder).isNotNull();
    }

    /** Test adId api level behavior with killswitch on. */
    @Test
    public void testBindableAdIdService_killswitchOn() {
        // Killswitch is on.
        mockAdIdKillSwitch(true);

        AdIdService adidService = new AdIdService();
        adidService.onCreate();
        IBinder binder = adidService.onBind(getIntentForAdIdService());

        expect.withMessage("onBind()").that(binder).isNull();
    }

    private void mockAdIdKillSwitch(boolean value) {
        when(mMockFlags.getAdIdKillSwitch()).thenReturn(value);
    }

    private Intent getIntentForAdIdService() {
        return new Intent(ApplicationProvider.getApplicationContext(), AdIdService.class);
    }
}
