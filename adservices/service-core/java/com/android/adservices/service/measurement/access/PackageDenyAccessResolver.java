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

package com.android.adservices.service.measurement.access;

import android.adservices.common.AdServicesStatusUtils;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;

import com.android.adservices.LogUtil;
import com.android.adservices.service.common.AdPackageDenyResolver;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Resolves the is package is in deny list. */
public class PackageDenyAccessResolver implements IAccessResolver {

    public static final String MEASUREMENT_GROUP = "measurement";
    public static final String MEASUREMENT_API_REGISTER_SOURCE = "measurement_api_register_source";

    private static final long PACKAGE_DENY_TIME_OUT_MILLIS = 5000;
    private final String mAppName;
    private final String mSdkName;
    private final Set<String> mApiGroups;
    private boolean mIsAllowed;

    public PackageDenyAccessResolver(
            boolean enablePackageDeny,
            AdPackageDenyResolver adPackageDenyResolver,
            String appName,
            String sdkName,
            Set<String> apiGroups) {
        this.mAppName = appName;
        this.mSdkName = sdkName;
        this.mApiGroups = apiGroups;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R || !enablePackageDeny) {
            LogUtil.d("The enable package deny flag is false");
            this.mIsAllowed = true;
        } else {
            try {
                ListenableFuture<Boolean> future =
                        adPackageDenyResolver.shouldDenyPackage(mAppName, mSdkName, mApiGroups);
                this.mIsAllowed = !future.get(PACKAGE_DENY_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
                LogUtil.d("isAllowed after package deny " + this.mIsAllowed);

            } catch (Exception e) {
                this.mIsAllowed = false;
                LogUtil.e(
                        "Exception in trying to check deny for app: %s, sdk: %s, api groups : %s,"
                                + " is %s ",
                        mAppName, mSdkName, mApiGroups, e);
            }
        }
        LogUtil.d(
                "The mIsAllowed for app: %s, sdk: %s, api groups : %s, is %s ",
                mAppName, mSdkName, mApiGroups, mIsAllowed);
    }

    @Override
    public AccessInfo getAccessInfo(@NonNull Context context) {
        LogUtil.d(
                "The accessInfo for app: %s, sdk: %s, api groups : %s, is %s ",
                mAppName, mSdkName, mApiGroups, mIsAllowed);
        int statusCode =
                mIsAllowed
                        ? AdServicesStatusUtils.STATUS_SUCCESS
                        : AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED_DENY_LIST;
        return new AccessInfo(mIsAllowed, statusCode);
    }

    @NonNull
    @Override
    public String getErrorMessage() {
        return String.format(
                "Package app %s for sdk %s is denied for measurement", mAppName, mSdkName);
    }
}
