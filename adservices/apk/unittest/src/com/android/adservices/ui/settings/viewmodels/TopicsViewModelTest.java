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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;

/** Tests for {@link TopicsViewModel}. */
@SpyStatic(FlagsFactory.class)
@SpyStatic(ConsentManager.class)
public final class TopicsViewModelTest extends AdServicesExtendedMockitoTestCase {

    private TopicsViewModel mTopicsViewModel;
    private BlockedTopicsViewModel mBlockedTopicsViewModel;
    @Mock private ConsentManager mConsentManager;
    @Mock private Flags mMockFlags;

    /** Setup needed before every test in this class. */
    @Before
    public void setup() throws IOException {
        doReturn(true).when(mMockFlags).getRecordManualInteractionEnabled();
        mocker.mockGetFlags(mMockFlags);
        mTopicsViewModel =
                new TopicsViewModel(
                        ApplicationProvider.getApplicationContext(), mConsentManager, true);
        mBlockedTopicsViewModel =
                new BlockedTopicsViewModel(
                        ApplicationProvider.getApplicationContext(), mConsentManager);
    }

    /** Test if getTopics returns no topics if {@link ConsentManager} returns no topics. */
    @Test
    public void testGetTopicsReturnsNoTopics() {
        ImmutableList<Topic> topicsList = ImmutableList.of();
        doReturn(topicsList).when(mConsentManager).getKnownTopicsWithConsent();

        mTopicsViewModel =
                new TopicsViewModel(
                        ApplicationProvider.getApplicationContext(), mConsentManager, true);

        assertThat(mTopicsViewModel.getTopics().getValue()).containsExactlyElementsIn(topicsList);
    }

    /**
     * Test if getTopics returns correct topics if {@link ConsentManager} returns correct topics.
     */
    @Test
    public void testGetTopicsReturnsTopics() throws IOException {
        Topic topic1 = Topic.create(1, 1, 1);
        Topic topic2 = Topic.create(2, 1, 1);
        ImmutableList<Topic> topicsList = ImmutableList.copyOf(new Topic[] {topic1, topic2});
        doReturn(topicsList).when(mConsentManager).getKnownTopicsWithConsent();

        mTopicsViewModel =
                new TopicsViewModel(
                        ApplicationProvider.getApplicationContext(), mConsentManager, true);

        assertThat(mTopicsViewModel.getTopics().getValue()).containsExactlyElementsIn(topicsList);
    }

    /** Test if revokeTopicConsent blocks a topic with a call to {@link ConsentManager}. */
    @Test
    public void testBlockTopic() {
        Topic topic1 = Topic.create(1, 1, 1);
        mTopicsViewModel.revokeTopicConsent(topic1);

        verify(mConsentManager).revokeConsentForTopic(topic1);
    }

    /** Test if restoreTopicConsent blocks a topic with a call to {@link ConsentManager}. */
    @Test
    public void testRestoreTopic() {
        Topic topic1 = Topic.create(1, 1, 1);
        mBlockedTopicsViewModel.restoreTopicConsent(topic1);

        verify(mConsentManager).restoreConsentForTopic(topic1);
    }
}
