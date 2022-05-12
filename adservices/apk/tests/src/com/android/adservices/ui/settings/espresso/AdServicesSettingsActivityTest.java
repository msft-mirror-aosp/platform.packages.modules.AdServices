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
package com.android.adservices.ui.settings.espresso;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.widget.Switch;

import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.adservices.api.R;
import com.android.adservices.ui.settings.AdServicesSettingsActivity;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;

/**
 *  Tests for {@link AdServicesSettingsMainFragment}.
 */
public class AdServicesSettingsActivityTest {

    /**
     * {@link ActivityScenarioRule} is a JUnit {@link Rule @Rule}
     * to launch your activity under test.
     *
     * Rules are interceptors which are executed for each test method and are important building
     * blocks of Junit tests.
     */
    @Rule
    public ActivityScenarioRule mRule = new ActivityScenarioRule<>(
            AdServicesSettingsActivity.class);

    /**
     *  Test if {@link AdServicesSettingsMainFragment} is displayed in
     *  {@link AdServicesSettingsActivity}.
     */
    @Test
    public void testMainFragmentContainer_isDisplayed() {
        onView(withId(R.id.fragment_container_view)).check(matches(isDisplayed()));
    }

    /**
     *  Test if the strings (settingsUI_topics_title, settingsUI_apps_title,
     *  settingsUI_privacy_sandbox_beta_title) are displayed in
     *  {@link AdServicesSettingsMainFragment}.
     */
    @Test
    public void testMainFragmentViews_isDisplayed() {
        // R.id.recycler_view refers to the RecyclerView used internally by PreferenceFragmentCompat
        onView(withId(R.id.recycler_view))
                .check(matches(hasDescendant(withText(R.string.settingsUI_topics_title))));
        onView(withId(R.id.recycler_view))
                .check(matches(hasDescendant(withText(R.string.settingsUI_apps_title))));
        onView(withId(R.id.recycler_view))
                .check(matches(hasDescendant(withText(
                        R.string.settingsUI_privacy_sandbox_beta_title))));
    }

    /**
     *  Test if getConsent works in {@link MainViewModel}.
     */
    @Test
    public void test_MainViewModel_getConsent() {
        onView(withId(R.id.recycler_view))
                .check(matches(hasDescendant(Matchers.allOf(
                        withClassName(Matchers.is(Switch.class.getName())),
                        isChecked()))));
        onView(withId(R.id.recycler_view))
                .perform(RecyclerViewActions.actionOnItem(hasDescendant(withClassName(
                        Matchers.is(Switch.class.getName()))), click()));

        mRule.getScenario().recreate();
        onView(withId(R.id.recycler_view))
                .check(matches(hasDescendant(Matchers.allOf(
                        withClassName(Matchers.is(Switch.class.getName())),
                        Matchers.not(isChecked())))));
    }
}
