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

package com.android.adservices.service.common;

import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_DISABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_ENABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.KEY_ADSERVICES_ENTRY_POINT_STATUS;

import android.adservices.common.IAdServicesCommonCallback;
import android.adservices.common.IAdServicesCommonService;
import android.adservices.common.IsAdServicesEnabledResult;
import android.annotation.NonNull;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;

import java.util.concurrent.Executor;

/**
 * Implementation of {@link IAdServicesCommonService}.
 *
 * @hide
 */
public class AdServicesCommonServiceImpl extends
        IAdServicesCommonService.Stub {

    private final Context mContext;
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();
    private final Flags mFlags;
    public final String ADSERVICES_STATUS_SHARED_PREFERENCE = "AdserviceStatusSharedPreference";

    public AdServicesCommonServiceImpl(Context context, Flags flags) {
        mContext = context;
        mFlags = flags;
    }

    @Override
    public void isAdServicesEnabled(@NonNull IAdServicesCommonCallback callback) {
        sBackgroundExecutor.execute(
                () -> {
                    try {
                        callback.onResult(
                                new IsAdServicesEnabledResult.Builder()
                                        .setAdServicesEnabled(mFlags.getAdservicesEnableStatus())
                                        .build());
                    } catch (Exception e) {
                        try {
                            callback.onFailure("getting AdServices status error");
                        } catch (RemoteException re) {
                            LogUtil.e("Unable to send result to the callback", re);
                        }
                    }
                });
    }

    /** Set the adservices entry point Status from UI side */
    @Override
    public void setAdServicesEntryPointEnabled(boolean adservicesEntryPointStatus) {
        sBackgroundExecutor.execute(
                () -> {
                    try {
                        SharedPreferences preferences =
                                mContext.getSharedPreferences(
                                        ADSERVICES_STATUS_SHARED_PREFERENCE, Context.MODE_PRIVATE);

                        int adserviceEntryPointStatusInt =
                                adservicesEntryPointStatus
                                        ? ADSERVICES_ENTRY_POINT_STATUS_ENABLE
                                        : ADSERVICES_ENTRY_POINT_STATUS_DISABLE;
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt(
                                KEY_ADSERVICES_ENTRY_POINT_STATUS, adserviceEntryPointStatusInt);
                        editor.apply();
                    } catch (Exception e) {
                        LogUtil.e("unable to save the adservices entry point status");
                    }
                });
    }

    /** Init the AdServices Status Service. */
    public void init() {}
}
