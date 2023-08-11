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

package com.android.adservices.data.signals;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProtectedSignalsDaoTest {
    public static AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    public static AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private ProtectedSignalsDao mProtectedSignalsDao;

    @Before
    public void setup() {
        mProtectedSignalsDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, ProtectedSignalsDatabase.class)
                        .build()
                        .protectedSignalsDao();
    }

    @Test
    public void testInsertThenRead() {
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertTrue(readResult.isEmpty());
        mProtectedSignalsDao.insertSignals(Arrays.asList(DBProtectedSignalFixture.SIGNAL));
        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(0));
    }

    @Test
    public void testInsertThenDeleteThenRead() {
        mProtectedSignalsDao.insertSignals(Arrays.asList(DBProtectedSignalFixture.SIGNAL));
        // Need to read before deleting, so that we have the correct id
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult.size());
        mProtectedSignalsDao.deleteSignals(readResult);
        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(0, readResult.size());
    }

    @Test
    public void testTwoIdenticalSignals() {
        mProtectedSignalsDao.insertSignals(
                Arrays.asList(DBProtectedSignalFixture.SIGNAL, DBProtectedSignalFixture.SIGNAL));

        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(2, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertNotNull(readResult.get(1).getId());
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(0));
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(1));
    }

    @Test
    public void testTwoSignalsOneDelete() {
        mProtectedSignalsDao.insertSignals(
                Arrays.asList(DBProtectedSignalFixture.SIGNAL, DBProtectedSignalFixture.SIGNAL));

        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        mProtectedSignalsDao.deleteSignals(readResult.subList(0, 1));
        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(0));
    }

    @Test
    public void testTwoBuyers() {
        DBProtectedSignal signal1 =
                DBProtectedSignal.builder()
                        .setId(null)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setKey(DBProtectedSignalFixture.KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();
        DBProtectedSignal signal2 =
                DBProtectedSignal.builder()
                        .setId(null)
                        .setBuyer(CommonFixture.VALID_BUYER_2)
                        .setKey(DBProtectedSignalFixture.KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();

        mProtectedSignalsDao.insertSignals(Arrays.asList(signal1, signal2));

        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(signal1, readResult.get(0));

        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_2);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(signal2, readResult.get(0));
    }

    @Test
    public void testInsertAndDelete() {
        // Insert two signals
        mProtectedSignalsDao.insertAndDelete(
                Arrays.asList(DBProtectedSignalFixture.SIGNAL, DBProtectedSignalFixture.SIGNAL),
                Collections.emptyList());
        // Get all the signals for the test buyer
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        // Delete one of the signals and insert two more
        mProtectedSignalsDao.insertAndDelete(
                Arrays.asList(DBProtectedSignalFixture.SIGNAL, DBProtectedSignalFixture.SIGNAL),
                readResult.subList(0, 1));
        // Check that the deletions and insertion occurred
        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(3, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(0));
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(1));
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(2));
    }

    private void assertEqualsExceptId(DBProtectedSignal expected, DBProtectedSignal actual) {
        assertEquals(expected.getBuyer(), actual.getBuyer());
        assertArrayEquals(expected.getKey(), actual.getKey());
        assertArrayEquals(expected.getValue(), actual.getValue());
        assertEquals(expected.getCreationTime(), actual.getCreationTime());
        assertEquals(expected.getPackageName(), actual.getPackageName());
    }
}
