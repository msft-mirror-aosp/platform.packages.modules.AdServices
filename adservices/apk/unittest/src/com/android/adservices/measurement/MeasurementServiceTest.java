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
package com.android.adservices.measurement;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import android.content.Intent;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.measurement.DeleteExpiredJobService;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.service.measurement.reporting.AggregateFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.EventFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.EventReportingJobService;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Arrays;

/** Unit test for {@link com.android.adservices.measurement.MeasurementService}. */
public class MeasurementServiceTest {
    @Mock ConsentManager mMockConsentManager;
    @Mock Flags mMockFlags;
    @Mock MeasurementImpl mMockMeasurementImpl;
    @Mock EnrollmentDao mMockEnrollmentDao;

    private static final EnrollmentData ENROLLMENT =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E1")
                    .setCompanyId("1001")
                    .setSdkNames("sdk1")
                    .setAttributionSourceRegistrationUrl(Arrays.asList("https://test.com/source"))
                    .setAttributionTriggerRegistrationUrl(Arrays.asList("https://test.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://test.com"))
                    .setEncryptionKeyUrl(Arrays.asList("https://test.com/keys"))
                    .build();

    /** Setup for tests */
    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    /** Test kill switch off */
    @Test
    public void testBindableMeasurementService_killSwitchOff() {
        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(AggregateReportingJobService.class)
                        .spyStatic(AggregateFallbackReportingJobService.class)
                        .spyStatic(AttributionJobService.class)
                        .spyStatic(ConsentManager.class)
                        .spyStatic(EnrollmentDao.class)
                        .spyStatic(EventReportingJobService.class)
                        .spyStatic(EventFallbackReportingJobService.class)
                        .spyStatic(DeleteExpiredJobService.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MeasurementImpl.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        try {
            // Kill Switch off
            doReturn(false).when(mMockFlags).getMeasurementKillSwitch();

            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            ExtendedMockito.doReturn(mMockConsentManager)
                    .when(() -> ConsentManager.getInstance(any()));

            ExtendedMockito.doReturn(mMockEnrollmentDao)
                    .when(() -> EnrollmentDao.getInstance(any()));
            doReturn(ENROLLMENT)
                    .when(mMockEnrollmentDao)
                    .getEnrollmentDataFromMeasurementUrl(any());

            ExtendedMockito.doReturn(mMockMeasurementImpl)
                    .when(() -> MeasurementImpl.getInstance(any()));

            ExtendedMockito.doNothing().when(() -> AggregateReportingJobService.schedule(any()));

            ExtendedMockito.doNothing()
                    .when(() -> AggregateFallbackReportingJobService.schedule(any()));

            ExtendedMockito.doNothing().when(() -> AttributionJobService.schedule(any()));

            ExtendedMockito.doNothing().when(() -> EventReportingJobService.schedule(any()));

            ExtendedMockito.doNothing()
                    .when(() -> EventFallbackReportingJobService.schedule(any()));

            ExtendedMockito.doNothing().when(() -> DeleteExpiredJobService.schedule(any()));

            MeasurementService measurementService = new MeasurementService();
            measurementService.onCreate();
            IBinder binder = measurementService.onBind(getIntentForMeasurementService());
            assertNotNull(binder);
        } finally {
            session.finishMocking();
        }
    }

    /** Test kill switch on */
    @Test
    public void testBindableMeasurementService_killSwitchOn() {
        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession().spyStatic(FlagsFactory.class).startMocking();

        try {
            // Kill Switch on
            doReturn(true).when(mMockFlags).getMeasurementKillSwitch();

            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            MeasurementService measurementService = new MeasurementService();
            measurementService.onCreate();
            IBinder binder = measurementService.onBind(getIntentForMeasurementService());
            assertNull(binder);
        } finally {
            session.finishMocking();
        }
    }

    private Intent getIntentForMeasurementService() {
        return new Intent(ApplicationProvider.getApplicationContext(), MeasurementService.class);
    }
}
