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

package com.android.adservices.service.measurement.reporting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import android.app.job.JobParameters;
import android.content.Context;
import android.provider.DeviceConfig;

import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

/**
 * Unit test for {@link AggregateFallbackReportingJobService
 */
public class AggregateFallbackReportingJobServiceTest {
    // This rule is used for configuring P/H flags
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    @Test
    public void onStartJob_killSwitchOn() throws Exception {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_job_aggregate_fallback_reporting_kill_switch",
                Boolean.toString(true),
                /* makeDefault */ false);

        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(DatastoreManagerFactory.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            DatastoreManager mockDatastoreManager = ExtendedMockito.mock(DatastoreManager.class);
            ExtendedMockito.doReturn(mockDatastoreManager)
                    .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));

            AggregateFallbackReportingJobService service =
                    new AggregateFallbackReportingJobService();
            JobParameters mockJobParameters = Mockito.mock(JobParameters.class);
            boolean result = service.onStartJob(mockJobParameters);

            Assert.assertFalse(result);

            // Allow background thread to execute
            Thread.sleep(50);
            ExtendedMockito.verify(mockDatastoreManager, never()).runInTransaction(any());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void onStartJob_killSwitchOff() throws Exception {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_job_aggregate_fallback_reporting_kill_switch",
                Boolean.toString(false),
                /* makeDefault */ false);

        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(DatastoreManagerFactory.class)
                        .startMocking();
        try {
            DatastoreManager mockDatastoreManager = ExtendedMockito.mock(DatastoreManager.class);
            ExtendedMockito.doReturn(mockDatastoreManager)
                    .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));
            AggregateFallbackReportingJobService spyService =
                    Mockito.spy(new AggregateFallbackReportingJobService());
            ExtendedMockito.doNothing().when(spyService).jobFinished(any(), anyBoolean());
            ExtendedMockito.doReturn(Mockito.mock(Context.class))
                    .when(spyService)
                    .getApplicationContext();
            JobParameters mockJobParameters = Mockito.mock(JobParameters.class);
            boolean result = spyService.onStartJob(mockJobParameters);

            Assert.assertTrue(result);
            // Allow background thread to execute
            Thread.sleep(50);
            ExtendedMockito.verify(spyService, times(1)).jobFinished(any(), anyBoolean());
        } finally {
            session.finishMocking();
        }
    }
}
