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
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.annotation.IntDef;
import android.test.mock.MockContentResolver;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.measurement.attribution.AttributionJobHandlerWrapper;
import com.android.adservices.service.measurement.registration.SourceFetcher;
import com.android.adservices.service.measurement.registration.TriggerFetcher;

import org.mockito.stubbing.Answer;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;
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

    static AttributionJobHandlerWrapper getAttributionJobHandler(@Type int type,
            DatastoreManager datastoreManager) throws DatastoreException {
        if (type == Type.DENOISED) {
            DatastoreManager spyDatastoreManager = spy(datastoreManager);
            // Mocking the randomized trigger data to always return the truth value.
            IMeasurementDao dao = spy(spyDatastoreManager.getMeasurementDao());
            when(spyDatastoreManager.getMeasurementDao()).thenReturn(dao);
            doAnswer((Answer<Trigger>) triggerInvocation -> {
                Trigger trigger = spy((Trigger) triggerInvocation.callRealMethod());
                doAnswer((Answer<Long>) triggerDataInvocation ->
                        trigger.getTruncatedTriggerData(
                            triggerDataInvocation.getArgument(0)))
                        .when(trigger)
                        .getRandomizedTriggerData(any());
                return trigger;
            }).when(dao).getTrigger(anyString());

            return new AttributionJobHandlerWrapper(spyDatastoreManager);
        } else if (type == Type.NOISY) {
            DatastoreManager spyDatastoreManager = spy(datastoreManager);
            // Mocking the randomized trigger data to always return the truth value.
            IMeasurementDao dao = spy(spyDatastoreManager.getMeasurementDao());
            when(spyDatastoreManager.getMeasurementDao()).thenReturn(dao);
            doAnswer((Answer<Trigger>) triggerInvocation -> {
                Trigger trigger = spy((Trigger) triggerInvocation.callRealMethod());
                doAnswer((Answer<Long>) triggerDataInvocation ->
                        // Noise at 100% probability
                        Math.abs(trigger.getTruncatedTriggerData(
                            triggerDataInvocation.getArgument(0)) - 1))
                        .when(trigger)
                        .getRandomizedTriggerData(any());
                return trigger;
            }).when(dao).getTrigger(anyString());

            return new AttributionJobHandlerWrapper(spyDatastoreManager);
        }

        return new AttributionJobHandlerWrapper(datastoreManager);
    }

    static MeasurementImpl getMeasurementImpl(@Type int type, DatastoreManager datastoreManager,
            SourceFetcher sourceFetcher, TriggerFetcher triggerFetcher) {
        if (type == Type.DENOISED) {
            MeasurementImpl measurementImpl = spy(new MeasurementImpl(new MockContentResolver(),
                    datastoreManager, sourceFetcher, triggerFetcher));
            // Disable Impression Noise
            doReturn(Collections.emptyList()).when(measurementImpl).getSourceEventReports(any());
            return measurementImpl;
        } else if (type == Type.NOISY) {
            MeasurementImpl measurementImpl = spy(new MeasurementImpl(
                    new MockContentResolver(), datastoreManager, sourceFetcher, triggerFetcher));
            // Create impression noise with 100% probability
            doAnswer((Answer<List<EventReport>>) invocation -> {
                Source source = (Source) invocation.getArgument(0);
                source.setAttributionMode(Source.AttributionMode.FALSELY);
                return Collections.singletonList(
                        new EventReport.Builder()
                                .setSourceId(source.getEventId())
                                .setReportTime(source.getExpiryTime() + ONE_HOUR_IN_MILLIS)
                                .setTriggerData(0)
                                .setAttributionDestination(source.getAttributionDestination())
                                .setAdTechDomain(source.getAdTechDomain())
                                .setTriggerTime(0)
                                .setTriggerPriority(0)
                                .setTriggerDedupKey(null)
                                .setSourceType(source.getSourceType())
                                .setStatus(EventReport.Status.PENDING)
                                .build()
                );
            }).when(measurementImpl).getSourceEventReports(any());
            return measurementImpl;
        }

        return new MeasurementImpl(
                new MockContentResolver(), datastoreManager, sourceFetcher, triggerFetcher);
    }
}
