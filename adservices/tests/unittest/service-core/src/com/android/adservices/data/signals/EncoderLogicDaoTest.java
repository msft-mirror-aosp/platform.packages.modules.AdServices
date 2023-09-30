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

package com.android.adservices.data.signals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

public class EncoderLogicDaoTest {

    public static AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    public static AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private EncoderLogicDao mEncoderLogicDao;

    @Before
    public void setup() {
        mEncoderLogicDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncoderLogicDao();
    }

    @Test
    public void testPersistEncoderLogic() {
        assertNull("Initial state should have been empty", mEncoderLogicDao.getEncoder(BUYER_1));

        DBEncoderLogic logic = DBEncoderLogicFixture.anEncoderLogic();
        assertEquals(
                "One entry should have been inserted", 1, mEncoderLogicDao.persistEncoder(logic));
    }

    @Test
    public void testExistsAndGetAnEncoder() {
        assertNull("Initial state should have been empty", mEncoderLogicDao.getEncoder(BUYER_1));

        DBEncoderLogic logic = DBEncoderLogicFixture.anEncoderLogic();
        assertEquals(
                "One entry should have been inserted", 1, mEncoderLogicDao.persistEncoder(logic));

        assertTrue(
                "Encoder for the buyer should have existed",
                mEncoderLogicDao.doesEncoderExist(BUYER_1));

        DBEncoderLogic retrieved = mEncoderLogicDao.getEncoder(BUYER_1);
        assertEquals(logic.getVersion(), retrieved.getVersion());
        assertEquals(logic.getBuyer(), retrieved.getBuyer());
    }

    @Test
    public void testPersistEncoderLogicOverwrites() {
        assertNull("Initial state should have been empty", mEncoderLogicDao.getEncoder(BUYER_1));

        DBEncoderLogic v1 = DBEncoderLogicFixture.anEncoderLogicBuilder().setVersion(1).build();
        assertEquals("One entry should have been inserted", 1, mEncoderLogicDao.persistEncoder(v1));
        assertEquals(
                "Version should have been 1",
                v1.getVersion(),
                mEncoderLogicDao.getEncoder(BUYER_1).getVersion());

        DBEncoderLogic v2 = DBEncoderLogicFixture.anEncoderLogicBuilder().setVersion(2).build();
        mEncoderLogicDao.persistEncoder(v2);
        assertEquals(
                "Version should have been 2",
                v2.getVersion(),
                mEncoderLogicDao.getEncoder(BUYER_1).getVersion());
    }

    @Test
    public void testGetAllBuyersWithRegisteredEncoders() {
        assertNull("Initial state should have been empty", mEncoderLogicDao.getEncoder(BUYER_1));
        assertNull("Initial state should have been empty", mEncoderLogicDao.getEncoder(BUYER_2));

        DBEncoderLogic logic1 =
                DBEncoderLogicFixture.anEncoderLogicBuilder(BUYER_1).setVersion(1).build();
        DBEncoderLogic logic2 =
                DBEncoderLogicFixture.anEncoderLogicBuilder(BUYER_2).setVersion(1).build();

        assertEquals(
                "First entry should have been inserted",
                1,
                mEncoderLogicDao.persistEncoder(logic1));
        assertEquals(
                "Second entry should have been inserted",
                2,
                mEncoderLogicDao.persistEncoder(logic2));

        Set<AdTechIdentifier> actualRegisteredBuyers =
                mEncoderLogicDao.getAllBuyersWithRegisteredEncoders().stream()
                        .collect(Collectors.toSet());
        Set<AdTechIdentifier> expectedRegisteredBuyers = Set.of(BUYER_1, BUYER_2);
        assertEquals(expectedRegisteredBuyers, actualRegisteredBuyers);
    }

    @Test
    public void testDeleteEncoder() {
        assertNull("Initial state should have been empty", mEncoderLogicDao.getEncoder(BUYER_1));

        DBEncoderLogic logic = DBEncoderLogicFixture.anEncoderLogic();
        assertEquals(
                "One entry should have been inserted", 1, mEncoderLogicDao.persistEncoder(logic));

        mEncoderLogicDao.persistEncoder(
                DBEncoderLogicFixture.anEncoderLogicBuilder(BUYER_2).build());

        assertTrue(
                "Encoder for the buyer should have existed",
                mEncoderLogicDao.doesEncoderExist(BUYER_1));
        assertTrue(
                "Encoder for the buyer should have existed",
                mEncoderLogicDao.doesEncoderExist(BUYER_2));

        mEncoderLogicDao.deleteEncoder(BUYER_1);

        assertFalse(
                "Encoder for the buyer should have been deleted",
                mEncoderLogicDao.doesEncoderExist(BUYER_1));
        assertTrue(
                "Encoder for the buyer should have remain untouched",
                mEncoderLogicDao.doesEncoderExist(BUYER_2));
    }

    @Test
    public void testDeleteAllEncoders() {
        assertNull("Initial state should have been empty", mEncoderLogicDao.getEncoder(BUYER_1));

        DBEncoderLogic logic = DBEncoderLogicFixture.anEncoderLogic();
        mEncoderLogicDao.persistEncoder(logic);
        mEncoderLogicDao.persistEncoder(
                DBEncoderLogicFixture.anEncoderLogicBuilder(BUYER_2).build());

        assertTrue(
                "Encoder for the buyer should have existed",
                mEncoderLogicDao.doesEncoderExist(BUYER_1));
        assertTrue(
                "Encoder for the buyer should have existed",
                mEncoderLogicDao.doesEncoderExist(BUYER_2));

        mEncoderLogicDao.deleteAllEncoders();

        assertFalse(
                "Encoder for all the buyers should have been deleted",
                mEncoderLogicDao.doesEncoderExist(BUYER_1));
        assertFalse(
                "Encoder for all the buyers should have been deleted",
                mEncoderLogicDao.doesEncoderExist(BUYER_2));
    }
}
