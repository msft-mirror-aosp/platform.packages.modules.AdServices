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

import android.annotation.NonNull;

import com.android.adservices.data.kanon.DBKAnonMessage;
import com.android.adservices.data.kanon.KAnonMessageConstants;
import com.android.adservices.data.kanon.KAnonMessageDao;
import com.android.adservices.service.Flags;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Class to manage fetching and persisting of {@link KAnonMessageEntity}. */
public class KAnonMessageManager {

    @NonNull private final KAnonMessageDao mKAnonMessageDao;
    @NonNull private final Flags mFlags;
    @NonNull private final Clock mClock;

    public KAnonMessageManager(
            @NonNull KAnonMessageDao kAnonMessageDao, @NonNull Flags flags, @NonNull Clock clock) {
        Objects.requireNonNull(kAnonMessageDao);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(clock);

        mKAnonMessageDao = kAnonMessageDao;
        mFlags = flags;
        mClock = clock;
    }

    /**
     * This method is used to persist NEW {@link KAnonMessageEntity} in the {@link DBKAnonMessage}
     * table.
     */
    public void persistNewAnonMessageEntities(List<KAnonMessageEntity> kAnonMessageEntityList) {
        List<DBKAnonMessage> dbkAnonMessages =
                kAnonMessageEntityList.stream()
                        .map(this::parseKAnonMessageEntityToNewDBKAnonMessage)
                        .collect(Collectors.toList());
        mKAnonMessageDao.insertAllKAnonMessages(dbkAnonMessages);
    }

    /**
     * This method fetches a list of {@link DBKAnonMessage} from the database, parses it and returns
     * a list of {@link KAnonMessageEntity}.
     *
     * @param numberOfMessages number of messages to be fetched.
     * @param status status of the messages that needed to be fetched
     */
    public List<KAnonMessageEntity> fetchNKAnonMessagesWithStatus(
            int numberOfMessages, @KAnonMessageConstants.MessageStatus int status) {
        return mKAnonMessageDao.getNLatestKAnonMessagesWithStatus(numberOfMessages, status).stream()
                .map(this::parseDBKAnonMessageToKAnonMessageEntity)
                .collect(Collectors.toList());
    }

    /**
     * @param hashSetToSearch Fetches the {@link DBKAnonMessage} matching the searchText and parses
     *     it to a {@link KAnonMessageEntity}
     */
    public List<KAnonMessageEntity> fetchKAnonMessageEntityWithMessage(String hashSetToSearch) {
        return mKAnonMessageDao.getKAnonMessagesWithMessage(hashSetToSearch).stream()
                .map(this::parseDBKAnonMessageToKAnonMessageEntity)
                .collect(Collectors.toList());
    }

    private DBKAnonMessage parseKAnonMessageEntityToNewDBKAnonMessage(
            KAnonMessageEntity kAnonMessageEntity) {
        if (kAnonMessageEntity == null) {
            return null;
        }
        // TODO(b/321942045) Calculate expiry instant by picking up values from flag.
        return DBKAnonMessage.builder()
                .setAdSelectionId(kAnonMessageEntity.getAdSelectionId())
                .setKanonHashSet(kAnonMessageEntity.getHashSet())
                .setStatus(
                        KAnonMessageConstants.fromKAnonMessageEntityStatus(
                                kAnonMessageEntity.getStatus()))
                .setExpiryInstant(mClock.instant())
                .setCreatedAt(mClock.instant())
                .build();
    }

    private KAnonMessageEntity parseDBKAnonMessageToKAnonMessageEntity(
            DBKAnonMessage dbkAnonMessage) {
        if (dbkAnonMessage == null) {
            return null;
        }
        return KAnonMessageEntity.builder()
                .setMessageId(dbkAnonMessage.getMessageId())
                .setHashSet(dbkAnonMessage.getKanonHashSet())
                .setAdSelectionId(dbkAnonMessage.getAdSelectionId())
                .setStatus(
                        KAnonMessageConstants.toKAnonMessageEntityStatus(
                                dbkAnonMessage.getStatus()))
                .setCorrespondingClientParametersExpiryInstant(
                        dbkAnonMessage.getCorrespondingClientParametersExpiryInstant())
                .build();
    }
}
