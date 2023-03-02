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

package com.android.adservices.service.stats;

import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_JS_SANDBOX_UNAVAILABLE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;

import android.os.LimitExceededException;

import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.js.JSSandboxIsNotAvailableException;

import com.google.common.util.concurrent.UncheckedTimeoutException;

/** Util class for AdServicesLogger */
public class AdServicesLoggerUtil {
    /** enum type value for any field in a telemetry atom that should be unset. */
    public static final int FIELD_UNSET = -1;

    /** @return the resultCode corresponding to the type of exception to be used in logging. */
    public static int getResultCodeFromException(Throwable t) {
        int resultCode;
        if (t instanceof AppImportanceFilter.WrongCallingApplicationStateException) {
            resultCode = STATUS_BACKGROUND_CALLER;
        } else if (t instanceof UncheckedTimeoutException) {
            resultCode = STATUS_TIMEOUT;
        } else if (t instanceof FledgeAuthorizationFilter.AdTechNotAllowedException
                || t instanceof FledgeAllowListsFilter.AppNotAllowedException) {
            resultCode = STATUS_CALLER_NOT_ALLOWED;
        } else if (t instanceof FledgeAuthorizationFilter.CallerMismatchException) {
            resultCode = STATUS_UNAUTHORIZED;
        } else if (t instanceof JSSandboxIsNotAvailableException) {
            resultCode = STATUS_JS_SANDBOX_UNAVAILABLE;
        } else if (t instanceof IllegalArgumentException) {
            resultCode = STATUS_INVALID_ARGUMENT;
        } else if (t instanceof LimitExceededException) {
            resultCode = STATUS_RATE_LIMIT_REACHED;
        } else if (t instanceof ConsentManager.RevokedConsentException) {
            resultCode = STATUS_USER_CONSENT_REVOKED;
        } else {
            resultCode = STATUS_INTERNAL_ERROR;
        }
        return resultCode;
    }
}
