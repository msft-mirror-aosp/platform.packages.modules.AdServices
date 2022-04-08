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
import android.util.Pair;

import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** A class to manage Topics Cache. */
public class CacheManager {
    private static CacheManager sSingleton;

    // Lock for Read and Write on the cached topics map.
    // This allows concurrent reads but exclusive update to the cache.
    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();

    private final EpochManager mEpochManager;
    private final TopicsDao mTopicsDao;
    private final Flags mFlags;

    // Map<EpochId, Map<Pair<App, Sdk>, Topic>
    private Map<Long, Map<Pair<String, String>, Topic>> mCachedTopics = new HashMap<>();

    @VisibleForTesting
    CacheManager(EpochManager epochManager, TopicsDao topicsDao, Flags flags) {
        mEpochManager = epochManager;
        mTopicsDao = topicsDao;
        mFlags = flags;
    }

    /** Returns an instance of the EpochManager given a context. */
    @NonNull
    public static CacheManager getInstance(Context context) {
        synchronized (CacheManager.class) {
            if (sSingleton == null) {
                sSingleton = new CacheManager(EpochManager.getInstance(context),
                        TopicsDao.getInstance(context), FlagsFactory.getFlags());
            }
            return sSingleton;
        }
    }

    /**
     * Load the cache from DB.
     *
     * When first created, the Cache is empty. We will need to retrieve the cache from DB.
     */
    public void loadCache() {
        // Retrieve the cache from DB.
        // Map<EpochId, Map<Pair<App, Sdk>, Topic>
        Map<Long, Map<Pair<String, String>, Topic>> cacheFromDb =
                mTopicsDao.retrieveReturnedTopics(mEpochManager.getCurrentEpochId(),
                        mFlags.getTopicsNumberOfLookBackEpochs() + 1);

        mReadWriteLock.writeLock().lock();
        mCachedTopics = cacheFromDb;
        mReadWriteLock.writeLock().unlock();
    }

    /**
     * Get list of topics for the numberOfLookBackEpochs epoch starting from
     * [epochId - numberOfLookBackEpochs + 1, epochId]
     * @param epochId the current epochId.
     * @param numberOfLookBackEpochs how many epochs to look back.
     * @param app the app
     * @param sdk the sdk. In case the app calls the Topics API directly, the sdk == empty string.
     * @return The list of Topics.
     */
    @NonNull
    public List<Topic> getTopics(int numberOfLookBackEpochs, String app, String sdk) {
        // We will need to look at the 3 historical epochs starting from last epoch.
        long epochId = mEpochManager.getCurrentEpochId() - 1;
        List<Topic> topics = new ArrayList<>();
        mReadWriteLock.readLock().lock();
        for (int numEpoch = 0; numEpoch < numberOfLookBackEpochs; numEpoch++) {
            if (mCachedTopics.containsKey(epochId - numEpoch)) {
                Topic topic = mCachedTopics.get(epochId - numEpoch).get(Pair.create(app, sdk));
                if (topic != null) {
                    topics.add(topic);
                }
            }
        }
        mReadWriteLock.readLock().unlock();

        // TODO(b/223916758): randomly shuffle the topics before returning.
        return topics;
    }
}
