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

package com.android.server.adservices;

import static com.android.adservices.service.CommonDebugFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.adservices.shared.testing.common.FileHelper.deleteDirectory;
import static com.android.server.adservices.PhFlags.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.adservices.AdServicesManager;
import android.app.adservices.consent.ConsentParcel;
import android.app.adservices.topics.TopicParcel;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.VersionedPackage;
import android.content.rollback.RollbackManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.ui.enrollment.collection.GaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.U18UxEnrollmentChannelCollection;
import com.android.adservices.shared.system.SystemContextSingleton;
import com.android.adservices.shared.testing.annotations.DisableDebugFlag;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.modules.utils.testing.TestableDeviceConfig;
import com.android.server.adservices.consent.AppConsentManagerFixture;
import com.android.server.adservices.data.topics.TopicsDao;
import com.android.server.adservices.data.topics.TopicsDbHelper;
import com.android.server.adservices.data.topics.TopicsDbTestUtil;
import com.android.server.adservices.data.topics.TopicsTables;
import com.android.server.adservices.feature.PrivacySandboxEnrollmentChannelCollection;
import com.android.server.adservices.feature.PrivacySandboxFeatureType;
import com.android.server.adservices.feature.PrivacySandboxUxCollection;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Tests for {@link AdServicesManagerService} */
public final class AdServicesManagerServiceTest extends AdServicesExtendedMockitoTestCase {

    private static final String PPAPI_PACKAGE_NAME = "com.google.android.adservices.api";
    private static final String ADSERVICES_APEX_PACKAGE_NAME = "com.google.android.adservices";
    private static final String PACKAGE_NAME = "com.package.example";
    private static final String PACKAGE_CHANGED_BROADCAST =
            "com.android.adservices.PACKAGE_CHANGED";
    private static final String PACKAGE_FULLY_REMOVED = "package_fully_removed";
    private static final String PACKAGE_ADDED = "package_added";
    private static final String PACKAGE_DATA_CLEARED = "package_data_cleared";
    private static final long TAXONOMY_VERSION = 1L;
    private static final long MODEL_VERSION = 1L;
    private static final int PACKAGE_UID = 12345;
    private static final Context PPAPI_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String BASE_DIR = PPAPI_CONTEXT.getFilesDir().getAbsolutePath();
    private static final int TEST_ROLLED_BACK_FROM_MODULE_VERSION = 339990000;
    private static final int TEST_ROLLED_BACK_TO_MODULE_VERSION = 330000000;
    private static final int ROLLBACK_ID = 1768705420;
    private static final String USER_INSTANCE_MANAGER_DUMP = "D'OHump!";

    // TODO(b/294423183): figure out why it cannot adopt shell permissions (otherwise some tests
    // will fail due to lack of android.permission.INTERACT_ACROSS_USERS_FULL.
    @Rule(order = 11)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.withoutAdoptingShellPermissions().setDefaultLogcatTags();

    private final List<AdServicesManagerService> mServices = new ArrayList<>();
    private final TopicsDbHelper mDBHelper = TopicsDbTestUtil.getDbHelperForTest();
    private UserInstanceManager mUserInstanceManager;
    @Mock private PackageManager mMockPackageManager;
    @Mock private RollbackManager mMockRollbackManager;

    @Override
    protected AdServicesExtendedMockitoRule getAdServicesExtendedMockitoRule() {
        return newDefaultAdServicesExtendedMockitoRuleBuilder()
                .addStaticMockFixtures(TestableDeviceConfig::new)
                .build();
    }

    @Before
    public void setup() throws Exception {
        TopicsDao topicsDao = new TopicsDao(mDBHelper);
        File baseDir = mContext.getFilesDir();
        String basePath = baseDir.getAbsolutePath();
        deleteDirectory(baseDir);
        mUserInstanceManager =
                new UserInstanceManager(topicsDao, basePath) {
                    @Override
                    public void dump(PrintWriter writer, String[] args) {
                        writer.println(USER_INSTANCE_MANAGER_DUMP);
                    }
                };

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        doReturn(mMockPackageManager).when(mSpyContext).getPackageManager();
        doReturn(mMockRollbackManager).when(mSpyContext).getSystemService(RollbackManager.class);
    }

    // TODO(b/343741206): Remove suppress warning once the lint is fixed.
    @SuppressWarnings("VisibleForTests")
    @After
    public void tearDown() {
        mServices.forEach(AdServicesManagerService::tearDownForTesting);

        // Clear BlockedTopics table in the database.
        TopicsDbTestUtil.deleteTable(TopicsTables.BlockedTopicsContract.TABLE);
    }

    @Test
    public void testAdServicesSystemService_enabled_then_disabled() throws Exception {
        // First enable the flag.
        setFlag(KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED, true);

        // This will trigger the registration of the Receiver.
        AdServicesManagerService service = newService();

        ArgumentCaptor<BroadcastReceiver> argumentReceiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        ArgumentCaptor<IntentFilter> argumentIntentFilter =
                ArgumentCaptor.forClass(IntentFilter.class);
        ArgumentCaptor<String> argumentPermission = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Handler> argumentHandler = ArgumentCaptor.forClass(Handler.class);

        // Calling the second time will not register again.
        service.registerReceivers();

        // We have 2 receivers which are PackageChangeReceiver and UserActionReceiver.
        int numReceivers = 2;
        // The flag is enabled so we call registerReceiverForAllUsers
        verify(mSpyContext, times(numReceivers))
                .registerReceiverForAllUsers(
                        argumentReceiver.capture(),
                        argumentIntentFilter.capture(),
                        argumentPermission.capture(),
                        argumentHandler.capture());

        List<BroadcastReceiver> receiverList = argumentReceiver.getAllValues();
        assertThat(receiverList).hasSize(numReceivers);

        // Validate PackageChangeReceiver
        List<IntentFilter> intentFilterList = argumentIntentFilter.getAllValues();
        IntentFilter packageIntentFilter = intentFilterList.get(0);
        assertThat(packageIntentFilter.hasAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)).isTrue();
        assertThat(packageIntentFilter.hasAction(Intent.ACTION_PACKAGE_DATA_CLEARED)).isTrue();
        assertThat(packageIntentFilter.hasAction(Intent.ACTION_PACKAGE_ADDED)).isTrue();
        assertThat(packageIntentFilter.countActions()).isEqualTo(3);
        assertThat(packageIntentFilter.getDataScheme(0)).isEqualTo("package");

        assertThat(argumentPermission.getAllValues().get(0)).isNull();
        assertThat(argumentHandler.getAllValues().get(0)).isNotNull();

        // Validate UserActionReceiver
        IntentFilter userActionIntentFilter = intentFilterList.get(1);
        assertThat(userActionIntentFilter.hasAction(Intent.ACTION_USER_REMOVED)).isTrue();
        assertThat(userActionIntentFilter.countActions()).isEqualTo(1);
        assertThat(argumentPermission.getAllValues().get(1)).isNull();
        assertThat(argumentHandler.getAllValues().get(1)).isNotNull();

        // Now disable the flag.
        setFlag(KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED, false);

        // When flag value is changed above, then TestableDeviceConfig invokes the DeviceConfig
        // .OnPropertiesChangedListener. The listener is invoked on the separate thread. So, we
        // need to add a wait time to ensure the listener gets executed. If listener gets
        // executed after the test is finished, we hit READ_DEVICE_CONFIG exception.
        // TODO(b/368342138): improve this by using a (new) SyncCallback that blocks until the given
        // prop->value change is received. Notice that such callback alone would not be enough right
        // now, as TestableDeviceConfig uses a HashMap to set the listeners, so there's no
        // guarantee about the order they'd be called - we'd need to change it to use a
        // LinkedHashMap instead first
        Thread.sleep(500);

        // Calling when the flag is disabled will unregister the Receiver!
        service.registerReceivers();
        verify(mSpyContext, times(numReceivers)).unregisterReceiver(argumentReceiver.capture());

        // The unregistered is called on the same receiver when registered above.
        assertThat(argumentReceiver.getAllValues().get(0)).isSameInstanceAs(receiverList.get(0));
        assertThat(argumentReceiver.getAllValues().get(1)).isSameInstanceAs(receiverList.get(1));
    }

    @Test
    public void testAdServicesSystemService_disabled() {
        // Disable the flag.
        setFlag(KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED, false);

        AdServicesManagerService service = newService();

        // The flag is disabled so there is no registerReceiverForAllUsers
        verify(mSpyContext, never())
                .registerReceiverForAllUsers(
                        any(BroadcastReceiver.class),
                        any(IntentFilter.class),
                        any(String.class),
                        any(Handler.class));
    }

    @Test
    public void testAdServicesSystemService_enabled_setAdServicesApexVersion() {
        // First enable the flag.
        setFlag(KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED, true);

        setupMockInstalledPackages();

        // This will trigger the lookup of the AdServices version.
        AdServicesManagerService service = newService();

        ArgumentCaptor<PackageManager.PackageInfoFlags> argumentPackageInfoFlags =
                ArgumentCaptor.forClass(PackageManager.PackageInfoFlags.class);

        verify(mSpyContext).getPackageManager();

        verify(mMockPackageManager).getInstalledPackages(argumentPackageInfoFlags.capture());

        assertThat(argumentPackageInfoFlags.getAllValues().get(0).getValue())
                .isEqualTo(PackageManager.MATCH_APEX);

        assertThat(service.getAdServicesApexVersion())
                .isEqualTo(TEST_ROLLED_BACK_FROM_MODULE_VERSION);
    }

    @Test
    public void testAdServicesSystemService_disabled_setAdServicesApexVersion() {
        // Disable the flag.
        setFlag(KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED, false);

        AdServicesManagerService service = newService();

        // The flag is disabled so there is no call to the packageManager
        verify(mSpyContext, never()).getPackageManager();
    }

    @Test
    @DisableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
    public void testAdServicesShellCommand_disabled() throws Exception {
        String expectedOutput = handleShellCommand(new Binder());

        AdServicesManagerService service = newService();

        String result = handleShellCommand(service);

        assertWithMessage("shell command output").that(result).contains(expectedOutput);
    }

    @Test
    @EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
    public void testAdServicesShellCommand_enabled() throws Exception {
        AdServicesManagerService service = newService();
        String expectedOutput =
                String.format(AdServicesShellCommand.WRONG_UID_TEMPLATE, Binder.getCallingUid());

        String result = handleShellCommand(service);

        assertWithMessage("shell command output").that(result).contains(expectedOutput);
    }

    @Test
    public void testSendBroadcastForPackageFullyRemoved() {
        AdServicesManagerService service = newService();

        Intent i = new Intent(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        i.setData(Uri.parse("package:" + PACKAGE_NAME));
        i.putExtra(Intent.EXTRA_UID, PACKAGE_UID);

        ArgumentCaptor<Intent> argumentIntent = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> argumentUser = ArgumentCaptor.forClass(UserHandle.class);

        setupMockResolveInfo();
        doNothing().when(mSpyContext).sendBroadcastAsUser(any(), any());

        service.onPackageChange(i, mSpyContext.getUser());

        verify(mSpyContext).sendBroadcastAsUser(argumentIntent.capture(), argumentUser.capture());

        assertThat(argumentIntent.getValue().getAction()).isEqualTo(PACKAGE_CHANGED_BROADCAST);
        assertThat(argumentIntent.getValue().getData()).isEqualTo(i.getData());
        assertThat(argumentIntent.getValue().getStringExtra("action"))
                .isEqualTo(PACKAGE_FULLY_REMOVED);
        assertThat(argumentIntent.getValue().getIntExtra(Intent.EXTRA_UID, -1))
                .isEqualTo(PACKAGE_UID);
        assertThat(argumentUser.getValue()).isEqualTo(mSpyContext.getUser());
    }

    @Test
    public void testOnUserRemoved() throws Exception {
        AdServicesManagerService service = newService();
        int userId = 1;
        String consentDataStoreDir = BASE_DIR + "/" + userId;
        Path packageDir = Paths.get(consentDataStoreDir);
        Intent intent = new Intent(Intent.ACTION_USER_REMOVED);
        intent.putExtra(Intent.EXTRA_USER, UserHandle.of(userId));
        mUserInstanceManager.getOrCreateUserConsentManagerInstance(userId);
        assertThat(Files.exists(packageDir)).isTrue();
        assertThat(mUserInstanceManager.getUserConsentManagerInstance(userId)).isNotNull();

        service.onUserRemoved(intent);

        assertThat(Files.exists(packageDir)).isFalse();
        assertThat(mUserInstanceManager.getUserConsentManagerInstance(userId)).isNull();
    }

    @Test
    public void testOnUserRemoved_userIdNotPresentInIntent() throws Exception {
        AdServicesManagerService service = newService();
        Intent intent = new Intent(Intent.ACTION_USER_REMOVED);
        // userId 1 is not present in the intent.
        int userId = 1;
        mUserInstanceManager.getOrCreateUserConsentManagerInstance(userId);
        assertThat(mUserInstanceManager.getUserConsentManagerInstance(userId)).isNotNull();

        service.onUserRemoved(intent);

        assertThat(mUserInstanceManager.getUserConsentManagerInstance(userId)).isNotNull();
    }

    @Test
    public void testOnUserRemoved_removeNonexistentUserId() throws Exception {
        AdServicesManagerService service = newService();
        Intent intent = new Intent(Intent.ACTION_USER_REMOVED);
        // userId 1 does not have consent directory.
        int userId = 1;
        intent.putExtra(Intent.EXTRA_USER, UserHandle.of(2));

        service.onUserRemoved(intent);

        assertThat(mUserInstanceManager.getUserConsentManagerInstance(userId)).isNull();
    }

    @Test
    public void testClearAllBlockedTopics() {
        AdServicesManagerService service = spy(newService());
        disableEnforceAdServicesManagerPermission(service);

        final int topicId = 1;

        TopicParcel topicParcel =
                new TopicParcel.Builder()
                        .setTopicId(topicId)
                        .setTaxonomyVersion(TAXONOMY_VERSION)
                        .setModelVersion(MODEL_VERSION)
                        .build();
        service.recordBlockedTopic(List.of(topicParcel));

        //  Verify the topic is recorded.
        List<TopicParcel> resultTopicParcels = service.retrieveAllBlockedTopics();
        assertThat(resultTopicParcels).hasSize(1);
        assertThat(resultTopicParcels.get(0)).isEqualTo(topicParcel);

        // Verify the topic is  removed
        service.clearAllBlockedTopics();
        assertThat(service.retrieveAllBlockedTopics()).isEmpty();
    }

    @Test
    public void testSendBroadcastForPackageAdded() {
        AdServicesManagerService service = newService();

        Intent i = new Intent(Intent.ACTION_PACKAGE_ADDED);
        i.setData(Uri.parse("package:" + PACKAGE_NAME));
        i.putExtra(Intent.EXTRA_UID, PACKAGE_UID);
        i.putExtra(Intent.EXTRA_REPLACING, false);

        ArgumentCaptor<Intent> argumentIntent = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> argumentUser = ArgumentCaptor.forClass(UserHandle.class);

        setupMockResolveInfo();
        doNothing().when(mSpyContext).sendBroadcastAsUser(any(), any());

        service.onPackageChange(i, mSpyContext.getUser());

        verify(mSpyContext).sendBroadcastAsUser(argumentIntent.capture(), argumentUser.capture());

        assertThat(argumentIntent.getValue().getAction()).isEqualTo(PACKAGE_CHANGED_BROADCAST);
        assertThat(argumentIntent.getValue().getData()).isEqualTo(i.getData());
        assertThat(argumentIntent.getValue().getStringExtra("action")).isEqualTo(PACKAGE_ADDED);
        assertThat(argumentIntent.getValue().getIntExtra(Intent.EXTRA_UID, -1))
                .isEqualTo(PACKAGE_UID);
        assertThat(argumentUser.getValue()).isEqualTo(mSpyContext.getUser());
    }

    @Test
    public void testSendBroadcastForPackageDataCleared() {
        AdServicesManagerService service = newService();

        Intent i = new Intent(Intent.ACTION_PACKAGE_DATA_CLEARED);
        i.setData(Uri.parse("package:" + PACKAGE_NAME));
        i.putExtra(Intent.EXTRA_UID, PACKAGE_UID);

        ArgumentCaptor<Intent> argumentIntent = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> argumentUser = ArgumentCaptor.forClass(UserHandle.class);

        setupMockResolveInfo();
        doNothing().when(mSpyContext).sendBroadcastAsUser(any(), any());

        service.onPackageChange(i, mSpyContext.getUser());

        verify(mSpyContext).sendBroadcastAsUser(argumentIntent.capture(), argumentUser.capture());

        assertThat(argumentIntent.getValue().getAction()).isEqualTo(PACKAGE_CHANGED_BROADCAST);
        assertThat(argumentIntent.getValue().getData()).isEqualTo(i.getData());
        assertThat(argumentIntent.getValue().getStringExtra("action"))
                .isEqualTo(PACKAGE_DATA_CLEARED);
        assertThat(argumentIntent.getValue().getIntExtra(Intent.EXTRA_UID, -1))
                .isEqualTo(PACKAGE_UID);
        assertThat(argumentUser.getValue()).isEqualTo(mSpyContext.getUser());
    }

    @Test
    public void testGetConsent_unSet() throws Exception {
        AdServicesManagerService service = spy(newService());
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // Newly initialized ConsentManager has consent = false.
        assertThat(service.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();
        assertThat(service.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();
        assertThat(service.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();
        assertThat(service.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();
    }

    @Test
    public void testGetAndSetConsent_null() throws Exception {
        AdServicesManagerService service = spy(newService());
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        service.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.ALL_API)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(service.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        service.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.TOPICS)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(service.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();

        service.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.FLEDGE)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(service.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();

        service.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.MEASUREMENT)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(service.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();
    }

    @Test
    public void testGetAndSetConsent_nonNull() {
        AdServicesManagerService service = spy(newService());
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        service.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API));
        assertThat(service.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        service.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(service.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();

        service.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.TOPICS));
        assertThat(service.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();

        service.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.TOPICS));
        assertThat(service.getConsent(ConsentParcel.TOPICS).isIsGiven()).isTrue();

        service.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE));
        assertThat(service.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();

        service.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE));
        assertThat(service.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isTrue();

        service.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.MEASUREMENT));
        assertThat(service.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();

        service.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.MEASUREMENT));
        assertThat(service.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isTrue();

        // Verify that all the setConsent calls were persisted by creating a new instance of
        // AdServicesManagerService, and it has the same value as the above instance.
        // Note: In general, AdServicesManagerService instance is a singleton obtained via
        // context.getSystemService(). However, when the system server restarts, there will be
        // another singleton instance of AdServicesManagerService. This test here verifies that
        // the Consents are persisted correctly across restarts.
        AdServicesManagerService service2 = spy(newService());
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service2);

        assertThat(service2.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();
        assertThat(service2.getConsent(ConsentParcel.TOPICS).isIsGiven()).isTrue();
        assertThat(service2.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isTrue();
        assertThat(service2.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isTrue();
    }

    @Test
    public void testRecordNotificationDisplayed() throws Exception {
        AdServicesManagerService service = spy(newService());
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // First, the notification displayed is false.
        assertThat(service.wasNotificationDisplayed()).isFalse();
        service.recordNotificationDisplayed(true);
        assertThat(service.wasNotificationDisplayed()).isTrue();
    }

    @Test
    public void testEnforceAdServicesManagerPermission() {
        AdServicesManagerService service = spy(newService());

        // Throw due to non-IPC call
        assertThrows(SecurityException.class, () -> service.getConsent(ConsentParcel.ALL_API));
    }

    @Test
    public void testRecordGaUxNotificationDisplayed() throws Exception {
        AdServicesManagerService service = spy(newService());
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // First, the notification displayed is false.
        assertThat(service.wasGaUxNotificationDisplayed()).isFalse();
        service.recordGaUxNotificationDisplayed(true);
        assertThat(service.wasGaUxNotificationDisplayed()).isTrue();
    }

    @Test
    public void testRecordPasNotificationDisplayed() {
        AdServicesManagerService service = spy(newService());
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // First, the notification displayed is false.
        assertThat(service.wasPasNotificationDisplayed()).isFalse();
        service.recordPasNotificationDisplayed(true);
        assertThat(service.wasPasNotificationDisplayed()).isTrue();
    }

    @Test
    public void testRecordPasNotificationOpened() {
        AdServicesManagerService service = spy(newService());
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // First, the notification opened is false.
        assertThat(service.wasPasNotificationOpened()).isFalse();
        service.recordPasNotificationOpened(true);
        assertThat(service.wasPasNotificationOpened()).isTrue();
    }

    @Test
    public void recordUserManualInteractionWithConsent() throws Exception {
        AdServicesManagerService service = spy(newService());
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // First, the topic consent page displayed is false.
        assertThat(service.getUserManualInteractionWithConsent()).isEqualTo(0);
        service.recordUserManualInteractionWithConsent(1);
        assertThat(service.getUserManualInteractionWithConsent()).isEqualTo(1);
    }

    @Test
    public void testSetAppConsent() {
        AdServicesManagerService service = spy(newService());
        disableEnforceAdServicesManagerPermission(service);

        service.setConsentForApp(
                AppConsentManagerFixture.APP10_PACKAGE_NAME,
                AppConsentManagerFixture.APP10_UID,
                false);
        assertFalse(
                service.isConsentRevokedForApp(
                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                        AppConsentManagerFixture.APP10_UID));

        service.setConsentForAppIfNew(
                AppConsentManagerFixture.APP20_PACKAGE_NAME,
                AppConsentManagerFixture.APP20_UID,
                false);
        assertFalse(
                service.isConsentRevokedForApp(
                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                        AppConsentManagerFixture.APP20_UID));

        service.setConsentForApp(
                AppConsentManagerFixture.APP20_PACKAGE_NAME,
                AppConsentManagerFixture.APP20_UID,
                true);
        assertTrue(
                service.isConsentRevokedForApp(
                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                        AppConsentManagerFixture.APP20_UID));
        assertTrue(
                service.setConsentForAppIfNew(
                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                        AppConsentManagerFixture.APP20_UID,
                        false));

        assertFalse(
                service.setConsentForAppIfNew(
                        AppConsentManagerFixture.APP30_PACKAGE_NAME,
                        AppConsentManagerFixture.APP30_UID,
                        false));

        assertThat(
                        service.getKnownAppsWithConsent(
                                List.of(
                                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(2);
        assertThat(
                        service.getAppsWithRevokedConsent(
                                List.of(
                                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(1);
    }

    @Test
    public void testClearAppConsent() {
        AdServicesManagerService service = spy(newService());
        disableEnforceAdServicesManagerPermission(service);

        service.setConsentForApp(
                AppConsentManagerFixture.APP10_PACKAGE_NAME,
                AppConsentManagerFixture.APP10_UID,
                false);
        service.setConsentForApp(
                AppConsentManagerFixture.APP20_PACKAGE_NAME,
                AppConsentManagerFixture.APP20_UID,
                false);
        service.setConsentForApp(
                AppConsentManagerFixture.APP30_PACKAGE_NAME,
                AppConsentManagerFixture.APP30_UID,
                true);
        assertThat(
                        service.getKnownAppsWithConsent(
                                List.of(
                                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(2);
        assertThat(
                        service.getAppsWithRevokedConsent(
                                List.of(
                                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(1);

        service.clearConsentForUninstalledApp(
                AppConsentManagerFixture.APP10_PACKAGE_NAME, AppConsentManagerFixture.APP10_UID);
        assertThat(
                        service.getKnownAppsWithConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(1);
        assertThat(
                        service.getAppsWithRevokedConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(1);

        service.clearKnownAppsWithConsent();
        assertThat(
                        service.getKnownAppsWithConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(0);
        assertThat(
                        service.getAppsWithRevokedConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(1);

        service.setConsentForApp(
                AppConsentManagerFixture.APP20_PACKAGE_NAME,
                AppConsentManagerFixture.APP20_UID,
                false);
        assertThat(
                        service.getKnownAppsWithConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(1);
        assertThat(
                        service.getAppsWithRevokedConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(1);

        service.clearAllAppConsentData();
        assertThat(
                        service.getKnownAppsWithConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(0);
        assertThat(
                        service.getAppsWithRevokedConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(0);
    }

    @Test
    public void testRecordAndRetrieveBlockedTopic() {
        AdServicesManagerService service = spy(newService());
        disableEnforceAdServicesManagerPermission(service);

        final int topicId = 1;

        TopicParcel topicParcel =
                new TopicParcel.Builder()
                        .setTopicId(topicId)
                        .setTaxonomyVersion(TAXONOMY_VERSION)
                        .setModelVersion(MODEL_VERSION)
                        .build();
        service.recordBlockedTopic(List.of(topicParcel));

        //  Verify the topic is recorded.
        List<TopicParcel> resultTopicParcels = service.retrieveAllBlockedTopics();
        assertThat(resultTopicParcels).hasSize(1);
        assertThat(resultTopicParcels.get(0)).isEqualTo(topicParcel);
    }

    @Test
    public void testRecordAndRemoveBlockedTopic() {
        AdServicesManagerService service = spy(newService());
        disableEnforceAdServicesManagerPermission(service);

        final int topicId = 1;

        TopicParcel topicParcel =
                new TopicParcel.Builder()
                        .setTopicId(topicId)
                        .setTaxonomyVersion(TAXONOMY_VERSION)
                        .setModelVersion(MODEL_VERSION)
                        .build();
        service.recordBlockedTopic(List.of(topicParcel));

        //  Verify the topic is recorded.
        List<TopicParcel> resultTopicParcels = service.retrieveAllBlockedTopics();
        assertThat(resultTopicParcels).hasSize(1);
        assertThat(resultTopicParcels.get(0)).isEqualTo(topicParcel);

        // Verify the topic is  removed
        service.removeBlockedTopic(topicParcel);
        assertThat(service.retrieveAllBlockedTopics()).isEmpty();
    }

    @Test
    public void testRecordMeasurementDeletionOccurred() throws Exception {
        AdServicesManagerService service = spy(newService());
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // Mock the setting of the AdServices module version in the system server.
        setAdServicesModuleVersion(service, TEST_ROLLED_BACK_FROM_MODULE_VERSION);

        // First, the has measurement deletion occurred is false.
        assertThat(service.hasAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION))
                .isFalse();
        service.recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
        assertThat(service.hasAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION))
                .isTrue();
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_noRollback_returnsFalse() {
        AdServicesManagerService service = spy(newService());

        disableEnforceAdServicesManagerPermission(service);

        // Set the rolled back from package to null, indicating there was not a rollback.
        doReturn(new SparseArray<>()).when(service).getAdServicesPackagesRolledBackFrom();

        doReturn(true).when(service).hasAdServicesDeletionOccurred(anyInt());

        assertThat(
                        service.needsToHandleRollbackReconciliation(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isFalse();
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_noDeletion_returnsFalse() {
        AdServicesManagerService service = spy(newService());

        disableEnforceAdServicesManagerPermission(service);

        setAdServicesRolledBackFromVersionedPackage(
                service, TEST_ROLLED_BACK_FROM_MODULE_VERSION, ROLLBACK_ID);
        setAdServicesRolledBackToVersionedPackage(
                service, TEST_ROLLED_BACK_TO_MODULE_VERSION, ROLLBACK_ID);

        // Set the deletion bit to false.
        doReturn(false).when(service).hasAdServicesDeletionOccurred(anyInt());

        doReturn(TEST_ROLLED_BACK_FROM_MODULE_VERSION)
                .when(service)
                .getPreviousStoredVersion(anyInt());
        setAdServicesModuleVersion(service, TEST_ROLLED_BACK_TO_MODULE_VERSION);

        assertThat(
                        service.needsToHandleRollbackReconciliation(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isFalse();
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_versionFromDoesNotEqual_returnsFalse() {
        AdServicesManagerService service = spy(newService());

        disableEnforceAdServicesManagerPermission(service);

        setAdServicesRolledBackFromVersionedPackage(
                service, TEST_ROLLED_BACK_FROM_MODULE_VERSION, ROLLBACK_ID);
        setAdServicesRolledBackToVersionedPackage(
                service, TEST_ROLLED_BACK_TO_MODULE_VERSION, ROLLBACK_ID);

        // Set the deletion bit to false.
        doReturn(true).when(service).hasAdServicesDeletionOccurred(anyInt());

        doReturn(TEST_ROLLED_BACK_FROM_MODULE_VERSION + 1)
                .when(service)
                .getPreviousStoredVersion(anyInt());
        setAdServicesModuleVersion(service, TEST_ROLLED_BACK_TO_MODULE_VERSION);

        assertThat(
                        service.needsToHandleRollbackReconciliation(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isFalse();
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_versionToDoesNotEqual_returnsFalse() {
        AdServicesManagerService service = spy(newService());

        disableEnforceAdServicesManagerPermission(service);

        setAdServicesRolledBackFromVersionedPackage(
                service, TEST_ROLLED_BACK_FROM_MODULE_VERSION, ROLLBACK_ID);
        setAdServicesRolledBackToVersionedPackage(
                service, TEST_ROLLED_BACK_TO_MODULE_VERSION, ROLLBACK_ID);

        // Set the deletion bit to false.
        doReturn(true).when(service).hasAdServicesDeletionOccurred(anyInt());

        doReturn(TEST_ROLLED_BACK_FROM_MODULE_VERSION)
                .when(service)
                .getPreviousStoredVersion(anyInt());
        setAdServicesModuleVersion(service, TEST_ROLLED_BACK_TO_MODULE_VERSION + 1);

        assertThat(
                        service.needsToHandleRollbackReconciliation(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isFalse();
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_returnsTrue() {
        AdServicesManagerService service = spy(newService());

        disableEnforceAdServicesManagerPermission(service);

        setAdServicesRolledBackFromVersionedPackage(
                service, TEST_ROLLED_BACK_FROM_MODULE_VERSION, ROLLBACK_ID);
        setAdServicesRolledBackToVersionedPackage(
                service, TEST_ROLLED_BACK_TO_MODULE_VERSION, ROLLBACK_ID);

        // Set the deletion bit to false.
        doReturn(true).when(service).hasAdServicesDeletionOccurred(anyInt());

        doReturn(TEST_ROLLED_BACK_FROM_MODULE_VERSION)
                .when(service)
                .getPreviousStoredVersion(anyInt());
        setAdServicesModuleVersion(service, TEST_ROLLED_BACK_TO_MODULE_VERSION);

        assertThat(
                        service.needsToHandleRollbackReconciliation(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isTrue();
        verify(service).resetAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    @Test
    public void setCurrentPrivacySandboxFeatureWithConsent() throws Exception {
        AdServicesManagerService service = spy(newService());
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // The default feature is PRIVACY_SANDBOX_UNSUPPORTED
        assertThat(service.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name());
        service.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name());
        assertThat(service.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name());
        service.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT.name());
        assertThat(service.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT.name());
        service.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name());
        assertThat(service.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name());
    }

    @Test
    public void uxConformanceTest() throws Exception {
        AdServicesManagerService service = spy(newService());
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // The default UX is UNSUPPORTED_UX
        assertThat(service.getUx()).isEqualTo(PrivacySandboxUxCollection.UNSUPPORTED_UX.toString());

        Stream.of(PrivacySandboxUxCollection.values())
                .forEach(
                        ux -> {
                            service.setUx(ux.toString());
                            assertThat(service.getUx()).isEqualTo(ux.toString());
                        });
    }

    @Test
    public void testDump_noPermission() throws Exception {
        AdServicesManagerService service = newService();

        assertThrows(
                SecurityException.class,
                () -> service.dump(/* fd= */ null, /* pw= */ null, /* args= */ null));
    }

    @Test
    public void testDump() throws Exception {
        mockDumpPermission();
        AdServicesManagerService service = newService();

        Context previousContext = SystemContextSingleton.setForTests(mContext);
        try {
            String dump = dump(pw -> service.dump(/* fd= */ null, pw, /* args= */ null));

            // Content doesn't matter much, we just wanna make sure it doesn't crash (for example,
            // by using the wrong %s / %d tokens) and that its components are dumped
            expect.withMessage("content of dump()").that(dump).contains(USER_INSTANCE_MANAGER_DUMP);
            expect.withMessage("content of dump()")
                    .that(dump)
                    .contains("SystemContextSingleton: " + mContext);
        } finally {
            SystemContextSingleton.setForTests(previousContext);
        }
    }

    @Test
    public void testDump_systemContextSingletonNotSetYet() throws Exception {
        mockDumpPermission();
        AdServicesManagerService service = newService();

        String dump = dump(pw -> service.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()")
                .that(dump)
                .contains(
                        "SystemContextSingleton: "
                                + SystemContextSingleton.ERROR_MESSAGE_SET_NOT_CALLED);
    }

    private void mockDumpPermission() {
        doNothing()
                .when(mSpyContext)
                .enforceCallingPermission(eq(android.Manifest.permission.DUMP), isNull());
    }

    // Mock the call to get the AdServices module version from the PackageManager.
    private void setAdServicesModuleVersion(AdServicesManagerService service, int version) {
        doReturn(version).when(service).getAdServicesApexVersion();
    }

    // Mock the call to get the rolled back from versioned package.
    private void setAdServicesRolledBackFromVersionedPackage(
            AdServicesManagerService service, int version, int rollbackId) {
        SparseArray<VersionedPackage> packagesRolledBackFrom = new SparseArray<>();
        VersionedPackage versionedPackage =
                new VersionedPackage(ADSERVICES_APEX_PACKAGE_NAME, version);
        packagesRolledBackFrom.put(rollbackId, versionedPackage);
        doReturn(packagesRolledBackFrom).when(service).getAdServicesPackagesRolledBackFrom();
    }

    // Mock the call to get the rolled back to versioned package.
    private void setAdServicesRolledBackToVersionedPackage(
            AdServicesManagerService service, int version, int rollbackId) {
        SparseArray<VersionedPackage> packagesRolledBackTo = new SparseArray<>();
        VersionedPackage versionedPackage =
                new VersionedPackage(ADSERVICES_APEX_PACKAGE_NAME, version);
        packagesRolledBackTo.put(rollbackId, versionedPackage);
        doReturn(packagesRolledBackTo).when(service).getAdServicesPackagesRolledBackTo();
    }

    // Since unit test cannot execute an IPC call, disable the permission check.
    private void disableEnforceAdServicesManagerPermission(AdServicesManagerService service) {
        doNothing().when(service).enforceAdServicesManagerPermission();
    }

    private void setupMockResolveInfo() {
        ResolveInfo resolveInfo = new ResolveInfo();
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = PPAPI_PACKAGE_NAME;
        activityInfo.name = "SomeName";
        resolveInfo.activityInfo = activityInfo;
        ArrayList<ResolveInfo> resolveInfoList = new ArrayList<>();
        resolveInfoList.add(resolveInfo);
        when(mMockPackageManager.queryBroadcastReceiversAsUser(
                        any(Intent.class),
                        any(PackageManager.ResolveInfoFlags.class),
                        any(UserHandle.class)))
                .thenReturn(resolveInfoList);
    }

    private void setupMockInstalledPackages() {
        PackageInfo packageInfo = Mockito.spy(PackageInfo.class);
        packageInfo.packageName = ADSERVICES_APEX_PACKAGE_NAME;
        packageInfo.isApex = true;
        doReturn((long) TEST_ROLLED_BACK_FROM_MODULE_VERSION)
                .when(packageInfo)
                .getLongVersionCode();
        ArrayList<PackageInfo> packageInfoList = new ArrayList<>();
        packageInfoList.add(packageInfo);
        when(mMockPackageManager.getInstalledPackages(any(PackageManager.PackageInfoFlags.class)))
                .thenReturn(packageInfoList);
    }

    @Test
    public void isAdIdEnabledTest() throws Exception {
        AdServicesManagerService service = spy(newService());

        disableEnforceAdServicesManagerPermission(service);

        assertThat(service.isAdIdEnabled()).isFalse();
        service.setAdIdEnabled(true);
        assertThat(service.isAdIdEnabled()).isTrue();
    }

    @Test
    public void isU18AccountTest() throws Exception {
        AdServicesManagerService service = spy(newService());

        disableEnforceAdServicesManagerPermission(service);

        assertThat(service.isU18Account()).isFalse();
        service.setU18Account(true);
        assertThat(service.isU18Account()).isTrue();
    }

    @Test
    public void isEntryPointEnabledTest() throws Exception {
        AdServicesManagerService service = spy(newService());

        disableEnforceAdServicesManagerPermission(service);

        assertThat(service.isEntryPointEnabled()).isFalse();
        service.setEntryPointEnabled(true);
        assertThat(service.isEntryPointEnabled()).isTrue();
    }

    @Test
    public void isAdultAccountTest() throws Exception {
        AdServicesManagerService service = spy(newService());

        disableEnforceAdServicesManagerPermission(service);

        assertThat(service.isAdultAccount()).isFalse();
        service.setAdultAccount(true);
        assertThat(service.isAdultAccount()).isTrue();
    }

    @Test
    public void wasU18NotificationDisplayedTest() throws Exception {
        AdServicesManagerService service = spy(newService());

        disableEnforceAdServicesManagerPermission(service);

        assertThat(service.wasU18NotificationDisplayed()).isFalse();
        service.setU18NotificationDisplayed(true);
        assertThat(service.wasU18NotificationDisplayed()).isTrue();
    }

    @Test
    public void enrollmentChannelConformanceTest() throws Exception {
        AdServicesManagerService service = spy(newService());
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // The default enrollment channel is null.
        assertThat(service.getEnrollmentChannel()).isNull();

        Stream.of(PrivacySandboxEnrollmentChannelCollection.values())
                .forEach(
                        channel -> {
                            service.setEnrollmentChannel(channel.toString());
                            assertThat(service.getEnrollmentChannel())
                                    .isEqualTo(channel.toString());
                        });
    }

    @Test
    public void enrollmentChannelConformanceWithServiceCoreTest() {
        AdServicesManagerService service = spy(newService());
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // check each UX to ensure all enrollment channel values are accounted for
        Object[] ssChannels = PrivacySandboxEnrollmentChannelCollection.values();
        Consumer<Object> consumer =
                channel -> {
                    Stream<Object> stream = Arrays.stream(ssChannels);
                    Boolean hasMatchingEntry =
                            stream.anyMatch(
                                    ssChannel -> ssChannel.toString().equals(channel.toString()));
                    assertThat(hasMatchingEntry).isTrue();
                };
        Stream.of(GaUxEnrollmentChannelCollection.values()).forEach(consumer);
        Stream.of(U18UxEnrollmentChannelCollection.values()).forEach(consumer);
    }

    private static String handleShellCommand(Binder binder) throws IOException {
        ParcelFileDescriptor[] inPipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor[] outAndErrPipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor readEnd = outAndErrPipe[0];
        ParcelFileDescriptor writeEnd = outAndErrPipe[1];

        binder.handleShellCommand(
                /* in= */ inPipe[1], /* out= */ writeEnd, /* err= */ writeEnd, /* args= */ null);
        writeEnd.close();

        // Input is not used, but cannot be null (and must be closed)
        inPipe[1].close(); // write end
        inPipe[0].close(); // read end

        String output;
        try (InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(readEnd)) {
            output =
                    new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.joining("\n"));
        }
        return output.trim();
    }

    private void setFlag(String name, boolean value) {
        Log.d(mTag, "setFlag(): " + name + "=" + value);
        flags.setFlag(name, value);

        // Make sure rule really set it
        // TODO(b/369198554): shouldn't be necessary
        assertWithMessage("flags.get(%s) after flags.set(%s, %s)", name, name, value)
                .that(flags.getFlag(name))
                .isEqualTo(Boolean.toString(value));
    }

    private AdServicesManagerService newService() {
        AdServicesManagerService service =
                new AdServicesManagerService(mSpyContext, mUserInstanceManager);
        mServices.add(service);
        mLog.v("newService(): returning %s for %s", service, getTestName());
        return service;
    }
}
