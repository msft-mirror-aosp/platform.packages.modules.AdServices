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
package com.android.adservices.ui.settings.viewmodels;

import android.app.Application;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsBlockedTopicsFragment;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsTopicsFragment;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * View model for the topics view and blocked topics view of the AdServices Settings App. This view
 * model is responsible for serving topics to the topics view and blocked topics view, and
 * interacting with the {@link ConsentManager} that persists and changes the topics data in a
 * storage.
 */
public class TopicsViewModel extends AndroidViewModel {

    private final MutableLiveData<Pair<TopicsViewModelUiEvent, Topic>> mEventTrigger =
            new MutableLiveData<>();
    private final MutableLiveData<ImmutableList<Topic>> mTopics;
    private final MutableLiveData<ImmutableList<Topic>> mBlockedTopics;
    private final ConsentManager mConsentManager;

    /** UI event triggered by view model */
    public enum TopicsViewModelUiEvent {
        BLOCK_TOPIC,
        RESTORE_TOPIC,
        RESET_TOPICS,
        DISPLAY_BLOCKED_TOPICS_FRAGMENT,
    }

    public TopicsViewModel(@NonNull Application application) {
        super(application);

        mConsentManager = ConsentManager.getInstance(application);
        mTopics = new MutableLiveData<>(getTopicsFromConsentManager());
        mBlockedTopics = new MutableLiveData<>(getBlockedTopicsFromConsentManager());
    }

    @VisibleForTesting
    public TopicsViewModel(@NonNull Application application, ConsentManager consentManager) {
        super(application);

        mConsentManager = consentManager;
        mTopics = new MutableLiveData<>(getTopicsFromConsentManager());
        mBlockedTopics = new MutableLiveData<>(getBlockedTopicsFromConsentManager());
    }

    // ---------------------------------------------------------------------------------------------
    // Topics View
    // ---------------------------------------------------------------------------------------------

    /**
     * Provides the topics displayed in {@link AdServicesSettingsTopicsFragment}.
     *
     * @return {@link mTopics} a list of topics that represents the user's interests.
     */
    public LiveData<ImmutableList<Topic>> getTopics() {
        return mTopics;
    }

    /**
     * Revoke the consent for the specified topic (i.e. block the topic).
     *
     * @param topic the topic to be blocked.
     */
    public void revokeTopicConsent(Topic topic) {
        mConsentManager.revokeConsentForTopic(topic);
        refresh();
    }

    /**
     * Reads all the data from {@link ConsentManager}.
     *
     * <p>TODO(b/238387560): To be moved to private when is fixed.
     */
    public void refresh() {
        mTopics.postValue(getTopicsFromConsentManager());
        mBlockedTopics.postValue(getBlockedTopicsFromConsentManager());
    }

    /** reset all information related to topics. */
    public void resetTopics() {
        // TODO(b/232417846): reset all topics through ConsentManager.
        mTopics.postValue(getTopicsFromConsentManager());
    }

    // ---------------------------------------------------------------------------------------------
    // Blocked Topics View
    // ---------------------------------------------------------------------------------------------

    /**
     * Provides the blocked topics displayed in {@link AdServicesSettingsBlockedTopicsFragment}.
     *
     * @return {@link mBlockedTopics} a list of topics that represents the user's blocked interests.
     */
    public LiveData<ImmutableList<Topic>> getBlockedTopics() {
        return mBlockedTopics;
    }

    /**
     * Restore the consent for the specified topic (i.e. unblock the topic).
     *
     * @param topic the topic to be restored.
     */
    public void restoreTopicConsent(Topic topic) {
        mConsentManager.restoreConsentForTopic(topic);
        refresh();
    }

    // ---------------------------------------------------------------------------------------------
    // Action Handlers
    // ---------------------------------------------------------------------------------------------

    /** Returns an observable but immutable event enum representing an view action on UI. */
    public LiveData<Pair<TopicsViewModelUiEvent, Topic>> getUiEvents() {
        return mEventTrigger;
    }

    /**
     * Sets the UI Event as handled so the action will not be handled again if activity is
     * recreated.
     */
    public void uiEventHandled() {
        mEventTrigger.postValue(new Pair<>(null, null));
    }

    /**
     * Triggers the block of the specified topic in the list of topics in {@link
     * AdServicesSettingsTopicsFragment}.
     *
     * @param topic the topic to be blocked.
     */
    public void revokeTopicConsentButtonClickHandler(Topic topic) {
        mEventTrigger.postValue(new Pair<>(TopicsViewModelUiEvent.BLOCK_TOPIC, topic));
    }

    /**
     * Triggers the block of the specified topic in the list of topics in {@link
     * AdServicesSettingsTopicsFragment}.
     *
     * @param topic the topic to be blocked.
     */
    public void restoreTopicConsentButtonClickHandler(Topic topic) {
        mEventTrigger.postValue(new Pair<>(TopicsViewModelUiEvent.RESTORE_TOPIC, topic));
    }

    /** Triggers a reset of all topics related data. */
    public void resetTopicsButtonClickHandler() {
        mEventTrigger.postValue(new Pair<>(TopicsViewModelUiEvent.RESET_TOPICS, null));
    }

    /** Triggers {@link AdServicesSettingsTopicsFragment}. */
    public void blockedTopicsFragmentButtonClickHandler() {
        mEventTrigger.postValue(
                new Pair<>(TopicsViewModelUiEvent.DISPLAY_BLOCKED_TOPICS_FRAGMENT, null));
    }

    // ---------------------------------------------------------------------------------------------
    // Private Methods
    // ---------------------------------------------------------------------------------------------

    private ImmutableList<Topic> getTopicsFromConsentManager() {
        return mConsentManager.getKnownTopicsWithConsent();
    }

    private ImmutableList<Topic> getBlockedTopicsFromConsentManager() {
        return mConsentManager.getTopicsWithRevokedConsent();
    }
}
