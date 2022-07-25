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
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
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
// TODO(b/239553255): Use transaction for methods have both read and write to the database.
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
    private final Random mRandom;
    private final Flags mFlags;

    AppUpdateManager(@NonNull TopicsDao topicsDao, @NonNull Random random, @NonNull Flags flags) {
        mTopicsDao = topicsDao;
        mRandom = random;
        mFlags = flags;
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
                sSingleton =
                        new AppUpdateManager(
                                TopicsDao.getInstance(context),
                                new Random(),
                                FlagsFactory.getFlags());
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
     * Reconcile any mismatched data for application uninstallation.
     *
     * <p>Uninstallation: Wipe out data in all tables for an uninstalled application with data still
     * persisted in database.
     *
     * <ul>
     *   <li>Step 1: Get currently installed apps from Package Manager.
     *   <li>Step 2: Apps that have either usages or returned topics but are not installed are
     *       regarded as newly uninstalled apps.
     *   <li>Step 3: For each newly uninstalled app, wipe out its data from database.
     * </ul>
     *
     * @param context the context
     */
    public void reconcileUninstalledApps(@NonNull Context context) {
        Set<String> currentInstalledApps = getCurrentInstalledApps(context);
        Set<String> unhandledUninstalledApps = getUnhandledUninstalledApps(currentInstalledApps);
        if (unhandledUninstalledApps.isEmpty()) {
            return;
        }

        LogUtil.v(
                "Detect below unhandled mismatched applications: %s",
                unhandledUninstalledApps.toString());
        handleUninstalledApps(unhandledUninstalledApps);
        LogUtil.v("App uninstallation reconciliation is finished!");
    }

    /**
     * Reconcile any mismatched data for application installation.
     *
     * <p>Installation: Assign a random top topic from last 3 epochs to app only.
     *
     * <ul>
     *   <li>Step 1: Get currently installed apps from Package Manager.
     *   <li>Step 2: Installed apps that don't have neither usages nor returned topics are regarded
     *       as newly installed apps.
     *   <li>Step 3: For each newly installed app, assign a random top topic from last epoch to it
     *       and persist in the database.
     * </ul>
     *
     * @param context the context
     * @param currentEpochId id of current epoch
     */
    public void reconcileInstalledApps(@NonNull Context context, long currentEpochId) {
        Set<String> currentInstalledApps = getCurrentInstalledApps(context);
        Set<String> unhandledInstalledApps = getUnhandledInstalledApps(currentInstalledApps);

        if (unhandledInstalledApps.isEmpty()) {
            return;
        }

        LogUtil.v(
                "Detect below unhandled installed applications: %s",
                unhandledInstalledApps.toString());
        handleInstalledApps(unhandledInstalledApps, currentEpochId);
        LogUtil.v("App installation reconciliation is finished!");
    }

    /**
     * An overloading method to allow passing in Uri instead of app name in string format.
     *
     * <p>For newly installed app, to allow it get topics in current epoch, one of top topics in
     * past epochs will be assigned to this app.
     *
     * <p>See more details in go/rb-topics-app-update
     *
     * @param packageUri the Uri of newly installed application
     * @param currentEpochId current epoch id
     */
    public void assignTopicsToNewlyInstalledApps(@NonNull Uri packageUri, long currentEpochId) {
        assignTopicsToNewlyInstalledApps(convertUriToAppName(packageUri), currentEpochId);
    }

    /**
     * For a newly installed app, in case SDKs that this app uses are not known when the app is
     * installed, the returned topic for an SDK can only be assigned when user calls getTopic().
     *
     * <p>If an app calls Topics API via an SDK, and this app has a returned topic while SDK
     * doesn't, assign this topic to the SDK if it can learn this topic in past observable epochs.
     *
     * @param app the app
     * @param sdk the sdk. In case the app calls the Topics API directly, the sdk == empty string.
     * @param currentEpochId the epoch id of current cycle
     * @return A {@link Boolean} that notes whether a topic has been assigned to the sdk, so that
     *     {@link CacheManager} needs to reload the cachedTopics
     */
    public boolean assignTopicsToSdkForAppInstallation(
            @NonNull String app, @NonNull String sdk, long currentEpochId) {
        // Don't do anything if app calls getTopics directly without an SDK.
        if (sdk.isEmpty()) {
            return false;
        }

        int numberOfLookBackEpochs = mFlags.getTopicsNumberOfLookBackEpochs();
        Pair<String, String> appOnlyCaller = Pair.create(app, /* sdk */ "");
        Pair<String, String> appSdkCaller = Pair.create(app, sdk);

        // Get ReturnedTopics and CallerCanLearnTopics  for past epochs in
        // [epochId - numberOfLookBackEpochs, epochId - 1].
        // TODO(b/237436146): Create an object class for Returned Topics.
        Map<Long, Map<Pair<String, String>, Topic>> pastReturnedTopics =
                mTopicsDao.retrieveReturnedTopics(currentEpochId - 1, numberOfLookBackEpochs);
        for (Map<Pair<String, String>, Topic> returnedTopics : pastReturnedTopics.values()) {
            // If the SDK has a returned topic, this implies we have generated returned topics for
            // SDKs already. Exit early.
            if (returnedTopics.containsKey(appSdkCaller)) {
                return false;
            }
        }

        // Track whether a topic is assigned in order to know whether cache needs to be reloaded.
        boolean isAssigned = false;

        for (long epochId = currentEpochId - 1;
                epochId >= currentEpochId - numberOfLookBackEpochs && epochId >= 0;
                epochId--) {
            // Validate for an app-sdk pair, whether it satisfies
            // 1) In current epoch, app as the single caller has a returned topic
            // 2) The sdk can learn this topic in last numberOfLookBackEpochs epochs
            // If so, the same topic should be assigned to the sdk
            if (pastReturnedTopics.get(epochId) != null
                    && pastReturnedTopics.get(epochId).containsKey(appOnlyCaller)) {
                Topic appReturnedTopic = pastReturnedTopics.get(epochId).get(appOnlyCaller);

                // For current epoch, check whether sdk can learn this topic for past observed
                // epochs in [epochId - numberOfLookBackEpochs, epochId - 1]
                Map<Topic, Set<String>> pastCallerCanLearnTopicsMap =
                        mTopicsDao.retrieveCallerCanLearnTopicsMap(
                                epochId - 1, numberOfLookBackEpochs);
                List<Topic> pastTopTopic = mTopicsDao.retrieveTopTopics(epochId);

                if (EpochManager.isTopicLearnableByCaller(
                        appReturnedTopic,
                        sdk,
                        pastCallerCanLearnTopicsMap,
                        pastTopTopic,
                        mFlags.getTopicsNumberOfTopTopics())) {
                    mTopicsDao.persistReturnedAppTopicsMap(
                            epochId, Map.of(appSdkCaller, appReturnedTopic));
                    isAssigned = true;
                }
            }
        }

        return isAssigned;
    }

    /**
     * Generating a random topic from given top topic list
     *
     * @param topTopics a {@link List} of top topics in current epoch
     * @param numberOfTopTopics the number of regular top topics
     * @param numberOfRandomTopics the number of random top topics
     * @param percentageForRandomTopic the probability to select random object
     * @return a selected {@link Topic} to be assigned to newly installed app
     */
    @NonNull
    public Topic selectAssignedTopicFromTopTopics(
            @NonNull List<Topic> topTopics,
            int numberOfTopTopics,
            int numberOfRandomTopics,
            int percentageForRandomTopic) {
        // Validate the Top Topics are combined with correct number of topics and random topics
        Preconditions.checkArgument(numberOfTopTopics + numberOfRandomTopics == topTopics.size());

        // If random number is in [0, randomPercentage - 1], a random topic will be selected.
        boolean shouldSelectRandomTopic = mRandom.nextInt(100) < percentageForRandomTopic;

        if (shouldSelectRandomTopic) {
            // Generate a random number to pick one of random topics.
            // Random topics' index starts from numberOfTopTopics
            return topTopics.get(numberOfTopTopics + mRandom.nextInt(numberOfRandomTopics));
        }

        // Regular top topics start from index 0
        return topTopics.get(mRandom.nextInt(numberOfTopTopics));
    }

    // An app will be regarded as an unhandled uninstalled app if it has an entry in any epoch of
    // either usage table or returned topics table, but the app doesn't show up in package manager.
    //
    // This will be used in reconciliation process. See details in go/rb-topics-app-update.
    @NonNull
    @VisibleForTesting
    Set<String> getUnhandledUninstalledApps(@NonNull Set<String> currentInstalledApps) {
        Set<String> appsWithUsage =
                mTopicsDao.retrieveDistinctAppsFromTables(
                        List.of(TopicsTables.AppUsageHistoryContract.TABLE),
                        List.of(TopicsTables.AppUsageHistoryContract.APP));
        Set<String> appsWithReturnedTopics =
                mTopicsDao.retrieveDistinctAppsFromTables(
                        List.of(TopicsTables.ReturnedTopicContract.TABLE),
                        List.of(TopicsTables.ReturnedTopicContract.APP));

        // Combine sets of apps that have usage and returned topics
        appsWithUsage.addAll(appsWithReturnedTopics);

        // Exclude currently installed apps
        appsWithUsage.removeAll(currentInstalledApps);

        return appsWithUsage;
    }

    // TODO(b/234444036): Handle apps that don't have usages in last 3 epochs
    // An app will be regarded as an unhandled installed app if it shows up in package manager,
    // but doesn't have an entry in neither usage table or returned topic table.
    //
    // This will be used in reconciliation process. See details in go/rb-topics-app-update.
    @NonNull
    @VisibleForTesting
    Set<String> getUnhandledInstalledApps(@NonNull Set<String> currentInstalledApps) {
        // Make a copy of installed apps
        Set<String> installedApps = new HashSet<>(currentInstalledApps);

        // Get apps with usages or(and) returned topics
        Set<String> appsWithUsageOrReturnedTopics =
                mTopicsDao.retrieveDistinctAppsFromTables(
                        List.of(
                                TopicsTables.AppUsageHistoryContract.TABLE,
                                TopicsTables.ReturnedTopicContract.TABLE),
                        List.of(
                                TopicsTables.AppUsageHistoryContract.APP,
                                TopicsTables.ReturnedTopicContract.APP));

        // Remove apps with usage and returned topics from currently installed apps
        installedApps.removeAll(appsWithUsageOrReturnedTopics);

        return installedApps;
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

    // Handle newly installed applications
    //
    // Assign topics as real-time service to the app only, if the app isn't assigned with topics.
    private void handleInstalledApps(@NonNull Set<String> newlyInstalledApps, long currentEpochId) {
        for (String newlyInstalledApp : newlyInstalledApps) {
            assignTopicsToNewlyInstalledApps(newlyInstalledApp, currentEpochId);
        }
    }

    //
    // For newly installed app, to allow it get topics in current epoch, one of top topics in past
    // epochs will be assigned to this app.
    //
    // See more details in go/rb-topics-app-update
    private void assignTopicsToNewlyInstalledApps(@NonNull String app, long currentEpochId) {
        Objects.requireNonNull(app);

        // Read topics related configurations from Flags
        final int numberOfEpochsToAssignTopics = mFlags.getTopicsNumberOfLookBackEpochs();
        final int topicsNumberOfTopTopics = mFlags.getTopicsNumberOfTopTopics();
        final int topicsNumberOfRandomTopics = mFlags.getTopicsNumberOfRandomTopics();
        final int topicsPercentageForRandomTopic = mFlags.getTopicsPercentageForRandomTopic();

        Pair<String, String> appOnlyCaller = Pair.create(app, /* sdk */ "");

        // For each past epoch, assign a random topic to this newly installed app.
        // The assigned topic should align the probability with rule to generate top topics.
        for (long epochId = currentEpochId - 1;
                epochId >= currentEpochId - numberOfEpochsToAssignTopics && epochId >= 0;
                epochId--) {
            List<Topic> topTopics = mTopicsDao.retrieveTopTopics(epochId);

            if (topTopics.isEmpty()) {
                LogUtil.v(
                        "Empty top topic list in Epoch %d, do not assign topic to App %s in Epoch"
                                + "%d.",
                        epochId, app, epochId);
                continue;
            }

            Topic assignedTopic =
                    selectAssignedTopicFromTopTopics(
                            topTopics,
                            topicsNumberOfTopTopics,
                            topicsNumberOfRandomTopics,
                            topicsPercentageForRandomTopic);

            // Persist this topic to database as returned topic in this epoch
            mTopicsDao.persistReturnedAppTopicsMap(epochId, Map.of(appOnlyCaller, assignedTopic));

            LogUtil.v(
                    "Topic %s has been assigned to newly installed App %s in Epoch %d",
                    assignedTopic.getTopic(), app, epochId);
        }
    }

    // packageUri.toString() has only app name, without "package:" in the front, i.e. it'll be like
    // "com.example.adservices.sampleapp".
    private String convertUriToAppName(@NonNull Uri packageUri) {
        return packageUri.getSchemeSpecificPart();
    }
}
