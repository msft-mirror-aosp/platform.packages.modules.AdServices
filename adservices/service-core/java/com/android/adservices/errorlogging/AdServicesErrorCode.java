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

package com.android.adservices.errorlogging;

/**
 * Enum representing an error/exception. These errors can be common to all PPAPIs or specific to a
 * particular API. We will group enums in blocks of 1000 like this below: - Common errors: 1-1000 -
 * Topics errors: 1001-2000 - Measurement errors: 2001-3000 - Fledge errors: 3001-4000 - UX errors:
 * 4001-5000 Keep enum definitions in sync with statsd ErrorCode enum defined here:
 * http://shortn/_X5V1wPkv7b.
 */
public enum AdServicesErrorCode {

    // Error code is not specified.
    ERROR_CODE_UNSPECIFIED(0),

    // Error occurred when reading the database.
    DATABASE_READ_EXCEPTION(1),

    // Error occurred when writing to database.
    DATABASE_WRITE_EXCEPTION(2),

    // Error representing Remote exception when calling the API.
    API_REMOTE_EXCEPTION(3),

    // UX Errors: 4001-5000
    // Error representing consent is revoked.
    CONSENT_REVOKED_ERROR(4001);

    private final int mErrorCode;

    /**
     * Get the integer error code from an enum.
     *
     * @return the integer error code
     */
    public int getErrorCode() {
        return mErrorCode;
    }

    AdServicesErrorCode(int errorCode) {
        mErrorCode = errorCode;
    }
}
