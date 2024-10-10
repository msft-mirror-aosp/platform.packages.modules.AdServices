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

import static com.android.adservices.service.topics.EpochManager.PADDED_TOP_TOPICS_STRING;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.util.Pair;

import com.android.adservices.MockRandom;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.topics.EncryptedTopic;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.TopicsEncryptionEpochComputationReportedStats;
import com.android.adservices.service.topics.classifier.Classifier;
import com.android.adservices.service.topics.classifier.ClassifierManager;
import com.android.adservices.shared.util.Clock;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/** Unit tests for {@link com.android.adservices.service.topics.EpochManager} */
public final class EpochManagerTest extends AdServicesExtendedMockitoTestCase {
    @SuppressWarnings({"unused"})
    private static final String TAG = "EpochManagerTest";

    private static final long TOPICS_EPOCH_JOB_PERIOD_MS = 7 * 86_400_000;
    // TODO: (b/232807776) Replace below hardcoded taxonomy version and model version
    private static final long TAXONOMY_VERSION = 1L;
    private static final long MODEL_VERSION = 1L;
    private static final long TEST_TOPICS_ENCRYPTION_START_TIMESTAMP = 100L;
    private static final long TEST_TOPICS_ENCRYPTION_END_TIMESTAMP = 150L;
    private static final long TEST_PERSIST_ENCRYPTED_TOPICS_TO_DB_START_TIMESTAMP = 200L;
    private static final long TEST_PERSIST_ENCRYPTED_TOPICS_TO_DB_END_TIMESTAMP = 210L;
    private static final int TEST_TOPICS_ENCRYPTION_LATENCY =
            (int) (TEST_TOPICS_ENCRYPTION_END_TIMESTAMP - TEST_TOPICS_ENCRYPTION_START_TIMESTAMP);
    private static final int TEST_PERSIST_ENCRYPTED_TOPICS_TO_DB_LATENCY =
            (int) (TEST_PERSIST_ENCRYPTED_TOPICS_TO_DB_END_TIMESTAMP
                    - TEST_PERSIST_ENCRYPTED_TOPICS_TO_DB_START_TIMESTAMP);

    private final Flags mFakeFlags = FakeFlagsFactory.getFlagsForTest();

    private DbHelper mDbHelper;
    private TopicsDao mTopicsDao;
    private EpochManager mEpochManager;

    @Mock private Classifier mMockClassifier;
    @Mock private Clock mMockClock;
    @Mock private ClassifierManager mClassifierManager;
    @Mock private EncryptionManager mEncryptionManager;
    @Mock private Random mRandom;
    @Mock private AdServicesLogger mAdServicesLogger;

    @Before
    public void setup() {
        mDbHelper = DbTestUtil.getDbHelperForTest();
        mTopicsDao = new TopicsDao(mDbHelper);
        mEpochManager =
                new EpochManager(
                        mTopicsDao,
                        mDbHelper,
                        new Random(),
                        mMockClassifier,
                        mFakeFlags,
                        mMockClock,
                        mClassifierManager,
                        mEncryptionManager,
                        mAdServicesLogger);

        // Erase all existing data.
        DbTestUtil.deleteTable(TopicsTables.TaxonomyContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppClassificationTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.CallerCanLearnTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.ReturnedTopicContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.UsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppUsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.EpochOriginContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopicContributorsContract.TABLE);
    }

    @Test
    public void testComputeCallersCanLearnMap() {
        Map<String, List<String>> appSdksUsageMap = new HashMap<>();

        // app1 called Topics API directly. In addition, 2 of its sdks, sdk1 and sdk2 called the
        // Topics API.
        appSdksUsageMap.put("app1", Arrays.asList("", "sdk1", "sdk2"));

        appSdksUsageMap.put("app2", Arrays.asList("sdk1", "sdk3", "sdk4"));
        appSdksUsageMap.put("app3", Arrays.asList("sdk1", "sdk5"));

        // app4 has no SDKs, it called Topics API directly.
        appSdksUsageMap.put("app4", Collections.singletonList(""));

        appSdksUsageMap.put("app5", Arrays.asList("sdk1", "sdk5"));

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);

        Map<String, List<Topic>> appClassificationTopicsMap = new HashMap<>();
        appClassificationTopicsMap.put("app1", Arrays.asList(topic1, topic2));
        appClassificationTopicsMap.put("app2", Arrays.asList(topic2, topic3));
        appClassificationTopicsMap.put("app3", Arrays.asList(topic4, topic5));
        appClassificationTopicsMap.put("app4", Arrays.asList(topic5, topic6));

        // app5 has no classification topics.
        appClassificationTopicsMap.put("app5", Collections.emptyList());

        Map<Topic, Set<String>> expectedCallerCanLearnMap = new HashMap<>();
        // topic1 is a classification topic for app1, so all SDKs in apps1 can learn this topic.
        // In addition, the app1 called the Topics API directly, so it can learn topic1 as well.
        expectedCallerCanLearnMap.put(topic1, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2")));

        // topic2 is a classification topic for app1 and app2, so any SDKs in app1 or app2 can learn
        // this topic.
        expectedCallerCanLearnMap.put(
                topic2, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2", "sdk3", "sdk4")));

        // topic3 is a classification topic for app2, so all SDKs in apps2 can learn this topic.
        expectedCallerCanLearnMap.put(topic3, new HashSet<>(Arrays.asList("sdk1", "sdk3", "sdk4")));

        // topic4 is a classification topic for app3, so all SDKs in apps3 can learn this topic.
        expectedCallerCanLearnMap.put(topic4, new HashSet<>(Arrays.asList("sdk1", "sdk5")));

        // topic5 is a classification topic for app3 and app4, so any SDKs in apps3 or app4 can
        // learn this topic.
        // app4 called Topics API directly, so it can learn this topic.
        expectedCallerCanLearnMap.put(topic5, new HashSet<>(Arrays.asList("sdk1", "sdk5", "app4")));

        // app4 called the Topics API directly, so it can learn this topic.
        expectedCallerCanLearnMap.put(topic6, new HashSet<>(Collections.singletonList("app4")));

        Map<Topic, Set<String>> canLearnMap =
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
        EpochManager epochManager =
                new EpochManager(
                        mTopicsDao,
                        mDbHelper,
                        new MockRandom(new long[] {1, 5, 6, 7, 8, 9}),
                        mMockClassifier,
                        mFakeFlags,
                        mMockClock,
                        mClassifierManager,
                        mEncryptionManager,
                        mAdServicesLogger);

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);

        List<Topic> topTopics = Arrays.asList(topic1, topic2, topic3, topic4, topic5, topic6);

        // random = 1
        assertThat(epochManager.selectRandomTopic(topTopics)).isEqualTo(topic6);

        // random = 5
        assertThat(epochManager.selectRandomTopic(topTopics)).isEqualTo(topic1);

        // random = 6
        assertThat(epochManager.selectRandomTopic(topTopics)).isEqualTo(topic2);

        // random = 7
        assertThat(epochManager.selectRandomTopic(topTopics)).isEqualTo(topic3);

        // random = 8
        assertThat(epochManager.selectRandomTopic(topTopics)).isEqualTo(topic4);

        // random = 9
        assertThat(epochManager.selectRandomTopic(topTopics)).isEqualTo(topic5);
    }

    @Test
    public void testSelectRandomTopic_invalidSize_throw() {
        // Create a new epochManager that we can control the random generator.
        EpochManager epochManager =
                new EpochManager(
                        mTopicsDao,
                        mDbHelper,
                        new MockRandom(new long[] {1, 5, 6, 7, 8, 9}),
                        mMockClassifier,
                        mFakeFlags,
                        mMockClock,
                        mClassifierManager,
                        mEncryptionManager,
                        mAdServicesLogger);

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);

        assertThrows(
                IllegalArgumentException.class,
                () -> epochManager.selectRandomTopic(Arrays.asList(topic1, topic2, topic3)));

        assertThrows(
                NullPointerException.class,
                () -> epochManager.selectRandomTopic(/* topTopics = */ null));
    }

    @Test
    public void testComputeReturnedAppTopics() {
        // Create a new epochManager that we can control the random generator.
        EpochManager epochManager =
                new EpochManager(
                        mTopicsDao,
                        mDbHelper,
                        new MockRandom(
                                new long[] {1, 5, 6, 7, 8, 9},
                                // Use a positive double greater than 1 to ensure that loggedTopic
                                // must be the same as the topic.
                                new double[] {2d}),
                        mMockClassifier,
                        mFakeFlags,
                        mMockClock,
                        mClassifierManager,
                        mEncryptionManager,
                        mAdServicesLogger);

        // Note: we iterate over the appSdksUsageMap. For the test to be deterministic, we use
        // LinkedHashMap so that the order of iteration is defined.
        // From Java doc:  https://docs.oracle.com/javase/6/docs/api/java/util/LinkedHashMap.html
        // "This linked list defines the iteration ordering, which is normally the order in which
        // keys were inserted into the map (insertion-order)."
        Map<String, List<String>> appSdksUsageMap = new LinkedHashMap<>();
        // app1 called Topics API directly. In addition, 2 of its sdks, sdk1 and sdk2 called the
        // Topics API.
        appSdksUsageMap.put("app1", Arrays.asList("", "sdk1", "sdk2"));

        appSdksUsageMap.put("app2", Arrays.asList("sdk1", "sdk3", "sdk4"));
        appSdksUsageMap.put("app3", Arrays.asList("sdk1", "sdk5"));

        // app4 has no SDKs, it called Topics API directly.
        appSdksUsageMap.put("app4", Collections.singletonList(""));

        appSdksUsageMap.put("app5", Arrays.asList("sdk1", "sdk5"));

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);
        List<Topic> topTopics = Arrays.asList(topic1, topic2, topic3, topic4, topic5, topic6);

        Map<Topic, Set<String>> callersCanLearnMap = new HashMap<>();
        // topic1 is a classification topic for app1, so all SDKs in apps1 can learn this topic.
        // In addition, the app1 called the Topics API directly, so it can learn topic1 as well.
        callersCanLearnMap.put(topic1, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2")));

        // topic2 is a classification topic for app1 and app2, so any SDKs in app1 or app2 can learn
        // this topic.
        callersCanLearnMap.put(
                topic2, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2", "sdk3", "sdk4")));

        // topic3 is a classification topic for app2, so all SDKs in apps2 can learn this topic.
        callersCanLearnMap.put(topic3, new HashSet<>(Arrays.asList("sdk1", "sdk3", "sdk4")));

        // topic4 is a classification topic for app3, so all SDKs in apps3 can learn this topic.
        callersCanLearnMap.put(topic4, new HashSet<>(Arrays.asList("sdk1", "sdk5")));

        // topic5 is a classification topic for app3 and app4, so any SDKs in apps3 or app4 can
        // learn this topic.
        // app4 called Topics API directly, so it can learn this topic.
        callersCanLearnMap.put(topic5, new HashSet<>(Arrays.asList("sdk1", "sdk5", "app4")));

        // app4 called the Topics API directly, so it can learn this topic.
        callersCanLearnMap.put(topic6, new HashSet<>(Collections.singletonList("app4")));

        // Random sequence numbers used in this test: {1, 5, 6, 7, 8, 9}.
        // The order of selected topics by iterations: "random_topic", "topic1", "topic2", "topic3",
        // "topic 4, "topic5".
        // The order of app is inserted in appSdksUsageMap: app1, app2, app3, app4, app5.
        // So random_topic is selected for app1, topic1 is selected for app2,
        // topic2 is selected for app3, topic3 is selected for app4, topic4 is selected for app5.
        Map<Pair<String, String>, Topic> returnedAppSdkTopics =
                epochManager.computeReturnedAppSdkTopics(
                        callersCanLearnMap, appSdksUsageMap, topTopics);

        Map<Pair<String, String>, Topic> expectedReturnedTopics = new HashMap<>();
        // Topic 6, which is the random topic, should be able to be learnt by any caller.
        // Therefore, app1 and all sdks it uses should have topic 6 as a return topic.
        expectedReturnedTopics.put(Pair.create("app1", ""), topic6);
        expectedReturnedTopics.put(Pair.create("app1", "sdk1"), topic6);
        expectedReturnedTopics.put(Pair.create("app1", "sdk2"), topic6);

        // Topic4 is selected for app5. Both sdk1 and sdk5 can learn about topic4.
        // (look at callersCanLearnMap)
        expectedReturnedTopics.put(Pair.create("app5", "sdk1"), topic4);
        expectedReturnedTopics.put(Pair.create("app5", "sdk5"), topic4);

        // Topic2 is selected for app3. However, only sdk1 can learn about topic2.
        // sdk5 can't learn topic2.
        expectedReturnedTopics.put(Pair.create("app3", "sdk1"), topic2);

        // Topic1 is selected for app2. However, only sdk1 can learn about topic1.
        // sdk3, and sdk4 can't learn topic1.
        expectedReturnedTopics.put(Pair.create("app2", "sdk1"), topic1);

        assertThat(returnedAppSdkTopics).isEqualTo(expectedReturnedTopics);
    }

    @Test
    public void testRecordUsage() {
        // Record some usages.
        // App1 called the Topics API directly and its SDKs also call Topics API.
        // Empty SDK implies the app calls the Topics API directly.
        mEpochManager.recordUsageHistory("app1", /* sdk = */ "");
        mEpochManager.recordUsageHistory("app1", "sdk1");
        mEpochManager.recordUsageHistory("app1", "sdk2");

        // App2 only did not call Topics API directly. Only SDKs of the app2 called the Topics API.
        mEpochManager.recordUsageHistory("app2", "sdk1");
        mEpochManager.recordUsageHistory("app2", "sdk3");

        // App3 called the Topics API directly and has not other SDKs.
        mEpochManager.recordUsageHistory("app3", /* sdk = */ "");

        Map<String, List<String>> expectedAppSdksUsageMap = new HashMap<>();
        expectedAppSdksUsageMap.put("app1", Arrays.asList("", "sdk1", "sdk2"));
        expectedAppSdksUsageMap.put("app2", Arrays.asList("sdk1", "sdk3"));
        expectedAppSdksUsageMap.put("app3", Collections.singletonList(""));

        // Now read back the usages from DB.
        Map<String, List<String>> appSdksUsageMapFromDb =
                mTopicsDao.retrieveAppSdksUsageMap(mEpochManager.getCurrentEpochId());

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(appSdksUsageMapFromDb).isEqualTo(expectedAppSdksUsageMap);
    }

    @Test
    public void testGarbageCollectOutdatedEpochData() {
        long currentEpoch = 7L;
        int epochLookBackNumberForGarbageCollection = 3;
        String appName = "app";

        // Mock the flag to make test result deterministic
        when(mMockFlags.getNumberOfEpochsToKeepInHistory())
                .thenReturn(epochLookBackNumberForGarbageCollection);

        EpochManager epochManager =
                new EpochManager(
                        mTopicsDao,
                        mDbHelper,
                        new Random(),
                        mMockClassifier,
                        mMockFlags,
                        mMockClock,
                        mClassifierManager,
                        mEncryptionManager,
                        mAdServicesLogger);

        // For table except CallerCanLearnTopicsContract, epoch to delete from is 7-3-1 = epoch 3
        long epochToDeleteFrom = currentEpoch - epochLookBackNumberForGarbageCollection - 1;
        long epochToDeleteFromForCallerCanLearn =
                currentEpoch - epochLookBackNumberForGarbageCollection * 2 - 1;

        // Save data in TopTopics Table and AppUsage table for gc testing
        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);
        List<Topic> topTopics = Arrays.asList(topic1, topic2, topic3, topic4, topic5, topic6);
        Map<Topic, Set<String>> callerCanLearnTopics = Map.of(topic1, Set.of(appName));

        // TopicsContributorTable
        Map<Integer, Set<String>> topicContributorsMap = Map.of(topic1.getTopic(), Set.of(appName));

        // To persist data in epoch [0, 7] for tables.
        for (long epoch = 0L; epoch <= currentEpoch; epoch++) {
            mTopicsDao.persistTopTopics(epoch, topTopics);
            mTopicsDao.recordAppUsageHistory(epoch, appName);
            mTopicsDao.persistCallerCanLearnTopics(epoch, callerCanLearnTopics);
            mTopicsDao.persistTopicContributors(epoch, topicContributorsMap);
        }

        epochManager.garbageCollectOutdatedEpochData(currentEpoch);

        verify(mMockFlags).getNumberOfEpochsToKeepInHistory();

        // Verify TopTopics, AppUsageHistory, TopicsContributor Tables.
        for (long epoch = currentEpoch; epoch > epochToDeleteFrom; epoch--) {
            assertThat(mTopicsDao.retrieveTopTopics(epoch)).isEqualTo(topTopics);

            // App has called Topics API once in each epoch
            Map<String, Integer> appUsageMap = mTopicsDao.retrieveAppUsageMap(epoch);
            Map<String, Integer> expectedAppUsageMap = new HashMap<>();
            expectedAppUsageMap.put(appName, 1);
            assertThat(appUsageMap).isEqualTo(expectedAppUsageMap);

            assertThat(mTopicsDao.retrieveTopicToContributorsMap(epoch))
                    .isEqualTo(topicContributorsMap);
        }

        // Epoch [0, epochToDeleteFrom] have been garbage collected.
        for (long epoch = epochToDeleteFrom; epoch >= 0; epoch--) {
            assertThat(mTopicsDao.retrieveTopTopics(epoch)).isEmpty();
            assertThat(mTopicsDao.retrieveAppUsageMap(epoch)).isEmpty();
        }

        // Verify CallerCanLearn Table.
        for (long epoch = currentEpoch; epoch > epochToDeleteFromForCallerCanLearn; epoch--) {
            assertThat(
                            mTopicsDao.retrieveCallerCanLearnTopicsMap(
                                    epoch, /* numberOfLookBackEpochs */ 1))
                    .isEqualTo(callerCanLearnTopics);
        }

        // Epoch [0, epochToDeleteFromForCallerCanLearn] have been garbage collected.
        for (long epoch = epochToDeleteFromForCallerCanLearn; epoch >= 0; epoch--) {
            assertThat(
                            mTopicsDao.retrieveCallerCanLearnTopicsMap(
                                    epoch, /* numberOfLookBackEpochs */ 1))
                    .isEmpty();
        }
    }

    @Test
    public void testGarbageCollectOutdatedEpochData_encryptedTopicsTable() {
        long currentEpochId = 7L;
        int epochLookBackNumberForGarbageCollection = 3;

        // Mock the flag to make test result deterministic
        when(mMockFlags.getNumberOfEpochsToKeepInHistory())
                .thenReturn(epochLookBackNumberForGarbageCollection);

        EpochManager epochManager =
                new EpochManager(
                        mTopicsDao,
                        mDbHelper,
                        new Random(),
                        mMockClassifier,
                        mMockFlags,
                        mMockClock,
                        mClassifierManager,
                        mEncryptionManager,
                        mAdServicesLogger);

        String app = "app";
        String sdk = "sdk";
        long epochId = 1L;
        int topicId = 1;
        int numberOfLookBackEpochs = 1;

        Topic topic1 = Topic.create(topicId, TAXONOMY_VERSION, MODEL_VERSION);
        EncryptedTopic encryptedTopic1 =
                EncryptedTopic.create(
                        topic1.toString().getBytes(StandardCharsets.UTF_8),
                        "publicKey",
                        "encapsulatedKey".getBytes(StandardCharsets.UTF_8));

        // Handle ReturnedTopicContract
        Map<Pair<String, String>, Topic> returnedAppSdkTopics = new HashMap<>();
        returnedAppSdkTopics.put(Pair.create(app, sdk), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(epochId, returnedAppSdkTopics);
        // Handle ReturnedEncryptedTopicContract
        Map<Pair<String, String>, EncryptedTopic> encryptedTopics =
                Map.of(Pair.create(app, sdk), encryptedTopic1);

        mTopicsDao.persistReturnedAppEncryptedTopicsMap(epochId, encryptedTopics);

        // When db flag is off for version 9.
        when(mMockFlags.getEnableDatabaseSchemaVersion9()).thenReturn(false);
        epochManager.garbageCollectOutdatedEpochData(7);
        // Unencrypted table is cleared.
        assertThat(mTopicsDao.retrieveReturnedTopics(epochId, numberOfLookBackEpochs)).isEmpty();
        // Encrypted table is not cleared.
        assertThat(
                        mTopicsDao
                                .retrieveReturnedEncryptedTopics(epochId, numberOfLookBackEpochs)
                                .get(epochId))
                .isEqualTo(encryptedTopics);

        // When db flag is on for version 9.
        when(mMockFlags.getEnableDatabaseSchemaVersion9()).thenReturn(true);
        epochManager.garbageCollectOutdatedEpochData(currentEpochId);
        // Unencrypted table is cleared.
        assertThat(mTopicsDao.retrieveReturnedTopics(epochId, numberOfLookBackEpochs)).isEmpty();
        // Encrypted table is cleared.
        assertThat(mTopicsDao.retrieveReturnedEncryptedTopics(epochId, numberOfLookBackEpochs))
                .isEmpty();
    }

    @Test
    public void testProcessEpoch() {
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
        TopicsDao topicsDao = Mockito.spy(new TopicsDao(mDbHelper));
        EpochManager epochManager =
                Mockito.spy(
                        new EpochManager(
                                topicsDao,
                                mDbHelper,
                                new MockRandom(
                                        new long[] {1, 5, 6, 7, 8, 9},
                                        // Use a positive double greater than 1 to ensure that
                                        // loggedTopic must be the same as the topic.
                                        new double[] {2d}),
                                mMockClassifier,
                                mFakeFlags,
                                mMockClock,
                                mClassifierManager,
                                mEncryptionManager,
                                mAdServicesLogger));
        // Mock EpochManager for getCurrentEpochId()
        long epochId = 1L;
        doReturn(epochId).when(epochManager).getCurrentEpochId();

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);

        // Note: we iterate over the appSdksUsageMap. For the test to be deterministic, we use
        // LinkedHashMap so that the order of iteration is defined.
        // From Java doc:  https://docs.oracle.com/javase/6/docs/api/java/util/LinkedHashMap.html
        // "This linked list defines the iteration ordering, which is normally the order in which
        // keys were inserted into the map (insertion-order)."
        Map<String, List<String>> appSdksUsageMap = new LinkedHashMap<>();
        // app1 called Topics API directly. In addition, 2 of its sdks, sdk1 and sdk2 called the
        // Topics API.
        appSdksUsageMap.put("app1", Arrays.asList("", "sdk1", "sdk2"));

        appSdksUsageMap.put("app2", Arrays.asList("sdk1", "sdk3", "sdk4"));
        appSdksUsageMap.put("app3", Arrays.asList("sdk1", "sdk5"));

        // app4 has no SDKs, it called Topics API directly.
        appSdksUsageMap.put("app4", Collections.singletonList(""));

        appSdksUsageMap.put("app5", Arrays.asList("sdk1", "sdk5"));
        // Mock TopicsDao to return above LinkedHashMap for retrieveAppSdksUsageMap()
        when(topicsDao.retrieveAppSdksUsageMap(epochId)).thenReturn(appSdksUsageMap);

        Map<String, List<Topic>> appClassificationTopicsMap = new HashMap<>();
        appClassificationTopicsMap.put("app1", createTopics(Arrays.asList(1, 2)));
        appClassificationTopicsMap.put("app2", createTopics(Arrays.asList(2, 3)));
        appClassificationTopicsMap.put("app3", createTopics(Arrays.asList(4, 5)));
        appClassificationTopicsMap.put("app4", createTopics(Arrays.asList(5, 6)));
        when(mMockClassifier.classify(eq(appSdksUsageMap.keySet())))
                .thenReturn(appClassificationTopicsMap);

        List<Topic> topTopics = createTopics(Arrays.asList(1, 2, 3, 4, 5, /* random_topic */ 6));
        when(mMockClassifier.getTopTopics(
                        eq(appClassificationTopicsMap),
                        eq(mFakeFlags.getTopicsNumberOfTopTopics()),
                        eq(mFakeFlags.getTopicsNumberOfRandomTopics())))
                .thenReturn(topTopics);

        epochManager.processEpoch();

        verify(epochManager).getCurrentEpochId();
        verify(topicsDao).retrieveAppSdksUsageMap(epochId);
        verify(mMockClassifier).classify(appSdksUsageMap.keySet());
        verify(mMockClassifier)
                .getTopTopics(
                        appClassificationTopicsMap,
                        mFakeFlags.getTopicsNumberOfTopTopics(),
                        mFakeFlags.getTopicsNumberOfRandomTopics());

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
        Map<Topic, Set<String>> expectedCallersCanLearnMap = new HashMap<>();
        // topic1 is a classification topic for app1, so all SDKs in apps1 can learn this topic.
        // In addition, the app1 called the Topics API directly, so it can learn topic1 as well.
        expectedCallersCanLearnMap.put(
                topic1, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2")));

        // topic2 is a classification topic for app1 and app2, so any SDKs in app1 or app2 can learn
        // this topic.
        expectedCallersCanLearnMap.put(
                topic2, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2", "sdk3", "sdk4")));

        // topic3 is a classification topic for app2, so all SDKs in apps2 can learn this topic.
        expectedCallersCanLearnMap.put(
                topic3, new HashSet<>(Arrays.asList("sdk1", "sdk3", "sdk4")));

        // topic4 is a classification topic for app3, so all SDKs in apps3 can learn this topic.
        expectedCallersCanLearnMap.put(topic4, new HashSet<>(Arrays.asList("sdk1", "sdk5")));

        // topic5 is a classification topic for app3 and app4, so any SDKs in apps3 or app4 can
        // learn this topic.
        // app4 called Topics API directly, so it can learn this topic.
        expectedCallersCanLearnMap.put(
                topic5, new HashSet<>(Arrays.asList("sdk1", "sdk5", "app4")));

        // app4 called the Topics API directly, so it can learn this topic.
        expectedCallersCanLearnMap.put(topic6, new HashSet<>(Collections.singletonList("app4")));
        // Only 1 epoch is recorded, so it doesn't need to look back
        Map<Topic, Set<String>> callersCanLearnMapFromDB =
                topicsDao.retrieveCallerCanLearnTopicsMap(epochId, /* numberOfLookBackEpochs */ 1);
        assertThat(callersCanLearnMapFromDB).isEqualTo(expectedCallersCanLearnMap);

        // Verify TopTopicsContract
        List<Topic> topTopicsFromDB = topicsDao.retrieveTopTopics(epochId);
        assertThat(topTopicsFromDB).isEqualTo(topTopics);

        // Verify TopicContributorsContract
        // AppClassificationTopics has:
        // app1 -> topic1, topic2, app2 -> topic2, topic3,
        // app3 -> topic4, topic5, app4 -> topic5, topic6
        // All app1 ~ app4 have usages and all topic1 ~ topic6 are top topics
        // So the reverse mapping of AppClassificationTopics, which is topTopicsToContributorsMap,
        // should be:
        // topic1 -> app1, topic2 -> app1, app2, topic3 -> app2
        // topic4 -> app3, topic5 -> app3, app4, topic6 -> app4
        Map<Integer, Set<String>> expectedTopTopicsToContributorsMap =
                Map.of(
                        topic1.getTopic(), Set.of("app1"),
                        topic2.getTopic(), Set.of("app1", "app2"),
                        topic3.getTopic(), Set.of("app2"),
                        topic4.getTopic(), Set.of("app3"),
                        topic5.getTopic(), Set.of("app3", "app4"),
                        topic6.getTopic(), Set.of("app4"));
        assertThat(topicsDao.retrieveTopicToContributorsMap(epochId))
                .isEqualTo(expectedTopTopicsToContributorsMap);

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
        // Topic 6, which is the random topic, should be able to be learnt by any caller.
        // Therefore, app1 and all sdks it uses should have topic 6 as a return topic.
        expectedReturnedTopicsEpoch1.put(Pair.create("app1", ""), topic6);
        expectedReturnedTopicsEpoch1.put(Pair.create("app1", "sdk1"), topic6);
        expectedReturnedTopicsEpoch1.put(Pair.create("app1", "sdk2"), topic6);

        // Topic4 is selected for app5. Both sdk1 and sdk5 can learn about topic4.
        // (look at callersCanLearnMap)
        expectedReturnedTopicsEpoch1.put(Pair.create("app5", "sdk1"), topic4);
        expectedReturnedTopicsEpoch1.put(Pair.create("app5", "sdk5"), topic4);

        // Topic2 is selected for app3. However, only sdk1 can learn about topic2.
        // sdk5 can't learn topic2.
        expectedReturnedTopicsEpoch1.put(Pair.create("app3", "sdk1"), topic2);

        // Topic1 is selected for app2. However, only sdk1 can learn about topic1.
        // sdk3, and sdk4 can't learn topic1.
        expectedReturnedTopicsEpoch1.put(Pair.create("app2", "sdk1"), topic1);

        Map<Long, Map<Pair<String, String>, Topic>> returnedTopicsFromDB =
                topicsDao.retrieveReturnedTopics(epochId, /* numberOfLookBackEpochs */ 1);
        assertThat(returnedTopicsFromDB).isEqualTo(expectedReturnedTopics);
    }

    @Test
    public void testProcessEpoch_enableEncryptedTopics_encryptionSucceed() {
        // Initializes the argumentCaptor to collect topics encryption metrics.
        ArgumentCaptor<TopicsEncryptionEpochComputationReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(TopicsEncryptionEpochComputationReportedStats.class);
        // Sets up the timestamps for topics encryption metrics logging.
        when(mMockClock.currentTimeMillis()).thenReturn(
                TEST_TOPICS_ENCRYPTION_START_TIMESTAMP,
                TEST_TOPICS_ENCRYPTION_END_TIMESTAMP,
                TEST_PERSIST_ENCRYPTED_TOPICS_TO_DB_START_TIMESTAMP,
                TEST_PERSIST_ENCRYPTED_TOPICS_TO_DB_END_TIMESTAMP);
        // Simplify the setup of epoch computation, to only test the effect of feature flag
        // Mock the flag to make test result deterministic
        EpochManager epochManager =
                Mockito.spy(
                        new EpochManager(
                                mTopicsDao,
                                mDbHelper,
                                new Random(),
                                mMockClassifier,
                                mMockFlags,
                                mMockClock,
                                mClassifierManager,
                                mEncryptionManager,
                                mAdServicesLogger));

        String sdk = "sdk";
        long epochId = 1L;
        int numberOfLookBackEpochs = 1;

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);
        Map<String, List<Topic>> appClassificationTopicsMap = new HashMap<>();
        appClassificationTopicsMap.put("app1", createTopics(Arrays.asList(1, 2, 3, 4, 5, 6)));
        appClassificationTopicsMap.put("app2", createTopics(Arrays.asList(1, 2, 3, 4, 5, 6)));
        appClassificationTopicsMap.put("app3", createTopics(Arrays.asList(1, 2, 3, 4, 5, 6)));
        appClassificationTopicsMap.put("app4", createTopics(Arrays.asList(1, 2, 3, 4, 5, 6)));

        List<Topic> topTopics = List.of(topic1, topic2, topic3, topic4, topic5, topic6);

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);
        when(mMockFlags.getTopicsNumberOfTopTopics())
                .thenReturn(mFakeFlags.getTopicsNumberOfTopTopics());
        when(mMockFlags.getTopicsNumberOfRandomTopics())
                .thenReturn(mFakeFlags.getTopicsNumberOfRandomTopics());
        doReturn(epochId).when(epochManager).getCurrentEpochId();

        mTopicsDao.recordUsageHistory(epochId, "app1", sdk);
        mTopicsDao.recordUsageHistory(epochId, "app2", sdk);
        mTopicsDao.recordUsageHistory(epochId, "app3", sdk);
        mTopicsDao.recordUsageHistory(epochId, "app4", sdk);
        when(mMockClassifier.classify(any())).thenReturn(appClassificationTopicsMap);
        when(mMockClassifier.getTopTopics(
                        appClassificationTopicsMap,
                        mFakeFlags.getTopicsNumberOfTopTopics(),
                        mFakeFlags.getTopicsNumberOfRandomTopics()))
                .thenReturn(topTopics);

        // Mock for encryption data.
        EncryptedTopic expectedEncryptedTopic =
                EncryptedTopic.create(new byte[] {}, "", new byte[] {});
        when(mEncryptionManager.encryptTopic(any(), any()))
                .thenReturn(Optional.of(expectedEncryptedTopic));

        // Mock encryption db, feature and metrics flag.
        when(mMockFlags.getTopicsEncryptionEnabled()).thenReturn(true);
        when(mMockFlags.getEnableDatabaseSchemaVersion9()).thenReturn(true);
        when(mMockFlags.getTopicsEncryptionMetricsEnabled()).thenReturn(true);

        epochManager.processEpoch();

        // ReturnedEncryptedTopics table should not be empty when feature is enabled.
        assertThat(
                        mTopicsDao
                                .retrieveReturnedEncryptedTopics(epochId, numberOfLookBackEpochs)
                                .get(epochId))
                .isNotEmpty();
        assertThat(
                        mTopicsDao
                                .retrieveReturnedEncryptedTopics(epochId, numberOfLookBackEpochs)
                                .get(epochId))
                .containsEntry(Pair.create("app1", sdk), expectedEncryptedTopic);
        assertThat(
                mTopicsDao
                        .retrieveReturnedEncryptedTopics(epochId, numberOfLookBackEpochs)
                        .get(epochId))
                .containsEntry(Pair.create("app2", sdk), expectedEncryptedTopic);

        // Verifies the topics encryption metrics are logged correctly.
        verify(mAdServicesLogger)
                .logTopicsEncryptionEpochComputationReportedStats(argumentCaptor.capture());
        TopicsEncryptionEpochComputationReportedStats stats = argumentCaptor.getValue();
        assertThat(stats.getCountOfTopicsBeforeEncryption()).isEqualTo(4);
        assertThat(stats.getCountOfEmptyEncryptedTopics()).isEqualTo(0);
        assertThat(stats.getCountOfEncryptedTopics()).isEqualTo(4);
        assertThat(stats.getLatencyOfWholeEncryptionProcessMs())
                .isEqualTo(TEST_TOPICS_ENCRYPTION_LATENCY);
        assertThat(stats.getLatencyOfEncryptionPerTopicMs())
                .isEqualTo(TEST_TOPICS_ENCRYPTION_LATENCY/4);
        assertThat(stats.getLatencyOfPersistingEncryptedTopicsToDbMs())
                .isEqualTo(TEST_PERSIST_ENCRYPTED_TOPICS_TO_DB_LATENCY);
    }

    @Test
    public void testProcessEpoch_enableEncryptedTopics_encryptionFailed() {
        // Do nothing for ErrorLogUtil calls.
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
        // Initializes the argumentCaptor to collect topics encryption metrics.
        ArgumentCaptor<TopicsEncryptionEpochComputationReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(TopicsEncryptionEpochComputationReportedStats.class);
        // Sets up the timestamps for topics encryption metrics logging.
        when(mMockClock.currentTimeMillis()).thenReturn(
                TEST_TOPICS_ENCRYPTION_START_TIMESTAMP,
                TEST_TOPICS_ENCRYPTION_END_TIMESTAMP,
                TEST_PERSIST_ENCRYPTED_TOPICS_TO_DB_START_TIMESTAMP,
                TEST_PERSIST_ENCRYPTED_TOPICS_TO_DB_END_TIMESTAMP);
        // Simplify the setup of epoch computation, to only test the effect of feature flag
        // Mock the flag to make test result deterministic
        EpochManager epochManager =
                Mockito.spy(
                        new EpochManager(
                                mTopicsDao,
                                mDbHelper,
                                new Random(),
                                mMockClassifier,
                                mMockFlags,
                                mMockClock,
                                mClassifierManager,
                                mEncryptionManager,
                                mAdServicesLogger));

        String sdk = "sdk";
        long epochId = 1L;
        int numberOfLookBackEpochs = 1;

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);
        Map<String, List<Topic>> appClassificationTopicsMap = new HashMap<>();
        appClassificationTopicsMap.put("app1", createTopics(Arrays.asList(1, 2, 3, 4, 5, 6)));
        appClassificationTopicsMap.put("app2", createTopics(Arrays.asList(1, 2, 3, 4, 5, 6)));
        appClassificationTopicsMap.put("app3", createTopics(Arrays.asList(1, 2, 3, 4, 5, 6)));

        List<Topic> topTopics = List.of(topic1, topic2, topic3, topic4, topic5, topic6);

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);
        when(mMockFlags.getTopicsNumberOfTopTopics())
                .thenReturn(mFakeFlags.getTopicsNumberOfTopTopics());
        when(mMockFlags.getTopicsNumberOfRandomTopics())
                .thenReturn(mFakeFlags.getTopicsNumberOfRandomTopics());
        doReturn(epochId).when(epochManager).getCurrentEpochId();

        mTopicsDao.recordUsageHistory(epochId, "app1", sdk);
        mTopicsDao.recordUsageHistory(epochId, "app2", sdk);
        mTopicsDao.recordUsageHistory(epochId, "app3", sdk);
        when(mMockClassifier.classify(any())).thenReturn(appClassificationTopicsMap);
        when(mMockClassifier.getTopTopics(
                        appClassificationTopicsMap,
                        mFakeFlags.getTopicsNumberOfTopTopics(),
                        mFakeFlags.getTopicsNumberOfRandomTopics()))
                .thenReturn(topTopics);

        // Mock for encryption data.
        when(mEncryptionManager.encryptTopic(any(), any()))
                .thenReturn(Optional.empty());

        // Mock encryption db, feature and metrics flag.
        when(mMockFlags.getTopicsEncryptionEnabled()).thenReturn(true);
        when(mMockFlags.getEnableDatabaseSchemaVersion9()).thenReturn(true);
        when(mMockFlags.getTopicsEncryptionMetricsEnabled()).thenReturn(true);

        epochManager.processEpoch();

        // ReturnedEncryptedTopics table should be empty when encryption failed.
        assertThat(
                mTopicsDao
                        .retrieveReturnedEncryptedTopics(epochId, numberOfLookBackEpochs)
                        .get(epochId))
                .isNull();

        // Verifies the topics encryption metrics are logged correctly.
        verify(mAdServicesLogger)
                .logTopicsEncryptionEpochComputationReportedStats(argumentCaptor.capture());
        TopicsEncryptionEpochComputationReportedStats stats = argumentCaptor.getValue();
        assertThat(stats.getCountOfTopicsBeforeEncryption()).isEqualTo(3);
        assertThat(stats.getCountOfEmptyEncryptedTopics()).isEqualTo(3);
        assertThat(stats.getCountOfEncryptedTopics()).isEqualTo(0);
        assertThat(stats.getLatencyOfWholeEncryptionProcessMs())
                .isEqualTo(TEST_TOPICS_ENCRYPTION_LATENCY);
        assertThat(stats.getLatencyOfEncryptionPerTopicMs())
                .isEqualTo(TEST_TOPICS_ENCRYPTION_LATENCY/3);
        assertThat(stats.getLatencyOfPersistingEncryptedTopicsToDbMs())
                .isEqualTo(TEST_PERSIST_ENCRYPTED_TOPICS_TO_DB_LATENCY);
    }

    @Test
    public void testDump() throws Exception {
        // Trigger the dump to verify no crash
        dump(pw -> mEpochManager.dump(pw, new String[] {}));
    }

    @Test
    public void testComputeEpoch_emptyTopTopics() {
        // Create a new EpochManager that we can control the random generator.
        TopicsDao topicsDao = Mockito.spy(new TopicsDao(mDbHelper));
        // Mock EpochManager for getCurrentEpochId()
        EpochManager epochManager =
                Mockito.spy(
                        new EpochManager(
                                topicsDao,
                                mDbHelper,
                                new Random(),
                                mMockClassifier,
                                mFakeFlags,
                                mMockClock,
                                mClassifierManager,
                                mEncryptionManager,
                                mAdServicesLogger));

        // To mimic the scenario that there was no usage in last epoch.
        // i.e. current epoch id is 2, with some usages, while epoch id = 1 has no usage.
        long epochId = 2L;
        doReturn(epochId).when(epochManager).getCurrentEpochId();

        // Note: we iterate over the appSdksUsageMap. For the test to be deterministic, we use
        // LinkedHashMap so that the order of iteration is defined.
        Map<String, List<String>> appSdksUsageMap = new LinkedHashMap<>();
        // app1 called Topics API directly. In addition, 2 of its sdks, sdk1 and sdk2 called the
        // Topics API.
        appSdksUsageMap.put("app1", Arrays.asList("", "sdk1", "sdk2"));

        // Mock TopicsDao to return above LinkedHashMap for retrieveAppSdksUsageMap()
        when(topicsDao.retrieveAppSdksUsageMap(epochId)).thenReturn(appSdksUsageMap);

        Map<String, List<Topic>> appClassificationTopicsMap = new HashMap<>();
        appClassificationTopicsMap.put("app1", createTopics(Arrays.asList(1, 2)));
        when(mMockClassifier.classify(eq(appSdksUsageMap.keySet())))
                .thenReturn(appClassificationTopicsMap);

        // Mock Classifier to return empty top topic list
        when(mMockClassifier.getTopTopics(
                        eq(appClassificationTopicsMap),
                        eq(mFakeFlags.getTopicsNumberOfTopTopics()),
                        eq(mFakeFlags.getTopicsNumberOfRandomTopics())))
                .thenReturn(Collections.emptyList());

        epochManager.processEpoch();

        verify(epochManager).getCurrentEpochId();
        verify(topicsDao).retrieveAppSdksUsageMap(epochId);
        verify(mMockClassifier).classify(appSdksUsageMap.keySet());
        verify(mMockClassifier)
                .getTopTopics(
                        appClassificationTopicsMap,
                        mFakeFlags.getTopicsNumberOfTopTopics(),
                        mFakeFlags.getTopicsNumberOfRandomTopics());

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);

        // Verify AppClassificationTopics table is still persisted
        Map<String, List<Topic>> expectedAppClassificationTopicsMap = new HashMap<>();
        expectedAppClassificationTopicsMap.put("app1", Arrays.asList(topic1, topic2));
        Map<String, List<Topic>> appClassificationTopicsMapFromDB =
                topicsDao.retrieveAppClassificationTopics(epochId);
        assertThat(appClassificationTopicsMapFromDB).isEqualTo(expectedAppClassificationTopicsMap);

        // Verify CallerCanLearnTopics table is still persisted
        Map<Topic, Set<String>> expectedCallersCanLearnMap = new HashMap<>();
        expectedCallersCanLearnMap.put(
                topic1, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2")));
        expectedCallersCanLearnMap.put(
                topic2, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2")));
        Map<Topic, Set<String>> callersCanLearnMapFromDB =
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

    @Test
    public void testIsTopicLearnableByCaller() {
        String app = "app";
        String sdk = "sdk";
        int numberOfTopicTopics = 5;

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic randomTopic = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic7 = Topic.create(/* topic */ 7, TAXONOMY_VERSION, MODEL_VERSION);
        // Top topic list contains 5 topics and 1 random topic
        List<Topic> topTopics = List.of(topic1, topic2, topic3, topic4, topic5, randomTopic);

        // Only app is able to learn topic1
        Map<Topic, Set<String>> callersCanLearnMap = Map.of(topic1, Set.of(app));

        // Both app and sdk can learn topic6, which is the random topic
        assertThat(
                        EpochManager.isTopicLearnableByCaller(
                                randomTopic,
                                app,
                                callersCanLearnMap,
                                topTopics,
                                numberOfTopicTopics))
                .isTrue();
        assertThat(
                        EpochManager.isTopicLearnableByCaller(
                                randomTopic,
                                sdk,
                                callersCanLearnMap,
                                topTopics,
                                numberOfTopicTopics))
                .isTrue();

        // Only app can learn topic1
        assertThat(
                        EpochManager.isTopicLearnableByCaller(
                                topic1, app, callersCanLearnMap, topTopics, numberOfTopicTopics))
                .isTrue();
        assertThat(
                        EpochManager.isTopicLearnableByCaller(
                                topic1, sdk, callersCanLearnMap, topTopics, numberOfTopicTopics))
                .isFalse();

        // No caller can learn topic 7, which is not in the list of top topics
        assertThat(
                        EpochManager.isTopicLearnableByCaller(
                                topic7, app, callersCanLearnMap, topTopics, numberOfTopicTopics))
                .isFalse();
        assertThat(
                        EpochManager.isTopicLearnableByCaller(
                                topic7, sdk, callersCanLearnMap, topTopics, numberOfTopicTopics))
                .isFalse();
    }

    @Test
    public void testIsTopicLearnableByCaller_configurableNumberOfTopics() {
        String app = "app";
        int numberOfTopicTopics = 3;

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic randomTopic1 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic randomTopic2 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic randomTopic3 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);
        // Top topic list contains 3 topics and 3 random topics
        List<Topic> topTopics =
                List.of(topic1, topic2, topic3, randomTopic1, randomTopic2, randomTopic3);

        // The app is only able to learn topic1
        Map<Topic, Set<String>> callersCanLearnMap = Map.of(topic1, Set.of(app));

        // All random topics can be learned.
        assertThat(
                        EpochManager.isTopicLearnableByCaller(
                                randomTopic1,
                                app,
                                callersCanLearnMap,
                                topTopics,
                                numberOfTopicTopics))
                .isTrue();
        assertThat(
                        EpochManager.isTopicLearnableByCaller(
                                randomTopic2,
                                app,
                                callersCanLearnMap,
                                topTopics,
                                numberOfTopicTopics))
                .isTrue();
        assertThat(
                        EpochManager.isTopicLearnableByCaller(
                                randomTopic3,
                                app,
                                callersCanLearnMap,
                                topTopics,
                                numberOfTopicTopics))
                .isTrue();

        // For regular topics, only topic 1 can be learned.
        assertThat(
                        EpochManager.isTopicLearnableByCaller(
                                topic1, app, callersCanLearnMap, topTopics, numberOfTopicTopics))
                .isTrue();
        assertThat(
                        EpochManager.isTopicLearnableByCaller(
                                topic2, app, callersCanLearnMap, topTopics, numberOfTopicTopics))
                .isFalse();
        assertThat(
                        EpochManager.isTopicLearnableByCaller(
                                topic3, app, callersCanLearnMap, topTopics, numberOfTopicTopics))
                .isFalse();
    }

    @Test
    public void testGetCurrentEpochId() {
        Flags flags = mock(Flags.class);
        when(flags.getTopicsEpochJobPeriodMs()).thenReturn(TOPICS_EPOCH_JOB_PERIOD_MS);
        // Initialize a local instance of epochManager to use mocked Flags.
        EpochManager epochManager =
                new EpochManager(
                        mTopicsDao,
                        mDbHelper,
                        new Random(),
                        mMockClassifier,
                        flags,
                        mMockClock,
                        mClassifierManager,
                        mEncryptionManager,
                        mAdServicesLogger);

        // Mock clock so that:
        // 1st call: There is no origin and will set 0 as origin.
        // 2nd call: The beginning of next epoch
        // 3rd call: In the middle of the epoch after to test if current time is at somewhere
        // between two epochs.
        when(mMockClock.currentTimeMillis())
                .thenReturn(
                        0L, TOPICS_EPOCH_JOB_PERIOD_MS, (long) (2.5 * TOPICS_EPOCH_JOB_PERIOD_MS));

        // Origin doesn't exist
        assertThat(mTopicsDao.retrieveEpochOrigin()).isEqualTo(-1);
        assertThat(epochManager.getCurrentEpochId()).isEqualTo(0L);
        // Origin has been persisted
        assertThat(mTopicsDao.retrieveEpochOrigin()).isEqualTo(0L);

        // 2nd call is on the start of next epoch (epochId = 1)
        assertThat(epochManager.getCurrentEpochId()).isEqualTo(1L);

        // 3rd call is in the middle of the epoch after (epochId = 2)
        assertThat(epochManager.getCurrentEpochId()).isEqualTo(2L);

        verify(flags, times(3)).getTopicsEpochJobPeriodMs();
        verify(mMockClock, times(3)).currentTimeMillis();
    }

    @Test
    public void testComputeTopTopicsToContributorsMap() {
        EpochManager epochManager = createEpochManagerWithMockedFlag();

        // Topic1 and Topic2 are top topics. Topic3 is not a top topic.
        Topic topic1 = createTopic(1);
        Topic topic2 = createTopic(2);
        Topic topic3 = createTopic(3);

        String app1 = "app1";
        String app2 = "app2";
        String app3 = "app3"; // an app without classified topics

        Map<String, List<Topic>> appClassificationTopicsMap =
                Map.of(
                        app1, List.of(topic1, topic3),
                        app2, List.of(topic1, topic2, topic3),
                        app3, List.of());
        List<Topic> topTopics = List.of(topic1, topic2);

        // Only topic1 and topic2 will be computed as they are top topics.
        Map<Integer, Set<String>> expectedTopTopicsToContributorsMap =
                Map.of(
                        topic1.getTopic(), Set.of(app1, app2),
                        topic2.getTopic(), Set.of(app2));

        // Ignore the effect of padded topics
        when(mMockFlags.getTopicsNumberOfTopTopics()).thenReturn(topTopics.size());

        assertThat(
                        epochManager.computeTopTopicsToContributorsMap(
                                appClassificationTopicsMap, topTopics))
                .isEqualTo(expectedTopTopicsToContributorsMap);
    }

    @Test
    public void testComputeTopTopicsToContributorsMap_emptyTopTopics() {
        EpochManager epochManager = createEpochManagerWithMockedFlag();
        // Topic1 and Topic2 are top topics. Topic3 is not a top topic.
        Topic topic1 = createTopic(1);
        Topic topic2 = createTopic(2);
        Topic topic3 = createTopic(3);

        String app1 = "app1";
        String app2 = "app2";
        String app3 = "app3";

        Map<String, List<Topic>> appClassificationTopicsMap =
                Map.of(
                        app1, List.of(topic1, topic3),
                        app2, List.of(topic1, topic2, topic3),
                        app3, List.of());
        List<Topic> topTopics = List.of();

        // Ignore the effect of padded topics
        when(mMockFlags.getTopicsNumberOfTopTopics()).thenReturn(topTopics.size());

        assertThat(
                        epochManager.computeTopTopicsToContributorsMap(
                                appClassificationTopicsMap, topTopics))
                .isEmpty();
    }

    @Test
    public void testComputeTopTopicsToContributorsMap_paddedTopics() {
        EpochManager epochManager = createEpochManagerWithMockedFlag();

        // Topic1 and Topic2 are top topics. Topic3 is not a top topic.
        Topic topic1 = createTopic(1);
        Topic topic2 = createTopic(2);
        Topic topic3 = createTopic(3);
        Topic topic4 = createTopic(4);
        Topic topic5 = createTopic(5);
        Topic topic6 = createTopic(6);

        String app1 = "app1";
        String app2 = "app2";

        Map<String, List<Topic>> appClassificationTopicsMap =
                Map.of(
                        app1, List.of(topic1, topic3),
                        app2, List.of(topic1, topic2, topic3));

        // app4 and app5 are padded topics without any contributors.
        List<Topic> topTopics = List.of(topic1, topic2, topic3, topic4, topic5, topic6);

        when(mMockFlags.getTopicsNumberOfTopTopics())
                .thenReturn(FakeFlagsFactory.getFlagsForTest().getTopicsNumberOfTopTopics());

        // topic1, topic2, topic3 will be computed as they are normal top topics.
        // topic4 and topic5 will be annotated as padded topics.
        // topic6 won't be included as it's a random topic.
        Map<Integer, Set<String>> expectedTopTopicsToContributorsMap =
                Map.of(
                        topic1.getTopic(), Set.of(app1, app2),
                        topic2.getTopic(), Set.of(app2),
                        topic3.getTopic(), Set.of(app1, app2),
                        topic4.getTopic(), Set.of(PADDED_TOP_TOPICS_STRING),
                        topic5.getTopic(), Set.of(PADDED_TOP_TOPICS_STRING));

        assertThat(
                        epochManager.computeTopTopicsToContributorsMap(
                                appClassificationTopicsMap, topTopics))
                .isEqualTo(expectedTopTopicsToContributorsMap);
    }

    @Test
    public void testGetTopicIdForLogging() {
        // Set topicsPrivacyBudgetForTopicIdDistribution to 1.0. Set taxonomy list to
        // [10, 20, 30, 40, 50]. According to equation:
        // (taxonomySize - 1) / (e^privacyBudget + taxonomySize - 1), there is a 59.5% probability
        // of getting a random topic different from the real topic when we log the topic.
        float topicsPrivacyBudgetForTopicIdDistribution = 1f;
        when(mMockFlags.getTopicsPrivacyBudgetForTopicIdDistribution())
                .thenReturn(topicsPrivacyBudgetForTopicIdDistribution);
        when(mClassifierManager.getTopicsTaxonomy())
                .thenReturn(ImmutableList.of(10, 20, 30, 40, 50));
        when(mRandom.nextInt(anyInt())).thenReturn(4, 2, 0, 1);
        EpochManager epochManager =
                new EpochManager(
                        mTopicsDao,
                        mDbHelper,
                        mRandom,
                        mMockClassifier,
                        mMockFlags,
                        mMockClock,
                        mClassifierManager,
                        mEncryptionManager,
                        mAdServicesLogger);

        // The first random double is 0.1, it's smaller than 0.595,
        // loggedTopic should be a random topic 50.
        when(mRandom.nextDouble()).thenReturn(0.1d);
        Topic topic1 =
                Topic.create(/* topic */ 10, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        int loggedTopic1 = epochManager.getTopicIdForLogging(topic1);
        assertThat(loggedTopic1).isEqualTo(50);

        // The second random double is 0.2, it's smaller than 0.595,
        // loggedTopic should be a random topic 30.
        when(mRandom.nextDouble()).thenReturn(0.2d);
        Topic topic2 =
                Topic.create(/* topic */ 20, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        int loggedTopic2 = epochManager.getTopicIdForLogging(topic2);
        assertThat(loggedTopic2).isEqualTo(30);

        // The third random double is 0.9, it's larger than 0.595,
        // loggedTopic should be a real topic.
        when(mRandom.nextDouble()).thenReturn(0.9d);
        Topic topic3 =
                Topic.create(/* topic */ 30, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        int loggedTopic3 = epochManager.getTopicIdForLogging(topic3);
        assertThat(loggedTopic3).isEqualTo(topic3.getTopic());

        // The forth random double is 0.55, it's smaller than 0.595,
        // loggedTopic should be a random topic 20.
        when(mRandom.nextDouble()).thenReturn(0.55d);
        Topic topic4 =
                Topic.create(/* topic */ 40, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        int loggedTopic4 = epochManager.getTopicIdForLogging(topic4);
        assertThat(loggedTopic4).isEqualTo(10);

        // The fifth random double is 0.6, it's larger than 0.595,
        // loggedTopic should be a real topic.
        when(mRandom.nextDouble()).thenReturn(0.6d);
        Topic topic5 =
                Topic.create(/* topic */ 50, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        int loggedTopic5 = epochManager.getTopicIdForLogging(topic5);
        assertThat(loggedTopic5).isEqualTo(topic5.getTopic());

        // Verify that when the taxonomy size is 1, even if the random double is smaller than
        // the probability, the function still returns the original topic.
        when(mClassifierManager.getTopicsTaxonomy()).thenReturn(ImmutableList.of(100));
        when(mRandom.nextDouble()).thenReturn(0.1d);
        when(mRandom.nextInt(anyInt())).thenReturn(0);
        Topic topic6 =
                Topic.create(/* topic */ 100, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        int loggedTopic6 = epochManager.getTopicIdForLogging(topic6);
        assertThat(loggedTopic6).isEqualTo(topic6.getTopic());

        // Verify that when there is a bug with random generation,
        // if the random double is smaller than the probability, the function will not be stuck in
        // an infinite loop because of the maximum attempts.
        when(mClassifierManager.getTopicsTaxonomy()).thenReturn(ImmutableList.of(1000, 2000));
        when(mRandom.nextDouble()).thenReturn(0.1d);
        when(mRandom.nextInt(anyInt())).thenReturn(0);
        Topic topic7 =
                Topic.create(/* topic */ 1000, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        int loggedTopic7 = epochManager.getTopicIdForLogging(topic7);
        assertThat(loggedTopic7).isEqualTo(topic7.getTopic());
    }

    private Topic createTopic(int topicId) {
        return Topic.create(topicId, TAXONOMY_VERSION, MODEL_VERSION);
    }

    private List<Topic> createTopics(List<Integer> topicIds) {
        return topicIds.stream().map(this::createTopic).collect(Collectors.toList());
    }

    private EpochManager createEpochManagerWithMockedFlag() {
        return new EpochManager(
                mTopicsDao,
                mDbHelper,
                new Random(),
                mMockClassifier,
                mMockFlags,
                mMockClock,
                mClassifierManager,
                mEncryptionManager,
                mAdServicesLogger);
    }
}
