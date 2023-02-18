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

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
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

    private static final int DEFAULT_PACKAGE_UID = -1;

    /** Enable the PackageChangedReceiver */
    public static boolean enableReceiver(@NonNull Context context) {
        try {
            context.getPackageManager()
                    .setComponentEnabledSetting(
                            new ComponentName(context, PackageChangedReceiver.class),
                            COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);
        } catch (IllegalArgumentException e) {
            LogUtil.e("enableService failed for %s", context.getPackageName());
            return false;
        }
        return true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        LogUtil.d("PackageChangedReceiver received a broadcast: " + intent.getAction());
        switch (intent.getAction()) {
            case PACKAGE_CHANGED_BROADCAST:
                Uri packageUri = Uri.parse(intent.getData().getSchemeSpecificPart());
                int packageUid = intent.getIntExtra(Intent.EXTRA_UID, DEFAULT_PACKAGE_UID);
                switch (intent.getStringExtra(ACTION_KEY)) {
                    case PACKAGE_FULLY_REMOVED:
                        measurementOnPackageFullyRemoved(context, packageUri);
                        topicsOnPackageFullyRemoved(context, packageUri);
                        fledgeOnPackageFullyRemovedOrDataCleared(context, packageUri);
                        consentOnPackageFullyRemoved(context, packageUri, packageUid);
                        break;
                    case PACKAGE_ADDED:
                        measurementOnPackageAdded(context, packageUri);
                        topicsOnPackageAdded(context, packageUri);
                        break;
                    case PACKAGE_DATA_CLEARED:
                        measurementOnPackageDataCleared(context, packageUri);
                        fledgeOnPackageFullyRemovedOrDataCleared(context, packageUri);
                        break;
                }
                break;
        }
    }

    @VisibleForTesting
    void measurementOnPackageFullyRemoved(Context context, Uri packageUri) {
        if (FlagsFactory.getFlags().getMeasurementReceiverDeletePackagesKillSwitch()) {
            LogUtil.e("Measurement Delete Packages Receiver is disabled");
            return;
        }

        LogUtil.d("Package Fully Removed:" + packageUri);
        sBackgroundExecutor.execute(
                () -> MeasurementImpl.getInstance(context).deletePackageRecords(packageUri));
    }

    @VisibleForTesting
    void measurementOnPackageDataCleared(Context context, Uri packageUri) {
        if (FlagsFactory.getFlags().getMeasurementReceiverDeletePackagesKillSwitch()) {
            LogUtil.e("Measurement Delete Packages Receiver is disabled");
            return;
        }

        LogUtil.d("Package Data Cleared: " + packageUri);
        sBackgroundExecutor.execute(
                () -> {
                    MeasurementImpl.getInstance(context).deletePackageRecords(packageUri);
                });
    }

    @VisibleForTesting
    void measurementOnPackageAdded(Context context, Uri packageUri) {
        if (FlagsFactory.getFlags().getMeasurementReceiverInstallAttributionKillSwitch()) {
            LogUtil.e("Measurement Install Attribution Receiver is disabled");
            return;
        }

        LogUtil.d("Package Added: " + packageUri);
        sBackgroundExecutor.execute(
                () ->
                        MeasurementImpl.getInstance(context)
                                .doInstallAttribution(packageUri, System.currentTimeMillis()));
    }

    @VisibleForTesting
    void topicsOnPackageFullyRemoved(Context context, @NonNull Uri packageUri) {
        if (FlagsFactory.getFlags().getTopicsKillSwitch()) {
            LogUtil.e("Topics API is disabled");
            return;
        }

        LogUtil.d(
                "Handling App Uninstallation in Topics API for package: " + packageUri.toString());
        sBackgroundExecutor.execute(
                () -> TopicsWorker.getInstance(context).handleAppUninstallation(packageUri));
    }

    @VisibleForTesting
    void topicsOnPackageAdded(Context context, @NonNull Uri packageUri) {
        LogUtil.d("Package Added for topics API: " + packageUri.toString());
        sBackgroundExecutor.execute(
                () -> TopicsWorker.getInstance(context).handleAppInstallation(packageUri));
    }

    /** Deletes FLEDGE custom audience data belonging to the given application. */
    @VisibleForTesting
    void fledgeOnPackageFullyRemovedOrDataCleared(
            @NonNull Context context, @NonNull Uri packageUri) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(packageUri);

        if (FlagsFactory.getFlags().getFledgeCustomAudienceServiceKillSwitch()) {
            LogUtil.v("FLEDGE CA API is disabled");
            return;
        }

        LogUtil.d("Deleting custom audience data for package: " + packageUri);
        sBackgroundExecutor.execute(
                () ->
                        getCustomAudienceDatabase(context)
                                .customAudienceDao()
                                .deleteCustomAudienceDataByOwner(packageUri.toString()));
    }

    /**
     * Deletes a consent setting for the given application and UID. If the UID is equal to
     * DEFAULT_PACKAGE_UID, all consent data is deleted.
     */
    @VisibleForTesting
    void consentOnPackageFullyRemoved(
            @NonNull Context context, @NonNull Uri packageUri, int packageUid) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(packageUri);

        String packageName = packageUri.toString();
        LogUtil.d("Deleting consent data for package %s with UID %d", packageName, packageUid);
        sBackgroundExecutor.execute(
                () -> {
                    ConsentManager instance = ConsentManager.getInstance(context);
                    if (packageUid == DEFAULT_PACKAGE_UID) {
                        // There can be multiple instances of PackageChangedReceiver, e.g. in
                        // different user profiles. The system broadcasts a package change
                        // notification when any package is installed/uninstalled/cleared on any
                        // profile, to all PackageChangedReceivers. However, if the
                        // uninstallation is in a different user profile than the one this
                        // instance of PackageChangedReceiver is in, it should ignore that
                        // notification.
                        // Because the Package UID is absent, we need to figure out
                        // if this package was deleted in the current profile or a different one.
                        // We can do that by querying the list of installed packages and checking
                        // if the package name appears there. If it does, then this package was
                        // uninstalled in a different profile, and so the method should no-op.

                        if (!isPackageStillInstalled(context, packageName)) {
                            instance.clearConsentForUninstalledApp(packageName);
                            LogUtil.d("Deleted all consent data for package %s", packageName);
                        } else {
                            LogUtil.d(
                                    "Uninstalled package %s is present in list of installed"
                                            + " packages; ignoring",
                                    packageName);
                        }
                    } else {
                        instance.clearConsentForUninstalledApp(packageName, packageUid);
                        LogUtil.d(
                                "Deleted consent data for package %s with UID %d",
                                packageName, packageUid);
                    }
                });
    }

    /**
     * Checks if the removed package name is still present in the list of installed packages
     *
     * @param context the context passed along with the package notification
     * @param packageName the name of the package that was removed
     * @return {@code true} if the removed package name still exists in the list of installed
     *     packages on the system retrieved from {@code PackageManager.getInstalledPackages}; {@code
     *     false} otherwise.
     */
    @VisibleForTesting
    boolean isPackageStillInstalled(@NonNull Context context, @NonNull String packageName) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(packageName);
        PackageManager packageManager = context.getPackageManager();
        return PackageManagerCompatUtils.getInstalledPackages(packageManager, 0).stream()
                .anyMatch(s -> packageName.equals(s.packageName));
    }

    /**
     * Returns an instance of the {@link CustomAudienceDatabase}.
     *
     * <p>This is split out for testing/mocking purposes only, since the {@link
     * CustomAudienceDatabase} is abstract and therefore unmockable.
     */
    @VisibleForTesting
    CustomAudienceDatabase getCustomAudienceDatabase(@NonNull Context context) {
        Objects.requireNonNull(context);
        return CustomAudienceDatabase.getInstance(context);
    }
}
