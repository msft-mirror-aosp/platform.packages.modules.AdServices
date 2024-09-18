/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.data.common;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.shared.util.Clock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.UUID;

public final class UserProfileIdDaoSharedPreferencesImplTest extends AdServicesMockitoTestCase {
    private static final String STORAGE_NAME = "test_storage";
    private static final long TIME_INITIAL_MS = 1000;

    private SharedPreferences mSharedPreferences;
    private UserProfileIdDao mUserProfileIdDao;
    @Mock private Clock mClock;

    @Before
    public void setup() {
        mSharedPreferences = mContext.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE);
        mUserProfileIdDao = new UserProfileIdDaoSharedPreferencesImpl(mSharedPreferences, mClock);
        when(mClock.currentTimeMillis()).thenReturn(TIME_INITIAL_MS);
    }

    @After
    public void teardown() {
        mContext.deleteSharedPreferences(STORAGE_NAME);
    }

    @Test
    public void testSetAndReadId() {
        UUID uuid = UUID.randomUUID();

        assertThat(mUserProfileIdDao.getUserProfileId()).isNull();
        mUserProfileIdDao.setUserProfileId(uuid);
        assertThat(
                        mSharedPreferences.getString(
                                UserProfileIdDaoSharedPreferencesImpl.USER_PROFILE_ID_KEY, null))
                .isEqualTo(uuid.toString());
        assertThat(mUserProfileIdDao.getUserProfileId()).isEqualTo(uuid);
        assertThat(mUserProfileIdDao.getTimestamp()).isEqualTo(TIME_INITIAL_MS);
    }

    @Test
    public void testSetId_idExist_overrideExistingId() {
        UUID uuid1 = UUID.randomUUID();

        assertThat(mUserProfileIdDao.getUserProfileId()).isNull();
        mUserProfileIdDao.setUserProfileId(uuid1);
        when(mClock.currentTimeMillis()).thenReturn(TIME_INITIAL_MS);
        assertThat(
                        mSharedPreferences.getString(
                                UserProfileIdDaoSharedPreferencesImpl.USER_PROFILE_ID_KEY, null))
                .isEqualTo(uuid1.toString());
        assertThat(mUserProfileIdDao.getUserProfileId()).isEqualTo(uuid1);
        assertThat(mUserProfileIdDao.getTimestamp()).isEqualTo(TIME_INITIAL_MS);

        UUID uuid2 = UUID.randomUUID();
        when(mClock.currentTimeMillis()).thenReturn(TIME_INITIAL_MS + 100);
        mUserProfileIdDao.setUserProfileId(uuid2);
        assertThat(
                        mSharedPreferences.getString(
                                UserProfileIdDaoSharedPreferencesImpl.USER_PROFILE_ID_KEY, null))
                .isEqualTo(uuid2.toString());
        assertThat(mUserProfileIdDao.getUserProfileId()).isEqualTo(uuid2);
        assertThat(mUserProfileIdDao.getTimestamp()).isEqualTo(TIME_INITIAL_MS + 100);
    }

    @Test
    public void testDeleteStorage() {
        UUID uuid = UUID.randomUUID();

        assertThat(mUserProfileIdDao.getUserProfileId()).isNull();
        mUserProfileIdDao.setUserProfileId(uuid);
        assertThat(
                        mSharedPreferences.getString(
                                UserProfileIdDaoSharedPreferencesImpl.USER_PROFILE_ID_KEY, null))
                .isEqualTo(uuid.toString());
        assertThat(mUserProfileIdDao.getUserProfileId()).isEqualTo(uuid);

        mUserProfileIdDao.deleteStorage();
        assertThat(mUserProfileIdDao.getUserProfileId()).isNull();
        assertThat(
                        mSharedPreferences.getString(
                                UserProfileIdDaoSharedPreferencesImpl.USER_PROFILE_ID_KEY, null))
                .isNull();
        assertThat(mUserProfileIdDao.getTimestamp()).isEqualTo(0);
        assertThat(
                        mSharedPreferences.getLong(
                                UserProfileIdDaoSharedPreferencesImpl
                                        .USER_PROFILE_ID_CREATION_TIMESTAMP_KEY,
                                0))
                .isEqualTo(0);
    }
}
