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

package com.android.adservices.service.topics;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class to manage application update flow in Topics API.
 *
 * <p>It contains methods to handle app installation and uninstallation. App update will either be
 * regarded as the combination of app installation and uninstallation, or be handled in the next
 * epoch.
 *
 * <p>See go/rb-topics-app-update for details.
 */
public class AppUpdateManager {
    private static AppUpdateManager sSingleton;

    // Tables that needs to be wiped out for application data
    // and its corresponding app column name.
    // Pair<Table Name, app Column Name>
    private static final Pair<String, String>[] TABLE_INFO_TO_ERASE_APP_DATA =
            new Pair[] {
                Pair.create(
                        TopicsTables.AppClassificationTopicsContract.TABLE,
                        TopicsTables.AppClassificationTopicsContract.APP),
                Pair.create(
                        TopicsTables.CallerCanLearnTopicsContract.TABLE,
                        TopicsTables.CallerCanLearnTopicsContract.CALLER),
                Pair.create(
                        TopicsTables.ReturnedTopicContract.TABLE,
                        TopicsTables.ReturnedTopicContract.APP),
                Pair.create(
                        TopicsTables.UsageHistoryContract.TABLE,
                        TopicsTables.UsageHistoryContract.APP),
                Pair.create(
                        TopicsTables.AppUsageHistoryContract.TABLE,
                        TopicsTables.AppUsageHistoryContract.APP)
            };

    private final TopicsDao mTopicsDao;

    AppUpdateManager(@NonNull TopicsDao topicsDao) {
        mTopicsDao = topicsDao;
    }

    /**
     * Returns an instance of AppUpdateManager given a context
     *
     * @param context the context
     * @return an instance of AppUpdateManager
     */
    @NonNull
    public static AppUpdateManager getInstance(@NonNull Context context) {
        synchronized (AppUpdateManager.class) {
            if (sSingleton == null) {
                sSingleton = new AppUpdateManager(TopicsDao.getInstance(context));
            }
        }

        return sSingleton;
    }

    /**
     * Delete application data for a specific application.
     *
     * <p>This method allows other usages besides daily maintenance job, such as real-time data
     * wiping for an app uninstallation.
     *
     * @param apps a {@link List} of applications to wipe data for
     */
    public void deleteAppDataFromTableByApps(@NonNull List<String> apps) {
        for (Pair<String, String> tableColumnNamePair : TABLE_INFO_TO_ERASE_APP_DATA) {
            mTopicsDao.deleteAppFromTable(
                    tableColumnNamePair.first, tableColumnNamePair.second, apps);
        }

        LogUtil.v("Have deleted data for application " + apps);
    }

    /**
     * Delete application data for a specific application.
     *
     * @param packageUri The {@link Uri} got from Broadcast Intent
     */
    public void deleteAppDataByUri(@NonNull Uri packageUri) {
        String appName = convertUriToAppName(packageUri);
        deleteAppDataFromTableByApps(List.of(appName));
    }

    /**
     * Reconcile any mismatched data for applications.
     *
     * <p>Currently, wipe out data in all tables for an uninstalled application with data still
     * persisted in database
     *
     * @param context the context
     */
    public void reconcileUninstalledApps(Context context) {
        Set<String> currentInstalledApps = getCurrentInstalledApps(context);
        Set<String> unhandledUninstalledApps = getUnhandledUninstalledApps(currentInstalledApps);
        if (unhandledUninstalledApps.isEmpty()) {
            return;
        }

        LogUtil.v(
                "Detect below unhandled mismatched applications: %s",
                unhandledUninstalledApps.toString());
        handleUninstalledApps(unhandledUninstalledApps);
    }

    // An app will be regarded as unhandled uninstalled apps if it has an entry in any epoch of
    // either usage table or returned topics table, but the app doesn't show up in package manager.
    //
    // This will be used in reconciliation process. See details in go/rb-topics-app-update.
    @NonNull
    @VisibleForTesting
    Set<String> getUnhandledUninstalledApps(@NonNull Set<String> currentInstalledApps) {
        Set<String> appsWithUsage =
                mTopicsDao.retrieveDistinctAppsFromTable(
                        TopicsTables.AppUsageHistoryContract.TABLE,
                        TopicsTables.AppUsageHistoryContract.APP);
        Set<String> appsWithReturnedTopics =
                mTopicsDao.retrieveDistinctAppsFromTable(
                        TopicsTables.ReturnedTopicContract.TABLE,
                        TopicsTables.ReturnedTopicContract.APP);

        // Combine sets of apps that have usage and returned topics
        appsWithUsage.addAll(appsWithReturnedTopics);

        // Exclude currently installed apps
        appsWithUsage.removeAll(currentInstalledApps);

        return appsWithUsage;
    }

    // Get current installed applications from package manager
    @NonNull
    private Set<String> getCurrentInstalledApps(Context context) {
        PackageManager packageManager = context.getPackageManager();
        List<ApplicationInfo> appInfoList =
                packageManager.getInstalledApplications(
                        PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA));

        return appInfoList.stream().map(appInfo -> appInfo.packageName).collect(Collectors.toSet());
    }

    // Handle Uninstalled applications that still have derived data in database
    //
    // Currently, simply wipe out these data in the database for an app. i.e. Deleting all
    // derived data from all tables that are related to app (has app column)
    private void handleUninstalledApps(@NonNull Set<String> newlyUninstalledApps) {
        deleteAppDataFromTableByApps(new ArrayList<>(newlyUninstalledApps));
    }

    // packageUri.toString() has only app name, without "package:" in the front, i.e. it'll be like
    // "com.example.adservices.sampleapp".
    private String convertUriToAppName(@NonNull Uri packageUri) {
        return packageUri.getSchemeSpecificPart();
    }
}
