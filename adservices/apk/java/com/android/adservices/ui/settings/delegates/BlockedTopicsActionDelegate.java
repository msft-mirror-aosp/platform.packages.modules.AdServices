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

import android.util.Log;
import android.util.Pair;

import androidx.lifecycle.Observer;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.PhFlags;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.BlockedTopicsActivity;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsBlockedTopicsFragment;
import com.android.adservices.ui.settings.viewmodels.BlockedTopicsViewModel;
import com.android.adservices.ui.settings.viewmodels.BlockedTopicsViewModel.BlockedTopicsViewModelUiEvent;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
public class BlockedTopicsActionDelegate extends BaseActionDelegate {
    private final BlockedTopicsActivity mBlockedTopicsActivity;
    private final BlockedTopicsViewModel mBlockedTopicsViewModel;

    public BlockedTopicsActionDelegate(
            BlockedTopicsActivity blockedTopicsActivity,
            BlockedTopicsViewModel blockedTopicsViewModel) {
        super(blockedTopicsActivity);
        mBlockedTopicsActivity = blockedTopicsActivity;
        mBlockedTopicsViewModel = blockedTopicsViewModel;
        listenToBlockedTopicsViewModelUiEvents();
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
                            logUIAction(ActionEnum.UNBLOCK_TOPIC_SELECTED);
                            mBlockedTopicsViewModel.restoreTopicConsent(topic);
                            if (PhFlags.getInstance().getUIDialogsFeatureEnabled()) {
                                DialogManager.showUnblockTopicDialog(mBlockedTopicsActivity, topic);
                            }
                        } else {
                            Log.e("AdservicesUI", "Unknown Action for UI Logging");
                        }
                    } finally {
                        mBlockedTopicsViewModel.uiEventHandled();
                    }
                };
        mBlockedTopicsViewModel.getUiEvents().observe(mBlockedTopicsActivity, observer);
    }

    /**
     * Configure all UI elements (except blocked topics list) in {@link
     * AdServicesSettingsBlockedTopicsFragment} to handle user actions.
     */
    public void initBlockedTopicsFragment() {
        mBlockedTopicsActivity.setTitle(R.string.settingsUI_blocked_topics_title);
    }
}
