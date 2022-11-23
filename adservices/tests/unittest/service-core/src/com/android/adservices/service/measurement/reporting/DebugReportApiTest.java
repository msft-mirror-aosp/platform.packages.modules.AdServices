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
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link DebugReport} */
@SmallTest
public final class DebugReportApiTest {

    private static final String TYPE = "trigger-event-deduplicated";
    private static final String BODY_KEY_1 = "attribution_destination";
    private static final String BODY_VALUE_1 = "https://destination.example";
    private static final String BODY_KEY_2 = "source_event_id";
    private static final String BODY_VALUE_2 = "936731";
    private static final String ENROLLMENT_ID = "E1";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private DebugReportApi mDebugReportApi;
    @Mock private ITransaction mTransaction;
    @Mock private IMeasurementDao mMeasurementDao;

    class FakeDatastoreManager extends DatastoreManager {

        @Override
        public ITransaction createNewTransaction() {
            return mTransaction;
        }

        @Override
        public IMeasurementDao getMeasurementDao() {
            return mMeasurementDao;
        }
    }

    @Before
    public void setup() {
        mDebugReportApi = new DebugReportApi(mContext, new FakeDatastoreManager());
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testScheduleReport_success() throws Exception {
        runWithMocks(
                () -> {
                    Map<String, String> body = new HashMap<>();
                    body.put(BODY_KEY_1, BODY_VALUE_1);
                    body.put(BODY_KEY_2, BODY_VALUE_2);
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleReport(
                            TYPE, body, ENROLLMENT_ID, /*isAdTechOptIn=*/ true);
                    verify(mMeasurementDao, times(1)).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleReport_without_enrollmentId_dontSchedule() throws Exception {
        runWithMocks(
                () -> {
                    Map<String, String> body = new HashMap<>();
                    body.put(BODY_KEY_1, BODY_VALUE_1);
                    body.put(BODY_KEY_2, BODY_VALUE_2);
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleReport(
                            TYPE, body, /*enrollmentId*/ "", /*isAdTechOptIn=*/ true);
                    verify(mMeasurementDao, never()).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleReport_without_adTechOptIn_dontSchedule() throws Exception {
        runWithMocks(
                () -> {
                    Map<String, String> body = new HashMap<>();
                    body.put(BODY_KEY_1, BODY_VALUE_1);
                    body.put(BODY_KEY_2, BODY_VALUE_2);
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleReport(
                            TYPE, body, ENROLLMENT_ID, /*isAdTechOptIn=*/ false);
                    verify(mMeasurementDao, never()).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleReport_empty_reportType_dontSchedule() throws Exception {
        runWithMocks(
                () -> {
                    Map<String, String> body = new HashMap<>();
                    body.put(BODY_KEY_1, BODY_VALUE_1);
                    body.put(BODY_KEY_2, BODY_VALUE_2);
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleReport(
                            /*type=*/ "", body, ENROLLMENT_ID, /*isAdTechOptIn=*/ false);
                    verify(mMeasurementDao, never()).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleReport_empty_reportBody_dontSchedule() throws Exception {
        runWithMocks(
                () -> {
                    Map<String, String> body = new HashMap<>();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleReport(
                            TYPE, body, ENROLLMENT_ID, /*isAdTechOptIn=*/ true);
                    verify(mMeasurementDao, never()).insertDebugReport(any());
                });
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(DebugReportingJobService.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            ExtendedMockito.doNothing()
                    .when(() -> DebugReportingJobService.schedule(any(), any(), anyBoolean()));

            // Execute
            execute.run();
        } finally {
            session.finishMocking();
        }
    }
}
