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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.cobalt.CobaltLogger;
import com.android.cobalt.domain.Project;

import com.google.cobalt.MetricDefinition;
import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Map;

public final class AppNameApiErrorLoggerTest extends AdServicesMockitoTestCase {
    private static final int METRIC_ID = 2;
    private static final int FIRST_API_NAME_CODE = 1;
    private static final int LAST_API_NAME_CODE = 28;
    private static final int FIRST_ERROR_CODE = 1;
    private static final int LAST_ERROR_CODE = 19;
    private static final int UNKNOWN_EVENT_CODE = 0;
    private static final String APP_PACKAGE_NAME = "test.app.name";

    private AppNameApiErrorLogger mAppNameApiErrorLogger;

    @Mock private CobaltLogger mMockCobaltLogger;

    @Before
    public void setUp() {
        mAppNameApiErrorLogger = new AppNameApiErrorLogger(mMockCobaltLogger);
        mockLogErrorOccurrence();
    }

    @Test
    public void testLogErrorOccurrence_nullAppPackageName() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mAppNameApiErrorLogger.logErrorOccurrence(
                                /* appPackageName= */ null, FIRST_API_NAME_CODE, FIRST_ERROR_CODE));
    }

    @Test
    public void testLogErrorOccurrence_apiCodeBelowLimit() {
        mAppNameApiErrorLogger.logErrorOccurrence(
                APP_PACKAGE_NAME, FIRST_ERROR_CODE - 1, FIRST_ERROR_CODE);

        verifyLoggedEvent(APP_PACKAGE_NAME, UNKNOWN_EVENT_CODE, FIRST_ERROR_CODE);
    }

    @Test
    public void testLogErrorOccurrence_apiCodeOverLimit() {
        mAppNameApiErrorLogger.logErrorOccurrence(
                APP_PACKAGE_NAME, LAST_API_NAME_CODE + 1, LAST_ERROR_CODE);

        verifyLoggedEvent(APP_PACKAGE_NAME, UNKNOWN_EVENT_CODE, LAST_ERROR_CODE);
    }

    @Test
    public void testLogErrorOccurrence_errorCodeBelowLimit() {
        mAppNameApiErrorLogger.logErrorOccurrence(
                APP_PACKAGE_NAME, FIRST_API_NAME_CODE, FIRST_ERROR_CODE - 1);

        verifyLoggedEvent(APP_PACKAGE_NAME, FIRST_API_NAME_CODE, UNKNOWN_EVENT_CODE);
    }

    @Test
    public void testLogErrorOccurrence_errorCodeOverLimit() {
        mAppNameApiErrorLogger.logErrorOccurrence(
                APP_PACKAGE_NAME, LAST_API_NAME_CODE, LAST_ERROR_CODE + 1);

        verifyLoggedEvent(APP_PACKAGE_NAME, LAST_API_NAME_CODE, UNKNOWN_EVENT_CODE);
    }

    @Test
    public void testLogErrorOccurrence_correctValue() {
        mAppNameApiErrorLogger.logErrorOccurrence(
                APP_PACKAGE_NAME, FIRST_API_NAME_CODE, LAST_ERROR_CODE);

        verifyLoggedEvent(APP_PACKAGE_NAME, FIRST_API_NAME_CODE, LAST_ERROR_CODE);
    }

    @Test
    public void testExpectationsMatchRegistryValues() throws Exception {
        // Parse the actual Cobalt registry for AdServices to ensure to app name api error logger's
        // assumptions are compatible with what is actually in the registry.
        //
        // See
        // //packages/modules/AdServices/adservices/service-core/resources/cobalt_registry.textpb
        // for the actual registry.
        Project cobaltRegistry = CobaltRegistryLoader.getRegistry(sContext);
        MetricDefinition appNameApiErrorMetric =
                cobaltRegistry.getMetrics().stream()
                        .filter(m -> m.getMetricName().equals("per_package_api_errors"))
                        .findFirst()
                        .orElseThrow();
        assertWithMessage("getMetricDimensionsCount()")
                .that(appNameApiErrorMetric.getMetricDimensionsCount())
                .isEqualTo(2);
        expect.withMessage("getMetricDimensions(0).getDimension()")
                .that(appNameApiErrorMetric.getMetricDimensions(0).getDimension())
                .isEqualTo("api");
        expect.withMessage("getId()").that(appNameApiErrorMetric.getId()).isEqualTo(METRIC_ID);
        Map<Integer, String> eventCodes =
                appNameApiErrorMetric.getMetricDimensions(0).getEventCodes();
        int unknownApiEventCode =
                eventCodes.entrySet().stream()
                        .filter(e -> e.getValue().equals("UNKNOWN"))
                        .map(e -> e.getKey())
                        .findFirst()
                        .orElseThrow();
        int firstApiEventCode =
                eventCodes.entrySet().stream()
                        .mapToInt(Map.Entry::getKey)
                        .filter(i -> i != UNKNOWN_EVENT_CODE)
                        .min()
                        .orElseThrow();
        expect.withMessage("unknownApiEventCode in appNameApiErrorMetric")
                .that(unknownApiEventCode)
                .isEqualTo(UNKNOWN_EVENT_CODE);
        expect.withMessage("firstApiEventCode in appNameApiErrorMetric")
                .that(firstApiEventCode)
                .isEqualTo(FIRST_API_NAME_CODE);

        expect.withMessage("getMetricDimensions(1).getDimension()")
                .that(appNameApiErrorMetric.getMetricDimensions(1).getDimension())
                .isEqualTo("error");
        eventCodes = appNameApiErrorMetric.getMetricDimensions(1).getEventCodes();
        int unknownErrorEventCode =
                eventCodes.entrySet().stream()
                        .filter(e -> e.getValue().equals("UNKNOWN"))
                        .map(e -> e.getKey())
                        .findFirst()
                        .orElseThrow();
        int firstErrorEventCode =
                eventCodes.entrySet().stream()
                        .mapToInt(Map.Entry::getKey)
                        .filter(i -> i != UNKNOWN_EVENT_CODE)
                        .min()
                        .orElseThrow();
        expect.withMessage("unknownErrorEventCode in appNameApiErrorMetric")
                .that(unknownErrorEventCode)
                .isEqualTo(UNKNOWN_EVENT_CODE);
        expect.withMessage("firstErrorEventCode in appNameApiErrorMetric")
                .that(firstErrorEventCode)
                .isEqualTo(FIRST_ERROR_CODE);
    }

    private void mockLogErrorOccurrence() {
        when(mMockCobaltLogger.logString(anyLong(), anyString(), any())).thenReturn(null);
    }

    private void verifyLoggedEvent(String appPackageName, int loggedApiCode, int loggedErrorCode) {
        verify(mMockCobaltLogger)
                .logString(
                        METRIC_ID,
                        appPackageName,
                        ImmutableList.of(loggedApiCode, loggedErrorCode));
    }
}