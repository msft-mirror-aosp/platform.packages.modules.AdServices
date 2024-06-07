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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__SUCCESS_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS;

import android.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.cobalt.CobaltLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.Objects;

/**
 * Wrapper around {@link CobaltLogger} that logs a measurement source or trigger registration with
 * {@code SurfaceType, SourceOrTriggerType, Status, Region} and app package name to Cobalt.
 */
public final class MeasurementCobaltLogger {
    // The measurement registration metric has an id of 3.
    //
    // See //packages/modules/AdServices/adservices/service-core/resources/cobalt_registry.textpb
    // for the full metric.`
    private static final int REGISTRATION_METRIC_ID = 3;

    // --------------------- Constants for dimension "source_or_trigger_type" ----------------------
    public static final int UNKNOWN_SOURCE_TYPE = 0;
    public static final int TRIGGER_TYPE_CODE = 100;
    public static final int UNKNOWN_TYPE = 200;

    // --------------------- Constants for dimension "status" -----------------------
    public static final int UNKNOWN_FAILURE_CODE = 0;
    public static final int SUCCESS_STATUS_CODE = 100;
    public static final int UNKNOWN_STATUS_CODE = 200;

    // --------------------- Constants for dimension "region" -----------------------
    public static final int EEA_REGION_CODE = 1;
    public static final int ROW_REGION_CODE = 2;

    private static final MeasurementCobaltLogger sInstance = new MeasurementCobaltLogger();

    @Nullable private final CobaltLogger mCobaltLogger;

    /** Returns the singleton of the {@code MeasurementCobaltLogger}. */
    public static MeasurementCobaltLogger getInstance() {
        return sInstance;
    }

    @VisibleForTesting
    MeasurementCobaltLogger(CobaltLogger cobaltLogger) {
        this.mCobaltLogger = cobaltLogger;
    }

    @VisibleForTesting
    MeasurementCobaltLogger() {
        this(getDefaultCobaltLogger());
    }

    @Nullable
    private static CobaltLogger getDefaultCobaltLogger() {
        CobaltLogger logger = null;
        try {
            Flags flags = FlagsFactory.getFlags();
            if (flags.getMsmtRegistrationCobaltLoggingEnabled()) {
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
            boolean isEeaDevice) {
        if (!isEnabled()) {
            LogUtil.w("Skip logRegistrationStatus because Cobalt logger is null");
            return;
        }
        Objects.requireNonNull(appPackageName, "appPackageName cannot be null");

        mCobaltLogger.logString(
                REGISTRATION_METRIC_ID,
                appPackageName,
                ImmutableList.of(
                        surfaceType,
                        getSourceTriggerType(type, sourceType),
                        getStatusEvent(statusCode, errorCode),
                        isEeaDevice ? EEA_REGION_CODE : ROW_REGION_CODE));
    }

    @VisibleForTesting
    boolean isEnabled() {
        return mCobaltLogger != null;
    }

    // Combines trigger and source types into the "source_or_trigger_type" dimension.
    @VisibleForTesting
    static int getSourceTriggerType(int type, int sourceType) {
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

    // Combines status code and failure type into "status" dimension.
    @VisibleForTesting
    static int getStatusEvent(int statusCode, int errorCode) {
        int statusEvent;
        if (statusCode == AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__FAILURE_STATUS) {
            // When the registration fails, the error code logging should match enum
            // RegistrationFailureType in
            // frameworks/proto_logging/stats/enums/adservices/measurement/enums.proto
            statusEvent = (errorCode >= 0) ? errorCode : UNKNOWN_FAILURE_CODE;
        } else if (statusCode == AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__SUCCESS_STATUS) {
            statusEvent = SUCCESS_STATUS_CODE;
        } else {
            statusEvent = UNKNOWN_STATUS_CODE;
        }
        return statusEvent;
    }
}
