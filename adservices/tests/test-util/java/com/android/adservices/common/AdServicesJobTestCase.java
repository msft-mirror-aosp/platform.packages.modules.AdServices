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
package com.android.adservices.common;

import android.content.Context;

import com.android.adservices.common.AdServicesJobTestCase.Mocker;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.mockito.AdServicesJobMocker;
import com.android.adservices.mockito.AdServicesMockitoJobMocker;
import com.android.adservices.mockito.StaticClassChecker;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.adservices.spe.AdServicesJobServiceLogger;

import com.google.common.annotations.VisibleForTesting;

/** Base class for tests that exercise custom {@code Jobs}. */
public abstract class AdServicesJobTestCase
        extends AdServicesMockerLessExtendedMockitoTestCase<Mocker> {

    @Override
    protected final Mocker newMocker(
            AdServicesExtendedMockitoRule rule, Flags mockFlags, DebugFlags mockDebugFlags) {
        return new Mocker(rule, mockFlags, mockDebugFlags);
    }

    public static final class Mocker
            extends AdServicesMockerLessExtendedMockitoTestCase.InternalMocker
            implements AdServicesJobMocker {
        private final AdServicesJobMocker mJobMocker;

        @VisibleForTesting
        Mocker(StaticClassChecker checker, Flags mockFlags, DebugFlags mockDebugFlags) {
            super(checker, mockFlags, mockDebugFlags);
            mJobMocker = new AdServicesMockitoJobMocker(checker);
        }

        @Override
        public JobSchedulingLogger mockJobSchedulingLogger(AdServicesJobServiceFactory factory) {
            return mJobMocker.mockJobSchedulingLogger(factory);
        }

        @Override
        public AdServicesJobServiceLogger getSpiedAdServicesJobServiceLogger(
                Context context, Flags flags) {
            return mJobMocker.getSpiedAdServicesJobServiceLogger(context, flags);
        }

        @Override
        public void mockGetAdServicesJobServiceLogger(AdServicesJobServiceLogger logger) {
            mJobMocker.mockGetAdServicesJobServiceLogger(logger);
        }

        @Override
        public AdServicesJobServiceLogger mockNoOpAdServicesJobServiceLogger(
                Context context, Flags flags) {
            return mJobMocker.mockNoOpAdServicesJobServiceLogger(context, flags);
        }
    }
}
