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

package com.android.adservices.service.customaudience;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.exceptions.AdServicesException;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;

public class CustomAudienceServiceEndToEndTest {
    protected static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private static final CustomAudience CUSTOM_AUDIENCE_PK1_1 =
            CustomAudienceFixture.getValidBuilder().build();

    private static final CustomAudience CUSTOM_AUDIENCE_PK1_2 =
            CustomAudienceFixture.getValidBuilder()
                    .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME)
                    .build();

    private static final CustomAudience CUSTOM_AUDIENCE_PK1_BEYOND_MAX_EXPIRATION_TIME =
            CustomAudienceFixture.getValidBuilder()
                    .setExpirationTime(CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME)
                    .build();

    private static final DBCustomAudience DB_CUSTOM_AUDIENCE_PK1_1 =
            DBCustomAudienceFixture.getValidBuilder().build();

    private static final DBCustomAudience DB_CUSTOM_AUDIENCE_PK1_2 =
            DBCustomAudienceFixture.getValidBuilder()
                    .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME)
                    .build();

    private CustomAudienceDao mCustomAudienceDao;

    private CustomAudienceServiceImpl mService;

    @Before
    public void setup() {
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CustomAudienceDatabase.class).build()
                        .customAudienceDao();
        mService =
                new CustomAudienceServiceImpl(CONTEXT,
                        new CustomAudienceImpl(mCustomAudienceDao,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI),
                        MoreExecutors.directExecutor());
    }

    @Test
    public void testJoinCustomAudience_joinTwice_secondJoinOverrideValues() {
        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(CUSTOM_AUDIENCE_PK1_1, callback);
        assertTrue(callback.isSuccess());
        assertEquals(DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(CustomAudienceFixture.VALID_OWNER,
                        CustomAudienceFixture.VALID_BUYER, CustomAudienceFixture.VALID_NAME));

        callback = new ResultCapturingCallback();
        mService.joinCustomAudience(CUSTOM_AUDIENCE_PK1_2, callback);
        assertTrue(callback.isSuccess());
        assertEquals(DB_CUSTOM_AUDIENCE_PK1_2, mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                CustomAudienceFixture.VALID_OWNER, CustomAudienceFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME));
    }

    @Test
    public void testJoinCustomAudience_beyondMaxExpirationTime_fail() {
        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(CUSTOM_AUDIENCE_PK1_BEYOND_MAX_EXPIRATION_TIME, callback);
        assertFalse(callback.isSuccess());
        assertTrue(callback.getException() instanceof AdServicesException);
        assertTrue(callback.getException().getCause() instanceof IllegalArgumentException);
        assertNull(mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                CustomAudienceFixture.VALID_OWNER, CustomAudienceFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME));
    }

    @Test
    public void testLeaveCustomAudience_leaveJoinedCustomAudience() {
        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(CUSTOM_AUDIENCE_PK1_1, callback);
        assertTrue(callback.isSuccess());
        assertEquals(DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(CustomAudienceFixture.VALID_OWNER,
                        CustomAudienceFixture.VALID_BUYER, CustomAudienceFixture.VALID_NAME));

        callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(CustomAudienceFixture.VALID_OWNER,
                CustomAudienceFixture.VALID_BUYER, CustomAudienceFixture.VALID_NAME, callback);
        assertTrue(callback.isSuccess());
        assertNull(mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                CustomAudienceFixture.VALID_OWNER, CustomAudienceFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME));
    }

    @Test
    public void testLeaveCustomAudience_leaveNotJoinedCustomAudience_doesNotFail() {
        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(CustomAudienceFixture.VALID_OWNER,
                CustomAudienceFixture.VALID_BUYER, "Not exist name", callback);
        assertTrue(callback.isSuccess());
        assertNull(mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                CustomAudienceFixture.VALID_OWNER, CustomAudienceFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME));
    }

    private static class ResultCapturingCallback implements ICustomAudienceCallback {
        private boolean mIsSuccess;
        private Exception mException;

        public boolean isSuccess() {
            return mIsSuccess;
        }

        public Exception getException() {
            return mException;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
        }

        @Override
        public void onFailure(FledgeErrorResponse responseParcel) throws RemoteException {
            mIsSuccess = false;
            mException = responseParcel.asException();
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Should not be called.");
        }
    }
}
