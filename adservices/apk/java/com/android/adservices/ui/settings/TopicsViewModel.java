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
    private final MutableLiveData<TopicsViewModelUiEvent> mEventTrigger = new MutableLiveData<>();
    private MutableLiveData<ImmutableList<Topic>> mTopics;
    private ConsentManager mConsentManager;

    /** UI event triggered by view model */
    public enum TopicsViewModelUiEvent {
        DISPLAY_BLOCKED_TOPICS_FRAGMENT
    }

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
    }

    /** Returns an observable but immutable event enum representing an view action on UI. */
    public LiveData<TopicsViewModelUiEvent> getUiEvents() {
        return mEventTrigger;
    }

    /** Triggers {@link AdServicesSettingsTopicsFragment}. */
    public void revokeTopicConsentButtonClickHandler() {
        mEventTrigger.postValue(TopicsViewModelUiEvent.DISPLAY_BLOCKED_TOPICS_FRAGMENT);
    }

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
}
