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

import static com.android.adservices.mockito.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.verifyErrorLogUtilError;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_INVALID_HEADER_FIELD_VALUE_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_INVALID_HEADER_FORMAT_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_MISSING_REQUIRED_HEADER_FIELD_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.Uri;


import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.measurement.registration.AsyncRegistration;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementOdpRegistrationStats;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpyStatic(ErrorLogUtil.class)
public class OdpDelegationWrapperImplTest extends AdServicesExtendedMockitoTestCase {
    private static final String ODP_PACKAGE_NAME = "com.adtech1";
    private static final String ODP_CLASS_NAME = "com.adtech1.AdTechIsolatedService";
    private static final String ODP_CERT_DIGEST = "AABBCCDD";
    private static final String ODP_EVENT_DATA = "123";
    private static final String ODP_INVALID_SERVICE_1 =
            "com.adtech1.com.adtech1.AdTechIsolatedService";
    private static final String ODP_INVALID_SERVICE_2 =
            "com.adtech1.com.adtech1.AdTechIsolatedService/";

    @Mock AdServicesLogger mLogger;

    @Before
    public void setup() {
        mLogger = spy(AdServicesLoggerImpl.getInstance());
        doNothingOnErrorLogUtilError();
    }

    @Test
    public void creation_nullParameters_fail() {
        assertThrows(NullPointerException.class, () -> new OdpDelegationWrapperImpl(null));
    }

    @Test
    public void registerOdpTrigger_nullParameters_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(null, true, mLogger);
        assertThrows(
                NullPointerException.class,
                () -> odpDelegationWrapperImpl.registerOdpTrigger(null, null));
        verify(mLogger, never()).logMeasurementOdpRegistrations(any());
        verify(mLogger, never()).logMeasurementOdpApiCall(any());
    }

    @Test
    public void registerOdpTrigger_validParameters_success() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(null, true, mLogger);
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
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header);
        verify(mLogger, times(1)).logMeasurementOdpRegistrations(any());
        verify(mLogger, never()).logMeasurementOdpApiCall(any());
    }

    @Test
    public void registerOdpTrigger_missingHeader_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(null, true, mLogger);
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId("1")
                        .setTopOrigin(Uri.parse("android-app://com.somePackageName"))
                        .setRegistrant(Uri.parse("android-app://com.somePackageName"))
                        .build();
        Map<String, List<String>> header = new HashMap<>();
        header.put(
                "Not-Odp-Register-Trigger",
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
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header);
        verify(mLogger, never()).logMeasurementOdpRegistrations(any());
        verify(mLogger, never()).logMeasurementOdpApiCall(any());
    }

    @Test
    public void registerOdpTrigger_invalidHeaderFormat_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(null, true, mLogger);
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
                                + "\"certDigest\":\""
                                + ODP_CERT_DIGEST
                                + "\","
                                + "\"data\":\""
                                + ODP_EVENT_DATA
                                + "\""
                                + "}",
                        "{"
                                + "\"certDigest\":\""
                                + ODP_CERT_DIGEST
                                + "\","
                                + "\"data\":\""
                                + ODP_EVENT_DATA
                                + "\""
                                + "}"));
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header);
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
                OdpRegistrationStatus.RegistrationStatus.INVALID_HEADER_FORMAT.getValue());
        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_INVALID_HEADER_FORMAT_ERROR,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    }

    @Test
    public void registerOdpTrigger_missingRequiredField_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(null, true, mLogger);
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
                                + "\"certDigest\":\""
                                + ODP_CERT_DIGEST
                                + "\","
                                + "\"data\":\""
                                + ODP_EVENT_DATA
                                + "\""
                                + "}"));
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header);
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
                OdpRegistrationStatus.RegistrationStatus.MISSING_REQUIRED_HEADER_FIELD.getValue());
        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_MISSING_REQUIRED_HEADER_FIELD_ERROR,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    }

    @Test
    public void registerOdpTrigger_invalidServiceName_NoForwardSlash_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(null, true, mLogger);
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
                                + ODP_INVALID_SERVICE_1
                                + "\","
                                + "\"certDigest\":\""
                                + ODP_CERT_DIGEST
                                + "\","
                                + "\"data\":\""
                                + ODP_EVENT_DATA
                                + "\""
                                + "}"));
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header);
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
                OdpRegistrationStatus.RegistrationStatus.INVALID_HEADER_FIELD_VALUE.getValue());
        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_INVALID_HEADER_FIELD_VALUE_ERROR,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    }

    @Test
    public void registerOdpTrigger_invalidServiceName_forwardSlashEndingCharacter_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(null, true, mLogger);
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
                                + ODP_INVALID_SERVICE_2
                                + "\","
                                + "\"certDigest\":\""
                                + ODP_CERT_DIGEST
                                + "\","
                                + "\"data\":\""
                                + ODP_EVENT_DATA
                                + "\""
                                + "}"));
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header);
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
                OdpRegistrationStatus.RegistrationStatus.INVALID_HEADER_FIELD_VALUE.getValue());
        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_INVALID_HEADER_FIELD_VALUE_ERROR,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    }
}
