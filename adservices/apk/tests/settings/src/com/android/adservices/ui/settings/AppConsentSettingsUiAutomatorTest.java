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
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.CompatAdServicesTestUtils;
import com.android.adservices.service.Flags;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppConsentSettingsUiAutomatorTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();

    private static UiDevice sDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    private static final String TEST_APP_NAME = "com.example.adservices.samples.ui.consenttestapp";
    private static final String TEST_APP_APK_PATH =
            "/data/local/tmp/cts/install/" + TEST_APP_NAME + ".apk";
    private static final String TEST_APP_ACTIVITY_NAME = TEST_APP_NAME + ".MainActivity";
    private static final String PRIVACY_SANDBOX_PACKAGE = "android.adservices.ui.SETTINGS";
    private static final String PLAY_STORE_DONT_SEND_BUTTON = "DON'T SEND";
    private String mTestName;

    private static final int LAUNCH_TIMEOUT = 5000;
    private static final int GENERAL_TIMEOUT = 1000;

    private static final ComponentName sComponent =
            new ComponentName(TEST_APP_NAME, TEST_APP_ACTIVITY_NAME);

    @Before
    public void setup() throws Exception {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(ApkTestUtil.isDeviceSupported());

        String installMessage = ShellUtils.runShellCommand("pm install -r " + TEST_APP_APK_PATH);

        removePlayProtectWarning();

        Assume.assumeTrue(installMessage.contains("Success"));

        // Start from the home screen
        sDevice.pressHome();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            CompatAdServicesTestUtils.setFlags();
        }
    }

    @After
    public void teardown() {
        ApkTestUtil.takeScreenshot(sDevice, getClass().getSimpleName() + "_" + mTestName + "_");

        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Note aosp_x86 requires --user 0 to uninstall though arm doesn't.
        ShellUtils.runShellCommand("pm uninstall --user 0 " + TEST_APP_NAME);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            CompatAdServicesTestUtils.resetFlagsToDefault();
        }
    }

    @Test
    public void consentSystemServerOnlyTest_betaUx() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        Assume.assumeTrue(SdkLevel.isAtLeastT());
        appConsentTest(0, false, false);
    }

    @Test
    public void consentPpApiOnlyTest_betaUx() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        appConsentTest(1, false, false);
    }

    @Test
    public void consentSystemServerAndPpApiTest_betaUx() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        Assume.assumeTrue(SdkLevel.isAtLeastT());
        appConsentTest(2, false, false);
    }

    @Test
    public void consentAppSearchOnlyTest_betaUx() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand(
                "device_config put adservices enable_appsearch_consent_data true");
        appConsentTest(Flags.APPSEARCH_ONLY, false, false);
    }

    @Test
    public void consentAppSearchOnlyDialogsOnTest_betaUx() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand(
                "device_config put adservices enable_appsearch_consent_data true");
        appConsentTest(Flags.APPSEARCH_ONLY, true, false);
    }

    @Test
    public void consentSystemServerOnlyDialogsOnTest_betaUx() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        Assume.assumeTrue(SdkLevel.isAtLeastT());
        appConsentTest(0, true, false);
    }

    @Test
    public void consentPpApiOnlyDialogsOnTest_betaUx() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        appConsentTest(1, true, false);
    }

    @Test
    public void consentSystemServerAndPpApiDialogsOnTest_betaUx() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        Assume.assumeTrue(SdkLevel.isAtLeastT());
        appConsentTest(2, true, false);
    }

    @Test
    public void consentSystemServerOnlyTest_gaUx() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        Assume.assumeTrue(SdkLevel.isAtLeastT());
        appConsentTest(0, false, true);
    }

    @Test
    public void consentPpApiOnlyTest_gaUx() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        appConsentTest(1, false, true);
    }

    @Test
    public void consentSystemServerAndPpApiTest_gaUx() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        Assume.assumeTrue(SdkLevel.isAtLeastT());
        appConsentTest(2, false, true);
    }

    @Test
    public void consentAppSearchOnlyTest_gaUx() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand(
                "device_config put adservices enable_appsearch_consent_data true");
        appConsentTest(Flags.APPSEARCH_ONLY, false, true);
    }

    // FLEDGE toggle does not have diaglogs in GA, so no dialogs on GA tests.

    private boolean launchSettingApp() {
        try {
            Context context = ApplicationProvider.getApplicationContext();
            Intent intent = new Intent(PRIVACY_SANDBOX_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            // Wait for the app to appear
            sDevice.wait(Until.hasObject(By.pkg(PRIVACY_SANDBOX_PACKAGE).depth(0)), LAUNCH_TIMEOUT);
        } catch (Exception e) {
            // activity might fail to be opened on S.
            return false;
        }
        return true;
    }

    private void appConsentTest(int consentSourceOfTruth, boolean dialogsOn, boolean isGa)
            throws Exception {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_source_of_truth " + consentSourceOfTruth);
        ShellUtils.runShellCommand(
                "device_config put adservices ui_dialogs_feature_enabled " + dialogsOn);
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled " + isGa);

        if (!launchSettingApp()) {
            return;
        }

        if (isGa) {
            openFledgeSettingsPage(isGa);
        }

        resetFledgeData(dialogsOn);

        if (isGa) {
            sDevice.pressBack();
        }

        initiateTestAppConsent();

        launchSettingApp();

        openFledgeSettingsPage(isGa);

        // skip if the FLEDGE API failed.
        if (!blockAppConsent(dialogsOn)) {
            return;
        }

        unblockAppConsent(isGa, dialogsOn);

        assertThat(ApkTestUtil.getElement(sDevice, R.string.settingsUI_block_app_title).exists())
                .isTrue();

        resetAppConsent(isGa, dialogsOn);

        assertThat(getElement(R.string.settingsUI_block_app_title, 0).exists()).isFalse();
        assertThat(getElement(R.string.settingsUI_blocked_apps_title, 0).exists()).isFalse();
        assertThat(
                        getElement(
                                        isGa
                                                ? R.string.settingsUI_apps_view_no_apps_ga_text
                                                : R.string.settingsUI_apps_view_no_apps_text,
                                        0)
                                .exists())
                .isTrue();
    }

    private void openFledgeSettingsPage(boolean isGa) throws Exception {
        ApkTestUtil.scrollToAndClick(
                sDevice, isGa ? R.string.settingsUI_apps_ga_title : R.string.settingsUI_apps_title);
        Thread.sleep(GENERAL_TIMEOUT);
    }

    // fully reset FLEDGE data.
    private void resetFledgeData(boolean dialogsOn) throws Exception {
        UiObject consentSwitch = ApkTestUtil.getConsentSwitch(sDevice);
        if (!consentSwitch.isChecked()) {
            consentSwitch.click();
            Thread.sleep(GENERAL_TIMEOUT);
        }
        if (consentSwitch.isChecked()) {
            if (!dialogsOn) {
                consentSwitch.click();
            } else {
                disableUserConsentWithDialog(consentSwitch, dialogsOn);
            }
            Thread.sleep(GENERAL_TIMEOUT);
        }
        consentSwitch.click();
        Thread.sleep(GENERAL_TIMEOUT);
    }

    private void unblockAppConsent(boolean isGa, boolean dialogsOn)
            throws UiObjectNotFoundException {
        ApkTestUtil.scrollToAndClick(
                sDevice,
                isGa
                        ? R.string.settingsUI_view_blocked_apps_title
                        : R.string.settingsUI_blocked_apps_title);

        UiObject unblockButton =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_unblock_app_title);
        unblockButton.waitForExists(GENERAL_TIMEOUT);
        unblockButton.click();

        if (dialogsOn) {
            // click unblock
            UiObject dialogTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_unblock_app_message);
            UiObject positiveText =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_unblock_app_positive_text);
            positiveText.waitForExists(GENERAL_TIMEOUT);
            assertThat(dialogTitle.exists()).isTrue();
            assertThat(positiveText.exists()).isTrue();

            // confirm
            positiveText.click();
        }

        UiObject noBlockedAppsText =
                ApkTestUtil.getElement(
                        sDevice,
                        isGa
                                ? R.string.settingsUI_no_blocked_apps_ga_text
                                : R.string.settingsUI_apps_view_no_blocked_apps_text);
        noBlockedAppsText.waitForExists(GENERAL_TIMEOUT);
        assertThat(noBlockedAppsText.exists()).isTrue();

        sDevice.pressBack();
    }

    private void resetAppConsent(boolean isGa, boolean dialogsOn) throws UiObjectNotFoundException {
        ApkTestUtil.scrollToAndClick(
                sDevice,
                isGa
                        ? R.string.settingsUI_reset_apps_ga_title
                        : R.string.settingsUI_reset_apps_title);

        if (dialogsOn) {
            UiObject dialogTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_reset_app_message);
            UiObject positiveText =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_reset_app_positive_text);
            positiveText.waitForExists(GENERAL_TIMEOUT);
            assertThat(dialogTitle.exists()).isTrue();
            assertThat(positiveText.exists()).isTrue();

            // confirm
            positiveText.click();
        }

        UiObject noAppsText =
                ApkTestUtil.getElement(
                        sDevice,
                        isGa
                                ? R.string.settingsUI_apps_view_no_apps_ga_text
                                : R.string.settingsUI_apps_view_no_apps_text,
                        0);
        noAppsText.waitForExists(GENERAL_TIMEOUT);
        assertThat(noAppsText.exists()).isTrue();

        assertThat(ApkTestUtil.getElement(sDevice, R.string.settingsUI_block_app_title, 0).exists())
                .isFalse();
        assertThat(
                        ApkTestUtil.getElement(sDevice, R.string.settingsUI_blocked_apps_title, 0)
                                .exists())
                .isFalse();
    }

    private boolean blockAppConsent(boolean dialogsOn) throws UiObjectNotFoundException {
        UiObject blockButton = ApkTestUtil.getElement(sDevice, R.string.settingsUI_block_app_title);
        blockButton.waitForExists(GENERAL_TIMEOUT);
        if (!blockButton.exists()) {
            return false;
        }
        blockButton.click();

        if (dialogsOn) {
            UiObject dialogTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_block_app_message);
            UiObject positiveText =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_block_app_positive_text);
            blockButton.waitForExists(GENERAL_TIMEOUT);
            assertThat(dialogTitle.exists()).isTrue();
            assertThat(positiveText.exists()).isTrue();
            positiveText.click();
        }

        return true;
    }

    private void disableUserConsentWithDialog(UiObject consentSwitch, boolean dialogsOn)
            throws Exception {
        // Verify the button is existed and click it.
        consentSwitch.click();

        if (dialogsOn) {
            // Handle dialog to disable user consent
            UiObject dialogTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_opt_out_title);
            UiObject positiveText =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_opt_out_positive_text);

            positiveText.waitForExists(GENERAL_TIMEOUT);
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

    private void initiateTestAppConsent() throws Exception {
        ShellUtils.runShellCommand("device_config set_sync_disabled_for_tests persistent");
        ShellUtils.runShellCommand("device_config put adservices global_kill_switch false");
        ShellUtils.runShellCommand(
                "device_config put adservices"
                        + " fledge_custom_audience_service_kill_switch false");
        ShellUtils.runShellCommand(
                "device_config put adservices disable_fledge_enrollment_check true");

        ShellUtils.runShellCommand("device_config put adservices ppapi_app_allow_list *");

        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent().setComponent(sComponent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        sDevice.wait(Until.hasObject(By.pkg(TEST_APP_NAME).depth(0)), LAUNCH_TIMEOUT);

        Thread.sleep(1000);

        ShellUtils.runShellCommand("device_config set_sync_disabled_for_tests none");
        ShellUtils.runShellCommand(
                "am force-stop com.example.adservices.samples.ui.consenttestapp");
        ShellUtils.runShellCommand(
                "device_config put adservices disable_fledge_enrollment_check null");
    }

    private void removePlayProtectWarning() throws Exception {
        UiObject ppw = sDevice.findObject(new UiSelector().text(PLAY_STORE_DONT_SEND_BUTTON));
        ppw.waitForExists(GENERAL_TIMEOUT);
        if (ppw.exists()) {
            ppw.click();
        }
    }
}
