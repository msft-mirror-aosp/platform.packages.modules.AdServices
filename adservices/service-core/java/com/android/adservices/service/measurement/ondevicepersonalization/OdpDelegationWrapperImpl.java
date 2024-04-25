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
import android.os.Build;
import android.os.OutcomeReceiver;

import androidx.annotation.RequiresApi;

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
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class OdpDelegationWrapperImpl implements IOdpDelegationWrapper {
    private OnDevicePersonalizationSystemEventManager mOdpSystemEventManager;
    private final AdServicesLogger mLogger;
    private final Clock mClock;
    private final Flags mFlags;

    public OdpDelegationWrapperImpl(OnDevicePersonalizationSystemEventManager manager) {
        this(manager, AdServicesLoggerImpl.getInstance(), FlagsFactory.getFlags());
    }

    @VisibleForTesting
    public OdpDelegationWrapperImpl(
            OnDevicePersonalizationSystemEventManager manager,
            AdServicesLogger logger,
            Flags flags) {
        Objects.requireNonNull(manager);
        mOdpSystemEventManager = manager;
        mLogger = logger;
        mClock = Clock.getInstance();
        mFlags = flags;
    }

    /** Calls the notifyMeasurementEvent API. */
    @Override
    @RequiresPermission(NOTIFY_MEASUREMENT_EVENT)
    public void registerOdpTrigger(
            AsyncRegistration asyncRegistration, Map<String, List<String>> headers) {
        Objects.requireNonNull(asyncRegistration);
        Objects.requireNonNull(headers);

        OdpRegistrationStatus odpRegistrationStatus = new OdpRegistrationStatus();
        odpRegistrationStatus.setRegistrationType(OdpRegistrationStatus.RegistrationType.TRIGGER);

        if (FetcherUtil.calculateHeadersCharactersLength(headers)
                > mFlags.getMaxOdpTriggerRegistrationHeaderSizeBytes()) {
            LoggerFactory.getMeasurementLogger()
                    .d("registerOdpTrigger: Header size limit exceeded");
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
        } catch (Exception e) {
            LoggerFactory.getMeasurementLogger().d(e, "registerOdpTrigger: Unknown Exception");
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_PARSING_UNKNOWN_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        } finally {
            logOdpRegistrationMetrics(odpRegistrationStatus);
        }
    }

    private void logOdpRegistrationMetrics(OdpRegistrationStatus odpRegistrationStatus) {
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
