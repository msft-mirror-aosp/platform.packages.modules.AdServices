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
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.NotificationManagerCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.api.R;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.topics.classifier.ModelManager;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@RunWith(AndroidJUnit4.class)
public class ConsentNotificationTriggerTest {
    private static final String NOTIFICATION_CHANNEL_ID = "PRIVACY_SANDBOX_CHANNEL";

    @Mock private NotificationManagerCompat mNotificationManagerCompat;
    @Mock private ConsentManager mConsentManager;
    @Mock private ModelManager mModelManager;
    private NotificationManager mNotificationManager;
    private Context mContext;

    private MockitoSession mStaticMockSession = null;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testEuNotification() throws InterruptedException {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ModelManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            doReturn(mModelManager).when(() -> ModelManager.getInstance(any(Context.class)));
            mNotificationManager = mContext.getSystemService(NotificationManager.class);
            final String expectedTitle =
                    mContext.getString(R.string.notificationUI_notification_title_eu);
            final String expectedContent =
                    mContext.getString(R.string.notificationUI_notification_content_eu);

            ConsentNotificationTrigger.showConsentNotification(mContext, true);
            Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

            assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
            final Notification notification =
                    mNotificationManager.getActiveNotifications()[0].getNotification();
            assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
            assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE))
                    .isEqualTo(expectedTitle);
            assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
                    .isEqualTo(expectedContent);
            Thread.sleep(5000); // wait 5s to make sure that Notification disappears.
        } finally {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testNonEuNotifications() throws InterruptedException {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ModelManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            doReturn(mModelManager).when(() -> ModelManager.getInstance(any(Context.class)));
            mNotificationManager = mContext.getSystemService(NotificationManager.class);
            final String expectedTitle =
                    mContext.getString(R.string.notificationUI_notification_title);
            final String expectedContent =
                    mContext.getString(R.string.notificationUI_notification_content);

            ConsentNotificationTrigger.showConsentNotification(mContext, false);
            Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

            assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
            final Notification notification =
                    mNotificationManager.getActiveNotifications()[0].getNotification();
            assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
            assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE))
                    .isEqualTo(expectedTitle);
            assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
                    .isEqualTo(expectedContent);
            Thread.sleep(5000); // wait 5s to make sure that Notification disappears.
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
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {

            doReturn(mNotificationManagerCompat)
                    .when(() -> NotificationManagerCompat.from(any(Context.class)));
            doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any(Context.class)));
            doReturn(false).when(mNotificationManagerCompat).areNotificationsEnabled();

            ConsentNotificationTrigger.showConsentNotification(mContext, true);

            verify(mConsentManager).recordNotificationDisplayed(any(PackageManager.class));
            verifyNoMoreInteractions(mConsentManager);
        } finally {
            mStaticMockSession.finishMocking();
        }
    }
}
