/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_CLEAR_ALL_BLOCKED_TOPICS_IN_SYSTEM_SERVER_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_GET_BLOCKED_TOPIC_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_RECORD_BLOCKED_TOPICS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_REMOVE_BLOCKED_TOPIC_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.adservices.service.topics.BlockedTopicsManager.SHARED_PREFS_BLOCKED_TOPICS;
import static com.android.adservices.service.topics.BlockedTopicsManager.SHARED_PREFS_KEY_HAS_MIGRATED;
import static com.android.adservices.service.topics.BlockedTopicsManager.SHARED_PREFS_KEY_PPAPI_HAS_CLEARED;
import static com.android.adservices.service.topics.BlockedTopicsManager.handleBlockedTopicsMigrationIfNeeded;
import static com.android.adservices.service.topics.BlockedTopicsManager.mayClearPpApiBlockedTopics;
import static com.android.adservices.service.topics.BlockedTopicsManager.mayMigratePpApiBlockedTopicsToSystemService;
import static com.android.adservices.service.topics.BlockedTopicsManager.resetSharedPreference;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.app.adservices.AdServicesManager;
import android.app.adservices.IAdServicesManager;
import android.app.adservices.topics.TopicParcel;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.appsearch.AppSearchConsentManager;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;

/** Unit test to test class {@link com.android.adservices.service.topics.BlockedTopicsManager} */
@SetErrorLogUtilDefaultParams(
        throwable = IllegalStateException.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS)
public final class BlockedTopicsManagerTest extends AdServicesExtendedMockitoTestCase {
    private static final long TAXONOMY_VERSION = 1L;
    private static final long MODEL_VERSION = 1L;
    private static final Topic TOPIC =
            Topic.create(/* topicId */ 1, TAXONOMY_VERSION, MODEL_VERSION);

    private final DbHelper mDBHelper = DbTestUtil.getDbHelperForTest();
    private final TopicsDao mTopicsDao = new TopicsDao(mDBHelper);

    private AdServicesManager mAdServicesManager;
    @Mock private AppSearchConsentManager mAppSearchConsentManager;

    @Mock private IAdServicesManager mMockIAdServicesManager;

    @Before
    public void setup() {
        DbTestUtil.deleteTable(TopicsTables.BlockedTopicsContract.TABLE);

        mAdServicesManager = new AdServicesManager(mMockIAdServicesManager);
        doReturn(mAdServicesManager).when(mSpyContext).getSystemService(AdServicesManager.class);
    }

    @Test
    public void testTopicParcelCreation() {
        TopicParcel topicParcelFromParcel = TopicParcel.CREATOR.createFromParcel(Parcel.obtain());
        TopicParcel[] topicParcels = TopicParcel.CREATOR.newArray(2);
        topicParcelFromParcel.writeToParcel(Parcel.obtain(), 0);

        assertThat(topicParcelFromParcel.describeContents()).isEqualTo(0);
        assertThat(topicParcels[0]).isNull();
    }

    @Test
    public void testBlockUnblockRetrieveBlockedTopics_PpapiOnly() throws Exception {
        int blockedTopicsSourceOfTruth = Flags.PPAPI_ONLY;

        // Block a topic
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        blockedTopicsSourceOfTruth, /* enableAppSearchConsent= */ false);
        blockedTopicsManager.blockTopic(TOPIC);

        // Verify the topic is blocked
        List<Topic> expectedBlockedTopics = blockedTopicsManager.retrieveAllBlockedTopics();
        assertThat(expectedBlockedTopics).hasSize(1);
        assertThat(expectedBlockedTopics.get(0)).isEqualTo(TOPIC);

        // Verify the topic is unblocked
        blockedTopicsManager.unblockTopic(TOPIC);
        assertThat(blockedTopicsManager.retrieveAllBlockedTopics()).isEmpty();
    }

    @Test
    public void testBlockUnblockRetrieveBlockedTopics_SystemServerOnly() throws Exception {
        int blockedTopicsSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;

        // Block a topic
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        blockedTopicsSourceOfTruth, /* enableAppSearchConsent= */ false);
        blockedTopicsManager.blockTopic(TOPIC);
        verify(mMockIAdServicesManager).recordBlockedTopic(any());

        // Verify the topic is blocked
        List<Topic> expectedBlockedTopics = blockedTopicsManager.retrieveAllBlockedTopics();
        assertThat(expectedBlockedTopics).hasSize(1);
        assertThat(expectedBlockedTopics.get(0)).isEqualTo(TOPIC);
        verify(mMockIAdServicesManager).retrieveAllBlockedTopics();

        // Verify the topic is unblocked
        blockedTopicsManager.unblockTopic(TOPIC);
        verify(mMockIAdServicesManager).removeBlockedTopic(TOPIC.convertTopicToTopicParcel());
    }

    @Test
    public void testBlockUnblockRetrieveBlockedTopics_PpapiAndSystemServer() throws Exception {
        int blockedTopicsSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;

        // Block a topic
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        blockedTopicsSourceOfTruth, /* enableAppSearchConsent= */ false);
        blockedTopicsManager.blockTopic(TOPIC);
        verify(mMockIAdServicesManager)
                .recordBlockedTopic(List.of(TOPIC.convertTopicToTopicParcel()));

        // Verify the topic is blocked
        List<Topic> expectedBlockedTopics = blockedTopicsManager.retrieveAllBlockedTopics();
        assertThat(expectedBlockedTopics).hasSize(1);
        assertThat(expectedBlockedTopics.get(0)).isEqualTo(TOPIC);
        verify(mMockIAdServicesManager).retrieveAllBlockedTopics();
        // Also verify PPAPI has recorded this topic
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).hasSize(1);
        assertThat(mTopicsDao.retrieveAllBlockedTopics().get(0)).isEqualTo(TOPIC);

        // Verify the topic is unblocked
        blockedTopicsManager.unblockTopic(TOPIC);
        verify(mMockIAdServicesManager).removeBlockedTopic(TOPIC.convertTopicToTopicParcel());
        // Also verify PPAPI has removed this topic
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isEmpty();
    }

    @Test
    public void testBlockUnblockRetrieveBlockedTopics_AppSearchOnly() throws Exception {
        int blockedTopicsSourceOfTruth = Flags.APPSEARCH_ONLY;

        // Block a topic.
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        blockedTopicsSourceOfTruth, /* enableAppSearchConsent= */ true);
        blockedTopicsManager.blockTopic(TOPIC);
        verify(mAppSearchConsentManager).blockTopic(TOPIC);

        // Unblock a topic.
        blockedTopicsManager.unblockTopic(TOPIC);
        verify(mAppSearchConsentManager).unblockTopic(TOPIC);

        // Get all blocked topics.
        when(mAppSearchConsentManager.retrieveAllBlockedTopics()).thenReturn(List.of(TOPIC));
        List<Topic> result = blockedTopicsManager.retrieveAllBlockedTopics();
        assertThat(result).isEqualTo(List.of(TOPIC));

        // Clear all blocked topics.
        blockedTopicsManager.clearAllBlockedTopics();
        verify(mAppSearchConsentManager).clearAllBlockedTopics();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_RECORD_BLOCKED_TOPICS_FAILURE)
    public void testBlockTopic_withAppSearchOnly_throwsException() throws Exception {
        Exception thrown = new IllegalStateException("test");
        ExtendedMockito.doThrow(thrown).when(mAppSearchConsentManager).blockTopic(any());
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        Flags.APPSEARCH_ONLY, /* enableAppSearchConsent= */ true);

        Exception e =
                assertThrows(RuntimeException.class, () -> blockedTopicsManager.blockTopic(TOPIC));
        assertThat(e).hasCauseThat().isSameInstanceAs(thrown);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_REMOVE_BLOCKED_TOPIC_FAILURE)
    public void testUnblockTopic_withAppSearchOnly_throwsException() throws Exception {
        Exception thrown = new IllegalStateException("test");
        ExtendedMockito.doThrow(thrown).when(mAppSearchConsentManager).unblockTopic(any());
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        Flags.APPSEARCH_ONLY, /* enableAppSearchConsent= */ true);

        Exception e =
                assertThrows(
                        RuntimeException.class, () -> blockedTopicsManager.unblockTopic(TOPIC));
        assertThat(e).hasCauseThat().isSameInstanceAs(thrown);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_GET_BLOCKED_TOPIC_FAILURE)
    public void testRetrieveAllBlockedTopics_withAppSearchOnly_throwsException() throws Exception {
        Exception thrown = new IllegalStateException("test");
        ExtendedMockito.doThrow(thrown).when(mAppSearchConsentManager).retrieveAllBlockedTopics();
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        Flags.APPSEARCH_ONLY, /* enableAppSearchConsent= */ true);

        Exception e =
                assertThrows(
                        RuntimeException.class, blockedTopicsManager::retrieveAllBlockedTopics);
        assertThat(e).hasCauseThat().isSameInstanceAs(thrown);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_CLEAR_ALL_BLOCKED_TOPICS_IN_SYSTEM_SERVER_FAILURE)
    public void testClearAllBlockedTopics_withAppSearchOnly_throwsException() throws Exception {
        Exception thrown = new IllegalStateException("test");
        ExtendedMockito.doThrow(thrown).when(mAppSearchConsentManager).clearAllBlockedTopics();
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        Flags.APPSEARCH_ONLY, /* enableAppSearchConsent= */ true);

        Exception e =
                assertThrows(RuntimeException.class, blockedTopicsManager::clearAllBlockedTopics);
        assertThat(e).hasCauseThat().isSameInstanceAs(thrown);
    }

    @Test
    public void testClearAllBlockedTopicsInSystemServiceIfNeeded_PpApiOnly() throws Exception {
        int blockedTopicsSourceOfTruth = Flags.PPAPI_ONLY;

        // Block a topic
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        blockedTopicsSourceOfTruth, /* enableAppSearchConsent= */ false);
        blockedTopicsManager.blockTopic(TOPIC);

        // Verify the topic is blocked
        List<Topic> expectedBlockedTopics = blockedTopicsManager.retrieveAllBlockedTopics();
        assertThat(expectedBlockedTopics).hasSize(1);
        assertThat(expectedBlockedTopics.get(0)).isEqualTo(TOPIC);

        // Verify the topic in PPAPI is not unblocked
        blockedTopicsManager.clearAllBlockedTopics();
        expectedBlockedTopics = blockedTopicsManager.retrieveAllBlockedTopics();
        assertThat(expectedBlockedTopics).hasSize(1);
        assertThat(expectedBlockedTopics.get(0)).isEqualTo(TOPIC);

        // Verify clearAllBlockedTopics() is not invoked
        verify(mMockIAdServicesManager, never()).clearAllBlockedTopics();
    }

    @Test
    public void testClearAllBlockedTopicsInSystemServiceIfNeeded_SystemServerOnly()
            throws Exception {
        int blockedTopicsSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;

        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        blockedTopicsSourceOfTruth, /* enableAppSearchConsent= */ false);

        blockedTopicsManager.clearAllBlockedTopics();

        // Verify clearAllBlockedTopics() is invoked
        verify(mMockIAdServicesManager).clearAllBlockedTopics();
    }

    @Test
    public void testClearAllBlockedTopicsInSystemServiceIfNeeded_PpApiAndSystemServer()
            throws Exception {
        int blockedTopicsSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;

        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        blockedTopicsSourceOfTruth, /* enableAppSearchConsent= */ false);

        blockedTopicsManager.clearAllBlockedTopics();

        // Verify clearAllBlockedTopics() is invoked
        verify(mMockIAdServicesManager).clearAllBlockedTopics();
    }

    @Test
    public void testResetSharedPreference() {
        SharedPreferences sharedPreferences =
                mSpyContext.getSharedPreferences(SHARED_PREFS_BLOCKED_TOPICS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putBoolean(SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, true);
        editor.putBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, true);
        editor.commit();

        assertThat(
                        sharedPreferences.getBoolean(
                                SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, /* defValue */ false))
                .isTrue();
        assertThat(
                        sharedPreferences.getBoolean(
                                SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue */ false))
                .isTrue();

        resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_PPAPI_HAS_CLEARED);
        resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED);

        assertThat(
                        sharedPreferences.getBoolean(
                                SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, /* defValue */ false))
                .isFalse();
        assertThat(
                        sharedPreferences.getBoolean(
                                SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue */ false))
                .isFalse();
    }

    @Test
    public void testMayMigratePpApiBlockedTopicsToSystemService() throws Exception {
        doNothing()
                .when(mMockIAdServicesManager)
                .recordBlockedTopic(List.of(TOPIC.convertTopicToTopicParcel()));

        mTopicsDao.recordBlockedTopic(TOPIC);

        mayMigratePpApiBlockedTopicsToSystemService(mSpyContext, mTopicsDao, mAdServicesManager);
        verify(mMockIAdServicesManager)
                .recordBlockedTopic(List.of(TOPIC.convertTopicToTopicParcel()));

        // Verify this should only happen once
        mayMigratePpApiBlockedTopicsToSystemService(mSpyContext, mTopicsDao, mAdServicesManager);
        verify(mMockIAdServicesManager)
                .recordBlockedTopic(List.of(TOPIC.convertTopicToTopicParcel()));

        // Clear shared preference
        resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED);
    }

    @Test
    public void testMayClearPpApiBlockedTopics() {
        mTopicsDao.recordBlockedTopic(TOPIC);

        mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao);
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isEmpty();

        // Verify this should only happen once
        mTopicsDao.recordBlockedTopic(TOPIC);
        mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao);
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isNotEmpty();

        // Clear shared preference
        resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_PPAPI_HAS_CLEARED);
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public void testHandleBlockedTopicsMigrationIfNeeded_PpApiOnly() {
        // Handle migration tests are only valid for T+.
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(BlockedTopicsManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        int blockedTopicsSourceOfTruth = Flags.PPAPI_ONLY;
        ExtendedMockito.doNothing()
                .when(() -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                mayMigratePpApiBlockedTopicsToSystemService(
                                        mSpyContext, mTopicsDao, mAdServicesManager));
        ExtendedMockito.doNothing().when(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao));

        handleBlockedTopicsMigrationIfNeeded(
                mSpyContext, mTopicsDao, mAdServicesManager, blockedTopicsSourceOfTruth);

        verify(() -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        verify(
                () ->
                        mayMigratePpApiBlockedTopicsToSystemService(
                                mSpyContext, mTopicsDao, mAdServicesManager),
                times(0));
        verify(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao), times(0));

        session.finishMocking();
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public void testHandleBlockedTopicsMigrationIfNeeded_SystemServerOnly() {
        // Handle migration tests are only valid for T+.
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(BlockedTopicsManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        int blockedTopicsSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ExtendedMockito.doNothing()
                .when(() -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                mayMigratePpApiBlockedTopicsToSystemService(
                                        mSpyContext, mTopicsDao, mAdServicesManager));
        ExtendedMockito.doNothing().when(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao));

        handleBlockedTopicsMigrationIfNeeded(
                mSpyContext, mTopicsDao, mAdServicesManager, blockedTopicsSourceOfTruth);

        verify(() -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED), times(0));
        verify(
                () ->
                        mayMigratePpApiBlockedTopicsToSystemService(
                                mSpyContext, mTopicsDao, mAdServicesManager));
        verify(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao));

        session.finishMocking();
    }

    @Test
    @RequiresSdkLevelAtLeastT
    @SpyStatic(BlockedTopicsManager.class)
    public void testHandleBlockedTopicsMigrationIfNeeded_PpApiAndSystemServer() {
        int blockedTopicsSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ExtendedMockito.doNothing()
                .when(() -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                mayMigratePpApiBlockedTopicsToSystemService(
                                        mSpyContext, mTopicsDao, mAdServicesManager));
        ExtendedMockito.doNothing().when(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao));

        handleBlockedTopicsMigrationIfNeeded(
                mSpyContext, mTopicsDao, mAdServicesManager, blockedTopicsSourceOfTruth);

        verify(() -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED), times(0));
        verify(
                () ->
                        mayMigratePpApiBlockedTopicsToSystemService(
                                mSpyContext, mTopicsDao, mAdServicesManager));
        verify(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao), times(0));
    }

    private BlockedTopicsManager getSpiedBlockedTopicsManager(
            int blockedTopicsSourceOfTruth, boolean enableAppSearchConsent) throws Exception {
        BlockedTopicsManager blockedTopicsManager =
                new BlockedTopicsManager(
                        mTopicsDao,
                        mAdServicesManager,
                        mAppSearchConsentManager,
                        blockedTopicsSourceOfTruth,
                        enableAppSearchConsent);

        // Disable IPC calls
        doNothing()
                .when(mMockIAdServicesManager)
                .recordBlockedTopic(List.of(TOPIC.convertTopicToTopicParcel()));
        doNothing()
                .when(mMockIAdServicesManager)
                .removeBlockedTopic(TOPIC.convertTopicToTopicParcel());
        doReturn(List.of(TOPIC.convertTopicToTopicParcel()))
                .when(mMockIAdServicesManager)
                .retrieveAllBlockedTopics();
        doNothing().when(mMockIAdServicesManager).clearAllBlockedTopics();

        return blockedTopicsManager;
    }

    @Test
    @SpyStatic(BlockedTopicsManager.class)
    public void testHandleBlockedTopicsMigrationIfNeeded_ExtServices() {
        ExtendedMockito.doNothing()
                .when(() -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                mayMigratePpApiBlockedTopicsToSystemService(
                                        mSpyContext, mTopicsDao, mAdServicesManager));
        ExtendedMockito.doNothing().when(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao));
        ExtendedMockito.doReturn("com." + AdServicesCommon.ADEXTSERVICES_PACKAGE_NAME_SUFFIX)
                .when(mSpyContext)
                .getPackageName();

        handleBlockedTopicsMigrationIfNeeded(mSpyContext, mTopicsDao, mAdServicesManager, 2);

        verify(() -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED), never());
        verify(
                () ->
                        mayMigratePpApiBlockedTopicsToSystemService(
                                mSpyContext, mTopicsDao, mAdServicesManager),
                never());
        verify(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao), never());
    }
}
