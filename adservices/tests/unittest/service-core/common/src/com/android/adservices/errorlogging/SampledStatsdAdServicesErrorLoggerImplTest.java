/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.metriclogger.CelMetricLogger;
import com.android.adservices.shared.errorlogging.AdServicesErrorStats;
import com.android.adservices.shared.errorlogging.StatsdAdServicesErrorLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@SpyStatic(CelMetricLogger.class)
public final class SampledStatsdAdServicesErrorLoggerImplTest
        extends AdServicesExtendedMockitoTestCase {

    @Mock private CelMetricLogger mMockCelMetricLogger;

    @Before
    public void setup() {
        doReturn(mMockCelMetricLogger).when(CelMetricLogger::get);
    }

    @Test
    public void logAdServicesError_logSamplingInfraFlagEnabled() {
        String className = "TopicsService";
        String methodName = "getTopics";
        int lineNumber = 100;
        String exceptionName = "SQLiteException";
        AdServicesErrorStats stats =
                AdServicesErrorStats.builder()
                        .setErrorCode(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION)
                        .setPpapiName(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS)
                        .setClassName(className)
                        .setMethodName(methodName)
                        .setLineNumber(lineNumber)
                        .setLastObservedExceptionName(exceptionName)
                        .build();

        StatsdAdServicesErrorLogger logger = SampledStatsdAdServicesErrorLoggerImpl.getInstance();
        logger.logAdServicesError(stats);

        verify(mMockCelMetricLogger).log(stats);
    }
}
