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

package com.android.adservices.service.common;

import static com.android.adservices.service.common.ConsentNotificationJobService.ADID_ENABLE_STATUS;
import static com.android.adservices.service.common.ConsentNotificationJobService.IS_V2_NOTIFICATION;
import static com.android.adservices.service.common.ConsentNotificationJobService.MILLISECONDS_IN_THE_DAY;
import static com.android.adservices.service.common.ConsentNotificationJobService.RE_CONSENT_STATUS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.PersistableBundle;

import com.android.adservices.common.AdServicesJobServiceTestCase;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

import java.time.Duration;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;

/** Unit test for {@link com.android.adservices.service.common.ConsentNotificationJobService}. */
@SpyStatic(FlagsFactory.class)
@SpyStatic(DebugFlags.class)
@SpyStatic(ConsentManager.class)
@SpyStatic(AdServicesSyncUtil.class)
@SpyStatic(ConsentNotificationJobService.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@SpyStatic(UxStatesManager.class)
@MockStatic(ServiceCompatUtils.class)
public final class ConsentNotificationJobServiceTest extends AdServicesJobServiceTestCase {

    @Spy
    private ConsentNotificationJobService mConsentNotificationJobService =
            new ConsentNotificationJobService();

    @Mock private ConsentManager mConsentManager;
    @Mock private JobParameters mMockJobParameters;
    @Mock private PackageManager mPackageManager;
    @Mock private AdServicesSyncUtil mAdservicesSyncUtil;
    @Mock private PersistableBundle mPersistableBundle;
    @Mock private JobScheduler mMockJobScheduler;
    @Mock private SharedPreferences mSharedPreferences;
    @Mock private SharedPreferences.Editor mEditor;
    @Mock UxStatesManager mUxStatesManager;
    private AdServicesJobServiceLogger mSpyLogger;
    private final long mIntervalEndMs = Duration.ofHours(17).toMillis();
    private final long mIntervalBeginMs = Duration.ofHours(9).toMillis();
    private final long mMinimalDelayBeforeIntervalEnds = Duration.ofHours(1).toMillis();

    /** Initialize static spies. */
    @Before
    public void setup() {
        doReturn(mPackageManager).when(mConsentNotificationJobService).getPackageManager();
        mocker.mockGetFlags(mMockFlags);
        mocker.mockGetDebugFlags(mMockDebugFlags);
        when(mPersistableBundle.getBoolean(eq(IS_V2_NOTIFICATION), anyBoolean())).thenReturn(false);
        when(mMockFlags.getConsentNotificationIntervalEndMs()).thenReturn(mIntervalEndMs);
        when(mMockFlags.getConsentNotificationIntervalBeginMs()).thenReturn(mIntervalBeginMs);
        when(mMockFlags.getConsentNotificationMinimalDelayBeforeIntervalEnds())
                .thenReturn(mMinimalDelayBeforeIntervalEnds);
        mSpyLogger = mocker.mockNoOpAdServicesJobServiceLogger(mContext, mMockFlags);
        ExtendedMockito.doReturn(mUxStatesManager).when(UxStatesManager::getInstance);
        ExtendedMockito.doReturn(mConsentManager).when(ConsentManager::getInstance);

        mConsentNotificationJobService.setConsentManager(mConsentManager);
        mConsentNotificationJobService.setUxStatesManager(mUxStatesManager);
    }

    @Test
    public void testOnStartJobAsyncUtilExecute_withLogging() throws Exception {
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(mSpyLogger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mSpyLogger);

        testOnStartJobAsyncUtilExecute();

        verifyJobFinishedLogged(mSpyLogger, onStartJobCallback, onJobDoneCallback);
    }

    /** Test GA UX disabled and reconsent, onStart method will not execute the job */
    @Test
    public void testOnStartJobAsyncUtilExecute_Reconsent_GaUxDisabled() throws Exception {
        clearInvocations(mAdservicesSyncUtil);
        mockServiceCompatUtilDisableJob(false);
        mocker.mockGetConsentNotificationDebugMode(false);
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        ConsentManager consentManager = mock(ConsentManager.class);
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mPackageManager).when(mConsentNotificationJobService).getPackageManager();
        mConsentNotificationJobService.setConsentManager(consentManager);

        Mockito.doReturn(true).when(mUxStatesManager).isEeaDevice();
        when(mPersistableBundle.getBoolean(eq(RE_CONSENT_STATUS), anyBoolean())).thenReturn(true);
        mockAdIdEnabled();
        doReturn(mAdservicesSyncUtil).when(AdServicesSyncUtil::getInstance);
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mConsentNotificationJobService)
                .jobFinished(mMockJobParameters, false);

        doNothing().when(mAdservicesSyncUtil).execute(any(Context.class), any(Boolean.class));
        mocker.mockGetConsentNotificationDebugMode(false);

        mConsentNotificationJobService.onStartJob(mMockJobParameters);
        jobFinishedCountDown.await();

        verify(mAdservicesSyncUtil, times(0)).execute(any(Context.class), any(Boolean.class));
        verify(mConsentNotificationJobService).jobFinished(mMockJobParameters, false);
    }

    /** Test reconsent false, onStart method will execute the job */
    @Test
    public void testOnStartJobAsyncUtilExecute_ReconsentFalse() throws Exception {
        mockServiceCompatUtilDisableJob(false);
        mocker.mockGetConsentNotificationDebugMode(false);
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        ConsentManager consentManager = mock(ConsentManager.class);
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mPackageManager).when(mConsentNotificationJobService).getPackageManager();
        mConsentNotificationJobService.setConsentManager(consentManager);
        doReturn(consentManager).when(ConsentManager::getInstance);
        Mockito.doReturn(true).when(mUxStatesManager).isEeaDevice();
        when(mMockJobParameters.getExtras()).thenReturn(mPersistableBundle);
        when(mPersistableBundle.getBoolean(eq(ADID_ENABLE_STATUS), anyBoolean())).thenReturn(true);
        when(mPersistableBundle.getBoolean(eq(RE_CONSENT_STATUS), anyBoolean())).thenReturn(false);
        doReturn(mAdservicesSyncUtil).when(AdServicesSyncUtil::getInstance);
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mConsentNotificationJobService)
                .jobFinished(mMockJobParameters, false);

        doNothing().when(mAdservicesSyncUtil).execute(any(Context.class), any(Boolean.class));
        mocker.mockGetConsentNotificationDebugMode(false);

        mConsentNotificationJobService.onStartJob(mMockJobParameters);
        jobFinishedCountDown.await();

        verify(mAdservicesSyncUtil, times(1)).execute(any(Context.class), any(Boolean.class));
        verify(mConsentNotificationJobService).jobFinished(mMockJobParameters, false);
    }

    @Test
    public void testOnStartJobShouldDisableJobTrue() {
        mockServiceCompatUtilDisableJob(true);
        doReturn(mMockJobScheduler)
                .when(mConsentNotificationJobService)
                .getSystemService(JobScheduler.class);
        doNothing().when(mConsentNotificationJobService).jobFinished(mMockJobParameters, false);

        assertThat(mConsentNotificationJobService.onStartJob(mMockJobParameters)).isFalse();

        verify(mConsentNotificationJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(mConsentManager);

        // Verify logging has not happened even though logging is enabled because this field is not
        // logged
        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void testOnStopJob_withLogging() throws Exception {
        JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

        testOnStopJob();

        verifyOnStopJobLogged(mSpyLogger, callback);
    }

    /**
     * Test calculation of initial delay and maximum deadline for task which is being scheduled
     * before approved interval.
     */
    @Test
    public void testDelaysWhenCalledBeforeIntervalBegins() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Etc/UTC"));
        calendar.setTimeInMillis(0);

        long initialDelay = ConsentNotificationJobService.calculateInitialDelay(calendar);
        long deadline = ConsentNotificationJobService.calculateDeadline(calendar);

        assertThat(initialDelay).isEqualTo(mIntervalBeginMs);
        assertThat(deadline).isEqualTo(mIntervalEndMs);
    }

    /**
     * Test calculation of initial delay and maximum deadline for task which is being scheduled
     * within approved interval.
     */
    @Test
    public void testDelaysWhenCalledWithinTheInterval() {
        long expectedDelay = /* 100 seconds */ 100000;
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Etc/UTC"));
        calendar.setTimeInMillis(mIntervalBeginMs + expectedDelay);

        long initialDelay = ConsentNotificationJobService.calculateInitialDelay(calendar);
        long deadline = ConsentNotificationJobService.calculateDeadline(calendar);

        assertThat(initialDelay).isEqualTo(0L);
        assertThat(deadline).isEqualTo(mIntervalEndMs - (mIntervalBeginMs + expectedDelay));
    }

    /**
     * Test calculation of initial delay and maximum deadline for task which is being scheduled
     * after approved interval (the same day).
     */
    @Test
    public void testDelaysWhenCalledAfterTheInterval() {
        long delay = /* 100 seconds */ 100000;
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Etc/UTC"));
        calendar.setTimeInMillis(mIntervalEndMs + delay);

        long initialDelay = ConsentNotificationJobService.calculateInitialDelay(calendar);
        long deadline = ConsentNotificationJobService.calculateDeadline(calendar);

        long midnight = MILLISECONDS_IN_THE_DAY - (mIntervalEndMs + delay);

        assertThat(initialDelay).isEqualTo(midnight + mIntervalBeginMs);
        assertThat(deadline).isEqualTo(midnight + mIntervalEndMs);
    }

    @Test
    public void testDelaysWhenDebugModeOn() {
        mocker.mockGetConsentNotificationDebugMode(true);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(0);
        long initialDelay = ConsentNotificationJobService.calculateInitialDelay(calendar);
        long deadline = ConsentNotificationJobService.calculateDeadline(calendar);
        assertThat(initialDelay).isEqualTo(0L);
        assertThat(deadline).isEqualTo(0L);
    }

    @Test
    public void testSchedule_jobInfoIsPersisted() {
        final ArgumentCaptor<JobInfo> argumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        when(mMockContext.getSystemService(JobScheduler.class)).thenReturn(mMockJobScheduler);
        when(mMockContext.getPackageName()).thenReturn("testSchedule_jobInfoIsPersisted");
        when(mMockContext.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferences);
        when(mSharedPreferences.edit()).thenReturn(mEditor);
        when(mEditor.putInt(anyString(), anyInt())).thenReturn(mEditor);
        Mockito.doNothing().when(mEditor).apply();

        ConsentNotificationJobService.schedule(mMockContext, true, false);

        Mockito.verify(mMockJobScheduler, times(1)).schedule(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isNotNull();
        assertThat(argumentCaptor.getValue().isPersisted()).isTrue();
    }

    /** Test that when the OTA strings feature is on, no notifications are sent immediately. */
    @Test
    public void testOnStartJob_otaStringsFeatureEnabled() throws Exception {
        mockAdIdEnabled();
        mockEuDevice();
        mockGaUxEnabled();
        mockConsentDebugMode(/* enabled */ false);

        mockOtaStringsFeature(/* enabled */ true);
        mockJobFinished();

        verify(mAdservicesSyncUtil, times(0)).getInstance();
    }

    /** Test that when the OTA resources feature is on, no notifications are sent immediately. */
    @Test
    public void testOnStartJob_otaResourcesFeatureEnabled() throws Exception {
        mockAdIdEnabled();
        mockEuDevice();
        mockGaUxEnabled();
        mockConsentDebugMode(/* enabled */ false);

        mockOtaResourcesFeature(/* enabled */ true);
        mockJobFinished();

        verify(mAdservicesSyncUtil, times(0)).getInstance();
    }

    /** Test that when the OTA features are disabled, the notification is sent immediately. */
    @Test
    public void testOnStartJob_otaFeatureDisabled() throws Exception {
        mockAdIdEnabled();
        mockEuDevice();
        mockGaUxEnabled();
        mockConsentDebugMode(/* enabled */ false);

        mockOtaStringsFeature(/* enabled */ false);
        mockOtaResourcesFeature(/* enabled */ false);
        mockJobFinished();

        verify(mAdservicesSyncUtil).execute(any(Context.class), any(Boolean.class));
    }

    /** Test that the notification will be sent immediately when OTA deadline passed. */
    @Test
    public void testOnStartJob_otaStringsDeadlinePassed() throws Exception {
        mockAdIdEnabled();
        mockEuDevice();
        mockGaUxEnabled();
        mockConsentDebugMode(/* enabled */ false);

        mockOtaStringsFeature(/* enabled */ true);
        when(mMockFlags.getUiOtaStringsDownloadDeadline()).thenReturn(Long.valueOf(0));
        mockJobFinished();

        verify(mAdservicesSyncUtil, times(1)).execute(any(Context.class), any(Boolean.class));
    }

    /** Test that the notification will be sent immediately when OTA deadline passed. */
    @Test
    public void testOnStartJob_otaResourcesDeadlinePassed() throws Exception {
        mockAdIdEnabled();
        mockEuDevice();
        mockGaUxEnabled();
        mockConsentDebugMode(/* enabled */ false);

        mockOtaResourcesFeature(/* enabled */ true);
        when(mMockFlags.getUiOtaStringsDownloadDeadline()).thenReturn(Long.valueOf(0));
        mockJobFinished();

        verify(mAdservicesSyncUtil, times(1)).execute(any(Context.class), any(Boolean.class));
    }

    /** Test V2 Job */
    @Test
    public void testOnStartJobV2() throws Exception {
        mockServiceCompatUtilDisableJob(false);
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);
        when(mPersistableBundle.getBoolean(eq(IS_V2_NOTIFICATION), anyBoolean())).thenReturn(true);
        doReturn(mPackageManager).when(mConsentNotificationJobService).getPackageManager();
        when(mMockJobParameters.getExtras()).thenReturn(mPersistableBundle);
        doReturn(mAdservicesSyncUtil).when(AdServicesSyncUtil::getInstance);
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mConsentNotificationJobService)
                .jobFinished(mMockJobParameters, false);

        doNothing()
                .when(mAdservicesSyncUtil)
                .executeNotificationTriggerV2(
                        any(Context.class),
                        any(Boolean.class),
                        any(Boolean.class),
                        any(Boolean.class));

        mConsentNotificationJobService.onStartJob(mMockJobParameters);
        jobFinishedCountDown.await();

        verify(mAdservicesSyncUtil)
                .executeNotificationTriggerV2(
                        any(Context.class),
                        any(Boolean.class),
                        any(Boolean.class),
                        any(Boolean.class));
        verify(mConsentNotificationJobService).jobFinished(mMockJobParameters, false);
    }

    private void testOnStartJobAsyncUtilExecute() throws Exception {
        mockServiceCompatUtilDisableJob(false);
        mocker.mockGetConsentNotificationDebugMode(false);
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        ConsentManager consentManager = mock(ConsentManager.class);
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mPackageManager).when(mConsentNotificationJobService).getPackageManager();
        doReturn(Boolean.FALSE).when(consentManager).wasNotificationDisplayed();
        doReturn(Boolean.TRUE).when(consentManager).wasGaUxNotificationDisplayed();
        doNothing().when(consentManager).recordNotificationDisplayed(true);
        doNothing().when(consentManager).recordGaUxNotificationDisplayed(true);
        mConsentNotificationJobService.setConsentManager(consentManager);
        doReturn(consentManager).when(ConsentManager::getInstance);
        Mockito.doReturn(true).when(mUxStatesManager).isEeaDevice();
        mockAdIdEnabled();
        doReturn(mAdservicesSyncUtil).when(AdServicesSyncUtil::getInstance);
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mConsentNotificationJobService)
                .jobFinished(mMockJobParameters, false);
        doNothing().when(mAdservicesSyncUtil).execute(any(Context.class), any(Boolean.class));
        mocker.mockGetConsentNotificationDebugMode(false);

        mConsentNotificationJobService.onStartJob(mMockJobParameters);
        jobFinishedCountDown.await();

        verify(mAdservicesSyncUtil).execute(any(Context.class), any(Boolean.class));
        verify(mConsentNotificationJobService).jobFinished(mMockJobParameters, false);
    }

    private void testOnStopJob() {
        // Verify nothing throws
        mConsentNotificationJobService.onStopJob(mMockJobParameters);
    }

    private void mockOtaStringsFeature(boolean enabled) {
        when(mMockFlags.getUiOtaStringsFeatureEnabled()).thenReturn(enabled);
    }

    private void mockOtaResourcesFeature(boolean enabled) {
        when(mMockFlags.getUiOtaResourcesFeatureEnabled()).thenReturn(enabled);
    }

    private void mockConsentDebugMode(boolean enabled) {
        mocker.mockGetConsentNotificationDebugMode(enabled);
    }

    private void mockJobFinished() throws Exception {
        mockServiceCompatUtilDisableJob(false);
        doReturn(mAdservicesSyncUtil).when(AdServicesSyncUtil::getInstance);
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mConsentNotificationJobService)
                .jobFinished(mMockJobParameters, false);

        mConsentNotificationJobService.onStartJob(mMockJobParameters);
        doNothing().when(mAdservicesSyncUtil).execute(any(Context.class), any(Boolean.class));
        jobFinishedCountDown.await();
    }

    private void mockAdIdEnabled() {
        when(mPersistableBundle.getBoolean(eq(ADID_ENABLE_STATUS), anyBoolean())).thenReturn(true);
        when(mMockJobParameters.getExtras()).thenReturn(mPersistableBundle);
    }

    private void mockEuDevice() {
        doReturn(mPackageManager).when(mConsentNotificationJobService).getPackageManager();
        Mockito.doReturn(true).when(mUxStatesManager).isEeaDevice();
    }

    private void mockServiceCompatUtilDisableJob(boolean returnValue) {
        doReturn(returnValue)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));
    }

    private void mockGaUxEnabled() {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
    }
}
