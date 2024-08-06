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

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.Flags;

import org.junit.Test;
import org.mockito.Mock;

public final class AdServicesMockitoMockerTest extends AdServicesMockitoTestCase {

    private final AdServicesMockitoMocker mMocker = new AdServicesMockitoMocker();

    @Mock private Flags mMockFlags;

    @Test
    public void testMockGetBackgroundJobsLoggingKillSwitch_null() {
        assertThrows(
                NullPointerException.class,
                () -> mMocker.mockGetBackgroundJobsLoggingKillSwitch(null, true));
    }

    @Test
    public void testMockGetBackgroundJobsLoggingKillSwitch() {
        mMocker.mockGetBackgroundJobsLoggingKillSwitch(mMockFlags, true);

        boolean result = mMockFlags.getBackgroundJobsLoggingKillSwitch();

        expect.withMessage("flags.getBackgroundJobsLoggingKillSwitch()").that(result).isTrue();
    }

    @Test
    public void testMockGetCobaltLoggingEnabled_null() {
        assertThrows(
                NullPointerException.class, () -> mMocker.mockGetCobaltLoggingEnabled(null, true));
    }

    @Test
    public void testMockGetCobaltLoggingEnabled() {
        mMocker.mockGetCobaltLoggingEnabled(mMockFlags, true);

        boolean result = mMockFlags.getCobaltLoggingEnabled();

        expect.withMessage("flags.getCobaltLoggingEnabled()").that(result).isTrue();
    }

    @Test
    public void testMockGetAppNameApiErrorCobaltLoggingEnabled_null() {
        assertThrows(
                NullPointerException.class,
                () -> mMocker.mockGetAppNameApiErrorCobaltLoggingEnabled(null, true));
    }

    @Test
    public void testMockGetAppNameApiErrorCobaltLoggingEnabled() {
        mMocker.mockGetAppNameApiErrorCobaltLoggingEnabled(mMockFlags, true);

        boolean result = mMockFlags.getAppNameApiErrorCobaltLoggingEnabled();

        expect.withMessage("flags.getAppNameApiErrorCobaltLoggingEnabled()").that(result).isTrue();
    }

    @Test
    public void testMockGetAdservicesReleaseStageForCobalt_null() {
        assertThrows(
                NullPointerException.class,
                () -> mMocker.mockGetAdservicesReleaseStageForCobalt(null, "Central Stage"));
        assertThrows(
                NullPointerException.class,
                () -> mMocker.mockGetAdservicesReleaseStageForCobalt(mMockFlags, null));
    }

    @Test
    public void testMockGetAdservicesReleaseStageForCobalt() {
        mMocker.mockGetAdservicesReleaseStageForCobalt(mMockFlags, "Central Stage");

        String result = mMockFlags.getAdservicesReleaseStageForCobalt();

        expect.withMessage("flags.getAdservicesReleaseStageForCobalt()")
                .that(result)
                .isEqualTo("Central Stage");
    }

    @Test
    public void testMockAllCobaltLoggingFlags_null() {
        assertThrows(
                NullPointerException.class, () -> mMocker.mockAllCobaltLoggingFlags(null, true));
    }

    @Test
    public void testMockAllCobaltLoggingFlags() {
        mMocker.mockAllCobaltLoggingFlags(mMockFlags, true);

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
