/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.ui.settings.activitydelegates;

import static com.android.adservices.ui.settings.expressivefragments.TopicsActivityFragment.TOPICS_BLCOKED_TOIPCS_BUTTON;
import static com.android.adservices.ui.settings.expressivefragments.TopicsActivityFragment.TOPICS_BLOCKED_TOPICS_WHEN_EMPTY_STATE_BUTTON;
import static com.android.adservices.ui.settings.expressivefragments.TopicsActivityFragment.TOPICS_EMPTY_TOPICS_HIDDEN_SECTION;
import static com.android.adservices.ui.settings.expressivefragments.TopicsActivityFragment.TOPICS_FOOTER;
import static com.android.adservices.ui.settings.expressivefragments.TopicsActivityFragment.TOPICS_LEARN_MORE_LINK;
import static com.android.adservices.ui.settings.expressivefragments.TopicsActivityFragment.TOPICS_LIST;
import static com.android.adservices.ui.settings.expressivefragments.TopicsActivityFragment.TOPICS_NO_TOPICS_MESSAGE_SECTION;
import static com.android.adservices.ui.settings.expressivefragments.TopicsActivityFragment.TOPICS_NO_TOPICS_STATE;
import static com.android.adservices.ui.settings.expressivefragments.TopicsActivityFragment.TOPICS_RESET_TOPICS_BUTTON;
import static com.android.adservices.ui.settings.expressivefragments.TopicsActivityFragment.TOPICS_SWITCH_BAR;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.service.topics.TopicsMapper;
import com.android.adservices.ui.settings.DialogFragmentManager;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.BlockedTopicsActivity;
import com.android.adservices.ui.settings.activities.TopicsActivity;
import com.android.adservices.ui.settings.expressivefragments.TopicsActivityFragment;
import com.android.adservices.ui.settings.preferences.AdservicesTwoTargetPreference;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;
import com.android.settingslib.widget.ButtonPreference;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.IntroPreference;

import com.google.common.collect.ImmutableList;

import java.util.Objects;
import java.util.function.Function;

@RequiresApi(Build.VERSION_CODES.S)
public class TopicsActivityFragmentActionDelegate extends BaseActionDelegate {
    private final TopicsViewModel mTopicsViewModel;
    private final TopicsActivityFragment mFragment;

    public TopicsActivityFragmentActionDelegate(
            TopicsActivity topicsActivity,
            TopicsViewModel topicsViewModel,
            TopicsActivityFragment fragment) {
        super(topicsActivity);
        this.mTopicsViewModel = topicsViewModel;
        this.mFragment = fragment;
        listenToTopicsViewModelUiEvents();
    }

    @Override
    public void initGA() {
        // set title
        mActivity.setTitle(R.string.settingsUI_topics_ga_title);
        // consent switch
        SwitchPreferenceCompat topicsSwitchToggle =
                Objects.requireNonNull(mFragment.findPreference(TOPICS_SWITCH_BAR));
        topicsSwitchToggle.setOnPreferenceChangeListener(
                (preference, intendedDebugLoggingValue) -> {
                    mTopicsViewModel.consentSwitchPreferenceClickHandler(
                            (SwitchPreferenceCompat) topicsSwitchToggle);
                    return true;
                });
        mTopicsViewModel.getTopicsConsent().observe(mFragment, topicsSwitchToggle::setChecked);

        IntroPreference noTopicsState =
                (IntroPreference)
                        Objects.requireNonNull(mFragment.findPreference(TOPICS_NO_TOPICS_STATE));
        noTopicsState.setLearnMoreText(
                mActivity
                        .getResources()
                        .getString(R.string.settingsUI_topics_view_fragment_no_topics_link_text));
        noTopicsState.setLearnMoreAction(view -> setLinkAction(mActivity, TOPICS_LEARN_MORE_LINK));

        // enable blocked button if there is blocked topics
        Function<ButtonPreference, Observer<ImmutableList<Topic>>> observerProvider =
                controls ->
                        list -> {
                            if (list.isEmpty()) {
                                controls.setEnabled(false);
                                ((ButtonPreference) controls)
                                        .setTitle(R.string.settingsUI_no_blocked_topics_ga_text);
                            } else {
                                controls.setEnabled(true);
                                ((ButtonPreference) controls)
                                        .setTitle(R.string.settingsUI_view_blocked_topics_title);
                            }
                        };

        ButtonPreference buttonPreference =
                (ButtonPreference)
                        Objects.requireNonNull(
                                mFragment.findPreference(
                                        TOPICS_BLOCKED_TOPICS_WHEN_EMPTY_STATE_BUTTON));
        buttonPreference.setGravity(Gravity.CENTER_HORIZONTAL);
        // set the button attribute to filled and large, direct import these attr from the
        // preference does not recognized and caused build issue.
        buttonPreference.setButtonStyle(0, 1);
        mTopicsViewModel
                .getBlockedTopics()
                .observe(mActivity, observerProvider.apply(buttonPreference));
        configureSharedElements();
    }

    @Override
    public void initU18() {
        // set title
        mActivity.setTitle(R.string.settingsUI_topics_ga_title);
    }

    @Override
    public void initGaUxWithPas() {
        initGA();
        // initial footer
        FooterPreference footer = Objects.requireNonNull(mFragment.findPreference(TOPICS_FOOTER));
        String footerSummary =
                mActivity
                        .getResources()
                        .getString(R.string.settingsUI_pas_topics_view_fragment_footer);
        footer.setSummary(footerSummary);
        footer.setLearnMoreText(
                mActivity
                        .getResources()
                        .getString(R.string.settingsUI_pas_topics_fragment_learn_more));
        footer.setLearnMoreAction(view -> setLinkAction(mActivity, TOPICS_LEARN_MORE_LINK));
    }

    private void configureSharedElements() {
        // blocked topics and reset button
        Preference blockedTopicsButton =
                (Preference)
                        Objects.requireNonNull(
                                mFragment.findPreference(TOPICS_BLCOKED_TOIPCS_BUTTON));
        blockedTopicsButton.setOnPreferenceClickListener(
                preference -> {
                    mTopicsViewModel.blockedTopicsFragmentButtonClickHandler();
                    return true;
                });
        Preference resetTopicsButton =
                (Preference)
                        Objects.requireNonNull(
                                mFragment.findPreference(TOPICS_RESET_TOPICS_BUTTON));
        resetTopicsButton.setOnPreferenceClickListener(
                preference -> {
                    mTopicsViewModel.resetTopicsButtonClickHandler();
                    return true;
                });
        ButtonPreference blocked_topics_when_empty_state_button =
                (ButtonPreference)
                        Objects.requireNonNull(
                                mFragment.findPreference(
                                        TOPICS_BLOCKED_TOPICS_WHEN_EMPTY_STATE_BUTTON));
        blocked_topics_when_empty_state_button.setOnClickListener(
                preference -> {
                    mTopicsViewModel.blockedTopicsFragmentButtonClickHandler();
                });
        setUpTopicsListObserve();
    }

    private AdservicesTwoTargetPreference getTopicPreference(Topic topic) {
        AdservicesTwoTargetPreference twoTargetPreference =
                new AdservicesTwoTargetPreference(
                        mActivity.getApplicationContext(),
                        mActivity.getResources().getString(R.string.settingsUI_block_topic_title));
        twoTargetPreference.setOnClickListener(
                (v) -> mTopicsViewModel.revokeTopicConsentButtonClickHandler(topic));
        twoTargetPreference.setTitle(
                mActivity
                        .getResources()
                        .getString(getTopicsTitleResId(topic, mActivity.getApplicationContext())));
        return twoTargetPreference;
    }

    private int getTopicsTitleResId(Topic topic, Context context) {
        int resourceId = TopicsMapper.getResourceIdByTopic(topic, context);
        if (resourceId == 0) {
            throw new IllegalArgumentException(
                    String.format("Android resource id for topic %s doesn't exist.", topic));
        }
        return resourceId;
    }

    private void setUpTopicsListObserve() {
        PreferenceCategory emptyTopicsHiddenSection =
                (PreferenceCategory)
                        Objects.requireNonNull(
                                mFragment.findPreference(TOPICS_EMPTY_TOPICS_HIDDEN_SECTION));
        emptyTopicsHiddenSection.setVisible(false);
        PreferenceCategory noTopicsMessageSection =
                (PreferenceCategory)
                        Objects.requireNonNull(
                                mFragment.findPreference(TOPICS_NO_TOPICS_MESSAGE_SECTION));
        PreferenceCategory topics_category =
                (PreferenceCategory) Objects.requireNonNull(mFragment.findPreference(TOPICS_LIST));
        Observer<ImmutableList<Topic>> topicsObserver =
                list -> {
                    topics_category.removeAll();
                    if (list.isEmpty()) {
                        emptyTopicsHiddenSection.setVisible(false);
                        noTopicsMessageSection.setVisible(true);
                    } else {
                        emptyTopicsHiddenSection.setVisible(true);
                        noTopicsMessageSection.setVisible(false);
                        for (Topic topic : list) {
                            topics_category.addPreference(getTopicPreference(topic));
                        }
                    }
                };
        mTopicsViewModel.getTopics().observe(mActivity, topicsObserver);
    }

    private void listenToTopicsViewModelUiEvents() {
        Observer<Pair<TopicsViewModel.TopicsViewModelUiEvent, Topic>> observer =
                eventTopicPair -> {
                    if (eventTopicPair == null) {
                        return;
                    }
                    TopicsViewModel.TopicsViewModelUiEvent event = eventTopicPair.first;
                    Topic topic = eventTopicPair.second;
                    if (event == null) {
                        return;
                    }
                    try {
                        switch (event) {
                            case SWITCH_ON_TOPICS:
                                if (FlagsFactory.getFlags().getToggleSpeedBumpEnabled()) {
                                    DialogFragmentManager.showOptInTopicsDialog(mActivity);
                                }
                                mTopicsViewModel.setTopicsConsent(true);
                                mTopicsViewModel.refresh();
                                break;
                            case SWITCH_OFF_TOPICS:
                                if (FlagsFactory.getFlags().getToggleSpeedBumpEnabled()) {
                                    DialogFragmentManager.showOptOutTopicsDialog(
                                            mActivity, mTopicsViewModel);
                                } else {
                                    mTopicsViewModel.setTopicsConsent(false);
                                    mTopicsViewModel.refresh();
                                }
                                break;
                            case BLOCK_TOPIC:
                                UiStatsLogger.logBlockTopicSelected();
                                if (FlagsFactory.getFlags().getUiDialogsFeatureEnabled()) {
                                    if (FlagsFactory.getFlags().getUiDialogFragmentEnabled()) {
                                        DialogFragmentManager.showBlockTopicDialog(
                                                mActivity, mTopicsViewModel, topic);
                                    } else {
                                        DialogManager.showBlockTopicDialog(
                                                mActivity, mTopicsViewModel, topic);
                                    }
                                } else {
                                    mTopicsViewModel.revokeTopicConsent(topic);
                                }
                                break;
                            case RESET_TOPICS:
                                UiStatsLogger.logResetTopicSelected();
                                if (FlagsFactory.getFlags().getUiDialogsFeatureEnabled()) {
                                    if (FlagsFactory.getFlags().getUiDialogFragmentEnabled()) {
                                        DialogFragmentManager.showResetTopicDialog(
                                                mActivity, mTopicsViewModel);
                                    } else {
                                        DialogManager.showResetTopicDialog(
                                                mActivity, mTopicsViewModel);
                                    }
                                } else {
                                    mTopicsViewModel.resetTopics();
                                }
                                break;
                            case DISPLAY_BLOCKED_TOPICS_FRAGMENT:
                                Log.i("adservices", "display blocked topics click");
                                Intent intent = new Intent(mActivity, BlockedTopicsActivity.class);
                                mActivity.startActivity(intent);
                                break;
                        }
                    } finally {
                        mTopicsViewModel.uiEventHandled();
                    }
                };
        mTopicsViewModel.getUiEvents().observe(mActivity, observer);
    }
}
