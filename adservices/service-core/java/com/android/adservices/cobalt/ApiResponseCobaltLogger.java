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

package com.android.adservices.cobalt;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import android.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.cobalt.CobaltLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.Objects;

/**
 * Wrapper around {@link CobaltLogger} that logs {@code ApiNameCode, StatusCode} and app package
 * name occurrences to Cobalt.
 */
public final class ApiResponseCobaltLogger {

    // The per_package_api_response metric has an id of 6.
    //
    // See //packages/modules/AdServices/adservices/service-core/resources/cobalt_registry.textpb
    // for the full metric.
    private static final int API_RESPONSE_METRIC_ID = 6;

    private static final ApiResponseCobaltLogger sInstance = new ApiResponseCobaltLogger();
    @Nullable private final CobaltLogger mCobaltLogger;

    @VisibleForTesting
    ApiResponseCobaltLogger(CobaltLogger cobaltLogger) {
        this.mCobaltLogger = cobaltLogger;
    }

    @VisibleForTesting
    ApiResponseCobaltLogger() {
        this(getDefaultCobaltLogger());
    }

    @VisibleForTesting
    boolean isEnabled() {
        return mCobaltLogger != null;
    }

    /** Returns the singleton of the {@code ApiResponseCobaltLogger}. */
    public static ApiResponseCobaltLogger getInstance() {
        return sInstance;
    }

    /**
     * Logs an api call event with app package name, api name and response code.
     *
     * @param appPackageName the app package name made the api call
     * @param apiCode the api name code in int
     * @param responseCode the response code in int
     */
    @SuppressWarnings("FutureReturnValueIgnored") // TODO(b/323263328): Remove @SuppressWarnings.
    public void logResponse(String appPackageName, int apiCode, int responseCode) {
        if (!isEnabled()) {
            LogUtil.w("Skip logging because Cobalt is disabled.");
            return;
        }
        Objects.requireNonNull(appPackageName, "appPackageName cannot be null");

        // Per_package_api_response metric defines response event_code SUCCESS = 100.
        mCobaltLogger.logString(
                API_RESPONSE_METRIC_ID,
                appPackageName,
                ImmutableList.of(apiCode, responseCode == STATUS_SUCCESS ? 100 : responseCode));
    }

    @Nullable
    private static CobaltLogger getDefaultCobaltLogger() {
        CobaltLogger logger = null;
        try {
            Flags flags = FlagsFactory.getFlags();
            if (flags.getCobaltEnableApiCallResponseLogging()) {
                logger = CobaltFactory.getCobaltLogger(ApplicationContextSingleton.get(), flags);
            } else {
                LogUtil.d("Cobalt logger is disabled.");
            }
        } catch (CobaltInitializationException | RuntimeException e) {
            LogUtil.e(e, "Cobalt logger initialization failed.");
            // TODO(b/323253975): Add CEL.
        }
        return logger;
    }
}
