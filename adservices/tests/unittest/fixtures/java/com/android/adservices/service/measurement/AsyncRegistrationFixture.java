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

package com.android.adservices.service.measurement;

import android.net.Uri;

import java.util.UUID;

public class AsyncRegistrationFixture {
    private AsyncRegistrationFixture() {}

    public static AsyncRegistration getValidAsyncRegistration() {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setEnrollmentId(UUID.randomUUID().toString())
                .setRegistrationUri(ValidAsyncRegistrationParams.REGISTRATION_URI)
                .setOsDestination(ValidAsyncRegistrationParams.OS_DESTINATION)
                .setWebDestination(ValidAsyncRegistrationParams.WEB_DESTINATION)
                .setVerifiedDestination(ValidAsyncRegistrationParams.VERIFIED_DESTINATION)
                .setRegistrant(ValidAsyncRegistrationParams.REGISTRANT)
                .setTopOrigin(ValidAsyncRegistrationParams.TOP_ORIGIN)
                .setRedirectType(ValidAsyncRegistrationParams.REDIRECT_TYPE)
                .setRedirectCount(ValidAsyncRegistrationParams.REDIRECT_COUNT)
                .setSourceType(ValidAsyncRegistrationParams.SOURCE_TYPE)
                .setRequestTime(System.currentTimeMillis())
                .setRetryCount(ValidAsyncRegistrationParams.RETRY_COUNT)
                .setLastProcessingTime(System.currentTimeMillis())
                .setType(ValidAsyncRegistrationParams.TYPE.ordinal())
                .setDebugKeyAllowed(ValidAsyncRegistrationParams.DEBUG_KEY_ALLOWED)
                .setRegistrationId(ValidAsyncRegistrationParams.REGISTRATION_ID)
                .build();
    }

    public static class ValidAsyncRegistrationParams {
        public static final Source.SourceType SOURCE_TYPE = Source.SourceType.EVENT;
        public static final long RETRY_COUNT = 0;
        public static final Uri REGISTRATION_URI = Uri.parse("android-app://com.example");
        public static final Uri OS_DESTINATION = Uri.parse("android-app://com.example");
        public static final Uri WEB_DESTINATION = Uri.parse("https://com.example");
        public static final Uri VERIFIED_DESTINATION = Uri.parse("android-app://com.example");
        public static final Uri REGISTRANT = Uri.parse("android-app://com.example");
        public static final Uri TOP_ORIGIN = Uri.parse("android-app://com.example");
        public static final @AsyncRegistration.RedirectType int REDIRECT_TYPE = 1;
        public static final int REDIRECT_COUNT = 0;
        public static final boolean DEBUG_KEY_ALLOWED = true;
        public static final AsyncRegistration.RegistrationType TYPE =
                AsyncRegistration.RegistrationType.APP_SOURCE;
        public static final String REGISTRATION_ID = "R1";
    }
}
