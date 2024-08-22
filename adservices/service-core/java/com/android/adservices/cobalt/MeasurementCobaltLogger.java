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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__AGGREGATE_AND_EVENT_REPORTS_GENERATED_SUCCESS_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__AGGREGATE_REPORT_GENERATED_SUCCESS_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__EVENT_REPORT_GENERATED_SUCCESS_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__SUCCESS_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__RESPONSE_CODE__FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__RESPONSE_CODE__SUCCESS;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.cobalt.CobaltLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;

import java.util.Objects;

/**
 * Wrapper around {@link CobaltLogger} that logs a measurement source or trigger registration with
 * {@code SurfaceType, SourceOrTriggerType, Status, Region} and app package name to Cobalt.
 */
public final class MeasurementCobaltLogger {
    // --------------------- Constants for per_package_registration_status metrics------------------
    // The measurement registration metric has an id of 3.
    //
    // See //packages/modules/AdServices/adservices/service-core/resources/cobalt_registry.textpb
    // for the full metric.`
    private static final int REGISTRATION_METRIC_ID = 3;

    // --------------------- Constants for dimension "source_or_trigger_type" ----------------------
    private static final int UNKNOWN_SOURCE_TYPE = 0;
    private static final int TRIGGER_TYPE_CODE = 100;
    private static final int UNKNOWN_TYPE = 200;

    // --------------------- Constants for dimension "status" -----------------------
    private static final int UNKNOWN_FAILURE_CODE = 0;
    private static final int SUCCESS_STATUS_CODE = 100;
    private static final int UNKNOWN_STATUS_CODE = 200;

    // --------------------- Constants for dimension "region" -----------------------
    private static final int EEA_REGION_CODE = 1;
    private static final int ROW_REGION_CODE = 2;

    // --------------------- Constants for per_package_attribution_status metrics------------------
    // The measurement attribution metric has an id of 4.
    //
    // See //packages/modules/AdServices/adservices/service-core/resources/cobalt_registry.textpb
    // for the full metric.`
    private static final int ATTRIBUTION_METRIC_ID = 4;

    // --------------------- Constants for dimension "status" --------------------------------------
    private static final int ATTRIBUTION_UNKNOWN_FAILURE_STATUS_CODE = 0;
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

    // --------------------- Constants for dimension "status" --------------------------------------
    private static final int REPORTING_UNKNOWN_FAILURE_STATUS_CODE = 0;
    private static final int REPORTING_SUCCESS_STATUS_CODE = 100;
    private static final int REPORTING_UNKNOWN_STATUS_CODE = 200;

    private static final MeasurementCobaltLogger sInstance = new MeasurementCobaltLogger();
    private final Flags mFlags;
    @Nullable private final CobaltLogger mCobaltLogger;

    /** Returns the singleton of the {@code MeasurementCobaltLogger}. */
    public static MeasurementCobaltLogger getInstance() {
        return sInstance;
    }

    @VisibleForTesting
    MeasurementCobaltLogger(CobaltLogger cobaltLogger, Flags flags) {
        this.mCobaltLogger = cobaltLogger;
        this.mFlags = flags;
    }

    private MeasurementCobaltLogger() {
        this(getDefaultCobaltLogger(FlagsFactory.getFlags()), FlagsFactory.getFlags());
    }

    @Nullable
    private static CobaltLogger getDefaultCobaltLogger(Flags flags) {
        CobaltLogger logger = null;
        try {
            if (flags.getMsmtRegistrationCobaltLoggingEnabled()
                    || flags.getMsmtAttributionCobaltLoggingEnabled()
                    || flags.getMsmtReportingCobaltLoggingEnabled()) {
                logger = CobaltFactory.getCobaltLogger(ApplicationContextSingleton.get(), flags);
            } else {
                LogUtil.d("Cobalt logger is disabled.");
            }
        } catch (CobaltInitializationException | IllegalStateException | SecurityException e) {
            LogUtil.e(e, "Cobalt logger initialization failed.");
            // TODO(b/324956419): Add CEL.
        }
        return logger;
    }

    /**
     * Log a measurement registration event with app package name, surface type, trigger or source
     * type, registration status, error code and if the device is in European Economic Area.
     *
     * @param appPackageName the app package name that registered a source or trigger.
     * @param surfaceType the registration surface, web or app.
     * @param type the registration type, source or trigger.
     * @param sourceType the type of source.
     * @param statusCode the registration status code.
     * @param errorCode the error type when the registration failed.
     * @param isEeaDevice if the device is in European Economic Area.
     */
    @SuppressWarnings("FutureReturnValueIgnored") // TODO(b/323263328): Remove @SuppressWarnings.
    public void logRegistrationStatus(
            String appPackageName,
            int surfaceType,
            int type,
            int sourceType,
            int statusCode,
            int errorCode,
            boolean isEeaDevice,
            @Nullable String enrollmentId) {
        if (!isRegistrationCobaltLoggingEnabled()) {
            LogUtil.w("Skip logRegistrationStatus because Cobalt logger is not available.");
            return;
        }
        Objects.requireNonNull(appPackageName, "appPackageName cannot be null");

        mCobaltLogger.logString(
                REGISTRATION_METRIC_ID,
                appPackageName,
                ImmutableList.of(
                        surfaceType,
                        getSourceTriggerType(type, sourceType),
                        getRegistrationStatusEvent(statusCode, errorCode),
                        isEeaDevice ? EEA_REGION_CODE : ROW_REGION_CODE,
                        hashEnrollmentIntoUnsignedInt(enrollmentId)));
    }

    @SuppressWarnings("FutureReturnValueIgnored") // TODO(b/323263328): Remove @SuppressWarnings.
    public void logAttributionStatusWithAppName(
            String appPackageName,
            int attrSurfaceType,
            int sourceType,
            int statusCode,
            int errorCode) {
        if (!isAttributionCobaltLoggingEnabled()) {
            LogUtil.w(
                    "Skip logAttributionStatusWithAppName because Cobalt logger is not available");
            return;
        }
        Objects.requireNonNull(appPackageName, "appPackageName cannot be null");

        mCobaltLogger.logString(
                ATTRIBUTION_METRIC_ID,
                appPackageName,
                ImmutableList.of(
                        attrSurfaceType,
                        sourceType,
                        getAttributionStatusEvent(statusCode, errorCode)));
    }

    @SuppressWarnings("FutureReturnValueIgnored") // TODO(b/323263328): Remove @SuppressWarnings.
    public void logReportingStatusWithAppName(
            String appPackageName,
            int reportType,
            int reportUploadMethod,
            int statusCode,
            int errorCode) {
        if (!isReportingCobaltLoggingEnabled()) {
            LogUtil.w("Skip logReportingStatusWithAppName because Cobalt logger is not available.");
            return;
        }
        Objects.requireNonNull(appPackageName, "appPackageName cannot be null");

        mCobaltLogger.logString(
                REPORTING_METRIC_ID,
                appPackageName,
                ImmutableList.of(
                        reportType,
                        reportUploadMethod,
                        getReportingStatusEvent(statusCode, errorCode)));
    }

    private boolean isRegistrationCobaltLoggingEnabled() {
        return mCobaltLogger != null && mFlags.getMsmtRegistrationCobaltLoggingEnabled();
    }

    private boolean isAttributionCobaltLoggingEnabled() {
        return mCobaltLogger != null && mFlags.getMsmtAttributionCobaltLoggingEnabled();
    }

    private boolean isReportingCobaltLoggingEnabled() {
        return mCobaltLogger != null && mFlags.getMsmtReportingCobaltLoggingEnabled();
    }

    // Combines trigger and source types into the "source_or_trigger_type" dimension.
    private static int getSourceTriggerType(int type, int sourceType) {
        int sourceTriggerType;
        if (type == AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER) {
            sourceTriggerType = TRIGGER_TYPE_CODE;
        } else if (type == AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE) {
            // Source type should be the same as enum SourceType in
            // frameworks/proto_logging/stats/enums/adservices/measurement/enums.proto
            sourceTriggerType = sourceType >= 0 ? sourceType : UNKNOWN_SOURCE_TYPE;
        } else {
            sourceTriggerType = UNKNOWN_TYPE;
        }
        return sourceTriggerType;
    }

    // Combines registration status code and failure type into "status" dimension.
    private static int getRegistrationStatusEvent(int statusCode, int errorCode) {
        int statusEvent;
        if (statusCode == AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS) {
            // When the registration fails, the error code logging should match enum
            // RegistrationFailureType in
            // frameworks/proto_logging/stats/enums/adservices/measurement/enums.proto
            statusEvent = errorCode >= 0 ? errorCode : UNKNOWN_FAILURE_CODE;
        } else if (statusCode == AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__SUCCESS_STATUS) {
            statusEvent = SUCCESS_STATUS_CODE;
        } else {
            statusEvent = UNKNOWN_STATUS_CODE;
        }
        return statusEvent;
    }

    // Combines attribution status code and failure type into "status" dimension.
    private static int getAttributionStatusEvent(int statusCode, int errorCode) {
        int statusEvent;
        if (statusCode == AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS) {
            // When the attribution fails, the error code logging should match enum
            // AttributionFailureType inside:
            // frameworks/proto_logging/stats/enums/adservices/measurement/enums.proto
            statusEvent = (errorCode >= 0) ? errorCode : ATTRIBUTION_UNKNOWN_FAILURE_STATUS_CODE;
        } else if (statusCode == AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__SUCCESS_STATUS) {
            statusEvent = ATTRIBUTION_SUCCESS_STATUS_CODE;
        } else if (statusCode
                == AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__AGGREGATE_REPORT_GENERATED_SUCCESS_STATUS) {
            statusEvent = AGGREGATE_REPORT_GENERATED_SUCCESS_STATUS;
        } else if (statusCode
                == AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__AGGREGATE_AND_EVENT_REPORTS_GENERATED_SUCCESS_STATUS) {
            statusEvent = AGGREGATE_AND_EVENT_REPORTS_GENERATED_SUCCESS_STATUS;
        } else if (statusCode
                == AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__EVENT_REPORT_GENERATED_SUCCESS_STATUS) {
            statusEvent = EVENT_REPORT_GENERATED_SUCCESS_STATUS;
        } else {
            statusEvent = ATTRIBUTION_UNKNOWN_STATUS_CODE;
        }
        return statusEvent;
    }

    // Combines reporting status code and failure type into "status" dimension.
    private static int getReportingStatusEvent(int statusCode, int errorCode) {
        int statusEvent;
        if (statusCode == AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__RESPONSE_CODE__FAILURE) {
            statusEvent = errorCode >= 0 ? errorCode : REPORTING_UNKNOWN_FAILURE_STATUS_CODE;
        } else if (statusCode == AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__RESPONSE_CODE__SUCCESS) {
            statusEvent = REPORTING_SUCCESS_STATUS_CODE;
        } else {
            statusEvent = REPORTING_UNKNOWN_STATUS_CODE;
        }
        return statusEvent;
    }

    private static int hashEnrollmentIntoUnsignedInt(@Nullable String enrollmentId) {
        if (enrollmentId == null) {
            return 0;
        }
        // Hash string into 32 bit int then remove the sign bit since Cobalt's dimension supports
        // unsigned int up to 2^31 -1.
        return Hashing.murmur3_32_fixed().hashString(enrollmentId, UTF_8).asInt() & 0x7FFFFFFF;
    }
}
