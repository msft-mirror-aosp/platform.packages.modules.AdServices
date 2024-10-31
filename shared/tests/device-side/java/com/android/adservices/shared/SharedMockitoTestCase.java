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
package com.android.adservices.shared;

import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.android.adservices.mockito.AndroidMocker;
import com.android.adservices.mockito.AndroidMockitoMocker;
import com.android.adservices.mockito.SharedMocker;
import com.android.adservices.mockito.SharedMockitoMocker;
import com.android.adservices.shared.common.flags.ModuleSharedFlags;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.adservices.shared.testing.CallSuper;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.util.Clock;

import org.junit.Rule;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

// NOTE: currently no subclass needs a custom mocker; once they do, this class should be split
// into a SharedMockerLessMockitoTestCase (similar to AdServiceExtendedMockitoTestCase /
// AdServicesMockerLessExtendedMockitoTestCase)
public abstract class SharedMockitoTestCase extends SharedUnitTestCase {

    protected final Context mMockContext = mock(Context.class);
    protected final ModuleSharedFlags mMockFlags = mock(ModuleSharedFlags.class);

    @Rule(order = 10)
    public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    /** Provides common expectations. */
    public final Mocker mocker = new Mocker();

    public static final class Mocker implements SharedMocker, AndroidMocker {

        private final SharedMocker mSharedMocker = new SharedMockitoMocker();
        private final AndroidMocker mAndroidMocker = new AndroidMockitoMocker();

        // SharedMocker methods

        @Override
        public Context setApplicationContextSingleton() {
            return mSharedMocker.setApplicationContextSingleton();
        }

        @Override
        public void mockSetApplicationContextSingleton(Context context) {
            mSharedMocker.mockSetApplicationContextSingleton(context);
        }

        @Override
        public JobServiceLoggingCallback syncRecordOnStopJob(JobServiceLogger logger) {
            return mSharedMocker.syncRecordOnStopJob(logger);
        }

        @Override
        public void mockCurrentTimeMillis(Clock mockClock, long... mockedValues) {
            mSharedMocker.mockCurrentTimeMillis(mockClock, mockedValues);
        }

        @Override
        public void mockElapsedRealtime(Clock mockClock, long... mockedValues) {
            mSharedMocker.mockElapsedRealtime(mockClock, mockedValues);
        }

        // AndroidMocker methods

        @Override
        public void mockQueryIntentService(PackageManager mockPm, ResolveInfo... resolveInfos) {
            mAndroidMocker.mockQueryIntentService(mockPm, resolveInfos);
        }

        @Override
        public void mockGetApplicationContext(Context mockContext, Context appContext) {
            mAndroidMocker.mockGetApplicationContext(mockContext, appContext);
        }
    }

    // TODO(b/361555631): rename to testShareMockitoTestCaseFixtures() and annotate it with
    // @MetaTest
    @CallSuper
    @Override
    protected void assertValidTestCaseFixtures() throws Exception {
        super.assertValidTestCaseFixtures();

        checkProhibitedMockitoFields(SharedMockitoTestCase.class, this);
    }
}
