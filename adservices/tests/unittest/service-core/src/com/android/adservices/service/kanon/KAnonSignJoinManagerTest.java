/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.kanon;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.kanon.DBKAnonMessage;
import com.android.adservices.data.kanon.KAnonDatabase;
import com.android.adservices.data.kanon.KAnonMessageConstants;
import com.android.adservices.data.kanon.KAnonMessageDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class KAnonSignJoinManagerTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private KAnonSignJoinManager mKAnonSignJoinManager;
    private KAnonMessageManager mKAnonMessageManager;
    private KAnonMessageDao mKAnonMessageDao;
    private Flags mFlags;
    @Mock private Clock mockClock;
    @Mock private KAnonCaller mockKanonCaller;
    @Captor private ArgumentCaptor<List<KAnonMessageEntity>> argumentCaptor;

    private final Instant FIXED_TIME = Instant.now();
    private static final long AD_SELECTION_ID_1 = 1;
    private static final String HASH_SET_1 = "somestring";
    private final KAnonMessageEntity.Builder mKAnonMessageEntityBuilder =
            KAnonMessageEntity.builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setHashSet(HASH_SET_1)
                    .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
    private final DBKAnonMessage.Builder mDbkAnonMessageBuilder =
            DBKAnonMessage.builder()
                    .setKanonHashSet(HASH_SET_1)
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setCorrespondingClientParametersExpiryInstant(FIXED_TIME)
                    .setExpiryInstant(FIXED_TIME)
                    .setCreatedAt(FIXED_TIME)
                    .setStatus(KAnonMessageConstants.MessageStatus.JOINED);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mKAnonMessageDao =
                Room.inMemoryDatabaseBuilder(mContext, KAnonDatabase.class)
                        .build()
                        .kAnonMessageDao();
        when(mockClock.instant()).thenReturn(FIXED_TIME);
        mFlags = FlagsFactory.getFlags();
        mKAnonMessageManager = new KAnonMessageManager(mKAnonMessageDao, mFlags, mockClock);
    }

    @Test
    public void processNewMessage_immediateJoinValueZero_shouldSaveMessageInDatabase() {
        Flags testFlags = new KanonSignJoinManagerTestFlags(0);
        mKAnonSignJoinManager =
                new KAnonSignJoinManager(
                        mockKanonCaller, mKAnonMessageManager, testFlags, mockClock);
        KAnonMessageEntity kAnonMessageEntity =
                mKAnonMessageEntityBuilder
                        .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                        .build();
        List<KAnonMessageEntity> newMessages = List.of(kAnonMessageEntity);

        mKAnonSignJoinManager.processNewMessages(newMessages);

        List<KAnonMessageEntity> fetchedKAnonEntities =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageConstants.MessageStatus.NOT_PROCESSED);
        assertThat(fetchedKAnonEntities.size()).isEqualTo(1);
        assertThat(fetchedKAnonEntities.get(0).getAdSelectionId())
                .isEqualTo(kAnonMessageEntity.getAdSelectionId());
    }

    @Test
    public void processNewMessage_immediateJoinValueHundred_shouldImmediatelyProcessMessage() {
        Flags testFlags = new KanonSignJoinManagerTestFlags(100);
        mKAnonSignJoinManager =
                new KAnonSignJoinManager(
                        mockKanonCaller, mKAnonMessageManager, testFlags, mockClock);
        KAnonMessageEntity kAnonMessageEntity =
                mKAnonMessageEntityBuilder
                        .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                        .build();
        List<KAnonMessageEntity> newMessages = List.of(kAnonMessageEntity);

        mKAnonSignJoinManager.processNewMessages(newMessages);

        verify(mockKanonCaller, times(1)).signAndJoinMessages(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().size()).isEqualTo(1);
        assertThat(argumentCaptor.getValue().get(0).getAdSelectionId())
                .isEqualTo(kAnonMessageEntity.getAdSelectionId());
    }

    @Test
    public void processNewMessage_messageSignedWithActiveClientParams_shouldNotProcessMessage() {
        Flags testFlags = new KanonSignJoinManagerTestFlags(100);
        mKAnonSignJoinManager =
                new KAnonSignJoinManager(
                        mockKanonCaller, mKAnonMessageManager, testFlags, mockClock);
        DBKAnonMessage dbkAnonMessageAlreadyJoined =
                mDbkAnonMessageBuilder
                        .setStatus(KAnonMessageConstants.MessageStatus.JOINED)
                        .setKanonHashSet(HASH_SET_1)
                        .setCorrespondingClientParametersExpiryInstant(
                                FIXED_TIME.plusSeconds(10000))
                        .build();
        mKAnonMessageDao.insertKAnonMessage(dbkAnonMessageAlreadyJoined);
        KAnonMessageEntity kAnonMessageEntity =
                mKAnonMessageEntityBuilder
                        .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                        .setHashSet(HASH_SET_1)
                        .build();
        List<KAnonMessageEntity> newMessages = List.of(kAnonMessageEntity);

        mKAnonSignJoinManager.processNewMessages(newMessages);

        List<KAnonMessageEntity> messagesInDB =
                mKAnonMessageManager.fetchKAnonMessageEntityWithMessage(HASH_SET_1);
        verify(mockKanonCaller, times(0)).signAndJoinMessages(newMessages);
        assertThat(messagesInDB.size()).isEqualTo(1); // new messages was not inserted.
    }

    @Test
    public void processNewMessage_messageSignedWithExpiredClientParams_shouldProcessMessage() {
        Flags testFlags = new KanonSignJoinManagerTestFlags(100);
        mKAnonSignJoinManager =
                new KAnonSignJoinManager(
                        mockKanonCaller, mKAnonMessageManager, testFlags, mockClock);
        DBKAnonMessage dbkAnonMessageAlreadyJoined =
                mDbkAnonMessageBuilder
                        .setStatus(KAnonMessageConstants.MessageStatus.JOINED)
                        .setKanonHashSet(HASH_SET_1)
                        .setCorrespondingClientParametersExpiryInstant(
                                FIXED_TIME.minusSeconds(10000))
                        .build();
        mKAnonMessageDao.insertKAnonMessage(dbkAnonMessageAlreadyJoined);
        KAnonMessageEntity kAnonMessageEntity =
                mKAnonMessageEntityBuilder
                        .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                        .setHashSet(HASH_SET_1)
                        .build();
        List<KAnonMessageEntity> newMessages = List.of(kAnonMessageEntity);

        mKAnonSignJoinManager.processNewMessages(newMessages);

        verify(mockKanonCaller, times(1)).signAndJoinMessages(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().size()).isEqualTo(1);
        assertThat(argumentCaptor.getValue().get(0).getAdSelectionId())
                .isEqualTo(kAnonMessageEntity.getAdSelectionId());
    }

    @Test
    public void processMessagesFromDatabase_shouldFetchAndProcessMessages() {
        KAnonMessageEntity kAnonMessageEntity =
                mKAnonMessageEntityBuilder
                        .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                        .build();
        mKAnonMessageManager.persistNewAnonMessageEntities(List.of(kAnonMessageEntity));
        mKAnonSignJoinManager =
                new KAnonSignJoinManager(mockKanonCaller, mKAnonMessageManager, mFlags, mockClock);

        mKAnonSignJoinManager.processMessagesFromDatabase(10);

        verify(mockKanonCaller, times(1)).signAndJoinMessages(anyList());
    }

    @Test
    public void processNewMessage_immediateJoinValueZero_isProcessedByProcessMessageFromDB() {
        Flags testFlags = new KanonSignJoinManagerTestFlags(0);
        mKAnonSignJoinManager =
                new KAnonSignJoinManager(
                        mockKanonCaller, mKAnonMessageManager, testFlags, mockClock);
        KAnonMessageEntity kAnonMessageEntity =
                mKAnonMessageEntityBuilder
                        .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                        .build();

        mKAnonSignJoinManager.processNewMessages(List.of(kAnonMessageEntity));

        verify(mockKanonCaller, times(0)).signAndJoinMessages(anyList());

        mKAnonSignJoinManager.processMessagesFromDatabase(100);

        verify(mockKanonCaller, times(1)).signAndJoinMessages(anyList());
    }

    private static class KanonSignJoinManagerTestFlags implements Flags {
        private final int percentageImmediateSignJoinCalls;

        private KanonSignJoinManagerTestFlags(int percentageImmediateSignJoinCalls) {
            this.percentageImmediateSignJoinCalls = percentageImmediateSignJoinCalls;
        }

        @Override
        public int getFledgeKAnonPercentageImmediateSignJoinCalls() {
            return percentageImmediateSignJoinCalls;
        }
    }
}
