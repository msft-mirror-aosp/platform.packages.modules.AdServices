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

import static android.adservices.exceptions.AdServicesNetworkException.ERROR_TOO_MANY_REQUESTS;
import static android.adservices.exceptions.RetryableAdServicesNetworkException.INVALID_ERROR_CODE_MESSAGE;
import static android.adservices.exceptions.RetryableAdServicesNetworkException.INVALID_RETRY_AFTER_MESSAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.util.Locale.ENGLISH;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

// TODO(b/278016822): Move to CTS tests once public APIs are unhidden
@SmallTest
public class RetryableAdServicesNetworkExceptionTest {
    private static final int VALID_ERROR_CODE = ERROR_TOO_MANY_REQUESTS;
    private static final int INVALID_ERROR_CODE = 1000;
    private static final Duration VALID_RETRY_AFTER = Duration.of(1000, ChronoUnit.MILLIS);
    private static final Duration UNSET_RETRY_AFTER = Duration.ZERO;
    private static final Duration NEGATIVE_RETRY_AFTER = Duration.of(-1, ChronoUnit.MILLIS);

    @Test
    public void testExceptionWithErrorCodeAndRetryAfter_valid() {
        RetryableAdServicesNetworkException exception =
                new RetryableAdServicesNetworkException(VALID_ERROR_CODE, VALID_RETRY_AFTER);

        assertThat(exception.getErrorCode()).isEqualTo(VALID_ERROR_CODE);
        assertThat(exception.getRetryAfter()).isEqualTo(VALID_RETRY_AFTER);
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.toString())
                .isEqualTo(
                        getHumanReadableRetryableNetworkException(
                                VALID_ERROR_CODE, VALID_RETRY_AFTER));
    }

    @Test
    public void testExceptionWithErrorCodeAndRetryAfter_errorCodeInvalid() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new RetryableAdServicesNetworkException(
                                        INVALID_ERROR_CODE, VALID_RETRY_AFTER));
        assertThat(exception.getMessage()).isEqualTo(INVALID_ERROR_CODE_MESSAGE);
    }

    @Test
    public void testExceptionWithErrorCodeAndRetryAfter_retryAfterUnset() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new RetryableAdServicesNetworkException(
                                        VALID_ERROR_CODE, UNSET_RETRY_AFTER));
        assertThat(exception.getMessage()).isEqualTo(INVALID_RETRY_AFTER_MESSAGE);
    }

    @Test
    public void testExceptionWithErrorCodeAndRetryAfter_retryAfterNegative() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new RetryableAdServicesNetworkException(
                                        VALID_ERROR_CODE, NEGATIVE_RETRY_AFTER));
        assertThat(exception.getMessage()).isEqualTo(INVALID_RETRY_AFTER_MESSAGE);
    }

    private String getHumanReadableRetryableNetworkException(int errorCode, Duration retryAfter) {
        return String.format(
                ENGLISH,
                "%s: {Error code: %s, Retry after: %sms}",
                RetryableAdServicesNetworkException.class.getCanonicalName(),
                errorCode,
                retryAfter.toMillis());
    }
}
