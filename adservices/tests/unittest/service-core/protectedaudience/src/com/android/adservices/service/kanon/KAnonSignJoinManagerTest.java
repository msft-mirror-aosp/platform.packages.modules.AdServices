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

import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_KANON_MESSAGE_TTL_SECONDS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_KANON_PERCENTAGE_IMMEDIATE_SIGN_JOIN_CALLS;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobScheduler;

import androidx.room.Room;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.kanon.DBKAnonMessage;
import com.android.adservices.data.kanon.KAnonDatabase;
import com.android.adservices.data.kanon.KAnonMessageConstants;
import com.android.adservices.data.kanon.KAnonMessageDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.annotations.SetLongFlag;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@SetIntegerFlag(name = KEY_FLEDGE_KANON_PERCENTAGE_IMMEDIATE_SIGN_JOIN_CALLS, value = 100)
@SetLongFlag(name = KEY_FLEDGE_KANON_MESSAGE_TTL_SECONDS, value = 10000)
public final class KAnonSignJoinManagerTest extends AdServicesExtendedMockitoTestCase {
    private KAnonSignJoinManager mKAnonSignJoinManager;
    private KAnonMessageManager mKAnonMessageManager;
    private KAnonMessageDao mKAnonMessageDao;
    // TODO(b/384949821): move to superclass
    private final Flags mFakeFlags = flags.getFlags();
    @Mock private Clock mMockClock;
    @Mock private KAnonCaller mMockKanonCaller;
    @Captor private ArgumentCaptor<List<KAnonMessageEntity>> kanonMessageEntityArgumentCaptor;

    @Captor
    private ArgumentCaptor<KAnonCallerImpl.KAnonCallerSource> kanonCallerSourceArgumentCaptor;

    @Mock private AdServicesLogger mMockAdServicesLogger;
    private KAnonSignJoinBackgroundJobService mKAnonSignJoinBackgroundJobServiceSpy;
    private JobScheduler mJobScheduler;
    private static final int FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB_ID =
            FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB.getJobId();

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

    @Before
    public void setup() {
        mKAnonMessageDao =
                Room.inMemoryDatabaseBuilder(mContext, KAnonDatabase.class)
                        .build()
                        .kAnonMessageDao();
        when(mMockClock.instant()).thenReturn(FIXED_TIME);
        mKAnonMessageManager = new KAnonMessageManager(mKAnonMessageDao, mFakeFlags, mMockClock);
        mJobScheduler = mContext.getSystemService(JobScheduler.class);
        mKAnonSignJoinBackgroundJobServiceSpy = spy(new KAnonSignJoinBackgroundJobService());
    }

    @After
    public void tearDown() {
        mJobScheduler.cancelAll();
    }

    @Test
    @SetIntegerFlag(name = KEY_FLEDGE_KANON_PERCENTAGE_IMMEDIATE_SIGN_JOIN_CALLS, value = 0)
    public void processNewMessage_immediateJoinValueZero_shouldSaveMessageInDatabase() {
        mKAnonSignJoinManager =
                new KAnonSignJoinManager(
                        mContext,
                        mMockKanonCaller,
                        mKAnonMessageManager,
                        mFakeFlags,
                        mMockClock,
                        mMockAdServicesLogger);
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
    @SetIntegerFlag(name = KEY_FLEDGE_KANON_PERCENTAGE_IMMEDIATE_SIGN_JOIN_CALLS, value = 100)
    public void processNewMessage_immediateJoinValueHundred_shouldImmediatelyProcessMessage() {
        mKAnonSignJoinManager =
                new KAnonSignJoinManager(
                        mContext,
                        mMockKanonCaller,
                        mKAnonMessageManager,
                        mFakeFlags,
                        mMockClock,
                        mMockAdServicesLogger);
        KAnonMessageEntity kAnonMessageEntity =
                mKAnonMessageEntityBuilder
                        .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                        .build();
        List<KAnonMessageEntity> newMessages = List.of(kAnonMessageEntity);

        mKAnonSignJoinManager.processNewMessages(newMessages);

        verify(mMockKanonCaller, times(1))
                .signAndJoinMessages(
                        kanonMessageEntityArgumentCaptor.capture(),
                        kanonCallerSourceArgumentCaptor.capture());
        assertThat(kanonMessageEntityArgumentCaptor.getValue().size()).isEqualTo(1);
        assertThat(kanonMessageEntityArgumentCaptor.getValue().get(0).getAdSelectionId())
                .isEqualTo(kAnonMessageEntity.getAdSelectionId());
    }

    @Test
    @SetIntegerFlag(name = KEY_FLEDGE_KANON_PERCENTAGE_IMMEDIATE_SIGN_JOIN_CALLS, value = 100)
    public void processNewMessage_messageSignedWithActiveClientParams_shouldNotProcessMessage() {
        mKAnonSignJoinManager =
                new KAnonSignJoinManager(
                        mContext,
                        mMockKanonCaller,
                        mKAnonMessageManager,
                        mFakeFlags,
                        mMockClock,
                        mMockAdServicesLogger);
        DBKAnonMessage dbkAnonMessageAlreadyJoined =
                mDbkAnonMessageBuilder
                        .setStatus(KAnonMessageConstants.MessageStatus.JOINED)
                        .setKanonHashSet(HASH_SET_1)
                        .setCorrespondingClientParametersExpiryInstant(
                                FIXED_TIME.plusSeconds(10000))
                        .setExpiryInstant(FIXED_TIME.plusSeconds(10000))
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
        verify(mMockKanonCaller, times(0))
                .signAndJoinMessages(
                        newMessages, KAnonCaller.KAnonCallerSource.IMMEDIATE_SIGN_JOIN);
        assertThat(messagesInDB.size()).isEqualTo(1); // new messages was not inserted.
    }

    @Test
    @SetIntegerFlag(name = KEY_FLEDGE_KANON_PERCENTAGE_IMMEDIATE_SIGN_JOIN_CALLS, value = 100)
    public void processNewMessage_messageSignedWithExpiredClientParams_shouldProcessMessage() {
        mKAnonSignJoinManager =
                new KAnonSignJoinManager(
                        mContext,
                        mMockKanonCaller,
                        mKAnonMessageManager,
                        mFakeFlags,
                        mMockClock,
                        mMockAdServicesLogger);
        DBKAnonMessage dbkAnonMessageAlreadyJoined =
                mDbkAnonMessageBuilder
                        .setStatus(KAnonMessageConstants.MessageStatus.JOINED)
                        .setKanonHashSet(HASH_SET_1)
                        .setCorrespondingClientParametersExpiryInstant(
                                FIXED_TIME.minusSeconds(10000))
                        .setExpiryInstant(FIXED_TIME.plusSeconds(10000))
                        .build();
        mKAnonMessageDao.insertKAnonMessage(dbkAnonMessageAlreadyJoined);
        KAnonMessageEntity kAnonMessageEntity =
                mKAnonMessageEntityBuilder
                        .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                        .setHashSet(HASH_SET_1)
                        .build();
        List<KAnonMessageEntity> newMessages = List.of(kAnonMessageEntity);

        mKAnonSignJoinManager.processNewMessages(newMessages);

        verify(mMockKanonCaller, times(1))
                .signAndJoinMessages(
                        kanonMessageEntityArgumentCaptor.capture(),
                        kanonCallerSourceArgumentCaptor.capture());
        assertThat(kanonMessageEntityArgumentCaptor.getValue().size()).isEqualTo(1);
        assertThat(kanonMessageEntityArgumentCaptor.getValue().get(0).getAdSelectionId())
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
                new KAnonSignJoinManager(
                        mContext,
                        mMockKanonCaller,
                        mKAnonMessageManager,
                        mFakeFlags,
                        mMockClock,
                        mMockAdServicesLogger);

        mKAnonSignJoinManager.processMessagesFromDatabase(10);

        verify(mMockKanonCaller, times(1)).signAndJoinMessages(anyList(), any());
    }

    @Test
    @SetIntegerFlag(name = KEY_FLEDGE_KANON_PERCENTAGE_IMMEDIATE_SIGN_JOIN_CALLS, value = 0)
    public void processNewMessage_immediateJoinValueZero_isProcessedByProcessMessageFromDB() {
        mKAnonSignJoinManager =
                new KAnonSignJoinManager(
                        mContext,
                        mMockKanonCaller,
                        mKAnonMessageManager,
                        mFakeFlags,
                        mMockClock,
                        mMockAdServicesLogger);
        KAnonMessageEntity kAnonMessageEntity =
                mKAnonMessageEntityBuilder
                        .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                        .build();

        mKAnonSignJoinManager.processNewMessages(List.of(kAnonMessageEntity));

        verify(mMockKanonCaller, times(0)).signAndJoinMessages(anyList(), any());

        mKAnonSignJoinManager.processMessagesFromDatabase(100);

        verify(mMockKanonCaller, times(1)).signAndJoinMessages(anyList(), any());
    }

    @Test
    @Ignore("b/327172045")
    @MockStatic(FlagsFactory.class)
    public void processNewMessage_noBackgroundJobRunning_schedulesBackgroundService() {
        when(mMockFlags.getFledgeKAnonBackgroundProcessEnabled()).thenReturn(true);
        when(mMockFlags.getFledgeKAnonBackgroundProcessTimePeriodInMs()).thenReturn(100000L);
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

        mKAnonSignJoinManager =
                new KAnonSignJoinManager(
                        mContext,
                        mMockKanonCaller,
                        mKAnonMessageManager,
                        mMockFlags,
                        mMockClock,
                        mMockAdServicesLogger);
            KAnonMessageEntity kAnonMessageEntity =
                    mKAnonMessageEntityBuilder
                            .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                            .build();
            List<KAnonMessageEntity> newMessages = List.of(kAnonMessageEntity);

            mKAnonSignJoinManager.processNewMessages(newMessages);

            assertThat(mJobScheduler.getPendingJob(FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB_ID))
                    .isNotNull();
    }
}
