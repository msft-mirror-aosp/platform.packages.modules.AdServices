/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.devapi;

import android.annotation.NonNull;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

import com.android.adservices.LogUtil;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Creates a {@link DevContext} instance using the information related to the caller of the current
 * API.
 */
public class DevContextFilter {
    private final ContentResolver mContentResolver;
    private final AppPackageNameRetriever mAppPackageNameRetriever;
    private final PackageManager mPackageManager;

    @VisibleForTesting
    DevContextFilter(
            @NonNull ContentResolver contentResolver,
            @NonNull PackageManager packageManager,
            @NonNull AppPackageNameRetriever appPackageNameRetriever) {
        Objects.requireNonNull(contentResolver);
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(appPackageNameRetriever);

        mAppPackageNameRetriever = appPackageNameRetriever;
        mContentResolver = contentResolver;
        mPackageManager = packageManager;
    }

    /** Creates an instance of {@link DevContextFilter}. */
    public static DevContextFilter create(@NonNull Context context) {
        Objects.requireNonNull(context);

        return new DevContextFilter(
                context.getContentResolver(),
                context.getPackageManager(),
                AppPackageNameRetriever.create(context));
    }

    /**
     * Creates a {@link DevContext} for a given app UID. It is assumed to be called by APIs after
     * having collected the caller UID in the API thread using {@code Binder.getCallingUid()}.
     *
     * @param callingUid The UID of the caller APP.
     * @return A dev context specifying if the developer options are enabled for this API call or a
     *     context with developer options disabled if there is any error retrieving info for the
     *     calling application.
     */
    public DevContext createDevContext(int callingUid) {
        if (!isDeveloperMode()) {
            return DevContext.createForDevOptionsDisabled();
        }

        try {
            String callingAppPackage = mAppPackageNameRetriever.getAppPackageNameForUid(callingUid);
            if (!isDebuggable(callingAppPackage)) {
                return DevContext.createForDevOptionsDisabled();
            }

            return DevContext.builder()
                    .setDevOptionsEnabled(true)
                    .setCallingAppPackageName(callingAppPackage)
                    .build();
        } catch (IllegalArgumentException e) {
            LogUtil.w(
                    "Unable to retrieve the package name for UID %d. Creating a DevContext with "
                            + "developer options disabled.",
                    callingUid);
            return DevContext.createForDevOptionsDisabled();
        }
    }

    private boolean isDebuggable(String callingAppPackage) {
        try {
            ApplicationInfo applicationInfo =
                    mPackageManager.getApplicationInfo(
                            callingAppPackage, PackageManager.ApplicationInfoFlags.of(0));

            return (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.w(
                    "Unable to retrieve application info for app with ID %d and resolved package "
                            + "name '%s', considering not debuggable for safety.",
                    callingAppPackage, callingAppPackage);
            return false;
        }
    }

    private boolean isDeveloperMode() {
        return Settings.Global.getInt(
                        mContentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
                != 0;
    }
}
