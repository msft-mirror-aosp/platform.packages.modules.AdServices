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
package com.android.adservices.tests.ui.libs;

import static com.android.adservices.tests.ui.libs.UiConstants.LAUNCH_TIMEOUT_MS;
import static com.android.adservices.tests.ui.libs.UiConstants.NOTIFICATION_LIST_TIMEOUT_MS;
import static com.android.adservices.tests.ui.libs.UiConstants.SIM_REGION;
import static com.android.adservices.tests.ui.libs.UiConstants.SYSTEM_UI_NAME;
import static com.android.adservices.tests.ui.libs.UiConstants.SYSTEM_UI_RESOURCE_ID;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import com.android.adservices.api.R;
import com.android.compatibility.common.util.ShellUtils;

import java.util.HashMap;
import java.util.Map;

public class UiUtils {

    public static Map<String, String> getInitialParams(boolean getSimRegion) {
        Map<String, String> initialParams = new HashMap<String, String>();

        if (getSimRegion) {
            String simRegion = ShellUtils.runShellCommand("getprop gsm.sim.operator.iso-country");
            initialParams.put(SIM_REGION, simRegion);
        }

        return initialParams;
    }

    public static void resetInitialParams(Map<String, String> initialParams) {
        for (String initialParam : initialParams.keySet()) {
            if (initialParam.equals(SIM_REGION)) {
                ShellUtils.runShellCommand(
                        "setprop gsm.sim.operator.iso-country " + initialParams.get(initialParam));
            }
        }
    }

    public static void setAsNonWorkingHours() {
        // set the notification interval start time to 9:00 AM
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_interval_begin_ms 32400000");
        // set the notification interval end time to 5:00 PM
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_interval_end_ms 61200000");
        ShellUtils.runShellCommand("date 00:00");
    }

    public static void disableSchedulingParams() {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_interval_begin_ms 0");
        // set the notification interval end time to 12:00 AM
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_interval_end_ms 86400000");

        ShellUtils.runShellCommand(
                "device_config put adservices"
                        + " consent_notification_minimal_delay_before_interval_ends 0");
    }

    public static void enableConsentDebugMode() {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_debug_mode true");
    }

    public static void disableConsentDebugMode() {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_debug_mode false");
    }

    public static void disableGlobalKillswitch() {
        ShellUtils.runShellCommand("device_config put adservices global_kill_switch false");
    }

    public static void enableGlobalKillSwitch() {
        ShellUtils.runShellCommand("device_config put adservices global_kill_switch true");
    }

    public static void setAsEuDevice() {
        ShellUtils.runShellCommand("setprop gsm.sim.operator.iso-country DE");
    }

    public static void setAsRowDevice() {
        ShellUtils.runShellCommand("setprop gsm.sim.operator.iso-country ROW");
    }

    public static void enableGaUxFeature() {
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
    }

    public static void disableGaUxFeature() {
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled false");
    }

    public static void restartAdservices() {
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");
    }

    public static void clearSavedStatus() {
        ShellUtils.runShellCommand(
                "rm /data/user/0/com.google.android.adservices.api/files/"
                        + "ConsentManagerStorageIdentifier.xml");
        ShellUtils.runShellCommand(
                "rm /data/system/adservices/0/consent/ConsentManagerStorageIdentifier.xml");
    }

    public static void setSourceOfTruthToPPAPI() {
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 1");
    }

    public static void verifyNotification(
            Context context, UiDevice device, boolean isDisplayed, boolean isEuTest)
            throws Exception {
        device.openNotification();
        Thread.sleep(LAUNCH_TIMEOUT_MS);
        UiObject scroller =
                device.findObject(
                        new UiSelector()
                                .packageName(SYSTEM_UI_NAME)
                                .resourceId(SYSTEM_UI_RESOURCE_ID));
        assertThat(scroller.exists()).isTrue();

        int notificationTitle =
                isEuTest
                        ? R.string.notificationUI_notification_title_eu
                        : R.string.notificationUI_notification_title;

        UiSelector notificationCardSelector =
                new UiSelector().text(getResourceString(context, notificationTitle));
        Thread.sleep(NOTIFICATION_LIST_TIMEOUT_MS);
        UiObject notificationCard = scroller.getChild(notificationCardSelector);
        if (!isDisplayed) {
            assertThat(notificationCard.exists()).isFalse();
            device.pressHome();
            return;
        } else {
            assertThat(notificationCard.exists()).isTrue();
        }

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT_MS);
    }

    public static void verifyGaUxNotification(
            Context context, UiDevice device, boolean isDisplayed, boolean isEuTest)
            throws Exception {
        device.openNotification();
        Thread.sleep(LAUNCH_TIMEOUT_MS);
        UiObject scroller =
                device.findObject(
                        new UiSelector()
                                .packageName(SYSTEM_UI_NAME)
                                .resourceId(SYSTEM_UI_RESOURCE_ID));
        assertThat(scroller.exists()).isTrue();

        int notificationTitle =
                isEuTest
                        ? R.string.notificationUI_notification_ga_title_eu
                        : R.string.notificationUI_notification_ga_title;

        UiSelector notificationCardSelector =
                new UiSelector().text(getResourceString(context, notificationTitle));
        Thread.sleep(NOTIFICATION_LIST_TIMEOUT_MS);
        UiObject notificationCard = scroller.getChild(notificationCardSelector);
        if (!isDisplayed) {
            assertThat(notificationCard.exists()).isFalse();
            device.pressHome();
            return;
        } else {
            assertThat(notificationCard.exists()).isTrue();
        }

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT_MS);
    }

    public static void consentConfirmationScreen(
            Context context, UiDevice device, boolean isEuDevice, boolean dialogsOn)
            throws UiObjectNotFoundException, InterruptedException {
        UiObject leftControlButton =
                getUiElement(device, context, R.string.notificationUI_left_control_button_text_eu);
        UiObject rightControlButton =
                getUiElement(device, context, R.string.notificationUI_right_control_button_text_eu);
        UiObject moreButton =
                getUiElement(device, context, R.string.notificationUI_more_button_text);
        assertThat(leftControlButton.exists()).isFalse();
        assertThat(rightControlButton.exists()).isFalse();
        assertThat(moreButton.exists()).isTrue();

        while (moreButton.exists()) {
            moreButton.click();
            Thread.sleep(1000);
        }

        // Because the activity bundle cannot be reset by clean the xml file and
        // restart adservices, we getting same beta notification display page, will
        // check content of the button to decide it is EU or ROW, and set the consent.
        if (rightControlButton.exists()) {
            isEuDevice = true;
        } else {
            leftControlButton =
                    getUiElement(device, context, R.string.notificationUI_left_control_button_text);
            rightControlButton =
                    getUiElement(
                            device, context, R.string.notificationUI_right_control_button_text);
            isEuDevice = false;
        }

        assertThat(leftControlButton.exists()).isTrue();
        assertThat(rightControlButton.exists()).isTrue();
        assertThat(moreButton.exists()).isFalse();

        if (isEuDevice) {
            if (!dialogsOn) {
                leftControlButton.click();
            } else {
                rightControlButton.click();
            }
        } else {
            leftControlButton.click();
            Thread.sleep(1000);
            UiObject mainSwitch =
                    device.findObject(new UiSelector().className("android.widget.Switch"));
            assertThat(mainSwitch.exists()).isTrue();
            if (dialogsOn) {
                if (!mainSwitch.isChecked()) {
                    performSwitchClick(device, context, dialogsOn, mainSwitch);
                }
                assertThat(mainSwitch.isChecked()).isTrue();
            } else {
                if (mainSwitch.isChecked()) {
                    performSwitchClick(device, context, dialogsOn, mainSwitch);
                }
                assertThat(mainSwitch.isChecked()).isFalse();
            }
        }
    }

    public static String getResourceString(Context context, int resourceId) {
        return context.getResources().getString(resourceId);
    }

    public static UiObject getUiElement(UiDevice device, Context context, int resId) {
        return device.findObject(new UiSelector().text(getResourceString(context, resId)));
    }

    public static void performSwitchClick(
            UiDevice device, Context context, boolean dialogsOn, UiObject mainSwitch)
            throws UiObjectNotFoundException {
        if (dialogsOn && mainSwitch.isChecked()) {
            mainSwitch.click();
            UiObject dialogTitle =
                    getUiElement(device, context, R.string.settingsUI_dialog_opt_out_title);
            UiObject positiveText =
                    getUiElement(device, context, R.string.settingsUI_dialog_opt_out_positive_text);
            assertThat(dialogTitle.exists()).isTrue();
            assertThat(positiveText.exists()).isTrue();
            positiveText.click();
        } else {
            mainSwitch.click();
        }
    }
}
