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

package com.android.adservices.service.common;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/** Verify caller of FLEDGE API has the permission of performing certain behaviour. */
public class FledgeAuthorizationFilter {
    @NonNull private final PackageManager mPackageManager;
    @NonNull private final EnrollmentDao mEnrollmentDao;
    @NonNull private final AdServicesLogger mAdServicesLogger;

    @VisibleForTesting
    public FledgeAuthorizationFilter(
            @NonNull PackageManager packageManager,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(enrollmentDao);
        Objects.requireNonNull(adServicesLogger);

        mPackageManager = packageManager;
        mEnrollmentDao = enrollmentDao;
        mAdServicesLogger = adServicesLogger;
    }

    /** Creates an instance of {@link FledgeAuthorizationFilter}. */
    public static FledgeAuthorizationFilter create(
            @NonNull Context context, @NonNull AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(context);

        return new FledgeAuthorizationFilter(
                context.getPackageManager(), EnrollmentDao.getInstance(context), adServicesLogger);
    }

    /**
     * Check if the package name provided by the caller is one of the package of the calling uid.
     *
     * @param callingPackageName the caller supplied package name
     * @param callingUid the uid get from the Binder
     * @param apiNameLoggingId the id of the api being called
     * @throws SecurityException if the package name provided does not associate with the uid.
     */
    public void assertCallingPackageName(
            @NonNull String callingPackageName, int callingUid, int apiNameLoggingId) {
        Objects.requireNonNull(callingPackageName);

        String[] packageNamesForUid = mPackageManager.getPackagesForUid(callingUid);
        for (String packageNameForUid : packageNamesForUid) {
            if (callingPackageName.equals(packageNameForUid)) return;
        }
        mAdServicesLogger.logFledgeApiCallStats(
                apiNameLoggingId, AdServicesStatusUtils.STATUS_UNAUTHORIZED);
        throw new SecurityException(
                AdServicesStatusUtils
                        .SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE);
    }

    /**
     * Check if the app had declared custom audience permission.
     *
     * @param context api service context.
     * @param apiNameLoggingId the id of the api being called
     * @throws SecurityException if the package did not declare custom audience permission.
     */
    public void assertAppHasPermission(@NonNull Context context, int apiNameLoggingId) {
        Objects.requireNonNull(context);
        if (!PermissionHelper.hasCustomAudiencesPermission(context)) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, AdServicesStatusUtils.STATUS_PERMISSION_NOT_REQUESTED);
            throw new SecurityException(
                    AdServicesStatusUtils
                            .SECURITY_EXCEPTION_PERMISSION_NOT_REQUESTED_ERROR_MESSAGE);
        }
    }

    /**
     * Check if a certain ad tech is authorized to perform the operation for the package.
     *
     * @param context api service context
     * @param appPackageName the package name to check against
     * @param adTechIdentifier the ad tech to check against
     * @param apiNameLoggingId the id of the api being called
     * @throws SecurityException if the ad tech are not authorized to perform the operation.
     */
    public void assertAdTechHasPermission(
            @NonNull Context context,
            @NonNull String appPackageName,
            @NonNull AdTechIdentifier adTechIdentifier,
            int apiNameLoggingId) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(appPackageName);
        Objects.requireNonNull(adTechIdentifier);

        EnrollmentData enrollmentData =
                mEnrollmentDao.getEnrollmentDataForFledgeByAdTechIdentifier(adTechIdentifier);

        if (enrollmentData == null) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED);
            throw new SecurityException(
                    AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);
        }

        if (!AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                context, appPackageName, enrollmentData.getEnrollmentId())) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED);
            throw new SecurityException(
                    AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);
        }
    }
}
