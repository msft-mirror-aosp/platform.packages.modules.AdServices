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

import static com.example.adservices.samples.ui.sampletestapp.MyBroadcastReceiver.TAG;

import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Android application activity that is guarded by
 * "android.adservices.common.action.VIEW_ADSERVICES_CONSENT_PAGE" action filter. This is opened
 * when the above intent is fired on notification click.
 */
public final class MainActivity extends AppCompatActivity {
    private final MyBroadcastReceiver mReceiver = new MyBroadcastReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_ADSERVICES_NOTIFICATION_DISPLAYED);
        Context context = getApplicationContext();
        ContextCompat.registerReceiver(context, mReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
        Log.d(TAG, "BroadcastReceiver Registered");
    }
}
