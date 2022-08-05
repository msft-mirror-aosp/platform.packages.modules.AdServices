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

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.Executor;

/**
 * Receiver to receive a com.android.adservices.PACKAGE_CHANGED broadcast from the AdServices system
 * service when package install/uninstalls occur.
 */
public class PackageChangedReceiver extends BroadcastReceiver {

    /**
     * Broadcast send from the system service to the AdServices module when a package has been
     * installed/uninstalled.
     */
    public static final String PACKAGE_CHANGED_BROADCAST = "com.android.adservices.PACKAGE_CHANGED";

    /** Key for designating if the action was an installation or an uninstallation. */
    public static final String ACTION_KEY = "action";

    /** Value if the package change was an uninstallation. */
    public static final String PACKAGE_FULLY_REMOVED = "package_fully_removed";

    /** Value if the package change was an installation. */
    public static final String PACKAGE_ADDED = "package_added";

    /** Value if the package had its data cleared. */
    public static final String PACKAGE_DATA_CLEARED = "package_data_cleared";

    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        LogUtil.d("PackageChangedReceiver received a broadcast: " + intent.getAction());
        switch (intent.getAction()) {
            case PACKAGE_CHANGED_BROADCAST:
                Uri packageUri = Uri.parse(intent.getData().getSchemeSpecificPart());
                switch (intent.getStringExtra(ACTION_KEY)) {
                    case PACKAGE_FULLY_REMOVED:
                        onPackageFullyRemoved(context, packageUri);
                        topicsOnPackageFullyRemoved(context, packageUri);
                        break;
                    case PACKAGE_ADDED:
                        onPackageAdded(context, packageUri);
                        topicsOnPackageAdded(context, packageUri);
                        break;
                    case PACKAGE_DATA_CLEARED:
                        onPackageDataCleared(context, packageUri);
                        break;
                }
                break;
        }
    }

    @VisibleForTesting
    void onPackageFullyRemoved(Context context, Uri packageUri) {
        LogUtil.i("Package Fully Removed:" + packageUri);
        sBackgroundExecutor.execute(
                () -> MeasurementImpl.getInstance(context).deletePackageRecords(packageUri));
    }

    void onPackageDataCleared(Context context, Uri packageUri) {
        LogUtil.i("Package Data Cleared: " + packageUri);
        sBackgroundExecutor.execute(
                () -> MeasurementImpl.getInstance(context).deletePackageRecords(packageUri));
    }

    @VisibleForTesting
    void onPackageAdded(Context context, Uri packageUri) {
        LogUtil.i("Package Added: " + packageUri);
        sBackgroundExecutor.execute(
                () ->
                        MeasurementImpl.getInstance(context)
                                .doInstallAttribution(packageUri, System.currentTimeMillis()));
    }

    private void topicsOnPackageFullyRemoved(Context context, @NonNull Uri packageUri) {
        if (FlagsFactory.getFlags().getTopicsKillSwitch()) {
            LogUtil.e("Topics API is disabled");
            return;
        }

        LogUtil.d("Deleting topics data for package: " + packageUri.toString());
        sBackgroundExecutor.execute(
                () -> TopicsWorker.getInstance(context).deletePackageData(packageUri));
    }

    private void topicsOnPackageAdded(Context context, @NonNull Uri packageUri) {
        LogUtil.d("Package Added for topics API: " + packageUri.toString());
        sBackgroundExecutor.execute(
                () -> TopicsWorker.getInstance(context).handleAppInstallation(packageUri));
    }
}
