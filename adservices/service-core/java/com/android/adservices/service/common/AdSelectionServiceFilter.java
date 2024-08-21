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

import static com.android.adservices.service.common.AppManifestConfigCall.API_AD_SELECTION;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.Flags;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.profiling.Tracing;

import java.util.Objects;

/** Utility class to filter FLEDGE requests. */
@RequiresApi(Build.VERSION_CODES.S)
public class AdSelectionServiceFilter extends AbstractFledgeServiceFilter {
    public AdSelectionServiceFilter(
            @NonNull Context context,
            @NonNull FledgeConsentFilter fledgeConsentFilter,
            @NonNull Flags flags,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull FledgeAllowListsFilter fledgeAllowListsFilter,
            @NonNull FledgeApiThrottleFilter fledgeApiThrottleFilter) {
        super(
                context,
                fledgeConsentFilter,
                flags,
                appImportanceFilter,
                fledgeAuthorizationFilter,
                fledgeAllowListsFilter,
                fledgeApiThrottleFilter);
    }

    /**
     * Applies the filtering operations to the context of a FLEDGE request. The specific filtering
     * operations are discussed in the comments below.
     *
     * @param adTech the adTech associated with the request. This parameter is nullable, and the
     *     enrollment check will not be applied if it is null.
     * @param callerPackageName caller package name to be validated
     * @param enforceForeground whether to enforce a foreground check
     * @param enforceConsent whether to enforce a consent check
     * @param enforceNotificationShown whether to enforce a UX notification check
     * @throws FilterException if any filter assertion fails and wraps the exception thrown by the
     *     failing filter Note: Any consumer of this API should not log the failure. The failing
     *     assertion logs it internally before throwing the corresponding exception.
     */
    @Override
    public void filterRequest(
            @Nullable AdTechIdentifier adTech,
            @NonNull String callerPackageName,
            boolean enforceForeground,
            boolean enforceConsent,
            boolean enforceNotificationShown,
            int callerUid,
            int apiName,
            @NonNull Throttler.ApiKey apiKey,
            DevContext devContext) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.AD_SELECTION_SERVICE_FILTER);
        try {
            Objects.requireNonNull(callerPackageName);
            Objects.requireNonNull(apiKey);

            assertCallerPackageName(callerPackageName, callerUid, apiName);
            assertCallerNotThrottled(callerPackageName, apiKey, apiName);
            if (enforceForeground) {
                assertForegroundCaller(callerUid, apiName);
            }
            assertEnrollmentShouldBeScheduled(
                    enforceConsent, enforceNotificationShown, callerPackageName, apiName);
            if (!Objects.isNull(adTech)) {
                assertFledgeEnrollment(
                        adTech, callerPackageName, apiName, devContext, API_AD_SELECTION);
            }
            assertAppInAllowList(callerPackageName, apiName, API_AD_SELECTION);
            if (enforceConsent) {
                assertCallerHasUserConsent(callerPackageName, apiName);
            }
        } catch (Throwable t) {
            throw new FilterException(t);
        } finally {
            Tracing.endAsyncSection(Tracing.AD_SELECTION_SERVICE_FILTER, traceCookie);
        }
    }
}
