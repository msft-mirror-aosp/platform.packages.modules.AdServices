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
package com.android.adservices.mockito;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

// NOTE: ErrorProne complains that mock objects should not be called directly, but in this test
// they need to, as the test verifies that they would return what is set by the mock
// expectaction methods.
/**
 * Base class for all {@link AdServicesFlagsMocker} implementations.
 *
 * @param <T> mocker implementation
 */
@SuppressWarnings("DirectInvocationOnMock")
public abstract class AdServicesFlagsMockerTestCase<T extends AdServicesFlagsMocker>
        extends AdServicesUnitTestCase {

    @Mock private Flags mMockFlags;

    @Rule public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    protected abstract T getMocker(Flags flags);

    private T getMocker() {
        return getMocker(mMockFlags);
    }

    @Before
    public final void verifyMocker() {
        assertWithMessage("getMocker()").that(getMocker(mMockFlags)).isNotNull();
    }

    @Test
    public final void testMockGetBackgroundJobsLoggingKillSwitch() {
        getMocker().mockGetBackgroundJobsLoggingKillSwitch(true);

        boolean result = mMockFlags.getBackgroundJobsLoggingKillSwitch();

        expect.withMessage("flags.getBackgroundJobsLoggingKillSwitch()").that(result).isTrue();
    }

    @Test
    public final void testMockGetCobaltLoggingEnabled() {
        getMocker().mockGetCobaltLoggingEnabled(true);

        boolean result = mMockFlags.getCobaltLoggingEnabled();

        expect.withMessage("flags.getCobaltLoggingEnabled()").that(result).isTrue();
    }

    @Test
    public final void testMockGetAppNameApiErrorCobaltLoggingEnabled() {
        getMocker().mockGetAppNameApiErrorCobaltLoggingEnabled(true);

        boolean result = mMockFlags.getAppNameApiErrorCobaltLoggingEnabled();

        expect.withMessage("flags.getAppNameApiErrorCobaltLoggingEnabled()").that(result).isTrue();
    }

    @Test
    public final void testMockGetEnableApiCallResponseLoggingEnabled() {
        getMocker().mockGetEnableApiCallResponseLoggingEnabled(true);

        boolean result = mMockFlags.getCobaltEnableApiCallResponseLogging();

        expect.withMessage("flags.getCobaltEnableApiCallResponseLogging()").that(result).isTrue();
    }

    @Test
    public final void testMockGetAdservicesReleaseStageForCobalt() {
        getMocker().mockGetAdservicesReleaseStageForCobalt("Central Stage");

        String result = mMockFlags.getAdservicesReleaseStageForCobalt();

        expect.withMessage("flags.getAdservicesReleaseStageForCobalt()")
                .that(result)
                .isEqualTo("Central Stage");
    }

    @Test
    public final void testMockAllCobaltLoggingFlags() {
        getMocker().mockAllCobaltLoggingFlags(true);

        expect.withMessage("flags.getCobaltLoggingEnabled()")
                .that(mMockFlags.getCobaltLoggingEnabled())
                .isTrue();
        expect.withMessage("flags.getAppNameApiErrorCobaltLoggingEnabled()")
                .that(mMockFlags.getAppNameApiErrorCobaltLoggingEnabled())
                .isTrue();
        expect.withMessage("flags.getAdservicesReleaseStageForCobalt()")
                .that(mMockFlags.getAdservicesReleaseStageForCobalt())
                .isEqualTo("DEBUG");
    }
}
