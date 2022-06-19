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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link com.android.adservices.service.topics.AppUpdateManager} */
public class AppUpdateManagerTest {
    @SuppressWarnings({"unused"})
    private static final String TAG = "AppInstallationInfoManagerTest";

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private final DbHelper mDbHelper = DbTestUtil.getDbHelperForTest();

    private AppUpdateManager mAppUpdateManager;
    private TopicsDao mTopicsDao;

    @Mock PackageManager mMockPackageManager;

    @Before
    public void setup() {
        // In order to mock Package Manager, context also needs to be mocked to return
        // mocked Package Manager
        mMockPackageManager = Mockito.mock(PackageManager.class);
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

        mAppUpdateManager = new AppUpdateManager(mTopicsDao);
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
}
