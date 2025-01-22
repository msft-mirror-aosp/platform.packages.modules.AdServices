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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.provider.DeviceConfig.Properties;

import com.android.adservices.common.AdServicesMockitoTestCase;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Map;

/** Unit tests for {@link ProcessStableFlagsLogger}. */
public final class ProcessStableFlagsLoggerTest extends AdServicesMockitoTestCase {
    private static final long TEST_LATENCY = 1L;
    private static final String TEST_NAMESPACE = "adservices_test";

    @Mock private ProcessStableFlagsStatsdLogger mMockStatsdLogger;
    private ProcessStableFlagsLogger mSpyProcessStableFlagsLogger;

    @Before
    public void setup() {
        mSpyProcessStableFlagsLogger = spy(new ProcessStableFlagsLogger(mMockStatsdLogger));
    }

    @Test
    public void testLogAdServicesProcessRestartEvent_enabled() {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ true);

        mSpyProcessStableFlagsLogger.logAdServicesProcessRestartEvent();
        verify(mMockStatsdLogger).logAdServicesProcessRestart();
    }

    @Test
    public void testLogAdServicesProcessRestartEvent_disabled() {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ false);

        mSpyProcessStableFlagsLogger.logAdServicesProcessRestartEvent();
        verify(mMockStatsdLogger, never()).logAdServicesProcessRestart();
    }

    @Test
    public void testLogBatchReadFromDeviceConfigLatencyMs_enabled() {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ true);

        mSpyProcessStableFlagsLogger.logBatchReadFromDeviceConfigLatencyMs(TEST_LATENCY);
        verify(mMockStatsdLogger).logBatchReadFromDeviceConfigLatencyMs(TEST_LATENCY);
    }

    @Test
    public void testLogBatchReadFromDeviceConfigLatencyMs_disabled() {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ false);

        mSpyProcessStableFlagsLogger.logBatchReadFromDeviceConfigLatencyMs(TEST_LATENCY);
        verify(mMockStatsdLogger, never()).logBatchReadFromDeviceConfigLatencyMs(anyLong());
    }

    @Test
    public void testLogAdServicesProcessLowMemoryLevel_enabled() {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ true);

        mSpyProcessStableFlagsLogger.logAdServicesProcessLowMemoryLevel();
        verify(mMockStatsdLogger).logAdServicesProcessLowMemoryLevel();
    }

    @Test
    public void testLogAdServicesProcessLowMemoryLevel_disabled() {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ false);

        mSpyProcessStableFlagsLogger.logAdServicesProcessLowMemoryLevel();
        verify(mMockStatsdLogger, never()).logAdServicesProcessLowMemoryLevel();
    }

    @Test
    public void testLogAdServicesFlagsUpdateEvent_enabled_sameProperties() {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ true);

        String key1 = "key1";
        String val1 = "val1";
        Map<String, String> sameKVMap = Map.of(key1, val1);
        Properties cachedProperties = new Properties(TEST_NAMESPACE, sameKVMap);
        Properties changedProperties = new Properties(TEST_NAMESPACE, sameKVMap);

        mSpyProcessStableFlagsLogger.logAdServicesFlagsUpdateEvent(
                cachedProperties, changedProperties);
        verify(mMockStatsdLogger).logAdServicesFlagsUpdateEvent(/* numOfCacheMissFlags= */ 0);
    }

    @Test
    public void testLogAdServicesFlagsUpdateEvent_enabled_emptyProperties() {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ true);

        Properties cachedProperties = new Properties(TEST_NAMESPACE, Map.of());
        Properties changedProperties = new Properties(TEST_NAMESPACE, Map.of("key1", "val1"));

        mSpyProcessStableFlagsLogger.logAdServicesFlagsUpdateEvent(
                cachedProperties, changedProperties);
        verify(mMockStatsdLogger).logAdServicesFlagsUpdateEvent(/* numOfCacheMissFlags= */ 1);
    }

    @Test
    public void testLogAdServicesFlagsUpdateEvent_enabled_differentProperties() {
        mockIsProcessStableFlagsLoggingEnabled(/* isEnabled= */ true);

        String key1 = "key1";
        String key2 = "key2";
        String val1 = "val1";
        String val2 = "val2";
        Properties cachedProperties = new Properties(TEST_NAMESPACE, Map.of(key1, val1));
        Properties changedProperties = new Properties(TEST_NAMESPACE, Map.of(key1, val2));

        mSpyProcessStableFlagsLogger.logAdServicesFlagsUpdateEvent(
                cachedProperties, changedProperties);
        verify(mMockStatsdLogger).logAdServicesFlagsUpdateEvent(/* numOfCacheMissFlags= */ 1);

        changedProperties = new Properties(TEST_NAMESPACE, Map.of(key1, val2, key2, val2));
        mSpyProcessStableFlagsLogger.logAdServicesFlagsUpdateEvent(
                cachedProperties, changedProperties);
        verify(mMockStatsdLogger).logAdServicesFlagsUpdateEvent(/* numOfCacheMissFlags= */ 2);
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
