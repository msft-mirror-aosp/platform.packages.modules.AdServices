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

import static com.android.adservices.mockito.MockitoExpectations.mockMsmtAttributionCobaltLoggingEnabled;
import static com.android.adservices.mockito.MockitoExpectations.mockMsmtRegistrationCobaltLoggingEnabled;
import static com.android.adservices.mockito.MockitoExpectations.mockMsmtReportingCobaltLoggingEnabled;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_ATTRIBUTION__ATTRIBUTION_SURFACE_COMBINATION__APP_APP_ATTRIBUTION_SURFACE_COMBINATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_ATTRIBUTION__ATTRIBUTION_SURFACE_COMBINATION__UNKNOWN_ATTRIBUTION_SURFACE_COMBINATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_ATTRIBUTION__ATTRIBUTION_SURFACE_COMBINATION__WEB_APP_ATTRIBUTION_SURFACE_COMBINATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_ATTRIBUTION__FAILURE_TYPE__NO_MATCHING_SOURCE_ATTRIBUTION_FAILURE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_ATTRIBUTION__FAILURE_TYPE__UNKNOWN_ATTRIBUTION_FAILURE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__PARSING_REGISTRATION_FAILURE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__UNKNOWN_REGISTRATION_FAILURE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__EVENT_SOURCE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__NAVIGATION_SOURCE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__UNKNOWN_SOURCE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__AGGREGATE_AND_EVENT_REPORTS_GENERATED_SUCCESS_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__AGGREGATE_REPORT_GENERATED_SUCCESS_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__EVENT_REPORT_GENERATED_SUCCESS_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__SUCCESS_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__UNKNOWN_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__SURFACE_TYPE__APP_REGISTRATION_SURFACE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__SURFACE_TYPE__UNKNOWN_REGISTRATION_SURFACE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__SURFACE_TYPE__WEB_REGISTRATION_SURFACE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__UNKNOWN_REGISTRATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__FAILURE_TYPE__NETWORK_ERROR_REPORT_UPLOAD_FAILURE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__FAILURE_TYPE__UNKNOWN_REPORT_UPLOAD_FAILURE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__RESPONSE_CODE__FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__RESPONSE_CODE__SUCCESS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__RESPONSE_CODE__UNKNOWN_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__AGGREGATE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__EVENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__UNKNOWN_REPORT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__UPLOAD_METHOD__FALLBACK_REPORT_UPLOAD_METHOD;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__UPLOAD_METHOD__REGULAR_REPORT_UPLOAD_METHOD;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__UPLOAD_METHOD__UNKNOWN_REPORT_UPLOAD_METHOD;
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

    // --------------------- Constants for per_package_attribution_status metrics-------------------
    // The measurement attribution metric has an id of 4.
    //
    // See //packages/modules/AdServices/adservices/service-core/resources/cobalt_registry.textpb
    // for the full metric.`
    private static final int ATTRIBUTION_METRIC_ID = 4;
    // -------------------- Constants for dimension "attribution_surface_type" ---------------------
    private static final int ATTRIBUTION_UNKNOWN_SURFACE_TYPE = 0;
    private static final int ATTRIBUTION_APP_APP_SURFACE = 1;
    private static final int ATTRIBUTION_WEB_APP_SURFACE = 3;

    // --------------------- Constants for dimension "source_type" ---------------------------------
    private static final int ATTRIBUTION_UNKNOWN_SOURCE_TYPE = 0;
    private static final int ATTRIBUTION_EVENT_SOURCE_TYPE = 1;
    private static final int ATTRIBUTION_NAVIGATION_SOURCE_TYPE = 2;

    // --------------------- Constants for dimension "status" --------------------------------------
    private static final int ATTRIBUTION_UNKNOWN_FAILURE_STATUS_CODE = 0;
    private static final int ATTRIBUTION_NO_MATCHING_SOURCE_FAILURE_STATUS_CODE = 4;
    private static final int ATTRIBUTION_SUCCESS_STATUS_CODE = 100;
    private static final int AGGREGATE_REPORT_GENERATED_SUCCESS_STATUS = 101;
    private static final int EVENT_REPORT_GENERATED_SUCCESS_STATUS = 102;
    private static final int AGGREGATE_AND_EVENT_REPORTS_GENERATED_SUCCESS_STATUS = 103;
    private static final int ATTRIBUTION_UNKNOWN_STATUS_CODE = 200;

    // --------------------- Constants for per_package_reporting_status metrics------------------
    // The measurement reporting metric has an id of 5.
    //
    // See //packages/modules/AdServices/adservices/service-core/resources/cobalt_registry.textpb
    // for the full metric.`
    private static final int REPORTING_METRIC_ID = 5;

    // --------------------- Constants for dimension "report_type" ---------------------------------
    private static final int UNKNOWN_REPORT_TYPE = 0;
    private static final int EVENT_REPORT_TYPE = 1;
    private static final int AGGREGATE_REPORT_TYPE = 2;
    // --------------------- Constants for dimension "report_upload_method" ------------------------
    private static final int UNKNOWN_REPORT_UPLOAD_METHOD = 0;
    private static final int REGULAR_REPORT_UPLOAD_METHOD = 1;
    private static final int FALLBACK_REPORT_UPLOAD_METHOD = 2;

    // --------------------- Constants for dimension "status" --------------------------------------
    private static final int REPORTING_UNKNOWN_FAILURE_STATUS_CODE = 0;
    private static final int REPORTING_NETWORK_FAILURE_STATUS_CODE = 2;
    private static final int REPORTING_SUCCESS_STATUS_CODE = 100;
    private static final int REPORTING_UNKNOWN_STATUS_CODE = 200;

    private static final String APP_PACKAGE_NAME = "test.app.name";

    @Mock private CobaltLogger mMockCobaltLogger;
    @Mock private Flags mMockFlags;

    @Before
    public void setUp() {
        mocker.mockGetFlags(mMockFlags);
    }

    @Test
    public void testGetInstance() {
        mockCobaltLoggingFlags(true);

        MeasurementCobaltLogger instance = MeasurementCobaltLogger.getInstance();
        assertThat(instance).isNotNull();

        MeasurementCobaltLogger otherInstance = MeasurementCobaltLogger.getInstance();
        assertThat(otherInstance).isSameInstanceAs(instance);
    }

    @Test
    public void testIsEnabled_cobaltInitializationException() {
        mockCobaltLoggingEnabled(true);
        mockMsmtRegistrationCobaltLoggingEnabled(mMockFlags, true);
        mockThrowExceptionOnGetCobaltLogger();

        MeasurementCobaltLogger logger = MeasurementCobaltLogger.getInstance();
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
    public void testIsEnabled_cobaltLoggingDisabled() {
        mockCobaltLoggingFlags(false);

        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);
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
    public void testIsEnabled_msmtRegistrationCobaltLoggingDisabled() {
        mockCobaltLoggingFlags(true);
        mockMsmtRegistrationCobaltLoggingEnabled(mMockFlags, false);

        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

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
    public void testLogRegistrationStatus_nullAppPackageName() {
        mockCobaltLoggingFlags(true);
        mockMsmtRegistrationCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

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
    public void testLogRegistrationStatus_negativeSourceTypeAndFailureType() {
        mockCobaltLoggingFlags(true);
        mockMsmtRegistrationCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logRegistrationStatus(
                APP_PACKAGE_NAME,
                WEB,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE,
                /* sourceType= */ -1,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS,
                /* errorCode= */ -1,
                /* isEeaDevice= */ true);

        verifyLoggedEvent(
                APP_PACKAGE_NAME,
                WEB,
                UNKNOWN_SOURCE_TYPE,
                UNKNOWN_FAILURE_TYPE,
                EEA_REGION_CODE,
                times(1));
    }

    @Test
    public void testLogRegistrationStatus_webSourceRegistrationSuccessLogged() {
        mockCobaltLoggingFlags(true);
        mockMsmtRegistrationCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

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
    public void testLogRegistrationStatus_appTriggerRegistrationParsingFailureLogged() {
        mockCobaltLoggingFlags(true);
        mockMsmtRegistrationCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

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
        mockCobaltLoggingFlags(true);
        mockMsmtRegistrationCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

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
        mockCobaltLoggingEnabled(false);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

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
        mockCobaltLoggingEnabled(true);
        mockMsmtRegistrationCobaltLoggingEnabled(mMockFlags, false);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

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

    @Test
    public void testLogAttributionStatus_negativeErrorCode() {
        mockCobaltLoggingFlags(true);
        mockMsmtAttributionCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logAttributionStatusWithAppName(
                APP_PACKAGE_NAME,
                AD_SERVICES_MEASUREMENT_ATTRIBUTION__ATTRIBUTION_SURFACE_COMBINATION__WEB_APP_ATTRIBUTION_SURFACE_COMBINATION,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__EVENT_SOURCE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS,
                /* errorCode= */ -10);

        verify(mMockCobaltLogger, times(1))
                .logString(
                        ATTRIBUTION_METRIC_ID,
                        APP_PACKAGE_NAME,
                        ImmutableList.of(
                                ATTRIBUTION_WEB_APP_SURFACE,
                                ATTRIBUTION_EVENT_SOURCE_TYPE,
                                ATTRIBUTION_UNKNOWN_FAILURE_STATUS_CODE));
    }

    @Test
    public void testLogAttributionStatus_failureStatusLogged() {
        mockCobaltLoggingFlags(true);
        mockMsmtAttributionCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logAttributionStatusWithAppName(
                APP_PACKAGE_NAME,
                AD_SERVICES_MEASUREMENT_ATTRIBUTION__ATTRIBUTION_SURFACE_COMBINATION__APP_APP_ATTRIBUTION_SURFACE_COMBINATION,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__NAVIGATION_SOURCE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS,
                AD_SERVICES_MEASUREMENT_ATTRIBUTION__FAILURE_TYPE__NO_MATCHING_SOURCE_ATTRIBUTION_FAILURE_TYPE);

        verify(mMockCobaltLogger, times(1))
                .logString(
                        ATTRIBUTION_METRIC_ID,
                        APP_PACKAGE_NAME,
                        ImmutableList.of(
                                ATTRIBUTION_APP_APP_SURFACE,
                                ATTRIBUTION_NAVIGATION_SOURCE_TYPE,
                                ATTRIBUTION_NO_MATCHING_SOURCE_FAILURE_STATUS_CODE));
    }

    @Test
    public void testLogAttributionStatus_successStatusLogged() {
        mockCobaltLoggingFlags(true);
        mockMsmtAttributionCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logAttributionStatusWithAppName(
                APP_PACKAGE_NAME,
                AD_SERVICES_MEASUREMENT_ATTRIBUTION__ATTRIBUTION_SURFACE_COMBINATION__APP_APP_ATTRIBUTION_SURFACE_COMBINATION,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__NAVIGATION_SOURCE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__SUCCESS_STATUS,
                AD_SERVICES_MEASUREMENT_ATTRIBUTION__FAILURE_TYPE__UNKNOWN_ATTRIBUTION_FAILURE_TYPE);

        verify(mMockCobaltLogger, times(1))
                .logString(
                        ATTRIBUTION_METRIC_ID,
                        APP_PACKAGE_NAME,
                        ImmutableList.of(
                                ATTRIBUTION_APP_APP_SURFACE,
                                ATTRIBUTION_NAVIGATION_SOURCE_TYPE,
                                ATTRIBUTION_SUCCESS_STATUS_CODE));
    }

    @Test
    public void testLogAttributionStatus_successAggregateReportLogged() {
        mockCobaltLoggingFlags(true);
        mockMsmtAttributionCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logAttributionStatusWithAppName(
                APP_PACKAGE_NAME,
                AD_SERVICES_MEASUREMENT_ATTRIBUTION__ATTRIBUTION_SURFACE_COMBINATION__APP_APP_ATTRIBUTION_SURFACE_COMBINATION,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__NAVIGATION_SOURCE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__AGGREGATE_REPORT_GENERATED_SUCCESS_STATUS,
                AD_SERVICES_MEASUREMENT_ATTRIBUTION__FAILURE_TYPE__UNKNOWN_ATTRIBUTION_FAILURE_TYPE);

        verify(mMockCobaltLogger, times(1))
                .logString(
                        ATTRIBUTION_METRIC_ID,
                        APP_PACKAGE_NAME,
                        ImmutableList.of(
                                ATTRIBUTION_APP_APP_SURFACE,
                                ATTRIBUTION_NAVIGATION_SOURCE_TYPE,
                                AGGREGATE_REPORT_GENERATED_SUCCESS_STATUS));
    }

    @Test
    public void testLogAttributionStatus_successEventReportLogged() {
        mockCobaltLoggingFlags(true);
        mockMsmtAttributionCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logAttributionStatusWithAppName(
                APP_PACKAGE_NAME,
                AD_SERVICES_MEASUREMENT_ATTRIBUTION__ATTRIBUTION_SURFACE_COMBINATION__APP_APP_ATTRIBUTION_SURFACE_COMBINATION,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__NAVIGATION_SOURCE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__EVENT_REPORT_GENERATED_SUCCESS_STATUS,
                AD_SERVICES_MEASUREMENT_ATTRIBUTION__FAILURE_TYPE__UNKNOWN_ATTRIBUTION_FAILURE_TYPE);

        verify(mMockCobaltLogger, times(1))
                .logString(
                        ATTRIBUTION_METRIC_ID,
                        APP_PACKAGE_NAME,
                        ImmutableList.of(
                                ATTRIBUTION_APP_APP_SURFACE,
                                ATTRIBUTION_NAVIGATION_SOURCE_TYPE,
                                EVENT_REPORT_GENERATED_SUCCESS_STATUS));
    }

    @Test
    public void testLogAttributionStatus_successEventAndAggregateReportLogged() {
        mockCobaltLoggingFlags(true);
        mockMsmtAttributionCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logAttributionStatusWithAppName(
                APP_PACKAGE_NAME,
                AD_SERVICES_MEASUREMENT_ATTRIBUTION__ATTRIBUTION_SURFACE_COMBINATION__APP_APP_ATTRIBUTION_SURFACE_COMBINATION,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__NAVIGATION_SOURCE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__AGGREGATE_AND_EVENT_REPORTS_GENERATED_SUCCESS_STATUS,
                AD_SERVICES_MEASUREMENT_ATTRIBUTION__FAILURE_TYPE__UNKNOWN_ATTRIBUTION_FAILURE_TYPE);

        verify(mMockCobaltLogger, times(1))
                .logString(
                        ATTRIBUTION_METRIC_ID,
                        APP_PACKAGE_NAME,
                        ImmutableList.of(
                                ATTRIBUTION_APP_APP_SURFACE,
                                ATTRIBUTION_NAVIGATION_SOURCE_TYPE,
                                AGGREGATE_AND_EVENT_REPORTS_GENERATED_SUCCESS_STATUS));
    }

    @Test
    public void testLogAttributionStatus_unknownSurfaceAndTypeLogged() {
        mockCobaltLoggingFlags(true);
        mockMsmtAttributionCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logAttributionStatusWithAppName(
                APP_PACKAGE_NAME,
                AD_SERVICES_MEASUREMENT_ATTRIBUTION__ATTRIBUTION_SURFACE_COMBINATION__UNKNOWN_ATTRIBUTION_SURFACE_COMBINATION,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__UNKNOWN_SOURCE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__UNKNOWN_STATUS,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__UNKNOWN_REGISTRATION_FAILURE_TYPE);

        verify(mMockCobaltLogger, times(1))
                .logString(
                        ATTRIBUTION_METRIC_ID,
                        APP_PACKAGE_NAME,
                        ImmutableList.of(
                                ATTRIBUTION_UNKNOWN_SURFACE_TYPE,
                                ATTRIBUTION_UNKNOWN_SOURCE_TYPE,
                                ATTRIBUTION_UNKNOWN_STATUS_CODE));
    }

    @Test
    public void testLogAttributionStatus_cobaltLoggingDisabled() {
        mockCobaltLoggingEnabled(false);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logAttributionStatusWithAppName(
                APP_PACKAGE_NAME,
                AD_SERVICES_MEASUREMENT_ATTRIBUTION__ATTRIBUTION_SURFACE_COMBINATION__UNKNOWN_ATTRIBUTION_SURFACE_COMBINATION,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__UNKNOWN_SOURCE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__UNKNOWN_STATUS,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__UNKNOWN_REGISTRATION_FAILURE_TYPE);

        verify(mMockCobaltLogger, never())
                .logString(
                        ATTRIBUTION_METRIC_ID,
                        APP_PACKAGE_NAME,
                        ImmutableList.of(
                                ATTRIBUTION_UNKNOWN_SURFACE_TYPE,
                                ATTRIBUTION_UNKNOWN_SOURCE_TYPE,
                                ATTRIBUTION_UNKNOWN_STATUS_CODE));
    }

    @Test
    public void testLogAttributionStatus_msmtAttributionCobaltLogDisabled() {
        mockCobaltLoggingEnabled(true);
        mockMsmtAttributionCobaltLoggingEnabled(mMockFlags, false);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logAttributionStatusWithAppName(
                APP_PACKAGE_NAME,
                AD_SERVICES_MEASUREMENT_ATTRIBUTION__ATTRIBUTION_SURFACE_COMBINATION__UNKNOWN_ATTRIBUTION_SURFACE_COMBINATION,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__UNKNOWN_SOURCE_TYPE,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__UNKNOWN_STATUS,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__UNKNOWN_REGISTRATION_FAILURE_TYPE);

        verify(mMockCobaltLogger, never())
                .logString(
                        ATTRIBUTION_METRIC_ID,
                        APP_PACKAGE_NAME,
                        ImmutableList.of(
                                ATTRIBUTION_UNKNOWN_SURFACE_TYPE,
                                ATTRIBUTION_UNKNOWN_SOURCE_TYPE,
                                ATTRIBUTION_UNKNOWN_STATUS_CODE));
    }

    @Test
    public void testLogReportingStatus_failureStatusLogged() {
        mockCobaltLoggingFlags(true);
        mockMsmtReportingCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logReportingStatusWithAppName(
                APP_PACKAGE_NAME,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__AGGREGATE,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__UPLOAD_METHOD__REGULAR_REPORT_UPLOAD_METHOD,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__RESPONSE_CODE__FAILURE,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__FAILURE_TYPE__NETWORK_ERROR_REPORT_UPLOAD_FAILURE_TYPE);

        verify(mMockCobaltLogger, times(1))
                .logString(
                        REPORTING_METRIC_ID,
                        APP_PACKAGE_NAME,
                        ImmutableList.of(
                                AGGREGATE_REPORT_TYPE,
                                REGULAR_REPORT_UPLOAD_METHOD,
                                REPORTING_NETWORK_FAILURE_STATUS_CODE));
    }

    @Test
    public void testLogReportingStatus_successStatusLogged() {
        mockCobaltLoggingFlags(true);
        mockMsmtReportingCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logReportingStatusWithAppName(
                APP_PACKAGE_NAME,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__EVENT,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__UPLOAD_METHOD__FALLBACK_REPORT_UPLOAD_METHOD,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__RESPONSE_CODE__SUCCESS,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__FAILURE_TYPE__UNKNOWN_REPORT_UPLOAD_FAILURE_TYPE);

        verify(mMockCobaltLogger, times(1))
                .logString(
                        REPORTING_METRIC_ID,
                        APP_PACKAGE_NAME,
                        ImmutableList.of(
                                EVENT_REPORT_TYPE,
                                FALLBACK_REPORT_UPLOAD_METHOD,
                                REPORTING_SUCCESS_STATUS_CODE));
    }

    @Test
    public void testLogReportingStatus_unknownSurfaceAndTypeLogged() {
        mockCobaltLoggingFlags(true);
        mockMsmtReportingCobaltLoggingEnabled(mMockFlags, true);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logReportingStatusWithAppName(
                APP_PACKAGE_NAME,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__UNKNOWN_REPORT,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__UPLOAD_METHOD__UNKNOWN_REPORT_UPLOAD_METHOD,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__RESPONSE_CODE__UNKNOWN_STATUS,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__FAILURE_TYPE__UNKNOWN_REPORT_UPLOAD_FAILURE_TYPE);

        verify(mMockCobaltLogger, times(1))
                .logString(
                        REPORTING_METRIC_ID,
                        APP_PACKAGE_NAME,
                        ImmutableList.of(
                                UNKNOWN_REPORT_TYPE,
                                UNKNOWN_REPORT_UPLOAD_METHOD,
                                REPORTING_UNKNOWN_STATUS_CODE));
    }

    @Test
    public void testLogReportingStatus_cobaltLoggingDisabled() {
        mockCobaltLoggingEnabled(false);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logReportingStatusWithAppName(
                APP_PACKAGE_NAME,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__UNKNOWN_REPORT,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__UPLOAD_METHOD__UNKNOWN_REPORT_UPLOAD_METHOD,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__RESPONSE_CODE__UNKNOWN_STATUS,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__FAILURE_TYPE__UNKNOWN_REPORT_UPLOAD_FAILURE_TYPE);

        verify(mMockCobaltLogger, never())
                .logString(
                        REPORTING_METRIC_ID,
                        APP_PACKAGE_NAME,
                        ImmutableList.of(
                                UNKNOWN_REPORT_TYPE,
                                UNKNOWN_REPORT_UPLOAD_METHOD,
                                REPORTING_UNKNOWN_STATUS_CODE));
    }

    @Test
    public void testLogReportingStatus_msmtReportingCobaltLogDisabled() {
        mockCobaltLoggingEnabled(true);
        mockMsmtReportingCobaltLoggingEnabled(mMockFlags, false);
        MeasurementCobaltLogger logger = new MeasurementCobaltLogger(mMockCobaltLogger, mMockFlags);

        logger.logReportingStatusWithAppName(
                APP_PACKAGE_NAME,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__UNKNOWN_REPORT,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__UPLOAD_METHOD__UNKNOWN_REPORT_UPLOAD_METHOD,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__RESPONSE_CODE__UNKNOWN_STATUS,
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__FAILURE_TYPE__UNKNOWN_REPORT_UPLOAD_FAILURE_TYPE);

        verify(mMockCobaltLogger, never())
                .logString(
                        REPORTING_METRIC_ID,
                        APP_PACKAGE_NAME,
                        ImmutableList.of(
                                UNKNOWN_REPORT_TYPE,
                                UNKNOWN_REPORT_UPLOAD_METHOD,
                                REPORTING_UNKNOWN_STATUS_CODE));
    }

    private void mockCobaltLoggingFlags(boolean value) {
        mocker.mockAllCobaltLoggingFlags(mMockFlags, value);
    }

    private void mockCobaltLoggingEnabled(boolean value) {
        mocker.mockGetCobaltLoggingEnabled(mMockFlags, value);
    }

    private static void mockThrowExceptionOnGetCobaltLogger() {
        doThrow(new CobaltInitializationException())
                .when(() -> CobaltFactory.getCobaltLogger(any(), any()));
    }
}
