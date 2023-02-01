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
import static com.android.adservices.tests.ui.libs.UiConstants.SIM_REGION;
import static com.android.adservices.tests.ui.libs.UiConstants.SYSTEM_UI_NAME;
import static com.android.adservices.tests.ui.libs.UiConstants.SYSTEM_UI_RESOURCE_ID;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
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
        int notificationHeader =
                isEuTest
                        ? R.string.notificationUI_header_title_eu
                        : R.string.notificationUI_header_title;

        UiSelector notificationCardSelector =
                new UiSelector().text(getResourceString(context, notificationTitle));
        Thread.sleep(LAUNCH_TIMEOUT_MS);
        UiObject notificationCard = scroller.getChild(notificationCardSelector);
        if (!isDisplayed) {
            assertThat(notificationCard.exists()).isFalse();
            device.pressHome();
            Thread.sleep(LAUNCH_TIMEOUT_MS);
            return;
        } else {
            assertThat(notificationCard.exists()).isTrue();
        }

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT_MS);
        UiObject title = getUiElement(device, context, notificationHeader);
        assertThat(title.exists()).isTrue();
    }

    public static String getResourceString(Context context, int resourceId) {
        return context.getResources().getString(resourceId);
    }

    public static UiObject getUiElement(UiDevice device, Context context, int resId) {
        return device.findObject(new UiSelector().text(getResourceString(context, resId)));
    }
}
