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

import static com.android.adservices.cobalt.CobaltLoggerConstantUtils.METRIC_ID;
import static com.android.adservices.cobalt.CobaltLoggerConstantUtils.RANGE_LOWER_API_CODE;
import static com.android.adservices.cobalt.CobaltLoggerConstantUtils.RANGE_LOWER_ERROR_CODE;
import static com.android.adservices.cobalt.CobaltLoggerConstantUtils.RANGE_UPPER_API_CODE;
import static com.android.adservices.cobalt.CobaltLoggerConstantUtils.RANGE_UPPER_ERROR_CODE;
import static com.android.adservices.cobalt.CobaltLoggerConstantUtils.UNKNOWN_EVENT_CODE;

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
public final class AppNameApiErrorLogger {

    private static final AppNameApiErrorLogger sInstance = new AppNameApiErrorLogger();

    @Nullable private final CobaltLogger mCobaltLogger;

    /** Returns the singleton of the {@code AppNameApiErrorLogger}. */
    public static AppNameApiErrorLogger getInstance() {
        return sInstance;
    }

    @VisibleForTesting
    AppNameApiErrorLogger(CobaltLogger cobaltLogger) {
        this.mCobaltLogger = cobaltLogger;
    }

    @VisibleForTesting
    AppNameApiErrorLogger() {
        this(getDefaultCobaltLogger());
    }

    @Nullable
    private static CobaltLogger getDefaultCobaltLogger() {
        CobaltLogger logger = null;
        try {
            Flags flags = FlagsFactory.getFlags();
            if (flags.getAppNameApiErrorCobaltLoggingEnabled()) {
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

    /**
     * Log an api call event with app package name, api name and error code. This method only logs
     * the event if the api call returns error.
     *
     * @param appPackageName the app package name made the api call
     * @param apiCode the api name code in int
     * @param errorCode the error code in int
     */
    @SuppressWarnings("FutureReturnValueIgnored") // TODO(b/323263328): Remove @SuppressWarnings.
    public void logErrorOccurrence(String appPackageName, int apiCode, int errorCode) {
        if (!isEnabled()) {
            LogUtil.w("Skip logging because Cobalt logger is null");
            return;
        }

        if (errorCode == 0) {
            LogUtil.d(
                    "Skip logging success event for app package name: %s, api code %d.",
                    appPackageName, apiCode);
            return;
        }

        Objects.requireNonNull(appPackageName, "appPackageName cannot be null");
        int apiCodeEvent = getRangeValue(RANGE_LOWER_API_CODE, RANGE_UPPER_API_CODE, apiCode);
        int errorCodeEvent =
                getRangeValue(RANGE_LOWER_ERROR_CODE, RANGE_UPPER_ERROR_CODE, errorCode);

        mCobaltLogger.logString(
                METRIC_ID, appPackageName, ImmutableList.of(apiCodeEvent, errorCodeEvent));
    }

    @VisibleForTesting
    boolean isEnabled() {
        return mCobaltLogger != null;
    }

    /**
     * Returns the value if it is within the range, otherwise returns {@code UNKNOWN_EVENT_CODE}.
     */
    private static int getRangeValue(int lowerRange, int upperRange, int value) {
        return value <= upperRange && value >= lowerRange ? value : UNKNOWN_EVENT_CODE;
    }
}
