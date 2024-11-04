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

package com.android.adservices.topics;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.download.MddJob;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.MaintenanceJobService;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.PackageChangedReceiver;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.encryptionkey.EncryptionKeyJobService;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.topics.EpochJob;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;
import org.mockito.Mock;

import java.util.function.Supplier;

/** Unit test for {@link com.android.adservices.topics.TopicsService}. */
@SpyStatic(FlagsFactory.class)
@SpyStatic(TopicsWorker.class)
@SpyStatic(ConsentManager.class)
@SpyStatic(AdServicesLoggerImpl.class)
@SpyStatic(MaintenanceJobService.class)
@SpyStatic(EncryptionKeyJobService.class)
@SpyStatic(EpochJob.class)
@SpyStatic(MddJob.class)
@SpyStatic(EnrollmentDao.class)
@SpyStatic(AppImportanceFilter.class)
@SpyStatic(PackageChangedReceiver.class)
public final class TopicsServiceTest extends AdServicesExtendedMockitoTestCase {

    @Mock private TopicsWorker mMockTopicsWorker;
    @Mock private ConsentManager mMockConsentManager;
    @Mock private EnrollmentDao mMockEnrollmentDao;
    @Mock private AppImportanceFilter mMockAppImportanceFilter;
    @Mock private AdServicesApiConsent mMockAdServicesApiConsent;

    @Test
    public void testBindableTopicsService_killswitchOff() {
        // Killswitch is off.
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();

        mocker.mockGetFlags(mMockFlags);

        doReturn(mMockTopicsWorker).when(TopicsWorker::getInstance);

        TopicsService spyTopicsService = spy(new TopicsService());
        doReturn(mMockConsentManager).when(() -> ConsentManager.getInstance());
        doReturn(true).when(mMockAdServicesApiConsent).isGiven();
        doReturn(mMockAdServicesApiConsent)
                .when(mMockConsentManager)
                .getConsent(AdServicesApiType.TOPICS);

        doReturn(true).when(() -> PackageChangedReceiver.enableReceiver(any(Context.class), any()));
        doReturn(true)
                .when(() -> MaintenanceJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        doReturn(true)
                .when(
                        () ->
                                EncryptionKeyJobService.scheduleIfNeeded(
                                        any(Context.class), eq(false)));
        ExtendedMockito.doNothing().when(EpochJob::schedule);
        ExtendedMockito.doNothing().when(MddJob::scheduleAllMddJobs);

        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);
        doReturn(mMockAppImportanceFilter)
                .when(() -> AppImportanceFilter.create(any(Context.class), any(Supplier.class)));

        spyTopicsService.onCreate();
        IBinder binder = spyTopicsService.onBind(getIntentForTopicsService());
        assertNotNull(binder);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS,
            times = 2)
    public void testBindableTopicsService_killswitchOn() {
        // Killswitch is on.
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();
        mocker.mockGetFlags(mMockFlags);

        TopicsService topicsService = new TopicsService();
        topicsService.onCreate();
        IBinder binder = topicsService.onBind(getIntentForTopicsService());
        assertNull(binder);
    }

    /**
     * Test whether the {@link TopicsService} works properly with the GA UX feature flag on. It
     * changes the behaviour of the consent - it's retrieved by a different method and it's per API.
     */
    @Test
    public void testBindableTopicsService_killswitchOffGaUxFeatureFlagOn() {
            // Killswitch is off.
            doReturn(false).when(mMockFlags).getTopicsKillSwitch();

        mocker.mockGetFlags(mMockFlags);

        doReturn(mMockTopicsWorker).when(TopicsWorker::getInstance);

            TopicsService spyTopicsService = spy(new TopicsService());
        doReturn(mMockConsentManager).when(() -> ConsentManager.getInstance());
        doReturn(true).when(mMockAdServicesApiConsent).isGiven();
            doReturn(mMockAdServicesApiConsent)
                    .when(mMockConsentManager)
                    .getConsent(AdServicesApiType.TOPICS);

        doReturn(true).when(() -> PackageChangedReceiver.enableReceiver(any(Context.class), any()));
        doReturn(true)
                .when(() -> MaintenanceJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        doReturn(true)
                .when(
                        () ->
                                EncryptionKeyJobService.scheduleIfNeeded(
                                        any(Context.class), eq(false)));
        ExtendedMockito.doNothing().when(EpochJob::schedule);
        ExtendedMockito.doNothing().when(MddJob::scheduleAllMddJobs);

        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);
        doReturn(mMockAppImportanceFilter)
                .when(() -> AppImportanceFilter.create(any(Context.class), any(Supplier.class)));

        spyTopicsService.onCreate();
        IBinder binder = spyTopicsService.onBind(getIntentForTopicsService());
        assertNotNull(binder);
        verifyMethodExecutionOnUserConsentGiven();
    }

    private Intent getIntentForTopicsService() {
        return new Intent(ApplicationProvider.getApplicationContext(), TopicsService.class);
    }

    private void verifyMethodExecutionOnUserConsentGiven() {
        verify(() -> PackageChangedReceiver.enableReceiver(any(Context.class), any()));
        verify(() -> MaintenanceJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(() -> EncryptionKeyJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(EpochJob::schedule);
        verify(MddJob::scheduleAllMddJobs);
    }
}
