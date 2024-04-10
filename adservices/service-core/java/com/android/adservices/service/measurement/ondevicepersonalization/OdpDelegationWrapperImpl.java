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

import android.adservices.ondevicepersonalization.MeasurementWebTriggerEventParams;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationSystemEventManager;
import android.annotation.RequiresPermission;
import android.content.ComponentName;
import android.os.Build;
import android.os.OutcomeReceiver;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.measurement.registration.AsyncRegistration;
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
    private boolean mDisableApiCallForTesting;

    public OdpDelegationWrapperImpl(OnDevicePersonalizationSystemEventManager manager) {
        this(manager, /* disableApiCallForTesting */ false);
    }

    @VisibleForTesting
    public OdpDelegationWrapperImpl(
            OnDevicePersonalizationSystemEventManager manager, boolean disableApiCallForTesting) {
        if (!disableApiCallForTesting) {
            Objects.requireNonNull(manager);
        }
        mOdpSystemEventManager = manager;
        mDisableApiCallForTesting = disableApiCallForTesting;
    }

    /** Calls the notifyMeasurementEvent API. */
    @Override
    @RequiresPermission(NOTIFY_MEASUREMENT_EVENT)
    public boolean registerOdpTrigger(
            AsyncRegistration asyncRegistration, Map<String, List<String>> headers) {
        Objects.requireNonNull(asyncRegistration);
        Objects.requireNonNull(headers);

        if (!isOdpTriggerHeaderPresent(headers)) {
            return false;
        }

        List<String> field = headers.get(OdpTriggerHeaderContract.HEADER_ODP_REGISTER_TRIGGER);
        if (field == null || field.size() != 1) {
            // TODO Add WW & CEL logging for exceptions (b/330784221)
            LoggerFactory.getMeasurementLogger().d("registerOdpTrigger: Invalid header format");
            return false;
        }

        try {
            JSONObject json = new JSONObject(field.get(0));
            if (json.isNull(OdpTriggerHeaderContract.ODP_SERVICE)) {
                // TODO Add WW & CEL logging for exceptions (b/330784221)
                LoggerFactory.getMeasurementLogger()
                        .d("registerOdpTrigger: Missing required field: Service");
                return false;
            }

            ComponentName componentName =
                    ComponentName.unflattenFromString(
                            json.getString(OdpTriggerHeaderContract.ODP_SERVICE));
            if (componentName == null) {
                // TODO Add WW & CEL logging for exceptions (b/330784221)
                LoggerFactory.getMeasurementLogger()
                        .d("registerOdpTrigger: Invalid field format: Service");
                return false;
            }

            // Prevent usage of classes from the OnDevicePersonalization module in unit tests
            if (mDisableApiCallForTesting) {
                return true;
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

            mOdpSystemEventManager.notifyMeasurementEvent(
                    builder.build(),
                    AdServicesExecutors.getLightWeightExecutor(),
                    new OutcomeReceiver<Void, Exception>() {
                        @Override
                        public void onResult(Void result) {
                            LoggerFactory.getMeasurementLogger()
                                    .d("Trigger successful sent to ODP module");
                        }

                        @Override
                        public void onError(Exception exception) {
                            LoggerFactory.getMeasurementLogger()
                                    .e(exception, "Trigger failed to be sent to ODP module");
                        }
                    });
            return true;
        } catch (JSONException e) {
            // TODO Add WW & CEL logging for exceptions (b/330784221)
            LoggerFactory.getMeasurementLogger().d(e, "registerOdpTrigger: JSONException");
        } catch (Exception e) {
            // TODO Add WW & CEL logging for exceptions (b/330784221)
            LoggerFactory.getMeasurementLogger().d(e, "registerOdpTrigger: Unknown Exception");
        }
        return false;
    }

    private boolean isOdpTriggerHeaderPresent(Map<String, List<String>> headers) {
        return headers.containsKey(OdpTriggerHeaderContract.HEADER_ODP_REGISTER_TRIGGER);
    }

    private interface OdpTriggerHeaderContract {
        String HEADER_ODP_REGISTER_TRIGGER = "Odp-Register-Trigger";
        String ODP_SERVICE = "service";
        String ODP_CERT_DIGEST = "certDigest";
        String ODP_DATA = "data";
    }
}
