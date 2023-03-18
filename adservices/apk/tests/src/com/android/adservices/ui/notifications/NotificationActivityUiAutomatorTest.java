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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.PhFlags;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
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
    @Mock private ConsentManager mConsentManager;

    // TODO(b/261216850): Migrate this NotificationActivity to non-mock test
    @Mock private Flags mMockFlags;

    @Before
    public void setup() throws UiObjectNotFoundException, IOException {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(ApkTestUtil.isDeviceSupported());

        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(PhFlags.class)
                        .spyStatic(BackgroundJobsManager.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(ConsentManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        doReturn(true).when(mMockFlags).getUIDialogsFeatureEnabled();
        doReturn(false).when(mMockFlags).isUiFeatureTypeLoggingEnabled();
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(mConsentManager)
                .when(() -> ConsentManager.getInstance(any(Context.class)));

        ExtendedMockito.doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();

        ExtendedMockito.doNothing()
                .when(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));

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
    public void teardown() throws IOException {
        if (!ApkTestUtil.isDeviceSupported()) return;

        AdservicesTestHelper.killAdservicesProcess(sContext);
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

    @Test
    public void notificationEuGaTest() throws UiObjectNotFoundException, InterruptedException {
        ExtendedMockito.doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();

        startActivity(true);

        UiObject notificationEuGaTitle = getElement(R.string.notificationUI_header_ga_title_eu);
        assertThat(notificationEuGaTitle.exists()).isTrue();

        UiObject leftControlButton =
                getElement(R.string.notificationUI_left_control_button_text_eu);
        UiObject rightControlButton =
                getElement(R.string.notificationUI_right_control_button_ga_text_eu);
        UiObject moreButton = getElement(R.string.notificationUI_more_button_text);

        verifyControlsAndMoreButtonAreDisplayed(leftControlButton, rightControlButton, moreButton);
        verify(mConsentManager).getDefaultConsent();
        verify(mConsentManager).getDefaultAdIdState();
        verify(mConsentManager).getCurrentPrivacySandboxFeature();
        verifyNoMoreInteractions(mConsentManager);
    }

    @Test
    public void notificationRowGaTest() throws UiObjectNotFoundException, InterruptedException {
        ExtendedMockito.doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();

        startActivity(false);

        UiObject notificationGaTitle = getElement(R.string.notificationUI_header_ga_title);
        assertThat(notificationGaTitle.exists()).isTrue();

        UiObject leftControlButton = getElement(R.string.notificationUI_left_control_button_text);
        UiObject rightControlButton = getElement(R.string.notificationUI_right_control_button_text);
        UiObject moreButton = getElement(R.string.notificationUI_more_button_text);

        verifyControlsAndMoreButtonAreDisplayed(leftControlButton, rightControlButton, moreButton);
        verify(mConsentManager).getDefaultConsent();
        verify(mConsentManager).getDefaultAdIdState();
        verify(mConsentManager).getCurrentPrivacySandboxFeature();
        verifyNoMoreInteractions(mConsentManager);
    }

    @Test
    public void acceptedConfirmationScreenGaTest()
            throws UiObjectNotFoundException, InterruptedException {
        ExtendedMockito.doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();

        startActivity(true);

        UiObject leftControlButton =
                getElement(R.string.notificationUI_left_control_button_text_eu);
        UiObject rightControlButton =
                getElement(R.string.notificationUI_right_control_button_ga_text_eu);
        UiObject moreButton = getElement(R.string.notificationUI_more_button_text);

        verifyControlsAndMoreButtonAreDisplayed(leftControlButton, rightControlButton, moreButton);

        rightControlButton.click();

        UiObject acceptedTitle = getElement(R.string.notificationUI_fledge_measurement_title);
        assertThat(acceptedTitle.exists()).isTrue();

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).enable(any(Context.class), eq(AdServicesApiType.TOPICS));
        verify(mConsentManager).enable(any(Context.class), eq(AdServicesApiType.FLEDGE));
        verify(mConsentManager).enable(any(Context.class), eq(AdServicesApiType.MEASUREMENTS));
        verify(mConsentManager, times(2)).getCurrentPrivacySandboxFeature();
        verifyNoMoreInteractions(mConsentManager);
    }

    @Test
    public void declinedConfirmationScreenGaTest()
            throws UiObjectNotFoundException, InterruptedException {
        ExtendedMockito.doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();

        startActivity(true);

        UiObject leftControlButton =
                getElement(R.string.notificationUI_left_control_button_text_eu);
        UiObject rightControlButton =
                getElement(R.string.notificationUI_right_control_button_ga_text_eu);
        UiObject moreButton = getElement(R.string.notificationUI_more_button_text);

        verifyControlsAndMoreButtonAreDisplayed(leftControlButton, rightControlButton, moreButton);

        leftControlButton.click();

        UiObject acceptedTitle = getElement(R.string.notificationUI_fledge_measurement_title);
        assertThat(acceptedTitle.exists()).isTrue();

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).disable(any(Context.class), eq(AdServicesApiType.TOPICS));
        verify(mConsentManager).enable(any(Context.class), eq(AdServicesApiType.FLEDGE));
        verify(mConsentManager).enable(any(Context.class), eq(AdServicesApiType.MEASUREMENTS));
        verify(mConsentManager, times(2)).getCurrentPrivacySandboxFeature();
        verifyNoMoreInteractions(mConsentManager);
    }

    private void verifyControlsAndMoreButtonAreDisplayed(
            UiObject leftControlButton, UiObject rightControlButton, UiObject moreButton)
            throws UiObjectNotFoundException, InterruptedException {
        UiObject scrollView =
                sDevice.findObject(new UiSelector().className("android.widget.ScrollView"));

        if (scrollView.isScrollable()) {
            // there should be a more button
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
        } else {
            assertThat(leftControlButton.exists()).isTrue();
            assertThat(rightControlButton.exists()).isTrue();
            assertThat(moreButton.exists()).isFalse();
        }
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
