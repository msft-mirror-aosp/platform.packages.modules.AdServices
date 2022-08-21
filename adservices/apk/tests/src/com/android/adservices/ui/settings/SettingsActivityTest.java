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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;

import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.PerformException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.espresso.util.HumanReadables;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.consent.App;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.topics.classifier.ModelManager;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsMainFragment;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Tests for {@link AdServicesSettingsActivity}. */
public class SettingsActivityTest {
    static ViewModelProvider sViewModelProvider = Mockito.mock(ViewModelProvider.class);
    static ConsentManager sConsentManager;
    private MockitoSession mStaticMockSession;

    @Mock ModelManager mModelManager;

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

    /**
     * This is used by {@link AdServicesSettingsActivityWrapper}. Provides a mocked {@link
     * ViewModelProvider} that serves mocked view models, which use a mocked {@link ConsentManager},
     * which gives mocked data.
     *
     * @return the mocked {@link ViewModelProvider}
     */
    public ViewModelProvider generateMockedViewModelProvider() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ModelManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(mModelManager)
                    .when(() -> ModelManager.getInstance(any(Context.class)));
            sConsentManager =
                    spy(ConsentManager.getInstance(ApplicationProvider.getApplicationContext()));
            List<Topic> tempList = new ArrayList<>();
            tempList.add(Topic.create(10001, 1, 1));
            tempList.add(Topic.create(10002, 1, 1));
            tempList.add(Topic.create(10003, 1, 1));
            ImmutableList<Topic> topicsList = ImmutableList.copyOf(tempList);
            doReturn(topicsList).when(sConsentManager).getKnownTopicsWithConsent();

            tempList = new ArrayList<>();
            tempList.add(Topic.create(10004, 1, 1));
            tempList.add(Topic.create(10005, 1, 1));
            ImmutableList<Topic> blockedTopicsList = ImmutableList.copyOf(tempList);
            doReturn(blockedTopicsList).when(sConsentManager).getTopicsWithRevokedConsent();

            List<App> appTempList = new ArrayList<>();
            appTempList.add(App.create("app1"));
            appTempList.add(App.create("app2"));
            ImmutableList<App> appsList = ImmutableList.copyOf(appTempList);
            doReturn(appsList).when(sConsentManager).getKnownAppsWithConsent();

            appTempList = new ArrayList<>();
            appTempList.add(App.create("app3"));
            ImmutableList<App> blockedAppsList = ImmutableList.copyOf(appTempList);
            doReturn(blockedAppsList).when(sConsentManager).getAppsWithRevokedConsent();

            doNothing().when(sConsentManager).resetTopicsAndBlockedTopics();
            try {
                doNothing().when(sConsentManager).resetAppsAndBlockedApps();
            } catch (IOException e) {
                e.printStackTrace();
            }
            doNothing().when(sConsentManager).resetMeasurement();

            TopicsViewModel topicsViewModel =
                    new TopicsViewModel(
                            ApplicationProvider.getApplicationContext(), sConsentManager);
            AppsViewModel appsViewModel =
                    new AppsViewModel(ApplicationProvider.getApplicationContext(), sConsentManager);
            MainViewModel mainViewModel =
                    new MainViewModel(ApplicationProvider.getApplicationContext(), sConsentManager);
            doReturn(topicsViewModel).when(sViewModelProvider).get(TopicsViewModel.class);
            doReturn(mainViewModel).when(sViewModelProvider).get(MainViewModel.class);
            doReturn(appsViewModel).when(sViewModelProvider).get(AppsViewModel.class);
            return sViewModelProvider;
        } finally {
            mStaticMockSession.finishMocking();
        }
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
     * settingsUI_main_view_title) are displayed in {@link AdServicesSettingsMainFragment}.
     */
    @Test
    public void test_MainFragmentView_isDisplayed() {
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

    /** Test if {@link MainViewModel} works if Activity is recreated (simulates rotate phone). */
    @Test
    public void test_MainViewModel_getConsent() {
        onView(withId(R.id.main_fragment))
                .check(
                        matches(
                                hasDescendant(
                                        Matchers.allOf(
                                                withClassName(Matchers.is(Switch.class.getName())),
                                                isChecked()))));
        onView(withText(R.string.settingsUI_privacy_sandbox_beta_switch_title))
                .perform(nestedScrollTo(), click());

        mRule.getScenario().recreate();
        onView(withId(R.id.main_fragment))
                .check(
                        matches(
                                hasDescendant(
                                        Matchers.allOf(
                                                withClassName(Matchers.is(Switch.class.getName())),
                                                Matchers.not(isChecked())))));

        // Give consent back
        onView(withText(R.string.settingsUI_privacy_sandbox_beta_switch_title))
                .perform(nestedScrollTo(), click());
        onView(withId(R.id.main_fragment))
                .check(
                        matches(
                                hasDescendant(
                                        Matchers.allOf(
                                                withClassName(Matchers.is(Switch.class.getName())),
                                                isChecked()))));
    }

    /**
     * Test if the Topics button in the main fragment opens the topics fragment, and the back button
     * returns to the main fragment.
     */
    @Test
    public void test_TopicsView() {
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
}
