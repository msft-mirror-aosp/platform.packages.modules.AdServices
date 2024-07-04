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

package com.android.adservices.appsetid;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.appsetid.AppSetIdWorker;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Unit test for {@link AppSetIdService}. */
@SpyStatic(FlagsFactory.class)
@SpyStatic(AppSetIdWorker.class)
@SpyStatic(AdServicesLoggerImpl.class)
@SpyStatic(AppImportanceFilter.class)
public final class AppSetIdServiceTest extends AdServicesExtendedMockitoTestCase {
    @Mock Flags mMockFlags;
    @Mock AppSetIdWorker mMockAppSetIdWorker;
    @Mock AppImportanceFilter mMockAppImportanceFilter;
    @Mock PackageManager mMockPackageManager;
    @Mock AdServicesLoggerImpl mMockAdServicesLoggerImpl;

    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);
        mocker.mockAdServicesLoggerImpl(mMockAdServicesLoggerImpl);

        doReturn(mMockAppSetIdWorker).when(AppSetIdWorker::getInstance);
    }

    @Test
    public void testBindableAppSetIdService_killswitchOff() {
        // Killswitch is off.
        when(mMockFlags.getAppSetIdKillSwitch()).thenReturn(false);

        AppSetIdService spyAppSetIdService = spy(AppSetIdService.class);
        doReturn(mMockPackageManager).when(spyAppSetIdService).getPackageManager();
        doReturn(mMockAppImportanceFilter).when(() -> AppImportanceFilter.create(any(), any()));

        spyAppSetIdService.onCreate();
        IBinder binder = spyAppSetIdService.onBind(getIntentForAppSetIdService());

        assertThat(binder).isNotNull();

        StringWriter writer = new StringWriter();
        spyAppSetIdService.dump(/* fd= */ null, new PrintWriter(writer), /* args */ null);
        assertThat(writer.toString()).contains("nothing to dump");
    }

    @Test
    public void testBindableAppSetIdService_killswitchOn() {
        // Killswitch is on.
        when(mMockFlags.getAppSetIdKillSwitch()).thenReturn(true);

        AppSetIdService appSetIdService = new AppSetIdService();

        appSetIdService.onCreate();
        IBinder binder = appSetIdService.onBind(getIntentForAppSetIdService());

        assertThat(binder).isNull();
    }

    private Intent getIntentForAppSetIdService() {
        return new Intent(mContext, AppSetIdService.class);
    }
}
