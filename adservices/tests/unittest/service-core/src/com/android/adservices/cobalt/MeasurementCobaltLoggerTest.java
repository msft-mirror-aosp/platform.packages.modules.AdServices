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

package com.android.adservices.cobalt;

import static com.android.adservices.cobalt.MeasurementCobaltLogger.getSourceTriggerType;
import static com.android.adservices.cobalt.MeasurementCobaltLogger.getStatusEvent;
import static com.android.adservices.mockito.MockitoExpectations.mockMsmtRegistrationCobaltLoggingEnabled;
import static com.android.adservices.mockito.MockitoExpectations.mockCobaltLoggingEnabled;
import static com.android.adservices.mockito.MockitoExpectations.mockCobaltLoggingFlags;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__PARSING_REGISTRATION_FAILURE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__UNKNOWN_REGISTRATION_FAILURE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__EVENT_SOURCE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__UNKNOWN_SOURCE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__SUCCESS_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__UNKNOWN_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__SURFACE_TYPE__APP_REGISTRATION_SURFACE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__SURFACE_TYPE__UNKNOWN_REGISTRATION_SURFACE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__SURFACE_TYPE__WEB_REGISTRATION_SURFACE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__UNKNOWN_REGISTRATION;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.cobalt.CobaltLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.verification.VerificationMode;

@SpyStatic(FlagsFactory.class)
@SpyStatic(CobaltFactory.class)
public final class MeasurementCobaltLoggerTest extends AdServicesExtendedMockitoTestCase {
    private static final int METRIC_ID = 3;
    // --------------------- Constants for dimension "surface_type" ----------------------
    private static final int UNKNOWN_SURFACE_TYPE =
            AD_SERVICES_MEASUREMENT_REGISTRATIONS__SURFACE_TYPE__UNKNOWN_REGISTRATION_SURFACE_TYPE;
    private static final int WEB =
            AD_SERVICES_MEASUREMENT_REGISTRATIONS__SURFACE_TYPE__WEB_REGISTRATION_SURFACE_TYPE;
    private static final int APP =
            AD_SERVICES_MEASUREMENT_REGISTRATIONS__SURFACE_TYPE__APP_REGISTRATION_SURFACE_TYPE;

    // --------------------- Constants for dimension "source_or_trigger_type" ----------------------
    private static final int UNKNOWN_SOURCE_TYPE =
            AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__UNKNOWN_REGISTRATION;
    private static final int EVENT_SOURCE_TYPE =
            AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__EVENT_SOURCE_TYPE;
    private static final int TRIGGER_TYPE_CODE = 100;
    private static final int UNKNOWN_TYPE = 200;

    // --------------------- Constants for dimension "status" -----------------------
    private static final int UNKNOWN_FAILURE_TYPE =
            AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__UNKNOWN_REGISTRATION_FAILURE_TYPE;
    private static final int PARSING_FAILURE_TYPE =
            AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__PARSING_REGISTRATION_FAILURE_TYPE;
    private static final int SUCCESS_STATUS_CODE = 100;
    private static final int UNKNOWN_STATUS_CODE = 200;

    // --------------------- Constants for dimension "region" -----------------------
    private static final int EEA_REGION_CODE = 1;
    private static final int ROW_REGION_CODE = 2;

    private static final String APP_PACKAGE_NAME = "test.app.name";

    @Mock private CobaltLogger mMockCobaltLogger;
    @Mock private Flags mMockFlags;

    @Before
    public void setUp() {
        mocker.mockGetFlags(mMockFlags);
    }

    @Test
    public void testGetInstance() {
        mockCobaltLoggingFlags(mMockFlags, true);

        MeasurementCobaltLogger instance = MeasurementCobaltLogger.getInstance();
        assertThat(instance).isNotNull();

        MeasurementCobaltLogger otherInstance = MeasurementCobaltLogger.getInstance();
        assertThat(otherInstance).isSameInstanceAs(instance);
    }

    @Test
    public void testIsEnabled_cobaltInitializationException() {
        mockCobaltLoggingEnabled(mMockFlags, true);
        mockThrowExceptionOnGetCobaltLogger();

        MeasurementCobaltLogger logger = new MeasurementCobaltLogger();

        assertThat(logger.isEnabled()).isFalse();
    }

    @Test
    public void testIsEnabled_cobaltLoggingDisabled() {
        mockCobaltLoggingFlags(mMockFlags, false);

        MeasurementCobaltLogger logger = new MeasurementCobaltLogger();

        assertThat(logger.isEnabled()).isFalse();
    }

    @Test
    public void testLogRegistrationStatus_nullAppPackageName() {
        mockCobaltLoggingFlags(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger);

        assertThrows(
                NullPointerException.class,
                () ->
                        logger.logRegistrationStatus(
                                /* appPackageName= */ null,
                                WEB,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__EVENT_SOURCE_TYPE,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__PARSING_REGISTRATION_FAILURE_TYPE,
                                /* isEeaDevice= */ true));
    }

    @Test
    public void testGetSourceTriggerType_unknownType() {
        expect.that(
                        getSourceTriggerType(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__UNKNOWN_REGISTRATION,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__UNKNOWN_SOURCE_TYPE))
                .isEqualTo(UNKNOWN_TYPE);
    }

    @Test
    public void testGetSourceTriggerType_triggerType() {
        expect.that(
                        getSourceTriggerType(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__UNKNOWN_SOURCE_TYPE))
                .isEqualTo(TRIGGER_TYPE_CODE);
    }

    @Test
    public void testGetSourceTriggerType_unknownSourceType() {
        expect.that(
                        getSourceTriggerType(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__UNKNOWN_SOURCE_TYPE))
                .isEqualTo(UNKNOWN_SOURCE_TYPE);
    }

    @Test
    public void testGetSourceTriggerType_negativeSourceType() {
        expect.that(
                        getSourceTriggerType(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE,
                                /* sourceType= */ -1))
                .isEqualTo(UNKNOWN_SOURCE_TYPE);
    }

    @Test
    public void testGetSourceTriggerType_eventSourceType() {
        expect.that(
                        getSourceTriggerType(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__EVENT_SOURCE_TYPE))
                .isEqualTo(EVENT_SOURCE_TYPE);
    }

    @Test
    public void testGetStatusEvent_unknownStatus() {
        expect.that(
                        getStatusEvent(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__UNKNOWN_STATUS,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__UNKNOWN_REGISTRATION_FAILURE_TYPE))
                .isEqualTo(UNKNOWN_STATUS_CODE);
    }

    @Test
    public void testGetStatusEvent_successStatus() {
        expect.that(
                        getStatusEvent(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__SUCCESS_STATUS,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__UNKNOWN_REGISTRATION_FAILURE_TYPE))
                .isEqualTo(SUCCESS_STATUS_CODE);
    }

    @Test
    public void testGetStatusEvent_unknownFailureStatus() {
        expect.that(
                        getStatusEvent(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__UNKNOWN_REGISTRATION_FAILURE_TYPE))
                .isEqualTo(UNKNOWN_FAILURE_TYPE);
    }

    @Test
    public void testGetStatusEvent_negativeFailureStatus() {
        expect.that(
                        getStatusEvent(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS,
                                /* errorCode= */ -1))
                .isEqualTo(UNKNOWN_FAILURE_TYPE);
    }

    @Test
    public void testGetStatusEvent_parsingFailureStatus() {
        expect.that(
                        getStatusEvent(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__PARSING_REGISTRATION_FAILURE_TYPE))
                .isEqualTo(PARSING_FAILURE_TYPE);
    }

    @Test
    public void testLogRegistrationStatus_webSourceRegistrationSuccessLogged() {
        mockCobaltLoggingFlags(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger);

        logger.logRegistrationStatus(
                APP_PACKAGE_NAME,
                WEB,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__EVENT_SOURCE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__SUCCESS_STATUS,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__UNKNOWN_REGISTRATION_FAILURE_TYPE,
                /* isEeaDevice= */ true);

        verifyLoggedEvent(
                APP_PACKAGE_NAME,
                WEB,
                EVENT_SOURCE_TYPE,
                SUCCESS_STATUS_CODE,
                EEA_REGION_CODE,
                times(1));
    }

    @Test
    public void testLogRegistrationStatus_appTriggerRegistrationFailureLogged() {
        mockCobaltLoggingFlags(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger);

        logger.logRegistrationStatus(
                APP_PACKAGE_NAME,
                APP,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__UNKNOWN_SOURCE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__PARSING_REGISTRATION_FAILURE_TYPE,
                /* isEeaDevice= */ false);

        verifyLoggedEvent(
                APP_PACKAGE_NAME,
                APP,
                TRIGGER_TYPE_CODE,
                PARSING_FAILURE_TYPE,
                ROW_REGION_CODE,
                times(1));
    }

    @Test
    public void testLogRegistrationStatus_unknownSurfaceAndTypeLogged() {
        mockCobaltLoggingFlags(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger);

        logger.logRegistrationStatus(
                APP_PACKAGE_NAME,
                UNKNOWN_SURFACE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__UNKNOWN_REGISTRATION,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__UNKNOWN_SOURCE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__UNKNOWN_STATUS,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__UNKNOWN_REGISTRATION_FAILURE_TYPE,
                /* isEeaDevice= */ true);

        verifyLoggedEvent(
                APP_PACKAGE_NAME,
                UNKNOWN_SURFACE_TYPE,
                UNKNOWN_TYPE,
                UNKNOWN_STATUS_CODE,
                EEA_REGION_CODE,
                times(1));
    }

    @Test
    public void testLogRegistrationStatus_cobaltLoggingDisabled() {
        mockCobaltLoggingEnabled(mMockFlags, false);
        // Passing a null cobaltLogger because COBALT_LOGGING_ENABLED is false.
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(/* cobaltLogger */ null);

        logger.logRegistrationStatus(
                APP_PACKAGE_NAME,
                WEB,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__EVENT_SOURCE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__SUCCESS_STATUS,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__UNKNOWN_REGISTRATION_FAILURE_TYPE,
                /* isEeaDevice= */ true);

        verifyLoggedEvent(
                APP_PACKAGE_NAME,
                WEB,
                EVENT_SOURCE_TYPE,
                SUCCESS_STATUS_CODE,
                EEA_REGION_CODE,
                never());
    }

    @Test
    public void testLogRegistrationStatus_msmtRegistrationCobaltLogDisabled() {
        mockMsmtRegistrationCobaltLoggingEnabled(mMockFlags, false);
        // Passing a null cobaltLogger because COBALT_LOGGING_ENABLED is false.
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(/* cobaltLogger */ null);

        logger.logRegistrationStatus(
                APP_PACKAGE_NAME,
                APP,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__UNKNOWN_SOURCE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__PARSING_REGISTRATION_FAILURE_TYPE,
                /* isEeaDevice= */ false);

        verifyLoggedEvent(
                APP_PACKAGE_NAME,
                APP,
                TRIGGER_TYPE_CODE,
                PARSING_FAILURE_TYPE,
                ROW_REGION_CODE,
                never());
    }

    private void verifyLoggedEvent(
            String appPackageName,
            int surfaceType,
            int sourceTriggerType,
            int statusEvent,
            int region,
            VerificationMode mode) {
        verify(mMockCobaltLogger, mode)
                .logString(
                        METRIC_ID,
                        appPackageName,
                        ImmutableList.of(surfaceType, sourceTriggerType, statusEvent, region));
    }

    private static void mockThrowExceptionOnGetCobaltLogger() {
        doThrow(new CobaltInitializationException())
                .when(() -> CobaltFactory.getCobaltLogger(any(), any()));
    }
}
