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

import static com.android.adservices.ui.settings.expressivefragments.BlockedTopicsActivityFragment.BLOCKED_TOPICS_LIST;
import static com.android.adservices.ui.settings.expressivefragments.BlockedTopicsActivityFragment.BLOCKED_TOPICS_NO_BLOCKED_TOPICS_MESSAGE;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.service.topics.TopicsMapper;
import com.android.adservices.ui.settings.DialogFragmentManager;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.BlockedTopicsActivity;
import com.android.adservices.ui.settings.expressivefragments.BlockedTopicsActivityFragment;
import com.android.adservices.ui.settings.preferences.AdservicesTwoTargetPreference;
import com.android.adservices.ui.settings.viewmodels.BlockedTopicsViewModel;
import com.android.adservices.ui.settings.viewmodels.BlockedTopicsViewModel.BlockedTopicsViewModelUiEvent;

import com.google.common.collect.ImmutableList;

import java.util.Objects;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class BlockedTopicsActivityFragmentActionDelegate extends BaseActionDelegate {
    private final BlockedTopicsViewModel mBlockedTopicsViewModel;
    private final BlockedTopicsActivityFragment mFragment;

    public BlockedTopicsActivityFragmentActionDelegate(
            BlockedTopicsActivity blockedTopicsActivity,
            BlockedTopicsViewModel blockedTopicsViewModel,
            BlockedTopicsActivityFragment fragment) {
        super(blockedTopicsActivity);
        this.mBlockedTopicsViewModel = blockedTopicsViewModel;
        this.mFragment = fragment;
        listenToBlockedTopicsViewModelUiEvents();
    }

    @Override
    public void initGA() {
        mActivity.setTitle(R.string.settingsUI_blocked_topics_ga_title);
        configureSharedElements();
    }

    @Override
    public void initU18() {}

    @Override
    public void initGaUxWithPas() {
        initGA();
    }

    private void configureSharedElements() {
        // no blocked topics message
        setUpTopicsListObserve();
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
        Preference noBlockedTopicsGaMessage =
                (Preference)
                        Objects.requireNonNull(
                                mFragment.findPreference(BLOCKED_TOPICS_NO_BLOCKED_TOPICS_MESSAGE));
        PreferenceCategory topics_category =
                (PreferenceCategory)
                        Objects.requireNonNull(mFragment.findPreference(BLOCKED_TOPICS_LIST));

        Observer<ImmutableList<Topic>> blockedTopicsObserver =
                list -> {
                    topics_category.removeAll();
                    if (list.isEmpty()) {
                        noBlockedTopicsGaMessage.setVisible(true);
                    } else {
                        noBlockedTopicsGaMessage.setVisible(false);
                        for (Topic topic : list) {
                            topics_category.addPreference(getTopicPreference(topic));
                        }
                    }
                };
        mBlockedTopicsViewModel.getBlockedTopics().observe(mActivity, blockedTopicsObserver);
    }

    private AdservicesTwoTargetPreference getTopicPreference(Topic topic) {
        AdservicesTwoTargetPreference twoTargetPreference =
                new AdservicesTwoTargetPreference(
                        mActivity.getApplicationContext(),
                        mActivity
                                .getResources()
                                .getString(R.string.settingsUI_unblock_topic_title));
        twoTargetPreference.setOnClickListener(
                (v) -> mBlockedTopicsViewModel.restoreTopicConsentButtonClickHandler(topic));
        twoTargetPreference.setTitle(
                mActivity
                        .getResources()
                        .getString(getTopicsTitleResId(topic, mActivity.getApplicationContext())));
        return twoTargetPreference;
    }

    private void listenToBlockedTopicsViewModelUiEvents() {
        Observer<Pair<BlockedTopicsViewModelUiEvent, Topic>> observer =
                eventTopicPair -> {
                    if (eventTopicPair == null) {
                        return;
                    }
                    BlockedTopicsViewModelUiEvent event = eventTopicPair.first;
                    Topic topic = eventTopicPair.second;
                    if (event == null) {
                        return;
                    }
                    try {
                        if (event == BlockedTopicsViewModelUiEvent.RESTORE_TOPIC) {
                            UiStatsLogger.logUnblockTopicSelected();
                            mBlockedTopicsViewModel.restoreTopicConsent(topic);
                            if (FlagsFactory.getFlags().getUiDialogsFeatureEnabled()) {
                                if (FlagsFactory.getFlags().getUiDialogFragmentEnabled()) {
                                    DialogFragmentManager.showUnblockTopicDialog(mActivity, topic);
                                } else {
                                    DialogManager.showUnblockTopicDialog(mActivity, topic);
                                }
                            }
                        } else {
                            Log.e("AdservicesUI", "Unknown Action for UI Logging");
                        }
                    } finally {
                        mBlockedTopicsViewModel.uiEventHandled();
                    }
                };
        mBlockedTopicsViewModel.getUiEvents().observe(mActivity, observer);
    }
}
