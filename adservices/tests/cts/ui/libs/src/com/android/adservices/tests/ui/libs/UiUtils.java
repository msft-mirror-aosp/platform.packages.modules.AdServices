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

import static android.Manifest.permission.POST_NOTIFICATIONS;

import static com.android.adservices.tests.ui.libs.UiConstants.AD_ID_ENABLED;
import static com.android.adservices.tests.ui.libs.UiConstants.ENTRY_POINT_ENABLED;
import static com.android.adservices.tests.ui.libs.UiConstants.LAUNCH_TIMEOUT_MS;
import static com.android.adservices.tests.ui.libs.UiConstants.PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS;
import static com.android.adservices.tests.ui.libs.UiConstants.SCROLL_TIMEOUT;
import static com.android.adservices.tests.ui.libs.UiConstants.SYSTEM_UI_NAME;
import static com.android.adservices.tests.ui.libs.UiConstants.SYSTEM_UI_RESOURCE_ID;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.adservices.LogUtil;
import com.android.adservices.api.R;
import com.android.compatibility.common.util.ShellUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class UiUtils {
    private static final String PRIVACY_SANDBOX_PACKAGE_NAME = "android.adservices.ui.SETTINGS";
    private static final String NOTIFICATION_PACKAGE_NAME = "android.adservices.ui.NOTIFICATIONS";
    public static final int LAUNCH_TIMEOUT = 5000;
    private static final int LONG_TIMEOUT = 15000;
    public static final int PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT = 500;
    public static final int SCROLL_WAIT_TIME = 1000;
    private static final int MAX_SWIPES = 7;

    private static final String ANDROID_WIDGET_SCROLLVIEW = "android.widget.ScrollView";

    private static void forceSetFlag(String flagName, boolean newFlagValue) throws Exception {
        String currentFlagValue =
                ShellUtils.runShellCommand("device_config get adservices " + flagName);

        for (int i = 0; i < 5; i++) {
            if (currentFlagValue.equals(String.valueOf(newFlagValue))) {
                return;
            }

            ShellUtils.runShellCommand(
                    String.format("device_config put adservices %s %s", flagName, newFlagValue));

            Thread.sleep(250);

            currentFlagValue =
                    ShellUtils.runShellCommand("device_config get adservices " + flagName);

            LogUtil.e(String.format("Flag was not set on iteration %d.", i));
        }

        throw new IllegalStateException("Unable to set flag in 5 iterations.");
    }

    public static void refreshConsentResetToken() {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_reset_token "
                        + UUID.randomUUID().toString());
    }

    public static void turnOffEnableAdsServicesAPI() throws Exception {
        forceSetFlag("enable_ad_services_system_api", false);
    }

    public static void turnOnEnableAdsServicesAPI() throws Exception {
        forceSetFlag("enable_ad_services_system_api", true);
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

    public static void enableConsentDebugMode() throws Exception {
        forceSetFlag("consent_notification_debug_mode", true);
    }

    public static void disableConsentDebugMode() throws Exception {
        forceSetFlag("consent_notification_debug_mode", false);
    }

    public static void disableGlobalKillswitch() throws Exception {
        forceSetFlag("global_kill_switch", false);
    }

    public static void enableGlobalKillSwitch() throws Exception {
        forceSetFlag("global_kill_switch", true);
    }

    public static void setAsEuDevice() throws Exception {
        forceSetFlag("is_eea_device", true);
    }

    public static void setAsRowDevice() throws Exception {
        forceSetFlag("is_eea_device", false);
    }

    public static void enableU18() throws Exception {
        forceSetFlag("u18_ux_enabled", true);
    }

    public static void disableU18() throws Exception {
        forceSetFlag("u18_ux_enabled", false);
    }

    public static void enableGa() throws Exception {
        forceSetFlag("ga_ux_enabled", true);
    }

    public static void disableGa() throws Exception {
        forceSetFlag("ga_ux_enabled", false);
    }

    public static void enableBeta() throws Exception {
        forceSetFlag("ga_ux_enabled", false);
    }

    public static void disableOtaStrings() throws Exception {
        forceSetFlag("ui_ota_strings_feature_enabled", false);
    }

    public static void restartAdservices() {
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");
        ShellUtils.runShellCommand("am force-stop com.android.adservices.api");
    }

    public static void clearSavedStatus() {
        ShellUtils.runShellCommand(
                "rm /data/user/0/com.google.android.adservices.api/files/"
                        + "ConsentManagerStorageIdentifier.xml");
        ShellUtils.runShellCommand(
                "rm /data/user/0/com.android.adservices.api/files/"
                        + "ConsentManagerStorageIdentifier.xml");
        ShellUtils.runShellCommand(
                "rm /data/system/adservices/0/consent/ConsentManagerStorageIdentifier.xml");
    }

    public static void setSourceOfTruthToPPAPI() {
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 1");
    }

    public static void enableNotificationPermission() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .grantRuntimePermission("com.android.adservices.api", POST_NOTIFICATIONS);
    }

    public static void enableConsentNotificationActivityDebugMode() throws Exception {
        forceSetFlag("consent_notification_activity_debug_mode", true);
    }

    public static void enableEnableAdservicesSystemApi() throws Exception {
        forceSetFlag("enable_ad_services_system_api", true);
    }

    public static void enableUiDialogsFeature() throws Exception {
        forceSetFlag("ui_dialogs_feature_enabled", true);
    }

    public static void disableNotificationFlowV2() throws Exception {
        forceSetFlag("eu_notif_flow_change_enabled", false);
    }

    public static void setDebugUx(UiConstants.UX ux) {
        ShellUtils.runShellCommand("device_config put adservices debug_ux " + ux);
    }

    public static void verifyNotification(
            Context context,
            UiDevice device,
            boolean isDisplayed,
            boolean isEuTest,
            UiConstants.UX ux)
            throws Exception {
        device.openNotification();
        Thread.sleep(LAUNCH_TIMEOUT_MS);

        int notificationTitle = -1;
        int notificationHeader = -1;
        switch (ux) {
            case GA_UX:
                notificationTitle =
                        isEuTest
                                ? R.string.notificationUI_notification_ga_title_eu
                                : R.string.notificationUI_notification_ga_title;
                notificationHeader =
                        isEuTest
                                ? R.string.notificationUI_header_ga_title_eu
                                : R.string.notificationUI_header_ga_title;
                break;
            case BETA_UX:
                notificationTitle =
                        isEuTest
                                ? R.string.notificationUI_notification_title_eu
                                : R.string.notificationUI_notification_title;
                notificationHeader =
                        isEuTest
                                ? R.string.notificationUI_header_title_eu
                                : R.string.notificationUI_header_title;
                break;
            case U18_UX:
                notificationTitle = R.string.notificationUI_u18_notification_title;
                notificationHeader = R.string.notificationUI_u18_header_title;
                break;
        }

        UiSelector notificationCardSelector =
                new UiSelector().text(getString(context, notificationTitle));

        UiObject scroller =
                device.findObject(
                        new UiSelector()
                                .packageName(SYSTEM_UI_NAME)
                                .resourceId(SYSTEM_UI_RESOURCE_ID));

        UiObject notificationCard = scroller.getChild(notificationCardSelector);
        if (!isDisplayed) {
            assertThat(notificationCard.exists()).isFalse();
            return;
        }
        assertThat(notificationCard.exists()).isTrue();

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT_MS);
        UiObject title = getElement(context, device, notificationHeader);
        assertThat(title.exists()).isTrue();
    }

    public static void verifyNotification(
            Context context, UiDevice device, boolean isDisplayed, boolean isEuTest, boolean isGa)
            throws Exception {
        device.openNotification();
        Thread.sleep(LAUNCH_TIMEOUT_MS);
        UiObject scroller =
                device.findObject(
                        new UiSelector()
                                .packageName(SYSTEM_UI_NAME)
                                .resourceId(SYSTEM_UI_RESOURCE_ID));

        int notificationTitle =
                isEuTest
                        ? isGa
                                ? R.string.notificationUI_notification_ga_title_eu
                                : R.string.notificationUI_notification_title_eu
                        : isGa
                                ? R.string.notificationUI_notification_ga_title
                                : R.string.notificationUI_notification_title;

        int notificationHeader =
                isEuTest
                        ? isGa
                                ? R.string.notificationUI_header_ga_title_eu
                                : R.string.notificationUI_header_title_eu
                        : isGa
                                ? R.string.notificationUI_header_ga_title
                                : R.string.notificationUI_header_title;

        UiSelector notificationCardSelector =
                new UiSelector().text(getString(context, notificationTitle));

        UiObject notificationCard = scroller.getChild(notificationCardSelector);
        if (!isDisplayed) {
            assertThat(notificationCard.exists()).isFalse();
            return;
        }

        assertThat(notificationCard.exists()).isTrue();
        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT_MS);
        UiObject title = getElement(context, device, notificationHeader);
        assertThat(title.exists()).isTrue();
    }

    public static void consentConfirmationScreen(
            Context context, UiDevice device, boolean isEuDevice, boolean dialogsOn)
            throws UiObjectNotFoundException, InterruptedException {
        UiObject leftControlButton =
                getElement(
                        context,
                        device,
                        isEuDevice
                                ? R.string.notificationUI_left_control_button_text_eu
                                : R.string.notificationUI_left_control_button_text);
        UiObject rightControlButton =
                getElement(
                        context,
                        device,
                        isEuDevice
                                ? R.string.notificationUI_right_control_button_text_eu
                                : R.string.notificationUI_right_control_button_text);
        UiObject moreButton = getElement(context, device, R.string.notificationUI_more_button_text);
        assertThat(leftControlButton.exists()).isFalse();
        assertThat(rightControlButton.exists()).isFalse();
        assertThat(moreButton.exists()).isTrue();

        while (moreButton.exists()) {
            moreButton.click();
            Thread.sleep(1000);
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

            rightControlButton =
                    getElement(
                            context,
                            device,
                            R.string.notificationUI_confirmation_right_control_button_text);
            rightControlButton.click();
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

    public static void setupOTAStrings(
            Context context, UiDevice device, AdServicesCommonManager commonManager, String mddURL)
            throws InterruptedException, UiObjectNotFoundException {
        setAdServicesFlagsForOTATesting(mddURL);
        commonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);
        Thread.sleep(LAUNCH_TIMEOUT);
        ShellUtils.runShellCommand("cmd jobscheduler run -f com.google.android.adservices.api 14");
        clearNotifications(context, device);
        Thread.sleep(LAUNCH_TIMEOUT);
    }

    public static void setAdServicesFlagsForOTATesting(String mddURL) {
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled false");
        ShellUtils.runShellCommand(
                "device_config put adservices ui_ota_strings_feature_enabled true");
        ShellUtils.runShellCommand("device_config put adservices adservice_enabled true");
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_debug_mode true");
        ShellUtils.runShellCommand("device_config put adservices global_kill_switch false");
        ShellUtils.runShellCommand(
                "device_config put adservices mdd_ui_ota_strings_manifest_file_url " + mddURL);
    }

    public static void clearNotifications(Context context, UiDevice device)
            throws UiObjectNotFoundException, InterruptedException {
        device.openNotification();
        device.wait(Until.hasObject(By.pkg("com.android.systemui")), LAUNCH_TIMEOUT);
        UiObject scroller =
                device.findObject(
                        new UiSelector()
                                .packageName("com.android.systemui")
                                .resourceId("com.android.systemui:id/notification_stack_scroller"));
        UiSelector notificationCardSelector =
                new UiSelector()
                        .textContains(
                                getString(context, R.string.notificationUI_notification_title));
        UiObject notificationCard = scroller.getChild(notificationCardSelector);
        if (notificationCard.exists()) {
            notificationCard.click();
        }
        Thread.sleep(LAUNCH_TIMEOUT);
        device.pressHome();
    }

    public static void verifyNotificationAndSettingsPage(
            Context context, UiDevice device, Boolean isOTA)
            throws UiObjectNotFoundException, InterruptedException {
        // open notification tray
        device.openNotification();
        device.wait(Until.hasObject(By.pkg("com.android.systemui")), LAUNCH_TIMEOUT);

        // verify notification card
        UiObject scroller =
                device.findObject(
                        new UiSelector()
                                .packageName("com.android.systemui")
                                .resourceId("com.android.systemui:id/notification_stack_scroller"));
        UiSelector notificationCardSelector =
                new UiSelector()
                        .textContains(
                                isOTA
                                        ? getOTAString(
                                                context, R.string.notificationUI_notification_title)
                                        : getString(
                                                context,
                                                R.string.notificationUI_notification_title));
        UiObject notificationCard = scroller.getChild(notificationCardSelector);
        notificationCard.waitForExists(LONG_TIMEOUT);
        Thread.sleep(LAUNCH_TIMEOUT);
        assertThat(notificationCard.exists()).isTrue();

        // click on notification card
        notificationCard.click();

        // Wait for the notification landing page to appear
        device.wait(Until.hasObject(By.pkg(NOTIFICATION_PACKAGE_NAME).depth(0)), LAUNCH_TIMEOUT);

        // verify strings
        UiObject title =
                isOTA
                        ? scrollToOTAElement(context, device, R.string.notificationUI_header_title)
                        : scrollToElement(context, device, R.string.notificationUI_header_title);
        assertThat(title.exists()).isTrue();

        // open settings
        scrollToThenClickElementContainingText(
                device,
                isOTA
                        ? getOTAString(context, R.string.notificationUI_left_control_button_text)
                        : getString(context, R.string.notificationUI_left_control_button_text));

        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(PRIVACY_SANDBOX_PACKAGE_NAME).depth(0)), LAUNCH_TIMEOUT);

        // verify strings have changed
        UiObject appButton =
                isOTA
                        ? scrollToOTAElement(context, device, R.string.settingsUI_apps_title)
                        : scrollToElement(context, device, R.string.settingsUI_apps_title);
        appButton.waitForExists(LAUNCH_TIMEOUT);
        assertThat(appButton.exists()).isTrue();
        UiObject topicsButton =
                isOTA
                        ? scrollToOTAElement(context, device, R.string.settingsUI_topics_title)
                        : scrollToElement(context, device, R.string.settingsUI_topics_title);
        assertThat(topicsButton.exists()).isTrue();
    }

    public static void connectToWifi() {
        ShellUtils.runShellCommand("svc wifi enable");
        ShellUtils.runShellCommand("cmd wifi connect-network VirtWifi open");
    }

    public static void turnOffWifi(UiDevice device) {
        ShellUtils.runShellCommand("svc wifi disable");
    }

    private static void startAndroidSettingsApp(UiDevice device) {
        // Go to home screen
        device.pressHome();

        // Wait for launcher
        final String launcherPackage = device.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);

        // launch Android Settings
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        // Wait for Android Settings to appear
        device.wait(
                Until.hasObject(By.pkg(android.provider.Settings.ACTION_SETTINGS).depth(0)),
                LAUNCH_TIMEOUT);
    }

    private static void scrollToThenClickElementContainingText(UiDevice device, String text)
            throws UiObjectNotFoundException {
        UiScrollable scrollView =
                new UiScrollable(
                        new UiSelector().scrollable(true).className(ANDROID_WIDGET_SCROLLVIEW));
        UiObject element =
                device.findObject(
                        new UiSelector().childSelector(new UiSelector().textContains(text)));
        scrollView.scrollIntoView(element);
        if (element.exists()) {
            element.click();
        }
    }

    private static UiObject scrollToOTAElement(Context context, UiDevice device, int resId)
            throws UiObjectNotFoundException {
        scrollToBeginning();
        UiScrollable scrollView =
                new UiScrollable(
                        new UiSelector().scrollable(true).className(ANDROID_WIDGET_SCROLLVIEW));
        UiObject element =
                device.findObject(
                        new UiSelector()
                                .childSelector(
                                        new UiSelector().text(getOTAString(context, resId))));
        scrollView.scrollIntoView(element);
        return element;
    }

    // Test strings for OTA have an exclamation mark appended to the end
    private static String getOTAString(Context context, int resourceId) {
        return getString(context, resourceId) + "!";
    }

    private static UiObject scrollToElement(Context context, UiDevice device, int resId)
            throws UiObjectNotFoundException {
        scrollToBeginning();
        UiScrollable scrollView =
                new UiScrollable(
                        new UiSelector().scrollable(true).className(ANDROID_WIDGET_SCROLLVIEW));
        UiObject element =
                device.findObject(
                        new UiSelector()
                                .childSelector(new UiSelector().text(getString(context, resId))));
        scrollView.scrollIntoView(element);
        return element;
    }

    private static void scrollToBeginning() throws UiObjectNotFoundException {
        UiScrollable scrollView =
                new UiScrollable(
                        new UiSelector().scrollable(true).className(ANDROID_WIDGET_SCROLLVIEW));
        scrollView.flingToBeginning(MAX_SWIPES);
    }

    public static UiObject getConsentSwitch(UiDevice device) throws UiObjectNotFoundException {
        UiObject consentSwitch =
                device.findObject(new UiSelector().className("android.widget.Switch"));
        consentSwitch.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);

        // Swipe the screen by the width of the toggle so it's not blocked by the nav bar on AOSP
        // devices.
        device.swipe(
                consentSwitch.getVisibleBounds().centerX(),
                500,
                consentSwitch.getVisibleBounds().centerX(),
                0,
                100);

        return consentSwitch;
    }

    public static void performSwitchClick(
            UiDevice device, Context context, boolean dialogsOn, UiObject mainSwitch)
            throws UiObjectNotFoundException {
        if (dialogsOn && mainSwitch.isChecked()) {
            mainSwitch.click();
            UiObject dialogTitle =
                    getElement(context, device, R.string.settingsUI_dialog_opt_out_title);
            UiObject positiveText =
                    getElement(context, device, R.string.settingsUI_dialog_opt_out_positive_text);
            assertThat(dialogTitle.exists()).isTrue();
            assertThat(positiveText.exists()).isTrue();
            positiveText.click();
        } else {
            mainSwitch.click();
        }
    }

    public static void gentleSwipe(UiDevice device) throws UiObjectNotFoundException {
        UiScrollable scrollView =
                new UiScrollable(
                        new UiSelector().scrollable(true).className(ANDROID_WIDGET_SCROLLVIEW));

        scrollView.scrollForward(100);
    }

    public static void setFlipFlow(boolean isFlip) {
        ShellUtils.runShellCommand(
                "device_config put adservices eu_notif_flow_change_enabled " + isFlip);
    }

    public static String getString(Context context, int resourceId) {
        return context.getResources().getString(resourceId);
    }

    public static void scrollToAndClick(Context context, UiDevice device, int resId)
            throws UiObjectNotFoundException, InterruptedException {
        scrollTo(context, device, resId);
        UiObject consentPageButton = getElement(context, device, resId);
        consentPageButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(consentPageButton.exists()).isTrue();
        // objects may be partially hidden by the status bar and nav bars.
        consentPageButton.clickTopLeft();
    }

    public static UiObject getPageElement(Context context, UiDevice device, int resId) {
        return device.findObject(new UiSelector().text(getString(context, resId)));
    }

    public static UiObject scrollTo(Context context, UiDevice device, int resId)
            throws UiObjectNotFoundException {
        UiScrollable scrollView =
                new UiScrollable(
                        new UiSelector().scrollable(true).className(ANDROID_WIDGET_SCROLLVIEW));
        UiObject element = getElement(context, device, resId);
        scrollView.scrollIntoView(element);
        try {
            Thread.sleep(SCROLL_TIMEOUT);
        } catch (InterruptedException e) {
            LogUtil.e("InterruptedException: " + e.getMessage());
        }
        return element;
    }

    public static UiObject getElement(Context context, UiDevice device, int resId) {
        UiObject obj = device.findObject(new UiSelector().text(getString(context, resId)));
        if (!obj.exists()) {
            obj = device.findObject(new UiSelector().text(getString(context, resId).toUpperCase()));
        }
        return obj;
    }

    public static UiObject getElement(Context context, UiDevice device, int resId, int index) {
        UiObject obj =
                device.findObject(new UiSelector().text(getString(context, resId)).instance(index));
        if (!obj.exists()) {
            obj =
                    device.findObject(
                            new UiSelector()
                                    .text(getString(context, resId).toUpperCase())
                                    .instance(index));
        }
        return obj;
    }

    public static void click(Context context, UiDevice device, int resId)
            throws UiObjectNotFoundException {
        UiObject obj = device.findObject(new UiSelector().text(getString(context, resId)));
        // objects may be partially hidden by the status bar and nav bars.
        obj.clickTopLeft();
    }

    public static void takeScreenshot(UiDevice device, String methodName) {
        try {
            String timeStamp =
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                            .format(Date.from(Instant.now()));

            File screenshotFile = new File("/sdcard/Pictures/" + methodName + timeStamp + ".png");
            device.takeScreenshot(screenshotFile);
        } catch (RuntimeException e) {
            LogUtil.e("Failed to take screenshot: " + e.getMessage());
        }
    }

    /**
     * Check whether the device is supported. Adservices doesn't support non-phone device.
     *
     * @return if the device is supported.
     */
    public static boolean isDeviceSupported() {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        PackageManager pm = inst.getContext().getPackageManager();
        return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                && !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }
}
