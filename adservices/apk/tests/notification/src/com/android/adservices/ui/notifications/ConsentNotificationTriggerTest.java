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

package com.android.adservices.ui.notifications;

import static android.adservices.common.AdServicesCommonManager.ACTION_ADSERVICES_NOTIFICATION_DISPLAYED;

import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_NOTIFICATION_DISMISSED_ON_CLICK;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_UX_ENABLED;
import static com.android.adservices.service.consent.ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__PAS_FIRST_NOTIFICATION_CHANNEL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__PAS_RENOTIFY_NOTIFICATION_CHANNEL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__GA_UX_WITH_PAS;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.BETA_UX;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.GA_UX;
import static com.android.adservices.ui.util.ApkTestUtil.getPageElement;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.consent.DeviceRegionProvider;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.UIStats;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.collection.GaUxEnrollmentChannelCollection;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.adservices.ui.util.NotificationActivityTestUtil;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.io.IOException;

@SpyStatic(ConsentManager.class)
@SpyStatic(ConsentNotificationTrigger.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(NotificationManagerCompat.class)
@SpyStatic(AdServicesLoggerImpl.class)
@SpyStatic(DeviceRegionProvider.class)
@SpyStatic(UxStatesManager.class)
@SpyStatic(UiStatsLogger.class)
@RunWith(AndroidJUnit4.class)
public final class ConsentNotificationTriggerTest extends AdServicesExtendedMockitoTestCase {

    private static final String NOTIFICATION_CHANNEL_ID = "PRIVACY_SANDBOX_CHANNEL";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static final int MAX_UI_OBJECT_SEARCH_TRIES = 3;

    private static final String TEST_PRIVILEGED_APP_NAME =
            "com.example.adservices.samples.ui.sampletestapp";
    private static final String TEST_PRIVILEGED_APP_APK_PATH =
            "/data/local/tmp/cts/install/" + TEST_PRIVILEGED_APP_NAME + ".apk";
    private static final String TEST_NON_PRIVILEGED_APP_NAME =
            "com.example.adservices.samples.ui.consenttestapp";
    private static final String TEST_NON_PRIVILEGED_APP_APK_PATH =
            "/data/local/tmp/cts/install/" + TEST_NON_PRIVILEGED_APP_NAME + ".apk";
    private static UiDevice sDevice;
    private NotificationManager mNotificationManager;

    @Mock private AdServicesLogger mAdServicesLogger;
    @Mock private NotificationManagerCompat mNotificationManagerCompat;
    @Mock private ConsentManager mConsentManager;
    @Mock private UxStatesManager mMockUxStatesManager;

    @Before
    public void setUp() {
        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mNotificationManager = mSpyContext.getSystemService(NotificationManager.class);

        mocker.mockGetFlags(mMockFlags);
        doReturn(mAdServicesLogger).when(UiStatsLogger::getAdServicesLogger);
        doReturn(mMockUxStatesManager).when(() -> UxStatesManager.getInstance());
        doReturn(mConsentManager).when(() -> ConsentManager.getInstance());
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(true).when(mMockFlags).isUiFeatureTypeLoggingEnabled();
        doReturn(false).when(mMockUxStatesManager).isEeaDevice();
        doReturn(false).when(mMockUxStatesManager).getFlag(any(String.class));
        doReturn(GA_UX).when(mMockUxStatesManager).getUx();
        doReturn(true).when(mMockUxStatesManager).getFlag(KEY_NOTIFICATION_DISMISSED_ON_CLICK);
        doReturn(false).when(mMockUxStatesManager).getFlag(KEY_PAS_UX_ENABLED);
        doReturn(false).when(mMockDebugFlags).getConsentNotificationActivityDebugMode();
        doReturn(false).when(mMockFlags).getAdServicesConsentBusinessLogicMigrationEnabled();
        doReturn(false).when(mMockDebugFlags).getConsentNotificationActivityDebugMode();
        doReturn("").when(mMockFlags).getDebugUx();
        cancelAllPreviousNotifications();
        AdservicesTestHelper.installTestApp(TEST_NON_PRIVILEGED_APP_APK_PATH);
        AdservicesTestHelper.installTestApp(TEST_PRIVILEGED_APP_APK_PATH);
    }

    @After
    public void tearDown() throws IOException {
        ApkTestUtil.takeScreenshot(sDevice, getClass().getSimpleName() + "_" + getTestName() + "_");

        AdservicesTestHelper.killAdservicesProcess(mSpyContext);

        AdservicesTestHelper.uninstallTestApp(TEST_NON_PRIVILEGED_APP_NAME);
        AdservicesTestHelper.uninstallTestApp(TEST_PRIVILEGED_APP_NAME);
    }

    @Test
    public void testEuNotification_gaUxFlagEnabled()
            throws InterruptedException, UiObjectNotFoundException {
        doReturn(true).when(mMockFlags).isEeaDevice();
        doReturn(true).when(mMockUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(GA_UX).when(mMockUxStatesManager).getUx();

        final String expectedTitle =
                mSpyContext.getString(R.string.notificationUI_notification_ga_title_eu_v2);
        final String expectedContent =
                mSpyContext.getString(R.string.notificationUI_notification_ga_content_eu_v2);

        ConsentNotificationTrigger.showConsentNotification(mSpyContext, true);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(mAdServicesLogger, times(2)).logUIStats(any());

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).recordTopicsDefaultConsent(false);
        verify(mConsentManager).recordFledgeDefaultConsent(false);
        verify(mConsentManager).recordMeasurementDefaultConsent(false);
        verify(mConsentManager).disable(mSpyContext, AdServicesApiType.FLEDGE);
        verify(mConsentManager).disable(mSpyContext, AdServicesApiType.TOPICS);
        verify(mConsentManager).disable(mSpyContext, AdServicesApiType.MEASUREMENTS);
        verify(mConsentManager).recordNotificationDisplayed(true);
        verify(mConsentManager).recordGaUxNotificationDisplayed(true);

        assertActiveNotificationsCount(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags)
                .isEqualTo(Notification.FLAG_ONGOING_EVENT);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags)
                .isEqualTo(Notification.FLAG_NO_CLEAR);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags)
                .isEqualTo(Notification.FLAG_AUTO_CANCEL);

        UiObject scroller = getNotificationTrayScroller();
        assertWithMessage("Notification tray scroller").that(scroller.exists()).isTrue();

        String notificationCardText =
                mSpyContext.getString(R.string.notificationUI_notification_ga_title_eu_v2);
        UiObject notificationCard = scroller.getChild(new UiSelector().text(notificationCardText));
        assertWithMessage("Notification card with text %s", notificationCardText)
                .that(notificationCard.exists())
                .isTrue();

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        assertActiveNotificationsCount(0);
    }

    @Test
    public void testNonEuNotifications_gaUxEnabled() throws InterruptedException {
        doReturn(false).when(mMockFlags).isEeaDevice();
        doReturn(true).when(mMockUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(GA_UX).when(mMockUxStatesManager).getUx();

        final String expectedTitle =
                mSpyContext.getString(R.string.notificationUI_notification_ga_title_v2);
        final String expectedContent =
                mSpyContext.getString(R.string.notificationUI_notification_ga_content_v2);

        ConsentNotificationTrigger.showConsentNotification(mSpyContext, false);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(mAdServicesLogger, times(2)).logUIStats(any());

        verify(mConsentManager).enable(mSpyContext, AdServicesApiType.TOPICS);
        verify(mConsentManager).enable(mSpyContext, AdServicesApiType.FLEDGE);
        verify(mConsentManager).enable(mSpyContext, AdServicesApiType.MEASUREMENTS);

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).recordTopicsDefaultConsent(true);
        verify(mConsentManager).recordFledgeDefaultConsent(true);
        verify(mConsentManager).recordMeasurementDefaultConsent(true);
        verify(mConsentManager).recordGaUxNotificationDisplayed(true);
        verify(mConsentManager).recordNotificationDisplayed(true);

        assertActiveNotificationsCount(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags)
                .isEqualTo(Notification.FLAG_AUTO_CANCEL);
        assertThat(notification.actions).isNull();
    }

    @Test
    public void testEuNotifications_gaUxEnabled_nonDismissable()
            throws InterruptedException, UiObjectNotFoundException {
        doReturn(true).when(mMockFlags).isEeaDevice();
        doReturn(true).when(mMockUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(GA_UX).when(mMockUxStatesManager).getUx();
        doReturn(false).when(mMockUxStatesManager).getFlag(KEY_NOTIFICATION_DISMISSED_ON_CLICK);

        final String expectedTitle =
                mSpyContext.getString(R.string.notificationUI_notification_ga_title_eu_v2);
        final String expectedContent =
                mSpyContext.getString(R.string.notificationUI_notification_ga_content_eu_v2);

        ConsentNotificationTrigger.showConsentNotification(mSpyContext, true);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(mAdServicesLogger, times(2)).logUIStats(any());

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).recordTopicsDefaultConsent(false);
        verify(mConsentManager).recordFledgeDefaultConsent(false);
        verify(mConsentManager).recordMeasurementDefaultConsent(false);
        verify(mConsentManager).disable(mSpyContext, AdServicesApiType.FLEDGE);
        verify(mConsentManager).disable(mSpyContext, AdServicesApiType.TOPICS);
        verify(mConsentManager).disable(mSpyContext, AdServicesApiType.MEASUREMENTS);
        verify(mConsentManager).recordNotificationDisplayed(true);
        verify(mConsentManager).recordGaUxNotificationDisplayed(true);

        assertActiveNotificationsCount(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags)
                .isEqualTo(Notification.FLAG_ONGOING_EVENT);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags)
                .isEqualTo(Notification.FLAG_NO_CLEAR);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags)
                .isEqualTo(Notification.FLAG_AUTO_CANCEL);
        assertThat(notification.actions).isNull();

        UiObject scroller = getNotificationTrayScroller();

        // there might be only one notification and no scroller exists.
        UiObject notificationCard;
        // notification card title might be cut off, so check for first portion of title
        UiSelector notificationCardSelector =
                new UiSelector()
                        .textContains(
                                mSpyContext
                                        .getString(
                                                R.string.notificationUI_notification_ga_title_eu_v2)
                                        .substring(0, 15));
        if (scroller.exists()) {
            notificationCard = scroller.getChild(notificationCardSelector);
        } else {
            notificationCard = sDevice.findObject(notificationCardSelector);
        }
        notificationCard.waitForExists(LAUNCH_TIMEOUT);
        assertThat(notificationCard.exists()).isTrue();

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        assertActiveNotificationsCount(0);
    }

    @Test
    @FlakyTest(bugId = 302607350)
    public void testEuNotifications_gaUxEnabled_nonDismissable_dismissedOnConfirmationPage()
            throws InterruptedException, UiObjectNotFoundException {
        doReturn(true).when(mMockFlags).isEeaDevice();
        doReturn(true).when(mMockFlags).getEnableAdServicesSystemApi();
        doReturn("GA_UX").when(mMockFlags).getDebugUx();
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(true).when(mMockDebugFlags).getConsentNotificationActivityDebugMode();
        doReturn(GA_UX).when(mMockUxStatesManager).getUx();
        doReturn(true).when(mMockUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(false).when(mMockUxStatesManager).getFlag(KEY_NOTIFICATION_DISMISSED_ON_CLICK);

        final String expectedTitle =
                mSpyContext.getString(R.string.notificationUI_notification_ga_title_eu_v2);
        final String expectedContent =
                mSpyContext.getString(R.string.notificationUI_notification_ga_content_eu_v2);

        ConsentNotificationTrigger.showConsentNotification(mSpyContext, true);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(mAdServicesLogger, times(2)).logUIStats(any());

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).recordTopicsDefaultConsent(false);
        verify(mConsentManager).recordFledgeDefaultConsent(false);
        verify(mConsentManager).recordMeasurementDefaultConsent(false);
        verify(mConsentManager).disable(mSpyContext, AdServicesApiType.FLEDGE);
        verify(mConsentManager).disable(mSpyContext, AdServicesApiType.TOPICS);
        verify(mConsentManager).disable(mSpyContext, AdServicesApiType.MEASUREMENTS);
        verify(mConsentManager).recordGaUxNotificationDisplayed(true);

        assertActiveNotificationsCount(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags)
                .isEqualTo(Notification.FLAG_ONGOING_EVENT);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags)
                .isEqualTo(Notification.FLAG_NO_CLEAR);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags)
                .isEqualTo(Notification.FLAG_AUTO_CANCEL);
        assertThat(notification.actions).isNull();

        // verify that notification was displayed
        UiObject scroller = getNotificationTrayScroller();

        // there might be only one notification and no scroller exists.
        UiObject notificationCard;
        // notification card title might be cut off, so check for first portion of title
        UiSelector notificationCardSelector =
                new UiSelector()
                        .textContains(
                                mSpyContext
                                        .getString(
                                                R.string.notificationUI_notification_ga_title_eu_v2)
                                        .substring(0, 15));
        if (scroller.exists()) {
            notificationCard = scroller.getChild(notificationCardSelector);
        } else {
            notificationCard = sDevice.findObject(notificationCardSelector);
        }
        notificationCard.waitForExists(LAUNCH_TIMEOUT);
        assertWithMessage("Notification card").that(notificationCard.exists()).isTrue();

        // click the notification and verify that notification still exists (wasn't dismissed)
        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        assertActiveNotificationsCount(0);

        // go to confirmation page and verify that notification was dismissed
        UiObject leftControlButton =
                getPageElement(
                        sDevice, R.string.notificationUI_confirmation_left_control_button_text);
        UiObject rightControlButton =
                getPageElement(
                        sDevice, R.string.notificationUI_confirmation_right_control_button_text);
        NotificationActivityTestUtil.clickMoreToBottom(sDevice);
        rightControlButton.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        assertActiveNotificationsCount(0);
    }

    @Test
    public void testNotificationsDisabled() {
        doReturn(false).when(mMockUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(BETA_UX).when(mMockUxStatesManager).getUx();

        doReturn(mNotificationManagerCompat)
                .when(() -> NotificationManagerCompat.from(mSpyContext));
        doReturn(false).when(mNotificationManagerCompat).areNotificationsEnabled();

        ConsentNotificationTrigger.showConsentNotification(mSpyContext, true);

        verify(mAdServicesLogger, times(2)).logUIStats(any());

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).recordNotificationDisplayed(true);
    }

    @Test
    public void testNotificationV2ActivityIntent() throws Exception {
        String expectedTitle =
                mSpyContext.getString(R.string.notificationUI_pas_notification_title);
        String expectedContent =
                mSpyContext.getString(R.string.notificationUI_pas_notification_content);

        ConsentNotificationTrigger.showConsentNotificationV2(
                mSpyContext,
                /* isRenotify= */ false,
                /* isNewAdPersonalizationModuleEnabled= */ true,
                /* isOngoingNotification= */ true);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(mAdServicesLogger, times(2)).logUIStats(any());

        assertActiveNotificationsCount(1);
        Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags)
                .isEqualTo(Notification.FLAG_ONGOING_EVENT);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags)
                .isEqualTo(Notification.FLAG_NO_CLEAR);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags)
                .isEqualTo(Notification.FLAG_AUTO_CANCEL);

        UiObject scroller = getNotificationTrayScroller();
        assertWithMessage("Notification tray scroller").that(scroller.exists()).isTrue();

        String notificationCardText =
                mSpyContext.getString(R.string.notificationUI_pas_notification_title);
        UiObject notificationCard = scroller.getChild(new UiSelector().text(notificationCardText));
        assertWithMessage("Notification card with text %s", notificationCardText)
                .that(notificationCard.exists())
                .isTrue();

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        assertActiveNotificationsCount(0);

        // Sample privileged app package that has the activity for the intent.
        BySelector titleSelector =
                By.res(TEST_PRIVILEGED_APP_NAME + ":id/action_bar")
                        .hasChild(By.clazz("android.widget.TextView"));
        UiObject2 titleObject = sDevice.wait(Until.findObject(titleSelector), LAUNCH_TIMEOUT);
        assertWithMessage("Sample privileged app title").that(titleObject).isNotNull();
    }

    @Test
    public void testNotificationV2BroadcastIntent() throws Exception {
        // Launch sample app to register the broadcast receiver
        sDevice.executeShellCommand(
                String.format("am start -n %s/.MainActivity", TEST_PRIVILEGED_APP_NAME));
        Thread.sleep(1000);

        String expectedTitle =
                mSpyContext.getString(R.string.notificationUI_pas_notification_title);
        String expectedContent =
                mSpyContext.getString(R.string.notificationUI_pas_notification_content);

        ConsentNotificationTrigger.showConsentNotificationV2(
                mSpyContext,
                /* isRenotify= */ false,
                /* isNewAdPersonalizationModuleEnabled= */ true,
                /* isOngoingNotification= */ true);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(mAdServicesLogger, times(2)).logUIStats(any());

        assertActiveNotificationsCount(1);
        Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags)
                .isEqualTo(Notification.FLAG_ONGOING_EVENT);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags)
                .isEqualTo(Notification.FLAG_NO_CLEAR);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags)
                .isEqualTo(Notification.FLAG_AUTO_CANCEL);

        UiObject scroller = getNotificationTrayScroller();
        assertWithMessage("Notification tray scroller").that(scroller.exists()).isTrue();

        String notificationCardText =
                mSpyContext.getString(R.string.notificationUI_pas_notification_title);
        UiObject notificationCard = scroller.getChild(new UiSelector().text(notificationCardText));
        assertWithMessage("Notification card with text %s", notificationCardText)
                .that(notificationCard.exists())
                .isTrue();

        // Check if broadcast intent is received by privileged app
        String logcatOutput = sDevice.executeShellCommand("logcat -d -s MyBroadcastReceiver");
        assertTrue(logcatOutput.contains(ACTION_ADSERVICES_NOTIFICATION_DISPLAYED));
        assertTrue(logcatOutput.contains(TEST_PRIVILEGED_APP_NAME));
    }

    @NonNull
    private static UiObject getNotificationTrayScroller() {
        UiObject scroller = null;
        for (int i = 0;
                i < MAX_UI_OBJECT_SEARCH_TRIES && (scroller == null || !scroller.exists());
                i++) {
            sDevice.openNotification();
            sDevice.wait(
                    Until.hasObject(
                            By.pkg("com.android.systemui")
                                    .res("com.android.systemui:id/notification_stack_scroller")),
                    LAUNCH_TIMEOUT);

            scroller =
                    sDevice.findObject(
                            new UiSelector()
                                    .packageName("com.android.systemui")
                                    .resourceId(
                                            "com.android.systemui:id/notification_stack_scroller"));
        }
        assertWithMessage("Notification tray scroller").that(scroller.exists()).isTrue();
        return scroller;
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = "PAS UX is currently only available on T+ devices")
    public void testPasNotifications_PasUxEnabled_FirstNotice() throws InterruptedException {
        doReturn(true).when(mMockFlags).getEnableAdServicesSystemApi();
        doReturn(true).when(mMockUxStatesManager).getFlag(KEY_PAS_UX_ENABLED);
        doReturn(true).when(mMockUxStatesManager).pasUxIsActive(anyBoolean());
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManager).getConsent(any());
        doReturn(false).when(mConsentManager).wasNotificationDisplayed();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(false).when(mConsentManager).wasPasNotificationDisplayed();
        doReturn(NO_MANUAL_INTERACTIONS_RECORDED)
                .when(mConsentManager)
                .getUserManualInteractionWithConsent();
        doReturn(GaUxEnrollmentChannelCollection.PAS_FIRST_CONSENT_NOTIFICATION_CHANNEL)
                .when(mMockUxStatesManager)
                .getEnrollmentChannel();

        String expectedTitle =
                mSpyContext.getString(R.string.notificationUI_pas_notification_title);
        String expectedContent =
                mSpyContext.getString(R.string.notificationUI_pas_notification_content);

        ConsentNotificationTrigger.showConsentNotification(mSpyContext, false);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        ArgumentCaptor<UIStats> argument = ArgumentCaptor.forClass(UIStats.class);
        verify(mAdServicesLogger, times(2)).logUIStats(argument.capture());

        assertThat(argument.getValue().getEnrollmentChannel())
                .isEqualTo(
                        AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__PAS_FIRST_NOTIFICATION_CHANNEL);
        assertThat(argument.getValue().getUx())
                .isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__GA_UX_WITH_PAS);

        verify(mConsentManager).enable(mSpyContext, AdServicesApiType.TOPICS);
        verify(mConsentManager).enable(mSpyContext, AdServicesApiType.FLEDGE);
        verify(mConsentManager).enable(mSpyContext, AdServicesApiType.MEASUREMENTS);

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).recordTopicsDefaultConsent(true);
        verify(mConsentManager).recordFledgeDefaultConsent(true);
        verify(mConsentManager).recordMeasurementDefaultConsent(true);
        verify(mConsentManager).recordPasNotificationDisplayed(true);

        assertActiveNotificationsCount(1);
        Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags)
                .isEqualTo(Notification.FLAG_AUTO_CANCEL);
        assertThat(notification.actions).isNull();
    }

    @Test
    public void testPasNotifications_PasUxEnabled_RenotifyNotice() throws InterruptedException {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        doReturn(true).when(mMockFlags).getEnableAdServicesSystemApi();
        doReturn(true).when(mMockUxStatesManager).getFlag(KEY_PAS_UX_ENABLED);
        doReturn(true).when(mMockUxStatesManager).pasUxIsActive(anyBoolean());
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent(any());
        doReturn(false).when(mConsentManager).wasPasNotificationDisplayed();
        doReturn(GaUxEnrollmentChannelCollection.PAS_RECONSENT_NOTIFICATION_CHANNEL)
                .when(mMockUxStatesManager)
                .getEnrollmentChannel();

        String expectedTitle =
                mSpyContext.getString(R.string.notificationUI_pas_re_notification_title);
        String expectedContent =
                mSpyContext.getString(R.string.notificationUI_pas_re_notification_content);

        ConsentNotificationTrigger.showConsentNotification(mSpyContext, false);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        ArgumentCaptor<UIStats> argument = ArgumentCaptor.forClass(UIStats.class);
        verify(mAdServicesLogger, times(2)).logUIStats(argument.capture());

        assertThat(argument.getValue().getEnrollmentChannel())
                .isEqualTo(
                        AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__PAS_RENOTIFY_NOTIFICATION_CHANNEL);
        assertThat(argument.getValue().getUx())
                .isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__GA_UX_WITH_PAS);

        verify(mConsentManager, times(0)).enable(mSpyContext, AdServicesApiType.TOPICS);
        verify(mConsentManager, times(0)).enable(mSpyContext, AdServicesApiType.FLEDGE);
        verify(mConsentManager, times(0)).enable(mSpyContext, AdServicesApiType.MEASUREMENTS);

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager, times(0)).recordTopicsDefaultConsent(true);
        verify(mConsentManager, times(0)).recordFledgeDefaultConsent(true);
        verify(mConsentManager, times(0)).recordMeasurementDefaultConsent(true);
        verify(mConsentManager).recordPasNotificationDisplayed(true);

        assertActiveNotificationsCount(1);
        Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags)
                .isEqualTo(Notification.FLAG_AUTO_CANCEL);
        assertThat(notification.actions).isNull();
    }

    @Test
    @FlakyTest(bugId = 345514911)
    public void testPasSettingsUpdatedAfterNotificationDisplayed() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        doReturn(true).when(mMockFlags).getEnableAdServicesSystemApi();
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn("GA_UX").when(mMockFlags).getDebugUx();
        doReturn(true).when(mMockDebugFlags).getConsentNotificationActivityDebugMode();
        doReturn(false).when(mMockUxStatesManager).pasUxIsActive(anyBoolean());
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManager)
                .getConsent(any(AdServicesApiType.class));
        doReturn(ImmutableList.of()).when(mConsentManager).getKnownTopicsWithConsent();
        doReturn(ImmutableList.of()).when(mConsentManager).getKnownAppsWithConsent();
        doReturn(ImmutableList.of()).when(mConsentManager).getTopicsWithRevokedConsent();
        doReturn(ImmutableList.of()).when(mConsentManager).getAppsWithRevokedConsent();

        // check is old settings
        Intent intent = new Intent("android.test.adservices.ui.MAIN");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ApplicationProvider.getApplicationContext().startActivity(intent);
        // Wait for the view to appear
        sDevice.wait(Until.hasObject(By.pkg("android.test.adservices.ui.MAIN").depth(0)), 5000);

        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_apps_ga_title);
        sDevice.waitForIdle();
        UiObject2 expectedOldFledgeBodyText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_apps_view_ga_subtitle);
        assertNotNull(expectedOldFledgeBodyText, R.string.settingsUI_apps_view_ga_subtitle);
        sDevice.pressHome();

        // mock PAS notification shown by going debug route
        doReturn(true).when(mMockUxStatesManager).pasUxIsActive(anyBoolean());
        sDevice.waitForIdle();

        // check is new settings
        intent = new Intent("android.test.adservices.ui.MAIN");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ApplicationProvider.getApplicationContext().startActivity(intent);
        // Wait for the view to appear
        sDevice.wait(Until.hasObject(By.pkg("android.test.adservices.ui.MAIN").depth(0)), 5000);

        sDevice.waitForIdle();
        UiObject2 expectedNewFledgeBodyText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_pas_apps_view_body_text);
        assertNotNull(expectedNewFledgeBodyText, R.string.settingsUI_pas_apps_view_body_text);
    }

    private void cancelAllPreviousNotifications() {
        if (mNotificationManager.getActiveNotifications().length > 0) {
            mNotificationManager.cancelAll();
        }
    }

    private static void assertNotNull(UiObject2 object, int resId) {
        assertWithMessage("Button with text %s ", ApkTestUtil.getString(resId))
                .that(object)
                .isNotNull();
    }

    private void assertActiveNotificationsCount(int count) {
        assertWithMessage("Number of Active notifications")
                .that(mNotificationManager.getActiveNotifications())
                .hasLength(count);
    }
}
