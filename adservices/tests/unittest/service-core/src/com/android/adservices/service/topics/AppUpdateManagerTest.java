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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockRandom;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/** Unit tests for {@link com.android.adservices.service.topics.AppUpdateManager} */
public class AppUpdateManagerTest {
    @SuppressWarnings({"unused"})
    private static final String TAG = "AppInstallationInfoManagerTest";

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private final DbHelper mDbHelper = DbTestUtil.getDbHelperForTest();

    private AppUpdateManager mAppUpdateManager;
    private TopicsDao mTopicsDao;

    @Mock PackageManager mMockPackageManager;
    @Mock Flags mMockFlags;

    @Before
    public void setup() {
        // In order to mock Package Manager, context also needs to be mocked to return
        // mocked Package Manager
        MockitoAnnotations.initMocks(this);
        when(mContext.getPackageManager()).thenReturn(mMockPackageManager);

        mTopicsDao = new TopicsDao(mDbHelper);
        // Erase all existing data.
        DbTestUtil.deleteTable(TopicsTables.TaxonomyContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppClassificationTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.CallerCanLearnTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.ReturnedTopicContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.UsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppUsageHistoryContract.TABLE);

        mAppUpdateManager = new AppUpdateManager(mTopicsDao, new Random(), mMockFlags);
    }

    @Test
    public void testReconcileUninstalledApps() {
        // Both app1 and app2 have usages in database. App 2 won't be current installed app list
        // that is returned by mocked Package Manager, so it'll be regarded as an unhanded installed
        // app.
        final String app1 = "app1";
        final String app2 = "app2";

        // Mock Package Manager for installed applications
        ApplicationInfo appInfo1 = new ApplicationInfo();
        appInfo1.packageName = app1;

        when(mMockPackageManager.getInstalledApplications(Mockito.any()))
                .thenReturn(Collections.singletonList(appInfo1));

        // Begin to persist data into database
        // Handle AppClassificationTopicsContract
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        final long epochId1 = 1L;
        final int topicId1 = 1;
        final int numberOfLookBackEpochs = 1;

        Topic topic1 = Topic.create(topicId1, taxonomyVersion, modelVersion);

        Map<String, List<Topic>> appClassificationTopicsMap1 = new HashMap<>();
        appClassificationTopicsMap1.put(app1, Collections.singletonList(topic1));
        appClassificationTopicsMap1.put(app2, Collections.singletonList(topic1));

        mTopicsDao.persistAppClassificationTopics(epochId1, appClassificationTopicsMap1);
        // Verify AppClassificationContract has both apps
        assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId1).keySet())
                .containsExactly(app1, app2);

        // Handle UsageHistoryContract
        final String sdk1 = "sdk1";

        mTopicsDao.recordUsageHistory(epochId1, app1, "");
        mTopicsDao.recordUsageHistory(epochId1, app1, sdk1);
        mTopicsDao.recordUsageHistory(epochId1, app2, "");
        mTopicsDao.recordUsageHistory(epochId1, app2, sdk1);

        // Verify UsageHistoryContract has both apps
        assertThat(mTopicsDao.retrieveAppSdksUsageMap(epochId1).keySet())
                .containsExactly(app1, app2);

        // Handle AppUsageHistoryContract
        mTopicsDao.recordAppUsageHistory(epochId1, app1);
        mTopicsDao.recordAppUsageHistory(epochId1, app2);

        // Verify AppUsageHistoryContract has both apps
        assertThat(mTopicsDao.retrieveAppUsageMap(epochId1).keySet()).containsExactly(app1, app2);

        // Handle CallerCanLearnTopicsContract
        Map<Topic, Set<String>> callerCanLearnMap = new HashMap<>();
        callerCanLearnMap.put(topic1, new HashSet<>(Arrays.asList(app1, app2, sdk1)));
        mTopicsDao.persistCallerCanLearnTopics(epochId1, callerCanLearnMap);

        // Verify CallerCanLearnTopicsContract has both apps
        assertThat(
                        mTopicsDao
                                .retrieveCallerCanLearnTopicsMap(epochId1, numberOfLookBackEpochs)
                                .get(topic1))
                .containsAtLeast(app1, app2);

        // Handle ReturnedTopicContract
        Map<Pair<String, String>, Topic> returnedAppSdkTopics = new HashMap<>();
        returnedAppSdkTopics.put(Pair.create(app1, /* sdk */ ""), topic1);
        returnedAppSdkTopics.put(Pair.create(app1, sdk1), topic1);
        returnedAppSdkTopics.put(Pair.create(app2, /* sdk */ ""), topic1);
        returnedAppSdkTopics.put(Pair.create(app2, sdk1), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(epochId1, returnedAppSdkTopics);
        Map<Pair<String, String>, Topic> expectedReturnedTopics = new HashMap<>();
        expectedReturnedTopics.put(Pair.create(app1, /* sdk */ ""), topic1);
        expectedReturnedTopics.put(Pair.create(app1, sdk1), topic1);
        expectedReturnedTopics.put(Pair.create(app2, /* sdk */ ""), topic1);
        expectedReturnedTopics.put(Pair.create(app2, sdk1), topic1);

        // Verify ReturnedTopicContract has both apps
        assertThat(
                        mTopicsDao
                                .retrieveReturnedTopics(epochId1, numberOfLookBackEpochs)
                                .get(epochId1))
                .isEqualTo(expectedReturnedTopics);

        // Reconcile uninstalled applications
        mAppUpdateManager.reconcileUninstalledApps(mContext);

        verify(mContext).getPackageManager();
        verify(mMockPackageManager).getInstalledApplications(Mockito.any());

        // Each Table should have wiped off all data belonging to app2
        Set<String> setContainsOnlyApp1 = new HashSet<>(Collections.singletonList(app1));
        assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId1).keySet())
                .isEqualTo(setContainsOnlyApp1);
        assertThat(mTopicsDao.retrieveAppSdksUsageMap(epochId1).keySet())
                .isEqualTo(setContainsOnlyApp1);
        assertThat(mTopicsDao.retrieveAppUsageMap(epochId1).keySet())
                .isEqualTo(setContainsOnlyApp1);
        assertThat(
                        mTopicsDao
                                .retrieveCallerCanLearnTopicsMap(epochId1, numberOfLookBackEpochs)
                                .get(topic1))
                .doesNotContain(app2);
        // Returned Topics Map contains only App1 paris
        Map<Pair<String, String>, Topic> expectedReturnedTopicsAfterWiping = new HashMap<>();
        expectedReturnedTopicsAfterWiping.put(Pair.create(app1, /* sdk */ ""), topic1);
        expectedReturnedTopicsAfterWiping.put(Pair.create(app1, sdk1), topic1);
        assertThat(
                        mTopicsDao
                                .retrieveReturnedTopics(epochId1, numberOfLookBackEpochs)
                                .get(epochId1))
                .isEqualTo(expectedReturnedTopicsAfterWiping);
    }

    @Test
    public void testGetUnhandledUninstalledApps() {
        final long epochId = 1L;
        Set<String> currentInstalledApps = Set.of("app1", "app2", "app5");

        // Add app1 and app3 into usage table
        mTopicsDao.recordAppUsageHistory(epochId, "app1");
        mTopicsDao.recordAppUsageHistory(epochId, "app3");

        // Add app2 and app4 into returned topic table
        mTopicsDao.persistReturnedAppTopicsMap(
                epochId,
                Map.of(
                        Pair.create("app2", ""),
                        Topic.create(
                                /* topic ID */ 1, /* taxonomyVersion */ 1L, /* model version */ 1L),
                        Pair.create("app4", ""),
                        Topic.create(
                                /* topic ID */ 1, /* taxonomyVersion */
                                1L, /* model version */
                                1L)));

        // Unhandled apps = usageTable U returnedTopicTable - currentInstalled
        //                = ((app1, app3) U (app2, app4)) - (app1, app2, app5) = (app3, app4)
        // Note that app5 is installed but doesn't have usage of returned topic, so it won't be
        // handled.
        assertThat(mAppUpdateManager.getUnhandledUninstalledApps(currentInstalledApps))
                .isEqualTo(Set.of("app3", "app4"));
    }

    @Test
    public void testGetUnhandledInstalledApps() {
        final long epochId = 10L;
        Set<String> currentInstalledApps = Set.of("app1", "app2", "app3", "app4");

        // Add app1 and app5 into usage table
        mTopicsDao.recordAppUsageHistory(epochId, "app1");
        mTopicsDao.recordAppUsageHistory(epochId, "app5");

        // Add app2 and app6 into returned topic table
        mTopicsDao.persistReturnedAppTopicsMap(
                epochId,
                Map.of(
                        Pair.create("app2", ""),
                        Topic.create(
                                /* topic ID */ 1, /* taxonomyVersion */ 1L, /* model version */ 1L),
                        Pair.create("app6", ""),
                        Topic.create(
                                /* topic ID */ 1, /* taxonomyVersion */
                                1L, /* model version */
                                1L)));

        // Unhandled apps = currentInstalled - usageTable U returnedTopicTable
        //          = (app1, app2, app3, app4) - ((app1, app5) U (app2, app6)) -  = (app3, app4)
        // Note that app5 and app6 have usages or returned topics, but not currently installed, so
        // they won't be handled.
        assertThat(mAppUpdateManager.getUnhandledInstalledApps(currentInstalledApps))
                .isEqualTo(Set.of("app3", "app4"));
    }

    @Test
    public void testDeleteAppDataFromTableByApps() {
        final String app1 = "app1";
        final String app2 = "app2";
        final String app3 = "app3";

        // Begin to persist data into database.
        // app1, app2 and app3 have usages in database. Derived data of app2 and app3 will be wiped.
        // Therefore, database will only contain app1's data.

        // Handle AppClassificationTopicsContract
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        final long epochId1 = 1L;
        final int topicId1 = 1;
        final int numberOfLookBackEpochs = 1;

        Topic topic1 = Topic.create(topicId1, taxonomyVersion, modelVersion);

        mTopicsDao.persistAppClassificationTopics(epochId1, Map.of(app1, List.of(topic1)));
        mTopicsDao.persistAppClassificationTopics(epochId1, Map.of(app2, List.of(topic1)));
        mTopicsDao.persistAppClassificationTopics(epochId1, Map.of(app3, List.of(topic1)));
        // Verify AppClassificationContract has both apps
        assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId1).keySet())
                .isEqualTo(Set.of(app1, app2, app3));

        // Handle UsageHistoryContract
        final String sdk1 = "sdk1";

        mTopicsDao.recordUsageHistory(epochId1, app1, "");
        mTopicsDao.recordUsageHistory(epochId1, app1, sdk1);
        mTopicsDao.recordUsageHistory(epochId1, app2, "");
        mTopicsDao.recordUsageHistory(epochId1, app2, sdk1);
        mTopicsDao.recordUsageHistory(epochId1, app3, "");
        mTopicsDao.recordUsageHistory(epochId1, app3, sdk1);

        // Verify UsageHistoryContract has both apps
        assertThat(mTopicsDao.retrieveAppSdksUsageMap(epochId1).keySet())
                .isEqualTo(Set.of(app1, app2, app3));

        // Handle AppUsageHistoryContract
        mTopicsDao.recordAppUsageHistory(epochId1, app1);
        mTopicsDao.recordAppUsageHistory(epochId1, app2);
        mTopicsDao.recordAppUsageHistory(epochId1, app3);

        // Verify AppUsageHistoryContract has both apps
        assertThat(mTopicsDao.retrieveAppUsageMap(epochId1).keySet())
                .isEqualTo(Set.of(app1, app2, app3));

        // Handle CallerCanLearnTopicsContract
        Map<Topic, Set<String>> callerCanLearnMap = new HashMap<>();
        callerCanLearnMap.put(topic1, new HashSet<>(List.of(app1, app2, app3, sdk1)));
        mTopicsDao.persistCallerCanLearnTopics(epochId1, callerCanLearnMap);

        // Verify CallerCanLearnTopicsContract has both apps
        assertThat(
                        mTopicsDao
                                .retrieveCallerCanLearnTopicsMap(epochId1, numberOfLookBackEpochs)
                                .get(topic1))
                .isEqualTo(Set.of(app1, app2, app3, sdk1));

        // Handle ReturnedTopicContract
        Map<Pair<String, String>, Topic> returnedAppSdkTopics = new HashMap<>();
        returnedAppSdkTopics.put(Pair.create(app1, /* sdk */ ""), topic1);
        returnedAppSdkTopics.put(Pair.create(app1, sdk1), topic1);
        returnedAppSdkTopics.put(Pair.create(app2, /* sdk */ ""), topic1);
        returnedAppSdkTopics.put(Pair.create(app2, sdk1), topic1);
        returnedAppSdkTopics.put(Pair.create(app3, /* sdk */ ""), topic1);
        returnedAppSdkTopics.put(Pair.create(app3, sdk1), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(epochId1, returnedAppSdkTopics);

        // Verify ReturnedTopicContract has both apps
        assertThat(
                        mTopicsDao
                                .retrieveReturnedTopics(epochId1, numberOfLookBackEpochs)
                                .get(epochId1))
                .isEqualTo(returnedAppSdkTopics);

        // Delete app2's derived data
        mAppUpdateManager.deleteAppDataFromTableByApps(List.of(app2, app3));

        // Each Table should have wiped off all data belonging to app2
        Set<String> setContainsOnlyApp1 = Set.of(app1);
        assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId1).keySet())
                .isEqualTo(setContainsOnlyApp1);
        assertThat(mTopicsDao.retrieveAppSdksUsageMap(epochId1).keySet())
                .isEqualTo(setContainsOnlyApp1);
        assertThat(mTopicsDao.retrieveAppUsageMap(epochId1).keySet())
                .isEqualTo(setContainsOnlyApp1);
        assertThat(
                        mTopicsDao
                                .retrieveCallerCanLearnTopicsMap(epochId1, numberOfLookBackEpochs)
                                .get(topic1))
                .isEqualTo(Set.of(app1, sdk1));
        // Returned Topics Map contains only App1 paris
        Map<Pair<String, String>, Topic> expectedReturnedTopicsAfterWiping = new HashMap<>();
        expectedReturnedTopicsAfterWiping.put(Pair.create(app1, /* sdk */ ""), topic1);
        expectedReturnedTopicsAfterWiping.put(Pair.create(app1, sdk1), topic1);
        assertThat(
                        mTopicsDao
                                .retrieveReturnedTopics(epochId1, numberOfLookBackEpochs)
                                .get(epochId1))
                .isEqualTo(expectedReturnedTopicsAfterWiping);
    }

    @Test
    public void testDeleteAppDataFromTableByApp_nullUninstalledAppName() {
        assertThrows(
                NullPointerException.class,
                () -> mAppUpdateManager.deleteAppDataFromTableByApps(null));
    }

    @Test
    public void testDeleteAppDataFromTableByApp_nonExistingUninstalledAppName() {
        // To test it won't throw by calling the method with non-existing application name
        mAppUpdateManager.deleteAppDataFromTableByApps(List.of("app"));
    }

    @Test
    public void testDeleteAppDataByUri() {
        // Mock AppUpdateManager to check the invocation of deleteAppDataByUri() because
        // the functionality has already been tested in testDeleteAppDataFromTableByApp
        AppUpdateManager appUpdateManager =
                Mockito.spy(new AppUpdateManager(mTopicsDao, new Random(), mMockFlags));

        final String appName = "app";
        Uri packageUri = Uri.parse(appName);

        // Only verify the invocation of deleteAppDataFromTableByApp()
        doNothing().when(appUpdateManager).deleteAppDataFromTableByApps(eq(List.of(appName)));

        appUpdateManager.deleteAppDataByUri(packageUri);

        verify(appUpdateManager).deleteAppDataByUri(eq(packageUri));
        verify(appUpdateManager).deleteAppDataFromTableByApps(eq(List.of(appName)));
    }

    @Test
    public void testDeleteAppDataByUri_nullUri() {
        assertThrows(NullPointerException.class, () -> mAppUpdateManager.deleteAppDataByUri(null));
    }

    @Test
    public void testDeleteAppDataByUri_nonExistingUninstalledAppName() {
        // To test it won't throw by calling the method with Uri containing
        // non-existing application name.
        mAppUpdateManager.deleteAppDataByUri(Uri.parse("app"));
    }

    @Test
    public void testReconcileInstalledApps() {
        final String app1 = "app1";
        final String app2 = "app2";
        final long currentEpochId = 4L;
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        final int numOfLookBackEpochs = 3;
        final int topicsNumberOfTopTopics = 5;
        final int topicsNumberOfRandomTopics = 1;
        final int topicsPercentageForRandomTopic = 5;

        // As selectAssignedTopicFromTopTopics() randomly assigns a top topic, pass in a Mocked
        // Random object to make the result deterministic.
        //
        // In this test, topic 1, 2, and 6 are supposed to be returned. For each topic, it needs 2
        // random draws: the first is to determine whether to select a random topic, the second is
        // draw the actual topic index.
        MockRandom mockRandom =
                new MockRandom(
                        new long[] {
                            topicsPercentageForRandomTopic, // Will select a regular topic
                            0, // Index of first topic
                            topicsPercentageForRandomTopic, // Will select a regular topic
                            1, // Index of second topic
                            0, // Will select a random topic
                            topicsNumberOfRandomTopics - 1 // Select the last random topic
                        });
        AppUpdateManager appUpdateManager =
                new AppUpdateManager(mTopicsDao, mockRandom, mMockFlags);
        // Mock Flags to get an independent result
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numOfLookBackEpochs);
        when(mMockFlags.getTopicsNumberOfTopTopics()).thenReturn(topicsNumberOfTopTopics);
        when(mMockFlags.getTopicsNumberOfRandomTopics()).thenReturn(topicsNumberOfRandomTopics);
        when(mMockFlags.getTopicsPercentageForRandomTopic())
                .thenReturn(topicsPercentageForRandomTopic);

        // Mock Package Manager for installed applications
        ApplicationInfo appInfo1 = new ApplicationInfo();
        appInfo1.packageName = app1;
        ApplicationInfo appInfo2 = new ApplicationInfo();
        appInfo2.packageName = app2;

        when(mMockPackageManager.getInstalledApplications(Mockito.any()))
                .thenReturn(List.of(appInfo1, appInfo2));

        Topic topic1 = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(/* topic */ 2, taxonomyVersion, modelVersion);
        Topic topic3 = Topic.create(/* topic */ 3, taxonomyVersion, modelVersion);
        Topic topic4 = Topic.create(/* topic */ 4, taxonomyVersion, modelVersion);
        Topic topic5 = Topic.create(/* topic */ 5, taxonomyVersion, modelVersion);
        Topic topic6 = Topic.create(/* topic */ 6, taxonomyVersion, modelVersion);
        List<Topic> topTopics = List.of(topic1, topic2, topic3, topic4, topic5, topic6);

        // Begin to persist data into database
        // Both app1 and app2 are currently installed apps according to Package Manager, but
        // Only app1 will have usage in database. Therefore, app2 will be regarded as newly
        // installed app.
        mTopicsDao.recordAppUsageHistory(currentEpochId - 1, app1);
        // Unused but to mimic what happens in reality
        mTopicsDao.recordUsageHistory(currentEpochId - 1, app1, /* sdk */ "sdk");

        // Persist top topics into database for last 3 epochs
        for (long epochId = currentEpochId - 1;
                epochId >= currentEpochId - numOfLookBackEpochs;
                epochId--) {
            mTopicsDao.persistTopTopics(epochId, topTopics);
        }

        // Assign topics to past epochs
        appUpdateManager.reconcileInstalledApps(mContext, currentEpochId);

        Map<Long, Map<Pair<String, String>, Topic>> expectedReturnedTopics = new HashMap<>();
        expectedReturnedTopics.put(
                currentEpochId - 1, Map.of(Pair.create(app2, /* sdk */ ""), topic1));
        expectedReturnedTopics.put(
                currentEpochId - 2, Map.of(Pair.create(app2, /* sdk */ ""), topic2));
        expectedReturnedTopics.put(
                currentEpochId - 3, Map.of(Pair.create(app2, /* sdk */ ""), topic6));

        assertThat(mTopicsDao.retrieveReturnedTopics(currentEpochId - 1, numOfLookBackEpochs))
                .isEqualTo(expectedReturnedTopics);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();
        verify(mMockFlags).getTopicsNumberOfTopTopics();
        verify(mMockFlags).getTopicsNumberOfRandomTopics();
        verify(mMockFlags).getTopicsPercentageForRandomTopic();
    }

    @Test
    public void testSelectAssignedTopicFromTopTopics() {
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        final int topicsNumberOfTopTopics = 5;
        final int topicsNumberOfRandomTopics = 1;
        final int topicsPercentageForRandomTopic = 5;

        // Test the randomness with pre-defined values
        MockRandom mockRandom =
                new MockRandom(
                        new long[] {
                            0, // Will select a random topic
                            topicsNumberOfRandomTopics - 1, // Select the last random topic
                            topicsPercentageForRandomTopic, // Will select a regular topic
                            0 // Select the first regular topic
                        });
        AppUpdateManager appUpdateManager =
                new AppUpdateManager(mTopicsDao, mockRandom, mMockFlags);

        Topic topic1 = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(/* topic */ 2, taxonomyVersion, modelVersion);
        Topic topic3 = Topic.create(/* topic */ 3, taxonomyVersion, modelVersion);
        Topic topic4 = Topic.create(/* topic */ 4, taxonomyVersion, modelVersion);
        Topic topic5 = Topic.create(/* topic */ 5, taxonomyVersion, modelVersion);
        Topic topic6 = Topic.create(/* topic */ 6, taxonomyVersion, modelVersion);

        List<Topic> topTopics = Arrays.asList(topic1, topic2, topic3, topic4, topic5, topic6);

        // In the first invocation, mockRandom returns a 0 that indicates a regular top topic will
        // be returned, and following by another 0 to select the first regular top topic.
        Topic regularTopTopic =
                appUpdateManager.selectAssignedTopicFromTopTopics(
                        topTopics,
                        topicsNumberOfTopTopics,
                        topicsNumberOfRandomTopics,
                        topicsPercentageForRandomTopic);
        assertThat(regularTopTopic).isEqualTo(topic6);

        // In the second invocation, mockRandom returns a 5 that indicates a random top topic will
        // be returned, and following by a 0 to select the first(only) random top topic.
        Topic randomTopTopic =
                appUpdateManager.selectAssignedTopicFromTopTopics(
                        topTopics,
                        topicsNumberOfTopTopics,
                        topicsNumberOfRandomTopics,
                        topicsPercentageForRandomTopic);
        assertThat(randomTopTopic).isEqualTo(topic1);
    }

    @Test
    public void testSelectAssignedTopicFromTopTopics_invalidSize() {
        List<Integer> intTopics = Arrays.asList(1, 2, 3, 4, 5, 6);
        List<Topic> topTopics =
                intTopics.stream()
                        .map(
                                intTopic ->
                                        Topic.create(
                                                intTopic,
                                                /* Taxonomy Version */ 1L, /* Model Version */
                                                1L))
                        .collect(Collectors.toList());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppUpdateManager.selectAssignedTopicFromTopTopics(
                                topTopics,
                                /* topicsNumberOfTopTopics */ 4,
                                /* topicsNumberOfRandomTopics */ 1,
                                /* topicsPercentageForRandomTopic */ 5));
    }

    @Test
    public void testAssignTopicsToNewlyInstalledApps() {
        final String appName = "app";
        Uri packageUri = Uri.parse(appName);
        final long currentEpochId = 4L;
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        final int numOfLookBackEpochs = 3;
        final int topicsNumberOfTopTopics = 5;
        final int topicsNumberOfRandomTopics = 1;
        final int topicsPercentageForRandomTopic = 5;

        // As selectAssignedTopicFromTopTopics() randomly assigns a top topic, pass in a Mocked
        // Random object to make the result deterministic.
        //
        // In this test, topic 1, 2, and 6 are supposed to be returned. For each topic, it needs 2
        // random draws: the first is to determine whether to select a random topic, the second is
        // draw the actual topic index.
        MockRandom mockRandom =
                new MockRandom(
                        new long[] {
                            topicsPercentageForRandomTopic, // Will select a regular topic
                            0, // Index of first topic
                            topicsPercentageForRandomTopic, // Will select a regular topic
                            1, // Index of second topic
                            0, // Will select a random topic
                            topicsNumberOfRandomTopics - 1 // Select the last random topic
                        });

        // Spy an instance of AppUpdateManager in order to mock selectAssignedTopicFromTopTopics()
        // to avoid randomness.
        AppUpdateManager appUpdateManager =
                new AppUpdateManager(mTopicsDao, mockRandom, mMockFlags);
        // Mock Flags to get an independent result
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numOfLookBackEpochs);
        when(mMockFlags.getTopicsNumberOfTopTopics()).thenReturn(topicsNumberOfTopTopics);
        when(mMockFlags.getTopicsNumberOfRandomTopics()).thenReturn(topicsNumberOfRandomTopics);
        when(mMockFlags.getTopicsPercentageForRandomTopic())
                .thenReturn(topicsPercentageForRandomTopic);

        Topic topic1 = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(/* topic */ 2, taxonomyVersion, modelVersion);
        Topic topic3 = Topic.create(/* topic */ 3, taxonomyVersion, modelVersion);
        Topic topic4 = Topic.create(/* topic */ 4, taxonomyVersion, modelVersion);
        Topic topic5 = Topic.create(/* topic */ 5, taxonomyVersion, modelVersion);
        Topic topic6 = Topic.create(/* topic */ 6, taxonomyVersion, modelVersion);
        List<Topic> topTopics = List.of(topic1, topic2, topic3, topic4, topic5, topic6);

        // Persist top topics into database for last 3 epochs
        for (long epochId = currentEpochId - 1;
                epochId >= currentEpochId - numOfLookBackEpochs;
                epochId--) {
            mTopicsDao.persistTopTopics(epochId, topTopics);
        }

        // Assign topics to past epochs
        appUpdateManager.assignTopicsToNewlyInstalledApps(packageUri, currentEpochId);

        Map<Long, Map<Pair<String, String>, Topic>> expectedReturnedTopics = new HashMap<>();
        expectedReturnedTopics.put(
                currentEpochId - 1, Map.of(Pair.create(appName, /* sdk */ ""), topic1));
        expectedReturnedTopics.put(
                currentEpochId - 2, Map.of(Pair.create(appName, /* sdk */ ""), topic2));
        expectedReturnedTopics.put(
                currentEpochId - 3, Map.of(Pair.create(appName, /* sdk */ ""), topic6));

        assertThat(mTopicsDao.retrieveReturnedTopics(currentEpochId - 1, numOfLookBackEpochs))
                .isEqualTo(expectedReturnedTopics);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();
        verify(mMockFlags).getTopicsNumberOfTopTopics();
        verify(mMockFlags).getTopicsNumberOfRandomTopics();
        verify(mMockFlags).getTopicsPercentageForRandomTopic();
    }

    @Test
    public void testAssignTopicsToSdkForAppInstallation() {
        final String app = "app";
        final String sdk = "sdk";
        final int numberOfLookBackEpochs = 3;
        final long currentEpochId = 5L;
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;

        Pair<String, String> appOnlyCaller = Pair.create(app, /* sdk */ "");
        Pair<String, String> appSdkCaller = Pair.create(app, sdk);

        Topic topic1 = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(/* topic */ 2, taxonomyVersion, modelVersion);
        Topic topic3 = Topic.create(/* topic */ 3, taxonomyVersion, modelVersion);
        Topic[] topics = {topic1, topic2, topic3};

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        for (long epoch = 0; epoch < numberOfLookBackEpochs; epoch++) {
            long epochId = currentEpochId - 1 - epoch;
            Topic topic = topics[(int) epoch];

            mTopicsDao.persistReturnedAppTopicsMap(epochId, Map.of(appOnlyCaller, topic));
            // SDK needs to be able to learn this topic in past epochs
            mTopicsDao.persistCallerCanLearnTopics(epochId, Map.of(topic, Set.of(sdk)));
        }

        // Check app-sdk doesn't have returned topic before calling the method
        Map<Long, Map<Pair<String, String>, Topic>> returnedTopicsWithoutAssignment =
                mTopicsDao.retrieveReturnedTopics(currentEpochId - 1, numberOfLookBackEpochs);
        for (Map.Entry<Long, Map<Pair<String, String>, Topic>> entry :
                returnedTopicsWithoutAssignment.entrySet()) {
            assertThat(entry.getValue()).doesNotContainKey(appSdkCaller);
        }

        assertTrue(mAppUpdateManager.assignTopicsToSdkForAppInstallation(app, sdk, currentEpochId));

        // Check app-sdk has been assigned with topic after calling the method
        Map<Long, Map<Pair<String, String>, Topic>> expectedReturnedTopics = new HashMap<>();
        for (long epoch = 0; epoch < numberOfLookBackEpochs; epoch++) {
            long epochId = currentEpochId - 1 - epoch;
            Topic topic = topics[(int) epoch];

            expectedReturnedTopics.put(epochId, Map.of(appSdkCaller, topic, appOnlyCaller, topic));
        }
        assertThat(mTopicsDao.retrieveReturnedTopics(currentEpochId - 1, numberOfLookBackEpochs))
                .isEqualTo(expectedReturnedTopics);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testAssignTopicsToSdkForAppInstallation_NonSdk() {
        final String app = "app";
        final String sdk = ""; // App calls Topics API directly
        final int numberOfLookBackEpochs = 3;
        final long currentEpochId = 5L;
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;

        Pair<String, String> appOnlyCaller = Pair.create(app, /* sdk */ "");

        Topic topic1 = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(/* topic */ 2, taxonomyVersion, modelVersion);
        Topic topic3 = Topic.create(/* topic */ 3, taxonomyVersion, modelVersion);
        Topic[] topics = {topic1, topic2, topic3};

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        for (long epoch = 0; epoch < numberOfLookBackEpochs; epoch++) {
            long epochId = currentEpochId - 1 - epoch;
            Topic topic = topics[(int) epoch];

            mTopicsDao.persistReturnedAppTopicsMap(epochId, Map.of(appOnlyCaller, topic));
            mTopicsDao.persistCallerCanLearnTopics(epochId - 1, Map.of(topic, Set.of(sdk)));
        }

        // No topic will be assigned even though app itself has returned topics
        assertFalse(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(app, sdk, currentEpochId));
    }

    @Test
    public void testAssignTopicsToSdkForAppInstallation_unsatisfiedApp() {
        final String app = "app";
        final String sdk = "sdk";
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        final int numberOfLookBackEpochs = 1;

        Pair<String, String> appOnlyCaller = Pair.create(app, /* sdk */ "");
        Pair<String, String> otherAppOnlyCaller = Pair.create("otherApp", /* sdk */ "");
        Pair<String, String> appSdkCaller = Pair.create(app, sdk);

        Topic topic = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // For Epoch 2, no topic will be assigned to app because Epoch 1 doesn't have any
        assertFalse(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(
                        app, sdk, /* currentEpochId */ 3L));

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, Map.of(otherAppOnlyCaller, topic));
        mTopicsDao.persistCallerCanLearnTopics(/* epochId */ 2L, Map.of(topic, Set.of(sdk)));

        // Epoch 2 won't be assigned topics as app doesn't have a returned Topic
        assertFalse(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(
                        app, sdk, /* currentEpochId */ 3L));

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, Map.of(appOnlyCaller, topic));

        assertTrue(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(
                        app, sdk, /* currentEpochId */ 3L));
        assertThat(mTopicsDao.retrieveReturnedTopics(/* epochId */ 2L, numberOfLookBackEpochs))
                .isEqualTo(
                        Map.of(
                                /* epochId */ 2L,
                                Map.of(
                                        appOnlyCaller,
                                        topic,
                                        appSdkCaller,
                                        topic,
                                        otherAppOnlyCaller,
                                        topic)));
    }

    @Test
    public void testAssignTopicsToSdkForAppInstallation_unsatisfiedSdk() {
        final String app = "app";
        final String sdk = "sdk";
        final String otherSDK = "otherSdk";
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        final int numberOfLookBackEpochs = 1;

        Pair<String, String> appOnlyCaller = Pair.create(app, /* sdk */ "");
        Pair<String, String> appSdkCaller = Pair.create(app, sdk);

        Topic topic = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, Map.of(appOnlyCaller, topic));

        // No topic will be assigned as topic is not learned in past epochs
        assertFalse(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(
                        app, sdk, /* currentEpochId */ 3L));

        mTopicsDao.persistCallerCanLearnTopics(/* epochId */ 3L, Map.of(topic, Set.of(sdk)));

        // No topic will be assigned as topic is only learned in current Epoch 3
        assertFalse(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(
                        app, sdk, /* currentEpochId */ 3L));

        mTopicsDao.persistCallerCanLearnTopics(/* epochId */ 2L, Map.of(topic, Set.of(otherSDK)));

        // No topic will be assigned as topic is not learned by "sdk" in past epochs
        assertFalse(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(
                        app, sdk, /* currentEpochId */ 3L));

        mTopicsDao.persistCallerCanLearnTopics(/* epochId */ 2L, Map.of(topic, Set.of(sdk)));

        // Topic will be assigned as both app and sdk are satisfied
        assertTrue(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(
                        app, sdk, /* currentEpochId */ 3L));
        assertThat(mTopicsDao.retrieveReturnedTopics(/* epochId */ 2L, numberOfLookBackEpochs))
                .isEqualTo(
                        Map.of(
                                /* epochId */ 2L,
                                Map.of(appOnlyCaller, topic, appSdkCaller, topic)));
    }
}
