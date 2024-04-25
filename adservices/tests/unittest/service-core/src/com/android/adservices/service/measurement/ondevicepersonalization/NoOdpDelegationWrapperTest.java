/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.measurement.ondevicepersonalization;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.Uri;

import com.android.adservices.service.measurement.registration.AsyncRegistration;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementOdpRegistrationStats;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NoOdpDelegationWrapperTest {
    private static final String ODP_PACKAGE_NAME = "com.adtech1";
    private static final String ODP_CLASS_NAME = "com.adtech1.AdTechIsolatedService";
    private static final String ODP_CERT_DIGEST = "AABBCCDD";
    private static final String ODP_EVENT_DATA = "123";
    @Mock private AdServicesLogger mLogger;

    @Before
    public void before() {
        mLogger = spy(AdServicesLoggerImpl.getInstance());
    }

    @Test
    public void registerOdpTrigger_logMetrics() {
        NoOdpDelegationWrapper noOdpDelegationWrapper = new NoOdpDelegationWrapper(mLogger);
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId("1")
                        .setTopOrigin(Uri.parse("android-app://com.somePackageName"))
                        .setRegistrant(Uri.parse("android-app://com.somePackageName"))
                        .build();
        Map<String, List<String>> header = new HashMap<>();
        header.put(
                "Odp-Register-Trigger",
                List.of(
                        "{"
                                + "\"service\":\""
                                + ODP_PACKAGE_NAME
                                + "/"
                                + ODP_CLASS_NAME
                                + "\","
                                + "\"certDigest\":\""
                                + ODP_CERT_DIGEST
                                + "\","
                                + "\"data\":\""
                                + ODP_EVENT_DATA
                                + "\""
                                + "}"));
        noOdpDelegationWrapper.registerOdpTrigger(asyncRegistration, header);
        ArgumentCaptor<MeasurementOdpRegistrationStats> odpRegistrationStatsArg =
                ArgumentCaptor.forClass(MeasurementOdpRegistrationStats.class);
        verify(mLogger, times(1)).logMeasurementOdpRegistrations(odpRegistrationStatsArg.capture());
        verify(mLogger, never()).logMeasurementOdpApiCall(any());
        MeasurementOdpRegistrationStats measurementOdpRegistrationStats =
                odpRegistrationStatsArg.getValue();
        assertEquals(
                measurementOdpRegistrationStats.getRegistrationType(),
                OdpRegistrationStatus.RegistrationType.TRIGGER.getValue());
        assertEquals(
                measurementOdpRegistrationStats.getRegistrationStatus(),
                OdpRegistrationStatus.RegistrationStatus.ODP_UNAVAILABLE.getValue());
    }
}
