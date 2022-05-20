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
package com.android.server.adservices;

import android.adservices.common.AdServicesCommonManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.android.server.SystemService;

/**
 * @hide
 */
public class AdServicesManagerService {
    private static final String TAG = "AdServicesManagerService";
    private final Context mContext;

    private final Handler mHandler;

    private AdServicesManagerService(Context context) {
        mContext = context;

        // Start the handler thread.
        HandlerThread handlerThread = new HandlerThread("AdServicesManagerServiceHandler");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
        registerBootBroadcastReceivers();
    }

    /** @hide */
    public static class Lifecycle extends SystemService {
        private AdServicesManagerService mService;

        /** @hide */
        public Lifecycle(Context context) {
            super(context);
            mService = new AdServicesManagerService(getContext());
        }

        /** @hide */
        @Override
        public void onStart() {
            Log.d(TAG, "AdServicesManagerService started!");
        }
    }

    private void registerBootBroadcastReceivers() {
        BroadcastReceiver bootIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch(intent.getAction()) {
                    case Intent.ACTION_BOOT_COMPLETED:
                        registerPackagedChangedBroadcastReceivers();
                }
            }
        };
        mContext.registerReceiver(bootIntentReceiver,
                new IntentFilter(Intent.ACTION_BOOT_COMPLETED));
        Log.d(TAG, "Boot Broadcast Receivers registered.");
    }

    private void registerPackagedChangedBroadcastReceivers() {
        final IntentFilter packageChangedIntentFilter = new IntentFilter();

        packageChangedIntentFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        // TODO(b/229412239): Add other actions.
        packageChangedIntentFilter.addDataScheme("package");

        BroadcastReceiver packageChangedIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received for intent: " + intent);
                switch(intent.getAction()) {
                    case Intent.ACTION_PACKAGE_FULLY_REMOVED:
                        Uri packageUri = intent.getData();
                        Log.d(TAG, "Removed package: " + packageUri.toString());
                        onPackageFullyRemoved(packageUri);
                    // TODO(b/229412239): Add other actions.
                }
            }
        };
        mContext.registerReceiver(packageChangedIntentReceiver, packageChangedIntentFilter,
                null, mHandler);
        Log.d(TAG, "Package changed Broadcast Receivers registered.");
    }

    private void onPackageFullyRemoved(Uri packageUri) {
        mHandler.post(() -> mContext.getSystemService(AdServicesCommonManager.class)
                .onPackageFullyRemoved(packageUri));

    }
}
