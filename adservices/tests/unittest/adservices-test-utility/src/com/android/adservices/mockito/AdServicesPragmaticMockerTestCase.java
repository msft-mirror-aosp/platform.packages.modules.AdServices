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

import static com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper.runAsync;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.testing.concurrency.ResultSyncCallback;

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

    @Mock private AdServicesLogger mMockAdServicesLogger;
    @Mock private ApiCallStats mApiCallStats;

    @Rule public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    protected abstract T getMocker();

    @Before
    public final void verifyMocker() {
        assertWithMessage("getMocker()").that(getMocker()).isNotNull();
    }

    @Test
    public final void testMockLogApiCallStats_null() {
        assertThrows(NullPointerException.class, () -> getMocker().mockLogApiCallStats(null));
        assertThrows(NullPointerException.class, () -> getMocker().mockLogApiCallStats(null, 42));
    }

    @Test
    public final void testMockLogApiCallStats_defaultTimeout() throws Exception {
        ResultSyncCallback<ApiCallStats> callback =
                getMocker().mockLogApiCallStats(mMockAdServicesLogger);
        assertWithMessage("mockLogApiCallStats()").that(callback).isNotNull();

        runAsync(/* delayMs= */ 10, () -> mMockAdServicesLogger.logApiCallStats(mApiCallStats));

        ApiCallStats stats = callback.assertResultReceived();
        assertWithMessage("callback result").that(stats).isSameInstanceAs(mApiCallStats);
    }

    @Test
    public final void testMockLogApiCallStats_customTimeout() throws Exception {
        ResultSyncCallback<ApiCallStats> callback =
                getMocker().mockLogApiCallStats(mMockAdServicesLogger, /* timeoutMs= */ 42);
        assertWithMessage("mockLogApiCallStats()").that(callback).isNotNull();

        runAsync(/* delayMs= */ 10, () -> mMockAdServicesLogger.logApiCallStats(mApiCallStats));

        ApiCallStats stats = callback.assertResultReceived();
        assertWithMessage("callback result").that(stats).isSameInstanceAs(mApiCallStats);
    }
}
