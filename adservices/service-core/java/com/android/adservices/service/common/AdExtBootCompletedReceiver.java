/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__BACK_COMPAT_INIT_BOOT_COMPLETED_RECEIVER_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.adservices.LogUtil;
import com.android.adservices.errorlogging.ErrorLogUtil;

/** Handles the BootCompleted initialization for AdExtServices APK on S. */
public class AdExtBootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            LogUtil.i("AdExtBootCompletedReceiver onReceive invoked");
            AdServicesBackCompatInit.getInstance().initializeComponents();
        } catch (Exception e) {
            LogUtil.e(e, "AdExtBootCompletedReceiver onReceive failed");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__BACK_COMPAT_INIT_BOOT_COMPLETED_RECEIVER_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        }
    }
}
