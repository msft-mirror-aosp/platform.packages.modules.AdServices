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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.LimitExceededException;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.TimeoutException;

/**
 * Utility class containing status codes and functions used by various response objects.
 *
 * <p>Those status codes are internal only.
 *
 * @hide
 */
public class AdServicesStatusUtils {
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

    /** There was an unknown error. */
    public static final int STATUS_UNKNOWN_ERROR = 3;

    /**
     * There was an I/O error.
     *
     * <p>This error may be considered similar to {@link IOException}.
     */
    public static final int STATUS_IO_ERROR = 4;

    /**
     * Result code for Rate Limit Reached.
     *
     * <p>This error may be considered similar to {@link LimitExceededException}.
     */
    public static final int STATUS_RATE_LIMIT_REACHED = 5;

    /**
     * Killswitch was enabled. AdServices is not available.
     *
     * <p>This error may be considered similar to {@link IllegalStateException}.
     */
    public static final int STATUS_KILLSWITCH_ENABLED = 6;

    /**
     * User consent was revoked. AdServices is not available.
     *
     * <p>This error may be considered similar to {@link IllegalStateException}.
     */
    public static final int STATUS_USER_CONSENT_REVOKED = 7;

    /**
     * AdServices were disabled. AdServices is not available.
     *
     * <p>This error may be considered similar to {@link IllegalStateException}.
     */
    public static final int STATUS_ADSERVICES_DISABLED = 8;

    /**
     * The caller is not authorized to make this call. Permission was not requested.
     *
     * <p>This error may be considered similar to {@link SecurityException}.
     */
    public static final int STATUS_PERMISSION_NOT_REQUESTED = 9;

    /**
     * The caller is not authorized to make this call. Caller is not allowed (not present in the
     * allowed list).
     *
     * <p>This error may be considered similar to {@link SecurityException}.
     */
    public static final int STATUS_CALLER_NOT_ALLOWED = 10;

    /**
     * The caller is not authorized to make this call. Call was executed from background thread.
     *
     * <p>This error may be considered similar to {@link IllegalStateException}.
     */
    public static final int STATUS_BACKGROUND_CALLER = 11;

    /**
     * The caller is not authorized to make this call.
     *
     * <p>This error may be considered similar to {@link SecurityException}.
     */
    public static final int STATUS_UNAUTHORIZED = 12;

    /**
     * There was an internal Timeout within the API, which is non-recoverable by the caller
     *
     * <p>This error may be considered similar to {@link java.util.concurrent.TimeoutException}
     */
    public static final int STATUS_TIMEOUT = 13;

    /**
     * The device is not running a version of WebView that supports JSSandbox, required for FLEDGE
     * Ad Selection.
     *
     * <p>This error may be considered similar to {@link IllegalStateException}.
     */
    public static final int STATUS_JS_SANDBOX_UNAVAILABLE = 14;

    /**
     * The service received an invalid object from the remote server.
     *
     * <p>This error may be considered similar to {@link InvalidObjectException}.
     */
    public static final int STATUS_INVALID_OBJECT = 15;

    /**
     * The caller is not authorized to make this call because it crosses user boundaries.
     *
     * <p>This error may be considered similar to {@link SecurityException}.
     */
    public static final int STATUS_CALLER_NOT_ALLOWED_TO_CROSS_USER_BOUNDARIES = 16;

    /**
     * Result code for Server Rate Limit Reached.
     *
     * <p>This error may be considered similar to {@link LimitExceededException}.
     */
    public static final int STATUS_SERVER_RATE_LIMIT_REACHED = 17;

    /**
     * Consent notification has not been displayed yet. AdServices is not available.
     *
     * <p>This error may be considered similar to {@link IllegalStateException}.
     */
    public static final int STATUS_USER_CONSENT_NOTIFICATION_NOT_DISPLAYED_YET = 18;

    public static final int FAILURE_REASON_UNSET = 0;

    // Failure Reason - Package Allowlist
    public static final int FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST = 1;

    // Failure Reason - Package Blocklist
    public static final int FAILURE_REASON_PACKAGE_BLOCKLISTED = 2;

    // Failure Reason - Enrollment
    public static final int FAILURE_REASON_ENROLLMENT_BLOCKLISTED = 3;
    public static final int FAILURE_REASON_ENROLLMENT_MATCH_NOT_FOUND = 4;
    public static final int FAILURE_REASON_ENROLLMENT_INVALID_ID = 5;

    // Failure Reason - Dev Options
    public static final int FAILURE_REASON_DEV_OPTIONS_DISABLED_WHILE_USING_LOCALHOST = 6;

    // Failure Reason - Foreground
    public static final int FAILURE_REASON_FOREGROUND_APP_NOT_IN_FOREGROUND = 7;
    public static final int FAILURE_REASON_FOREGROUND_ASSERTION_EXCEPTION = 8;

    // Failure Reason - App Manifest AdServices Config
    public static final int FAILURE_REASON_MANIFEST_ADSERVICES_CONFIG_NO_PERMISSION = 9;

    // Failure Reason - Calling Package
    public static final int FAILURE_REASON_CALLING_PACKAGE_NOT_FOUND = 10;
    public static final int FAILURE_REASON_CALLING_PACKAGE_DOES_NOT_BELONG_TO_CALLING_ID = 11;

    /**
     * Result code for Encryption related failures.
     *
     * <p>This error may be considered similar to {@link IllegalArgumentException}.
     */
    public static final int STATUS_ENCRYPTION_FAILURE = 19;

    /** The error message to be returned along with {@link IllegalStateException}. */
    public static final String ILLEGAL_STATE_EXCEPTION_ERROR_MESSAGE = "Service is not available.";

    /** The error message to be returned along with {@link LimitExceededException}. */
    public static final String RATE_LIMIT_REACHED_ERROR_MESSAGE = "API rate limit exceeded.";

    /** The error message to be returned along with {@link LimitExceededException}. */
    public static final String SERVER_RATE_LIMIT_REACHED_ERROR_MESSAGE =
            "Server rate limit exceeded.";

    /**
     * The error message to be returned along with {@link SecurityException} when permission was not
     * requested in the manifest.
     */
    public static final String SECURITY_EXCEPTION_PERMISSION_NOT_REQUESTED_ERROR_MESSAGE =
            "Caller is not authorized to call this API. Permission was not requested.";

    /**
     * The error message to be returned along with {@link SecurityException} when caller is not
     * allowed to call AdServices (not present in the allowed list).
     */
    public static final String SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE =
            "Caller is not authorized to call this API. Caller is not allowed.";

    /**
     * The error message to be returned along with {@link SecurityException} when call was executed
     * from the background thread.
     */
    public static final String ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE =
            "Background thread is not allowed to call this service.";

    /**
     * The error message to be returned along with {@link SecurityException} when call failed
     * because it crosses user boundaries.
     */
    public static final String SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_TO_CROSS_USER_BOUNDARIES =
            "Caller is not authorized to access information from another user";

    /**
     * The error message to be returned along with {@link SecurityException} when caller not allowed
     * to perform this operation on behalf of the given package.
     */
    public static final String SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE =
            "Caller is not allowed to perform this operation on behalf of the given package.";

    /** The error message to be returned along with {@link TimeoutException}. */
    public static final String TIMED_OUT_ERROR_MESSAGE = "API timed out.";

    /** The error message to be returned along with {@link InvalidObjectException}. */
    public static final String INVALID_OBJECT_ERROR_MESSAGE =
            "The service received an invalid object from the server.";

    /** The error message to be returned along with {@link IllegalArgumentException}. */
    public static final String ENCRYPTION_FAILURE_MESSAGE = "Failed to encrypt responses.";

    // API codes used for logging. Keep in sync with the AdServicesApiName in
    // frameworks/proto_logging/stats/atoms.proto
    // TODO(b/323439428): Add unit test for api name code.
    public static final int API_NAME_GET_TOPICS = 1;
    public static final int API_NAME_JOIN_CUSTOM_AUDIENCE = 2;
    public static final int API_NAME_LEAVE_CUSTOM_AUDIENCE = 3;
    public static final int API_NAME_SELECT_ADS = 4;
    public static final int API_NAME_REGISTER_SOURCE = 5;
    public static final int API_NAME_DELETE_REGISTRATIONS = 6;
    public static final int API_NAME_REPORT_IMPRESSION = 7;
    public static final int API_NAME_OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO = 8;
    public static final int API_NAME_REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE = 9;
    public static final int API_NAME_RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES = 10;
    public static final int API_NAME_OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO = 11;
    public static final int API_NAME_REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE = 12;
    public static final int API_NAME_RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES = 13;
    public static final int API_NAME_GET_ADID = 14;
    public static final int API_NAME_GET_APPSETID = 15;
    public static final int API_NAME_REGISTER_TRIGGER = 16;
    public static final int API_NAME_REGISTER_WEB_SOURCE = 17;
    public static final int API_NAME_REGISTER_WEB_TRIGGER = 18;
    public static final int API_NAME_GET_MEASUREMENT_API_STATUS = 19;
    public static final int API_NAME_GET_TOPICS_PREVIEW_API = 20;
    public static final int API_NAME_SELECT_ADS_FROM_OUTCOMES = 21;
    public static final int API_NAME_SET_APP_INSTALL_ADVERTISERS = 22;
    public static final int API_NAME_REPORT_INTERACTION = 23;
    public static final int API_NAME_UPDATE_AD_COUNTER_HISTOGRAM = 24;
    public static final int API_NAME_FETCH_AND_JOIN_CUSTOM_AUDIENCE = 25;
    public static final int API_NAME_REGISTER_SOURCES = 26;
    public static final int API_NAME_GET_AD_SERVICES_EXT_DATA = 27;
    public static final int API_NAME_PUT_AD_SERVICES_EXT_DATA = 28;

    /** Returns true for a successful status. */
    public static boolean isSuccess(@StatusCode int statusCode) {
        return statusCode == STATUS_SUCCESS;
    }

    /** Converts the input {@code statusCode} to an exception to be used in the callback. */
    @NonNull
    public static Exception asException(@StatusCode int statusCode) {
        switch (statusCode) {
            case STATUS_ENCRYPTION_FAILURE:
                return new IllegalArgumentException(ENCRYPTION_FAILURE_MESSAGE);
            case STATUS_INVALID_ARGUMENT:
                return new IllegalArgumentException();
            case STATUS_IO_ERROR:
                return new IOException();
            case STATUS_KILLSWITCH_ENABLED: // Intentional fallthrough
            case STATUS_USER_CONSENT_NOTIFICATION_NOT_DISPLAYED_YET: // Intentional fallthrough
            case STATUS_USER_CONSENT_REVOKED: // Intentional fallthrough
            case STATUS_JS_SANDBOX_UNAVAILABLE:
                return new IllegalStateException(ILLEGAL_STATE_EXCEPTION_ERROR_MESSAGE);
            case STATUS_PERMISSION_NOT_REQUESTED:
                return new SecurityException(
                        SECURITY_EXCEPTION_PERMISSION_NOT_REQUESTED_ERROR_MESSAGE);
            case STATUS_CALLER_NOT_ALLOWED:
                return new SecurityException(SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);
            case STATUS_BACKGROUND_CALLER:
                return new IllegalStateException(ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);
            case STATUS_UNAUTHORIZED:
                return new SecurityException(
                        SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE);
            case STATUS_TIMEOUT:
                return new TimeoutException(TIMED_OUT_ERROR_MESSAGE);
            case STATUS_RATE_LIMIT_REACHED:
                return new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE);
            case STATUS_INVALID_OBJECT:
                return new InvalidObjectException(INVALID_OBJECT_ERROR_MESSAGE);
            case STATUS_SERVER_RATE_LIMIT_REACHED:
                return new LimitExceededException(SERVER_RATE_LIMIT_REACHED_ERROR_MESSAGE);
            default:
                return new IllegalStateException();
        }
    }

    /** Converts the {@link AdServicesResponse} to an exception to be used in the callback. */
    @NonNull
    public static Exception asException(@NonNull AdServicesResponse adServicesResponse) {
        return asException(adServicesResponse.getStatusCode());
    }

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
                STATUS_RATE_LIMIT_REACHED,
                STATUS_UNKNOWN_ERROR,
                STATUS_IO_ERROR,
                STATUS_KILLSWITCH_ENABLED,
                STATUS_USER_CONSENT_REVOKED,
                STATUS_ADSERVICES_DISABLED,
                STATUS_PERMISSION_NOT_REQUESTED,
                STATUS_CALLER_NOT_ALLOWED,
                STATUS_BACKGROUND_CALLER,
                STATUS_UNAUTHORIZED,
                STATUS_TIMEOUT,
                STATUS_JS_SANDBOX_UNAVAILABLE,
                STATUS_INVALID_OBJECT,
                STATUS_SERVER_RATE_LIMIT_REACHED,
                STATUS_USER_CONSENT_NOTIFICATION_NOT_DISPLAYED_YET,
                STATUS_ENCRYPTION_FAILURE
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface StatusCode {}

    /**
     * Api name codes used for app name api error logging.
     *
     * @hide
     */
    @IntDef(
            value = {
                API_NAME_GET_TOPICS,
                API_NAME_JOIN_CUSTOM_AUDIENCE,
                API_NAME_LEAVE_CUSTOM_AUDIENCE,
                API_NAME_SELECT_ADS,
                API_NAME_REGISTER_SOURCE,
                API_NAME_DELETE_REGISTRATIONS,
                API_NAME_REPORT_IMPRESSION,
                API_NAME_OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                API_NAME_REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE,
                API_NAME_RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES,
                API_NAME_OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO,
                API_NAME_REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE,
                API_NAME_RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES,
                API_NAME_GET_ADID,
                API_NAME_GET_APPSETID,
                API_NAME_REGISTER_TRIGGER,
                API_NAME_REGISTER_WEB_SOURCE,
                API_NAME_REGISTER_WEB_TRIGGER,
                API_NAME_GET_MEASUREMENT_API_STATUS,
                API_NAME_GET_TOPICS_PREVIEW_API,
                API_NAME_SELECT_ADS_FROM_OUTCOMES,
                API_NAME_SET_APP_INSTALL_ADVERTISERS,
                API_NAME_REPORT_INTERACTION,
                API_NAME_UPDATE_AD_COUNTER_HISTOGRAM,
                API_NAME_FETCH_AND_JOIN_CUSTOM_AUDIENCE,
                API_NAME_REGISTER_SOURCES,
                API_NAME_GET_AD_SERVICES_EXT_DATA,
                API_NAME_PUT_AD_SERVICES_EXT_DATA
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ApiNameCode {}
}
