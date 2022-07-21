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
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Dumpable;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.Clock;
import com.android.adservices.service.topics.classifier.Classifier;
import com.android.adservices.service.topics.classifier.OnDeviceClassifier;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** A class to manage Epoch computation. */
public class EpochManager implements Dumpable {
    // TODO(b/223915674): make this configurable.
    // The Top Topics will have 6 topics.
    // The first 5 topics are the Top Topics derived by ML, and the 6th is a random topic from
    // taxonomy.
    // The index starts from 0.
    private static final int RANDOM_TOPIC_INDEX = 5;

    // TODO(b/223916172): make this configurable.
    // The number of top Topics not including the random one.
    private static final int NUM_TOP_TOPICS_NOT_INCLUDING_RANDOM_ONE = 5;

    // The tables to do garbage collection for old epochs
    // and its corresponding epoch_id column name.
    // Pair<Table Name, Column Name>
    private static final Pair<String, String>[] TABLE_INFO_FOR_EPOCH_GARBAGE_COLLECTION =
            new Pair[] {
                Pair.create(
                        TopicsTables.AppClassificationTopicsContract.TABLE,
                        TopicsTables.AppClassificationTopicsContract.EPOCH_ID),
                Pair.create(
                        TopicsTables.CallerCanLearnTopicsContract.TABLE,
                        TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID),
                Pair.create(
                        TopicsTables.TopTopicsContract.TABLE,
                        TopicsTables.TopTopicsContract.EPOCH_ID),
                Pair.create(
                        TopicsTables.ReturnedTopicContract.TABLE,
                        TopicsTables.ReturnedTopicContract.EPOCH_ID),
                Pair.create(
                        TopicsTables.UsageHistoryContract.TABLE,
                        TopicsTables.UsageHistoryContract.EPOCH_ID),
                Pair.create(
                        TopicsTables.AppUsageHistoryContract.TABLE,
                        TopicsTables.AppUsageHistoryContract.EPOCH_ID)
            };

    private static EpochManager sSingleton;

    private final TopicsDao mTopicsDao;
    private final DbHelper mDbHelper;
    private final Random mRandom;
    private final Classifier mClassifier;
    private final Flags mFlags;
    // Use Clock.SYSTEM_CLOCK except in unit tests, which pass in a local instance of Clock to mock.
    private final Clock mClock;

    @VisibleForTesting
    EpochManager(
            @NonNull TopicsDao topicsDao,
            @NonNull DbHelper dbHelper,
            @NonNull Random random,
            @NonNull Classifier classifier,
            Flags flags,
            @NonNull Clock clock) {
        mTopicsDao = topicsDao;
        mDbHelper = dbHelper;
        mRandom = random;
        mClassifier = classifier;
        mFlags = flags;
        mClock = clock;
    }

    /** Returns an instance of the EpochManager given a context. */
    @NonNull
    public static EpochManager getInstance(@NonNull Context context) {
        synchronized (EpochManager.class) {
            if (sSingleton == null) {
                sSingleton =
                        new EpochManager(
                                TopicsDao.getInstance(context),
                                DbHelper.getInstance(context),
                                new Random(),
                                OnDeviceClassifier.getInstance(context),
                                FlagsFactory.getFlags(),
                                Clock.SYSTEM_CLOCK);
            }
            return sSingleton;
        }
    }

    /** Offline Epoch Processing. For more details, see go/rb-topics-epoch-computation */
    public void processEpoch() {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        // This cross db and java boundaries multiple times, so we need to have a db transaction.
        db.beginTransaction();

        long currentEpochId = getCurrentEpochId();
        LogUtil.d("EpochManager.processEpoch for the current epochId %d", currentEpochId);

        try {
            // Step 1: Compute the UsageMap from the UsageHistory table.
            // appSdksUsageMap = Map<App, List<SDK>> has the app and its SDKs that called Topics API
            // in the current Epoch.
            Map<String, List<String>> appSdksUsageMap =
                    mTopicsDao.retrieveAppSdksUsageMap(currentEpochId);
            LogUtil.v("appSdksUsageMap size is  %d", appSdksUsageMap.size());

            // Step 2: Compute the Map from App to its classification topics.
            // Only produce for apps that called the Topics API in the current Epoch.
            // appClassificationTopicsMap = Map<App, List<Topics>>
            Map<String, List<Topic>> appClassificationTopicsMap =
                    mClassifier.classify(appSdksUsageMap.keySet());
            LogUtil.v("appClassificationTopicsMap size is %d", appClassificationTopicsMap.size());

            // Then save app-topics Map into DB
            mTopicsDao.persistAppClassificationTopics(currentEpochId, appClassificationTopicsMap);

            // Step 3: Compute the Callers can learn map for this epoch.
            // This is similar to the Callers Can Learn table in the explainer.
            Map<Topic, Set<String>> callersCanLearnThisEpochMap =
                    computeCallersCanLearnMap(appSdksUsageMap, appClassificationTopicsMap);
            LogUtil.v(
                    "callersCanLearnThisEpochMap size is  %d", callersCanLearnThisEpochMap.size());

            // And then save this CallersCanLearnMap to DB.
            mTopicsDao.persistCallerCanLearnTopics(currentEpochId, callersCanLearnThisEpochMap);

            // Step 4: For each topic, retrieve the callers (App or SDK) that can learn about that
            // topic. We look at last 3 epochs.
            // Return callersCanLearnMap = Map<Topic, Set<Caller>>  where Caller = App or Sdk.
            Map<Topic, Set<String>> callersCanLearnMap =
                    mTopicsDao.retrieveCallerCanLearnTopicsMap(
                            currentEpochId, mFlags.getTopicsNumberOfLookBackEpochs());
            LogUtil.v("callersCanLearnMap size is %d", callersCanLearnMap.size());

            // Step 5: Retrieve the Top Topics. This will return a list of 5 top topics and
            // the 6th topic which is selected randomly. We can refer this 6th topic as the
            // random-topic.
            List<Topic> topTopics =
                    mClassifier.getTopTopics(
                            appClassificationTopicsMap,
                            mFlags.getTopicsNumberOfTopTopics(),
                            mFlags.getTopicsNumberOfRandomTopics());
            // Abort the computation if empty list of top topics is returned from classifier.
            // This could happen if there is no usage of the Topics API in the last epoch.
            if (topTopics.isEmpty()) {
                LogUtil.w(
                        "Empty list of top topics is returned from classifier. Aborting the"
                                + " computation!");
                db.setTransactionSuccessful();
                return;
            }
            LogUtil.v("topTopics are  %s", topTopics.toString());

            // Then save Top Topics into DB
            mTopicsDao.persistTopTopics(currentEpochId, topTopics);

            // Step 6: Assign topics to apps and SDK from the global top topics.
            // Currently, hard-code the taxonomyVersion and the modelVersion.
            // Return returnedAppSdkTopics = Map<Pair<App, Sdk>, Topic>
            Map<Pair<String, String>, Topic> returnedAppSdkTopics =
                    computeReturnedAppSdkTopics(callersCanLearnMap, appSdksUsageMap, topTopics);
            LogUtil.v("returnedAppSdkTopics size is  %d", returnedAppSdkTopics.size());

            // And persist the map to DB so that we can reuse later.
            mTopicsDao.persistReturnedAppTopicsMap(currentEpochId, returnedAppSdkTopics);

            // Finally erase outdated epoch's data
            garbageCollectOutdatedEpochData(currentEpochId);

            // Mark the transaction successful.
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Record the call from App and Sdk to usage history. This UsageHistory will be used to
     * determine if a caller (app or sdk) has observed a topic before.
     *
     * @param app the app
     * @param sdk the sdk of the app. In case the app calls the Topics API directly, the sdk ==
     *     empty string.
     */
    public void recordUsageHistory(String app, String sdk) {
        long epochId = getCurrentEpochId();
        // TODO(b/223159123): Do we need to filter out this log in prod build?
        LogUtil.v(
                "EpochManager.recordUsageHistory for current EpochId = %d for %s, %s",
                epochId, app, sdk);
        mTopicsDao.recordUsageHistory(epochId, app, sdk);
        mTopicsDao.recordAppUsageHistory(epochId, app);
    }

    /**
     * Determine the learn-ability of a topic to a certain caller.
     *
     * @param topic the topic to check the learn-ability
     * @param caller the caller to check whether it can learn the given topic
     * @param callersCanLearnMap the map that stores topic->caller mapping which shows a topic can
     *     be learnt by a caller
     * @param topTopics a {@link List} of top topics
     * @return a {@code boolean} that indicates if the caller can learn the topic
     */
    // TODO(b/234444036): Enable this feature for newly installed apps
    // TODO(b/236834213): Create a class for Top Topics
    public boolean isTopicLearnableByCaller(
            @NonNull Topic topic,
            @NonNull String caller,
            @NonNull Map<Topic, Set<String>> callersCanLearnMap,
            @NonNull List<Topic> topTopics) {
        // If a topic is the random topic in top topic list, it can be learnt by any caller.
        int index = topTopics.lastIndexOf(topic);
        // Regular top topics are placed in the front of the list. Topics after are random topics.
        if (index >= mFlags.getTopicsNumberOfTopTopics()) {
            return true;
        }

        return callersCanLearnMap.containsKey(topic)
                && callersCanLearnMap.get(topic).contains(caller);
    }

    /**
     * Get the ID of current epoch.
     *
     * <p>The origin's timestamp is saved in the database. If the origin doesn't exist, it means the
     * user never calls Topics API and the origin will be returned with -1. In this case, set
     * current time as origin and persist it into database.
     *
     * @return a non-negative epoch ID of current epoch.
     */
    // TODO(b/237119788): Cache origin in cache manager.
    // TODO(b/237119790): Set origin to sometime after midnight to get better maintenance timing.
    public long getCurrentEpochId() {
        long origin = mTopicsDao.retrieveEpochOrigin();
        long currentTimeStamp = mClock.currentTimeMillis();
        long epochJobPeriodsMs = mFlags.getTopicsEpochJobPeriodMs();

        // If origin doesn't exist in database, set current timestamp as origin.
        if (origin == -1) {
            origin = currentTimeStamp;
            mTopicsDao.persistEpochOrigin(origin);
            LogUtil.d(
                    "Origin isn't found! Set current time %s as origin.",
                    Instant.ofEpochMilli(origin).toString());
        }

        LogUtil.v("Epoch length is  %d", epochJobPeriodsMs);
        return (long) Math.floor((currentTimeStamp - origin) / (double) epochJobPeriodsMs);
    }

    // Return a Map from Topic to set of App or Sdk that can learn about that topic.
    // This is similar to the table Can Learn Topic in the explainer.
    // Return Map<Topic, Set<Caller>>  where Caller = App or Sdk.
    @VisibleForTesting
    @NonNull
    static Map<Topic, Set<String>> computeCallersCanLearnMap(
            @NonNull Map<String, List<String>> appSdksUsageMap,
            @NonNull Map<String, List<Topic>> appClassificationTopicsMap) {
        Objects.requireNonNull(appSdksUsageMap);
        Objects.requireNonNull(appClassificationTopicsMap);

        // Map from Topic to set of App or Sdk that can learn about that topic.
        // This is similar to the table Can Learn Topic in the explainer.
        // Map<Topic, Set<Caller>>  where Caller = App or Sdk.
        Map<Topic, Set<String>> callersCanLearnMap = new HashMap<>();

        for (Map.Entry<String, List<Topic>> entry : appClassificationTopicsMap.entrySet()) {
            String app = entry.getKey();
            List<Topic> appTopics = entry.getValue();
            if (appTopics == null) {
                LogUtil.e("Can't find the Classification Topics for app = " + app);
                continue;
            }

            for (Topic topic : appTopics) {
                if (!callersCanLearnMap.containsKey(topic)) {
                    callersCanLearnMap.put(topic, new HashSet<>());
                }

                // All SDKs in the app can learn this topic too.
                for (String sdk : appSdksUsageMap.get(app)) {
                    if (TextUtils.isEmpty(sdk)) {
                        // Empty sdk means the app called the Topics API directly.
                        // Caller = app
                        // Then the app can learn its topic.
                        callersCanLearnMap.get(topic).add(app);
                    } else {
                        // Caller = sdk
                        callersCanLearnMap.get(topic).add(sdk);
                    }
                }
            }
        }

        return callersCanLearnMap;
    }

    // Inputs:
    // callersCanLearnMap = Map<Topic, Set<Caller>> map from topic to set of callers that can learn
    // about the topic. Caller = App or Sdk.
    // appSdksUsageMap = Map<App, List<SDK>> has the app and its SDKs that called Topics API
    // in the current Epoch.
    // topTopics = List<Topic> list of top 5 topics and 1 random topic.
    //
    // Return returnedAppSdkTopics = Map<Pair<App, Sdk>, Topic>
    @VisibleForTesting
    @NonNull
    Map<Pair<String, String>, Topic> computeReturnedAppSdkTopics(
            @NonNull Map<Topic, Set<String>> callersCanLearnMap,
            @NonNull Map<String, List<String>> appSdksUsageMap,
            @NonNull List<Topic> topTopics) {
        Map<Pair<String, String>, Topic> returnedAppSdkTopics = new HashMap<>();

        for (Map.Entry<String, List<String>> appSdks : appSdksUsageMap.entrySet()) {
            Topic returnedTopic = selectRandomTopic(topTopics);
            String app = appSdks.getKey();

            // Check if the app can learn this topic.
            if (isTopicLearnableByCaller(returnedTopic, app, callersCanLearnMap, topTopics)) {
                // The app calls Topics API directly. In this case, we set the sdk == empty string.
                returnedAppSdkTopics.put(Pair.create(app, /* empty Sdk */ ""), returnedTopic);
                // TODO(b/223159123): Do we need to filter out this log in prod build?
                LogUtil.v(
                        "CacheManager.computeReturnedAppSdkTopics. Topic %s is returned for"
                                + " %s",
                        returnedTopic, app);
            }

            // Then check all SDKs of the app.
            for (String sdk : appSdks.getValue()) {
                if (isTopicLearnableByCaller(returnedTopic, sdk, callersCanLearnMap, topTopics)) {
                    returnedAppSdkTopics.put(Pair.create(app, sdk), returnedTopic);
                    // TODO(b/223159123): Do we need to filter out this log in prod build?
                    LogUtil.v(
                            "CacheManager.computeReturnedAppSdkTopics. Topic %s is returned"
                                    + " for %s, %s",
                            returnedTopic, app, sdk);
                }
            }
        }

        return returnedAppSdkTopics;
    }

    // Return a random topics from the Top Topics.
    // The Top Topics include the Top 5 Topics and one random topic from the Taxonomy.
    @VisibleForTesting
    @NonNull
    Topic selectRandomTopic(List<Topic> topTopics) {
        Preconditions.checkArgument(
                topTopics.size()
                        == mFlags.getTopicsNumberOfTopTopics()
                                + mFlags.getTopicsNumberOfRandomTopics());
        int random = mRandom.nextInt(100);

        // For 5%, get the random topic.
        if (random < mFlags.getTopicsPercentageForRandomTopic()) {
            // The random topic is the last one on the list.
            return topTopics.get(RANDOM_TOPIC_INDEX);
        }

        // For 95%, pick randomly one out of 5 top topics.
        return topTopics.get(random % NUM_TOP_TOPICS_NOT_INCLUDING_RANDOM_ONE);
    }

    // To garbage collect data for old epochs.
    @VisibleForTesting
    void garbageCollectOutdatedEpochData(long currentEpochID) {
        // Assume current Epoch is T, and the earliest epoch should be kept is T-3
        // Then any epoch data older than T-3-1 = T-4, including T-4 should be deleted.
        long epochToDeleteFrom = currentEpochID - mFlags.getNumberOfEpochsToKeepInHistory() - 1;
        // To do garbage collection for each table
        for (Pair<String, String> tableColumnPair : TABLE_INFO_FOR_EPOCH_GARBAGE_COLLECTION) {
            mTopicsDao.deleteDataOfOldEpochs(
                    tableColumnPair.first, tableColumnPair.second, epochToDeleteFrom);
        }
    }

    @Override
    public void dump(@NonNull PrintWriter writer, @Nullable String[] args) {
        writer.println("==== EpochManager Dump ====");
        long epochId = getCurrentEpochId();
        writer.println(String.format("Current epochId is %d", epochId));
    }
}
