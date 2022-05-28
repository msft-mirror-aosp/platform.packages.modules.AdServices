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

import static android.adservices.topics.TopicsManager.RESULT_OK;

import android.adservices.topics.GetTopicsResult;
import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Worker class to handle Topics API Implementation.
 *
 * <p>This class is thread safe.
 *
 * @hide
 */
@ThreadSafe
@WorkerThread
public class TopicsWorker {
    // Singleton instance of the TopicsWorker.
    private static volatile TopicsWorker sTopicsWorker;

    // Lock for concurrent Read and Write processing in TopicsWorker.
    // Read-only API will only need to acquire Read Lock.
    // Write API (can update data) will need to acquire Write Lock.
    // This lock allows concurrent Read API and exclusive Write API.
    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();

    private final EpochManager mEpochManager;
    private final CacheManager mCacheManager;
    private final Flags mFlags;

    @VisibleForTesting
    TopicsWorker(@NonNull EpochManager epochManager, @NonNull CacheManager cacheManager,
            Flags flags) {
        mEpochManager = epochManager;
        mCacheManager = cacheManager;
        mFlags = flags;
    }

    /**
     * Gets an instance of TopicsWorker to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static TopicsWorker getInstance(Context context) {
        if (sTopicsWorker == null) {
            synchronized (TopicsWorker.class) {
                if (sTopicsWorker == null) {
                    sTopicsWorker = new TopicsWorker(EpochManager.getInstance(context),
                            CacheManager.getInstance(context), FlagsFactory.getFlags());
                }
            }
        }
        return sTopicsWorker;
    }

    /**
     * Get topics for the specified app and sdk.
     *
     * @param app the app
     * @param sdk the sdk. In case the app calls the Topics API directly, the skd == empty string.
     * @return the Topics Response.
     */
    @NonNull
    public GetTopicsResult getTopics(@NonNull String app, @NonNull String sdk) {
        LogUtil.v("TopicsWorker.getTopics for %s, %s", app, sdk);
        mReadWriteLock.readLock().lock();
        try {
            List<Topic> topics = mCacheManager.getTopics(
                    mFlags.getTopicsNumberOfLookBackEpochs(), app, sdk);

            List<Long> taxonomyVersions = new ArrayList<>(topics.size());
            List<Long> modelVersions = new ArrayList<>(topics.size());
            List<Integer> topicIds = new ArrayList<>(topics.size());

            for (Topic topic : topics) {
                taxonomyVersions.add(topic.getTaxonomyVersion());
                modelVersions.add(topic.getModelVersion());
                topicIds.add(topic.getTopic());
            }

            GetTopicsResult result = new GetTopicsResult.Builder()
                    .setResultCode(RESULT_OK)
                    .setTaxonomyVersions(taxonomyVersions)
                    .setModelVersions(modelVersions)
                    .setTopics(topicIds)
                    .build();
            LogUtil.v("The result of TopicsWorker.getTopics for %s, %s is %s", app, sdk,
                    result.toString());
            return result;
        } finally {
            mReadWriteLock.readLock().unlock();
        }
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
    @NonNull
    public void recordUsage(@NonNull String app, @NonNull String sdk) {
        mReadWriteLock.readLock().lock();
        try {
            mEpochManager.recordUsageHistory(app, sdk);
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Load the Topics Cache from DB.
     */
    @NonNull
    public void loadCache() {
        // This loadCache happens when the TopicsService is created. The Cache is empty at that
        // time. Since the load happens async, clients can call getTopics API during the cache load.
        // Here we use Write lock to block Read during that loading time.
        mReadWriteLock.writeLock().lock();
        try {
            mCacheManager.loadCache();
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /**
     * Compute Epoch algorithm.
     * If the computation succeed, it will reload the cache.
     */
    @NonNull
    public void computeEpoch() {
        // This computeEpoch happens in the EpochJobService which happens every epoch. Since the
        // epoch computation happens async, clients can call getTopics API during the epoch
        // computation. Here we use Write lock to block Read during that computation time.
        mReadWriteLock.writeLock().lock();
        try {
            mEpochManager.processEpoch();

            // TODO(b/227179955): Handle error in mEpochManager.processEpoch and only reload Cache
            // when the computation succeeded.
            loadCache();
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

}
