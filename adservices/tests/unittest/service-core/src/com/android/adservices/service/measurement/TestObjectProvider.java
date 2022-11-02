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

package com.android.adservices.service.measurement;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.annotation.IntDef;
import android.test.mock.MockContentResolver;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.deletion.MeasurementDataDeleter;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.attribution.AttributionJobHandlerWrapper;
import com.android.adservices.service.measurement.inputverification.ClickVerifier;
import com.android.adservices.service.measurement.registration.AsyncSourceFetcher;
import com.android.adservices.service.measurement.registration.AsyncTriggerFetcher;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.mockito.stubbing.Answer;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

class TestObjectProvider {
    private static final long ONE_HOUR_IN_MILLIS = TimeUnit.HOURS.toMillis(1);

    @IntDef(value = {
            Type.DENOISED,
            Type.NOISY,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface Type {
        int DENOISED = 1;
        int NOISY = 2;
    }

    static AttributionJobHandlerWrapper getAttributionJobHandler(
            DatastoreManager datastoreManager) {
        return new AttributionJobHandlerWrapper(datastoreManager);
    }

    static MeasurementImpl getMeasurementImpl(
            DatastoreManager datastoreManager,
            ClickVerifier clickVerifier,
            Flags flags,
            MeasurementDataDeleter measurementDataDeleter,
            EnrollmentDao enrollmentDao) {
        return spy(
                new MeasurementImpl(
                        null,
                        datastoreManager,
                        clickVerifier,
                        measurementDataDeleter,
                        enrollmentDao));
    }

    static AsyncRegistrationQueueRunner getAsyncRegistrationQueueRunner(
            @Type int type,
            DatastoreManager datastoreManager,
            AsyncSourceFetcher asyncSourceFetcher,
            AsyncTriggerFetcher asyncTriggerFetcher,
            EnrollmentDao enrollmentDao) {
        if (type == Type.DENOISED) {
            AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                    spy(
                            new AsyncRegistrationQueueRunner(
                                    new MockContentResolver(),
                                    asyncSourceFetcher,
                                    asyncTriggerFetcher,
                                    enrollmentDao,
                                    datastoreManager));
            // Disable Impression Noise
            doReturn(Collections.emptyList())
                    .when(asyncRegistrationQueueRunner)
                    .generateFakeEventReports(any());
            return asyncRegistrationQueueRunner;
        } else if (type == Type.NOISY) {
            AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                    spy(
                            new AsyncRegistrationQueueRunner(
                                    new MockContentResolver(),
                                    asyncSourceFetcher,
                                    asyncTriggerFetcher,
                                    enrollmentDao,
                                    datastoreManager));
            // Create impression noise with 100% probability
            Answer<?> answerSourceEventReports =
                    invocation -> {
                        Source source = invocation.getArgument(0);
                        source.setAttributionMode(Source.AttributionMode.FALSELY);
                        return Collections.singletonList(
                                new EventReport.Builder()
                                        .setSourceEventId(source.getEventId())
                                        .setReportTime(source.getExpiryTime() + ONE_HOUR_IN_MILLIS)
                                        .setTriggerData(new UnsignedLong(0L))
                                        .setAttributionDestination(source.getAppDestination())
                                        .setEnrollmentId(source.getEnrollmentId())
                                        .setTriggerTime(0)
                                        .setTriggerPriority(0L)
                                        .setTriggerDedupKey(null)
                                        .setSourceType(source.getSourceType())
                                        .setStatus(EventReport.Status.PENDING)
                                        .build());
                    };
            doAnswer(answerSourceEventReports)
                    .when(asyncRegistrationQueueRunner)
                    .generateFakeEventReports(any());
            return asyncRegistrationQueueRunner;
        }

        return new AsyncRegistrationQueueRunner(
                new MockContentResolver(),
                asyncSourceFetcher,
                asyncTriggerFetcher,
                enrollmentDao,
                datastoreManager);
    }
}
