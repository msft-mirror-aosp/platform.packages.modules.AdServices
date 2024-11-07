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
package com.example.adservices.samples.ui.sampletestapp;

import static android.adservices.common.AdServicesCommonManager.ACTION_ADSERVICES_NOTIFICATION_DISPLAYED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Android broadcast receiver that listens to
 * "android.adservices.common.action.ADSERVICES_NOTIFICATION_DISPLAYED" action filter. This is
 * triggered when the above intent is fired on notification display.
 */
public final class MyBroadcastReceiver extends BroadcastReceiver {

    public static final String TAG = "MyBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Get the broadcast action
        String action = intent.getAction();

        if (action.equals(ACTION_ADSERVICES_NOTIFICATION_DISPLAYED)) {
            Log.d(TAG, "Received broadcast: " + action + " to " + context.getPackageName());
        }
    }
}
