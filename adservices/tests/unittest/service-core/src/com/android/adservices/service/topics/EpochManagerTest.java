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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.MockRandom;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.topics.classifier.Classifier;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Unit tests for {@link com.android.adservices.service.topics.EpochManager} */
@SmallTest
public final class EpochManagerTest {
    private static final String TAG = "EpochManagerTest";

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final Flags mFlags = FlagsFactory.getFlagsForTest();

    @Mock Classifier mMockClassifier;

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
    public void testComputeCallersCanLearnMap() {
        Map<String, List<String>> appSdksUsageMap = new HashMap<>();

        // app1 called Topics API directly. In addition, 2 of its sdk1 an sdk2 called the Topics
        // API.
        appSdksUsageMap.put("app1", Arrays.asList("", "sdk1", "sdk2"));

        appSdksUsageMap.put("app2", Arrays.asList("sdk1", "sdk3", "sdk4"));
        appSdksUsageMap.put("app3", Arrays.asList("sdk1", "sdk5"));

        // app4 has no SDKs, it called Topics API directly.
        appSdksUsageMap.put("app4", Arrays.asList(""));

        appSdksUsageMap.put("app5", Arrays.asList("sdk1", "sdk5"));

        Map<String, List<Integer>> appClassificationTopicsMap = new HashMap<>();
        appClassificationTopicsMap.put("app1", Arrays.asList(1, 2));
        appClassificationTopicsMap.put("app2", Arrays.asList(2, 3));
        appClassificationTopicsMap.put("app3", Arrays.asList(4, 5));
        appClassificationTopicsMap.put("app4", Arrays.asList(5, 6));

        // app5 has not classification topics.
        appClassificationTopicsMap.put("app5", Arrays.asList());

        Map<Integer, Set<String>> expectedCallerCanLearnMap = new HashMap<>();
        // topic1 is a classification topic for app1, so all SDKs in apps1 can learn this topic.
        // In addition, the app1 called the Topics API directly so it can learn topic1 as well.
        expectedCallerCanLearnMap.put(
                /* topic */ 1, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2")));

        // topic2 is a classification topic for app1 and app2, so any SDKs in app1 or app2 can learn
        // this topic.
        expectedCallerCanLearnMap.put(
                /* topic */ 2,
                new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2", "sdk3", "sdk4")));

        // topic3 is a classification topic for app2, so all SDKs in apps2 can learn this topic.
        expectedCallerCanLearnMap.put(
                /* topic */ 3, new HashSet<>(Arrays.asList("sdk1", "sdk3", "sdk4")));

        // topic4 is a classification topic for app3, so all SDKs in apps3 can learn this topic.
        expectedCallerCanLearnMap.put(/* topic */ 4, new HashSet<>(Arrays.asList("sdk1", "sdk5")));

        // topic5 is a classification topic for app3 and app4, so any SDKs in apps3 or app4 can
        // learn this topic.
        // app4 called Topics API directly, so it can learn this topic.
        expectedCallerCanLearnMap.put(
                /* topic */ 5, new HashSet<>(Arrays.asList("sdk1", "sdk5", "app4")));

        // app4 called the Topics API directly so it can learn this topic.
        expectedCallerCanLearnMap.put(/* topic */ 6, new HashSet<>(Arrays.asList("app4")));

        Map<Integer, Set<String>> canLearnMap =
                EpochManager.computeCallersCanLearnMap(appSdksUsageMap, appClassificationTopicsMap);

        assertThat(canLearnMap).isEqualTo(expectedCallerCanLearnMap);
    }

    @Test
    public void testComputeCallersCanLearnMap_nullUsageMapOrNullClassificationMap() {
        assertThrows(
                NullPointerException.class,
                () ->
                        EpochManager.computeCallersCanLearnMap(
                                /* appSdksUsageMap = */ null,
                                /* appClassificationTopicsMap = */ new HashMap<>()));

        assertThrows(
                NullPointerException.class,
                () ->
                        EpochManager.computeCallersCanLearnMap(
                                /* appSdksUsageMap = */ new HashMap<>(),
                                /* appClassificationTopicsMap = */ null));
    }

    @Test
    public void testSelectRandomTopic() {
        // Create a new epochManager that we can control the random generator.
        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        TopicsDao topicsDao = new TopicsDao(dbHelper);
        EpochManager epochManager =
                new EpochManager(
                        topicsDao,
                        dbHelper,
                        new MockRandom(new long[] {1, 5, 6, 7, 8, 9}),
                        mMockClassifier,
                        mFlags);
        List<Integer> topTopics = Arrays.asList(1, 2, 3, 4, 5, /* random_topic */ 6);

        // random = 1
        assertThat(epochManager.selectRandomTopic(topTopics)).isEqualTo(6);

        // random = 5
        assertThat(epochManager.selectRandomTopic(topTopics)).isEqualTo(1);

        // random = 6
        assertThat(epochManager.selectRandomTopic(topTopics)).isEqualTo(2);

        // random = 7
        assertThat(epochManager.selectRandomTopic(topTopics)).isEqualTo(3);

        // random = 8
        assertThat(epochManager.selectRandomTopic(topTopics)).isEqualTo(4);

        // random = 9
        assertThat(epochManager.selectRandomTopic(topTopics)).isEqualTo(5);
    }

    @Test
    public void testSelectRandomTopic_invalidSize_throw() {
        // Create a new epochManager that we can control the random generator.
        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        TopicsDao topicsDao = new TopicsDao(dbHelper);
        EpochManager epochManager =
                new EpochManager(
                        topicsDao,
                        dbHelper,
                        new MockRandom(new long[] {1, 5, 6, 7, 8, 9}),
                        mMockClassifier,
                        mFlags);

        assertThrows(
                IllegalArgumentException.class,
                () -> epochManager.selectRandomTopic(Arrays.asList(1, 2, 3)));

        assertThrows(
                NullPointerException.class,
                () -> epochManager.selectRandomTopic(/* topTopics = */ null));
    }

    @Test
    public void testComputeReturnedAppTopics() {
        // Create a new epochManager that we can control the random generator.
        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        TopicsDao topicsDao = new TopicsDao(dbHelper);
        EpochManager epochManager =
                new EpochManager(
                        topicsDao,
                        dbHelper,
                        new MockRandom(new long[] {1, 5, 6, 7, 8, 9}),
                        mMockClassifier,
                        mFlags);
        List<Integer> topTopics = Arrays.asList(1, 2, 3, 4, 5, /* random_topic */ 6);

        // Note: we iterate over the appSdksUsageMap. For the test to be deterministic, we use
        // LinkedHashMap so that the order of iteration is defined.
        // From Java doc:  https://docs.oracle.com/javase/6/docs/api/java/util/LinkedHashMap.html
        // "This linked list defines the iteration ordering, which is normally the order in which
        // keys were inserted into the map (insertion-order)."
        Map<String, List<String>> appSdksUsageMap = new LinkedHashMap<>();
        // app1 called Topics API directly. In addition, 2 of its sdk1 an sdk2 called the Topics
        // API.
        appSdksUsageMap.put("app1", Arrays.asList("", "sdk1", "sdk2"));

        appSdksUsageMap.put("app2", Arrays.asList("sdk1", "sdk3", "sdk4"));
        appSdksUsageMap.put("app3", Arrays.asList("sdk1", "sdk5"));

        // app4 has no SDKs, it called Topics API directly.
        appSdksUsageMap.put("app4", Arrays.asList(""));

        appSdksUsageMap.put("app5", Arrays.asList("sdk1", "sdk5"));

        // Note: the appClassificationTopicsMap is not used in this test but we add here
        // for documentation.
        Map<String, List<Integer>> appClassificationTopicsMap = new HashMap<>();
        appClassificationTopicsMap.put("app1", Arrays.asList(1, 2));
        appClassificationTopicsMap.put("app2", Arrays.asList(2, 3));
        appClassificationTopicsMap.put("app3", Arrays.asList(4, 5));
        appClassificationTopicsMap.put("app4", Arrays.asList(5, 6));

        Map<Integer, Set<String>> callersCanLearnMap = new HashMap<>();
        // topic1 is a classification topic for app1, so all SDKs in apps1 can learn this topic.
        // In addition, the app1 called the Topics API directly so it can learn topic1 as well.
        callersCanLearnMap.put(/* topic */ 1, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2")));

        // topic2 is a classification topic for app1 and app2, so any SDKs in app1 or app2 can learn
        // this topic.
        callersCanLearnMap.put(
                /* topic */ 2,
                new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2", "sdk3", "sdk4")));

        // topic3 is a classification topic for app2, so all SDKs in apps2 can learn this topic.
        callersCanLearnMap.put(/* topic */ 3, new HashSet<>(Arrays.asList("sdk1", "sdk3", "sdk4")));

        // topic4 is a classification topic for app3, so all SDKs in apps3 can learn this topic.
        callersCanLearnMap.put(/* topic */ 4, new HashSet<>(Arrays.asList("sdk1", "sdk5")));

        // topic5 is a classification topic for app3 and app4, so any SDKs in apps3 or app4 can
        // learn this topic.
        // app4 called Topics API directly, so it can learn this topic.
        callersCanLearnMap.put(/* topic */ 5, new HashSet<>(Arrays.asList("sdk1", "sdk5", "app4")));

        // app4 called the Topics API directly so it can learn this topic.
        callersCanLearnMap.put(/* topic */ 6, new HashSet<>(Arrays.asList("app4")));

        // Random sequence numbers used in this test: {1, 5, 6, 7, 8, 9}.
        // The order of selected topics by iterations: "random_topic", "topic1", "topic2", "topic3",
        // "topic 4, "topic5".
        // The order of app is inserted in appSdksUsageMap: app1, app2, app3, app4, app5.
        // So random_topic is selected for app1, topic1 is selected for app2,
        // topic2 is selected for app3, topic3 is selected for app4, topic4 is selected for app5.
        Map<Pair<String, String>, Integer> returnedAppSdkTopics =
                epochManager.computeReturnedAppSdkTopics(
                        callersCanLearnMap, appSdksUsageMap, topTopics);

        Map<Pair<String, String>, Integer> expectedReturnedTopics = new HashMap<>();
        // Topic4 is selected for app5. Both sdk1 and sdk5 can learn about topic4.
        // (look at callersCanLearnMap)
        expectedReturnedTopics.put(Pair.create("app5", "sdk1"), /* topic */ 4);
        expectedReturnedTopics.put(Pair.create("app5", "sdk5"), /* topic */ 4);

        // Topic2 is selected for app3. However only sdk1 can learn about topic2.
        // sdk5 can't learn topic2.
        expectedReturnedTopics.put(Pair.create("app3", "sdk1"), /* topic */ 2);

        // Topic1 is selected for app2. However only sdk1 can learn about topic1.
        // sdk3, and sdk4 can't learn topic1.
        expectedReturnedTopics.put(Pair.create("app2", "sdk1"), /* topic */ 1);

        assertThat(returnedAppSdkTopics).isEqualTo(expectedReturnedTopics);
    }

    @Test
    public void testRecordUsage() {
        // Create a new epochManager that we can control the random generator.
        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        TopicsDao topicsDao = new TopicsDao(dbHelper);
        EpochManager epochManager =
                new EpochManager(topicsDao, dbHelper, new Random(), mMockClassifier, mFlags);

        // Record some usages.
        // App1 called the Topics API directly and its SDKs also call Topics API.
        // Empty SDK implies the app calls the Topics API directly.
        epochManager.recordUsageHistory("app1", /* sdk = */ "");
        epochManager.recordUsageHistory("app1", "sdk1");
        epochManager.recordUsageHistory("app1", "sdk2");

        // App2 only did not call Topics API directly. Only SDKs of the app2 called the Topics API.
        epochManager.recordUsageHistory("app2", "sdk1");
        epochManager.recordUsageHistory("app2", "sdk3");

        // App3 called the Topics API directly and has not other SDKs.
        epochManager.recordUsageHistory("app3", /* sdk = */ "");

        Map<String, List<String>> expectedAppSdksUsageMap = new HashMap<>();
        expectedAppSdksUsageMap.put("app1", Arrays.asList("", "sdk1", "sdk2"));
        expectedAppSdksUsageMap.put("app2", Arrays.asList("sdk1", "sdk3"));
        expectedAppSdksUsageMap.put("app3", Arrays.asList(""));

        // Now read back the usages from DB.
        Map<String, List<String>> appSdksUsageMapFromDb =
                topicsDao.retrieveAppSdksUsageMap(epochManager.getCurrentEpochId());

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(appSdksUsageMapFromDb).isEqualTo(expectedAppSdksUsageMap);
    }

    @Test
    public void testGarbageCollectOutdatedEpochData() {
        // Create a local Topics Dao to do real db query instead of mocking
        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        TopicsDao topicsDao = new TopicsDao(dbHelper);
        // Mock the flag to make test result deterministic
        Flags mockedFlags = Mockito.mock(Flags.class);
        when(mockedFlags.getNumberOfEpochsToKeepInHistory()).thenReturn(3);

        // Both Random and Classifier are not used in this test so initialize them in convenience
        EpochManager epochManager =
                new EpochManager(topicsDao, dbHelper, new Random(), mMockClassifier, mockedFlags);

        final long currentEpoch = 6L;
        final int epochLookBackNumberForGarbageCollection = 3;
        // The epoch that is outdated starts from 6-1-3 = 2
        final long epochToDeleteFrom = currentEpoch - epochLookBackNumberForGarbageCollection - 1;

        // Save data in TopTopics Table and AppUsage table for gc testing
        List<Integer> topTopics = Arrays.asList(1, 2, 3, 4, 5, 6);
        final String appName = "app";
        // The epoch range for testing is [1, currentEpoch].
        for (long epoch = 1L; epoch <= currentEpoch; epoch++) {
            topicsDao.persistTopTopics(epoch, topTopics);
            topicsDao.recordAppUsageHistory(epoch, appName);
        }

        epochManager.garbageCollectOutdatedEpochData(currentEpoch);

        verify(mockedFlags).getNumberOfEpochsToKeepInHistory();

        for (long epoch = currentEpoch; epoch > epochToDeleteFrom; epoch--) {
            assertThat(topicsDao.retrieveTopTopics(epoch)).isEqualTo(topTopics);

            // App has called Topics API once in each epoch
            Map<String, Integer> appUsageMap = topicsDao.retrieveAppUsageMap(epoch);
            Map<String, Integer> expectedAppUsageMap = new HashMap<>();
            expectedAppUsageMap.put(appName, 1);
            assertThat(appUsageMap).isEqualTo(expectedAppUsageMap);
        }

        // Epoch [1, epochToDeleteFrom] have been garbage collected.
        for (long epoch = epochToDeleteFrom; epoch >= 1; epoch--) {
            assertThat(topicsDao.retrieveTopTopics(epoch)).isEmpty();
            assertThat(topicsDao.retrieveAppUsageMap(epoch)).isEmpty();
        }
    }

    @Test
    public void testComputeEpoch() {
        // Create a new EpochManager that we can control the random generator.
        //
        // In this test, in order to make test result to be deterministic so TopicsDao has to be
        // mocked to get a LinkedHashMap of appSdksUsageMap (see below for details) However, real
        // DB commitments need to be tested as well. Therefore, real methods will be called for
        // rest of TopicsDao usages.
        //
        // Furthermore, real DB commitments require Epoch ID to verify write and read so that
        // EpochManager also needs to be mocked, but initialized with real constructor
        //
        // Therefore, as only 1 method in EpochManager or TopicsDao needs to be mocked, use
        // Mockito.Spy instead of a full Mock object.
        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        TopicsDao topicsDao = Mockito.spy(new TopicsDao(dbHelper));
        EpochManager epochManager =
                Mockito.spy(
                        new EpochManager(
                                topicsDao,
                                dbHelper,
                                new MockRandom(new long[] {1, 5, 6, 7, 8, 9}),
                                mMockClassifier,
                                mFlags));
        // Mock EpochManager for getCurrentEpochId()
        final long epochId = 1L;
        when(epochManager.getCurrentEpochId()).thenReturn(epochId);

        // Note: we iterate over the appSdksUsageMap. For the test to be deterministic, we use
        // LinkedHashMap so that the order of iteration is defined.
        // From Java doc:  https://docs.oracle.com/javase/6/docs/api/java/util/LinkedHashMap.html
        // "This linked list defines the iteration ordering, which is normally the order in which
        // keys were inserted into the map (insertion-order)."
        Map<String, List<String>> appSdksUsageMap = new LinkedHashMap<>();
        // app1 called Topics API directly. In addition, 2 of its sdk1 an sdk2 called the Topics
        // API.
        appSdksUsageMap.put("app1", Arrays.asList("", "sdk1", "sdk2"));

        appSdksUsageMap.put("app2", Arrays.asList("sdk1", "sdk3", "sdk4"));
        appSdksUsageMap.put("app3", Arrays.asList("sdk1", "sdk5"));

        // app4 has no SDKs, it called Topics API directly.
        appSdksUsageMap.put("app4", Arrays.asList(""));

        appSdksUsageMap.put("app5", Arrays.asList("sdk1", "sdk5"));
        // Mock TopicsDao to return above LinkedHashMap for retrieveAppSdksUsageMap()
        when(topicsDao.retrieveAppSdksUsageMap(epochId)).thenReturn(appSdksUsageMap);

        Map<String, List<Integer>> appClassificationTopicsMap = new HashMap<>();
        appClassificationTopicsMap.put("app1", Arrays.asList(1, 2));
        appClassificationTopicsMap.put("app2", Arrays.asList(2, 3));
        appClassificationTopicsMap.put("app3", Arrays.asList(4, 5));
        appClassificationTopicsMap.put("app4", Arrays.asList(5, 6));
        when(mMockClassifier.classify(eq(appSdksUsageMap.keySet())))
                .thenReturn(appClassificationTopicsMap);

        List<Integer> topTopics = Arrays.asList(1, 2, 3, 4, 5, /* random_topic */ 6);
        when(mMockClassifier.getTopTopics(
                        eq(appClassificationTopicsMap),
                        eq(mFlags.getTopicsNumberOfTopTopics()),
                        eq(mFlags.getTopicsNumberOfRandomTopics())))
                .thenReturn(topTopics);

        epochManager.processEpoch();

        verify(epochManager).getCurrentEpochId();
        verify(topicsDao).retrieveAppSdksUsageMap(eq(epochId));
        verify(mMockClassifier).classify(eq(appSdksUsageMap.keySet()));
        verify(mMockClassifier)
                .getTopTopics(
                        eq(appClassificationTopicsMap),
                        eq(mFlags.getTopicsNumberOfTopTopics()),
                        eq(mFlags.getTopicsNumberOfRandomTopics()));

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic4 = Topic.create(/* topic */ 4, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic5 = Topic.create(/* topic */ 5, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic6 = Topic.create(/* topic */ 6, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);

        // Verify AppClassificationTopicsContract
        Map<String, List<Topic>> expectedAppClassificationTopicsMap = new HashMap<>();
        expectedAppClassificationTopicsMap.put("app1", Arrays.asList(topic1, topic2));
        expectedAppClassificationTopicsMap.put("app2", Arrays.asList(topic2, topic3));
        expectedAppClassificationTopicsMap.put("app3", Arrays.asList(topic4, topic5));
        expectedAppClassificationTopicsMap.put("app4", Arrays.asList(topic5, topic6));
        Map<String, List<Topic>> appClassificationTopicsMapFromDB =
                topicsDao.retrieveAppClassificationTopics(epochId);
        assertThat(appClassificationTopicsMapFromDB).isEqualTo(expectedAppClassificationTopicsMap);

        // Verify CallerCanLearnTopicsContract
        Map<Integer, Set<String>> expectedCallersCanLearnMap = new HashMap<>();
        // topic1 is a classification topic for app1, so all SDKs in apps1 can learn this topic.
        // In addition, the app1 called the Topics API directly so it can learn topic1 as well.
        expectedCallersCanLearnMap.put(
                /* topic */ 1, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2")));

        // topic2 is a classification topic for app1 and app2, so any SDKs in app1 or app2 can learn
        // this topic.
        expectedCallersCanLearnMap.put(
                /* topic */ 2,
                new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2", "sdk3", "sdk4")));

        // topic3 is a classification topic for app2, so all SDKs in apps2 can learn this topic.
        expectedCallersCanLearnMap.put(
                /* topic */ 3, new HashSet<>(Arrays.asList("sdk1", "sdk3", "sdk4")));

        // topic4 is a classification topic for app3, so all SDKs in apps3 can learn this topic.
        expectedCallersCanLearnMap.put(/* topic */ 4, new HashSet<>(Arrays.asList("sdk1", "sdk5")));

        // topic5 is a classification topic for app3 and app4, so any SDKs in apps3 or app4 can
        // learn this topic.
        // app4 called Topics API directly, so it can learn this topic.
        expectedCallersCanLearnMap.put(
                /* topic */ 5, new HashSet<>(Arrays.asList("sdk1", "sdk5", "app4")));

        // app4 called the Topics API directly so it can learn this topic.
        expectedCallersCanLearnMap.put(/* topic */ 6, new HashSet<>(Arrays.asList("app4")));
        // Only 1 epoch is recorded so it doesn't need to look back
        Map<Integer, Set<String>> callersCanLearnMapFromDB =
                topicsDao.retrieveCallerCanLearnTopicsMap(epochId, /* numberOfLookBackEpochs */ 1);
        assertThat(callersCanLearnMapFromDB).isEqualTo(expectedCallersCanLearnMap);

        // Verify TopTopicsContract
        List<Integer> topTopicsFromDB = topicsDao.retrieveTopTopics(epochId);
        assertThat(topTopicsFromDB).isEqualTo(topTopics);

        // Verify ReturnedTopicContract
        // Random sequence numbers used in this test: {1, 5, 6, 7, 8, 9}.
        // The order of selected topics by iterations: "random_topic", "topic1", "topic2", "topic3",
        // "topic 4, "topic5".
        // The order of app is inserted in appSdksUsageMap: app1, app2, app3, app4, app5.
        // So random_topic is selected for app1, topic1 is selected for app2,
        // topic2 is selected for app3, topic3 is selected for app4, topic4 is selected for app5.
        Map<Long, Map<Pair<String, String>, Topic>> expectedReturnedTopics = new HashMap<>();
        expectedReturnedTopics.put(epochId, new HashMap<>());
        Map<Pair<String, String>, Topic> expectedReturnedTopicsEpoch1 =
                expectedReturnedTopics.get(epochId);
        // Topic4 is selected for app5. Both sdk1 and sdk5 can learn about topic4.
        // (look at callersCanLearnMap)
        expectedReturnedTopicsEpoch1.put(Pair.create("app5", "sdk1"), topic4);
        expectedReturnedTopicsEpoch1.put(Pair.create("app5", "sdk5"), topic4);

        // Topic2 is selected for app3. However only sdk1 can learn about topic2.
        // sdk5 can't learn topic2.
        expectedReturnedTopicsEpoch1.put(Pair.create("app3", "sdk1"), topic2);

        // Topic1 is selected for app2. However only sdk1 can learn about topic1.
        // sdk3, and sdk4 can't learn topic1.
        expectedReturnedTopicsEpoch1.put(Pair.create("app2", "sdk1"), topic1);

        Map<Long, Map<Pair<String, String>, Topic>> returnedTopicsFromDB =
                topicsDao.retrieveReturnedTopics(epochId, /* numberOfLookBackEpochs */ 1);
        assertThat(returnedTopicsFromDB).isEqualTo(expectedReturnedTopics);
    }

    @Test
    public void testDump() throws FileNotFoundException {
        // Trigger the dump to verify no crash
        // Create a new epochManager that we can control the random generator.
        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        TopicsDao topicsDao = new TopicsDao(dbHelper);
        EpochManager epochManager =
                new EpochManager(topicsDao, dbHelper, new Random(), mMockClassifier, mFlags);

        PrintWriter printWriter = new PrintWriter(new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {

            }

            @Override
            public void flush() throws IOException {

            }

            @Override
            public void close() throws IOException {

            }
        });
        String[] args = new String[] {};
        epochManager.dump(printWriter, args);
    }

    @Test
    public void testComputeEpoch_emptyTopTopics() {
        // Create a new EpochManager that we can control the random generator.
        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        TopicsDao topicsDao = Mockito.spy(new TopicsDao(dbHelper));
        // Mock EpochManager for getCurrentEpochId()
        EpochManager epochManager =
                Mockito.spy(
                        new EpochManager(
                                topicsDao, dbHelper, new Random(), mMockClassifier, mFlags));

        // To mimic the scenario that there was no usage in last epoch.
        // i.e. current epoch id is 2, with some usages, while epoch id = 1 has no usage.
        final long epochId = 2L;
        when(epochManager.getCurrentEpochId()).thenReturn(epochId);

        // Note: we iterate over the appSdksUsageMap. For the test to be deterministic, we use
        // LinkedHashMap so that the order of iteration is defined.
        Map<String, List<String>> appSdksUsageMap = new LinkedHashMap<>();
        // app1 called Topics API directly. In addition, 2 of its sdk1 an sdk2 called the Topics
        // API.
        appSdksUsageMap.put("app1", Arrays.asList("", "sdk1", "sdk2"));

        // Mock TopicsDao to return above LinkedHashMap for retrieveAppSdksUsageMap()
        when(topicsDao.retrieveAppSdksUsageMap(epochId)).thenReturn(appSdksUsageMap);

        Map<String, List<Integer>> appClassificationTopicsMap = new HashMap<>();
        appClassificationTopicsMap.put("app1", Arrays.asList(1, 2));
        when(mMockClassifier.classify(eq(appSdksUsageMap.keySet())))
                .thenReturn(appClassificationTopicsMap);

        // Mock Classifier to return empty top topic list
        when(mMockClassifier.getTopTopics(
                        eq(appClassificationTopicsMap),
                        eq(mFlags.getTopicsNumberOfTopTopics()),
                        eq(mFlags.getTopicsNumberOfRandomTopics())))
                .thenReturn(Collections.emptyList());

        epochManager.processEpoch();

        verify(epochManager).getCurrentEpochId();
        verify(topicsDao).retrieveAppSdksUsageMap(eq(epochId));
        verify(mMockClassifier).classify(eq(appSdksUsageMap.keySet()));
        verify(mMockClassifier)
                .getTopTopics(
                        eq(appClassificationTopicsMap),
                        eq(mFlags.getTopicsNumberOfTopTopics()),
                        eq(mFlags.getTopicsNumberOfRandomTopics()));

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);

        // Verify AppClassificationTopics table is still persisted
        Map<String, List<Topic>> expectedAppClassificationTopicsMap = new HashMap<>();
        expectedAppClassificationTopicsMap.put("app1", Arrays.asList(topic1, topic2));
        Map<String, List<Topic>> appClassificationTopicsMapFromDB =
                topicsDao.retrieveAppClassificationTopics(epochId);
        assertThat(appClassificationTopicsMapFromDB).isEqualTo(expectedAppClassificationTopicsMap);

        // Verify CallerCanLearnTopics table is still persisted
        Map<Integer, Set<String>> expectedCallersCanLearnMap = new HashMap<>();
        expectedCallersCanLearnMap.put(
                /* topic */ 1, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2")));
        expectedCallersCanLearnMap.put(
                /* topic */ 2, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2")));
        Map<Integer, Set<String>> callersCanLearnMapFromDB =
                topicsDao.retrieveCallerCanLearnTopicsMap(epochId, /* numberOfLookBackEpochs */ 2);
        assertThat(callersCanLearnMapFromDB).isEqualTo(expectedCallersCanLearnMap);

        // Look back till epoch id = 1, which has no usage.
        // In current epoch id 2, top topics return an empty list, which aborts the
        // processing of epoch computation. So returned topics list is empty for epoch id = 2.
        // In last epoch id 1, there is no usage so returned topics list is also empty.
        // Therefore, to verify that no top topic has been persisted into database and return topic
        // list is empty for 2 epochs
        assertThat(topicsDao.retrieveTopTopics(epochId)).isEmpty();
        assertThat(topicsDao.retrieveReturnedTopics(epochId, /* numberOfLookBackEpochs */ 2))
                .isEmpty();
    }
}
