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

import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.testing.concurrency.ResultSyncCallback;

/**
 * Helper interface providing common expectations for "regular" (non static or final) methods on
 * AdServices APIs.
 *
 * <p><b>Note: </b>this interface should be called {@code AdServicesMocker} and be implemented by
 * both {@link AdServicesMockitoMocker} and the {@code Mocker} interfaces on {@link
 * com.android.adservices.common.AdServicesMockitoTestCase} and {@link
 * com.android.adservices.common.AdServicesExtendedMockitoTestCase}. But pragmatically speaking (and
 * hence the name :-), the majority of tests extend the latter, so the former doesn't need to
 * implement this interface. If eventually it does (need such expectations), then we could create a
 * new {@code AdServicesMocker} interface, which would be implemented by these 3 classes ({@link
 * AdServicesMockitoMocker} and the 2 {@code Mocker} implementations).
 */
public interface AdServicesPragmaticMocker {

    /**
     * Mocks a call to {@link AdServicesLogger#logApiCallStats(ApiCallStats)} and returns a callback
     * object that blocks until that call is made.
     */
    ResultSyncCallback<ApiCallStats> mockLogApiCallStats(AdServicesLogger adServicesLogger);

    /**
     * Mocks a call to {@link AdServicesLogger#logApiCallStats(ApiCallStats)} and returns a callback
     * object that blocks until that call is made.
     *
     * <p>This method allows to pass in a customizedtimeout.
     */
    ResultSyncCallback<ApiCallStats> mockLogApiCallStats(
            AdServicesLogger adServicesLogger, long timeoutMs);
}
