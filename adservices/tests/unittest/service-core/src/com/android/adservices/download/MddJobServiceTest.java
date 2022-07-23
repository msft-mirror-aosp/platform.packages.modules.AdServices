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

package com.android.adservices.download;

import static com.android.adservices.download.MddTaskScheduler.KEY_MDD_TASK_TAG;

import static com.google.android.libraries.mobiledatadownload.TaskScheduler.WIFI_CHARGING_PERIODIC_TASK;
import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobParameters;
import android.content.Context;
import android.os.PersistableBundle;

import com.android.adservices.service.Flags;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link com.android.adservices.download.MddJobService} */
public class MddJobServiceTest {
    private static final int BACKGROUND_TASK_TIMEOUT_MS = 5_000;

    private MddJobService mMddJobService;

    @Mock JobParameters mMockJobParameters;

    @Mock MobileDataDownload mMockMdd;
    @Mock MobileDataDownloadFactory mMockMddFactory;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // MDD Task Tag.
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(KEY_MDD_TASK_TAG, WIFI_CHARGING_PERIODIC_TASK);
        when(mMockJobParameters.getExtras()).thenReturn(bundle);
        mMddJobService = new MddJobService();
    }

    @Test
    public void testOnStartJob() throws InterruptedException {
        // Add a countDownLatch to ensure background thread gets executed
        CountDownLatch countDownLatch = new CountDownLatch(1);

        // Start a mockitoSession to mock static method to return Mock MDD.
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(MobileDataDownloadFactory.class)
                        .startMocking();

        try {
            ExtendedMockito.doReturn(mMockMdd)
                    .when(
                            () ->
                                    MobileDataDownloadFactory.getMdd(
                                            any(Context.class), any(Flags.class)));

            mMddJobService.onStartJob(mMockJobParameters);

            // The countDownLatch doesn't get decreased and waits until timeout.
            assertThat(countDownLatch.await(BACKGROUND_TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isFalse();

            // Check that Mdd.handleTask is executed.
            ExtendedMockito.verify(
                    () -> MobileDataDownloadFactory.getMdd(any(Context.class), any(Flags.class)));
            verify(mMockMdd).handleTask(WIFI_CHARGING_PERIODIC_TASK);
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testOnStopJob() {
        // Verify nothing throws
        mMddJobService.onStopJob(mMockJobParameters);
    }

    // TODO: Implement after the decision between WorkManager and JobScheduler is made.
    @Test
    public void testSchedule() {}
}
