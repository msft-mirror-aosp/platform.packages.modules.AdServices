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

import android.content.Context;

import com.android.adservices.mockito.SharedMocker;
import com.android.adservices.mockito.SharedMockitoMocker;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;

import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

public abstract class SharedMockitoTestCase extends SharedUnitTestCase {

    @Mock protected Context mContext;
    @Mock protected Context mAppContext;

    @Rule(order = 10)
    public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    /** Provides common expectations. */
    public final Mocker mocker = new Mocker();

    public static final class Mocker implements SharedMocker {

        private final SharedMocker mSharedMocker = new SharedMockitoMocker();

        // SharedMocker methods

        @Override
        public Context setApplicationContextSingleton() {
            return mSharedMocker.setApplicationContextSingleton();
        }

        @Override
        public JobServiceLoggingCallback syncRecordOnStopJob(JobServiceLogger logger) {
            return mSharedMocker.syncRecordOnStopJob(logger);
        }
    }
}
