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
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.topics.classifier.Classifier;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** A class to manage Epoch computation. */
public class EpochManager {

    // We use this origin to compute epoch timestamp.
    // In other words, the first epoch started at
    // Saturday, January 1, 2022 12:00:00 AM
    // TODO(b/221463765): get the timestamp when first access to the origin.
    // Save in SharedPreferences or slqlite db.
    private static final long ORIGIN_EPOCH_TIMESTAMP = 1640995200;

    // TODO(b/223915674): make this configurable.
    // The Top Topics will have 6 topics.
    // The first 5 topics are the Top Topics derived by ML, and the 6th is a random topic from
    // taxonomy.
    // The index starts from 0.
    private static final int RANDOM_TOPIC_INDEX = 5;

    // TODO(b/223916172): make this configurable.
    // The number of top Topics not including the random one.
    private static final int NUM_TOP_TOPICS_NOT_INCLUDING_RANDOM_ONE = 5;

    private static EpochManager sSingleton;

    private final TopicsDao mTopicsDao;
    private final DbHelper mDbHelper;
    private final Random mRandom;
    private final Classifier mClassifier;
    private final Flags mFlags;

    @VisibleForTesting
    EpochManager(@NonNull TopicsDao topicsDao, @NonNull DbHelper dbHelper,
            @NonNull Random random, @NonNull Classifier classifier, Flags flags) {
        mTopicsDao = topicsDao;
        mDbHelper = dbHelper;
        mRandom = random;
        mClassifier = classifier;
        mFlags = flags;
    }

    /** Returns an instance of the EpochManager given a context. */
    @NonNull
    public static EpochManager getInstance(@NonNull Context context) {
        synchronized (EpochManager.class) {
            if (sSingleton == null) {
                sSingleton = new EpochManager(TopicsDao.getInstance(context),
                        DbHelper.getInstance(context), new Random(),
                        Classifier.getInstance(context), FlagsFactory.getFlags());
            }
            return sSingleton;
        }
    }

    /**
     * Returns an instance of the EpochManager given a context. Not using Singleton so that we can
     * return different instances of EpochManager used for test
     */
    @NonNull
    public static EpochManager getInstanceForTest(@NonNull Context context,
            @NonNull Random random, @NonNull Classifier classifier) {
        return new EpochManager(TopicsDao.getInstanceForTest(context),
                DbHelper.getInstanceForTest(context), random, classifier,
                FlagsFactory.getFlagsForTest());
    }

    /**
     * Offline Epoch Processing.
     * For more details, see go/rb-topics-epoch-computation
     */
    public void processEpoch() {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        // This cross db and java boundaries multiple times so we need to have a db transaction.
        db.beginTransaction();
        long epochId = getCurrentEpochId();
        try {
            // Step 1: Compute the UsageMap from the UsageHistory table.
            // appSdksUsageMap = Map<App, List<SDK>> has the app and its SDKs that called Topics API
            // in the current Epoch.
            Map<String, List<String>> appSdksUsageMap = mTopicsDao.retrieveAppSdksUsageMap(epochId);

            // Step 2: Compute the Map from App to its classification topics.
            // Only produce for apps that called the Topics API in the current Epoch.
            // appClassificationTopicsMap = Map<App, List<Topics>>
            Map<String, List<String>> appClassificationTopicsMap =
                    computeAppClassificationTopics(appSdksUsageMap);
            // Then save app-topics Map into DB
            mTopicsDao.persistAppClassificationTopics(epochId, /* taxonomyVersion = */ 1L,
                    /* modelVersion = */ 1L, appSdksUsageMap);

            // Step 3: Compute the Callers can learn map for this epoch.
            // This is similar to the Callers Can Learn table in the explainer.
            Map<String, Set<String>> callersCanLearnThisEpochMap =
                    computeCallersCanLearnMap(appSdksUsageMap, appClassificationTopicsMap);
            // And then save this CallersCanLearnMap to DB.
            mTopicsDao.persistCallerCanLearnTopics(epochId, callersCanLearnThisEpochMap);

            // Step 4: For each topic, retrieve the callers (App or SDK) that can learn about that
            // topic. We look at last 3 epochs.
            // Return callersCanLearnMap = Map<Topic, Set<Caller>>  where Caller = App or Sdk.
            Map<String, Set<String>> callersCanLearnMap =
                    mTopicsDao.retrieveCallerCanLearnTopicsMap(epochId,
                            mFlags.getTopicsNumberOfLookBackEpochs());

            // Step 5: Retrieve the Top Topics. This will return a list of 5 top topics and
            // the 6th topic which is selected randomly. We can refer this 6th topic as the
            // random-topic.
            List<String> topTopics = computeTopTopics(appClassificationTopicsMap);
            // Then save Top Topics into DB
            mTopicsDao.persistTopTopics(epochId, topTopics);

            // Step 6: Assign topics to apps and SDK from the global top topics.
            // Currently hard-code the taxonomyVersion and the modelVersion.
            // Return returnedAppSdkTopics = Map<Pair<App, Sdk>, Topic>
            Map<Pair<String, String>, String> returnedAppSdkTopics =
                    computeReturnedAppSdkTopics(callersCanLearnMap, appSdksUsageMap, topTopics);

            // And persist the map to DB so that we can reuse later.
            mTopicsDao.persistReturnedAppTopicsMap(epochId, /* taxonomyVersion = */ 1L,
                    /* modelVersion = */ 1L, returnedAppSdkTopics);

            // Mark the transaction successful.
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // Query the Classifier to get the top Topics for this epoch.
    // appClassificationTopicsMap = Map<App, List<Topics>>
    @NonNull
    private List<String> computeTopTopics(Map<String, List<String>> appClassificationTopicsMap) {
        return mClassifier.getTopTopics(
                appClassificationTopicsMap,
                mFlags.getTopicsNumberOfTopTopics(),
                mFlags.getTopicsNumberOfRandomTopics());
    }

    // Compute the Map from App to its classification topics.
    // Only produce for apps that called the Topics API in the current Epoch.
    // input:
    // appSdksUsageMap = Map<App, List<SDK>> has the app and its SDKs that called Topics API
    // Return appClassificationTopicsMap = Map<App, List<Topic>>
    @VisibleForTesting
    Map<String, List<String>> computeAppClassificationTopics(
            Map<String, List<String>> appSdksUsageMap) {
        return mClassifier.classify(appSdksUsageMap.keySet());
    }

    /**
     * Record the call from App and Sdk to usage history.
     * This UsageHistory will be used to determine if a caller (app or sdk) has observed a topic
     * before.
     *
     * @param app the app
     * @param sdk the sdk of the app. In case the app calls the Topics API directly, the sdk
     *            == empty string.
     */
    public void recordUsageHistory(String app, String sdk) {
        long epochID = getCurrentEpochId();
        mTopicsDao.recordUsageHistory(epochID, app, sdk);
        mTopicsDao.recordAppUsageHistory(epochID, app);
    }

    // Return a Map from Topic to set of App or Sdk that can learn about that topic.
    // This is similar to the table Can Learn Topic in the explainer.
    // Return Map<Topic, Set<Caller>>  where Caller = App or Sdk.
    @VisibleForTesting
    @NonNull
    static Map<String, Set<String>> computeCallersCanLearnMap(
            @NonNull Map<String, List<String>> appSdksUsageMap,
            @NonNull Map<String, List<String>> appClassificationTopicsMap) {
        Objects.requireNonNull(appSdksUsageMap);
        Objects.requireNonNull(appClassificationTopicsMap);

        // Map from Topic to set of App or Sdk that can learn about that topic.
        // This is similar to the table Can Learn Topic in the explainer.
        // Map<Topic, Set<Caller>>  where Caller = App or Sdk.
        Map<String, Set<String>> callersCanLearnMap = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : appClassificationTopicsMap.entrySet()) {
            String app = entry.getKey();
            List<String> appTopics = entry.getValue();
            if (appTopics == null) {
                LogUtil.e("Can't find the Classification Topics for app = " + app);
                continue;
            }

            for (String topic : appTopics) {
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
    Map<Pair<String, String>, String> computeReturnedAppSdkTopics(
            @NonNull Map<String, Set<String>> callersCanLearnMap,
            @NonNull Map<String, List<String>> appSdksUsageMap,
            @NonNull List<String> topTopics) {
        Map<Pair<String, String>, String> returnedAppSdkTopics = new HashMap<>();

        for (Map.Entry<String, List<String>> app : appSdksUsageMap.entrySet()) {
            String returnedTopic = selectRandomTopic(topTopics);
            Set<String> callersCanLearnThisTopic = callersCanLearnMap.get(returnedTopic);
            if (callersCanLearnThisTopic == null) {
                continue;
            }

            // Check if the app can learn this topic.
            if (callersCanLearnThisTopic.contains(app.getKey())) {
                // The app calls Topics API directly. In this case, we set the sdk == empty string.
                returnedAppSdkTopics.put(
                        Pair.create(app.getKey(), /* empty Sdk */ ""), returnedTopic);
            }

            // Then check all SDKs of the app.
            for (String sdk : app.getValue()) {
                if (callersCanLearnThisTopic.contains(sdk)) {
                    returnedAppSdkTopics.put(
                            Pair.create(app.getKey(), sdk), returnedTopic);
                }
            }
        }

        return returnedAppSdkTopics;
    }

    // Return a random topics from the Top Topics.
    // The Top Topics include the Top 5 Topics and one random topic from the Taxonomy.
    @VisibleForTesting
    String selectRandomTopic(List<String> topTopics) {
        Preconditions.checkArgument(topTopics.size()
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

    // Return the current epochId.
    // Each Epoch will have an Id. The first epoch has Id = 0.
    // For Alpha 1, we assume a fixed origin epoch starting from
    // Saturday, January 1, 2022 12:00:00 AM.
    // Later, we will use per device starting origin.
    @VisibleForTesting
    public long getCurrentEpochId() {
        // TODO(b/221463765): Don't use a fix epoch origin like this. This is for Alpha 1 only.
        return (long) Math.floor((System.currentTimeMillis() - ORIGIN_EPOCH_TIMESTAMP)
                /  mFlags.getTopicsEpochJobPeriodMs());
    }
}
