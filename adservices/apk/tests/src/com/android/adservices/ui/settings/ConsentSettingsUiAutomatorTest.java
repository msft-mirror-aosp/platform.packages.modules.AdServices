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

import android.content.Context;
import android.content.Intent;

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
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ConsentSettingsUiAutomatorTest {
    private static final String PRIVACY_SANDBOX_TEST_PACKAGE = "android.adservices.ui.SETTINGS";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static UiDevice sDevice;

    @Before
    public void setup() {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(ApkTestUtil.isDeviceSupported());

        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        sDevice.pressHome();

        // Wait for launcher
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled false");
    }

    @After
    public void teardown() {
        if (!ApkTestUtil.isDeviceSupported()) return;
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");
    }

    @Test
    public void consentSystemServerOnlyTest() throws UiObjectNotFoundException {
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 0");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        consentTest(false);
    }

    @Test
    public void consentPpApiOnlyTest() throws UiObjectNotFoundException {
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 1");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        consentTest(false);
    }

    @Test
    public void consentSystemServerAndPpApiTest() throws UiObjectNotFoundException {
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 2");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        consentTest(false);
    }

    @Test
    public void consentSystemServerOnlyDialogsOnTest() throws UiObjectNotFoundException {
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 0");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        consentTest(true);
    }

    @Test
    public void consentPpApiOnlyDialogsOnTest() throws UiObjectNotFoundException {
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 1");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        consentTest(true);
    }

    @Test
    public void consentSystemServerAndPpApiDialogsOnTest() throws UiObjectNotFoundException {
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 2");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        consentTest(true);
    }

    private void consentTest(boolean dialogsOn) throws UiObjectNotFoundException {
        // launch app
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(PRIVACY_SANDBOX_TEST_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        // Wait for the app to appear
        sDevice.wait(
                Until.hasObject(By.pkg(PRIVACY_SANDBOX_TEST_PACKAGE).depth(0)), LAUNCH_TIMEOUT);

        UiObject mainSwitch =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        assertThat(mainSwitch.exists()).isTrue();

        setConsentToFalse(dialogsOn);

        // click switch
        performSwitchClick(dialogsOn, mainSwitch);
        assertThat(mainSwitch.isChecked()).isTrue();

        // click switch
        performSwitchClick(dialogsOn, mainSwitch);
        assertThat(mainSwitch.isChecked()).isFalse();
    }

    private void setConsentToFalse(boolean dialogsOn) throws UiObjectNotFoundException {
        UiObject mainSwitch =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        if (mainSwitch.isChecked()) {
            performSwitchClick(dialogsOn, mainSwitch);
        }
    }

    private void performSwitchClick(boolean dialogsOn, UiObject mainSwitch)
            throws UiObjectNotFoundException {
        if (dialogsOn && mainSwitch.isChecked()) {
            mainSwitch.click();
            UiObject dialogTitle = getElement(R.string.settingsUI_dialog_opt_out_title);
            UiObject positiveText = getElement(R.string.settingsUI_dialog_opt_out_positive_text);
            assertThat(dialogTitle.exists()).isTrue();
            assertThat(positiveText.exists()).isTrue();
            positiveText.click();
        } else {
            mainSwitch.click();
        }
    }

    private UiObject getElement(int resId) {
        return sDevice.findObject(new UiSelector().text(getString(resId)));
    }

    private String getString(int resourceId) {
        return ApplicationProvider.getApplicationContext().getResources().getString(resourceId);
    }
}
