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
public class SettingsGaUiAutomatorTest {
    private static final String PRIVACY_SANDBOX_TEST_PACKAGE = "android.adservices.ui.SETTINGS";
    private static final int LAUNCH_TIMEOUT = 5000;
    public static final int PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT = 1000;
    private static UiDevice sDevice;

    @Before
    public void setup() {
        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        sDevice.pressHome();

        // Wait for launcher
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);
    }

    @After
    public void teardown() {
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");
    }

    @Test
    public void settingsRemoveMainToggleAndMeasurementEntryTest() throws UiObjectNotFoundException {
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");

        launchApp();
        // no main switch any more
        UiObject mainSwitch =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        assertThat(mainSwitch.exists()).isFalse();

        // make sure we are on the main settings page
        UiObject appButton = getElement(R.string.settingsUI_apps_ga_title);
        assertThat(appButton.exists()).isTrue();
        UiObject topicsButton = getElement(R.string.settingsUI_topics_ga_title);
        assertThat(topicsButton.exists()).isTrue();

        // click measurement page
        scrollToAndClick(R.string.settingsUI_measurement_view_title);

        // verify have entered to measurement page
        UiObject measurementSwitch = getElement(R.string.settingsUI_measurement_switch_title);
        // needed as the new page is displayed (this can take time to propagate to the UiAutomator)
        measurementSwitch.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(measurementSwitch.exists()).isTrue();

        sDevice.pressBack();
        // verify back to the main page
        assertThat(appButton.exists()).isTrue();
    }

    @Test
    public void measurementDialogTest() throws UiObjectNotFoundException {
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");

        launchApp();
        // open measurement view
        scrollToAndClick(R.string.settingsUI_measurement_view_title);

        // click reset
        scrollToAndClick(R.string.settingsUI_measurement_view_reset_title);
        UiObject resetButton =
                sDevice.findObject(
                        new UiSelector()
                                .childSelector(
                                        new UiSelector()
                                                .text(
                                                        getString(
                                                                R.string
                                                                        .settingsUI_measurement_view_reset_title))));
        assertThat(resetButton.exists()).isTrue();

        // click reset again
        scrollToAndClick(R.string.settingsUI_measurement_view_reset_title);
        resetButton =
                sDevice.findObject(
                        new UiSelector()
                                .childSelector(
                                        new UiSelector()
                                                .text(
                                                        getString(
                                                                R.string
                                                                        .settingsUI_measurement_view_reset_title))));
        assertThat(resetButton.exists()).isTrue();
    }

    @Test
    public void togglesTestWithDialogs() throws UiObjectNotFoundException {
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");

        launchApp();
        // 1) revoke Topics if given
        scrollToAndClick(R.string.settingsUI_topics_ga_title);

        UiObject topicsToggle =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        if (topicsToggle.isChecked()) {
            topicsToggle.click();
        }
        assertThat(topicsToggle.isChecked()).isFalse();
        sDevice.pressBack();
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");

        launchApp();

        // 2) revoke Fledge if given
        scrollToAndClick(R.string.settingsUI_apps_ga_title);

        UiObject appsToggle =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        if (appsToggle.isChecked()) {
            appsToggle.click();
        }
        assertThat(appsToggle.isChecked()).isFalse();
        sDevice.pressBack();
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");

        launchApp();
        // 3) revoke Measurement if given
        scrollToAndClick(R.string.settingsUI_measurement_view_title);

        UiObject measurementToggle =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        if (measurementToggle.isChecked()) {
            measurementToggle.click();
        }
        assertThat(measurementToggle.isChecked()).isFalse();
        sDevice.pressBack();
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");

        launchApp();
        // 4) set consent to given for measurement
        scrollToAndClick(R.string.settingsUI_measurement_view_title);
        measurementToggle = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        assertThat(measurementToggle.isChecked()).isFalse();
        measurementToggle.click();
        assertThat(measurementToggle.isChecked()).isTrue();
        sDevice.pressBack();
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");

        launchApp();
        // 5) check revoked consent for Topics
        scrollToAndClick(R.string.settingsUI_topics_ga_title);
        topicsToggle = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        assertThat(topicsToggle.isChecked()).isFalse();
        sDevice.pressBack();
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");

        launchApp();
        // 6) check revoked consent for Fledge
        scrollToAndClick(R.string.settingsUI_apps_ga_title);
        appsToggle = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        assertThat(appsToggle.isChecked()).isFalse();
    }

    @Test
    public void togglesTestWithoutDialogs() throws UiObjectNotFoundException {
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        launchApp();
        // 1) revoke Topics if given
        scrollToAndClick(R.string.settingsUI_topics_ga_title);
        UiObject topicsToggle =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        if (topicsToggle.isChecked()) {
            topicsToggle.click();
        }
        assertThat(topicsToggle.isChecked()).isFalse();
        sDevice.pressBack();

        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");

        launchApp();
        // 2) revoke Fledge if given
        scrollToAndClick(R.string.settingsUI_apps_ga_title);

        UiObject appsToggle =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        if (appsToggle.isChecked()) {
            appsToggle.click();
        }
        assertThat(appsToggle.isChecked()).isFalse();
        sDevice.pressBack();
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");

        launchApp();
        // 3) revoke Msmt if given
        scrollToAndClick(R.string.settingsUI_measurement_view_title);

        UiObject measurementToggle =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        if (measurementToggle.isChecked()) {
            measurementToggle.click();
        }
        assertThat(measurementToggle.isChecked()).isFalse();
        sDevice.pressBack();
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");

        launchApp();
        // 4) set consent to given for measurement
        scrollToAndClick(R.string.settingsUI_measurement_view_title);
        measurementToggle = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        assertThat(measurementToggle.isChecked()).isFalse();
        measurementToggle.click();
        assertThat(measurementToggle.isChecked()).isTrue();
        sDevice.pressBack();
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");

        launchApp();
        // 5) check revoked consent for Topics
        scrollToAndClick(R.string.settingsUI_topics_ga_title);
        topicsToggle = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        assertThat(topicsToggle.isChecked()).isFalse();
        sDevice.pressBack();
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");

        launchApp();
        // 6) check revoked consent for Fledge
        scrollToAndClick(R.string.settingsUI_apps_ga_title);
        appsToggle = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        assertThat(appsToggle.isChecked()).isFalse();
    }

    @Test
    public void topicsSubTitleTest() throws UiObjectNotFoundException {
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        launchApp();
        checkSubtitleMatchesToggle(
                ".*:id/topics_preference_subtitle", R.string.settingsUI_topics_ga_title);
    }

    @Test
    public void appsSubTitleTest() throws UiObjectNotFoundException {
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        launchApp();
        checkSubtitleMatchesToggle(
                ".*:id/apps_preference_subtitle", R.string.settingsUI_apps_ga_title);
    }

    @Test
    public void measurementSubTitleTest() throws UiObjectNotFoundException {
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        launchApp();
        checkSubtitleMatchesToggle(
                ".*:id/measurement_preference_subtitle",
                R.string.settingsUI_measurement_view_title);
    }

    private void checkSubtitleMatchesToggle(String regexResId, int stringIdOfTitle)
            throws UiObjectNotFoundException {
        UiScrollable scrollView =
                new UiScrollable(
                        new UiSelector().scrollable(true).className("android.widget.ScrollView"));
        UiObject thisSubtitle = sDevice.findObject(new UiSelector().resourceIdMatches(regexResId));
        scrollView.scrollIntoView(thisSubtitle);
        if (thisSubtitle.getText().equals("Off")) {
            scrollToAndClick(stringIdOfTitle);
            UiObject thisToggle =
                    sDevice.findObject(new UiSelector().className("android.widget.Switch"));
            assertThat(thisToggle.isChecked()).isFalse();
            thisToggle.click();
            sDevice.pressBack();
            assertThat(thisSubtitle.getText().equals("Off")).isFalse();
        } else {
            scrollToAndClick(stringIdOfTitle);
            UiObject thisToggle =
                    sDevice.findObject(new UiSelector().className("android.widget.Switch"));
            assertThat(thisToggle.isChecked()).isTrue();
            thisToggle.click();
            sDevice.pressBack();
            assertThat(thisSubtitle.getText().equals("Off")).isTrue();
        }
    }

    private UiObject getElement(int resId) {
        return sDevice.findObject(new UiSelector().text(getString(resId)));
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
                        new UiSelector().childSelector(new UiSelector().text(getString(resId))));
        scrollView.scrollIntoView(element);
        element.click();
    }

    private void launchApp() {
        // launch app
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(PRIVACY_SANDBOX_TEST_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        // Wait for the app to appear
        sDevice.wait(
                Until.hasObject(By.pkg(PRIVACY_SANDBOX_TEST_PACKAGE).depth(0)), LAUNCH_TIMEOUT);
    }
}
