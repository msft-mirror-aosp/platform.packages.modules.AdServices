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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;

import android.content.Context;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.spe.AdServicesJobServiceLogger;

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
 * Base class for all {@link AdServicesPragmaticMocker} implementations.
 *
 * @param <T> mocker implementation
 */
@SuppressWarnings("DirectInvocationOnMock")
public abstract class AdServicesPragmaticMockerTestCase<T extends AdServicesPragmaticMocker>
        extends AdServicesUnitTestCase {

    @Mock private Context mMockContext;
    @Mock private Flags mMockFlags;

    @Rule public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    protected abstract T getMocker();

    @Before
    public final void verifyMocker() {
        assertWithMessage("getMocker()").that(getMocker()).isNotNull();
    }

    @Test
    public final void testMockGetBackgroundJobsLoggingKillSwitch_null() {
        assertThrows(
                NullPointerException.class,
                () -> getMocker().mockGetBackgroundJobsLoggingKillSwitch(null, true));
    }

    @Test
    public final void testMockGetBackgroundJobsLoggingKillSwitch() {
        getMocker().mockGetBackgroundJobsLoggingKillSwitch(mMockFlags, true);

        boolean result = mMockFlags.getBackgroundJobsLoggingKillSwitch();

        expect.withMessage("flags.getBackgroundJobsLoggingKillSwitch()").that(result).isTrue();
    }

    @Test
    public final void testMockGetCobaltLoggingEnabled_null() {
        assertThrows(
                NullPointerException.class,
                () -> getMocker().mockGetCobaltLoggingEnabled(null, true));
    }

    @Test
    public final void testMockGetCobaltLoggingEnabled() {
        getMocker().mockGetCobaltLoggingEnabled(mMockFlags, true);

        boolean result = mMockFlags.getCobaltLoggingEnabled();

        expect.withMessage("flags.getCobaltLoggingEnabled()").that(result).isTrue();
    }

    @Test
    public final void testMockGetAppNameApiErrorCobaltLoggingEnabled_null() {
        assertThrows(
                NullPointerException.class,
                () -> getMocker().mockGetAppNameApiErrorCobaltLoggingEnabled(null, true));
    }

    @Test
    public final void testMockGetAppNameApiErrorCobaltLoggingEnabled() {
        getMocker().mockGetAppNameApiErrorCobaltLoggingEnabled(mMockFlags, true);

        boolean result = mMockFlags.getAppNameApiErrorCobaltLoggingEnabled();

        expect.withMessage("flags.getAppNameApiErrorCobaltLoggingEnabled()").that(result).isTrue();
    }

    @Test
    public final void testMockGetAdservicesReleaseStageForCobalt_null() {
        assertThrows(
                NullPointerException.class,
                () -> getMocker().mockGetAdservicesReleaseStageForCobalt(null, "Central Stage"));
        assertThrows(
                NullPointerException.class,
                () -> getMocker().mockGetAdservicesReleaseStageForCobalt(mMockFlags, null));
    }

    @Test
    public final void testMockGetAdservicesReleaseStageForCobalt() {
        getMocker().mockGetAdservicesReleaseStageForCobalt(mMockFlags, "Central Stage");

        String result = mMockFlags.getAdservicesReleaseStageForCobalt();

        expect.withMessage("flags.getAdservicesReleaseStageForCobalt()")
                .that(result)
                .isEqualTo("Central Stage");
    }

    @Test
    public final void testMockAllCobaltLoggingFlags_null() {
        assertThrows(
                NullPointerException.class,
                () -> getMocker().mockAllCobaltLoggingFlags(null, true));
    }

    @Test
    public final void testMockAllCobaltLoggingFlags() {
        getMocker().mockAllCobaltLoggingFlags(mMockFlags, true);

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

    @Test
    public final void testGetSpiedAdServicesJobServiceLogger_null() {
        T mocker = getMocker();
        assertThrows(
                NullPointerException.class,
                () -> mocker.getSpiedAdServicesJobServiceLogger(/* context= */ null, mMockFlags));
        assertThrows(
                NullPointerException.class,
                () -> mocker.getSpiedAdServicesJobServiceLogger(mMockContext, /* flags= */ null));
    }

    @Test
    public final void testGetSpiedAdServicesJobServiceLogger() {
        AdServicesJobServiceLogger spy =
                getMocker().getSpiedAdServicesJobServiceLogger(mMockContext, mMockFlags);
        expect.withMessage("getSpiedAdServicesJobServiceLogger()").that(spy).isNotNull();

        spy.recordOnStartJob(42);
        verify(spy).recordOnStartJob(42);
    }
}
