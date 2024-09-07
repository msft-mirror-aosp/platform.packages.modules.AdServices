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

package com.android.adservices.service.profiling;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;

@SpyStatic(AdServicesStatsLog.class)
public final class StatsdStopWatchTest extends AdServicesExtendedMockitoTestCase {
    @Test
    public void testStopForSandboxInit() {
        verifyStatsLogWrite(
                JSScriptEngineLogConstants.SANDBOX_INIT_TIME,
                AdServicesStatsLog.JSSCRIPT_ENGINE_LATENCY_REPORTED__STAT__SANDBOX_INIT);
    }

    @Test
    public void testStopForIsolateCreate() {
        verifyStatsLogWrite(
                JSScriptEngineLogConstants.ISOLATE_CREATE_TIME,
                AdServicesStatsLog.JSSCRIPT_ENGINE_LATENCY_REPORTED__STAT__ISOLATE_CREATE);
    }

    @Test
    public void testStopForJavaExecution() {
        verifyStatsLogWrite(
                JSScriptEngineLogConstants.JAVA_EXECUTION_TIME,
                AdServicesStatsLog.JSSCRIPT_ENGINE_LATENCY_REPORTED__STAT__JAVA_PROCESS_EXECUTION);
    }

    @Test
    public void testStopForWebviewExecution() {
        verifyStatsLogWrite(
                JSScriptEngineLogConstants.WEBVIEW_EXECUTION_TIME,
                AdServicesStatsLog
                        .JSSCRIPT_ENGINE_LATENCY_REPORTED__STAT__WEBVIEW_PROCESS_EXECUTION);
    }

    private void verifyStatsLogWrite(String metricName, int code) {
        StatsdStopWatch watch = new StatsdStopWatch(metricName);
        watch.stop();

        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(AdServicesStatsLog.JSSCRIPTENGINE_LATENCY_REPORTED),
                                eq(code),
                                anyLong()));
    }

    @Test
    public void testMultipleStopsSingleWrite() {
        StatsdStopWatch watch =
                new StatsdStopWatch(JSScriptEngineLogConstants.WEBVIEW_EXECUTION_TIME);
        watch.stop();
        watch.stop();

        ExtendedMockito.verify(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyLong()));
    }
}
