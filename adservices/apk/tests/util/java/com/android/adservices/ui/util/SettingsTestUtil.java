/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.ui.util;

import static com.android.adservices.ui.util.AdServicesUiTestCase.LAUNCH_TIMEOUT;
import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.os.Build;
import android.os.RemoteException;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;

/** Util class for Settings tests. */
public final class SettingsTestUtil {

    private static final String ANDROID_WIDGET_SWITCH = "android.widget.Switch";

    private static final String TAG = SettingsTestUtil.class.getSimpleName();
    private static final int WINDOW_LAUNCH_TIMEOUT = 2_000;
    private static final int PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS = 2_000;

    public static void settingsRemoveMainToggleAndMeasurementEntryTestUtil(
            Context context, UiDevice device) throws InterruptedException {
        ApkTestUtil.launchSettingView(context, device, LAUNCH_TIMEOUT);

        // make sure we are on the main settings page
        UiObject2 appButton =
                ApkTestUtil.scrollTo(context, device, R.string.settingsUI_apps_ga_title);
        assertNotNull(context, appButton, R.string.settingsUI_apps_ga_title);

        UiObject2 topicsButton =
                ApkTestUtil.scrollTo(context, device, R.string.settingsUI_topics_ga_title);
        assertNotNull(context, topicsButton, R.string.settingsUI_topics_ga_title);

        // click measurement page
        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_measurement_view_title);

        // verify have entered to measurement page
        UiObject2 measurementSwitch =
                ApkTestUtil.getElement(
                        context, device, R.string.settingsUI_measurement_switch_title);
        assertNotNull(context, measurementSwitch, R.string.settingsUI_measurement_switch_title);

        pressBack(device);
        // verify back to the main page
        assertNotNull(context, appButton, R.string.settingsUI_apps_ga_title);
    }

    public static void settingsRemoveMainToggleAndMeasurementEntryTestRvcUxUtil(
            Context context, UiDevice device) throws InterruptedException {
        ApkTestUtil.launchSettingView(context, device, LAUNCH_TIMEOUT);

        UiObject2 appButton =
                ApkTestUtil.scrollTo(context, device, R.string.settingsUI_measurement_view_title);
        assertNotNull(context, appButton, R.string.settingsUI_measurement_view_title);

        // make sure we are on the main settings page
        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_measurement_view_title);

        // verify have entered to measurement page
        UiObject2 measurementSwitch =
                ApkTestUtil.getElement(
                        context, device, R.string.settingsUI_measurement_switch_title);
        assertNotNull(context, measurementSwitch, R.string.settingsUI_measurement_switch_title);

        pressBack(device);
        // verify back to the main page
        assertNotNull(context, appButton, R.string.settingsUI_measurement_view_title);
    }

    public static void measurementDialogTestUtil(Context context, UiDevice device)
            throws InterruptedException, UiObjectNotFoundException, RemoteException {
        runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        device.setOrientationNatural();
        ApkTestUtil.launchSettingView(context, device, LAUNCH_TIMEOUT);
        // open measurement view
        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_measurement_view_title);

        // click reset
        SettingsTestUtil.clickResetButton(context, device);
        UiObject2 resetButton =
                ApkTestUtil.getElement(
                        context, device, R.string.settingsUI_measurement_view_reset_title);
        assertNotNull(context, resetButton, R.string.settingsUI_measurement_view_reset_title);

        // click reset again
        SettingsTestUtil.clickResetButton(context, device);
        resetButton =
                ApkTestUtil.getElement(
                        context, device, R.string.settingsUI_measurement_view_reset_title);
        assertNotNull(context, resetButton, R.string.settingsUI_measurement_view_reset_title);
    }

    public static void topicsToggleTestUtil(Context context, UiDevice device)
            throws InterruptedException, RemoteException {
        runShellCommand("device_config put adservices ui_toggle_speed_bump_enabled false");

        ApkTestUtil.launchSettingView(context, device, LAUNCH_TIMEOUT);
        // 1) disable Topics API is enabled
        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_topics_ga_title);

        UiObject2 topicsToggle = getToggleSwitch(device);
        if (topicsToggle.isChecked()) {
            topicsToggle.clickAndWait(Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        }
        assertToggleState(topicsToggle, /* checked= */ false);
        pressBack(device);

        // 2) enable Topics API
        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_topics_ga_title);

        topicsToggle = getToggleSwitch(device);
        assertToggleState(topicsToggle, /* checked= */ false);
        topicsToggle.clickAndWait(Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        assertToggleState(topicsToggle, /* checked= */ true);
        pressBack(device);

        // 3) check if Topics API is enabled
        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_topics_ga_title);
        // rotate device to test rotating as well
        device.setOrientationLeft();
        device.setOrientationNatural();
        topicsToggle = getToggleSwitch(device);
        assertToggleState(topicsToggle, /* checked= */ true);
        pressBack(device);
    }

    public static void fledgeToggleTestUtil(Context context, UiDevice device)
            throws InterruptedException, RemoteException {
        runShellCommand("device_config put adservices ui_toggle_speed_bump_enabled false");

        ApkTestUtil.launchSettingView(context, device, LAUNCH_TIMEOUT);
        // 1) disable Fledge API is enabled
        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_apps_ga_title);

        UiObject2 fledgeToggle = getToggleSwitch(device);
        if (fledgeToggle.isChecked()) {
            fledgeToggle.clickAndWait(Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        }
        assertToggleState(fledgeToggle, /* checked= */ false);
        pressBack(device);

        // 2) enable Fledge API
        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_apps_ga_title);

        fledgeToggle = getToggleSwitch(device);
        assertToggleState(fledgeToggle, /* checked= */ false);
        fledgeToggle.clickAndWait(Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        assertToggleState(fledgeToggle, /* checked= */ true);
        pressBack(device);

        // 3) check if Fledge API is enabled
        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_apps_ga_title);
        // rotate device to test rotating as well
        device.setOrientationLeft();
        device.setOrientationNatural();
        fledgeToggle = getToggleSwitch(device);
        assertToggleState(fledgeToggle, /* checked= */ true);
        pressBack(device);
    }

    public static void measurementToggleTestUtil(Context context, UiDevice device)
            throws InterruptedException, RemoteException {
        runShellCommand("device_config put adservices ui_toggle_speed_bump_enabled false");

        ApkTestUtil.launchSettingView(context, device, LAUNCH_TIMEOUT);
        // 1) disable Measurement API is enabled
        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_measurement_view_title);

        UiObject2 measurementToggle = getToggleSwitch(device);
        if (measurementToggle.isChecked()) {
            measurementToggle.clickAndWait(
                    Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        }
        assertToggleState(measurementToggle, /* checked= */ false);
        pressBack(device);

        // 2) enable Measurement API
        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_measurement_view_title);

        measurementToggle = getToggleSwitch(device);
        assertToggleState(measurementToggle, /* checked= */ false);
        measurementToggle.clickAndWait(Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        assertToggleState(measurementToggle, /* checked= */ true);
        pressBack(device);

        // 3) check if Measurement API is enabled
        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_measurement_view_title);
        // rotate device to test rotating as well
        device.setOrientationLeft();
        device.setOrientationNatural();
        measurementToggle = getToggleSwitch(device);
        assertToggleState(measurementToggle, /* checked= */ true);
        pressBack(device);
    }

    public static void topicsSubTitleTestUtil(Context context, UiDevice device)
            throws InterruptedException {
        runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        runShellCommand("device_config put adservices ui_toggle_speed_bump_enabled false");

        ApkTestUtil.launchSettingView(
                ApplicationProvider.getApplicationContext(), device, LAUNCH_TIMEOUT);
        SettingsTestUtil.checkSubtitleMatchesToggle(
                context,
                device,
                ".*:id/topics_preference_subtitle",
                R.string.settingsUI_topics_ga_title);
    }

    public static void appsSubTitleTestUtil(Context context, UiDevice device)
            throws InterruptedException {
        runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        runShellCommand("device_config put adservices ui_toggle_speed_bump_enabled false");

        ApkTestUtil.launchSettingView(
                ApplicationProvider.getApplicationContext(), device, LAUNCH_TIMEOUT);
        SettingsTestUtil.checkSubtitleMatchesToggle(
                context,
                device,
                ".*:id/apps_preference_subtitle",
                R.string.settingsUI_apps_ga_title);
    }

    public static void measurementSubTitleTestUtil(Context context, UiDevice device)
            throws InterruptedException {
        runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        runShellCommand("device_config put adservices ui_toggle_speed_bump_enabled false");

        ApkTestUtil.launchSettingView(
                ApplicationProvider.getApplicationContext(), device, LAUNCH_TIMEOUT);
        SettingsTestUtil.checkSubtitleMatchesToggle(
                context,
                device,
                ".*:id/measurement_preference_subtitle",
                R.string.settingsUI_measurement_view_title);
    }

    public static void topicsToggleDialogTestUtil(Context context, UiDevice device)
            throws InterruptedException {
        runShellCommand("device_config put adservices ui_toggle_speed_bump_enabled true");
        ApkTestUtil.launchSettingView(context, device, LAUNCH_TIMEOUT);

        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_topics_ga_title);

        UiObject2 topicsToggle = getToggleSwitch(device);
        if (topicsToggle.isChecked()) {
            // turn it off
            topicsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptOutTitle =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_topics_opt_out_title);
            UiObject2 positiveButton =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_opt_out_positive_text);
            assertNotNull(
                    context, dialogOptOutTitle, R.string.settingsUI_dialog_topics_opt_out_title);
            positiveButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            topicsToggle = getToggleSwitch(device);
            assertToggleState(topicsToggle, /* checked= */ false);
            // then turn it on again
            topicsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptInTitle =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_topics_opt_in_title);
            UiObject2 okButton =
                    ApkTestUtil.getElement(context, device, R.string.settingsUI_dialog_acknowledge);
            assertNotNull(
                    context, dialogOptInTitle, R.string.settingsUI_dialog_topics_opt_in_title);
            okButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            topicsToggle = getToggleSwitch(device);
            assertToggleState(topicsToggle, /* checked= */ true);
        } else {
            // turn it on
            topicsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptInTitle =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_topics_opt_in_title);
            UiObject2 okButton =
                    ApkTestUtil.getElement(context, device, R.string.settingsUI_dialog_acknowledge);
            assertNotNull(
                    context, dialogOptInTitle, R.string.settingsUI_dialog_topics_opt_in_title);
            okButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            topicsToggle = getToggleSwitch(device);
            assertToggleState(topicsToggle, /* checked= */ true);
            // then turn it off
            topicsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptOutTitle =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_topics_opt_out_title);
            UiObject2 positiveButton =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_opt_out_positive_text);
            assertNotNull(
                    context, dialogOptOutTitle, R.string.settingsUI_dialog_topics_opt_out_title);
            positiveButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            topicsToggle = getToggleSwitch(device);
            assertToggleState(topicsToggle, /* checked= */ false);
        }
    }

    public static void appsToggleDialogTestUtil(Context context, UiDevice device)
            throws InterruptedException {
        runShellCommand("device_config put adservices ui_toggle_speed_bump_enabled true");
        ApkTestUtil.launchSettingView(context, device, LAUNCH_TIMEOUT);

        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_apps_ga_title);

        UiObject2 appsToggle = getToggleSwitch(device);
        if (appsToggle.isChecked()) {
            // turn it off
            appsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptOutTitle =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_apps_opt_out_title);
            UiObject2 positiveButton =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_opt_out_positive_text);
            assertNotNull(
                    context, dialogOptOutTitle, R.string.settingsUI_dialog_apps_opt_out_title);
            positiveButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            appsToggle = getToggleSwitch(device);
            assertToggleState(appsToggle, /* checked= */ false);
            // then turn it on again
            appsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptInTitle =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_apps_opt_in_title);
            UiObject2 okButton =
                    ApkTestUtil.getElement(context, device, R.string.settingsUI_dialog_acknowledge);
            assertNotNull(context, dialogOptInTitle, R.string.settingsUI_dialog_apps_opt_in_title);
            okButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            appsToggle = getToggleSwitch(device);
            assertToggleState(appsToggle, /* checked= */ true);
        } else {
            // turn it on
            appsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptInTitle =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_apps_opt_in_title);
            UiObject2 okButton =
                    ApkTestUtil.getElement(context, device, R.string.settingsUI_dialog_acknowledge);
            assertNotNull(context, dialogOptInTitle, R.string.settingsUI_dialog_apps_opt_in_title);
            okButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            appsToggle = getToggleSwitch(device);
            assertToggleState(appsToggle, /* checked= */ true);
            // then turn it off
            appsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptOutTitle =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_apps_opt_out_title);
            UiObject2 positiveButton =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_opt_out_positive_text);
            assertNotNull(
                    context, dialogOptOutTitle, R.string.settingsUI_dialog_apps_opt_out_title);
            positiveButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            appsToggle = getToggleSwitch(device);
            assertToggleState(appsToggle, /* checked= */ false);
        }
    }

    public static void measurementToggleDialogTestUtil(Context context, UiDevice device)
            throws InterruptedException {
        runShellCommand("device_config put adservices ui_toggle_speed_bump_enabled true");
        ApkTestUtil.launchSettingView(context, device, LAUNCH_TIMEOUT);

        ApkTestUtil.scrollToAndClick(context, device, R.string.settingsUI_measurement_ga_title);

        UiObject2 measurementToggle = getToggleSwitch(device);

        if (measurementToggle.isChecked()) {
            // turn it off
            measurementToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptOutTitle =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_measurement_opt_out_title);
            UiObject2 positiveButton =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_opt_out_positive_text);
            assertNotNull(
                    context,
                    dialogOptOutTitle,
                    R.string.settingsUI_dialog_measurement_opt_out_title);
            positiveButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            measurementToggle = getToggleSwitch(device);
            assertToggleState(measurementToggle, /* checked= */ false);
            // then turn it on again
            measurementToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptInTitle =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_measurement_opt_in_title);
            UiObject2 okButton =
                    ApkTestUtil.getElement(context, device, R.string.settingsUI_dialog_acknowledge);
            assertNotNull(
                    context, dialogOptInTitle, R.string.settingsUI_dialog_measurement_opt_in_title);
            okButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            measurementToggle = getToggleSwitch(device);
            assertToggleState(measurementToggle, /* checked= */ true);
        } else {
            // turn it on
            measurementToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptInTitle =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_measurement_opt_in_title);
            UiObject2 okButton =
                    ApkTestUtil.getElement(context, device, R.string.settingsUI_dialog_acknowledge);
            assertNotNull(
                    context, dialogOptInTitle, R.string.settingsUI_dialog_measurement_opt_in_title);
            okButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            measurementToggle = getToggleSwitch(device);
            assertToggleState(measurementToggle, /* checked= */ true);
            // then turn it off
            measurementToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptOutTitle =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_measurement_opt_out_title);
            UiObject2 positiveButton =
                    ApkTestUtil.getElement(
                            context, device, R.string.settingsUI_dialog_opt_out_positive_text);
            assertNotNull(
                    context,
                    dialogOptOutTitle,
                    R.string.settingsUI_dialog_measurement_opt_out_title);
            positiveButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            measurementToggle = getToggleSwitch(device);
            assertToggleState(measurementToggle, /* checked= */ false);
        }
    }

    public static void checkSubtitleMatchesToggle(
            Context context, UiDevice device, String regexResId, int stringIdOfTitle)
            throws InterruptedException {
        UiObject2 subtitle = ApkTestUtil.scrollTo(device, regexResId);
        if (subtitle.getText()
                .equals(ApkTestUtil.getString(R.string.settingsUI_subtitle_consent_off))) {
            ApkTestUtil.scrollToAndClick(context, device, stringIdOfTitle);
            UiObject2 toggle = getToggleSwitch(device);
            assertToggleState(toggle, /* checked= */ false);
            toggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            pressBack(device);
            subtitle = ApkTestUtil.getElement(device, regexResId);
            assertThat(
                            subtitle.getText()
                                    .equals(
                                            ApkTestUtil.getString(
                                                    R.string.settingsUI_subtitle_consent_off)))
                    .isFalse();
        } else {
            ApkTestUtil.scrollToAndClick(context, device, stringIdOfTitle);
            UiObject2 toggle = getToggleSwitch(device);
            assertToggleState(toggle, /* checked= */ true);
            toggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            pressBack(device);
            subtitle = ApkTestUtil.getElement(device, regexResId);
            assertThat(
                            subtitle.getText()
                                    .equals(
                                            ApkTestUtil.getString(
                                                    R.string.settingsUI_subtitle_consent_off)))
                    .isTrue();
        }
    }

    public static void clickResetButton(Context context, UiDevice device) {
        // R Msmt UI is not scrollable
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            ApkTestUtil.click(device, R.string.settingsUI_measurement_view_reset_title);
        } else {
            ApkTestUtil.scrollToAndClick(
                    context, device, R.string.settingsUI_measurement_view_reset_title);
        }
    }

    private static void assertNotNull(Context context, UiObject2 object, int resId) {
        assertWithMessage("Button with text %s ", ApkTestUtil.getString(context, resId))
                .that(object)
                .isNotNull();
    }

    private static void assertToggleState(UiObject2 toggleSwitch, boolean checked) {
        if (checked) {
            assertWithMessage("Toggle switch checked").that(toggleSwitch.isChecked()).isTrue();
        } else {
            assertWithMessage("Toggle switch checked").that(toggleSwitch.isChecked()).isFalse();
        }
    }

    /** Creates a toggleable button. */
    private static UiObject2 getToggleSwitch(UiDevice device) {
        return device.wait(
                Until.findObject(By.clazz(ANDROID_WIDGET_SWITCH)),
                PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
    }

    /** Presses the Back button. */
    public static void pressBack(UiDevice device) {
        Log.d(TAG, "pressBack()");
        device.pressBack();
    }
}
