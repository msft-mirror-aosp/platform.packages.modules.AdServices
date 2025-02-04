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

package com.android.adservices.service.stable;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.provider.DeviceConfig.Properties;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.shared.testing.AnswerSyncCallback;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Unit tests for {@link ProcessStableFlagsLogger}. */
public final class ProcessStableFlagsLoggerTest extends AdServicesMockitoTestCase {
    private static final long TEST_LATENCY = 1L;
    private static final String TEST_NAMESPACE = "adservices_test";
    private static final ExecutorService sExecutor = Executors.newCachedThreadPool();

    @Mock private ProcessStableFlagsStatsdLogger mMockStatsdLogger;
    private ProcessStableFlagsLogger mSpyProcessStableFlagsLogger;

    @Before
    public void setup() {
        mSpyProcessStableFlagsLogger =
                spy(new ProcessStableFlagsLogger(mMockStatsdLogger, sExecutor));
    }

    @Test
    public void testLogAdServicesProcessRestartEvent_enabled() throws Exception {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ true);

        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback).when(mMockStatsdLogger).logAdServicesProcessRestart();

        mSpyProcessStableFlagsLogger.logAdServicesProcessRestartEvent();

        callback.assertCalled();
    }

    @Test
    public void testLogAdServicesProcessRestartEvent_disabled() {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ false);

        mSpyProcessStableFlagsLogger.logAdServicesProcessRestartEvent();
        verify(mMockStatsdLogger, never()).logAdServicesProcessRestart();
    }

    @Test
    public void testLogBatchReadFromDeviceConfigLatencyMs_enabled() throws Exception {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ true);

        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback)
                .when(mMockStatsdLogger)
                .logBatchReadFromDeviceConfigLatencyMs(TEST_LATENCY);

        mSpyProcessStableFlagsLogger.logBatchReadFromDeviceConfigLatencyMs(TEST_LATENCY);

        callback.assertCalled();
    }

    @Test
    public void testLogBatchReadFromDeviceConfigLatencyMs_disabled() {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ false);

        mSpyProcessStableFlagsLogger.logBatchReadFromDeviceConfigLatencyMs(TEST_LATENCY);
        verify(mMockStatsdLogger, never()).logBatchReadFromDeviceConfigLatencyMs(anyLong());
    }

    @Test
    public void testLogAdServicesProcessLowMemoryLevel_enabled() throws Exception {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ true);

        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback).when(mMockStatsdLogger).logAdServicesProcessLowMemoryLevel();

        mSpyProcessStableFlagsLogger.logAdServicesProcessLowMemoryLevel();

        callback.assertCalled();
    }

    @Test
    public void testLogAdServicesProcessLowMemoryLevel_disabled() {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ false);

        mSpyProcessStableFlagsLogger.logAdServicesProcessLowMemoryLevel();
        verify(mMockStatsdLogger, never()).logAdServicesProcessLowMemoryLevel();
    }

    @Test
    public void testLogAdServicesFlagsUpdateEvent_enabled_sameProperties() throws Exception {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ true);

        String key1 = "key1";
        String val1 = "val1";
        Map<String, String> sameKVMap = Map.of(key1, val1);
        Properties cachedProperties = new Properties(TEST_NAMESPACE, sameKVMap);
        Properties changedProperties = new Properties(TEST_NAMESPACE, sameKVMap);

        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback)
                .when(mMockStatsdLogger)
                .logAdServicesFlagsUpdateEvent(/* numOfCacheMissFlags= */ 0);

        mSpyProcessStableFlagsLogger.logAdServicesFlagsUpdateEvent(
                cachedProperties, changedProperties);

        callback.assertCalled();
    }

    @Test
    public void testLogAdServicesFlagsUpdateEvent_enabled_emptyProperties() throws Exception {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ true);

        Properties cachedProperties = new Properties(TEST_NAMESPACE, Map.of());
        Properties changedProperties = new Properties(TEST_NAMESPACE, Map.of("key1", "val1"));

        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback)
                .when(mMockStatsdLogger)
                .logAdServicesFlagsUpdateEvent(/* numOfCacheMissFlags= */ 1);

        mSpyProcessStableFlagsLogger.logAdServicesFlagsUpdateEvent(
                cachedProperties, changedProperties);

        callback.assertCalled();
    }

    @Test
    public void testLogAdServicesFlagsUpdateEvent_enabled_differentProperties() throws Exception {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ true);

        String key1 = "key1";
        String key2 = "key2";
        String val1 = "val1";
        String val2 = "val2";
        Properties cachedProperties = new Properties(TEST_NAMESPACE, Map.of(key1, val1));
        Properties changedProperties = new Properties(TEST_NAMESPACE, Map.of(key1, val2));

        AnswerSyncCallback<Void> callback1 = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback1)
                .when(mMockStatsdLogger)
                .logAdServicesFlagsUpdateEvent(/* numOfCacheMissFlags= */ 1);

        mSpyProcessStableFlagsLogger.logAdServicesFlagsUpdateEvent(
                cachedProperties, changedProperties);

        callback1.assertCalled();

        changedProperties = new Properties(TEST_NAMESPACE, Map.of(key1, val2, key2, val2));
        AnswerSyncCallback<Void> callback2 = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback2)
                .when(mMockStatsdLogger)
                .logAdServicesFlagsUpdateEvent(/* numOfCacheMissFlags= */ 2);

        mSpyProcessStableFlagsLogger.logAdServicesFlagsUpdateEvent(
                cachedProperties, changedProperties);

        callback2.assertCalled();
    }

    @Test
    public void testLogAdServicesFlagsUpdateEvent_disabled() {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ false);

        Properties cachedProperties = new Properties(TEST_NAMESPACE, Map.of());
        Properties changedProperties = new Properties(TEST_NAMESPACE, Map.of());

        mSpyProcessStableFlagsLogger.logAdServicesFlagsUpdateEvent(
                cachedProperties, changedProperties);
        verify(mMockStatsdLogger, never()).logAdServicesFlagsUpdateEvent(anyInt());
    }

    private void mockIsProcessStableFlagsLoggingEnabled(boolean isEnabled) {
        doReturn(isEnabled).when(mSpyProcessStableFlagsLogger).isProcessStableFlagsLoggingEnabled();
    }
}
