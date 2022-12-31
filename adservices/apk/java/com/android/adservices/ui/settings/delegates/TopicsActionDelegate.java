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
package com.android.adservices.ui.settings.delegates;

import android.content.Intent;
import android.util.Pair;
import android.view.View;

import androidx.lifecycle.Observer;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.PhFlags;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.BlockedTopicsActivity;
import com.android.adservices.ui.settings.activities.TopicsActivity;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsTopicsFragment;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel.TopicsViewModelUiEvent;
import com.android.settingslib.widget.MainSwitchBar;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
public class TopicsActionDelegate extends BaseActionDelegate {
    private final TopicsActivity mTopicsActivity;
    private final TopicsViewModel mTopicsViewModel;

    public TopicsActionDelegate(TopicsActivity topicsActivity, TopicsViewModel topicsViewModel) {
        super(topicsActivity);
        mTopicsActivity = topicsActivity;
        mTopicsViewModel = topicsViewModel;
        listenToTopicsViewModelUiEvents();
    }

    private void listenToTopicsViewModelUiEvents() {
        Observer<Pair<TopicsViewModelUiEvent, Topic>> observer =
                eventTopicPair -> {
                    if (eventTopicPair == null) {
                        return;
                    }
                    TopicsViewModelUiEvent event = eventTopicPair.first;
                    Topic topic = eventTopicPair.second;
                    if (event == null) {
                        return;
                    }
                    try {
                        switch (event) {
                            case SWITCH_ON_TOPICS:
                                mTopicsViewModel.setTopicsConsent(true);
                                break;
                            case SWITCH_OFF_TOPICS:
                                if (FlagsFactory.getFlags().getUIDialogsFeatureEnabled()) {
                                    DialogManager.showTopicsOptOutDialog(
                                            mTopicsActivity, mTopicsViewModel);
                                } else {
                                    mTopicsViewModel.setTopicsConsent(false);
                                }
                                break;
                            case BLOCK_TOPIC:
                                logUIAction(ActionEnum.BLOCK_TOPIC_SELECTED);
                                if (PhFlags.getInstance().getUIDialogsFeatureEnabled()) {
                                    DialogManager.showBlockTopicDialog(
                                            mTopicsActivity, mTopicsViewModel, topic);
                                } else {
                                    mTopicsViewModel.revokeTopicConsent(topic);
                                }
                                break;
                            case RESET_TOPICS:
                                logUIAction(ActionEnum.RESET_TOPIC_SELECTED);
                                if (PhFlags.getInstance().getUIDialogsFeatureEnabled()) {
                                    DialogManager.showResetTopicDialog(
                                            mTopicsActivity, mTopicsViewModel);
                                } else {
                                    mTopicsViewModel.resetTopics();
                                }
                                break;
                            case DISPLAY_BLOCKED_TOPICS_FRAGMENT:
                                Intent intent =
                                        new Intent(mTopicsActivity, BlockedTopicsActivity.class);
                                mTopicsActivity.startActivity(intent);
                                break;
                        }
                    } finally {
                        mTopicsViewModel.uiEventHandled();
                    }
                };
        mTopicsViewModel.getUiEvents().observe(mTopicsActivity, observer);
    }

    /**
     * Configure all UI elements (except topics list) in {@link AdServicesSettingsTopicsFragment} to
     * handle user actions.
     */
    public void initTopicsFragment(AdServicesSettingsTopicsFragment fragment) {
        mTopicsActivity.setTitle(R.string.settingsUI_topics_view_title);
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            configureTopicsConsentSwitch(fragment);
        }
        configureBlockedTopicsFragmentButton(fragment);
        configureResetTopicsButton(fragment);
    }

    private void configureTopicsConsentSwitch(AdServicesSettingsTopicsFragment fragment) {
        MainSwitchBar topicsSwitchBar = mTopicsActivity.findViewById(R.id.topics_switch_bar);
        topicsSwitchBar.setVisibility(View.VISIBLE);

        mTopicsViewModel.getTopicsConsent().observe(fragment, topicsSwitchBar::setChecked);
        topicsSwitchBar.setOnClickListener(
                switchBar -> mTopicsViewModel.consentSwitchClickHandler((MainSwitchBar) switchBar));
    }

    private void configureBlockedTopicsFragmentButton(AdServicesSettingsTopicsFragment fragment) {
        View blockedTopicsButton = fragment.requireView().findViewById(R.id.blocked_topics_button);
        View blockedTopicsWhenEmptyListButton =
                fragment.requireView().findViewById(R.id.blocked_topics_when_empty_state_button);

        blockedTopicsButton.setOnClickListener(
                view -> mTopicsViewModel.blockedTopicsFragmentButtonClickHandler());
        blockedTopicsWhenEmptyListButton.setOnClickListener(
                view -> mTopicsViewModel.blockedTopicsFragmentButtonClickHandler());
    }

    private void configureResetTopicsButton(AdServicesSettingsTopicsFragment fragment) {
        View resetTopicsButton = fragment.requireView().findViewById(R.id.reset_topics_button);

        resetTopicsButton.setOnClickListener(
                view -> mTopicsViewModel.resetTopicsButtonClickHandler());
    }
}
