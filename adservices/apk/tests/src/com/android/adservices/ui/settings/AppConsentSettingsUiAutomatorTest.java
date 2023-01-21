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

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppConsentSettingsUiAutomatorTest {
    private static final String TEST_APP_NAME = "com.example.adservices.samples.ui.consenttestapp";
    private static final String TEST_APP_APK_PATH =
            "/data/local/tmp/cts/install/" + TEST_APP_NAME + ".apk";
    private static final String TEST_APP_ACTIVITI_NAME = TEST_APP_NAME + ".MainActivity";
    private static final ComponentName COMPONENT =
            new ComponentName(TEST_APP_NAME, TEST_APP_ACTIVITI_NAME);

    private static final String PRIVACY_SANDBOX_TEST_PACKAGE = "android.adservices.ui.SETTINGS";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static UiDevice sDevice;

    @Before
    public void setup() throws UiObjectNotFoundException {
        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        sDevice.pressHome();
    }

    @After
    public void teardown() {
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");
    }

    @Test
    public void consentSystemServerOnlyTest() throws UiObjectNotFoundException {
        appConsentTest(0, false);
    }

    @Test
    public void consentPpApiOnlyTest() throws UiObjectNotFoundException {
        appConsentTest(1, false);
    }

    @Test
    public void consentSystemServerAndPpApiTest() throws UiObjectNotFoundException {
        appConsentTest(2, false);
    }

    @Test
    public void consentSystemServerOnlyDialogsOnTest() throws UiObjectNotFoundException {
        appConsentTest(0, true);
    }

    @Test
    public void consentPpApiOnlyDialogsOnTest() throws UiObjectNotFoundException {
        appConsentTest(1, true);
    }

    @Test
    public void consentSystemServerAndPpApiDialogsOnTest() throws UiObjectNotFoundException {
        appConsentTest(2, true);
    }

    private void setPpApiConsentToGiven() throws UiObjectNotFoundException {
        // launch app
        launchSettingApp();

        UiObject mainSwitch =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        assertThat(mainSwitch.exists()).isTrue();

        if (!mainSwitch.isChecked()) {
            mainSwitch.click();
        }
    }

    private void launchSettingApp() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(PRIVACY_SANDBOX_TEST_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        // Wait for the app to appear
        sDevice.wait(
                Until.hasObject(By.pkg(PRIVACY_SANDBOX_TEST_PACKAGE).depth(0)), LAUNCH_TIMEOUT);
    }

    private void appConsentTest(int consentSourceOfTruth, boolean dialogsOn)
            throws UiObjectNotFoundException {
        String installMessage = ShellUtils.runShellCommand("pm install -r " + TEST_APP_APK_PATH);
        assertThat(installMessage).contains("Success");

        ShellUtils.runShellCommand(
                "device_config put adservices consent_source_of_truth " + consentSourceOfTruth);
        ShellUtils.runShellCommand(
                "device_config put adservices ui_dialogs_feature_enabled " + dialogsOn);
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");

        // Wait for launcher
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled false");

        setPpApiConsentToGiven();

        // Initiate test app consent.
        initiateTestAppConsent();

        // open apps view
        launchSettingApp();
        scrollToAndClick(R.string.settingsUI_apps_title);

        blockAppConsent(dialogsOn);

        unblockAppConsent(dialogsOn);

        assertThat(getElement(R.string.settingsUI_block_app_title).exists()).isTrue();

        resetAppConsent(dialogsOn);

        assertThat(getElement(R.string.settingsUI_block_app_title, 0).exists()).isFalse();
        assertThat(getElement(R.string.settingsUI_blocked_apps_title, 0).exists()).isFalse();
        assertThat(getElement(R.string.settingsUI_apps_view_no_apps_text, 0).exists()).isTrue();
        // Note aosp_x86 requires --user 0 to uninstall though arm doesn't.
        ShellUtils.runShellCommand("pm uninstall --user 0 " + TEST_APP_NAME);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void unblockAppConsent(boolean dialogsOn) throws UiObjectNotFoundException {
        scrollToAndClick(R.string.settingsUI_blocked_apps_title);
        scrollToAndClick(R.string.settingsUI_unblock_app_title);

        if (dialogsOn) {
            // click unblock
            UiObject dialogTitle = getElement(R.string.settingsUI_dialog_unblock_app_message);
            UiObject positiveText =
                    getElement(R.string.settingsUI_dialog_unblock_app_positive_text);
            assertThat(dialogTitle.exists()).isTrue();
            assertThat(positiveText.exists()).isTrue();

            // confirm
            positiveText.click();
        }

        assertThat(getElement(R.string.settingsUI_apps_view_no_blocked_apps_text).exists())
                .isTrue();
        sDevice.pressBack();
    }

    private void resetAppConsent(boolean dialogsOn) throws UiObjectNotFoundException {
        scrollToAndClick(R.string.settingsUI_reset_apps_title);

        if (dialogsOn) {
            UiObject dialogTitle = getElement(R.string.settingsUI_dialog_reset_app_message);
            UiObject positiveText = getElement(R.string.settingsUI_dialog_reset_app_positive_text);
            assertThat(dialogTitle.exists()).isTrue();
            assertThat(positiveText.exists()).isTrue();

            // confirm
            positiveText.click();
        }
    }

    private void blockAppConsent(boolean dialogsOn) throws UiObjectNotFoundException {
        scrollToAndClick(R.string.settingsUI_block_app_title);

        if (dialogsOn) {
            UiObject dialogTitle = getElement(R.string.settingsUI_dialog_block_app_message);
            UiObject positiveText = getElement(R.string.settingsUI_dialog_block_app_positive_text);
            assertThat(dialogTitle.exists()).isTrue();
            assertThat(positiveText.exists()).isTrue();
            positiveText.click();
        }
    }

    private UiObject getElement(int resId) {
        return sDevice.findObject(new UiSelector().text(getString(resId)));
    }

    private UiObject getElement(int resId, int index) {
        return sDevice.findObject(new UiSelector().text(getString(resId)).instance(index));
    }

    private String getString(int resourceId) {
        return ApplicationProvider.getApplicationContext().getResources().getString(resourceId);
    }

    private void scrollToAndClick(int resId) throws UiObjectNotFoundException {
        UiScrollable scrollView =
                new UiScrollable(
                        new UiSelector().scrollable(true).className("android.widget.ScrollView"));
        UiObject element =
                sDevice.findObject(
                        new UiSelector()
                                .childSelector(
                                        new UiSelector().text(getString(resId)).instance(0)));
        scrollView.scrollIntoView(element);
        element.click();
    }

    private void initiateTestAppConsent() {
        String installMessage = ShellUtils.runShellCommand("pm install -r " + TEST_APP_APK_PATH);
        assertThat(installMessage).contains("Success");

        ShellUtils.runShellCommand("device_config set_sync_disabled_for_tests persistent");
        ShellUtils.runShellCommand("device_config put adservices global_kill_switch false");
        ShellUtils.runShellCommand(
                "device_config put adservices"
                        + " fledge_custom_audience_service_kill_switch false");

        ShellUtils.runShellCommand("device_config put adservices ppapi_app_allow_list *");

        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent().setComponent(COMPONENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        try {
            Thread.sleep(8000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        ShellUtils.runShellCommand("device_config set_sync_disabled_for_tests none");
        ShellUtils.runShellCommand(
                "am force-stop com.example.adservices.samples.ui.consenttestapp");
    }
}
