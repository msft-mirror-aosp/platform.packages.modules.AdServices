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

import android.annotation.CallSuper;

import com.android.adservices.mockito.AdServicesJobMocker;
import com.android.adservices.mockito.AdServicesMockitoJobMocker;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.spe.AdServicesJobServiceFactory;

/** Base class for tests that exercise custom {@code Jobs}. */
public abstract class AdServicesJobTestCase extends AdServicesExtendedMockitoTestCase {

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

    // TODO(b/361555631): rename to testAdServicesJobTestCaseFixtures() and annotate
    // it with @MetaTest
    @CallSuper
    @Override
    protected void assertValidTestCaseFixtures() throws Exception {
        super.assertValidTestCaseFixtures();

        assertTestClassHasNoFieldsFromSuperclass(AdServicesJobTestCase.class, "jobMocker");
    }
}
