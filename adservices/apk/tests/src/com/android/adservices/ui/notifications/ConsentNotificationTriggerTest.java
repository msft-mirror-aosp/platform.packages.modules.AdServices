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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.adservices.AdServicesManager;
import android.content.Context;

import androidx.core.app.NotificationManagerCompat;
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
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.consent.DeviceRegionProvider;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class ConsentNotificationTriggerTest {
    private static final String NOTIFICATION_CHANNEL_ID = "PRIVACY_SANDBOX_CHANNEL";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static UiDevice sDevice;
    private AdServicesManager mAdServicesManager;
    @Mock private AdServicesLoggerImpl mAdServicesLoggerImpl;
    @Mock private NotificationManagerCompat mNotificationManagerCompat;
    @Mock private ConsentManager mConsentManager;
    private NotificationManager mNotificationManager;
    @Spy private Context mContext;

    private MockitoSession mStaticMockSession = null;

    @Mock Flags mMockFlags;

    @Before
    public void setUp() {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(ApkTestUtil.isDeviceSupported());

        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mNotificationManager = mContext.getSystemService(NotificationManager.class);

        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ConsentManager.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(NotificationManagerCompat.class)
                        .spyStatic(AdServicesLoggerImpl.class)
                        .spyStatic(UiStatsLogger.class)
                        .spyStatic(DeviceRegionProvider.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(mAdServicesLoggerImpl).when(AdServicesLoggerImpl::getInstance);
        doReturn(mAdServicesManager).when(mContext).getSystemService(AdServicesManager.class);
        doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any(Context.class)));
    }

    @After
    public void tearDown() throws IOException {
        if (!ApkTestUtil.isDeviceSupported()) return;

        AdservicesTestHelper.killAdservicesProcess(mContext);
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testEuNotification() throws InterruptedException, UiObjectNotFoundException {
        doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();

        cancelAllPreviousNotifications();
        final String expectedTitle =
                mContext.getString(R.string.notificationUI_notification_title_eu);
        final String expectedContent =
                mContext.getString(R.string.notificationUI_notification_content_eu);

        ConsentNotificationTrigger.showConsentNotification(mContext, true);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(() -> UiStatsLogger.logRequestedNotification(mContext));

        verify(mConsentManager).getDefaultConsent();
        verify(mConsentManager).getDefaultAdIdState();
        verify(mConsentManager).disable(mContext);
        verify(mConsentManager).recordNotificationDisplayed();
        verifyNoMoreInteractions(mConsentManager);

        assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags).isEqualTo(0);

        sDevice.openNotification();
        sDevice.wait(Until.hasObject(By.pkg("com.android.systemui")), LAUNCH_TIMEOUT);
        UiObject scroller =
                sDevice.findObject(
                        new UiSelector()
                                .packageName("com.android.systemui")
                                .resourceId("com.android.systemui:id/notification_stack_scroller"));
        assertThat(scroller.exists()).isTrue();
        UiSelector notificationCardSelector =
                new UiSelector().text(getString(R.string.notificationUI_notification_title_eu));
        UiObject notificationCard = scroller.getChild(notificationCardSelector);
        assertThat(notificationCard.exists()).isTrue();

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        UiObject title = getElement(R.string.notificationUI_header_title_eu);
        assertThat(title.exists()).isTrue();
    }

    @Test
    public void testEuNotification_gaUxFlagEnabled() throws InterruptedException {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();

        cancelAllPreviousNotifications();
        final String expectedTitle =
                mContext.getString(R.string.notificationUI_notification_ga_title_eu);
        final String expectedContent =
                mContext.getString(R.string.notificationUI_notification_ga_content_eu);

        ConsentNotificationTrigger.showConsentNotification(mContext, true);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(() -> UiStatsLogger.logRequestedNotification(mContext));

        verify(mConsentManager).getDefaultConsent();
        verify(mConsentManager).getDefaultAdIdState();
        verify(mConsentManager).recordTopicsDefaultConsent(false);
        verify(mConsentManager).recordFledgeDefaultConsent(false);
        verify(mConsentManager).recordMeasurementDefaultConsent(false);
        verify(mConsentManager).disable(mContext, AdServicesApiType.FLEDGE);
        verify(mConsentManager).disable(mContext, AdServicesApiType.TOPICS);
        verify(mConsentManager).disable(mContext, AdServicesApiType.MEASUREMENTS);
        verify(mConsentManager).recordNotificationDisplayed();
        verify(mConsentManager).recordGaUxNotificationDisplayed();
        verifyNoMoreInteractions(mConsentManager);

        assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
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
    }

    private void cancelAllPreviousNotifications() {
        if (mNotificationManager.getActiveNotifications().length > 0) {
            mNotificationManager.cancelAll();
        }
    }

    @Test
    public void testNonEuNotifications() throws InterruptedException, UiObjectNotFoundException {
        doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();

        cancelAllPreviousNotifications();
        final String expectedTitle = mContext.getString(R.string.notificationUI_notification_title);
        final String expectedContent =
                mContext.getString(R.string.notificationUI_notification_content);

        ConsentNotificationTrigger.showConsentNotification(mContext, false);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(() -> UiStatsLogger.logRequestedNotification(mContext));

        verify(mConsentManager).getDefaultConsent();
        verify(mConsentManager).getDefaultAdIdState();
        verify(mConsentManager).enable(mContext);
        verify(mConsentManager).recordNotificationDisplayed();
        verifyNoMoreInteractions(mConsentManager);

        assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags).isEqualTo(0);

        sDevice.openNotification();
        sDevice.wait(Until.hasObject(By.pkg("com.android.systemui")), LAUNCH_TIMEOUT);

        UiObject scroller =
                sDevice.findObject(
                        new UiSelector()
                                .packageName("com.android.systemui")
                                .resourceId("com.android.systemui:id/notification_stack_scroller"));
        assertThat(scroller.exists()).isTrue();
        UiObject notificationCard =
                scroller.getChild(
                        new UiSelector()
                                .text(getString(R.string.notificationUI_notification_title)));
        assertThat(notificationCard.exists()).isTrue();

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        UiObject title = getElement(R.string.notificationUI_header_title);
        assertThat(title.exists()).isTrue();
    }

    @Test
    public void testNonEuNotifications_gaUxEnabled() throws InterruptedException {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();

        cancelAllPreviousNotifications();
        final String expectedTitle =
                mContext.getString(R.string.notificationUI_notification_ga_title);
        final String expectedContent =
                mContext.getString(R.string.notificationUI_notification_ga_content);

        ConsentNotificationTrigger.showConsentNotification(mContext, false);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(() -> UiStatsLogger.logRequestedNotification(mContext));

        verify(mConsentManager).enable(mContext, AdServicesApiType.TOPICS);
        verify(mConsentManager).enable(mContext, AdServicesApiType.FLEDGE);
        verify(mConsentManager).enable(mContext, AdServicesApiType.MEASUREMENTS);

        verify(mConsentManager).getDefaultConsent();
        verify(mConsentManager).getDefaultAdIdState();
        verify(mConsentManager).recordTopicsDefaultConsent(true);
        verify(mConsentManager).recordFledgeDefaultConsent(true);
        verify(mConsentManager).recordMeasurementDefaultConsent(true);
        verify(mConsentManager).recordGaUxNotificationDisplayed();
        verify(mConsentManager).recordNotificationDisplayed();
        verifyNoMoreInteractions(mConsentManager);

        assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags).isEqualTo(0);
        assertThat(notification.actions).isNull();
    }

    @Test
    public void testNotificationsDisabled() {
        doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();

        ExtendedMockito.doReturn(mNotificationManagerCompat)
                .when(() -> NotificationManagerCompat.from(mContext));
        doReturn(false).when(mNotificationManagerCompat).areNotificationsEnabled();

        ConsentNotificationTrigger.showConsentNotification(mContext, true);

        verify(() -> UiStatsLogger.logRequestedNotification(mContext));
        verify(() -> UiStatsLogger.logNotificationDisabled(mContext));

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).recordNotificationDisplayed();
        verifyNoMoreInteractions(mConsentManager);
    }

    private String getString(int resourceId) {
        return ApplicationProvider.getApplicationContext().getResources().getString(resourceId);
    }

    private UiObject getElement(int resId) {
        return sDevice.findObject(new UiSelector().text(getString(resId)));
    }
}
