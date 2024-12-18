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

import com.android.adservices.service.Flags;

/**
 * Helper interface providing expectations to set the most common AdService flags / features.
 *
 * <p>A "feature" could require setting a combo of flags - adding that logic here would make the
 * tests less verbose and easier to maintain (for example, when a flag is not needed by a feature
 * anymore, only the implementation of this interface would need to be changed, not individual
 * tests).
 */
public interface AdServicesFlagsMocker {

    // TODO(b/358120731): rename some methods below, like:
    // - mockGetBackgroundJobsLoggingKillSwitch -> mockGetBackgroundJobsLoggingFeature
    //  would need to negate previous
    // - mockAllCobaltLoggingFlags -> mockCobaltLoggingFeature
    // Or create a convention: the "mockGet" is "as-is", while a "mockFeature" is a combo

    // TODO(b/354932043): it might make more sense to move this one to AdServicesJobMocker instead
    /**
     * Mocks a call to {@link Flags#getBackgroundJobsLoggingKillSwitch()}, returning {@code value}.
     */
    void mockGetBackgroundJobsLoggingKillSwitch(boolean value);

    /** Mocks a call to {@link Flags#getCobaltLoggingEnabled()}, returning {@code value}. */
    void mockGetCobaltLoggingEnabled(boolean value);

    /** Mocks a call to {@link Flags#getAppNameApiErrorCobaltLoggingEnabled()}. */
    void mockGetAppNameApiErrorCobaltLoggingEnabled(boolean value);

    /** Mocks a call to {@link Flags#getCobaltEnableApiCallResponseLogging()}. */
    void mockGetEnableApiCallResponseLoggingEnabled(boolean value);

    /** Mocks calls to override Cobalt app name api error logging related flags. */
    void mockAllCobaltLoggingFlags(boolean enabled);

    /**
     * Mocks a call to {@link Flags#getAdservicesReleaseStageForCobalt()}, returning the proper code
     * for the testing release stage.
     */
    void mockGetAdservicesReleaseStageForCobalt(String stage);
}
