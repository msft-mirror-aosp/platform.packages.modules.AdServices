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

package android.adservices.exceptions;

import static android.adservices.exceptions.AdServicesNetworkException.ERROR_OTHER;
import static android.adservices.exceptions.AdServicesNetworkException.INVALID_ERROR_CODE_MESSAGE;
import static android.adservices.exceptions.AdServicesNetworkException.INVALID_RETRY_AFTER_MESSAGE;
import static android.adservices.exceptions.AdServicesNetworkException.UNSET_RETRY_AFTER_VALUE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.util.Locale.ENGLISH;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

// TODO(b/278016822): Move to CTS tests once public APIs are unhidden
@SmallTest
public class AdServicesNetworkExceptionTest {
    private final String mServerResponse = "Example of a server response.";
    private final int mValidErrorCode = ERROR_OTHER;
    private final int mInvalidErrorCode = 1000;
    private final Duration mValidRetryAfter = Duration.of(1000, ChronoUnit.MILLIS);
    private final Duration mUnsetRetryAfter = Duration.ZERO;
    private final Duration mNegativeRetryAfter = Duration.of(-1, ChronoUnit.MILLIS);

    @Test
    public void testExceptionWithErrorCode_valid() {
        AdServicesNetworkException exception = new AdServicesNetworkException(mValidErrorCode);

        assertThat(exception.getErrorCode()).isEqualTo(mValidErrorCode);
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.getRetryAfter()).isEqualTo(UNSET_RETRY_AFTER_VALUE);
        assertThat(exception.toString())
                .isEqualTo(
                        getHumanReadableAdServicesNetworkException(
                                mValidErrorCode, UNSET_RETRY_AFTER_VALUE, null));
    }

    @Test
    public void testExceptionWithErrorCode_errorCodeInvalid() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new AdServicesNetworkException(mInvalidErrorCode));
        assertThat(exception.getMessage()).isEqualTo(INVALID_ERROR_CODE_MESSAGE);
    }

    @Test
    public void testExceptionWithErrorCodeAndServerResponse_valid() {
        AdServicesNetworkException exception =
                new AdServicesNetworkException(mValidErrorCode, mServerResponse);

        assertThat(exception.getErrorCode()).isEqualTo(mValidErrorCode);
        assertThat(exception.getMessage()).isEqualTo(mServerResponse);
        assertThat(exception.getRetryAfter()).isEqualTo(UNSET_RETRY_AFTER_VALUE);
        assertThat(exception.toString())
                .isEqualTo(
                        getHumanReadableAdServicesNetworkException(
                                mValidErrorCode, UNSET_RETRY_AFTER_VALUE, mServerResponse));
    }

    @Test
    public void testExceptionWithErrorCodeAndServerResponse_errorCodeInvalid() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new AdServicesNetworkException(mInvalidErrorCode, mServerResponse));
        assertThat(exception.getMessage()).isEqualTo(INVALID_ERROR_CODE_MESSAGE);
    }

    @Test
    public void testExceptionWithErrorCodeAndRetryAfter_valid() {
        AdServicesNetworkException exception =
                new AdServicesNetworkException(mValidErrorCode, mValidRetryAfter);

        assertThat(exception.getErrorCode()).isEqualTo(mValidErrorCode);
        assertThat(exception.getRetryAfter()).isEqualTo(mValidRetryAfter);
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.toString())
                .isEqualTo(
                        getHumanReadableAdServicesNetworkException(
                                mValidErrorCode, mValidRetryAfter, null));
    }

    @Test
    public void testExceptionWithErrorCodeAndRetryAfter_errorCodeInvalid() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new AdServicesNetworkException(mInvalidErrorCode, mValidRetryAfter));
        assertThat(exception.getMessage()).isEqualTo(INVALID_ERROR_CODE_MESSAGE);
    }

    @Test
    public void testExceptionWithErrorCodeAndRetryAfter_retryAfterUnset() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new AdServicesNetworkException(mValidErrorCode, mUnsetRetryAfter));
        assertThat(exception.getMessage()).isEqualTo(INVALID_RETRY_AFTER_MESSAGE);
    }

    @Test
    public void testExceptionWithErrorCodeAndRetryAfter_retryAfterNegative() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new AdServicesNetworkException(mValidErrorCode, mNegativeRetryAfter));
        assertThat(exception.getMessage()).isEqualTo(INVALID_RETRY_AFTER_MESSAGE);
    }

    @Test
    public void testExceptionWithErrorCodeAndRetryAfterAndServerResponse_valid() {
        AdServicesNetworkException exception =
                new AdServicesNetworkException(mValidErrorCode, mValidRetryAfter, mServerResponse);

        assertThat(exception.getErrorCode()).isEqualTo(mValidErrorCode);
        assertThat(exception.getRetryAfter()).isEqualTo(mValidRetryAfter);
        assertThat(exception.getMessage()).isEqualTo(mServerResponse);
        assertThat(exception.toString())
                .isEqualTo(
                        getHumanReadableAdServicesNetworkException(
                                mValidErrorCode, mValidRetryAfter, mServerResponse));
    }

    @Test
    public void testExceptionWithErrorCodeAndRetryAfterAndServerResponse_errorCodeInvalid() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new AdServicesNetworkException(
                                        mInvalidErrorCode, mValidRetryAfter, mServerResponse));
        assertThat(exception.getMessage()).isEqualTo(INVALID_ERROR_CODE_MESSAGE);
    }

    @Test
    public void testExceptionWithErrorCodeAndRetryAfterAndServerResponse_retryAfterUnset() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new AdServicesNetworkException(
                                        mValidErrorCode, mUnsetRetryAfter, mServerResponse));
        assertThat(exception.getMessage()).isEqualTo(INVALID_RETRY_AFTER_MESSAGE);
    }

    @Test
    public void testExceptionWithErrorCodeAndRetryAfterAndServerResponse_retryAfterNegative() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new AdServicesNetworkException(
                                        mValidErrorCode, mNegativeRetryAfter, mServerResponse));
        assertThat(exception.getMessage()).isEqualTo(INVALID_RETRY_AFTER_MESSAGE);
    }

    private String getHumanReadableAdServicesNetworkException(
            int errorCode, Duration retryAfter, String serverResponse) {
        return String.format(
                ENGLISH,
                "%s: {Error code: %s, Retry after: %sms, Server response: %s}",
                AdServicesNetworkException.class.getCanonicalName(),
                errorCode,
                retryAfter.toMillis(),
                serverResponse);
    }
}
