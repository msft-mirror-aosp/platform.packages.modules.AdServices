/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.download;

import static com.android.adservices.download.MddJob.CELLULAR_CHARGING_PERIODIC_TASK;
import static com.android.adservices.download.MddJob.CHARGING_PERIODIC_TASK;
import static com.android.adservices.download.MddJob.KEY_MDD_TASK_TAG;
import static com.android.adservices.download.MddJob.MAINTENANCE_PERIODIC_TASK;
import static com.android.adservices.download.MddJob.MILLISECONDS_PER_SECOND;
import static com.android.adservices.download.MddJob.NetworkState.NETWORK_STATE_ANY;
import static com.android.adservices.download.MddJob.NetworkState.NETWORK_STATE_CONNECTED;
import static com.android.adservices.download.MddJob.NetworkState.NETWORK_STATE_UNMETERED;
import static com.android.adservices.download.MddJob.createJobSpec;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_MAINTENANCE_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.android.libraries.mobiledatadownload.TaskScheduler.WIFI_CHARGING_PERIODIC_TASK;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.job.JobScheduler;
import android.os.PersistableBundle;

import com.android.adservices.common.AdServicesJobTestCase;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdPackageDenyResolver;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.testing.FutureSyncCallback;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.Future;

/** Unit test for {@link com.android.adservices.download.MddJob} */
@SpyStatic(AdServicesJobScheduler.class)
@SpyStatic(AdServicesJobServiceFactory.class)
@SpyStatic(EncryptionDataDownloadManager.class)
@SpyStatic(EnrollmentDataDownloadManager.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(MddFlags.class)
@SpyStatic(MddJob.class)
@SpyStatic(MddJobService.class)
@SpyStatic(MobileDataDownloadFactory.class)
@SpyStatic(AdPackageDenyResolver.class)
public final class MddJobTest extends AdServicesJobTestCase {
    private static final com.google.android.libraries.mobiledatadownload.Flags sMddFlags =
            new com.google.android.libraries.mobiledatadownload.Flags() {};

    private final MddJob mMddJob = new MddJob();

    @Mock private AdServicesJobScheduler mMockAdServicesJobScheduler;
    @Mock private AdServicesJobServiceFactory mMockFactory;
    @Mock private MobileDataDownload mMockMobileDataDownload;
    @Mock private EnrollmentDataDownloadManager mMockEnrollmentDataDownloadManager;
    @Mock private EncryptionDataDownloadManager mMockEncryptionDataDownloadManager;
    @Mock private ExecutionRuntimeParameters mMockParams;
    @Mock private JobScheduler mMockJobScheduler;
    @Mock private MddFlags mMockMddFlags;

    @Mock private AdPackageDenyResolver mMockAdPackageDenyResolver;

    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);
        mockMddFlags();

        doReturn(mMockFactory).when(AdServicesJobServiceFactory::getInstance);

        // Bypass actual executions of MDD library.
        doReturn(mMockMobileDataDownload).when(() -> MobileDataDownloadFactory.getMdd(any()));
        doReturn(mMockEnrollmentDataDownloadManager)
                .when(EnrollmentDataDownloadManager::getInstance);
        doReturn(mMockEncryptionDataDownloadManager)
                .when(EncryptionDataDownloadManager::getInstance);
        doReturn(mMockAdPackageDenyResolver).when(AdPackageDenyResolver::getInstance);
        // Assign a MDD task tag for general cases.
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(KEY_MDD_TASK_TAG, WIFI_CHARGING_PERIODIC_TASK);
        when(mMockParams.getExtras()).thenReturn(bundle);

        // Mock AdServicesJobScheduler to not actually schedule the jobs.
        mocker.mockSpeJobScheduler(mMockAdServicesJobScheduler);
    }

    @Test
    public void testGetJobEnablementStatus_disabled() {
        when(mMockFlags.getMddBackgroundTaskKillSwitch()).thenReturn(true);

        assertWithMessage("testGetJobEnablementStatus disabling for MDD kill switch")
                .that(mMddJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_enabled() {
        when(mMockFlags.getMddBackgroundTaskKillSwitch()).thenReturn(false);

        assertWithMessage("testGetJobEnablementStatus enabling")
                .that(mMddJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_ENABLED);
    }

    @Test
    public void testGetExecutionFuture() throws Exception {
        FutureSyncCallback<Void> mddHandleTaskCallBack = mockMddHandleTask();
        FutureSyncCallback<Void> enrollmentCallBack = mockEnrollmentReadFromMdd();
        FutureSyncCallback<Void> encryptionCallBack = mockEncryptionReadFromMdd();
        FutureSyncCallback<Void> packageDenyCallBack = mockPackageDenyReadFromMdd();

        ListenableFuture<ExecutionResult> unusedFuture =
                mMddJob.getExecutionFuture(mContext, mMockParams);
        // Call it to suppress the linter.
        assertThat(unusedFuture.get()).isEqualTo(SUCCESS);

        mddHandleTaskCallBack.assertResultReceived();
        enrollmentCallBack.assertResultReceived();
        encryptionCallBack.assertResultReceived();
        packageDenyCallBack.assertResultReceived();
    }

    @Test
    public void testGetExecutionFuture_adDenyFlagIsFalse() throws Exception {
        FutureSyncCallback<Void> mddHandleTaskCallBack = mockMddHandleTask();
        FutureSyncCallback<Void> enrollmentCallBack = mockEnrollmentReadFromMdd();
        FutureSyncCallback<Void> encryptionCallBack = mockEncryptionReadFromMdd();
        when(mMockFlags.getEnablePackageDenyJobOnMddDownload()).thenReturn(false);

        ListenableFuture<ExecutionResult> unusedFuture =
                mMddJob.getExecutionFuture(mContext, mMockParams);
        // Call it to suppress the linter.
        assertThat(unusedFuture.get()).isEqualTo(SUCCESS);

        mddHandleTaskCallBack.assertResultReceived();
        enrollmentCallBack.assertResultReceived();
        encryptionCallBack.assertResultReceived();
        Future<AdPackageDenyResolver.PackageDenyMddProcessStatus> notCalled =
                verify(mMockAdPackageDenyResolver, never()).loadDenyDataFromMdd();
    }

    @Test
    public void testScheduleAllMddJobs_spe() {
        when(mMockFlags.getSpeOnPilotJobsEnabled()).thenReturn(true);

        MddJob.scheduleAllMddJobs();

        verify(mMockAdServicesJobScheduler)
                .schedule(
                        createJobSpec(
                                MAINTENANCE_PERIODIC_TASK,
                                sMddFlags.maintenanceGcmTaskPeriod() * MILLISECONDS_PER_SECOND,
                                NETWORK_STATE_ANY));
        verify(mMockAdServicesJobScheduler)
                .schedule(
                        createJobSpec(
                                CHARGING_PERIODIC_TASK,
                                sMddFlags.chargingGcmTaskPeriod() * MILLISECONDS_PER_SECOND,
                                NETWORK_STATE_ANY));
        verify(mMockAdServicesJobScheduler)
                .schedule(
                        createJobSpec(
                                CELLULAR_CHARGING_PERIODIC_TASK,
                                sMddFlags.cellularChargingGcmTaskPeriod() * MILLISECONDS_PER_SECOND,
                                NETWORK_STATE_CONNECTED));
        verify(mMockAdServicesJobScheduler)
                .schedule(
                        createJobSpec(
                                WIFI_CHARGING_PERIODIC_TASK,
                                sMddFlags.wifiChargingGcmTaskPeriod() * MILLISECONDS_PER_SECOND,
                                NETWORK_STATE_UNMETERED));
    }

    @Test
    public void testScheduleAllMddJobs_legacy() {
        int resultCode = SCHEDULING_RESULT_CODE_SUCCESSFUL;
        when(mMockFlags.getSpeOnPilotJobsEnabled()).thenReturn(false);
        doReturn(resultCode).when(() -> MddJobService.scheduleIfNeeded(/* forceSchedule= */ false));
        doNothing().when(() -> MddJob.logJobSchedulingLegacy(resultCode));

        MddJob.scheduleAllMddJobs();

        verify(() -> MddJobService.scheduleIfNeeded(/* forceSchedule= */ false));
        verify(() -> MddJob.logJobSchedulingLegacy(resultCode));
        verifyZeroInteractions(mMockAdServicesJobScheduler);
    }

    @Test
    public void testUnscheduleAllMddJobs() {
        MddJob.unscheduleAllJobs(mMockJobScheduler);

        verify(mMockJobScheduler).cancel(MDD_MAINTENANCE_PERIODIC_TASK_JOB.getJobId());
        verify(mMockJobScheduler).cancel(MDD_CHARGING_PERIODIC_TASK_JOB.getJobId());
        verify(mMockJobScheduler).cancel(MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB.getJobId());
        verify(mMockJobScheduler).cancel(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB.getJobId());
    }

    private void mockMddFlags() {
        doReturn(mMockMddFlags).when(MddFlags::getInstance);
        when(mMockMddFlags.maintenanceGcmTaskPeriod())
                .thenReturn(sMddFlags.maintenanceGcmTaskPeriod());
        when(mMockMddFlags.chargingGcmTaskPeriod()).thenReturn(sMddFlags.chargingGcmTaskPeriod());
        when(mMockMddFlags.cellularChargingGcmTaskPeriod())
                .thenReturn(sMddFlags.cellularChargingGcmTaskPeriod());
        when(mMockMddFlags.wifiChargingGcmTaskPeriod())
                .thenReturn(sMddFlags.wifiChargingGcmTaskPeriod());
    }

    @Test
    public void testLogJobSchedulingLegacy() {
        JobSchedulingLogger logger = mock(JobSchedulingLogger.class);
        doReturn(logger).when(mMockFactory).getJobSchedulingLogger();
        int resultCode = SCHEDULING_RESULT_CODE_SUCCESSFUL;

        MddJob.logJobSchedulingLegacy(resultCode);

        verify(logger)
                .recordOnSchedulingLegacy(MDD_MAINTENANCE_PERIODIC_TASK_JOB.getJobId(), resultCode);
        verify(logger)
                .recordOnSchedulingLegacy(MDD_CHARGING_PERIODIC_TASK_JOB.getJobId(), resultCode);
        verify(logger)
                .recordOnSchedulingLegacy(
                        MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB.getJobId(), resultCode);
        verify(logger)
                .recordOnSchedulingLegacy(
                        MDD_WIFI_CHARGING_PERIODIC_TASK_JOB.getJobId(), resultCode);
    }

    private FutureSyncCallback<Void> mockMddHandleTask() {
        FutureSyncCallback<Void> futureCallback = new FutureSyncCallback<>();

        doAnswer(
                        invocation -> {
                            futureCallback.onSuccess(null);
                            return Futures.immediateVoidFuture();
                        })
                .when(mMockMobileDataDownload)
                .handleTask(anyString());

        return futureCallback;
    }

    private FutureSyncCallback<Void> mockEnrollmentReadFromMdd() {
        FutureSyncCallback<Void> futureCallback = new FutureSyncCallback<>();

        doAnswer(
                        invocation -> {
                            futureCallback.onSuccess(null);
                            return Futures.immediateFuture(
                                    EnrollmentDataDownloadManager.DownloadStatus.SUCCESS);
                        })
                .when(mMockEnrollmentDataDownloadManager)
                .readAndInsertEnrollmentDataFromMdd();

        return futureCallback;
    }

    private FutureSyncCallback<Void> mockEncryptionReadFromMdd() {
        FutureSyncCallback<Void> futureCallback = new FutureSyncCallback<>();

        doAnswer(
                        invocation -> {
                            futureCallback.onSuccess(null);
                            return Futures.immediateFuture(
                                    EncryptionDataDownloadManager.DownloadStatus.SUCCESS);
                        })
                .when(mMockEncryptionDataDownloadManager)
                .readAndInsertEncryptionDataFromMdd();

        return futureCallback;
    }

    private FutureSyncCallback<Void> mockPackageDenyReadFromMdd() {
        when(mMockFlags.getEnablePackageDenyJobOnMddDownload()).thenReturn(true);
        FutureSyncCallback<Void> futureCallback = new FutureSyncCallback<>();
        doAnswer(
                        invocation -> {
                            futureCallback.onSuccess(null);
                            return Futures.immediateFuture(
                                    AdPackageDenyResolver.PackageDenyMddProcessStatus.SUCCESS);
                        })
                .when(mMockAdPackageDenyResolver)
                .loadDenyDataFromMdd();

        return futureCallback;
    }
}
