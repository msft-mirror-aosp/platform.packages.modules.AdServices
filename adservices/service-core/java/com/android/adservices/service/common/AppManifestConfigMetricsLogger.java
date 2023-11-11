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
package com.android.adservices.service.common;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/** Class used to log metrics of how apps are using the app config on manifest. */
@VisibleForTesting // TODO(b/310270746): remove public when TopicsServiceImplTest is refactored
public final class AppManifestConfigMetricsLogger {

    private static final String PREFS_NAME = "AppManifestConfigMetricsLogger";

    private static final int NOT_SET = -1;
    private static final int FLAG_APP_EXISTS = 0x1;
    private static final int FLAG_APP_HAS_CONFIG = 0x2;
    private static final int FLAG_ENABLED_BY_DEFAULT = 0x4;

    /** Logs the app usage. */
    @VisibleForTesting // TODO(b/310270746): remove public when TopicsServiceImplTest is refactored
    public static void logUsage(
            Context context,
            String packageName,
            boolean appExists,
            boolean appHasConfig,
            boolean enabledByDefault) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(packageName, "packageName cannot be null");

        AdServicesExecutors.getBackgroundExecutor()
                .execute(
                        () ->
                                handleLogUsage(
                                        context,
                                        packageName,
                                        appExists,
                                        appHasConfig,
                                        enabledByDefault));
    }

    private static void handleLogUsage(
            Context context,
            String packageName,
            boolean appExists,
            boolean appHasConfig,
            boolean enabledByDefault) {
        String name = FileCompatUtils.getAdservicesFilename(PREFS_NAME);
        try {
            int newValue =
                    (appExists ? FLAG_APP_EXISTS : 0)
                            | (appHasConfig ? FLAG_APP_HAS_CONFIG : 0)
                            | (enabledByDefault ? FLAG_ENABLED_BY_DEFAULT : 0);
            LogUtil.d(
                    "AppManifestConfigMetricsLogger.logUsage(): app=[name=%s, exists=%b,"
                            + " hasConfig=%b], enabledByDefault=%b, newValue=%d",
                    packageName, appExists, appHasConfig, enabledByDefault, newValue);

            @SuppressWarnings("NewAdServicesFile") // name already calls FileCompatUtils
            SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
            String key = packageName;

            int currentValue = prefs.getInt(key, NOT_SET);
            if (currentValue == NOT_SET) {
                LogUtil.v("Logging for the first time (value=%d)", newValue);
            } else if (currentValue != newValue) {
                LogUtil.v("Logging as value change (was %d)", currentValue);
            } else {
                LogUtil.v("Value didn't change, don't need to log");
                return;
            }

            // TODO(b/306417555): upload metrics first (and unit test it) - it should mask the
            // package name
            Editor editor = prefs.edit().putInt(key, newValue);

            if (editor.commit()) {
                LogUtil.v("Changes committed");
            } else {
                LogUtil.e(
                        "logUsage(ctx, file=%s, app=%s, appExist=%b, appHasConfig=%b,"
                                + " enabledByDefault=%b, newValue=%d): failed to commit",
                        name, packageName, appExists, appHasConfig, enabledByDefault, newValue);
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            }
        } catch (Exception e) {
            LogUtil.e(
                    e,
                    "logUsage(ctx, file=%s, app=%s, appExist=%b, appHasConfig=%b,"
                            + " enabledByDefault=%b) failed",
                    name,
                    packageName,
                    appExists,
                    appHasConfig,
                    enabledByDefault);
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_EXCEPTION,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        }
    }

    private AppManifestConfigMetricsLogger() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
