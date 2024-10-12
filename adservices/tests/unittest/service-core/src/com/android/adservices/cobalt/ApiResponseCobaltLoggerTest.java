/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.cobalt;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.FlagsFactory;
import com.android.cobalt.CobaltLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.verification.VerificationMode;

@SpyStatic(FlagsFactory.class)
@SpyStatic(CobaltFactory.class)
public final class ApiResponseCobaltLoggerTest extends AdServicesExtendedMockitoTestCase {
    private static final int API_RESPONSE_METRIC_ID = 6;
    private static final String APP_PACKAGE_NAME = "test.app.name";
    private static final int GET_TOPICS_API_CODE = 1;
    private static final int SUCCESS_RESULT_CODE = 0;
    private static final int SUCCESS_EVENT_CODE = 100;
    private static final int ADSERVICES_DISABLED_RESULT_CODE = 8;

    @Mock private CobaltLogger mMockCobaltLogger;

    @Before
    public void setUp() {
        mocker.mockGetFlags(mMockFlags);
    }

    @Test
    public void testGetInstance() {
        mocker.mockAllCobaltLoggingFlags(true);

        ApiResponseCobaltLogger instance = ApiResponseCobaltLogger.getInstance();
        assertThat(instance).isNotNull();

        ApiResponseCobaltLogger otherInstance = ApiResponseCobaltLogger.getInstance();
        assertThat(otherInstance).isSameInstanceAs(instance);
    }

    @Test
    public void testIsEnabled_cobaltInitializationException() {
        mocker.mockGetCobaltLoggingEnabled(true);
        mockThrowExceptionOnGetCobaltLogger();

        ApiResponseCobaltLogger logger = new ApiResponseCobaltLogger();

        assertThat(logger.isEnabled()).isFalse();
    }

    @Test
    public void testIsEnabled_cobaltLoggingDisabled() {
        mocker.mockAllCobaltLoggingFlags(false);

        ApiResponseCobaltLogger logger = new ApiResponseCobaltLogger();

        assertThat(logger.isEnabled()).isFalse();
    }

    @Test
    public void testLogResponse_cobaltLoggingDisabled() {
        mocker.mockGetCobaltLoggingEnabled(false);
        // Passing a null cobaltLogger because COBALT_LOGGING_ENABLED is false.
        ApiResponseCobaltLogger logger = new ApiResponseCobaltLogger(/* cobaltLogger */ null);

        logger.logResponse(APP_PACKAGE_NAME, GET_TOPICS_API_CODE, SUCCESS_RESULT_CODE);

        verifyLoggedEvent(APP_PACKAGE_NAME, GET_TOPICS_API_CODE, SUCCESS_RESULT_CODE, never());
    }

    @Test
    public void testLogResponse_featureEnabled() {
        mocker.mockAllCobaltLoggingFlags(true);
        ApiResponseCobaltLogger logger = new ApiResponseCobaltLogger(mMockCobaltLogger);

        logger.logResponse(APP_PACKAGE_NAME, GET_TOPICS_API_CODE, SUCCESS_RESULT_CODE);

        verifyLoggedEvent(APP_PACKAGE_NAME, GET_TOPICS_API_CODE, SUCCESS_EVENT_CODE, times(1));

        logger.logResponse(APP_PACKAGE_NAME, GET_TOPICS_API_CODE, ADSERVICES_DISABLED_RESULT_CODE);

        verifyLoggedEvent(
                APP_PACKAGE_NAME, GET_TOPICS_API_CODE, ADSERVICES_DISABLED_RESULT_CODE, times(1));
    }

    @Test
    public void testLogResponse_nullAppPackageName() {
        mocker.mockAllCobaltLoggingFlags(true);
        ApiResponseCobaltLogger logger = new ApiResponseCobaltLogger(mMockCobaltLogger);

        assertThrows(
                NullPointerException.class,
                () ->
                        logger.logResponse(
                                /* appPackageName= */ null,
                                GET_TOPICS_API_CODE,
                                SUCCESS_RESULT_CODE));
    }

    private void verifyLoggedEvent(
            String appPackageName,
            int loggedApiCode,
            int loggedResponseCode,
            VerificationMode mode) {
        verify(mMockCobaltLogger, mode)
                .logString(
                        API_RESPONSE_METRIC_ID,
                        appPackageName,
                        ImmutableList.of(loggedApiCode, loggedResponseCode));
    }

    private static void mockThrowExceptionOnGetCobaltLogger() {
        doThrow(new CobaltInitializationException())
                .when(() -> CobaltFactory.getCobaltLogger(any(), any()));
    }
}
