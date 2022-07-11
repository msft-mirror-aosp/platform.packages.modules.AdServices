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
package com.android.adservices.ui.settings;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.mockito.Mockito.doReturn;

import android.widget.Switch;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsMainFragment;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;

import com.google.common.collect.ImmutableList;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

/** Tests for {@link AdServicesSettingsActivity}. */
public class SettingsActivityTest {
    static ViewModelProvider sViewModelProvider = Mockito.mock(ViewModelProvider.class);
    static ConsentManager sConsentManager = Mockito.mock(ConsentManager.class);

    /**
     * {@link ActivityScenarioRule} is a JUnit {@link Rule @Rule} to launch your activity under
     * test.
     *
     * <p>Rules are interceptors which are executed for each test method and are important building
     * blocks of Junit tests.
     */
    @Rule
    public ActivityScenarioRule mRule =
            new ActivityScenarioRule<>(AdServicesSettingsActivityWrapper.class);

    /**
     * This is used by {@link AdServicesSettingsActivityWrapper}. Provides a mocked {@link
     * ViewModelProvider} that serves mocked view models, which use a mocked {@link ConsentManager},
     * which gives mocked data.
     *
     * @return the mocked {@link ViewModelProvider}
     */
    public static ViewModelProvider generateMockedViewModelProvider() {
        List<Topic> tempList = new ArrayList<>();
        tempList.add(Topic.create(1, 1, 1));
        tempList.add(Topic.create(2, 1, 1));
        tempList.add(Topic.create(3, 1, 1));
        ImmutableList<Topic> topicsList = ImmutableList.copyOf(tempList);
        doReturn(topicsList).when(sConsentManager).getKnownTopicsWithConsent();

        tempList = new ArrayList<>();
        tempList.add(Topic.create(4, 1, 1));
        tempList.add(Topic.create(5, 1, 1));
        ImmutableList<Topic> blockedTopicsList = ImmutableList.copyOf(tempList);
        doReturn(blockedTopicsList).when(sConsentManager).getTopicsWithRevokedConsent();

        TopicsViewModel topicsViewModel =
                new TopicsViewModel(ApplicationProvider.getApplicationContext(), sConsentManager);
        MainViewModel mainViewModel =
                new MainViewModel(ApplicationProvider.getApplicationContext());
        doReturn(topicsViewModel).when(sViewModelProvider).get(TopicsViewModel.class);
        doReturn(mainViewModel).when(sViewModelProvider).get(MainViewModel.class);
        return sViewModelProvider;
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
     * Test if the strings (settingsUI_topics_title, settingsUI_apps_title,
     * settingsUI_privacy_sandbox_beta_title) are displayed in {@link
     * AdServicesSettingsMainFragment}.
     */
    @Test
    public void test_MainFragmentView_isDisplayed() {
        onPreferenceScreen()
                .check(matches(hasDescendant(withText(R.string.settingsUI_topics_title))));
        onPreferenceScreen()
                .check(matches(hasDescendant(withText(R.string.settingsUI_apps_title))));
        onPreferenceScreen()
                .check(
                        matches(
                                hasDescendant(
                                        withText(R.string.settingsUI_privacy_sandbox_beta_title))));
    }

    /**
     *  Test if {@link MainViewModel} works if Activity is recreated (simulates rotate phone).
     */
    @Test
    public void test_MainViewModel_getConsent() {
        onPreferenceScreen()
                .check(
                        matches(
                                hasDescendant(
                                        Matchers.allOf(
                                                withClassName(Matchers.is(Switch.class.getName())),
                                                isChecked()))));
        onPreferenceScreen()
                .perform(
                        RecyclerViewActions.actionOnItem(
                                hasDescendant(withClassName(Matchers.is(Switch.class.getName()))),
                                click()));

        mRule.getScenario().recreate();
        onPreferenceScreen()
                .check(
                        matches(
                                hasDescendant(
                                        Matchers.allOf(
                                                withClassName(Matchers.is(Switch.class.getName())),
                                                Matchers.not(isChecked())))));
    }

    /**
     * Test if the Topics button in the main fragment opens the topics fragment, and the back button
     * returns to the main fragment.
     */
    @Test
    public void test_TopicsView() {
        assertMainFragmentDisplayed();

        onPreferenceScreen()
                .perform(
                        RecyclerViewActions.actionOnItem(
                                hasDescendant(withText(R.string.settingsUI_topics_title)),
                                click()));

        assertTopicsFragmentDisplayed();

        pressBack();

        assertMainFragmentDisplayed();
    }

    /**
     * Test if the Topics button in the main fragment opens the topics fragment, and the back button
     * returns to the main fragment.
     */
    @Test
    public void test_BlockedTopicsView() {
        assertMainFragmentDisplayed();

        onPreferenceScreen()
                .perform(
                        RecyclerViewActions.actionOnItem(
                                hasDescendant(withText(R.string.settingsUI_topics_title)),
                                click()));

        assertTopicsFragmentDisplayed();

        onView(withId(R.id.blocked_topics_button)).perform(scrollTo(), click());

        assertBlockedTopicsFragmentDisplayed();

        pressBack();

        assertTopicsFragmentDisplayed();

        pressBack();

        assertMainFragmentDisplayed();
    }

    private void assertMainFragmentDisplayed() {
        onView(withText(R.string.settingsUI_privacy_sandbox_beta_switch_summary))
                .check(matches(isDisplayed()));
    }

    private void assertTopicsFragmentDisplayed() {
        onView(
                        Matchers.allOf(
                                Matchers.not(hasDescendant(withId(R.id.blocked_topics_button))),
                                hasDescendant(withText(R.string.settingsUI_blocked_topics_title))))
                .check(matches(isDisplayed()));
    }

    private void assertBlockedTopicsFragmentDisplayed() {
        onView(withText(R.string.settingsUI_blocked_topics_title)).check(matches(isDisplayed()));
    }

    private ViewInteraction onPreferenceScreen() {
        // R.id.recycler_view refers to the RecyclerView used internally by PreferenceFragmentCompat
        return onView(withId(R.id.recycler_view));
    }
}
