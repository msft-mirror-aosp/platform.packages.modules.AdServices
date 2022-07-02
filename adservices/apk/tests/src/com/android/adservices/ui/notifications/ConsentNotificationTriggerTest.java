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

import static com.google.common.truth.Truth.assertThat;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ConsentNotificationTriggerTest {
    private static final String NOTIFICATION_CHANNEL_ID = "PRIVACY_SANDBOX_CHANNEL";

    private NotificationManager mNotificationManager;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testEuNotification() throws InterruptedException {
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        final String expectedTitle = "Join the ads privacy beta";
        final String expectedContent = "Test new features that will restrict cross-app tracking";

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
    }

    @Test
    public void testNonEuNotifications() throws InterruptedException {
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        final String expectedTitle = "Android's ads privacy beta";
        final String expectedContent =
                "You're testing new features that restrict cross-app tracking";

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
    }
}
