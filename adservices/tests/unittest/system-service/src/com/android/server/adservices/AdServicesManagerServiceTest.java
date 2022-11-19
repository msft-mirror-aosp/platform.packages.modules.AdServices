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

import static com.android.server.adservices.PhFlags.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.adservices.ConsentParcel;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.DeviceConfig;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;

/** Tests for {@link AdServicesManagerService} */
public class AdServicesManagerServiceTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    private AdServicesManagerService mService;
    private UserInstanceManager mUserInstanceManager;
    private Context mSpyContext;
    @Mock private PackageManager mMockPackageManager;

    private static final String PPAPI_PACKAGE_NAME = "com.google.android.adservices.api";
    private static final String PACKAGE_NAME = "com.package.example";
    private static final String PACKAGE_CHANGED_BROADCAST =
            "com.android.adservices.PACKAGE_CHANGED";
    private static final String PACKAGE_FULLY_REMOVED = "package_fully_removed";
    private static final String PACKAGE_ADDED = "package_added";
    private static final String PACKAGE_DATA_CLEARED = "package_data_cleared";
    private static final int PACKAGE_UID = 12345;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSpyContext = Mockito.spy(context);

        mUserInstanceManager =
                new UserInstanceManager(
                        /* adserviceBaseDir */ context.getFilesDir().getAbsolutePath());

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        doReturn(mMockPackageManager).when(mSpyContext).getPackageManager();
    }

    @After
    public void tearDown() {
        // We need tear down this instance since it can have underlying persisted Data Store.
        mUserInstanceManager.tearDownForTesting();
    }

    @Test
    public void testAdServicesSystemService_enabled_then_disabled() {
        // First enable the flag.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED,
                Boolean.toString(Boolean.TRUE),
                /* makeDefault */ false);

        // This will trigger the registration of the Receiver.
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        ArgumentCaptor<BroadcastReceiver> argumentReceiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        ArgumentCaptor<IntentFilter> argumentIntentFilter =
                ArgumentCaptor.forClass(IntentFilter.class);
        ArgumentCaptor<String> argumentPermission = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Handler> argumentHandler = ArgumentCaptor.forClass(Handler.class);

        // Calling the second time will not register again.
        mService.registerPackagedChangedBroadcastReceivers();

        // The flag is enabled so we call registerReceiverForAllUsers
        Mockito.verify(mSpyContext, Mockito.times(1))
                .registerReceiverForAllUsers(
                        argumentReceiver.capture(),
                        argumentIntentFilter.capture(),
                        argumentPermission.capture(),
                        argumentHandler.capture());

        BroadcastReceiver receiver = argumentReceiver.getValue();
        assertThat(receiver).isNotNull();

        IntentFilter intentFilter = argumentIntentFilter.getValue();
        assertThat(intentFilter.hasAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)).isTrue();
        assertThat(intentFilter.hasAction(Intent.ACTION_PACKAGE_DATA_CLEARED)).isTrue();
        assertThat(intentFilter.hasAction(Intent.ACTION_PACKAGE_ADDED)).isTrue();
        assertThat(intentFilter.countActions()).isEqualTo(3);
        assertThat(intentFilter.getDataScheme(0)).isEqualTo("package");

        assertThat(argumentPermission.getValue()).isNull();
        assertThat(argumentHandler.getValue()).isNotNull();

        // Now disable the flag.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED,
                Boolean.toString(Boolean.FALSE),
                /* makeDefault */ false);

        // Calling when the flag is disabled will unregister the Receiver!
        mService.registerPackagedChangedBroadcastReceivers();
        Mockito.verify(mSpyContext, Mockito.times(1))
                .unregisterReceiver(argumentReceiver.capture());

        // The unregistered is called on the same receiver when registered above.
        assertThat(argumentReceiver.getValue()).isSameInstanceAs(receiver);
    }

    @Test
    public void testAdServicesSystemService_disabled() {
        // Disable the flag.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED,
                Boolean.toString(Boolean.FALSE),
                /* makeDefault */ false);

        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        // The flag is disabled so there is no registerReceiverForAllUsers
        Mockito.verify(mSpyContext, Mockito.times(0))
                .registerReceiverForAllUsers(
                        any(BroadcastReceiver.class),
                        any(IntentFilter.class),
                        any(String.class),
                        any(Handler.class));
    }

    @Test
    public void testSendBroadcastForPackageFullyRemoved() {
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        Intent i = new Intent(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        i.setData(Uri.parse("package:" + PACKAGE_NAME));
        i.putExtra(Intent.EXTRA_UID, PACKAGE_UID);

        ArgumentCaptor<Intent> argumentIntent = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> argumentUser = ArgumentCaptor.forClass(UserHandle.class);

        setupMockResolveInfo();
        Mockito.doNothing().when(mSpyContext).sendBroadcastAsUser(Mockito.any(), Mockito.any());

        mService.onPackageChange(i, mSpyContext.getUser());

        Mockito.verify(mSpyContext, Mockito.times(1))
                .sendBroadcastAsUser(argumentIntent.capture(), argumentUser.capture());

        assertThat(argumentIntent.getValue().getAction()).isEqualTo(PACKAGE_CHANGED_BROADCAST);
        assertThat(argumentIntent.getValue().getData()).isEqualTo(i.getData());
        assertThat(argumentIntent.getValue().getStringExtra("action"))
                .isEqualTo(PACKAGE_FULLY_REMOVED);
        assertThat(argumentIntent.getValue().getIntExtra(Intent.EXTRA_UID, -1))
                .isEqualTo(PACKAGE_UID);
        assertThat(argumentUser.getValue()).isEqualTo(mSpyContext.getUser());
    }

    @Test
    public void testSendBroadcastForPackageAdded() {
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        Intent i = new Intent(Intent.ACTION_PACKAGE_ADDED);
        i.setData(Uri.parse("package:" + PACKAGE_NAME));
        i.putExtra(Intent.EXTRA_UID, PACKAGE_UID);
        i.putExtra(Intent.EXTRA_REPLACING, false);

        ArgumentCaptor<Intent> argumentIntent = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> argumentUser = ArgumentCaptor.forClass(UserHandle.class);

        setupMockResolveInfo();
        Mockito.doNothing().when(mSpyContext).sendBroadcastAsUser(Mockito.any(), Mockito.any());

        mService.onPackageChange(i, mSpyContext.getUser());

        Mockito.verify(mSpyContext, Mockito.times(1))
                .sendBroadcastAsUser(argumentIntent.capture(), argumentUser.capture());

        assertThat(argumentIntent.getValue().getAction()).isEqualTo(PACKAGE_CHANGED_BROADCAST);
        assertThat(argumentIntent.getValue().getData()).isEqualTo(i.getData());
        assertThat(argumentIntent.getValue().getStringExtra("action")).isEqualTo(PACKAGE_ADDED);
        assertThat(argumentIntent.getValue().getIntExtra(Intent.EXTRA_UID, -1))
                .isEqualTo(PACKAGE_UID);
        assertThat(argumentUser.getValue()).isEqualTo(mSpyContext.getUser());
    }

    @Test
    public void testSendBroadcastForPackageDataCleared() {
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        Intent i = new Intent(Intent.ACTION_PACKAGE_DATA_CLEARED);
        i.setData(Uri.parse("package:" + PACKAGE_NAME));
        i.putExtra(Intent.EXTRA_UID, PACKAGE_UID);

        ArgumentCaptor<Intent> argumentIntent = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> argumentUser = ArgumentCaptor.forClass(UserHandle.class);

        setupMockResolveInfo();
        Mockito.doNothing().when(mSpyContext).sendBroadcastAsUser(Mockito.any(), Mockito.any());

        mService.onPackageChange(i, mSpyContext.getUser());

        Mockito.verify(mSpyContext, Mockito.times(1))
                .sendBroadcastAsUser(argumentIntent.capture(), argumentUser.capture());

        assertThat(argumentIntent.getValue().getAction()).isEqualTo(PACKAGE_CHANGED_BROADCAST);
        assertThat(argumentIntent.getValue().getData()).isEqualTo(i.getData());
        assertThat(argumentIntent.getValue().getStringExtra("action"))
                .isEqualTo(PACKAGE_DATA_CLEARED);
        assertThat(argumentIntent.getValue().getIntExtra(Intent.EXTRA_UID, -1))
                .isEqualTo(PACKAGE_UID);
        assertThat(argumentUser.getValue()).isEqualTo(mSpyContext.getUser());
    }

    @Test
    public void testGetConsent_unSet() {
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);
        // Newly initialized ConsentManager has consent = false.
        assertThat(mService.getConsent().isIsGiven()).isFalse();
    }

    @Test
    public void testGetAndSetConsent_null() throws IOException {
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        mService.setConsent(new ConsentParcel.Builder().setIsGiven(null).build());
        // null means the consent is not given (false).
        assertThat(mService.getConsent().isIsGiven()).isFalse();
    }

    @Test
    public void testGetAndSetConsent_nonNull() throws IOException {
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);
        mService.setConsent(new ConsentParcel.Builder().setIsGiven(false).build());
        assertThat(mService.getConsent().isIsGiven()).isFalse();

        mService.setConsent(new ConsentParcel.Builder().setIsGiven(true).build());
        assertThat(mService.getConsent().isIsGiven()).isTrue();
    }

    @Test
    public void testRecordNotificationDisplayed() throws IOException {
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);
        // First, the notification displayed is false.
        assertThat(mService.wasNotificationDisplayed()).isFalse();
        mService.recordNotificationDisplayed();
        assertThat(mService.wasNotificationDisplayed()).isTrue();
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
}
