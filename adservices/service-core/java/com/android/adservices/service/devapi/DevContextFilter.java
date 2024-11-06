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
import android.os.Binder;
import android.provider.Settings;

import com.android.adservices.LogUtil;
import com.android.adservices.service.common.SdkRuntimeUtil;
import com.android.adservices.service.common.compat.BuildCompatUtils;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Locale;
import java.util.Objects;

/**
 * Creates a {@link DevContext} instance using the information related to the caller of the current
 * API.
 */
public class DevContextFilter {

    @VisibleForTesting
    static final String PACKAGE_NAME_FOR_DISABLED_DEVELOPER_MODE_TEMPLATE =
            "dev.context.for.app.with.uid_%d.when.developer_mode.is.off";

    @VisibleForTesting
    static final String PACKAGE_NAME_WHEN_LOOKUP_FAILED_TEMPLATE =
            "dev.context.for.unknown.app.with.uid_%d";

    private final ContentResolver mContentResolver;
    private final AppPackageNameRetriever mAppPackageNameRetriever;
    private final PackageManager mPackageManager;

    /**
     * Construct a DevContextFilter.
     *
     * @param contentResolver The system content resolver to use.
     * @param packageManager The system package manager to use.
     * @param appPackageNameRetriever An instance of a class to fetch app package names.
     */
    @VisibleForTesting
    public DevContextFilter(
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
     * Creates a {@link DevContext} for the current binder call. It is assumed to be called by APIs
     * after having collected the caller UID in the API thread..
     *
     * @return A dev context specifying if the developer options are enabled for this API call or a
     *     context with developer options disabled if there is any error retrieving info for the
     *     calling application.
     * @throws IllegalStateException if the current thread is not currently executing an incoming
     *     transaction.
     */
    public DevContext createDevContext() throws IllegalStateException {
        return createDevContextFromCallingUid(Binder.getCallingUidOrThrow());
    }

    /**
     * Creates a {@link DevContext} for a given Binder calling UID.
     *
     * @param callingUid The Binder calling UID.
     * @return A dev context specifying if the developer options are enabled for this API call or a
     *     context with developer options disabled if there is any error retrieving info for the
     *     calling application.
     */
    public DevContext createDevContextFromCallingUid(int callingUid) {
        return createDevContext(SdkRuntimeUtil.getCallingAppUid(callingUid));
    }

    /**
     * Creates a {@link DevContext} for a given app UID.
     *
     * @param callingAppUid The UID of the caller APP.
     * @return A dev context specifying if the developer options are enabled for this API call or a
     *     context with developer options disabled if there is any error retrieving info for the
     *     calling application.
     */
    @VisibleForTesting
    public DevContext createDevContext(int callingAppUid) {
        String callingAppPackage = null;
        // TODO(b/363472834): Propagate developer mode state from the DB.
        DevContext.Builder builder = DevContext.builder().setDevSessionActive(false);

        if (!isDeveloperMode()) {
            // Since developer mode is off, we don't want to look up the app name; OTOH, we need to
            // set a non-null package name otherwise tests could fail
            callingAppPackage =
                    String.format(
                            Locale.ENGLISH,
                            PACKAGE_NAME_FOR_DISABLED_DEVELOPER_MODE_TEMPLATE,
                            callingAppUid);
            LogUtil.v(
                    "createDevContext(%d): developer mode is disabled, creating DevContext as"
                            + " disabled and using package name %s",
                    callingAppUid, callingAppPackage);
            return builder.setCallingAppPackageName(callingAppPackage)
                    .setDeviceDevOptionsEnabled(false)
                    .build();
        }

        try {
            callingAppPackage = mAppPackageNameRetriever.getAppPackageNameForUid(callingAppUid);
        } catch (IllegalArgumentException e) {
            callingAppPackage =
                    String.format(
                            Locale.ENGLISH,
                            PACKAGE_NAME_WHEN_LOOKUP_FAILED_TEMPLATE,
                            callingAppUid);
            LogUtil.w(
                    e,
                    "Unable to retrieve the package name for UID %d - should NOT happen on"
                        + " production, just in unit tests. Creating a DevContext with developer"
                        + " options disabled and using package name %s.",
                    callingAppUid,
                    callingAppPackage);
            return builder.setCallingAppPackageName(callingAppPackage)
                    .setDeviceDevOptionsEnabled(false)
                    .build();
        }
        builder.setCallingAppPackageName(callingAppPackage);
        if (!isDebuggable(callingAppPackage)) {
            LogUtil.v(
                    "createDevContext(%d): app %s not debuggable, creating DevContext as disabled",
                    callingAppUid, callingAppPackage);
            builder.setDeviceDevOptionsEnabled(false);
        } else {
            LogUtil.v(
                    "createDevContext(%d): creating DevContext for calling app with package %s",
                    callingAppUid, callingAppPackage);
            builder.setDeviceDevOptionsEnabled(true);
        }
        return builder.build();
    }

    /**
     * Returns true if the callingAppPackage is debuggable and false if it is not or if {@code
     * callingAppPackage} is null.
     *
     * @param callingAppPackage the calling app package
     */
    @VisibleForTesting
    public boolean isDebuggable(String callingAppPackage) {
        if (Objects.isNull(callingAppPackage)) {
            return false;
        }
        try {
            ApplicationInfo applicationInfo =
                    PackageManagerCompatUtils.getApplicationInfo(
                            mPackageManager, callingAppPackage, 0);
            return (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.w(
                    "Unable to retrieve application info for app with ID %s and resolved package "
                            + "name '%s', considering not debuggable for safety.",
                    callingAppPackage, callingAppPackage);
            return false;
        }
    }

    /** Returns true if developer options are enabled. */
    @VisibleForTesting
    public boolean isDeveloperMode() {
        return BuildCompatUtils.isDebuggable()
                || Settings.Global.getInt(
                                mContentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
                        != 0;
    }
}
