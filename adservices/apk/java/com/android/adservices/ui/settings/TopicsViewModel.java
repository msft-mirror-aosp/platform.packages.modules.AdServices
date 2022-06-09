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

import android.app.Application;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.consent.ConsentManager;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * View model for the topics view of the AdServices Settings App. This view model is responsible for
 * serving topics to the topics view, and interacting with the {@link ConsentManager} that persists
 * and changes the topics data in a storage.
 */
public class TopicsViewModel extends AndroidViewModel {

    private final MutableLiveData<Pair<TopicsViewModelUiEvent, Topic>> mEventTrigger =
            new MutableLiveData<>();
    private MutableLiveData<ImmutableList<Topic>> mTopics;
    private ConsentManager mConsentManager;

    public TopicsViewModel(@NonNull Application application) throws IOException {
        super(application);
        setConsentManager(ConsentManager.getInstance(application));
    }

    /**
     * Provides the topics displayed in {@link AdServicesSettingsTopicsFragment}.
     *
     * @return {@link mTopics} a list of topics that represents the user's interests.
     */
    public MutableLiveData<ImmutableList<Topic>> getTopics() {
        if (mTopics == null) {
            mTopics = getTopicsFromConsentManager();
        }
        return mTopics;
    }

    /**
     * Revoke the consent for the specified topic (i.e. block the topic).
     *
     * @param topic the topic to be blocked.
     */
    public void revokeTopicConsent(Topic topic) {
        if (mTopics == null) {
            mTopics = getTopicsFromConsentManager();
        }
        // TODO(b/232417846): mConsentManager.revokeConsentForTopic(topic);
        ImmutableList<Topic> newTopics = getTopicsFromConsentManager().getValue();
        mTopics.postValue(newTopics);
    }

    /** reset all information related to topics. */
    public void resetTopics() {
        mTopics.postValue(ImmutableList.of());
        // TODO(b/232417846): reset all topics through ConsentManager.
    }

    // ---------------------------------------------------------------------------------------------
    // BEGIN Action handlers
    // ---------------------------------------------------------------------------------------------

    /** Returns an observable but immutable event enum representing an view action on UI. */
    public LiveData<Pair<TopicsViewModelUiEvent, Topic>> getUiEvents() {
        return mEventTrigger;
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

    /** Triggers {@link AdServicesSettingsTopicsFragment}. */
    public void blockedTopicsFragmentButtonClickHandler() {
        mEventTrigger.postValue(
                new Pair<>(TopicsViewModelUiEvent.DISPLAY_BLOCKED_TOPICS_FRAGMENT, null));
    }

    /** Triggers a reset of all topics related data. */
    public void resetTopicsButtonClickHandler() {
        mEventTrigger.postValue(new Pair<>(TopicsViewModelUiEvent.RESET_TOPICS, null));
    }

    // ---------------------------------------------------------------------------------------------
    // END Action handlers
    // ---------------------------------------------------------------------------------------------

    @VisibleForTesting
    void setConsentManager(ConsentManager consentManager) {
        mConsentManager = consentManager;
    }

    private MutableLiveData<ImmutableList<Topic>> getTopicsFromConsentManager() {
        // TODO(b/232417846): return new
        // MutableLiveData<>(mConsentManager.getKnownTopicsWithConsent());
        List<Topic> tempList = new ArrayList<>();
        tempList.add(Topic.create(1, 1, 1));
        tempList.add(Topic.create(2, 1, 1));
        tempList.add(Topic.create(4, 1, 1));
        ImmutableList<Topic> immutableTempList = ImmutableList.copyOf(tempList);
        return new MutableLiveData<>(immutableTempList);
    }

    /** UI event triggered by view model */
    public enum TopicsViewModelUiEvent {
        BLOCK_TOPIC,
        DISPLAY_BLOCKED_TOPICS_FRAGMENT,
        RESET_TOPICS,
    }
}
