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

import static android.adservices.common.AdServicesStatusUtils.STATUS_DEV_SESSION_CALLER_IS_NON_DEBUGGABLE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_DEV_SESSION_FAILURE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_DEV_SESSION_IS_STILL_TRANSITIONING;

import android.adservices.common.AdServicesStatusUtils;
import android.annotation.NonNull;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.provider.Settings;

import com.android.adservices.LogUtil;
import com.android.adservices.LoggerFactory;
import com.android.adservices.service.common.SdkRuntimeUtil;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Creates a {@link DevContext} instance using the information related to the caller of the current
 * API.
 */
public class DevContextFilter {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    @VisibleForTesting
    static final String PACKAGE_NAME_FOR_DISABLED_DEVELOPER_MODE_TEMPLATE =
            "dev.context.for.app.with.uid_%d.when.developer_mode.is.off";

    @VisibleForTesting
    static final String PACKAGE_NAME_WHEN_LOOKUP_FAILED_TEMPLATE =
            "dev.context.for.unknown.app.with.uid_%d";

    private static final long DEV_SESSION_LOOKUP_SEC = 3;

    private final ContentResolver mContentResolver;
    private final AppPackageNameRetriever mAppPackageNameRetriever;
    private final PackageManager mPackageManager;
    private final DevSessionDataStore mDevSessionDataStore;

    /**
     * Construct a DevContextFilter.
     *
     * @param contentResolver The system content resolver to use.
     * @param packageManager The system package manager to use.
     * @param appPackageNameRetriever An instance of a class to fetch app package names.
     * @param devSessionDataStore An instance of the class to fetch dev session status.
     */
    public DevContextFilter(
            @NonNull ContentResolver contentResolver,
            @NonNull PackageManager packageManager,
            @NonNull AppPackageNameRetriever appPackageNameRetriever,
            @NonNull DevSessionDataStore devSessionDataStore) {
        Objects.requireNonNull(contentResolver);
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(appPackageNameRetriever);
        Objects.requireNonNull(devSessionDataStore);

        mAppPackageNameRetriever = appPackageNameRetriever;
        mContentResolver = contentResolver;
        mPackageManager = packageManager;
        mDevSessionDataStore = devSessionDataStore;
    }

    /**
     * Creates an instance of {@link DevContextFilter} for testing.
     *
     * @param context Application context.
     * @param developerModeFeatureEnabled If the developer mode feature is enabled.
     * @return A valid {@link DevContextFilter} instance.
     */
    @SuppressWarnings("AvoidStaticContext")
    public static DevContextFilter create(
            @NonNull Context context, final boolean developerModeFeatureEnabled) {
        // A separate constructor is needed for tests as the data store factory makes a flags check,
        // which will fail on R/S/T.
        Objects.requireNonNull(context);

        return new DevContextFilter(
                context.getContentResolver(),
                context.getPackageManager(),
                AppPackageNameRetriever.create(context),
                DevSessionDataStoreFactory.get(developerModeFeatureEnabled));
    }

    /**
     * Creates a {@link DevContext} for the current binder call. It is assumed to be called by APIs
     * after having collected the caller UID in the API thread.
     *
     * @return A dev context specifying if the developer options are enabled for this API call or a
     *     context with developer options disabled if there is any error retrieving info for the
     *     calling application.
     * @throws IllegalStateException if the current thread is not currently executing an incoming
     *     transaction.
     * @throws SecurityException If the calling app is non-debuggable during a dev session. In this
     *     state a {@link DevContext} is invalid and cannot be constructed.
     * @throws IllegalStateException If currently entering or exiting a dev session and PPAPIs are
     *     not available at this time.
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
     * @throws SecurityException If the calling app is non-debuggable during a dev session. In this
     *     state a {@link DevContext} is invalid and cannot be constructed.
     * @throws IllegalStateException If currently entering or exiting a dev session and PPAPIs are
     *     not available at this time.
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
     * @throws SecurityException If the calling app is non-debuggable during a dev session. In this
     *     state a {@link DevContext} is invalid and cannot be constructed.
     * @throws IllegalStateException If currently entering or exiting a dev session and PPAPIs are
     *     not available at this time.
     */
    @VisibleForTesting
    public DevContext createDevContext(int callingAppUid) {
        String callingAppPackage = null;
        boolean isDeviceDevOptionsEnabledOrDebuggable = isDeviceDevOptionsEnabledOrDebuggable();
        DevContext.Builder builder =
                DevContext.builder()
                        .setDevSession(getDevSession(isDeviceDevOptionsEnabledOrDebuggable));

        if (!isDeviceDevOptionsEnabledOrDebuggable) {
            // Since dev options are off, and device is non-debuggable we don't want to look up the
            // app name; OTOH, we need to set a non-null package name otherwise tests could fail.
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
        boolean isCallerDebuggable = isDebuggable(callingAppPackage);
        if (isCallerDebuggable) {
            LogUtil.v(
                    "createDevContext(%d): creating DevContext for calling app with package %s",
                    callingAppUid, callingAppPackage);
            builder.setDeviceDevOptionsEnabled(true);
        } else {
            LogUtil.v(
                    "createDevContext(%d): app %s not debuggable, creating DevContext as disabled",
                    callingAppUid, callingAppPackage);

            builder.setDeviceDevOptionsEnabled(false);
        }
        DevContext devContext = builder.build();
        validateDevSessionStateOrThrow(devContext.getDevSession().getState(), isCallerDebuggable);
        return devContext;
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
    @SuppressWarnings("NewApi")
    public boolean isDeviceDevOptionsEnabledOrDebuggable() {
        return Build.isDebuggable() || isDeviceDevOptionsEnabled();
    }

    private boolean isDeviceDevOptionsEnabled() {
        return Settings.Global.getInt(
                        mContentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
                != 0;
    }

    private void validateDevSessionStateOrThrow(
            DevSessionState devSessionState, boolean isCallerDebuggable) throws RuntimeException {
        Exception genericException = null;
        sLogger.v(
                "Current DevSessionState: %s, isCallerDebuggable: %b,",
                devSessionState, isCallerDebuggable);
        if (devSessionState.equals(DevSessionState.IN_DEV) && !isCallerDebuggable) {
            sLogger.v("Rejecting non-debuggable app in dev session");
            genericException =
                    AdServicesStatusUtils.asException(STATUS_DEV_SESSION_CALLER_IS_NON_DEBUGGABLE);
        }
        if (devSessionState.equals(DevSessionState.UNKNOWN)) {
            genericException = AdServicesStatusUtils.asException(STATUS_DEV_SESSION_FAILURE);
        }
        if (devSessionState.equals(DevSessionState.TRANSITIONING_PROD_TO_DEV)) {
            genericException =
                    AdServicesStatusUtils.asException(STATUS_DEV_SESSION_IS_STILL_TRANSITIONING);
        }
        if (devSessionState.equals(DevSessionState.TRANSITIONING_DEV_TO_PROD)) {
            genericException =
                    AdServicesStatusUtils.asException(STATUS_DEV_SESSION_IS_STILL_TRANSITIONING);
        }
        if (genericException instanceof RuntimeException) {
            throw (RuntimeException) genericException;
        }
    }

    private DevSession getDevSession(boolean isDeviceDevOptionsEnabledOrDebuggable) {
        // Ideally the DevSessionDataStoreFactory would ensure that we never read when the device
        // dev options are disabled. This implies that debuggable builds (such as userdebug or
        // emulators) do not need to go into the device Settings to explicitly enable the developer
        // mode to use the AdServices "dev session" feature.
        if (!isDeviceDevOptionsEnabledOrDebuggable) {
            return DevSession.builder().setState(DevSessionState.IN_PROD).build();
        }

        try {
            return mDevSessionDataStore.get().get(DEV_SESSION_LOOKUP_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            // Note that in this case the value really is UNKNOWN, as if the flag was just disabled
            // we would expect IN_PROD as the default.
            sLogger.e(e, "failed to retrieve DevSession, treating as UNKNOWN");
            return DevSession.UNKNOWN;
        }
    }
}
