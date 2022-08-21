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

import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_STATE;
import static android.adservices.common.AdServicesPermissions.MODIFY_ADSERVICES_STATE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;

import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_DISABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_ENABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.KEY_ADSERVICES_ENTRY_POINT_STATUS;

import android.adservices.common.IAdServicesCommonCallback;
import android.adservices.common.IAdServicesCommonService;
import android.adservices.common.IsAdServicesEnabledResult;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
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
    @RequiresPermission(ACCESS_ADSERVICES_STATE)
    public void isAdServicesEnabled(@NonNull IAdServicesCommonCallback callback) {
        boolean hasAccessAdServicesStatePermission =
                PermissionHelper.hasAccessAdServicesStatePermission(mContext);

        sBackgroundExecutor.execute(
                () -> {
                    try {
                        if (!hasAccessAdServicesStatePermission) {
                            callback.onFailure(STATUS_UNAUTHORIZED);
                            return;
                        }
                        callback.onResult(
                                new IsAdServicesEnabledResult.Builder()
                                        .setAdServicesEnabled(mFlags.getAdservicesEnableStatus())
                                        .build());
                    } catch (Exception e) {
                        try {
                            callback.onFailure(STATUS_INTERNAL_ERROR);
                        } catch (RemoteException re) {
                            LogUtil.e(re, "Unable to send result to the callback");
                        }
                    }
                });
    }

    /**
     * Set the adservices entry point Status from UI side, and also check adid zero-out status, and
     * Schedule notification if both adservices entry point enabled and adid not opt-out and
     * Adservice Is enabled
     */
    @Override
    @RequiresPermission(MODIFY_ADSERVICES_STATE)
    public void setAdServicesEnabled(boolean adServicesEntryPointEnabled, boolean adIdEnabled) {
        boolean hasModifyAdServicesStatePermission =
                PermissionHelper.hasModifyAdServicesStatePermission(mContext);
        sBackgroundExecutor.execute(
                () -> {
                    try {
                        if (!hasModifyAdServicesStatePermission) {
                            // TODO(b/242578032): handle the security exception in a better way
                            LogUtil.i("Caller is not authorized to control AdServices state");
                            return;
                        }

                        SharedPreferences preferences =
                                mContext.getSharedPreferences(
                                        ADSERVICES_STATUS_SHARED_PREFERENCE,
                                        Context.MODE_MULTI_PROCESS);

                        int adServiceEntryPointStatusInt =
                                adServicesEntryPointEnabled
                                        ? ADSERVICES_ENTRY_POINT_STATUS_ENABLE
                                        : ADSERVICES_ENTRY_POINT_STATUS_DISABLE;
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt(
                                KEY_ADSERVICES_ENTRY_POINT_STATUS, adServiceEntryPointStatusInt);
                        editor.apply();
                        LogUtil.i(
                                "adid status is "
                                        + adIdEnabled
                                        + ", adservice status is "
                                        + mFlags.getAdservicesEnableStatus());
                        if (mFlags.getAdservicesEnableStatus() && adServicesEntryPointEnabled) {
                            ConsentNotificationJobService.schedule(mContext, adIdEnabled);
                        }
                    } catch (Exception e) {
                        LogUtil.e(
                                "unable to save the adservices entry point status of "
                                        + e.getMessage());
                    }
                });
    }

    /** Init the AdServices Status Service. */
    public void init() {}
}
