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
import android.annotation.NonNull;

import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.AdServicesLogger;

import java.util.Objects;

/** FLEDGE Security filter for {@link AllowLists}. */
public class FledgeAllowListsFilter {
    @NonNull private final Flags mFlags;
    @NonNull private final AdServicesLogger mAdServicesLogger;

    public FledgeAllowListsFilter(
            @NonNull Flags flags, @NonNull AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(flags);
        Objects.requireNonNull(adServicesLogger);

        mFlags = flags;
        mAdServicesLogger = adServicesLogger;
    }

    /**
     * Asserts the package is allowed to call PPAPI.
     *
     * @param appPackageName the package name to be validated.
     * @param apiNameLoggingId the id of the api being called
     * @throws SecurityException if the package is not authorized.
     */
    public void assertAppCanUsePpapi(@NonNull String appPackageName, int apiNameLoggingId) {
        Objects.requireNonNull(appPackageName);
        if (!AllowLists.appCanUsePpapi(mFlags, appPackageName)) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED);
            throw new SecurityException(
                    AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);
        }
    }
}
