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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.content.Intent;

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
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.PhFlags;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class NotificationActivityUiAutomatorTest {
    private static final String NOTIFICATION_TEST_PACKAGE =
            "android.test.adservices.ui.NOTIFICATIONS";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static Context sContext;
    private static UiDevice sDevice;
    private static Intent sIntent;
    private MockitoSession mStaticMockSession;
    private PhFlags mPhFlags;

    @Before
    public void setup() throws UiObjectNotFoundException, IOException {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(PhFlags.class)
                        .spyStatic(BackgroundJobsManager.class)
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        ExtendedMockito.doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
        ExtendedMockito.doNothing()
                .when(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
        mPhFlags = spy(PhFlags.getInstance());
        doReturn(true).when(mPhFlags).getUIDialogsFeatureEnabled();
        ExtendedMockito.doReturn(mPhFlags).when(PhFlags::getInstance);

        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        sDevice.pressHome();

        // Wait for launcher
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);

        // Create intent
        sContext = ApplicationProvider.getApplicationContext();
        sIntent = new Intent(NOTIFICATION_TEST_PACKAGE);
        sIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void moreButtonTest() throws UiObjectNotFoundException, InterruptedException {
        startActivity(true);
        UiObject leftControlButton =
                getElement(R.string.notificationUI_left_control_button_text_eu);
        UiObject rightControlButton =
                getElement(R.string.notificationUI_right_control_button_text_eu);
        UiObject moreButton = getElement(R.string.notificationUI_more_button_text);
        assertThat(leftControlButton.exists()).isFalse();
        assertThat(rightControlButton.exists()).isFalse();
        assertThat(moreButton.exists()).isTrue();

        while (moreButton.exists()) {
            moreButton.click();
            Thread.sleep(2000);
        }
        assertThat(leftControlButton.exists()).isTrue();
        assertThat(rightControlButton.exists()).isTrue();
        assertThat(moreButton.exists()).isFalse();
    }

    @Test
    public void acceptedConfirmationScreenTest()
            throws UiObjectNotFoundException, InterruptedException {
        startActivity(true);
        UiObject leftControlButton =
                getElement(R.string.notificationUI_left_control_button_text_eu);
        UiObject rightControlButton =
                getElement(R.string.notificationUI_right_control_button_text_eu);
        UiObject moreButton = getElement(R.string.notificationUI_more_button_text);
        assertThat(leftControlButton.exists()).isFalse();
        assertThat(rightControlButton.exists()).isFalse();
        assertThat(moreButton.exists()).isTrue();

        while (moreButton.exists()) {
            moreButton.click();
            Thread.sleep(2000);
        }
        assertThat(leftControlButton.exists()).isTrue();
        assertThat(rightControlButton.exists()).isTrue();
        assertThat(moreButton.exists()).isFalse();

        rightControlButton.click();
        UiObject acceptedTitle = getElement(R.string.notificationUI_confirmation_accept_title);
        assertThat(acceptedTitle.exists()).isTrue();
    }

    private void startActivity(boolean isEUActivity) {
        // Send intent
        sIntent.putExtra("isEUDevice", isEUActivity);
        sContext.startActivity(sIntent);

        // Wait for the app to appear
        sDevice.wait(Until.hasObject(By.pkg(NOTIFICATION_TEST_PACKAGE).depth(0)), LAUNCH_TIMEOUT);
    }

    private String getString(int resourceId) {
        return ApplicationProvider.getApplicationContext().getResources().getString(resourceId);
    }

    private UiObject getElement(int resId) {
        return sDevice.findObject(new UiSelector().text(getString(resId)));
    }
}
