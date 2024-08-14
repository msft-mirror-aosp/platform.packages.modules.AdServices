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

import com.android.adservices.mockito.AdServicesJobMocker;
import com.android.adservices.mockito.AdServicesMockitoJobMocker;
import com.android.adservices.service.Flags;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.adservices.spe.AdServicesJobServiceLogger;

import org.junit.Test;

/** Base class for tests that exercise {@code JobService} implementations. */
public abstract class AdServicesJobServiceTestCase extends AdServicesExtendedMockitoTestCase {

    public final AdServicesJobMocker jobMocker = new AdServicesMockitoJobMocker(extendedMockito);

    // TODO(b/314969513): consider inlining after all classes are refactored
    /**
     * Convenience method to call {@link
     * AdServicesJobMocker#mockJobSchedulingLogger(AdServicesJobServiceFactory)} using {@link
     * #jobMocker}
     */
    protected final JobSchedulingLogger mockJobSchedulingLogger(
            AdServicesJobServiceFactory factory) {
        return jobMocker.mockJobSchedulingLogger(factory);
    }

    @Test
    public final void testAdServicesJobTestCaseFixtures() throws Exception {
        assertTestClassHasNoFieldsFromSuperclass(AdServicesJobServiceTestCase.class, "jobMocker");
    }

    // TODO(b/314969513): inline methods below once all classes are refactored (and use a common
    // mMockFlags instance)

    /**
     * Convenience helper to call {@code
     * AdServicesPragmaticMocker.mockGetBackgroundJobsLoggingKillSwitch()} using {@code mocker}.
     */
    protected final void mockBackgroundJobsLoggingKillSwitch(Flags flag, boolean value) {
        mocker.mockGetBackgroundJobsLoggingKillSwitch(flag, value);
    }

    /**
     * Convenience helper to call {@link
     * AdServicesJobMocker#mockGetAdServicesJobServiceLogger(AdServicesJobServiceLogger)} using
     * {@code jobMocker}.
     */
    protected final void mockGetAdServicesJobServiceLogger(AdServicesJobServiceLogger logger) {
        jobMocker.mockGetAdServicesJobServiceLogger(logger);
    }

    /**
     * Convenience helper to call {@link
     * AdServicesJobMocker#getSpiedAdServicesJobServiceLogger(Context, Flags)} using {@code
     * jobMocker}.
     */
    protected final AdServicesJobServiceLogger getSpiedAdServicesJobServiceLogger(
            Context context, Flags flags) {
        return jobMocker.getSpiedAdServicesJobServiceLogger(context, flags);
    }

    /**
     * Convenience helper to call {@link
     * AdServicesJobMocker#mockNoOpAdServicesJobServiceLogger(Context, Flags)} using {@code
     * jobMocker}.
     */
    protected final AdServicesJobServiceLogger mockAdServicesJobServiceLogger(
            Context context, Flags flags) {
        return jobMocker.mockNoOpAdServicesJobServiceLogger(context, flags);
    }
}
