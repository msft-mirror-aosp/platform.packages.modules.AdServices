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
import android.util.Dumpable;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.GetTopicsReportedStats;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/** A class to manage Topics Cache. */
public class CacheManager implements Dumpable {
    // The verbose level for dumpsys usage
    private static final int VERBOSE = 1;
    private static CacheManager sSingleton;
    // Lock for Read and Write on the cached topics map.
    // This allows concurrent reads but exclusive update to the cache.
    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();
    private final EpochManager mEpochManager;
    private final TopicsDao mTopicsDao;
    private final Flags mFlags;
    // Map<EpochId, Map<Pair<App, Sdk>, Topic>
    private Map<Long, Map<Pair<String, String>, Topic>> mCachedTopics = new HashMap<>();
    // TODO(b/236422354): merge hashsets to have one point of truth (Taxonomy update)
    // HashSet<BlockedTopic>
    private HashSet<Topic> mCachedBlockedTopics = new HashSet<>();
    // HashSet<TopicId>
    private HashSet<Integer> mCachedBlockedTopicIds = new HashSet<>();
    private final AdServicesLogger mLogger;

    @VisibleForTesting
    CacheManager(
            EpochManager epochManager, TopicsDao topicsDao, Flags flags, AdServicesLogger logger) {
        mEpochManager = epochManager;
        mTopicsDao = topicsDao;
        mFlags = flags;
        mLogger = logger;
    }

    /** Returns an instance of the CacheManager given a context. */
    @NonNull
    public static CacheManager getInstance(Context context) {
        synchronized (CacheManager.class) {
            if (sSingleton == null) {
                sSingleton =
                        new CacheManager(
                                EpochManager.getInstance(context),
                                TopicsDao.getInstance(context),
                                FlagsFactory.getFlags(),
                                AdServicesLoggerImpl.getInstance());
            }
            return sSingleton;
        }
    }

    /**
     * Load the cache from DB.
     *
     * <p>When first created, the Cache is empty. We will need to retrieve the cache from DB.
     */
    public void loadCache() {
        // Retrieve the cache from DB.
        long currentEpoch = mEpochManager.getCurrentEpochId();
        int lookbackEpochs = mFlags.getTopicsNumberOfLookBackEpochs();
        // Map<EpochId, Map<Pair<App, Sdk>, Topic>
        Map<Long, Map<Pair<String, String>, Topic>> cacheFromDb =
                mTopicsDao.retrieveReturnedTopics(currentEpoch, lookbackEpochs + 1);
        // HashSet<BlockedTopic>
        HashSet<Topic> blockedTopicsCacheFromDb =
                new HashSet<>(mTopicsDao.retrieveAllBlockedTopics());
        HashSet<Integer> blockedTopicIdsFromDb =
                blockedTopicsCacheFromDb.stream()
                        .map(Topic::getTopic)
                        .collect(Collectors.toCollection(HashSet::new));

        LogUtil.v(
                "CacheManager.loadCache(). CachedTopics mapping size is "
                        + cacheFromDb.size()
                        + ", CachedBlockedTopics mapping size is "
                        + blockedTopicsCacheFromDb.size());
        try {
            mReadWriteLock.writeLock().lock();
            mCachedTopics = cacheFromDb;
            mCachedBlockedTopics = blockedTopicsCacheFromDb;
            mCachedBlockedTopicIds = blockedTopicIdsFromDb;
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /**
     * Get list of topics for the numberOfLookBackEpochs epoch starting from [epochId -
     * numberOfLookBackEpochs + 1, epochId] that were not blocked by the user.
     *
     * @param numberOfLookBackEpochs how many epochs to look back.
     * @param app the app
     * @param sdk the sdk. In case the app calls the Topics API directly, the sdk == empty string.
     * @param random a {@link Random} instance for shuffling
     * @return {@link List<Topic>} a list of Topics
     */
    @NonNull
    public List<Topic> getTopics(
            int numberOfLookBackEpochs, String app, String sdk, Random random) {
        // We will need to look at the 3 historical epochs starting from last epoch.
        long epochId = mEpochManager.getCurrentEpochId() - 1;
        List<Topic> topics = new ArrayList<>();
        // To deduplicate returned topics
        Set<Integer> topicsSet = new HashSet<>();

        mReadWriteLock.readLock().lock();
        int duplicateTopicCount = 0, blockedTopicCount = 0;
        try {
            for (int numEpoch = 0; numEpoch < numberOfLookBackEpochs; numEpoch++) {
                if (mCachedTopics.containsKey(epochId - numEpoch)) {
                    Topic topic = mCachedTopics.get(epochId - numEpoch).get(Pair.create(app, sdk));
                    if (topic != null) {
                        if (topicsSet.contains(topic.getTopic())) {
                            duplicateTopicCount++;
                            continue;
                        }
                        if (mCachedBlockedTopicIds.contains(topic.getTopic())) {
                            blockedTopicCount++;
                            continue;
                        }
                        topics.add(topic);
                        topicsSet.add(topic.getTopic());
                    }
                }
            }
        } finally {
            mReadWriteLock.readLock().unlock();
        }

        Collections.shuffle(topics, random);

        // Log GetTopics stats.
        ImmutableList.Builder<Integer> topicIds = ImmutableList.builder();
        for (Topic topic : topics) {
            topicIds.add(topic.getTopic());
        }
        mLogger.logGetTopicsReportedStats(
                GetTopicsReportedStats.builder()
                        .setDuplicateTopicCount(duplicateTopicCount)
                        .setFilteredBlockedTopicCount(blockedTopicCount)
                        .setTopicIdsCount(topics.size())
                        .build());

        return topics;
    }

    /**
     * Overloading getTopics() method to pass in an initialized Random object.
     *
     * @param numberOfLookBackEpochs how many epochs to look back.
     * @param app the app
     * @param sdk the sdk. In case the app calls the Topics API directly, the sdk == empty string.
     * @return {@link List<Topic>} a list of Topics
     */
    @NonNull
    public List<Topic> getTopics(int numberOfLookBackEpochs, String app, String sdk) {
        return getTopics(numberOfLookBackEpochs, app, sdk, new Random());
    }

    /**
     * Get cached topics within certain epoch range. This is a helper method to get cached topics
     * for an app-sdk caller, without considering other constraints, like UI blocking logic.
     *
     * @param epochLowerBound the earliest epoch to include cached topics from
     * @param epochUpperBound the latest epoch to included cached topics to
     * @param app the app
     * @param sdk the sdk. In case the app calls the Topics API directly, the sdk == empty string.
     * @return {@link List<Topic>} a list of Topics between {@code epochLowerBound} and {@code
     *     epochUpperBound}.
     */
    @NonNull
    public List<Topic> getTopicsInEpochRange(
            long epochLowerBound, long epochUpperBound, @NonNull String app, @NonNull String sdk) {
        List<Topic> topics = new ArrayList<>();
        // To deduplicate returned topics
        Set<Integer> topicsSet = new HashSet<>();

        mReadWriteLock.readLock().lock();
        try {
            for (long epochId = epochLowerBound; epochId <= epochUpperBound; epochId++) {
                if (mCachedTopics.containsKey(epochId)) {
                    Topic topic = mCachedTopics.get(epochId).get(Pair.create(app, sdk));
                    if (topic != null && !topicsSet.contains(topic.getTopic())) {
                        topics.add(topic);
                        topicsSet.add(topic.getTopic());
                    }
                }
            }
        } finally {
            mReadWriteLock.readLock().unlock();
        }

        return topics;
    }

    /**
     * Gets a list of all topics that could be returned to the user in the last
     * numberOfLookBackEpochs epochs. Does not include the current epoch, so range is
     * [currentEpochId - numberOfLookBackEpochs, currentEpochId - 1].
     *
     * @return The list of Topics.
     */
    @NonNull
    public ImmutableList<Topic> getKnownTopicsWithConsent() {
        // We will need to look at the 3 historical epochs starting from last epoch.
        long epochId = mEpochManager.getCurrentEpochId() - 1;
        HashSet<Topic> topics = new HashSet<>();
        mReadWriteLock.readLock().lock();
        try {
            for (int numEpoch = 0;
                    numEpoch < mFlags.getTopicsNumberOfLookBackEpochs();
                    numEpoch++) {
                if (mCachedTopics.containsKey(epochId - numEpoch)) {
                    topics.addAll(
                            mCachedTopics.get(epochId - numEpoch).values().stream()
                                    .filter(
                                            topic ->
                                                    !mCachedBlockedTopicIds.contains(
                                                            topic.getTopic()))
                                    .collect(Collectors.toList()));
                }
            }
        } finally {
            mReadWriteLock.readLock().unlock();
        }
        return ImmutableList.copyOf(topics);
    }

    /**
     * Gets a list of all cached topics that were blocked by the user.
     *
     * @return The list of Topics.
     */
    @NonNull
    public ImmutableList<Topic> getTopicsWithRevokedConsent() {
        mReadWriteLock.readLock().lock();
        try {
            return ImmutableList.copyOf(mCachedBlockedTopics);
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Delete all data generated by Topics API, except for tables in the exclusion list.
     *
     * @param tablesToExclude a {@link List} of tables that won't be deleted.
     */
    public void clearAllTopicsData(@NonNull List<String> tablesToExclude) {
        mReadWriteLock.writeLock().lock();
        try {
            mTopicsDao.deleteAllTopicsTables(tablesToExclude);
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    @Override
    public void dump(@NonNull PrintWriter writer, String[] args) {
        boolean isVerbose =
                args != null
                        && args.length >= 1
                        && Integer.parseInt(args[0].toLowerCase()) == VERBOSE;
        writer.println("==== CacheManager Dump ====");
        writer.println(String.format("mCachedTopics size: %d", mCachedTopics.size()));
        if (isVerbose) {
            for (Long epochId : mCachedTopics.keySet()) {
                writer.println(String.format("Epoch Id: %d \n", epochId));
                Map<Pair<String, String>, Topic> epochMapping = mCachedTopics.get(epochId);
                for (Pair<String, String> pair : epochMapping.keySet()) {
                    String app = pair.first;
                    String sdk = pair.second;
                    Topic topic = epochMapping.get(pair);
                    writer.println(String.format("(%s, %s): %s", app, sdk, topic.toString()));
                }
            }
        }
    }
}
