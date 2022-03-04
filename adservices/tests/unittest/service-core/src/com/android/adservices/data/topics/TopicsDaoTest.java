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

package com.android.adservices.data.topics;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.MediumTest;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link com.android.adservices.data.topics.TopicsDao} */
@MediumTest
public final class TopicsDaoTest {
    private static final String TAG = "TopicsDaoTest";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    TopicsDao mTopicsDao = TopicsDao.getInstanceForTest(mContext);

    @Test
    public void testGetTopTopicsAndPersistTopics() {
        List<String> topTopics = Arrays.asList("topic1", "topic2", "topic3", "topic4", "topic5",
                "random_topic");

        mTopicsDao.persistTopTopics(/* epochId = */ 1L, topTopics);

        List<String> topicsFromDb = mTopicsDao.getTopTopics(/* epochId = */ 1L);

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(topicsFromDb).isEqualTo(topTopics);
    }

    @Test
    public void testGetTopTopicsAndPersistTopics_notFoundEpochId() {
        List<String> topTopics = Arrays.asList("topic1", "topic2", "topic3", "topic4", "topic5",
                "random_topic");

        mTopicsDao.persistTopTopics(/* epochId = */ 1L, topTopics);

        // Try to fetch TopTopics for a different epoch. It should find anything.
        List<String> topicsFromDb = mTopicsDao.getTopTopics(/* epochId = */ 2L);

        assertThat(topicsFromDb).isEmpty();
    }

    @Test
    public void testGetTopTopicsAndPersistTopics_invalidSize() {
        // Not enough 6 topics.
        List<String> topTopics = Arrays.asList("topic1", "topic2", "topic3", "random_topic");

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mTopicsDao.persistTopTopics(/* epochId = */ 1L, topTopics);
                });
    }

    @Test
    public void testGetTopTopicsAndPersistTopics_nullTopTopics() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mTopicsDao.persistTopTopics(/* epochId = */ 1L, /* topTopics = */ null);
                });
    }

    @Test
    public void testRecordUsageHistory() {
        // Record some usages.
        // App1 called the Topics API directly and its SDKs also call Topics API.
        // Empty SDK implies the app calls the Topics API directly.
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app1", /* sdk = */ "");
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app1", "sdk1");
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app1", "sdk2");

        // App2 only did not call Topics API directly. Only SDKs of the app2 called the Topics API.
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app2", "sdk1");
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app2", "sdk3");

        // App3 called the Topics API directly and has not other SDKs.
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app3", /* sdk = */ "");

        Map<String, List<String>> expectedAppSdksUsageMap = new HashMap<>();
        expectedAppSdksUsageMap.put("app1", Arrays.asList("", "sdk1", "sdk2"));
        expectedAppSdksUsageMap.put("app2", Arrays.asList("sdk1", "sdk3"));
        expectedAppSdksUsageMap.put("app3", Arrays.asList(""));

        // Now read back the usages from DB.
        Map<String, List<String>> appSdksUsageMapFromDb =
                mTopicsDao.produceAppSdksUsageMap(/* epochId = */ 1L);

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(appSdksUsageMapFromDb).isEqualTo(expectedAppSdksUsageMap);
    }

    @Test
    public void testRecordUsageHistory_notFoundEpochId() {
        // Record some usages.
        // App1 called the Topics API directly and its SDKs also call Topics API.
        // Empty SDK implies the app calls the Topics API directly.
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app1", /* sdk = */ "");
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app1", "sdk1");
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app1", "sdk2");

        // App2 only did not call Topics API directly. Only SDKs of the app2 called the Topics API.
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app2", "sdk1");
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app2", "sdk3");

        // App3 called the Topics API directly and has not other SDKs.
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app3", /* sdk = */ "");

        Map<String, List<String>> expectedAppSdksUsageMap = new HashMap<>();
        expectedAppSdksUsageMap.put("app1", Arrays.asList("", "sdk1", "sdk2"));
        expectedAppSdksUsageMap.put("app2", Arrays.asList("sdk1", "sdk3"));
        expectedAppSdksUsageMap.put("app2", Arrays.asList(""));

        // Now read back the usages from DB.
        // Note that we record for epochId = 1L but read from DB for epochId = 2L.
        Map<String, List<String>> appSdksUsageMapFromDb =
                mTopicsDao.produceAppSdksUsageMap(/* epochId = */ 2L);

        // The map from DB is empty since we read epochId = 2L.
        assertThat(appSdksUsageMapFromDb).isEmpty();
    }

    @Test
    public void testRecordUsageHistory_nullApp() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mTopicsDao.recordUsageHistory(/* epochId = */ 1L, /* app = */ null, "sdk1");
                });
    }

    @Test
    public void testRecordUsageHistory_nullSdk() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app",  /* sdk = */ null);
                });
    }

    @Test
    public void testRecordUsageHistory_emptyApp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mTopicsDao.recordUsageHistory(/* epochId = */ 1L, /* app = */ "",  "sdk");
                });
    }
}
