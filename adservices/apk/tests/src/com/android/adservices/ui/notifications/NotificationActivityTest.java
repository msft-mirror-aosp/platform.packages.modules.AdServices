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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;

import com.android.adservices.api.R;
import com.android.adservices.ui.settings.AdServicesSettingsActivity;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsMainFragment;

import org.junit.Before;
import org.junit.Test;

/** Tests for {@link ConsentNotificationActivity}. */
public class NotificationActivityTest {
    private static final String NOTIFICATION_INTENT = "android.test.adservices.ui.NOTIFICATIONS";

    @Before
    public void setup() {
        Intent mIntent = new Intent(NOTIFICATION_INTENT);
        mIntent.putExtra("isEUDevice", false);
        ActivityScenario.launch(mIntent);
    }

    /**
     * Test if {@link AdServicesSettingsMainFragment} is displayed in {@link
     * AdServicesSettingsActivity}.
     */
    @Test
    public void test_FragmentContainer_isDisplayed() {
        onView(withId(R.id.fragment_container_view)).check(matches(isDisplayed()));
    }

    /**
     * Test if {@link ConsentNotificationFragment} is displayed in {@link
     * ConsentNotificationActivity}.
     */
    @Test
    public void test_ConsentNotificationFragment_isDisplayed() {
        checkConsentNotificationFragmentIsDisplayed();
    }

    @Test
    public void test_ConsentNotificationConfirmationFragment_isDisplayed() {
        launchEUActivity();
        checkConsentNotificationFragmentIsDisplayed();

        onView(withId(R.id.rightControlButton)).perform(click());

        onView(withId(R.id.consent_notification_accept_confirmation_view))
                .check(matches(isDisplayed()));
    }

    private void checkConsentNotificationFragmentIsDisplayed() {
        onView(withText(R.string.notificationUI_header_title)).check(matches(isDisplayed()));
    }

    private void launchEUActivity() {
        Intent mIntent = new Intent(NOTIFICATION_INTENT);
        mIntent.putExtra("isEUDevice", true);
        ActivityScenario.launch(mIntent);
    }
}
