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
package com.android.adservices.ui.settings;

import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.GA_UX;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.Until;

import com.android.adservices.LogUtil;
import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastT;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.App;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

@SpyStatic(FlagsFactory.class)
@SpyStatic(BackgroundJobsManager.class)
@SpyStatic(ConsentManager.class)
@RequiresSdkLevelAtLeastT(reason = "Back Compat only support GA UX")
@RunWith(AndroidJUnit4.class)
public final class DialogFragmentTest extends AdServicesExtendedMockitoTestCase {

    private static final String PRIVACY_SANDBOX_TEST_PACKAGE = "android.test.adservices.ui.MAIN";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static UiDevice sDevice;

    @Mock private ConsentManager mConsentManager;
    @Mock private Flags mMockFlags;

    @Before
    public void setup() throws Exception {
        extendedMockito.mockGetFlags(mMockFlags);
        doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();
        // UiDialogFragmentEnable flag should be on for this test
        doReturn(true).when(mMockFlags).getUiDialogFragmentEnabled();
        doReturn(true).when(mMockFlags).getUIDialogsFeatureEnabled();
        List<Topic> tempList = new ArrayList<>();
        tempList.add(Topic.create(10001, 1, 1));
        tempList.add(Topic.create(10002, 1, 1));
        tempList.add(Topic.create(10003, 1, 1));
        ImmutableList<Topic> topicsList = ImmutableList.copyOf(tempList);
        doReturn(topicsList).when(mConsentManager).getKnownTopicsWithConsent();

        tempList = new ArrayList<>();
        tempList.add(Topic.create(10004, 1, 1));
        tempList.add(Topic.create(10005, 1, 1));
        ImmutableList<Topic> blockedTopicsList = ImmutableList.copyOf(tempList);
        doReturn(blockedTopicsList).when(mConsentManager).getTopicsWithRevokedConsent();

        List<App> appTempList = new ArrayList<>();
        appTempList.add(App.create("app1"));
        appTempList.add(App.create("app2"));
        ImmutableList<App> appsList = ImmutableList.copyOf(appTempList);
        doReturn(appsList).when(mConsentManager).getKnownAppsWithConsent();

        appTempList = new ArrayList<>();
        appTempList.add(App.create("app3"));
        ImmutableList<App> blockedAppsList = ImmutableList.copyOf(appTempList);
        doReturn(blockedAppsList).when(mConsentManager).getAppsWithRevokedConsent();

        doNothing().when(mConsentManager).resetTopicsAndBlockedTopics();
        doNothing().when(mConsentManager).resetTopics();
        doNothing().when(mConsentManager).revokeConsentForTopic(any(Topic.class));
        doNothing().when(mConsentManager).restoreConsentForTopic(any(Topic.class));
        doNothing().when(mConsentManager).resetAppsAndBlockedApps();
        doNothing().when(mConsentManager).resetApps();
        doNothing().when(mConsentManager).revokeConsentForApp(any(App.class));
        doNothing().when(mConsentManager).restoreConsentForApp(any(App.class));
        doNothing().when(mConsentManager).resetMeasurement();

        ExtendedMockito.doNothing()
                .when(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
        ExtendedMockito.doReturn(mConsentManager)
                .when(() -> ConsentManager.getInstance(any(Context.class)));
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManager)
                .getConsent(AdServicesApiType.TOPICS);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManager)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManager)
                .getConsent(AdServicesApiType.MEASUREMENTS);

        doNothing().when(mConsentManager).enable(any(Context.class));
        doNothing().when(mConsentManager).disable(any(Context.class));
        doReturn(GA_UX).when(mConsentManager).getUx();

        try {
            startActivityFromHomeAndCheckMainSwitch();
        } catch (RemoteException e) {
            LogUtil.e("RemoteException from setOrientation.");
        }
    }

    private void startActivityFromHomeAndCheckMainSwitch() throws RemoteException {
        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        sDevice.pressHome();
        sDevice.setOrientationNatural();

        // Wait for launcher
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);

        // launch app
        Context context = appContext.get();
        Intent intent = new Intent(PRIVACY_SANDBOX_TEST_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        // Wait for the app to appear
        sDevice.wait(
                Until.hasObject(By.pkg(PRIVACY_SANDBOX_TEST_PACKAGE).depth(0)), LAUNCH_TIMEOUT);
    }

    @After
    public void teardown() {
        ApkTestUtil.takeScreenshot(sDevice, getClass().getSimpleName() + "_" + getTestName() + "_");
    }

    @Test
    public void optOutDialogTest() throws Exception {
        UiObject consentSwitch = ApkTestUtil.getConsentSwitch(sDevice);
        assertThat(consentSwitch.exists()).isTrue();

        // guarantee in on state
        if (!consentSwitch.isChecked()) {
            consentSwitch.click();
        }

        // click switch
        consentSwitch.click();
        UiObject dialogTitle =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_opt_out_title);

        UiObject negativeText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_negative_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();

        // cancel
        negativeText.click();

        // click switch
        consentSwitch.click();
        dialogTitle = ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_opt_out_title);
        UiObject positiveText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_opt_out_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();
        // confirm
        positiveText.click();
    }

    @Test
    public void blockTopicDialogTest() throws Exception {
        // open topics view
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_topics_title);
        UiObject blockTopicText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_block_topic_title, 0);
        assertThat(blockTopicText.exists()).isTrue();

        // click block
        blockTopicText.click();
        UiObject dialogTitle =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_block_topic_message);
        UiObject positiveText =
                ApkTestUtil.getElement(
                        sDevice, R.string.settingsUI_dialog_block_topic_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // confirm
        positiveText.click();
        verify(mConsentManager).revokeConsentForTopic(any(Topic.class));
        blockTopicText = ApkTestUtil.getElement(sDevice, R.string.settingsUI_block_topic_title, 0);
        assertThat(blockTopicText.exists()).isTrue();

        // click block again
        blockTopicText.click();
        dialogTitle =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_block_topic_message);
        UiObject negativeText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_negative_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();

        // cancel and verify it has still only been called once
        negativeText.click();
        verify(mConsentManager).revokeConsentForTopic(any(Topic.class));
    }

    @Test
    public void unblockTopicDialogTest() throws Exception {
        // open topics view
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_topics_title);

        // open blocked topics view
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_blocked_topics_title);
        UiObject unblockTopicText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_unblock_topic_title, 0);
        assertThat(unblockTopicText.exists()).isTrue();

        // click unblock
        unblockTopicText.click();
        UiObject dialogTitle =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_unblock_topic_message);
        UiObject positiveText =
                ApkTestUtil.getElement(
                        sDevice, R.string.settingsUI_dialog_unblock_topic_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // confirm
        positiveText.click();
        verify(mConsentManager).restoreConsentForTopic(any(Topic.class));
        unblockTopicText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_unblock_topic_title, 0);
        assertThat(unblockTopicText.exists()).isTrue();
    }

    @Test
    public void resetTopicDialogTest() throws Exception {
        // open topics view
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_topics_title);

        // click reset
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_reset_topics_title);
        UiObject dialogTitle =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_reset_topic_message);
        UiObject positiveText =
                ApkTestUtil.getElement(
                        sDevice, R.string.settingsUI_dialog_reset_topic_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // confirm
        positiveText.click();
        verify(mConsentManager).resetTopics();

        // click reset again
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_reset_topics_title);
        dialogTitle =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_reset_topic_message);
        UiObject negativeText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_negative_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();

        // cancel and verify it has still only been called once
        negativeText.click();
        verify(mConsentManager).resetTopics();
    }

    @Test
    @FlakyTest(bugId = 301779505)
    public void blockAppDialogTest() throws Exception {
        // perform a gentle swipe so scroll won't miss the text close to the
        // bottom of the current screen.
        UiObject appsTitle = ApkTestUtil.getElement(sDevice, R.string.settingsUI_apps_title);
        if(!appsTitle.exists()){
            ApkTestUtil.gentleSwipe(sDevice);
        }

        // open apps view
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_apps_title);
        UiObject blockAppText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_block_app_title, 0);
        assertThat(blockAppText.exists()).isTrue();

        // click block
        blockAppText.click();
        UiObject dialogTitle =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_block_app_message);
        UiObject positiveText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_block_app_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // confirm
        positiveText.click();
        verify(mConsentManager).revokeConsentForApp(any(App.class));
        blockAppText = ApkTestUtil.getElement(sDevice, R.string.settingsUI_block_app_title, 0);
        assertThat(blockAppText.exists()).isTrue();

        // click block again
        blockAppText.click();
        dialogTitle = ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_block_app_message);
        UiObject negativeText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_negative_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();

        // cancel and verify it has still only been called once
        negativeText.click();
        verify(mConsentManager).revokeConsentForApp(any(App.class));
    }

    @Test
    public void unblockAppDialogTest() throws Exception {
        UiObject appsTitle = ApkTestUtil.getElement(sDevice, R.string.settingsUI_apps_title);
        if(!appsTitle.exists()){
            ApkTestUtil.gentleSwipe(sDevice);
        }

        // open apps view
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_apps_title);

        // perform a gentle swipe so scroll won't miss the text close to the
        // bottom of the current screen.
        ApkTestUtil.gentleSwipe(sDevice);

        // open blocked apps view
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_blocked_apps_title);
        UiObject unblockAppText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_unblock_app_title, 0);
        assertThat(unblockAppText.exists()).isTrue();

        // click unblock
        unblockAppText.click();
        UiObject dialogTitle =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_unblock_app_message);
        UiObject positiveText =
                ApkTestUtil.getElement(
                        sDevice, R.string.settingsUI_dialog_unblock_app_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // confirm
        positiveText.click();
        verify(mConsentManager).restoreConsentForApp(any(App.class));
        unblockAppText = ApkTestUtil.getElement(sDevice, R.string.settingsUI_unblock_app_title, 0);
        assertThat(unblockAppText.exists()).isTrue();
    }

    @Test
    public void resetAppDialogTest() throws Exception {
        UiObject appsTitle = ApkTestUtil.getElement(sDevice, R.string.settingsUI_apps_title);
        if(!appsTitle.exists()){
            ApkTestUtil.gentleSwipe(sDevice);
        }

        // open apps view
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_apps_title);

        // click reset
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_reset_apps_title);
        UiObject dialogTitle =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_reset_app_message);
        UiObject positiveText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_reset_app_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // confirm
        positiveText.click();
        verify(mConsentManager).resetApps();

        // click reset again
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_reset_apps_title);
        dialogTitle = ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_reset_app_message);
        UiObject negativeText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_negative_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();

        // cancel and verify it has still only been called once
        negativeText.click();
        verify(mConsentManager).resetApps();
    }
}
