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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED__COBALT_LOGGING_EVENT__LOGGING_EVENT_OVER_EVENT_VECTOR_BUFFER_MAX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED__COBALT_LOGGING_EVENT__LOGGING_EVENT_OVER_MAX_VALUE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED__COBALT_LOGGING_EVENT__LOGGING_EVENT_OVER_STRING_BUFFER_MAX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_COBALT_PERIODIC_JOB_EVENT_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_COBALT_PERIODIC_JOB_EVENT_REPORTED__COBALT_PERIODIC_JOB_EVENT__UPLOAD_EVENT_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_COBALT_PERIODIC_JOB_EVENT_REPORTED__COBALT_PERIODIC_JOB_EVENT__UPLOAD_EVENT_SUCCESS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.dx.mockito.inline.extended.MockedVoidMethod;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;

@SpyStatic(AdServicesStatsLog.class)
public final class CobaltOperationLoggerImplTest extends AdServicesExtendedMockitoTestCase {
    private static final int TEST_METRIC_ID = 1;
    private static final int TEST_REPORT_ID = 2;

    private CobaltOperationLoggerImpl mLogger;
    private CobaltOperationLoggerImpl mDisabledLogger;

    @Before
    public void setUp() {
        mLogger = new CobaltOperationLoggerImpl(/* enabled= */ true);
        mDisabledLogger = new CobaltOperationLoggerImpl(/* enabled= */ false);
    }

    @Test
    public void testLogStringBufferMaxExceeded_success() {
        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyInt(), anyInt()));

        // Invoke logging call
        mLogger.logStringBufferMaxExceeded(TEST_METRIC_ID, TEST_REPORT_ID);

        // Verify correct value are logged
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED),
                                eq(TEST_METRIC_ID),
                                eq(TEST_REPORT_ID),
                                eq(
                                        AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED__COBALT_LOGGING_EVENT__LOGGING_EVENT_OVER_STRING_BUFFER_MAX));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogStringBufferMaxExceeded_noOpWhenLoggerDisabled() {
        // Invoke logging call
        mDisabledLogger.logStringBufferMaxExceeded(TEST_METRIC_ID, TEST_REPORT_ID);

        // Verify No Op
        verifyZeroInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogEventVectorBufferMaxExceeded_success() {
        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyInt(), anyInt()));

        // Invoke logging call
        mLogger.logEventVectorBufferMaxExceeded(TEST_METRIC_ID, TEST_REPORT_ID);

        // Verify correct value are logged
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED),
                                eq(TEST_METRIC_ID),
                                eq(TEST_REPORT_ID),
                                eq(
                                        AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED__COBALT_LOGGING_EVENT__LOGGING_EVENT_OVER_EVENT_VECTOR_BUFFER_MAX));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogEventVectorBufferMaxExceeded_noOpWhenLoggerDisabled() {
        // Invoke logging call
        mDisabledLogger.logEventVectorBufferMaxExceeded(TEST_METRIC_ID, TEST_REPORT_ID);

        // Verify No Op
        verifyZeroInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogMaxValueExceeded_success() {
        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyInt(), anyInt()));

        // Invoke logging call
        mLogger.logMaxValueExceeded(TEST_METRIC_ID, TEST_REPORT_ID);

        // Verify correct value are logged
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED),
                                eq(TEST_METRIC_ID),
                                eq(TEST_REPORT_ID),
                                eq(
                                        AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED__COBALT_LOGGING_EVENT__LOGGING_EVENT_OVER_MAX_VALUE));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogMaxValueExceeded_noOpWhenLoggerDisabled() {
        // Invoke logging call
        mDisabledLogger.logMaxValueExceeded(TEST_METRIC_ID, TEST_REPORT_ID);

        // Verify No Op
        verifyZeroInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogUploadFailure_success() {
        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyInt()));

        // Invoke logging call
        mLogger.logUploadFailure();

        // Verify correct value are logged
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_COBALT_PERIODIC_JOB_EVENT_REPORTED),
                                eq(
                                        AD_SERVICES_COBALT_PERIODIC_JOB_EVENT_REPORTED__COBALT_PERIODIC_JOB_EVENT__UPLOAD_EVENT_FAILURE));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogUploadFailure_noOpWhenLoggerDisabled() {
        // Invoke logging call
        mDisabledLogger.logUploadFailure();

        // Verify No Op
        verifyZeroInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogUploadSuccess_success() {
        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyInt()));

        // Invoke logging call
        mLogger.logUploadSuccess();

        // Verify correct value are logged
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_COBALT_PERIODIC_JOB_EVENT_REPORTED),
                                eq(
                                        AD_SERVICES_COBALT_PERIODIC_JOB_EVENT_REPORTED__COBALT_PERIODIC_JOB_EVENT__UPLOAD_EVENT_SUCCESS));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogUploadSuccess_noOpWhenLoggerDisabled() {
        // Invoke logging call
        mDisabledLogger.logUploadSuccess();

        // Verify No Op
        verifyZeroInteractions(staticMockMarker(AdServicesStatsLog.class));
    }
}
