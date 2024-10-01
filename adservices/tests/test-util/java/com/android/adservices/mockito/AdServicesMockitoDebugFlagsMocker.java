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

import com.android.adservices.service.DebugFlags;

import java.util.Objects;

/** {@link AdServicesDebugFlagsMocker} implementation that uses {@code Mockito}. */
public final class AdServicesMockitoDebugFlagsMocker extends AbstractMocker
        implements AdServicesDebugFlagsMocker {

    private final DebugFlags mDebugFlags;

    public AdServicesMockitoDebugFlagsMocker(DebugFlags debugFlags) {
        this.mDebugFlags = Objects.requireNonNull(debugFlags, "DebugFlags cannot be null");
    }

    @Override
    public void mockGetConsentManagerDebugMode(boolean value) {
        logV("mockGetConsentManagerDebugMode(%b)", value);
        when(mDebugFlags.getConsentManagerDebugMode()).thenReturn(value);
    }
}
