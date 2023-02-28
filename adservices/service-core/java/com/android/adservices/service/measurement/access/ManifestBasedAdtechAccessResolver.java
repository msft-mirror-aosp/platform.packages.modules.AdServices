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

import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;

import android.adservices.common.AdServicesStatusUtils;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.adservices.service.enrollment.EnrollmentData;

/** Resolves whether the app developer has included the adtech in the app manifest. */
public class ManifestBasedAdtechAccessResolver implements IAccessResolver {
    private static final String ERROR_MESSAGE = "Caller is not authorized.";
    private final EnrollmentDao mEnrollmentDao;
    private final Flags mFlags;
    private final String mPackageName;
    private final Uri mUrl;

    public ManifestBasedAdtechAccessResolver(
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull Flags flags,
            @NonNull String packageName,
            Uri url) {
        mEnrollmentDao = enrollmentDao;
        mFlags = flags;
        mPackageName = packageName;
        mUrl = url;
    }

    @Override
    public boolean isAllowed(@NonNull Context context) {
        if (mFlags.isDisableMeasurementEnrollmentCheck()) {
            return true;
        }
        /* Note: The following block of code only checks (and returns false) if the
        first URL in the chain is not allowed based on enrollment. The ones that appear
        later in the chain will be silently dropped if not included in the enrollment list.
        This is implemented elsewhere.
        TODO: verify that the above behavior aligns with the expectation of the serving
        adtech.
        */
        if (mUrl == null || TextUtils.isEmpty(mUrl.toString())) {
            return false;
        }
        Uri uriWithoutParams = mUrl.buildUpon().clearQuery().fragment(null).build();
        EnrollmentData enrollment =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(uriWithoutParams);
        if (enrollment == null || enrollment.getEnrollmentId() == null) {
            return false;
        }
        String enrollmentId = enrollment.getEnrollmentId();
        return AppManifestConfigHelper.isAllowedAttributionAccess(
                        context, mPackageName, enrollmentId)
                && !mFlags.isEnrollmentBlocklisted(enrollmentId);
    }

    @NonNull
    @Override
    public String getErrorMessage() {
        return ERROR_MESSAGE;
    }

    @NonNull
    @Override
    @AdServicesStatusUtils.StatusCode
    public int getErrorStatusCode() {
        return STATUS_CALLER_NOT_ALLOWED;
    }
}
