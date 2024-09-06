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

import static org.mockito.Mockito.spy;

import android.content.ContentResolver;
import android.test.mock.MockContentResolver;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.deletion.MeasurementDataDeleter;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.attribution.AttributionJobHandlerWrapper;
import com.android.adservices.service.measurement.inputverification.ClickVerifier;
import com.android.adservices.service.measurement.noising.SourceNoiseHandler;
import com.android.adservices.service.measurement.registration.AsyncRegistrationQueueRunner;
import com.android.adservices.service.measurement.registration.AsyncSourceFetcher;
import com.android.adservices.service.measurement.registration.AsyncTriggerFetcher;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.reporting.EventReportWindowCalcDelegate;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;

class TestObjectProvider {
    static AttributionJobHandlerWrapper getAttributionJobHandler(
            DatastoreManager datastoreManager, Flags flags, AdServicesErrorLogger errorLogger) {
        return new AttributionJobHandlerWrapper(
                datastoreManager,
                flags,
                new DebugReportApi(
                        ApplicationProvider.getApplicationContext(),
                        flags,
                        new EventReportWindowCalcDelegate(flags),
                        new SourceNoiseHandler(flags)),
                new EventReportWindowCalcDelegate(flags),
                new SourceNoiseHandler(flags),
                AdServicesLoggerImpl.getInstance());
    }

    static MeasurementImpl getMeasurementImpl(
            DatastoreManager datastoreManager,
            ClickVerifier clickVerifier,
            MeasurementDataDeleter measurementDataDeleter,
            ContentResolver contentResolver) {
        return spy(
                new MeasurementImpl(
                        null,
                        FakeFlagsFactory.getFlagsForTest(),
                        datastoreManager,
                        clickVerifier,
                        measurementDataDeleter,
                        contentResolver));
    }

    static AsyncRegistrationQueueRunner getAsyncRegistrationQueueRunner(
            SourceNoiseHandler sourceNoiseHandler,
            DatastoreManager datastoreManager,
            AsyncSourceFetcher asyncSourceFetcher,
            AsyncTriggerFetcher asyncTriggerFetcher,
            DebugReportApi debugReportApi,
            Flags flags) {
        return new AsyncRegistrationQueueRunner(
                ApplicationProvider.getApplicationContext(),
                new MockContentResolver(),
                asyncSourceFetcher,
                asyncTriggerFetcher,
                datastoreManager,
                debugReportApi,
                sourceNoiseHandler,
                flags);
    }
}
