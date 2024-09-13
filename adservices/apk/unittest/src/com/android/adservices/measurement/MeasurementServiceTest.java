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
package com.android.adservices.measurement;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.PackageChangedReceiver;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.measurement.MeasurementServiceImpl;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;
import org.mockito.Mock;

/** Unit test for {@link com.android.adservices.measurement.MeasurementService}. */
@SpyStatic(ConsentManager.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(PackageChangedReceiver.class)
public final class MeasurementServiceTest extends AdServicesExtendedMockitoTestCase {
    @Mock private ConsentManager mMockConsentManager;
    @Mock private MeasurementServiceImpl mSpyServiceImpl;
    private MeasurementService mSpyService;

    /** Test kill switch off with consent given */
    @Test
    public void testBindableMeasurementService_killSwitchOff_gaUxEnabled_consentGiven()
            throws Exception {
        runWithMocks(
                /* killSwitchOff */ false,
                /* consentNotifiedState */
                /* consentGiven */ true,
                () -> {
                    // Execute
                    final IBinder binder = onCreateAndOnBindService();

                    // Verification
                    assertNotNull(binder);
                    verify(mMockConsentManager, never()).getConsent();
                    verify(mMockConsentManager, times(1))
                            .getConsent(eq(AdServicesApiType.MEASUREMENTS));
                    ExtendedMockito.verify(
                            () -> PackageChangedReceiver.enableReceiver(any(Context.class), any()));
                    assertJobScheduled(/* timesCalled */ 1);
                });
    }

    /** Test kill switch off with consent revoked */
    @Test
    public void testBindableMeasurementService_killSwitchOff_gaUxEnabled_consentRevoked()
            throws Exception {
        runWithMocks(
                /* killSwitchOff */ false,
                /* consentNotifiedState */
                /* consentRevoked */ false,
                () -> {
                    // Execute
                    final IBinder binder = onCreateAndOnBindService();

                    // Verification
                    assertNotNull(binder);
                    verify(mMockConsentManager, never()).getConsent();
                    verify(mMockConsentManager, times(1))
                            .getConsent(eq(AdServicesApiType.MEASUREMENTS));
                    assertJobScheduled(/* timesCalled */ 0);
                });
    }

    /** Test kill switch on */
    @Test
    public void testBindableMeasurementService_killSwitchOn_gaUxEnabled() throws Exception {
        runWithMocks(
                /* killSwitchOn */ true,
                /* consentGiven */ true,
                () -> {
                    // Execute
                    final IBinder binder = onCreateAndOnBindService();

                    // Verification
                    assertNull(binder);
                    verify(mMockConsentManager, never()).getConsent();
                    verify(mMockConsentManager, never()).getConsent(any());
                    assertJobScheduled(/* timesCalled */ 0);
                });
    }

    private Intent getIntentForMeasurementService() {
        return new Intent(ApplicationProvider.getApplicationContext(), MeasurementService.class);
    }

    private IBinder onCreateAndOnBindService() {
        doReturn(mock(PackageManager.class)).when(mSpyService).getPackageManager();
        mSpyService.onCreate();
        return mSpyService.onBind(getIntentForMeasurementService());
    }

    private void runWithMocks(
            boolean killSwitchStatus, boolean consentStatus, TestUtils.RunnableWithThrow execute)
            throws Exception {
        mSpyService = spy(new MeasurementService(mSpyServiceImpl));
        doReturn(!killSwitchStatus).when(mMockFlags).getMeasurementEnabled();

        doNothing().when(mSpyServiceImpl).schedulePeriodicJobs(any());
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(mMockConsentManager).when(ConsentManager::getInstance);

        final AdServicesApiConsent mockConsent = mock(AdServicesApiConsent.class);
        doReturn(consentStatus).when(mockConsent).isGiven();

        doReturn(mockConsent)
                .when(mMockConsentManager)
                .getConsent(eq(AdServicesApiType.MEASUREMENTS));

        ExtendedMockito.doReturn(true)
                .when(() -> PackageChangedReceiver.enableReceiver(any(Context.class), any()));

        // Execute
        execute.run();
    }

    private void assertJobScheduled(int timesCalled) {
        verify(mSpyServiceImpl, times(timesCalled)).schedulePeriodicJobs(any());
    }
}
