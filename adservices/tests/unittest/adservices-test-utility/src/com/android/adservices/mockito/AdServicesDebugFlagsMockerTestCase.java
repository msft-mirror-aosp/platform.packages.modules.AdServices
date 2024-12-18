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
import com.android.adservices.service.DebugFlags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

/**
 * Base class for all {@link AdServicesDebugFlagsMocker} implementations.
 *
 * @param <T> mocker implementation
 */
@SuppressWarnings("DirectInvocationOnMock")
public abstract class AdServicesDebugFlagsMockerTestCase<T extends AdServicesDebugFlagsMocker>
        extends AdServicesUnitTestCase {

    @Mock private DebugFlags mMockDebugFlags;

    @Rule public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    protected abstract T getMocker(DebugFlags debugFlags);

    private T getMocker() {
        return getMocker(mMockDebugFlags);
    }

    @Before
    public final void verifyMocker() {
        assertWithMessage("getMocker()").that(getMocker(mMockDebugFlags)).isNotNull();
    }

    @Test
    public final void testMockGetConsentManagerDebugMode() {
        getMocker().mockGetConsentManagerDebugMode(true);

        boolean result = mMockDebugFlags.getConsentManagerDebugMode();

        expect.withMessage("flags.mockGetConsentManagerDebugMode()").that(result).isTrue();
    }

    @Test
    public final void testMockGetConsentNotificationDebugMode() {
        getMocker().mockGetConsentNotificationDebugMode(true);

        boolean result = mMockDebugFlags.getConsentNotificationDebugMode();

        expect.withMessage("flags.getConsentNotificationDebugMode()").that(result).isTrue();
    }

    @Test
    public final void testMockGetDeveloperSessionFeatureEnabled() {
        getMocker().mockGetDeveloperSessionFeatureEnabled(true);

        boolean result = mMockDebugFlags.getDeveloperSessionFeatureEnabled();

        expect.withMessage("flags.mockGetDeveloperSessionFeatureEnabled()").that(result).isTrue();
    }
}
