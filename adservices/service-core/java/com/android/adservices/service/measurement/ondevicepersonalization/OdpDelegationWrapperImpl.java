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

import static android.adservices.ondevicepersonalization.OnDevicePersonalizationPermissions.NOTIFY_MEASUREMENT_EVENT;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_GET_MANAGER_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_GET_MANAGER_TIMEOUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_INVALID_HEADER_FIELD_VALUE_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_INVALID_HEADER_FORMAT_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_JSON_PARSING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_MISSING_REQUIRED_HEADER_FIELD_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_PARSING_UNKNOWN_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_NOTIFY_REGISTRATION_TO_ODP;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_PROCESS_ODP_REGISTRATION;

import android.adservices.ondevicepersonalization.MeasurementWebTriggerEventParams;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationSystemEventManager;
import android.annotation.RequiresPermission;
import android.content.ComponentName;
import android.content.Context;
import android.os.OutcomeReceiver;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.registration.AsyncRegistration;
import com.android.adservices.service.measurement.registration.FetcherUtil;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementOdpApiCallStats;
import com.android.adservices.service.stats.MeasurementOdpRegistrationStats;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class OdpDelegationWrapperImpl implements IOdpDelegationWrapper {
    private final OnDevicePersonalizationSystemEventManager mOdpSystemEventManager;
    private final AdServicesLogger mLogger;
    private final Clock mClock;
    private final Flags mFlags;

    // Lazy initialization holder class idiom for static fields as described in Effective Java Item
    // 83 - this is needed because otherwise the singleton would be initialized in unit tests, even
    // when they (correctly) call newInstance() instead of getInstance().
    private static final class FieldHolder {
        private static OdpDelegationWrapperImpl sSingleton;

        static {
            sSingleton =
                    getOdpWrapperImpl(
                            ApplicationContextSingleton.get(),
                            AdServicesLoggerImpl.getInstance(),
                            FlagsFactory.getFlags());
        }
    }

    /** Returns the singleton instance of the OdpDelegationWrapperImpl. */
    public static OdpDelegationWrapperImpl getInstance() {
        return FieldHolder.sSingleton;
    }

    /** Factory method - should only be used for tests. */
    @VisibleForTesting
    public static OdpDelegationWrapperImpl createInstanceForTest(
            OnDevicePersonalizationSystemEventManager manager,
            AdServicesLogger logger,
            Flags flags) {
        FieldHolder.sSingleton = new OdpDelegationWrapperImpl(manager, logger, flags);
        return FieldHolder.sSingleton;
    }

    private OdpDelegationWrapperImpl(
            OnDevicePersonalizationSystemEventManager manager,
            AdServicesLogger logger,
            Flags flags) {
        mOdpSystemEventManager = manager;
        mLogger = logger;
        mClock = Clock.getInstance();
        mFlags = flags;
    }

    private static OdpDelegationWrapperImpl getOdpWrapperImpl(
            Context context, AdServicesLogger logger, Flags flags) {
        OnDevicePersonalizationSystemEventManager manager = getOdpDelegationManager(context, flags);
        if (manager == null) {
            return null;
        }
        return new OdpDelegationWrapperImpl(manager, logger, flags);
    }

    /** Returns ODP system service as a listenable future. */
    @SuppressWarnings("AvoidStaticContext")
    public static ListenableFuture<OnDevicePersonalizationSystemEventManager> getOdpServiceFuture(
            Context context) {
        ListeningExecutorService executor = AdServicesExecutors.getBackgroundExecutor();
        ListenableFuture<OnDevicePersonalizationSystemEventManager> future =
                executor.submit(
                        () ->
                                context.getSystemService(
                                        OnDevicePersonalizationSystemEventManager.class));
        return future;
    }

    /** Checks for ODP availability and returns ODP system event manager. */
    @SuppressWarnings("AvoidStaticContext")
    public static OnDevicePersonalizationSystemEventManager getOdpDelegationManager(
            Context context, Flags flags) {
        if (!SdkLevel.isAtLeastT() || !flags.getMeasurementEnableOdpWebTriggerRegistration()) {
            return null;
        }
        OnDevicePersonalizationSystemEventManager odpSystemEventManager = null;
        try {
            ListenableFuture<OnDevicePersonalizationSystemEventManager> future =
                    getOdpServiceFuture(context);
            odpSystemEventManager = future.get(2000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LoggerFactory.getMeasurementLogger().d(e, "getOdpDelegationManager: Timeout Exception");
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_GET_MANAGER_TIMEOUT,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        } catch (Exception e) {
            LoggerFactory.getMeasurementLogger().d(e, "getOdpDelegationManager: Unknown Exception");
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_GET_MANAGER_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        }
        return odpSystemEventManager;
    }

    /** Calls the notifyMeasurementEvent API. */
    @Override
    @RequiresPermission(NOTIFY_MEASUREMENT_EVENT)
    public void registerOdpTrigger(
            AsyncRegistration asyncRegistration,
            Map<String, List<String>> headers,
            boolean isValidEnrollment) {
        Objects.requireNonNull(asyncRegistration);
        Objects.requireNonNull(headers);
        LoggerFactory.getMeasurementLogger().d("registerOdpTrigger: ODP is available");

        OdpRegistrationStatus odpRegistrationStatus = new OdpRegistrationStatus();
        odpRegistrationStatus.setRegistrationType(OdpRegistrationStatus.RegistrationType.TRIGGER);

        if (!isValidEnrollment) {
            odpRegistrationStatus.setRegistrationStatus(
                    OdpRegistrationStatus.RegistrationStatus.INVALID_ENROLLMENT);
            logOdpRegistrationMetrics(odpRegistrationStatus);
            return;
        }

        if (FetcherUtil.calculateHeadersCharactersLength(headers)
                > mFlags.getMaxOdpTriggerRegistrationHeaderSizeBytes()) {
            LoggerFactory.getMeasurementLogger()
                    .d("registerOdpTrigger: Header size limit exceeded");
            odpRegistrationStatus.setRegistrationStatus(
                    OdpRegistrationStatus.RegistrationStatus.HEADER_SIZE_LIMIT_EXCEEDED);
            logOdpRegistrationMetrics(odpRegistrationStatus);
            return;
        }

        List<String> field = headers.get(OdpTriggerHeaderContract.HEADER_ODP_REGISTER_TRIGGER);
        if (field == null || field.size() != 1) {
            LoggerFactory.getMeasurementLogger().d("registerOdpTrigger: Invalid header format");
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_INVALID_HEADER_FORMAT_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            odpRegistrationStatus.setRegistrationStatus(
                    OdpRegistrationStatus.RegistrationStatus.INVALID_HEADER_FORMAT);
            logOdpRegistrationMetrics(odpRegistrationStatus);
            return;
        }

        try {
            JSONObject json = new JSONObject(field.get(0));
            if (json.isNull(OdpTriggerHeaderContract.ODP_SERVICE)) {
                LoggerFactory.getMeasurementLogger()
                        .d("registerOdpTrigger: Missing required field: Service");
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_MISSING_REQUIRED_HEADER_FIELD_ERROR,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
                odpRegistrationStatus.setRegistrationStatus(
                        OdpRegistrationStatus.RegistrationStatus.MISSING_REQUIRED_HEADER_FIELD);
                return;
            }

            ComponentName componentName =
                    ComponentName.unflattenFromString(
                            json.getString(OdpTriggerHeaderContract.ODP_SERVICE));
            if (componentName == null) {
                LoggerFactory.getMeasurementLogger()
                        .d("registerOdpTrigger: Invalid field format: Service");
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_INVALID_HEADER_FIELD_VALUE_ERROR,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
                odpRegistrationStatus.setRegistrationStatus(
                        OdpRegistrationStatus.RegistrationStatus.INVALID_HEADER_FIELD_VALUE);
                return;
            }

            MeasurementWebTriggerEventParams.Builder builder =
                    new MeasurementWebTriggerEventParams.Builder(
                            asyncRegistration.getTopOrigin(),
                            asyncRegistration.getRegistrant().toString(),
                            componentName);

            if (!json.isNull(OdpTriggerHeaderContract.ODP_CERT_DIGEST)) {
                builder.setCertDigest(json.getString(OdpTriggerHeaderContract.ODP_CERT_DIGEST));
            }
            if (!json.isNull(OdpTriggerHeaderContract.ODP_DATA)) {
                builder.setEventData(
                        json.getString(OdpTriggerHeaderContract.ODP_DATA)
                                .getBytes(StandardCharsets.UTF_8));
            }

            final long startServiceTime = mClock.elapsedRealtime();
            mOdpSystemEventManager.notifyMeasurementEvent(
                    builder.build(),
                    AdServicesExecutors.getLightWeightExecutor(),
                    new OutcomeReceiver<Void, Exception>() {
                        @Override
                        public void onResult(Void result) {
                            LoggerFactory.getMeasurementLogger()
                                    .d("Trigger successful sent to ODP module");
                            long latency = mClock.elapsedRealtime() - startServiceTime;
                            logOdpApiCallMetrics(
                                    latency, OdpApiCallStatus.ApiCallStatus.SUCCESS.getValue());
                        }

                        @Override
                        public void onError(Exception exception) {
                            LoggerFactory.getMeasurementLogger()
                                    .e(exception, "Trigger failed to be sent to ODP module");
                            long latency = mClock.elapsedRealtime() - startServiceTime;
                            logOdpApiCallMetrics(
                                    latency, OdpApiCallStatus.ApiCallStatus.FAILURE.getValue());
                        }
                    });
            odpRegistrationStatus.setRegistrationStatus(
                    OdpRegistrationStatus.RegistrationStatus.SUCCESS);
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger().d(e, "registerOdpTrigger: JSONException");
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_JSON_PARSING_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            odpRegistrationStatus.setRegistrationStatus(
                    OdpRegistrationStatus.RegistrationStatus.PARSING_EXCEPTION);
        } catch (Exception e) {
            LoggerFactory.getMeasurementLogger().d(e, "registerOdpTrigger: Unknown Exception");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_PARSING_UNKNOWN_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            odpRegistrationStatus.setRegistrationStatus(
                    OdpRegistrationStatus.RegistrationStatus.PARSING_EXCEPTION);
        } finally {
            logOdpRegistrationMetrics(odpRegistrationStatus);
        }
    }

    @Override
    public void logOdpRegistrationMetrics(OdpRegistrationStatus odpRegistrationStatus) {
        mLogger.logMeasurementOdpRegistrations(
                new MeasurementOdpRegistrationStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_PROCESS_ODP_REGISTRATION)
                        .setRegistrationType(odpRegistrationStatus.getRegistrationType().getValue())
                        .setRegistrationStatus(
                                odpRegistrationStatus.getRegistrationStatus().getValue())
                        .build());
    }

    private void logOdpApiCallMetrics(long latency, int status) {
        mLogger.logMeasurementOdpApiCall(
                new MeasurementOdpApiCallStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_NOTIFY_REGISTRATION_TO_ODP)
                        .setLatency(latency)
                        .setApiCallStatus(status)
                        .build());
    }

    private interface OdpTriggerHeaderContract {
        String HEADER_ODP_REGISTER_TRIGGER = "Odp-Register-Trigger";
        String ODP_SERVICE = "service";
        String ODP_CERT_DIGEST = "certDigest";
        String ODP_DATA = "data";
    }
}
