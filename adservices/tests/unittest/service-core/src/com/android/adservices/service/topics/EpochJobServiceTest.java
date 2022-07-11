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

package com.android.adservices.service.topics;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

import android.app.job.JobParameters;
import android.content.Context;

import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link com.android.adservices.service.topics.EpochJobService} */
public class EpochJobServiceTest {
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 5_000;

    private EpochJobService mEpochJobService;

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

        mEpochJobService = new EpochJobService();
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
        // Add a countDownLatch to ensure background thread gets executed
        CountDownLatch countDownLatch = new CountDownLatch(1);

        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession().spyStatic(TopicsWorker.class).startMocking();

        try {
            // Mock static method TopicsWorker.getInstance, let it return the local topicsWorker
            // in order to get a test instance.
            ExtendedMockito.doReturn(topicsWorker)
                    .when(() -> TopicsWorker.getInstance(any(Context.class)));

            mEpochJobService.onStartJob(mMockJobParameters);

            // The countDownLatch doesn't get decreased and waits until timeout.
            assertThat(countDownLatch.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isFalse();

            // Check that processEpoch() and loadCache() are executed to justify
            // TopicsWorker.computeEpoch() is executed.
            ExtendedMockito.verify(() -> TopicsWorker.getInstance(any(Context.class)));
            verify(mMockEpochManager).processEpoch();
            verify(mMockCacheManager).loadCache();
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testOnStopJob() {
        // Verify nothing throws
        mEpochJobService.onStopJob(mMockJobParameters);
    }

    // TODO: Implement after the decision between WorkManager and JobScheduler is made.
    @Test
    public void testSchedule() {}
}
