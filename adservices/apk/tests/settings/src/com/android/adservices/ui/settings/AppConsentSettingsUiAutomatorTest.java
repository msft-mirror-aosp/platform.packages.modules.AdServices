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

import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_APPSEARCH_CONSENT_DATA;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_UI_DIALOGS_FEATURE_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.Until;

import com.android.adservices.LogUtil;
import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.SetAllLogcatTags;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.ui.util.AdservicesSettingsUiTestCase;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@DisableGlobalKillSwitch
@SetAllLogcatTags
@SetCompatModeFlags
public final class AppConsentSettingsUiAutomatorTest extends AdservicesSettingsUiTestCase {
    private static final String TEST_APP_NAME = "com.example.adservices.samples.ui.consenttestapp";
    private static final String TEST_APP_APK_PATH =
            "/data/local/tmp/cts/install/" + TEST_APP_NAME + ".apk";
    private static final String TEST_APP_ACTIVITY_NAME = TEST_APP_NAME + ".MainActivity";
    private static final ComponentName COMPONENT =
            new ComponentName(TEST_APP_NAME, TEST_APP_ACTIVITY_NAME);

    private static final String PRIVACY_SANDBOX_PACKAGE = "android.adservices.ui.SETTINGS";
    private static final String PRIVACY_SANDBOX_TEST_PACKAGE = "android.test.adservices.ui.MAIN";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static UiDevice sDevice;

    private int mUserId;

    private String mTestName;

    @Rule(order = 5)
    public final AdServicesFlagsSetterRule flags = AdServicesFlagsSetterRule.newInstance();

    @Before
    public void setup() throws UiObjectNotFoundException {
        mTestName = getTestName();
        mUserId = Process.myUserHandle().getIdentifier();
        LogUtil.d("work on user id %d", mUserId);

        String installMessage =
                ShellUtils.runShellCommand(
                        "pm install -r --user %d %s", mUserId, TEST_APP_APK_PATH);
        assertThat(installMessage).contains("Success");

        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        sDevice.pressHome();
    }

    @After
    public void teardown() {
        ApkTestUtil.takeScreenshot(sDevice, getClass().getSimpleName() + "_" + mTestName + "_");

        AdservicesTestHelper.killAdservicesProcess(mContext);

        // Note aosp_x86 requires --user 0 to uninstall though arm doesn't.
        ShellUtils.runShellCommand("pm uninstall --user %d %s", mUserId, TEST_APP_NAME);
    }

    // TODO: Remove this blank test along with the other @Ignore. b/268351419
    @Test
    public void placeholderTest() {
        // As this class is the only test class in the test module and need to be @Ignore for the
        // moment, add a blank test to help presubmit to pass.
        assertThat(true).isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastT
    @Ignore("Flaky test. (b/268351419)")
    public void consentSystemServerOnlyTest() throws InterruptedException {
        // System server is not available on S-, skip this test for S-
        appConsentTest(0, false);
    }

    @Test
    @Ignore("Flaky test. (b/268351419)")
    public void consentPpApiOnlyTest() throws InterruptedException {
        appConsentTest(1, false);
    }

    @Test
    @RequiresSdkLevelAtLeastT
    @Ignore("Flaky test. (b/268351419)")
    public void consentSystemServerAndPpApiTest() throws InterruptedException {
        // System server is not available on S-, skip this test for S-
        appConsentTest(2, false);
    }

    @Test
    @SetFlagEnabled(KEY_ENABLE_APPSEARCH_CONSENT_DATA)
    @SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = 3)
    @Ignore("Flaky test. (b/268351419)")
    public void consentAppSearchOnlyTest() throws InterruptedException {
        appConsentTest(Flags.APPSEARCH_ONLY, false);
    }

    @Test
    @SetFlagEnabled(KEY_ENABLE_APPSEARCH_CONSENT_DATA)
    @SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = 3)
    @Ignore("Flaky test. (b/268351419)")
    public void consentAppSearchOnlyDialogsOnTest() throws InterruptedException {
        appConsentTest(Flags.APPSEARCH_ONLY, true);
    }

    @Test
    @RequiresSdkLevelAtLeastT
    @Ignore("Flaky test. (b/268351419)")
    public void consentSystemServerOnlyDialogsOnTest() throws InterruptedException {
        // System server is not available on S-, skip this test for S-
        appConsentTest(0, true);
    }

    @Test
    @Ignore("Flaky test. (b/268351419)")
    public void consentPpApiOnlyDialogsOnTest() throws InterruptedException {
        appConsentTest(1, true);
    }

    @Test
    @RequiresSdkLevelAtLeastT
    @Ignore("Flaky test. (b/268351419)")
    public void consentSystemServerAndPpApiDialogsOnTest() throws InterruptedException {
        // System server is not available on S-, skip this test for S-
        appConsentTest(2, true);
    }

    private void setPpApiConsentToGiven() {
        // launch app
        launchSettingApp();

        UiObject2 mainSwitch = ApkTestUtil.getConsentSwitch(sDevice);
        assertThat(mainSwitch).isNotNull();

        if (!mainSwitch.isChecked()) {
            mainSwitch.click();
        }
    }

    private void launchSettingApp() {
        String privacySandboxUi;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            privacySandboxUi = PRIVACY_SANDBOX_TEST_PACKAGE;
        } else {
            privacySandboxUi = PRIVACY_SANDBOX_PACKAGE;
        }
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(privacySandboxUi);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        // Wait for the app to appear
        sDevice.wait(Until.hasObject(By.pkg(privacySandboxUi).depth(0)), LAUNCH_TIMEOUT);
    }

    private void appConsentTest(int consentSourceOfTruth, boolean dialogsOn)
            throws InterruptedException {
        flags.setFlag(KEY_CONSENT_SOURCE_OF_TRUTH, consentSourceOfTruth);
        flags.setFlag(KEY_UI_DIALOGS_FEATURE_ENABLED, dialogsOn);

        AdservicesTestHelper.killAdservicesProcess(mContext);

        // Wait for launcher
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);

        flags.setFlag(KEY_GA_UX_FEATURE_ENABLED, false);

        setPpApiConsentToGiven();

        // Initiate test app consent.
        initiateTestAppConsent();

        // open apps view
        launchSettingApp();
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_apps_title);

        blockAppConsent(dialogsOn);

        unblockAppConsent(dialogsOn);

        assertThat(ApkTestUtil.getElement(sDevice, R.string.settingsUI_block_app_title))
                .isNotNull();

        resetAppConsent(dialogsOn);

        assertThat(ApkTestUtil.getElement(sDevice, R.string.settingsUI_block_app_title, 0))
                .isNull();
        assertThat(ApkTestUtil.getElement(sDevice, R.string.settingsUI_blocked_apps_title, 0))
                .isNull();
        assertThat(ApkTestUtil.getElement(sDevice, R.string.settingsUI_apps_view_no_apps_text, 0))
                .isNotNull();
    }

    private void unblockAppConsent(boolean dialogsOn) {
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_blocked_apps_title);
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_unblock_app_title);

        if (dialogsOn) {
            // click unblock
            UiObject2 dialogTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_unblock_app_message);
            UiObject2 positiveText =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_unblock_app_positive_text);
            assertThat(dialogTitle).isNotNull();
            assertThat(positiveText).isNotNull();

            // confirm
            positiveText.click();
        }

        assertThat(
                        ApkTestUtil.getElement(
                                sDevice,
                                R.string.settingsUI_apps_view_no_blocked_apps_text))
                .isNotNull();
        sDevice.pressBack();
    }

    private void resetAppConsent(boolean dialogsOn) {
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_reset_apps_title);

        if (dialogsOn) {
            UiObject2 dialogTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_reset_app_message);
            UiObject2 positiveText =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_reset_app_positive_text);
            assertThat(dialogTitle).isNotNull();
            assertThat(positiveText).isNotNull();

            // confirm
            positiveText.click();
        }
    }

    private void blockAppConsent(boolean dialogsOn) {
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_block_app_title);

        if (dialogsOn) {
            UiObject2 dialogTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_block_app_message);
            UiObject2 positiveText =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_block_app_positive_text);
            assertThat(dialogTitle).isNotNull();
            assertThat(positiveText).isNotNull();
            positiveText.click();
        }
    }

    private void initiateTestAppConsent() throws InterruptedException {
        String installMessage = ShellUtils.runShellCommand("pm install -r " + TEST_APP_APK_PATH);
        assertThat(installMessage).contains("Success");

        flags.setFlag(KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH, false);
        flags.setFlag(KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED, true);
        flags.setFlag(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK, true);
        flags.setPpapiAppAllowList("*");

        Intent intent = new Intent().setComponent(COMPONENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);

        sDevice.wait(Until.hasObject(By.pkg(TEST_APP_NAME).depth(0)), LAUNCH_TIMEOUT);

        Thread.sleep(1000);

        ShellUtils.runShellCommand(
                "am force-stop com.example.adservices.samples.ui.consenttestapp");
    }
}
