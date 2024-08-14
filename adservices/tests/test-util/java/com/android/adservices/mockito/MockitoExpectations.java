/*
 * Copyright (C) 2023 The Android Open Source Project
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



import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.testing.concurrency.ResultSyncCallback;

/** Provides Mockito expectation for common calls. */
public final class MockitoExpectations {

    private static final AdServicesMockitoMocker sMocker = new AdServicesMockitoMocker();

    /**
     * Mocks a call to {@link AdServicesLogger#logApiCallStats(ApiCallStats)} and returns a callback
     * object that blocks until that call is made. This method allows to pass in a customized
     * timeout. @Deprecated use {@code mocker.mockLogApiCallStats()} instead
     */
    @Deprecated
    public static ResultSyncCallback<ApiCallStats> mockLogApiCallStats(
            AdServicesLogger adServicesLogger) {
        return sMocker.mockLogApiCallStats(adServicesLogger);
    }

    private MockitoExpectations() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
