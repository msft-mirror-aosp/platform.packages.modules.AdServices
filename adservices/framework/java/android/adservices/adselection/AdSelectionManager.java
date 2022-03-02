/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.adservices.adselection;

import android.adservices.exceptions.AdServicesException;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.content.Context;
import android.os.OutcomeReceiver;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * AdSelection Manager.
 *
 * @hide
 */
public class AdSelectionManager {
    public static final String AD_SELECTION_SERVICE = "ad_selection_service";

    /**
     * This field will be used once full implementation is ready.
     *
     * TODO(b/212300065) remove the warning suppression once the service is implemented.
     */
    @SuppressWarnings("unused")
    private final Context mContext;

    private final ServiceBinder<AdSelectionService> mServiceBinder;

    /**
     * Create AdSelectionManager
     *
     * @hide
     */
    public AdSelectionManager(@NonNull Context context) {
        mContext = context;
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        AdServicesCommon.ACTION_AD_SELECTION_SERVICE,
                        AdSelectionService.Stub::asInterface);
    }

    @NonNull
    private AdSelectionService getService() {
        AdSelectionService service = mServiceBinder.getService();
        if (service == null) {
            throw new IllegalStateException("Unable to find the service");
        }
        return service;
    }

    /** Report the given impression. */
    @NonNull
    public void reportImpression(
            @NonNull int adSelectionId,
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, AdServicesException> receiver) {
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = getService();
            service.reportImpression(
                    new ReportImpressionRequest.Builder()
                            .setAdSelectionId(adSelectionId)
                            .setAdSelectionConfig(adSelectionConfig)
                            .build(),
                    new ReportImpressionCallback.Stub() {
                        @Override
                        public void onResult(ReportImpressionResponse resultParcel) {
                            executor.execute(
                                    () -> {
                                        if (resultParcel.isSuccess()) {
                                            receiver.onResult(null);
                                        } else {
                                            receiver.onError(
                                                    new AdServicesException(
                                                            resultParcel.getErrorMessage()));
                                        }
                                    });
                        }
                    });
        } catch (Exception e) {
            LogUtil.e("Exception", e);
            receiver.onError(new AdServicesException("Internal Error!", e));
        }
    }
}
