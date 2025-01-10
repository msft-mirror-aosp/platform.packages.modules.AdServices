/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.signals;

import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.download.MddJob;
import com.android.adservices.service.common.PackageChangedReceiver;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.signals.ProtectedSignalsServiceImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;
import org.mockito.Mock;

/** Service tests for protected signals */
@SpyStatic(ConsentManager.class)
@SpyStatic(ProtectedSignalsServiceImpl.class)
@SpyStatic(PackageChangedReceiver.class)
@SpyStatic(MddJob.class)
public final class ProtectedSignalsServiceTest extends AdServicesExtendedMockitoTestCase {

    @Mock private ProtectedSignalsServiceImpl mMockProtectedSignalsServiceImpl;
    @Mock private ConsentManager mMockConsentManager;
    @Mock private PackageManager mMockPackageManager;

    /**
     * Test whether the service is not bindable when the kill switch is off with the GA UX flag off.
     */
    @Test
    public void testBindableProtectedSignalsServiceKillSwitchOnGaUxDisabled() {
        setFlagsWithKillSwitchOnGaUxDisabled();
        ProtectedSignalsService protectedSignalsService = new ProtectedSignalsService(mFakeFlags);
        protectedSignalsService.onCreate();
        IBinder binder = protectedSignalsService.onBind(getIntentForProtectedSignalsService());
        assertNull(binder);

        verifyZeroInteractions(mMockConsentManager);
        verify(MddJob::scheduleAllMddJobs, never());
    }

    /**
     * Test whether the service is not bindable when the kill switch is on with the GA UX flag on.
     */
    @Test
    public void testBindableProtectedSignalsServiceKillSwitchOnGaUxEnabled() {
        setFlagsWithKillSwitchOnGaUxEnabled();
        ProtectedSignalsService protectedSignalsService = new ProtectedSignalsService(mFakeFlags);
        protectedSignalsService.onCreate();
        IBinder binder = protectedSignalsService.onBind(getIntentForProtectedSignalsService());
        assertNull(binder);

        verifyZeroInteractions(mMockConsentManager);
        verify(MddJob::scheduleAllMddJobs, never());
    }

    /**
     * Test whether the service is bindable and works properly when the kill switch is off with the
     * GA UX flag on.
     */
    @Test
    public void testBindableProtectedSignalsServiceKillSwitchOffGaUxEnabled() {
        doReturn(mMockProtectedSignalsServiceImpl)
                .when(() -> ProtectedSignalsServiceImpl.create(any(Context.class)));
        doReturn(mMockConsentManager).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mMockConsentManager)
                .getConsent(eq(AdServicesApiType.FLEDGE));
        ExtendedMockito.doReturn(true)
                .when(() -> PackageChangedReceiver.enableReceiver(any(Context.class), any()));
        doNothing().when(MddJob::scheduleAllMddJobs);

        setFlagsWithKillSwitchOffGaUxEnabled();
        ProtectedSignalsService protectedSignalsServiceSpy =
                new ProtectedSignalsService(mFakeFlags);

        spyOn(protectedSignalsServiceSpy);
        doReturn(mMockPackageManager).when(protectedSignalsServiceSpy).getPackageManager();

        protectedSignalsServiceSpy.onCreate();
        IBinder binder = protectedSignalsServiceSpy.onBind(getIntentForProtectedSignalsService());
        assertNotNull(binder);

        verify(mMockConsentManager, never()).getConsent();
        verify(mMockConsentManager).getConsent(eq(AdServicesApiType.FLEDGE));
        verify(() -> PackageChangedReceiver.enableReceiver(any(Context.class), any()));
        verify(MddJob::scheduleAllMddJobs);
    }

    private Intent getIntentForProtectedSignalsService() {
        return new Intent(mAppContext, ProtectedSignalsService.class);
    }

    private void setFlagsWithKillSwitchOnGaUxDisabled() {
        flags.setFlag(KEY_PROTECTED_SIGNALS_ENABLED, false);
        flags.setFlag(KEY_GA_UX_FEATURE_ENABLED, false);
    }

    private void setFlagsWithKillSwitchOnGaUxEnabled() {
        flags.setFlag(KEY_PROTECTED_SIGNALS_ENABLED, false);
        flags.setFlag(KEY_GA_UX_FEATURE_ENABLED, true);
    }

    private void setFlagsWithKillSwitchOffGaUxEnabled() {
        flags.setFlag(KEY_PROTECTED_SIGNALS_ENABLED, true);
        flags.setFlag(KEY_GA_UX_FEATURE_ENABLED, true);
    }
}
