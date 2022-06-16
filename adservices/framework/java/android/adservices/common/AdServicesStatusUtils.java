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

package android.adservices.common;

import android.adservices.exceptions.ApiNotAuthorizedException;
import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Utility class containing status codes and functions used by various response objects.
 *
 * @hide
 */
public class AdServicesStatusUtils {
    /**
     * Result codes that are common across various APIs.
     *
     * @hide
     */
    @IntDef(
            prefix = {"STATUS_"},
            value = {
                STATUS_UNSET,
                STATUS_SUCCESS,
                STATUS_INTERNAL_ERROR,
                STATUS_INVALID_ARGUMENT,
                STATUS_UNAUTHORIZED,
                STATUS_UNKNOWN_ERROR
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface StatusCode {}

    /**
     * The status code has not been set. Keep unset status code the lowest value of the status
     * codes.
     */
    public static final int STATUS_UNSET = -1;

    /** The call was successful. */
    public static final int STATUS_SUCCESS = 0;

    /**
     * An internal error occurred within the API, which the caller cannot address.
     *
     * <p>This error may be considered similar to {@link IllegalStateException}.
     */
    public static final int STATUS_INTERNAL_ERROR = 1;

    /**
     * The caller supplied invalid arguments to the call.
     *
     * <p>This error may be considered similar to {@link IllegalArgumentException}.
     */
    public static final int STATUS_INVALID_ARGUMENT = 2;

    /**
     * The caller is not authorized to make this call.
     *
     * <p>This error may be considered similar to {@link ApiNotAuthorizedException}.
     */
    public static final int STATUS_UNAUTHORIZED = 3;

    /** There was an unknown error. Keep unknown error the largest status code. */
    public static final int STATUS_UNKNOWN_ERROR = 4;

    /** Returns true for a successful status. */
    public static boolean isSuccess(@StatusCode int statusCode) {
        return statusCode == STATUS_SUCCESS;
    }
}
