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

package com.android.adservices.service;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

import android.app.job.JobParameters;
import android.content.Context;

import com.android.adservices.service.topics.AppUpdateManager;
import com.android.adservices.service.topics.BlockedTopicsManager;
import com.android.adservices.service.topics.CacheManager;
import com.android.adservices.service.topics.EpochManager;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

/** Unit tests for {@link com.android.adservices.service.MaintenanceJobService} */
public class MaintenanceJobServiceTest {
    private static final int BACKGROUND_THREAD_TIMEOUT_MS = 5_000;

    private MaintenanceJobService mMaintenanceJobService;

    // Mock EpochManager and CacheManager as the methods called are tested in corresponding
    // unit test. In this test, only verify whether specific method is initiated.
    @Mock EpochManager mMockEpochManager;
    @Mock CacheManager mMockCacheManager;
    @Mock BlockedTopicsManager mBlockedTopicsManager;
    @Mock AppUpdateManager mMockAppUpdateManager;
    @Mock JobParameters mMockJobParameters;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mMaintenanceJobService = new MaintenanceJobService();
    }

    @Test
    public void testOnStartJob() throws InterruptedException {
        final TopicsWorker topicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mMockCacheManager,
                        mBlockedTopicsManager,
                        mMockAppUpdateManager,
                        FlagsFactory.getFlagsForTest());

        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession().spyStatic(TopicsWorker.class).startMocking();

        try {
            // Mock static method AppUpdateWorker.getInstance, let it return the local
            // appUpdateWorker in order to get a test instance.
            ExtendedMockito.doReturn(topicsWorker)
                    .when(() -> TopicsWorker.getInstance(any(Context.class)));

            mMaintenanceJobService.onStartJob(mMockJobParameters);

            // Grant some time to allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            ExtendedMockito.verify(() -> TopicsWorker.getInstance(any(Context.class)));
            verify(mMockAppUpdateManager).reconcileUninstalledApps(any(Context.class));
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testOnStopJob() {
        // Verify nothing throws
        mMaintenanceJobService.onStopJob(mMockJobParameters);
    }
}
