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
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.mockito.Mockito.any;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;

import androidx.core.widget.NestedScrollView;
import androidx.test.espresso.PerformException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.espresso.util.HumanReadables;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.adservices.api.R;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsMainFragment;
import com.android.dx.mockito.inline.extended.ExtendedMockito;


import junit.framework.AssertionFailedError;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;


/** Tests for {@link AdServicesSettingsActivity}. */
public class SettingsActivityTest {

    private MockitoSession mStaticMockSession;

    private static final class NestedScrollToAction implements ViewAction {
        private static final String TAG =
                androidx.test.espresso.action.ScrollToAction.class.getSimpleName();

        @Override
        public Matcher<View> getConstraints() {
            return Matchers.allOf(
                    ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE),
                    ViewMatchers.isDescendantOfA(
                            Matchers.anyOf(
                                    ViewMatchers.isAssignableFrom(NestedScrollView.class),
                                    ViewMatchers.isAssignableFrom(ScrollView.class),
                                    ViewMatchers.isAssignableFrom(HorizontalScrollView.class),
                                    ViewMatchers.isAssignableFrom(ListView.class))));
        }

        @Override
        public void perform(UiController uiController, View view) {
            if (isDisplayingAtLeast(90).matches(view)) {
                Log.i(TAG, "View is already displayed. Returning.");
                return;
            }
            Rect rect = new Rect();
            view.getDrawingRect(rect);
            if (!view.requestRectangleOnScreen(rect, true /* immediate */)) {
                Log.w(TAG, "Scrolling to view was requested, but none of the parents scrolled.");
            }
            uiController.loopMainThreadUntilIdle();
            if (!isDisplayingAtLeast(90).matches(view)) {
                throw new PerformException.Builder()
                        .withActionDescription(this.getDescription())
                        .withViewDescription(HumanReadables.describe(view))
                        .withCause(
                                new RuntimeException(
                                        "Scrolling to view was attempted, but the view is not "
                                                + "displayed"))
                        .build();
            }
        }

        @Override
        public String getDescription() {
            return "scroll to";
        }
    }

    private static ViewAction nestedScrollTo() {
        return ViewActions.actionWithAssertions(new NestedScrollToAction());
    }

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

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(BackgroundJobsManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        ExtendedMockito.doNothing()
                .when(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    /**
     * Test if {@link AdServicesSettingsMainFragment} is displayed in {@link
     * AdServicesSettingsActivity}.
     */
    @Test
    public void test_FragmentContainer_isDisplayed() {
        giveConsentIfNeeded();
        onView(withId(R.id.fragment_container_view)).check(matches(isDisplayed()));
    }

    /**
     * Test if the strings (settingsUI_topics_title, settingsUI_apps_title,
     * settingsUI_main_view_title) are displayed in {@link AdServicesSettingsMainFragment}.
     */
    @Test
    public void test_MainFragmentView_isDisplayed() {
        giveConsentIfNeeded();
        onView(withText(R.string.settingsUI_privacy_sandbox_beta_switch_title))
                .perform(nestedScrollTo())
                .check(matches(isDisplayed()));
        onView(withText(R.string.settingsUI_topics_title))
                .perform(nestedScrollTo())
                .check(matches(isDisplayed()));
        onView(withText(R.string.settingsUI_apps_title))
                .perform(nestedScrollTo())
                .check(matches(isDisplayed()));
    }

    /**
     * Test if the Topics button in the main fragment opens the topics fragment, and the back button
     * returns to the main fragment.
     */
    @Test
    public void test_TopicsView() {
        giveConsentIfNeeded();

        assertMainFragmentDisplayed();

        onView(withText(R.string.settingsUI_topics_title)).perform(nestedScrollTo(), click());

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
        giveConsentIfNeeded();

        assertMainFragmentDisplayed();

        onView(withText(R.string.settingsUI_topics_title)).perform(nestedScrollTo(), click());

        assertTopicsFragmentDisplayed();

        onView(withId(R.id.blocked_topics_button)).perform(nestedScrollTo(), click());

        assertBlockedTopicsFragmentDisplayed();

        pressBack();

        assertTopicsFragmentDisplayed();

        pressBack();

        assertMainFragmentDisplayed();
    }

    /**
     * Test if the Topics button in the main fragment opens the topics fragment, and the back button
     * returns to the main fragment.
     */
    @Test
    public void test_AppsView() {
        giveConsentIfNeeded();

        assertMainFragmentDisplayed();

        onView(withText(R.string.settingsUI_apps_title)).perform(nestedScrollTo(), click());

        assertAppsFragmentDisplayed();

        pressBack();

        assertMainFragmentDisplayed();
    }

    /**
     * Test if the Topics button in the main fragment opens the topics fragment, and the back button
     * returns to the main fragment.
     */
    @Test
    public void test_BlockedAppsView() {
        giveConsentIfNeeded();

        assertMainFragmentDisplayed();

        onView(withText(R.string.settingsUI_apps_title)).perform(nestedScrollTo(), click());

        assertAppsFragmentDisplayed();

        onView(withId(R.id.blocked_apps_button)).perform(nestedScrollTo(), click());

        assertBlockedAppsFragmentDisplayed();

        pressBack();

        assertAppsFragmentDisplayed();

        pressBack();

        assertMainFragmentDisplayed();
    }

    private void assertMainFragmentDisplayed() {
        onView(withText(R.string.settingsUI_main_view_subtitle))
                .perform(nestedScrollTo())
                .check(matches(isDisplayed()));
    }

    private void assertTopicsFragmentDisplayed() {
        onView(withText(R.string.settingsUI_topics_view_subtitle))
                .perform(nestedScrollTo())
                .check(matches(isDisplayed()));
    }

    private void assertAppsFragmentDisplayed() {
        onView(withText(R.string.settingsUI_apps_view_subtitle))
                .perform(nestedScrollTo())
                .check(matches(isDisplayed()));
    }

    private void assertBlockedTopicsFragmentDisplayed() {
        onView(withId(R.id.blocked_topics_list)).check(matches(isDisplayed()));
    }

    private void assertBlockedAppsFragmentDisplayed() {
        onView(withId(R.id.blocked_apps_list)).check(matches(isDisplayed()));
    }

    private void giveConsentIfNeeded() {
        try {
            onView(withId(R.id.main_fragment))
                    .check(
                            matches(
                                    hasDescendant(
                                            Matchers.allOf(
                                                    withClassName(
                                                            Matchers.is(Switch.class.getName())),
                                                    isChecked()))));
        } catch (AssertionFailedError e) {
            // Give consent
            onView(withText(R.string.settingsUI_privacy_sandbox_beta_switch_title))
                    .perform(nestedScrollTo(), click());
        }
    }
}
