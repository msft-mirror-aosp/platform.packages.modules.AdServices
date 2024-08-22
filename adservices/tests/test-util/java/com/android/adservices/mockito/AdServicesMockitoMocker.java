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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.testing.concurrency.ResultSyncCallback;
import com.android.adservices.shared.testing.concurrency.SyncCallbackFactory;

import java.util.Objects;

/** {@link AdServicesPragmaticMocker} implementation that uses {@code Mockito}. */
public final class AdServicesMockitoMocker extends AbstractMocker
        implements AdServicesPragmaticMocker {

    @Override
    public ResultSyncCallback<ApiCallStats> mockLogApiCallStats(AdServicesLogger adServicesLogger) {
        ResultSyncCallback<ApiCallStats> callback = new ResultSyncCallback<>();
        logV("mockLogApiCallStats(%s): will return %s", adServicesLogger, callback);
        mockLogApiCallStats(callback, adServicesLogger);
        return callback;
    }

    @Override
    public ResultSyncCallback<ApiCallStats> mockLogApiCallStats(
            AdServicesLogger adServicesLogger, long timeoutMs) {
        ResultSyncCallback<ApiCallStats> callback =
                new ResultSyncCallback<>(
                        SyncCallbackFactory.newSettingsBuilder()
                                .setMaxTimeoutMs(timeoutMs)
                                .build());
        mockLogApiCallStats(callback, adServicesLogger);
        logV("mockLogApiCallStats(%s, %d): will return %s", adServicesLogger, timeoutMs, callback);
        return callback;
    }

    private void mockLogApiCallStats(
            ResultSyncCallback<ApiCallStats> callback, AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(adServicesLogger, "adServicesLogger cannot be null");
        doAnswer(
                        inv -> {
                            logV("mockLogApiCallStats(): inv=%s", inv);
                            ApiCallStats apiCallStats = inv.getArgument(0);
                            callback.injectResult(apiCallStats);
                            return null;
                        })
                .when(adServicesLogger)
                .logApiCallStats(any());
    }
}
