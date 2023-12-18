/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_APP_ALLOWS_ALL;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_DOES_NOT_EXIST;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_BY_APP;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_GENERIC_ERROR;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_UNSPECIFIED;
import static com.android.adservices.service.common.AppManifestConfigCall.isAllowed;
import static com.android.adservices.service.common.AppManifestConfigCall.resultToString;

import static org.mockito.ArgumentMatchers.argThat;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;
import org.mockito.ArgumentMatcher;

public final class AppManifestConfigCallTest extends AdServicesUnitTestCase {

    @Test
    public void testResultToString() {
        expect.withMessage("resultToString(%s)", RESULT_UNSPECIFIED)
                .that(resultToString(RESULT_UNSPECIFIED))
                .isEqualTo("UNSPECIFIED");
        expect.withMessage("resultToString(%s)", RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG)
                .that(resultToString(RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG))
                .isEqualTo("ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG");
        expect.withMessage(
                        "resultToString(%s)",
                        RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION)
                .that(resultToString(RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION))
                .isEqualTo("ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION");
        expect.withMessage("resultToString(%s)", RESULT_ALLOWED_APP_ALLOWS_ALL)
                .that(resultToString(RESULT_ALLOWED_APP_ALLOWS_ALL))
                .isEqualTo("ALLOWED_APP_ALLOWS_ALL");
        expect.withMessage("resultToString(%s)", RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID)
                .that(resultToString(RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID))
                .isEqualTo("ALLOWED_APP_ALLOWS_SPECIFIC_ID");
        expect.withMessage("resultToString(%s)", RESULT_DISALLOWED_APP_DOES_NOT_EXIST)
                .that(resultToString(RESULT_DISALLOWED_APP_DOES_NOT_EXIST))
                .isEqualTo("DISALLOWED_APP_DOES_NOT_EXIST");
        expect.withMessage("resultToString(%s)", RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR)
                .that(resultToString(RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR))
                .isEqualTo("DISALLOWED_APP_CONFIG_PARSING_ERROR");
        expect.withMessage("resultToString(%s)", RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG)
                .that(resultToString(RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG))
                .isEqualTo("DISALLOWED_APP_DOES_NOT_HAVE_CONFIG");
        expect.withMessage(
                        "resultToString(%s)", RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION)
                .that(resultToString(RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION))
                .isEqualTo("DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION");
        expect.withMessage("resultToString(%s)", RESULT_DISALLOWED_BY_APP)
                .that(resultToString(RESULT_DISALLOWED_BY_APP))
                .isEqualTo("DISALLOWED_BY_APP");
        expect.withMessage("resultToString(%s)", RESULT_DISALLOWED_GENERIC_ERROR)
                .that(resultToString(RESULT_DISALLOWED_GENERIC_ERROR))
                .isEqualTo("DISALLOWED_GENERIC_ERROR");
    }

    @Test
    public void testIsAllowed() {
        expect.withMessage("isAllowed(%s)", resultToString(RESULT_UNSPECIFIED))
                .that(isAllowed(RESULT_UNSPECIFIED))
                .isFalse();
        expect.withMessage(
                        "isAllowed(%s)",
                        resultToString(RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG))
                .that(isAllowed(RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG))
                .isTrue();
        expect.withMessage(
                        "isAllowed(%s)",
                        resultToString(
                                RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION))
                .that(isAllowed(RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION))
                .isTrue();
        expect.withMessage("isAllowed(%s)", resultToString(RESULT_ALLOWED_APP_ALLOWS_ALL))
                .that(isAllowed(RESULT_ALLOWED_APP_ALLOWS_ALL))
                .isTrue();
        expect.withMessage("isAllowed(%s)", resultToString(RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID))
                .that(isAllowed(RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID))
                .isTrue();
        expect.withMessage("isAllowed(%s)", resultToString(RESULT_DISALLOWED_APP_DOES_NOT_EXIST))
                .that(isAllowed(RESULT_DISALLOWED_APP_DOES_NOT_EXIST))
                .isFalse();
        expect.withMessage(
                        "isAllowed(%s)", resultToString(RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR))
                .that(isAllowed(RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR))
                .isFalse();
        expect.withMessage(
                        "isAllowed(%s)", resultToString(RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG))
                .that(isAllowed(RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG))
                .isFalse();
        expect.withMessage(
                        "isAllowed(%s)",
                        resultToString(RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION))
                .that(isAllowed(RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION))
                .isFalse();
        expect.withMessage("isAllowed(%s)", resultToString(RESULT_DISALLOWED_BY_APP))
                .that(isAllowed(RESULT_DISALLOWED_BY_APP))
                .isFalse();
        expect.withMessage("isAllowed(%s)", resultToString(RESULT_DISALLOWED_GENERIC_ERROR))
                .that(isAllowed(RESULT_DISALLOWED_GENERIC_ERROR))
                .isFalse();
    }

    /** Gets a custom Mockito matcher for a {@link AppManifestConfigCall}, without the result. */
    static AppManifestConfigCall appManifestConfigCall(
            String packageName,
            boolean appExists,
            boolean appHasConfig,
            boolean enabledByDefault,
            int result) {
        return argThat(
                new AppManifestConfigCallMatcher(
                        packageName, appExists, appHasConfig, enabledByDefault, result));
    }

    // TODO(b/306417555): remove it / implement equals() on AppManifestConfigCall instead (once that
    // class is refactored to only have fields for API type and result)
    private static final class AppManifestConfigCallMatcher
            implements ArgumentMatcher<AppManifestConfigCall> {

        private final String mPackageName;
        private final boolean mAppExists;
        private final boolean mAppHasConfig;
        private final boolean mEnabledByDefault;
        private final int mResult;

        private AppManifestConfigCallMatcher(
                String packageName,
                boolean appExists,
                boolean appHasConfig,
                boolean enabledByDefault,
                int result) {
            mPackageName = packageName;
            mAppExists = appExists;
            mAppHasConfig = appHasConfig;
            mEnabledByDefault = enabledByDefault;
            mResult = result;
        }

        @Override
        public boolean matches(AppManifestConfigCall arg) {
            return arg != null
                    && arg.packageName.equals(mPackageName)
                    && arg.appExists == mAppExists
                    && arg.appHasConfig == mAppHasConfig
                    && arg.enabledByDefault == mEnabledByDefault
                    && arg.result == mResult;
        }

        @Override
        public String toString() {
            AppManifestConfigCall call = new AppManifestConfigCall(mPackageName);
            call.result = mResult;
            call.appExists = mAppExists;
            call.appHasConfig = mAppHasConfig;
            call.enabledByDefault = mEnabledByDefault;

            return call.toString();
        }
    }
}
