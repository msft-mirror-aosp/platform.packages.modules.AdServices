/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_UID_CHECK_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import android.adservices.common.AdServicesStatusUtils;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.adservices.LoggerFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.common.SdkRuntimeUtil;

import java.util.Locale;

/**
 * Access Resolver that verifies the package name from the request actually belongs to the calling
 * package.
 */
public final class PackageNameUidCheckAccessResolver implements IAccessResolver {
    private static final String ERROR_MESSAGE = "Package %s does not belong to UID %d.";
    private final String mCallingPackage;
    private final int mCallingUid;
    private final Boolean mEnablePackageNameUidCheck;

    public PackageNameUidCheckAccessResolver(
            String callingPackage, int callingUid, Boolean packageNameUidCheckEnabled) {
        mCallingPackage = callingPackage;
        mCallingUid = callingUid;
        mEnablePackageNameUidCheck = packageNameUidCheckEnabled;
    }

    @Override
    public AccessInfo getAccessInfo(Context context) {
        if (!mEnablePackageNameUidCheck) {
            LoggerFactory.getMeasurementLogger()
                    .d("Checking package name against the calling uid has been disabled.");
            return new AccessInfo(true, AdServicesStatusUtils.STATUS_SUCCESS);
        }

        int appCallingUid = SdkRuntimeUtil.getCallingAppUid(mCallingUid);
        int packageUid;
        try {
            LoggerFactory.getMeasurementLogger()
                    .d("Fetching package UID for package: %s", mCallingPackage);
            packageUid = context.getPackageManager().getPackageUid(mCallingPackage, /* flags */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            return new AccessInfo(
                    false, AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED_UID_MISMATCH);
        } catch (Exception e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "Unexpected error occurred when asserting caller UID");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_UID_CHECK_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            return new AccessInfo(
                    false, AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED_UID_MISMATCH);
        }
        LoggerFactory.getMeasurementLogger()
                .d("Comparing package UID: %d to calling UID: %d.", packageUid, appCallingUid);
        if (packageUid != appCallingUid) {
            return new AccessInfo(
                    false, AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED_UID_MISMATCH);
        }

        LoggerFactory.getMeasurementLogger().d("Package UID and calling UID match");

        return new AccessInfo(true, AdServicesStatusUtils.STATUS_SUCCESS);
    }

    @NonNull
    @Override
    public String getErrorMessage() {
        return String.format(Locale.ENGLISH, ERROR_MESSAGE, mCallingPackage, mCallingUid);
    }
}
