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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Notification;
import android.app.NotificationManager;
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
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.compatibility.common.util.ShellUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class ConsentNotificationTriggerTest {
    private static final String NOTIFICATION_CHANNEL_ID = "PRIVACY_SANDBOX_CHANNEL";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static UiDevice sDevice;

    @Mock private NotificationManagerCompat mNotificationManagerCompat;
    @Mock private ConsentManager mConsentManager;
    private NotificationManager mNotificationManager;
    private Context mContext;

    private MockitoSession mStaticMockSession = null;

    @Mock Flags mMockFlags;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @After
    public void tearDown() throws IOException {
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");
        Runtime.getRuntime()
                .exec(new String[] {"am", "force-stop", "com.android.adservices.tests.ui"});
    }

    @Test
    public void testEuNotification() throws InterruptedException, UiObjectNotFoundException {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ConsentManager.class)
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();

            doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any(Context.class)));
            ExtendedMockito.doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
            mNotificationManager = mContext.getSystemService(NotificationManager.class);
            cancelAllPreviousNotifications();
            final String expectedTitle =
                    mContext.getString(R.string.notificationUI_notification_title_eu);
            final String expectedContent =
                    mContext.getString(R.string.notificationUI_notification_content_eu);

            ConsentNotificationTrigger.showConsentNotification(mContext, true);
            Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

            verify(mConsentManager).disable(any(Context.class));
            verify(mConsentManager).recordNotificationDisplayed();
            verifyNoMoreInteractions(mConsentManager);
            assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
            final Notification notification =
                    mNotificationManager.getActiveNotifications()[0].getNotification();
            assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
            assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE))
                    .isEqualTo(expectedTitle);
            assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
                    .isEqualTo(expectedContent);
            assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags).isEqualTo(0);
            assertThat(Notification.FLAG_NO_CLEAR & notification.flags).isEqualTo(0);

            sDevice.openNotification();
            sDevice.wait(Until.hasObject(By.pkg("com.android.systemui")), LAUNCH_TIMEOUT);
            UiObject scroller =
                    sDevice.findObject(
                            new UiSelector()
                                    .packageName("com.android.systemui")
                                    .resourceId(
                                            "com.android.systemui:id/notification_stack_scroller"));
            assertThat(scroller.exists()).isTrue();
            UiSelector notificationCardSelector =
                    new UiSelector().text(getString(R.string.notificationUI_notification_title_eu));
            UiObject notificationCard = scroller.getChild(notificationCardSelector);
            assertThat(notificationCard.exists()).isTrue();

            notificationCard.click();
            Thread.sleep(LAUNCH_TIMEOUT);
            UiObject title = getElement(R.string.notificationUI_header_title_eu);
            assertThat(title.exists()).isTrue();
        } finally {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testEuNotification_gaUxFlagEnabled() throws InterruptedException {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ConsentManager.class)
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();

            doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any(Context.class)));
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            mNotificationManager = mContext.getSystemService(NotificationManager.class);
            cancelAllPreviousNotifications();
            final String expectedTitle =
                    mContext.getString(R.string.notificationUI_notification_ga_title_eu);
            final String expectedContent =
                    mContext.getString(R.string.notificationUI_notification_ga_content_eu);

            ConsentNotificationTrigger.showConsentNotification(mContext, true);
            Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

            verify(mConsentManager).disable(any(Context.class), eq(AdServicesApiType.TOPICS));
            verify(mConsentManager).disable(any(Context.class), eq(AdServicesApiType.FLEDGE));
            verify(mConsentManager).disable(any(Context.class), eq(AdServicesApiType.MEASUREMENTS));
            verify(mConsentManager).recordGaUxNotificationDisplayed();
            verifyNoMoreInteractions(mConsentManager);

            assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
            final Notification notification =
                    mNotificationManager.getActiveNotifications()[0].getNotification();
            assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
            assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE))
                    .isEqualTo(expectedTitle);
            assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
                    .isEqualTo(expectedContent);
            assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags)
                    .isEqualTo(Notification.FLAG_ONGOING_EVENT);
            assertThat(Notification.FLAG_NO_CLEAR & notification.flags)
                    .isEqualTo(Notification.FLAG_NO_CLEAR);
        } finally {
            mStaticMockSession.finishMocking();
        }
    }

    private void cancelAllPreviousNotifications() {
        if (mNotificationManager.getActiveNotifications().length > 0) {
            mNotificationManager.cancelAll();
        }
    }

    @Test
    public void testNonEuNotifications() throws InterruptedException, UiObjectNotFoundException {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ConsentManager.class)
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();

            doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any(Context.class)));
            mNotificationManager = mContext.getSystemService(NotificationManager.class);
            cancelAllPreviousNotifications();
            final String expectedTitle =
                    mContext.getString(R.string.notificationUI_notification_title);
            final String expectedContent =
                    mContext.getString(R.string.notificationUI_notification_content);

            ConsentNotificationTrigger.showConsentNotification(mContext, false);
            Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

            verify(mConsentManager).enable(any(Context.class));
            verify(mConsentManager).recordNotificationDisplayed();
            verifyNoMoreInteractions(mConsentManager);
            assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
            final Notification notification =
                    mNotificationManager.getActiveNotifications()[0].getNotification();
            assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
            assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE))
                    .isEqualTo(expectedTitle);
            assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
                    .isEqualTo(expectedContent);
            assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags).isEqualTo(0);
            assertThat(Notification.FLAG_NO_CLEAR & notification.flags).isEqualTo(0);

            sDevice.openNotification();
            sDevice.wait(Until.hasObject(By.pkg("com.android.systemui")), LAUNCH_TIMEOUT);

            UiObject scroller =
                    sDevice.findObject(
                            new UiSelector()
                                    .packageName("com.android.systemui")
                                    .resourceId(
                                            "com.android.systemui:id/notification_stack_scroller"));
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
        } finally {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testNonEuNotifications_gaUxEnabled() throws InterruptedException {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ConsentManager.class)
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();

            doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any(Context.class)));
            mNotificationManager = mContext.getSystemService(NotificationManager.class);
            cancelAllPreviousNotifications();
            final String expectedTitle =
                    mContext.getString(R.string.notificationUI_notification_ga_title);
            final String expectedContent =
                    mContext.getString(R.string.notificationUI_notification_ga_content);

            ConsentNotificationTrigger.showConsentNotification(mContext, false);
            Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

            verify(mConsentManager).enable(any(Context.class), eq(AdServicesApiType.TOPICS));
            verify(mConsentManager).enable(any(Context.class), eq(AdServicesApiType.FLEDGE));
            verify(mConsentManager).enable(any(Context.class), eq(AdServicesApiType.MEASUREMENTS));
            verify(mConsentManager).recordGaUxNotificationDisplayed();
            verifyNoMoreInteractions(mConsentManager);

            assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
            final Notification notification =
                    mNotificationManager.getActiveNotifications()[0].getNotification();
            assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
            assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE))
                    .isEqualTo(expectedTitle);
            assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
                    .isEqualTo(expectedContent);
            assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags).isEqualTo(0);
            assertThat(Notification.FLAG_NO_CLEAR & notification.flags).isEqualTo(0);
            assertThat(notification.actions).isNull();
        } finally {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testNotificationsDisabled() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(NotificationManagerCompat.class)
                        .spyStatic(ConsentManager.class)
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();

            doReturn(mNotificationManagerCompat)
                    .when(() -> NotificationManagerCompat.from(any(Context.class)));
            doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any(Context.class)));
            doReturn(false).when(mNotificationManagerCompat).areNotificationsEnabled();

            ConsentNotificationTrigger.showConsentNotification(mContext, true);

            verify(mConsentManager).recordNotificationDisplayed();
            verifyNoMoreInteractions(mConsentManager);
        } finally {
            mStaticMockSession.finishMocking();
        }
    }

    private String getString(int resourceId) {
        return ApplicationProvider.getApplicationContext().getResources().getString(resourceId);
    }

    private UiObject getElement(int resId) {
        return sDevice.findObject(new UiSelector().text(getString(resId)));
    }
}
