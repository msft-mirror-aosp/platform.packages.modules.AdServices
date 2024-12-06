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

import static org.mockito.Mockito.when;

import com.android.adservices.service.Flags;

import java.util.Objects;

/** {@link AdServicesFlagsMocker} implementation that uses {@code Mockito}. */
public final class AdServicesMockitoFlagsMocker extends AbstractMocker
        implements AdServicesFlagsMocker {

    private final Flags mFlags;

    public AdServicesMockitoFlagsMocker(Flags flags) {
        this.mFlags = Objects.requireNonNull(flags, "flags cannot be null");
    }

    @Override
    public void mockGetBackgroundJobsLoggingKillSwitch(boolean value) {
        logV("mockBackgroundJobsLoggingKillSwitch(%b)", value);
        when(mFlags.getBackgroundJobsLoggingKillSwitch()).thenReturn(value);
    }

    @Override
    public void mockGetCobaltLoggingEnabled(boolean value) {
        logV("mockGetCobaltLoggingEnabled(%b)", value);
        when(mFlags.getCobaltLoggingEnabled()).thenReturn(value);
    }

    @Override
    public void mockGetAppNameApiErrorCobaltLoggingEnabled(boolean value) {
        logV("mockGetAppNameApiErrorCobaltLoggingEnabled(%b)", value);
        when(mFlags.getAppNameApiErrorCobaltLoggingEnabled()).thenReturn(value);
    }

    @Override
    public void mockGetEnableApiCallResponseLoggingEnabled(boolean value) {
        logV("mockGetEnableApiCallResponseLoggingEnabled(%b)", value);
        when(mFlags.getCobaltEnableApiCallResponseLogging()).thenReturn(value);
    }

    @Override
    public void mockGetAdservicesReleaseStageForCobalt(String stage) {
        logV("mockGetAdservicesReleaseStageForCobalt(%s)", stage);
        Objects.requireNonNull(stage, "Stage cannot be null");
        when(mFlags.getAdservicesReleaseStageForCobalt()).thenReturn(stage);
    }

    @Override
    public void mockAllCobaltLoggingFlags(boolean enabled) {
        logV("mockAllCobaltLoggingFlags(%b)", enabled);
        mockGetCobaltLoggingEnabled(enabled);
        mockGetAppNameApiErrorCobaltLoggingEnabled(enabled);
        mockGetEnableApiCallResponseLoggingEnabled(enabled);
        mockGetAdservicesReleaseStageForCobalt("DEBUG");
    }
}
