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

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Build;
import android.os.LimitExceededException;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.Flags;
import com.android.adservices.service.consent.ConsentManager;

import java.util.Objects;
import java.util.function.Supplier;

/** Utility class to filter FLEDGE requests. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class FledgeServiceFilter extends AbstractFledgeServiceFilter {
    public FledgeServiceFilter(
            @NonNull Context context,
            @NonNull ConsentManager consentManager,
            @NonNull Flags flags,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull FledgeAllowListsFilter fledgeAllowListsFilter,
            @NonNull Supplier<Throttler> throttlerSupplier) {
        super(
                context,
                consentManager,
                flags,
                appImportanceFilter,
                fledgeAuthorizationFilter,
                fledgeAllowListsFilter,
                throttlerSupplier);
    }

    /**
     * Applies the filtering operations to the context of a FLEDGE request. The specific filtering
     * operations are discussed in the comments below.
     *
     * @param adTech the adTech associated with the request. This parameter is nullable, and the
     *     enrollment check will not be applied if it is null.
     * @param callerPackageName caller package name to be validated
     * @param enforceForeground whether to enforce a foreground check
     * @param enforceConsent Checks if the user has revoked global FLEDGE consent if set to true.
     *     Should only be set to false if the caller is checking FLEDGE consent on their own.
     * @throws FledgeAuthorizationFilter.CallerMismatchException if the {@code callerPackageName} is
     *     not valid
     * @throws AppImportanceFilter.WrongCallingApplicationStateException if the foreground check is
     *     enabled and fails
     * @throws FledgeAuthorizationFilter.AdTechNotAllowedException if the ad tech is not authorized
     *     to perform the operation
     * @throws FledgeAllowListsFilter.AppNotAllowedException if the package is not authorized.
     * @throws ConsentManager.RevokedConsentException if FLEDGE or the Privacy Sandbox do not have
     *     user consent
     * @throws LimitExceededException if the provided {@code callerPackageName} exceeds the rate
     *     limits
     */
    @Override
    public void filterRequest(
            @Nullable AdTechIdentifier adTech,
            @NonNull String callerPackageName,
            boolean enforceForeground,
            boolean enforceConsent,
            int callerUid,
            int apiName,
            @NonNull Throttler.ApiKey apiKey) {
        Objects.requireNonNull(callerPackageName);
        Objects.requireNonNull(apiKey);

        assertCallerPackageName(callerPackageName, callerUid, apiName);
        assertCallerNotThrottled(callerPackageName, apiKey);
        if (enforceForeground) {
            assertForegroundCaller(callerUid, apiName);
        }
        if (!Objects.isNull(adTech)) {
            assertFledgeEnrollment(adTech, callerPackageName, apiName);
        }
        assertAppInAllowList(callerPackageName, apiName);
        if (enforceConsent) {
            assertCallerHasUserConsent();
        }
    }
}
