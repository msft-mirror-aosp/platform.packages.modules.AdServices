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

import java.util.Objects;

// TODO(b/310270746): make it package-protected when TopicsServiceImplTest is refactored
/** Represents a call to a public {@link AppManifestConfigHelper} method. */
public final class AppManifestConfigCall {

    // TODO(b/306417555): use values from statsd atom for constants below
    static final int RESULT_UNSPECIFIED = 0;
    static final int RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG = 1;
    static final int RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION = 2;
    static final int RESULT_ALLOWED_APP_ALLOWS_ALL = 3;
    static final int RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID = 4;
    static final int RESULT_DISALLOWED_APP_DOES_NOT_EXIST = 5;
    static final int RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR = 6;
    static final int RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG = 7;
    static final int RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION = 8;
    static final int RESULT_DISALLOWED_BY_APP = 9;
    static final int RESULT_DISALLOWED_GENERIC_ERROR = 10;

    public final String packageName;
    public int result;

    public AppManifestConfigCall(String packageName) {
        this.packageName = Objects.requireNonNull(packageName, "packageName cannot be null");
    }

    @Override
    public String toString() {
        return "AppManifestConfigCall[pkg="
                + packageName
                + ", result="
                + resultToString(result)
                + "]";
    }

    static String resultToString(int result) {
        switch (result) {
            case RESULT_UNSPECIFIED:
                return "UNSPECIFIED";
            case RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG:
                return "ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG";
            case RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION:
                return "ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION";
            case RESULT_ALLOWED_APP_ALLOWS_ALL:
                return "ALLOWED_APP_ALLOWS_ALL";
            case RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID:
                return "ALLOWED_APP_ALLOWS_SPECIFIC_ID";
            case RESULT_DISALLOWED_APP_DOES_NOT_EXIST:
                return "DISALLOWED_APP_DOES_NOT_EXIST";
            case RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR:
                return "DISALLOWED_APP_CONFIG_PARSING_ERROR";
            case RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG:
                return "DISALLOWED_APP_DOES_NOT_HAVE_CONFIG";
            case RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION:
                return "DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION";
            case RESULT_DISALLOWED_BY_APP:
                return "DISALLOWED_BY_APP";
            case RESULT_DISALLOWED_GENERIC_ERROR:
                return "DISALLOWED_GENERIC_ERROR";
            default:
                return "INVALID-" + result;
        }
    }

    static boolean isAllowed(int result) {
        switch (result) {
            case RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG:
            case RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION:
            case RESULT_ALLOWED_APP_ALLOWS_ALL:
            case RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID:
                return true;
            default:
                return false;
        }
    }
}
