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

import static com.android.adservices.service.common.ConsentNotificationJobService.MILLISECONDS_IN_THE_DAY;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.app.job.JobParameters;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.util.Calendar;
import java.util.concurrent.CountDownLatch;

/** Unit test for {@link com.android.adservices.ui.notifications.ConsentNotificationJobService}. */
public class ConsentNotificationJobServiceTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    @Spy private final ConsentManager mConsentManager = ConsentManager.getInstance(CONTEXT);

    @Spy
    private final ConsentNotificationJobService mConsentNotificationJobService =
            new ConsentNotificationJobService();

    @Mock JobParameters mMockJobParameters;
    @Mock PackageManager mPackageManager;
    @Mock AdServicesSyncUtil mAdservicesSyncUtil;
    @Mock PersistableBundle mPersistableBundle;
    @Mock Flags mFlags;
    private MockitoSession mStaticMockSession = null;

    /** Initialize static spies. */
    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(ConsentManager.class)
                        .spyStatic(AdServicesSyncUtil.class)
                        .spyStatic(ConsentNotificationJobService.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
    }

    /** Clean up static spies. */
    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    /** Test successful onStart method execution when notification was not yet displayed. */
    @Test
    public void testOnStartJobAsyncUtilExecute() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mPackageManager).when(mConsentNotificationJobService).getPackageManager();
        doReturn(Boolean.FALSE)
                .when(mConsentManager)
                .wasNotificationDisplayed(any(PackageManager.class));
        doNothing().when(mConsentManager).recordNotificationDisplayed(any());
        mConsentNotificationJobService.setConsentManager(mConsentManager);
        doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any(Context.class)));
        doReturn(true).when(() -> ConsentNotificationJobService.isEuDevice(any(Context.class)));
        when(mMockJobParameters.getExtras()).thenReturn(mPersistableBundle);
        when(mPersistableBundle.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        doReturn(mAdservicesSyncUtil).when(() -> AdServicesSyncUtil.getInstance());
        doAnswer(unusedInvocation -> {
            jobFinishedCountDown.countDown();
            return null;
        }).when(mConsentNotificationJobService).jobFinished(mMockJobParameters, false);

        doNothing().when(mAdservicesSyncUtil).execute(any(Context.class), any(Boolean.class));
        doReturn(mFlags).when(() -> FlagsFactory.getFlags());
        when(mFlags.getConsentNotificationDebugMode()).thenReturn(false);

        mConsentNotificationJobService.onStartJob(mMockJobParameters);
        jobFinishedCountDown.await();

        verify(mConsentManager).wasNotificationDisplayed(any());
        verify(mAdservicesSyncUtil).execute(any(Context.class), any(Boolean.class));
        verify(mConsentNotificationJobService).jobFinished(mMockJobParameters, false);
    }

    /** Test successful onStop method execution. */
    @Test
    public void testOnStopJob() {
        // Verify nothing throws
        mConsentNotificationJobService.onStopJob(mMockJobParameters);
    }

    /**
     * Test calculation of initial delay and maximum deadline for task which is being scheduled
     * before approved interval.
     */
    @Test
    public void testDelaysWhenCalledBeforeIntervalBegins() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(0);

        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        long initialDelay = ConsentNotificationJobService.calculateInitialDelay(calendar);
        long deadline = ConsentNotificationJobService.calculateDeadline(calendar);

        assertThat(initialDelay)
                .isEqualTo(FlagsFactory.getFlagsForTest().getConsentNotificationIntervalBeginMs());
        assertThat(deadline)
                .isEqualTo(FlagsFactory.getFlagsForTest().getConsentNotificationIntervalEndMs());
    }

    /**
     * Test calculation of initial delay and maximum deadline for task which is being scheduled
     * within approved interval.
     */
    @Test
    public void testDelaysWhenCalledWithinTheInterval() {
        long expectedDelay = /* 100 seconds */ 100000;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(
                FlagsFactory.getFlagsForTest().getConsentNotificationIntervalBeginMs()
                        + expectedDelay);

        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        long initialDelay = ConsentNotificationJobService.calculateInitialDelay(calendar);
        long deadline = ConsentNotificationJobService.calculateDeadline(calendar);

        assertThat(initialDelay).isEqualTo(0L);
        assertThat(deadline)
                .isEqualTo(
                        FlagsFactory.getFlagsForTest().getConsentNotificationIntervalEndMs()
                                - (FlagsFactory.getFlagsForTest()
                                                .getConsentNotificationIntervalBeginMs()
                                        + expectedDelay));
    }

    /**
     * Test calculation of initial delay and maximum deadline for task which is being scheduled
     * after approved interval (the same day).
     */
    @Test
    public void testDelaysWhenCalledAfterTheInterval() {
        long delay = /* 100 seconds */ 100000;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(
                FlagsFactory.getFlagsForTest().getConsentNotificationIntervalEndMs() + delay);

        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        long initialDelay = ConsentNotificationJobService.calculateInitialDelay(calendar);
        long deadline = ConsentNotificationJobService.calculateDeadline(calendar);

        long midnight =
                MILLISECONDS_IN_THE_DAY
                        - (FlagsFactory.getFlagsForTest().getConsentNotificationIntervalEndMs()
                                + delay);

        assertThat(initialDelay)
                .isEqualTo(
                        midnight
                                + FlagsFactory.getFlagsForTest()
                                        .getConsentNotificationIntervalBeginMs());
        assertThat(deadline)
                .isEqualTo(
                        midnight
                                + FlagsFactory.getFlagsForTest()
                                        .getConsentNotificationIntervalEndMs());
    }

    @Test
    public void testDelaysWhenDebugModeOn() {
        doReturn(mFlags).when(() -> FlagsFactory.getFlags());
        when(mFlags.getConsentNotificationDebugMode()).thenReturn(true);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(0);
        long initialDelay = ConsentNotificationJobService.calculateInitialDelay(calendar);
        long deadline = ConsentNotificationJobService.calculateDeadline(calendar);
        assertThat(initialDelay).isEqualTo(0L);
        assertThat(deadline).isEqualTo(0L);
    }
}
