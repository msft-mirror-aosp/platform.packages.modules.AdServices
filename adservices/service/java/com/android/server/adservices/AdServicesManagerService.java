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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemService;

import java.util.List;

/**
 * @hide
 */
public class AdServicesManagerService {
    private static final String TAG = "AdServicesManagerService";

    /**
     * Broadcast send from the system service to the AdServices module when a package has been
     * installed/uninstalled. This intent must match the intent defined in the AdServices manifest.
     */
    private static final String PACKAGE_CHANGED_BROADCAST =
            "com.android.adservices.PACKAGE_CHANGED";

    /** Key for designating the specific action. */
    private static final String ACTION_KEY = "action";

    /** Value if the package change was an uninstallation. */
    private static final String PACKAGE_FULLY_REMOVED = "package_fully_removed";

    /** Value if the package change was an installation. */
    private static final String PACKAGE_ADDED = "package_added";

    /** Value if the package has its data cleared. */
    private static final String PACKAGE_DATA_CLEARED = "package_data_cleared";

    private final Context mContext;
    private final Handler mHandler;

    @VisibleForTesting
    AdServicesManagerService(Context context) {
        mContext = context;

        // Start the handler thread.
        HandlerThread handlerThread = new HandlerThread("AdServicesManagerServiceHandler");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
        registerPackagedChangedBroadcastReceivers();
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

    /**
     * Registers a receiver for any broadcasts regarding changes to any packages for all users on
     * the device at boot up. After receiving the broadcast, send an explicit broadcast to the
     * AdServices module as that user.
     */
    private void registerPackagedChangedBroadcastReceivers() {
        final IntentFilter packageChangedIntentFilter = new IntentFilter();

        packageChangedIntentFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        packageChangedIntentFilter.addAction(Intent.ACTION_PACKAGE_DATA_CLEARED);
        packageChangedIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageChangedIntentFilter.addDataScheme("package");

        BroadcastReceiver systemServicePackageChangedReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        UserHandle user = getSendingUser();
                        mHandler.post(() -> onPackageChange(intent, user));
                    }
                };
        mContext.registerReceiverForAllUsers(
                systemServicePackageChangedReceiver,
                packageChangedIntentFilter,
                /* broadcastPermission */ null,
                mHandler);
        Log.d(TAG, "Package changed broadcast receivers registered.");
    }

    /** Sends an explicit broadcast to the AdServices module when a package change occurs. */
    @VisibleForTesting
    public void onPackageChange(Intent intent, UserHandle user) {
        Intent explicitBroadcast = new Intent();
        explicitBroadcast.setAction(PACKAGE_CHANGED_BROADCAST);
        explicitBroadcast.setData(intent.getData());

        final Intent i = new Intent(PACKAGE_CHANGED_BROADCAST);
        final List<ResolveInfo> resolveInfo =
                mContext.getPackageManager()
                        .queryBroadcastReceiversAsUser(i, PackageManager.GET_RECEIVERS, user);
        if (resolveInfo != null && !resolveInfo.isEmpty()) {
            for (ResolveInfo info : resolveInfo) {
                explicitBroadcast.setClassName(
                        info.activityInfo.packageName, info.activityInfo.name);
                switch (intent.getAction()) {
                    case Intent.ACTION_PACKAGE_DATA_CLEARED:
                        explicitBroadcast.putExtra(ACTION_KEY, PACKAGE_DATA_CLEARED);
                        mContext.sendBroadcastAsUser(explicitBroadcast, user);
                        break;
                    case Intent.ACTION_PACKAGE_FULLY_REMOVED:
                        // TODO (b/233373604): Propagate broadcast to users not currently running
                        explicitBroadcast.putExtra(ACTION_KEY, PACKAGE_FULLY_REMOVED);
                        mContext.sendBroadcastAsUser(explicitBroadcast, user);
                        break;
                    case Intent.ACTION_PACKAGE_ADDED:
                        explicitBroadcast.putExtra(ACTION_KEY, PACKAGE_ADDED);
                        // For users where the app is merely being updated rather than added, we
                        // don't want to send the broadcast.
                        if (!intent.getExtras().getBoolean(Intent.EXTRA_REPLACING, false)) {
                            mContext.sendBroadcastAsUser(explicitBroadcast, user);
                        }
                        break;
                }
            }
        }
    }
}
