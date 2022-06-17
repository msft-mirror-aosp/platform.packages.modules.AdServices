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

import com.android.adservices.LogUtil;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.internal.annotations.VisibleForTesting;

/** Class to manage blocked {@link Topic}s. */
public class BlockedTopicsManager {
    private static BlockedTopicsManager sSingleton;

    private final TopicsDao mTopicsDao;

    @VisibleForTesting
    BlockedTopicsManager(TopicsDao topicsDao) {
        mTopicsDao = topicsDao;
    }

    /** Returns an instance of the {@link BlockedTopicsManager} given a context. */
    @NonNull
    public static BlockedTopicsManager getInstance(Context context) {
        synchronized (BlockedTopicsManager.class) {
            if (sSingleton == null) {
                sSingleton = new BlockedTopicsManager(TopicsDao.getInstance(context));
            }
            return sSingleton;
        }
    }

    /**
     * Revoke consent for provided {@link Topic} (block topic). This topic will not be returned by
     * any of the {@link TopicsWorker} methods.
     *
     * @param topic {@link Topic} to block.
     */
    public void blockTopic(@NonNull Topic topic) {
        LogUtil.v("BlockedTopicsManager.blockTopic");
        mTopicsDao.recordBlockedTopic(topic);
    }

    /**
     * Restore consent for provided {@link Topic} (unblock the topic). This topic can be returned by
     * any of the {@link TopicsWorker} methods.
     *
     * @param topic {@link Topic} to restore consent for.
     */
    public void unblockTopic(@NonNull Topic topic) {
        LogUtil.v("BlockedTopicsManager.unblockTopic");
        mTopicsDao.removeBlockedTopic(topic);
    }
}
