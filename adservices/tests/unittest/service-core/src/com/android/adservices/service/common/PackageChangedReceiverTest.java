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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.topics.AppUpdateManager;
import com.android.adservices.service.topics.BlockedTopicsManager;
import com.android.adservices.service.topics.CacheManager;
import com.android.adservices.service.topics.EpochManager;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

/** Unit test for {@link com.android.adservices.service.common.PackageChangedReceiver}. */
@SmallTest
public class PackageChangedReceiverTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String SAMPLE_PACKAGE = "com.example.measurement.sampleapp";
    private static final String PACKAGE_SCHEME = "package:";
    private static final int BACKGROUND_THREAD_TIMEOUT_MS = 5_000;

    @Mock PackageChangedReceiver mMockPackageChangedReceiver;
    @Mock EpochManager mMockEpochManager;
    @Mock CacheManager mMockCacheManager;
    @Mock BlockedTopicsManager mBlockedTopicsManager;
    @Mock AppUpdateManager mMockAppUpdateManager;

    private TopicsWorker mTopicsWorker;
    private MockitoSession mTopicsWorkerMockedSession;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        doNothing()
                .when(mMockPackageChangedReceiver)
                .onPackageFullyRemoved(any(Context.class), any(Uri.class));
        doNothing()
                .when(mMockPackageChangedReceiver)
                .onPackageAdded(any(Context.class), any(Uri.class));
        doCallRealMethod()
                .when(mMockPackageChangedReceiver)
                .onReceive(any(Context.class), any(Intent.class));

        // Mock TopicsWorker to test app update flow in topics API.
        // Start a mockitoSession to mock static method
        mTopicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mMockCacheManager,
                        mBlockedTopicsManager,
                        mMockAppUpdateManager,
                        FlagsFactory.getFlagsForTest());
        mTopicsWorkerMockedSession =
                ExtendedMockito.mockitoSession().spyStatic(TopicsWorker.class).startMocking();
    }

    @After
    public void teardown() {
        mTopicsWorkerMockedSession.finishMocking();
    }

    @Test
    public void testReceivePackageFullyRemoved() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(
                PackageChangedReceiver.ACTION_KEY, PackageChangedReceiver.PACKAGE_FULLY_REMOVED);

        // Stubbing TopicsWorker.getInstance() to return mocked TopicsWorker instance
        ExtendedMockito.doReturn(mTopicsWorker).when(() -> TopicsWorker.getInstance(eq(sContext)));

        mMockPackageChangedReceiver.onReceive(sContext, intent);

        // Grant some time to allow background thread to execute
        Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

        verify(mMockPackageChangedReceiver, times(1))
                .onPackageFullyRemoved(any(Context.class), any(Uri.class));
        verify(mMockPackageChangedReceiver, never())
                .onPackageAdded(any(Context.class), any(Uri.class));

        // Verify method in AppUpdateManager is invoked
        // Note that only package name is passed into following methods.
        verify(mMockAppUpdateManager).deleteAppDataByUri(eq(Uri.parse(SAMPLE_PACKAGE)));
    }

    @Test
    public void testReceivePackageAdded() {
        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(PackageChangedReceiver.ACTION_KEY, PackageChangedReceiver.PACKAGE_ADDED);

        mMockPackageChangedReceiver.onReceive(sContext, intent);

        verify(mMockPackageChangedReceiver, times(1))
                .onPackageAdded(any(Context.class), any(Uri.class));
        verify(mMockPackageChangedReceiver, never())
                .onPackageFullyRemoved(any(Context.class), any(Uri.class));
    }
}
