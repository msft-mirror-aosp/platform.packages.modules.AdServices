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

import static com.android.adservices.service.Flags.MAX_ODP_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_INVALID_HEADER_FIELD_VALUE_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_INVALID_HEADER_FORMAT_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_JSON_PARSING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_MISSING_REQUIRED_HEADER_FIELD_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_PARSING_UNKNOWN_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.ondevicepersonalization.MeasurementWebTriggerEventParams;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationSystemEventManager;
import android.net.Uri;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.AdServicesLoggingUsageRule;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.registration.AsyncRegistration;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementOdpRegistrationStats;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpyStatic(ErrorLogUtil.class)
@SetErrorLogUtilDefaultParams(ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)
public class OdpDelegationWrapperImplTest extends AdServicesExtendedMockitoTestCase {
    private static final String ODP_PACKAGE_NAME = "com.adtech1";
    private static final String ODP_CLASS_NAME = "com.adtech1.AdTechIsolatedService";
    private static final String ODP_CERT_DIGEST = "AABBCCDD";
    private static final String ODP_EVENT_DATA = "123";
    private static final String ODP_INVALID_SERVICE_1 =
            "com.adtech1.com.adtech1.AdTechIsolatedService";
    private static final String ODP_INVALID_SERVICE_2 =
            "com.adtech1.com.adtech1.AdTechIsolatedService/";

    @Mock private AdServicesLogger mLogger;
    @Mock private OnDevicePersonalizationSystemEventManager mOdpSystemEventManager;
    @Mock private Flags mFlags;

    @Rule(order = 11)
    public final AdServicesLoggingUsageRule errorLogUtilUsageRule =
            AdServicesLoggingUsageRule.errorLogUtilUsageRule();

    @Before
    public void setup() {
        when(mFlags.getMaxOdpTriggerRegistrationHeaderSizeBytes())
                .thenReturn(MAX_ODP_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES);
    }

    @Test
    public void creation_nullParameters_fail() {
        assertThrows(
                NullPointerException.class,
                () -> new OdpDelegationWrapperImpl(null, mLogger, mFlags));
    }

    @Test
    public void registerOdpTrigger_nullParameters_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(mOdpSystemEventManager, mLogger, mFlags);
        assertThrows(
                NullPointerException.class,
                () -> odpDelegationWrapperImpl.registerOdpTrigger(null, null, true));
        verify(mLogger, never()).logMeasurementOdpRegistrations(any());
        verify(mLogger, never()).logMeasurementOdpApiCall(any());
    }

    /*
     * ODP is available only on T+ devices. Testing it on R- produces NoClassDefFoundError for
     * android.os.OutcomeReceiver class as it was introduced in S. So explicitly disabling.
     */
    @Test
    public void registerOdpTrigger_validParameters_success() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(mOdpSystemEventManager, mLogger, mFlags);
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
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header, true);

        ArgumentCaptor<MeasurementWebTriggerEventParams> statsArg =
                ArgumentCaptor.forClass(MeasurementWebTriggerEventParams.class);
        verify(mOdpSystemEventManager, times(1))
                .notifyMeasurementEvent(statsArg.capture(), any(), any());
        MeasurementWebTriggerEventParams params = statsArg.getValue();
        assertEquals(asyncRegistration.getTopOrigin(), params.getDestinationUrl());
        assertEquals(asyncRegistration.getRegistrant().toString(), params.getAppPackageName());
        assertEquals(ODP_PACKAGE_NAME, params.getIsolatedService().getPackageName());
        assertEquals(ODP_CLASS_NAME, params.getIsolatedService().getClassName());
        assertEquals(ODP_CERT_DIGEST, params.getCertDigest());
        assertTrue(
                Arrays.equals(
                        ODP_EVENT_DATA.getBytes(StandardCharsets.UTF_8), params.getEventData()));

        ArgumentCaptor<MeasurementOdpRegistrationStats> odpRegistrationStatsArg =
                ArgumentCaptor.forClass(MeasurementOdpRegistrationStats.class);
        verify(mLogger, times(1)).logMeasurementOdpRegistrations(odpRegistrationStatsArg.capture());
        MeasurementOdpRegistrationStats measurementOdpRegistrationStats =
                odpRegistrationStatsArg.getValue();
        assertEquals(
                measurementOdpRegistrationStats.getRegistrationType(),
                OdpRegistrationStatus.RegistrationType.TRIGGER.getValue());
        assertEquals(
                measurementOdpRegistrationStats.getRegistrationStatus(),
                OdpRegistrationStatus.RegistrationStatus.SUCCESS.getValue());
    }

    @Test
    public void registerOdpTrigger_invalidEnrollment_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(mOdpSystemEventManager, mLogger, mFlags);
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
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header, false);

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
                OdpRegistrationStatus.RegistrationStatus.INVALID_ENROLLMENT.getValue());
    }

    @Test
    public void registerOdpTrigger_headerSizeLimitExceeded_fail() {
        when(mFlags.getMaxOdpTriggerRegistrationHeaderSizeBytes()).thenReturn(0L);
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(mOdpSystemEventManager, mLogger, mFlags);
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
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header, true);

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
                OdpRegistrationStatus.RegistrationStatus.HEADER_SIZE_LIMIT_EXCEEDED.getValue());
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_INVALID_HEADER_FORMAT_ERROR)
    public void registerOdpTrigger_invalidHeaderFormat_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(mOdpSystemEventManager, mLogger, mFlags);
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
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header, true);
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
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_MISSING_REQUIRED_HEADER_FIELD_ERROR)
    public void registerOdpTrigger_missingRequiredField_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(mOdpSystemEventManager, mLogger, mFlags);
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
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header, true);
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
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_INVALID_HEADER_FIELD_VALUE_ERROR)
    public void registerOdpTrigger_invalidServiceName_NoForwardSlash_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(mOdpSystemEventManager, mLogger, mFlags);
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
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header, true);
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
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_INVALID_HEADER_FIELD_VALUE_ERROR)
    public void registerOdpTrigger_invalidServiceName_forwardSlashEndingCharacter_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(mOdpSystemEventManager, mLogger, mFlags);
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
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header, true);
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
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_JSON_PARSING_ERROR)
    public void registerOdpTrigger_headerNotJson_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(mOdpSystemEventManager, mLogger, mFlags);
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
                        "\"service\":\""
                                + ODP_PACKAGE_NAME
                                + "/"
                                + ODP_CLASS_NAME
                                + "\","
                                + "\"certDigest\":\""
                                + ODP_CERT_DIGEST
                                + "\","
                                + "\"data\":\""
                                + ODP_EVENT_DATA
                                + "\""));
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header, true);

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
                OdpRegistrationStatus.RegistrationStatus.PARSING_EXCEPTION.getValue());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = IllegalArgumentException.class,
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_PARSING_UNKNOWN_ERROR)
    public void registerOdpTrigger_throwIllegalArgumentException_fail() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        doThrow(new IllegalArgumentException("Illegal Argument"))
                .when(mOdpSystemEventManager)
                .notifyMeasurementEvent(any(), any(), any());
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(mOdpSystemEventManager, mLogger, mFlags);
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
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header, true);

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
                OdpRegistrationStatus.RegistrationStatus.PARSING_EXCEPTION.getValue());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = NullPointerException.class,
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_PARSING_UNKNOWN_ERROR)
    public void registerOdpTrigger_throwNullPointerException_fail() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        doThrow(new NullPointerException("Null Error"))
                .when(mOdpSystemEventManager)
                .notifyMeasurementEvent(any(), any(), any());
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(mOdpSystemEventManager, mLogger, mFlags);
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
        odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header, true);

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
                OdpRegistrationStatus.RegistrationStatus.PARSING_EXCEPTION.getValue());
    }
}
