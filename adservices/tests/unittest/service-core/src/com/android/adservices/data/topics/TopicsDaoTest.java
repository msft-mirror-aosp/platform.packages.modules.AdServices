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
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.MediumTest;

import com.android.adservices.data.DbTestUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Unit tests for {@link com.android.adservices.data.topics.TopicsDao} */
@MediumTest
public final class TopicsDaoTest {
    private static final String TAG = "TopicsDaoTest";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final TopicsDao mTopicsDao = TopicsDao.getInstanceForTest(mContext);

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Erase all existing data.
        DbTestUtil.deleteTable(TopicsTables.TaxonomyContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppClassificationTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.CallerCanLearnTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.ReturnedTopicContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.UsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppUsageHistoryContract.TABLE);
    }

    @Test
    public void testPersistAndGetAppClassificationTopics() {
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;

        final long epochId1 = 1L;
        final long epochId2 = 2L;

        final String app1 = "app1";
        final String app2 = "app2";

        // Initialize appClassificationTopicsMap and topics
        Map<String, List<String>> appClassificationTopicsMap1 = new HashMap<>();
        Map<String, List<String>> appClassificationTopicsMap2 = new HashMap<>();
        Topic topic1 = new Topic("topic1", taxonomyVersion, modelVersion);
        Topic topic2 = new Topic("topic1", taxonomyVersion, modelVersion);
        // to test multiple topics for one app
        appClassificationTopicsMap1.put(app1, Arrays.asList(topic1.getTopic(), topic2.getTopic()));

        // to test different apps
        appClassificationTopicsMap1.put(app2, Collections.singletonList(topic1.getTopic()));

        // to test different epochs for same app
        appClassificationTopicsMap2.put(app1, Collections.singletonList(topic1.getTopic()));

        mTopicsDao.persistAppClassificationTopics(epochId1, taxonomyVersion, modelVersion,
                appClassificationTopicsMap1);
        mTopicsDao.persistAppClassificationTopics(epochId2, taxonomyVersion, modelVersion,
                appClassificationTopicsMap2);

        // MapEpoch1: app1 -> topic1, topic2; app2 -> topic1
        // MapEpoch2: app1 -> topic1
        Map<String, List<Topic>> expectedTopicsMap1 = new HashMap<>();
        Map<String, List<Topic>> expectedTopicsMap2 = new HashMap<>();
        expectedTopicsMap1.put(app1, Arrays.asList(topic1, topic2));
        expectedTopicsMap1.put(app2, Collections.singletonList(topic1));
        expectedTopicsMap2.put(app1, Collections.singletonList(topic1));

        Map<String, List<Topic>> topicsMapFromDb1 = mTopicsDao
                .retrieveAppClassificationTopics(epochId1);
        Map<String, List<Topic>> topicsMapFromDb2 = mTopicsDao
                .retrieveAppClassificationTopics(epochId2);
        assertThat(topicsMapFromDb1).isEqualTo(expectedTopicsMap1);
        assertThat(topicsMapFromDb2).isEqualTo(expectedTopicsMap2);

        // to test non-existed epoch ID
        final long epochId3 = 3L;
        assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId3)).isEmpty();
    }

    @Test
    public void testPersistAppClassificationTopics_nullTopicsMap() {
        assertThrows(
                NullPointerException.class,
                () -> mTopicsDao.persistAppClassificationTopics(/* epochId */ 1L,
                        /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L,
                        /* appClassificationMap */ null)
        );
    }

    @Test
    public void testGetTopTopicsAndPersistTopics() {
        List<String> topTopics = Arrays.asList("topic1", "topic2", "topic3", "topic4", "topic5",
                "random_topic");
        mTopicsDao.persistTopTopics(/* epochId = */ 1L, topTopics);

        List<String> topicsFromDb = mTopicsDao.retrieveTopTopics(/* epochId = */ 1L);

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(topicsFromDb).isEqualTo(topTopics);
    }

    @Test
    public void testGetTopTopicsAndPersistTopics_notFoundEpochId() {
        List<String> topTopics = Arrays.asList("topic1", "topic2", "topic3", "topic4", "topic5",
                "random_topic");

        mTopicsDao.persistTopTopics(/* epochId = */ 1L, topTopics);

        // Try to fetch TopTopics for a different epoch. It should find anything.
        List<String> topicsFromDb = mTopicsDao.retrieveTopTopics(/* epochId = */ 2L);

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
                mTopicsDao.retrieveAppSdksUsageMap(/* epochId = */ 1L);

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
        expectedAppSdksUsageMap.put("app3", Arrays.asList(""));

        // Now read back the usages from DB.
        // Note that we record for epochId = 1L but read from DB for epochId = 2L.
        Map<String, List<String>> appSdksUsageMapFromDb =
                mTopicsDao.retrieveAppSdksUsageMap(/* epochId = */ 2L);

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
                    mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app", /* sdk = */ null);
                });
    }

    @Test
    public void testRecordUsageHistory_emptyApp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mTopicsDao.recordUsageHistory(/* epochId = */ 1L, /* app = */ "", "sdk");
                });
    }

    @Test
    public void testRecordAndRetrieveAppUsageHistory() {
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app1");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app2");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app2");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app3");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app3");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app3");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 2L, "app1");

        // Epoch 1
        Map<String, Integer> expectedAppUsageMap1 = new HashMap<>();
        expectedAppUsageMap1.put("app1", 1);
        expectedAppUsageMap1.put("app2", 2);
        expectedAppUsageMap1.put("app3", 3);

        // Now read back the usages from DB.
        Map<String, Integer> appUsageMapFromDb1 =
                mTopicsDao.retrieveAppUsageMap(/* epochId = */ 1L);

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(appUsageMapFromDb1).isEqualTo(expectedAppUsageMap1);

        // Epoch 2
        Map<String, Integer> expectedAppUsageMap2 = new HashMap<>();
        expectedAppUsageMap2.put("app1", 1);

        // Now read back the usages from DB.
        Map<String, Integer> appUsageMapFromDb2 =
                mTopicsDao.retrieveAppUsageMap(/* epochId = */ 2L);

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(appUsageMapFromDb2).isEqualTo(expectedAppUsageMap2);
    }

    @Test
    public void testRecordAppUsageHistory_notFoundEpochId() {
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app1");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app2");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app2");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app3");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app3");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app3");

        // Now read back the usages from DB.
        Map<String, Integer> appUsageMapFromDb =
                mTopicsDao.retrieveAppUsageMap(/* epochId = */ 2L);

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(appUsageMapFromDb).isEmpty();
    }

    @Test
    public void testRecordAppUsageHistory_nullApp() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, /* app = */ null);
                });
    }

    @Test
    public void testRecordAppUsageHistory_emptyApp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, /* app = */ "");
                });
    }

    @Test
    public void testPersistCallerCanLearnTopics() {
        Map<String, List<String>> appSdksUsageMap = new HashMap<>();

        // app1 called Topics API directly. In addition, 2 of its sdk1 an sdk2 called the Topics
        // API.
        appSdksUsageMap.put("app1", Arrays.asList("", "sdk1", "sdk2"));

        appSdksUsageMap.put("app2", Arrays.asList("sdk1", "sdk3", "sdk4"));
        appSdksUsageMap.put("app3", Arrays.asList("sdk1", "sdk5"));

        // app4 has no SDKs, it called Topics API directly.
        appSdksUsageMap.put("app4", Arrays.asList(""));

        appSdksUsageMap.put("app5", Arrays.asList("sdk1", "sdk5"));

        Map<String, List<String>> appClassificationTopicsMap = new HashMap<>();
        appClassificationTopicsMap.put("app1", Arrays.asList("topic1", "topic2"));
        appClassificationTopicsMap.put("app2", Arrays.asList("topic2", "topic3"));
        appClassificationTopicsMap.put("app3", Arrays.asList("topic4", "topic5"));
        appClassificationTopicsMap.put("app4", Arrays.asList("topic5", "topic6"));

        // app5 has not classification topics.
        appClassificationTopicsMap.put("app5", Arrays.asList());

        Map<String, Set<String>> callerCanLearnMap = new HashMap<>();
        // topic1 is a classification topic for app1, so all SDKs in apps1 can learn this topic.
        // In addition, the app1 called the Topics API directly so it can learn topic1 as well.
        callerCanLearnMap.put("topic1", new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2")));

        // topic2 is a classification topic for app1 and app2, so any SDKs in app1 or app2 can learn
        // this topic.
        callerCanLearnMap.put(
                "topic2", new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2", "sdk3", "sdk4")));

        // topic3 is a classification topic for app2, so all SDKs in apps2 can learn this topic.
        callerCanLearnMap.put("topic3", new HashSet<>(Arrays.asList("sdk1", "sdk3", "sdk4")));

        // topic4 is a classification topic for app3, so all SDKs in apps3 can learn this topic.
        callerCanLearnMap.put("topic4", new HashSet<>(Arrays.asList("sdk1", "sdk5")));

        // topic5 is a classification topic for app3 and app4, so any SDKs in apps3 or app4 can
        // learn this topic.
        // app4 called Topics API directly, so it can learn this topic.
        callerCanLearnMap.put("topic5", new HashSet<>(Arrays.asList("sdk1", "sdk5", "app4")));

        // app4 called the Topics API directly so it can learn this topic.
        callerCanLearnMap.put("topic6", new HashSet<>(Arrays.asList("app4")));

        mTopicsDao.persistCallerCanLearnTopics(/* epochId = */ 3L, callerCanLearnMap);

        Map<String, Set<String>> callerCanLearnMapFromDb =
                mTopicsDao.retrieveCallerCanLearnTopicsMap(/* epochId = */ 5L,
                        /* howManyEpochs = */ 3);

        assertThat(callerCanLearnMap).isEqualTo(callerCanLearnMapFromDb);
    }

    @Test
    public void testPersistAndRetrieveReturnedAppTopics_oneEpoch() {
        // returnedAppSdkTopics = Map<Pair<App, Sdk>, Topic>
        Map<Pair<String, String>, String> returnedAppSdkTopics = new HashMap<>();
        returnedAppSdkTopics.put(Pair.create("app1", ""), "topic1");
        returnedAppSdkTopics.put(Pair.create("app1", "sdk1"), "topic1");
        returnedAppSdkTopics.put(Pair.create("app1", "sdk2"), "topic1");

        returnedAppSdkTopics.put(Pair.create("app2", "sdk1"), "topic2");
        returnedAppSdkTopics.put(Pair.create("app2", "sdk3"), "topic2");
        returnedAppSdkTopics.put(Pair.create("app2", "sdk4"), "topic2");

        returnedAppSdkTopics.put(Pair.create("app3", "sdk1"), "topic3");

        returnedAppSdkTopics.put(Pair.create("app5", "sdk1"), "topic5");
        returnedAppSdkTopics.put(Pair.create("app5", "sdk5"), "topic5");

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId = */ 1L, /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L, returnedAppSdkTopics);

        Map<Pair<String, String>, Topic> expectedReturnedAppSdkTopics = new HashMap<>();
        for (Map.Entry<Pair<String, String>, String>  entry
                : returnedAppSdkTopics.entrySet()) {
            expectedReturnedAppSdkTopics.put(entry.getKey(),
                    new Topic(entry.getValue(), /* taxonomyVersion = */ 1L,
                            /* modelVersion = */ 1L));
        }

        // Map<EpochId, Map<Pair<App, Sdk>, Topic>
        Map<Long, Map<Pair<String, String>, Topic>> returnedTopicsFromDb =
                mTopicsDao.retrieveReturnedTopics(/* epochId = */ 1L,
                        /* numberOfLookBackEpochs = */ 1);

        // There is 1 epoch.
        assertThat(returnedTopicsFromDb).hasSize(1);
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsFromDb = returnedTopicsFromDb.get(1L);

        // And the returedAppSdkTopics match.
        assertThat(returnedAppSdkTopicsFromDb).isEqualTo(expectedReturnedAppSdkTopics);
    }

    @Test
    public void testPersistAndRetrieveReturnedAppTopics_multipleEpochs() {
        // We will have 5 topics and setup the returned topics for epoch 3, 2, and 1.
        Topic topic1 = new Topic("topic1", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);
        Topic topic2 = new Topic("topic2", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);
        Topic topic3 = new Topic("topic3", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);
        Topic topic4 = new Topic("topic4", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);
        Topic topic5 = new Topic("topic5", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);

        // Setup for EpochId 1
        // Map<Pair<App, Sdk>, Topic>
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsForEpoch1 = new HashMap<>();
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app1", "sdk2"), topic1);

        returnedAppSdkTopicsForEpoch1.put(Pair.create("app2", "sdk1"), topic2);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app2", "sdk3"), topic2);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app2", "sdk4"), topic2);

        returnedAppSdkTopicsForEpoch1.put(Pair.create("app3", "sdk1"), topic3);

        returnedAppSdkTopicsForEpoch1.put(Pair.create("app5", "sdk1"), topic5);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app5", "sdk5"), topic5);

        // Convert the returned Topics with POJO to returned Topics with String.
        Map<Pair<String, String>, String> returnedTopicsStringForEpoch1 =
                returnedAppSdkTopicsForEpoch1.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().getTopic()));

        // Setup for EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsForEpoch2 = new HashMap<>();
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app1", ""), topic2);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app1", "sdk2"), topic2);

        returnedAppSdkTopicsForEpoch2.put(Pair.create("app2", "sdk1"), topic3);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app2", "sdk3"), topic3);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app2", "sdk4"), topic3);

        returnedAppSdkTopicsForEpoch2.put(Pair.create("app3", "sdk1"), topic4);

        returnedAppSdkTopicsForEpoch2.put(Pair.create("app5", "sdk1"), topic1);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app5", "sdk5"), topic1);

        // Convert the returned Topics with POJO to returned Topics with String.
        Map<Pair<String, String>, String> returnedTopicsStringForEpoch2 =
                returnedAppSdkTopicsForEpoch2.entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                e -> e.getValue().getTopic()));

        // Setup for EpochId 3
        // epochId == 3 does not have any topics. This could happen if the epoch computation failed
        // or the device was offline and no epoch computation was done.
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsForEpoch3 = new HashMap<>();
        // Convert the returned Topics with POJO to returned Topics with String.
        Map<Pair<String, String>, String> returnedTopicsStringForEpoch3 =
                returnedAppSdkTopicsForEpoch3.entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                e -> e.getValue().getTopic()));

        // Now persist the returned topics for 3 epochs
        mTopicsDao.persistReturnedAppTopicsMap(/* epochId = */ 1L,
                /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L,
                returnedTopicsStringForEpoch1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId = */ 2L,
                /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L,
                returnedTopicsStringForEpoch2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId = */ 3L,
                /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L,
                returnedTopicsStringForEpoch3);

        // Now retrieve from DB and verify the result for reach epoch.
        // Now look at epochId == 3 only by setting numberOfLookBackEpochs == 1.
        // Since the epochId 3 is empty, the results are always empty.
        Map<Long, Map<Pair<String, String>, Topic>> returnedTopicsFromDb =
                mTopicsDao.retrieveReturnedTopics(/* epochId = */ 3L,
                /* numberOfLookBackEpochs = */ 1);
        assertThat(returnedTopicsFromDb).isEmpty();

        // Now look at epochId in {3, 2} only by setting numberOfLookBackEpochs = 2.
        returnedTopicsFromDb =
                mTopicsDao.retrieveReturnedTopics(/* epochId = */ 3L,
                        /* numberOfLookBackEpochs = */ 2);
        Map<Long, Map<Pair<String, String>, Topic>> expectedReturnedTopics = new HashMap<>();
        expectedReturnedTopics.put(2L, returnedAppSdkTopicsForEpoch2);
        assertThat(returnedTopicsFromDb).isEqualTo(expectedReturnedTopics);

        // Now look at epochId in {3, 2, 1} only by setting numberOfLookBackEpochs = 3.
        returnedTopicsFromDb =
                mTopicsDao.retrieveReturnedTopics(/* epochId = */ 3L,
                        /* numberOfLookBackEpochs = */ 3);
        expectedReturnedTopics = new HashMap<>();
        expectedReturnedTopics.put(1L, returnedAppSdkTopicsForEpoch1);
        expectedReturnedTopics.put(2L, returnedAppSdkTopicsForEpoch2);
        assertThat(returnedTopicsFromDb).isEqualTo(expectedReturnedTopics);
    }
}
