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

import static com.android.adservices.data.signals.DBEncodedPayloadFixture.assertDBEncodedPayloadsAreEqual;
import static com.android.adservices.data.signals.DBProtectedSignalFixture.assertEqualsExceptId;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.room.Room;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@MockStatic(PackageManagerCompatUtils.class)
@RequiresSdkLevelAtLeastT
public final class ProtectedSignalsDaoTest extends AdServicesExtendedMockitoTestCase {

    public static AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    public static AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;
    public static String PACKAGE_1 = CommonFixture.TEST_PACKAGE_NAME_1;
    public static String PACKAGE_2 = CommonFixture.TEST_PACKAGE_NAME_2;

    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private PackageManager mPackageManagerMock;

    private ProtectedSignalsDao mProtectedSignalsDao;
    private EncodedPayloadDao mEncodedPayloadDao;

    @Before
    public void setup() {
        ProtectedSignalsDatabase protectedSignalsDatabase =
                Room.inMemoryDatabaseBuilder(mContext, ProtectedSignalsDatabase.class).build();

        mProtectedSignalsDao = protectedSignalsDatabase.protectedSignalsDao();
        mEncodedPayloadDao = protectedSignalsDatabase.getEncodedPayloadDao();
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
    public void testTwoSignalsDeleteAll() {
        mProtectedSignalsDao.insertSignals(
                Arrays.asList(DBProtectedSignalFixture.SIGNAL, DBProtectedSignalFixture.SIGNAL));

        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        mProtectedSignalsDao.deleteAllSignals();
        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(0, readResult.size());
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
                BUYER_1,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                Arrays.asList(DBProtectedSignalFixture.SIGNAL, DBProtectedSignalFixture.SIGNAL),
                Collections.emptyList());
        // Get all the signals for the test buyer
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1).getLastSignalsUpdatedTime());
        // Delete one of the signals and insert two more
        mProtectedSignalsDao.insertAndDelete(
                BUYER_1,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                Arrays.asList(DBProtectedSignalFixture.SIGNAL, DBProtectedSignalFixture.SIGNAL),
                readResult.subList(0, 1));
        // Check that the deletions and insertion occurred
        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1).getLastSignalsUpdatedTime());

        assertEquals(3, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(0));
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(1));
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(2));
    }

    @Test
    public void testInsertAndDelete_deletesOrphanedEncodedPayloads() {
        DBEncodedPayload expectedEncodedPayloadBuyer1 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(
                                DBProtectedSignalFixture.SIGNAL.getBuyer())
                        .build();
        DBEncodedPayload expectedEncodedPayloadBuyer2 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(
                                DBProtectedSignalFixture.SIGNAL_OTHER_BUYER.getBuyer())
                        .build();
        mProtectedSignalsDao.insertSignals(
                ImmutableList.of(
                        DBProtectedSignalFixture.SIGNAL,
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER));
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer1);
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer2);
        List<DBProtectedSignal> initialSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Initial signals for BUYER_1").that(initialSignalsBuyer1).hasSize(1);
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, initialSignalsBuyer1.get(0));
        List<DBProtectedSignal> initialSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER.getBuyer());
        assertWithMessage("Initial signals for BUYER_1").that(initialSignalsBuyer2).hasSize(1);
        assertEqualsExceptId(
                DBProtectedSignalFixture.SIGNAL_OTHER_BUYER, initialSignalsBuyer2.get(0));
        DBEncodedPayload initialPersistedEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Initial encoded payload for BUYER_1")
                .that(initialPersistedEncodedPayloadBuyer1)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer1, initialPersistedEncodedPayloadBuyer1);
        DBEncodedPayload initialPersistedEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER.getBuyer());
        assertWithMessage("Initial encoded payload for BUYER_2")
                .that(initialPersistedEncodedPayloadBuyer2)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer2, initialPersistedEncodedPayloadBuyer2);

        mProtectedSignalsDao.insertAndDelete(
                DBProtectedSignalFixture.SIGNAL.getBuyer(),
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                /* signalsToInsert= */ ImmutableList.of(),
                /* signalsToDelete= */ initialSignalsBuyer1);

        List<DBProtectedSignal> postDeletionSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Signals for BUYER_1 after deletion")
                .that(postDeletionSignalsBuyer1)
                .isEmpty();
        DBEncodedPayload postDeletionEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Encoded payload for BUYER_1 after deletion")
                .that(postDeletionEncodedPayloadBuyer1)
                .isNull();
        DBEncodedPayload postDeletionEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER.getBuyer());
        assertWithMessage("Encoded payload for BUYER_2 after deletion")
                .that(postDeletionEncodedPayloadBuyer2)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer2, postDeletionEncodedPayloadBuyer2);
    }

    @Test
    public void testDeleteExpiredSignalsAndUpdateSignalsUpdateMetadata() {
        // Insert two signals
        mProtectedSignalsDao.insertAndDelete(
                BUYER_1,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                Arrays.asList(
                        DBProtectedSignalFixture.SIGNAL,
                        DBProtectedSignalFixture.LATER_TIME_SIGNAL),
                Collections.emptyList());
        assertEquals(
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));
        // Delete the older signal
        assertEquals(
                1,
                mProtectedSignalsDao.deleteExpiredSignalsAndUpdateSignalsUpdateMetadata(
                        DBProtectedSignalFixture.SIGNAL
                                .getCreationTime()
                                .plus(Duration.ofMillis(1)),
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
        assertEquals(
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));
        // Check that the deletions and insertion occurred
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(DBProtectedSignalFixture.LATER_TIME_SIGNAL, readResult.get(0));
    }

    @Test
    public void
            testDeleteExpiredSignalsAndUpdateSignalsUpdateMetadata_deletesOrphanedEncodedPayloads() {
        DBEncodedPayload expectedEncodedPayloadBuyer1 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(
                                DBProtectedSignalFixture.SIGNAL.getBuyer())
                        .build();
        DBEncodedPayload expectedEncodedPayloadBuyer2 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(
                                DBProtectedSignalFixture.LATER_TIME_SIGNAL_OTHER_BUYER.getBuyer())
                        .build();
        mProtectedSignalsDao.insertSignals(
                ImmutableList.of(
                        DBProtectedSignalFixture.SIGNAL,
                        DBProtectedSignalFixture.LATER_TIME_SIGNAL_OTHER_BUYER));
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer1);
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer2);
        List<DBProtectedSignal> initialSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Initial signals for BUYER_1").that(initialSignalsBuyer1).hasSize(1);
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, initialSignalsBuyer1.get(0));
        List<DBProtectedSignal> initialSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(
                        DBProtectedSignalFixture.LATER_TIME_SIGNAL_OTHER_BUYER.getBuyer());
        assertWithMessage("Initial signals for BUYER_1").that(initialSignalsBuyer2).hasSize(1);
        assertEqualsExceptId(
                DBProtectedSignalFixture.LATER_TIME_SIGNAL_OTHER_BUYER,
                initialSignalsBuyer2.get(0));
        DBEncodedPayload initialPersistedEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Initial encoded payload for BUYER_1")
                .that(initialPersistedEncodedPayloadBuyer1)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer1, initialPersistedEncodedPayloadBuyer1);
        DBEncodedPayload initialPersistedEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(
                        DBProtectedSignalFixture.LATER_TIME_SIGNAL_OTHER_BUYER.getBuyer());
        assertWithMessage("Initial encoded payload for BUYER_2")
                .that(initialPersistedEncodedPayloadBuyer2)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer2, initialPersistedEncodedPayloadBuyer2);

        mProtectedSignalsDao.deleteExpiredSignalsAndUpdateSignalsUpdateMetadata(
                DBProtectedSignalFixture.SIGNAL.getCreationTime().plus(Duration.ofMillis(1)),
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);

        List<DBProtectedSignal> postDeletionSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Signals for BUYER_1 after deletion")
                .that(postDeletionSignalsBuyer1)
                .isEmpty();
        DBEncodedPayload postDeletionEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Encoded payload for BUYER_1 after deletion")
                .that(postDeletionEncodedPayloadBuyer1)
                .isNull();
        DBEncodedPayload postDeletionEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(
                        DBProtectedSignalFixture.LATER_TIME_SIGNAL_OTHER_BUYER.getBuyer());
        assertWithMessage("Encoded payload for BUYER_2 after deletion")
                .that(postDeletionEncodedPayloadBuyer2)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer2, postDeletionEncodedPayloadBuyer2);
    }

    @Test
    public void testDeleteDisallowedBuyerSignalsNoSignals() {
        assertEquals(0, mProtectedSignalsDao.deleteDisallowedBuyerSignals(mEnrollmentDaoMock));
    }

    @Test
    public void testDeleteDisallowedBuyerSignalsAllAllowed() {
        DBProtectedSignal signal1 = DBProtectedSignalFixture.getBuilder().setBuyer(BUYER_1).build();
        DBProtectedSignal signal2 = DBProtectedSignalFixture.getBuilder().setBuyer(BUYER_2).build();
        // Insert two signals
        mProtectedSignalsDao.insertAndDelete(
                BUYER_1,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                Arrays.asList(signal1, signal2),
                Collections.emptyList());
        when(mEnrollmentDaoMock.getAllFledgeEnrolledAdTechs())
                .thenReturn(new HashSet<>(Arrays.asList(BUYER_1, BUYER_2)));
        assertEquals(0, mProtectedSignalsDao.deleteDisallowedBuyerSignals(mEnrollmentDaoMock));
        // Check that no deletion occurred
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(signal1, readResult.get(0));
        assertEquals(
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));
        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_2);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(signal2, readResult.get(0));
        assertNull(mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_2));
    }

    @Test
    public void testDeleteDisallowedBuyerSignals() {
        DBProtectedSignal signal1 =
                DBProtectedSignalFixture.getBuilder().setBuyer(CommonFixture.VALID_BUYER_1).build();
        DBProtectedSignal signal2 =
                DBProtectedSignalFixture.getBuilder().setBuyer(CommonFixture.VALID_BUYER_2).build();
        // Insert two signals
        mProtectedSignalsDao.insertAndDelete(
                BUYER_1,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                List.of(signal1),
                Collections.emptyList());
        mProtectedSignalsDao.insertAndDelete(
                BUYER_2,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                List.of(signal2),
                Collections.emptyList());
        assertEquals(
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));
        assertEquals(
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_2)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_2));
        when(mEnrollmentDaoMock.getAllFledgeEnrolledAdTechs())
                .thenReturn(Collections.singleton(CommonFixture.VALID_BUYER_1));
        assertEquals(1, mProtectedSignalsDao.deleteDisallowedBuyerSignals(mEnrollmentDaoMock));
        assertEquals(
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));
        assertNull(mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_2));
        // Check that the correct deletion occurred
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(signal1, readResult.get(0));
    }

    @Test
    public void testDeleteDisallowedBuyerSignals_deletesOrphanedEncodedPayloads() {
        when(mEnrollmentDaoMock.getAllFledgeEnrolledAdTechs())
                .thenReturn(
                        Collections.singleton(
                                DBProtectedSignalFixture.SIGNAL_OTHER_BUYER.getBuyer()));
        DBEncodedPayload expectedEncodedPayloadBuyer1 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(
                                DBProtectedSignalFixture.SIGNAL.getBuyer())
                        .build();
        DBEncodedPayload expectedEncodedPayloadBuyer2 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(
                                DBProtectedSignalFixture.SIGNAL_OTHER_BUYER.getBuyer())
                        .build();
        mProtectedSignalsDao.insertSignals(
                ImmutableList.of(
                        DBProtectedSignalFixture.SIGNAL,
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER));
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer1);
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer2);
        List<DBProtectedSignal> initialSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Initial signals for BUYER_1").that(initialSignalsBuyer1).hasSize(1);
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, initialSignalsBuyer1.get(0));
        List<DBProtectedSignal> initialSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER.getBuyer());
        assertWithMessage("Initial signals for BUYER_1").that(initialSignalsBuyer2).hasSize(1);
        assertEqualsExceptId(
                DBProtectedSignalFixture.SIGNAL_OTHER_BUYER, initialSignalsBuyer2.get(0));
        DBEncodedPayload initialPersistedEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Initial encoded payload for BUYER_1")
                .that(initialPersistedEncodedPayloadBuyer1)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer1, initialPersistedEncodedPayloadBuyer1);
        DBEncodedPayload initialPersistedEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER.getBuyer());
        assertWithMessage("Initial encoded payload for BUYER_2")
                .that(initialPersistedEncodedPayloadBuyer2)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer2, initialPersistedEncodedPayloadBuyer2);

        mProtectedSignalsDao.deleteDisallowedBuyerSignals(mEnrollmentDaoMock);

        List<DBProtectedSignal> postDeletionSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Signals for BUYER_1 after deletion")
                .that(postDeletionSignalsBuyer1)
                .isEmpty();
        DBEncodedPayload postDeletionEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Encoded payload for BUYER_1 after deletion")
                .that(postDeletionEncodedPayloadBuyer1)
                .isNull();
        DBEncodedPayload postDeletionEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER.getBuyer());
        assertWithMessage("Encoded payload for BUYER_2 after deletion")
                .that(postDeletionEncodedPayloadBuyer2)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer2, postDeletionEncodedPayloadBuyer2);
    }

    @Test
    public void testDeleteDisallowedPackageSignalsNoSignals() {
        assertEquals(
                0,
                mProtectedSignalsDao.deleteAllDisallowedPackageSignalsAndUpdateSignalUpdateMetadata(
                        mPackageManagerMock,
                        mMockFlags,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testDeleteAllDisallowedPackageSignalsAllAllowed() {
        DBProtectedSignal signal1 =
                DBProtectedSignalFixture.getBuilder().setPackageName(PACKAGE_1).build();
        DBProtectedSignal signal2 =
                DBProtectedSignalFixture.getBuilder().setPackageName(PACKAGE_2).build();
        final class FlagsWithAllAppsAllowed implements Flags {
            @Override
            public String getPasAppAllowList() {
                return AllowLists.ALLOW_ALL;
            }
        }
        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = PACKAGE_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = PACKAGE_2;
        doReturn(Arrays.asList(installedPackage1, installedPackage2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
        // Insert two signals
        mProtectedSignalsDao.insertSignals(Arrays.asList(signal1, signal2));
        List<DBProtectedSignal> readResult1 = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(2, readResult1.size());
        assertEquals(
                0,
                mProtectedSignalsDao.deleteAllDisallowedPackageSignalsAndUpdateSignalUpdateMetadata(
                        mPackageManagerMock,
                        new FlagsWithAllAppsAllowed(),
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
        // Check that no deletion occurred
        List<DBProtectedSignal> readResult2 = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(2, readResult2.size());
    }

    @Test
    public void testDeleteAllDisallowedPackageSignals() {
        DBProtectedSignal signal1 =
                DBProtectedSignalFixture.getBuilder().setPackageName(PACKAGE_1).build();
        DBProtectedSignal signal2 =
                DBProtectedSignalFixture.getBuilder().setPackageName(PACKAGE_2).build();
        class FlagsThatAllowOneApp implements Flags {
            @Override
            public String getPasAppAllowList() {
                return PACKAGE_1;
            }
        }
        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = PACKAGE_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = PACKAGE_2;
        doReturn(Arrays.asList(installedPackage1, installedPackage2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
        mProtectedSignalsDao.insertSignals(Arrays.asList(signal1, signal2));
        List<DBProtectedSignal> readResult1 = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(2, readResult1.size());
        assertEquals(
                1,
                mProtectedSignalsDao.deleteAllDisallowedPackageSignalsAndUpdateSignalUpdateMetadata(
                        mContext.getPackageManager(),
                        new FlagsThatAllowOneApp(),
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
        List<DBProtectedSignal> readResult2 = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult2.size());
        assertEquals(PACKAGE_1, readResult2.get(0).getPackageName());
    }

    @Test
    public void testPersistMetadata() {
        assertNull(
                "Initial state of the table should be empty",
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));

        DBSignalsUpdateMetadata signalsUpdateMetadata =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        assertEquals(
                "One entry should have been inserted",
                1,
                mProtectedSignalsDao.persistSignalsUpdateMetadata(signalsUpdateMetadata));
    }

    @Test
    public void testDeleteMetadata() {
        assertNull(
                "Initial state of the table should be empty",
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));

        DBSignalsUpdateMetadata signalsUpdateMetadata =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        assertEquals(
                "One entry should have been inserted",
                1,
                mProtectedSignalsDao.persistSignalsUpdateMetadata(signalsUpdateMetadata));

        mProtectedSignalsDao.deleteSignalsUpdateMetadata(BUYER_1);
        assertNull(
                "Metadata should have been deleted",
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));
    }

    @Test
    public void testQuerySignalsUpdateMetadata() {
        assertNull(
                "Initial state of the table should be empty",
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));

        DBSignalsUpdateMetadata signalsUpdateMetadata =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        assertEquals(
                "One entry should have been inserted",
                1,
                mProtectedSignalsDao.persistSignalsUpdateMetadata(signalsUpdateMetadata));
        DBSignalsUpdateMetadata retrieved = mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1);

        assertEquals(signalsUpdateMetadata.getBuyer(), retrieved.getBuyer());
        assertEquals(
                signalsUpdateMetadata.getLastSignalsUpdatedTime(),
                retrieved.getLastSignalsUpdatedTime());
    }

    @Test
    public void testPersistMetadataReplacesExisting() {
        assertNull(
                "Initial state of the table should be empty",
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));

        DBSignalsUpdateMetadata.Builder anBuilder =
                DBSignalsUpdateMetadata.builder().setBuyer(BUYER_1);
        DBSignalsUpdateMetadata previous =
                anBuilder
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        mProtectedSignalsDao.persistSignalsUpdateMetadata(previous);

        DBSignalsUpdateMetadata retrieved = mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1);
        assertEquals(previous.getLastSignalsUpdatedTime(), retrieved.getLastSignalsUpdatedTime());

        DBSignalsUpdateMetadata updated =
                anBuilder
                        .setLastSignalsUpdatedTime(
                                CommonFixture.FIXED_NEXT_ONE_DAY.truncatedTo(ChronoUnit.MILLIS))
                        .build();

        mProtectedSignalsDao.persistSignalsUpdateMetadata(updated);
        retrieved = mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1);
        assertNotEquals(
                previous.getLastSignalsUpdatedTime(), retrieved.getLastSignalsUpdatedTime());
        assertEquals(updated.getLastSignalsUpdatedTime(), retrieved.getLastSignalsUpdatedTime());
    }

    @Test
    public void testDeleteRawSignalsByPackage_onlyDeletesRawSignalsFromPackage() {
        mProtectedSignalsDao.insertSignals(
                ImmutableList.of(
                        DBProtectedSignalFixture.SIGNAL,
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE));
        List<DBProtectedSignal> initialSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Initial signals for BUYER_1").that(initialSignalsBuyer1).hasSize(1);
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, initialSignalsBuyer1.get(0));
        List<DBProtectedSignal> initialSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer());
        assertWithMessage("Initial signals for BUYER_2").that(initialSignalsBuyer2).hasSize(1);
        assertEqualsExceptId(
                DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE,
                initialSignalsBuyer2.get(0));

        mProtectedSignalsDao.deleteRawSignalsByPackage(
                ImmutableList.of(DBProtectedSignalFixture.SIGNAL.getPackageName()));

        List<DBProtectedSignal> postDeletionSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Signals for BUYER_1 after deletion")
                .that(postDeletionSignalsBuyer1)
                .isEmpty();
        List<DBProtectedSignal> postDeletionSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer());
        assertWithMessage("Signals for BUYER_2 after deletion")
                .that(postDeletionSignalsBuyer2)
                .hasSize(1);
        assertEqualsExceptId(
                DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE,
                postDeletionSignalsBuyer2.get(0));
    }

    @Test
    public void
            testDeleteEncodedPayloadsWithMissingRawSignals_onlyDeletesOrphanedEncodedPayloads() {
        DBEncodedPayload expectedEncodedPayloadBuyer1 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(
                                DBProtectedSignalFixture.SIGNAL.getBuyer())
                        .build();
        DBEncodedPayload expectedEncodedPayloadBuyer2 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(
                                DBProtectedSignalFixture.SIGNAL_OTHER_BUYER.getBuyer())
                        .build();
        mProtectedSignalsDao.insertSignals(ImmutableList.of(DBProtectedSignalFixture.SIGNAL));
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer1);
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer2);
        List<DBProtectedSignal> initialSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Initial signals for BUYER_1").that(initialSignalsBuyer1).hasSize(1);
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, initialSignalsBuyer1.get(0));
        DBEncodedPayload initialPersistedEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Initial encoded payload for BUYER_1")
                .that(initialPersistedEncodedPayloadBuyer1)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer1, initialPersistedEncodedPayloadBuyer1);
        DBEncodedPayload initialPersistedEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER.getBuyer());
        assertWithMessage("Initial encoded payload for BUYER_2")
                .that(initialPersistedEncodedPayloadBuyer2)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer2, initialPersistedEncodedPayloadBuyer2);

        mProtectedSignalsDao.deleteEncodedPayloadsWithMissingRawSignals();

        DBEncodedPayload postDeletionEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Encoded payload for BUYER_1 after deletion")
                .that(postDeletionEncodedPayloadBuyer1)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer1, postDeletionEncodedPayloadBuyer1);
        DBEncodedPayload postDeletionEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER.getBuyer());
        assertWithMessage("Encoded payload for BUYER_2 after deletion")
                .that(postDeletionEncodedPayloadBuyer2)
                .isNull();
    }

    @Test
    public void testDeleteSignalsByPackage_deletesRawSignalsFromPackage() {
        mProtectedSignalsDao.insertSignals(
                ImmutableList.of(
                        DBProtectedSignalFixture.SIGNAL,
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE));
        List<DBProtectedSignal> initialSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Initial signals for BUYER_1").that(initialSignalsBuyer1).hasSize(1);
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, initialSignalsBuyer1.get(0));
        List<DBProtectedSignal> initialSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer());
        assertWithMessage("Initial signals for BUYER_2").that(initialSignalsBuyer2).hasSize(1);
        assertEqualsExceptId(
                DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE,
                initialSignalsBuyer2.get(0));

        mProtectedSignalsDao.deleteSignalsByPackage(
                ImmutableList.of(DBProtectedSignalFixture.SIGNAL.getPackageName()));

        List<DBProtectedSignal> postDeletionSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Signals for BUYER_1 after deletion")
                .that(postDeletionSignalsBuyer1)
                .isEmpty();
        List<DBProtectedSignal> postDeletionSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer());
        assertWithMessage("Signals for BUYER_2 after deletion")
                .that(postDeletionSignalsBuyer2)
                .hasSize(1);
        assertEqualsExceptId(
                DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE,
                postDeletionSignalsBuyer2.get(0));
    }

    @Test
    public void testDeleteSignalsByPackage_deletesOrphanedEncodedPayloads() {
        DBEncodedPayload expectedEncodedPayloadBuyer1 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(
                                DBProtectedSignalFixture.SIGNAL.getBuyer())
                        .build();
        DBEncodedPayload expectedEncodedPayloadBuyer2 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(
                                DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE
                                        .getBuyer())
                        .build();
        mProtectedSignalsDao.insertSignals(
                ImmutableList.of(
                        DBProtectedSignalFixture.SIGNAL,
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE));
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer1);
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer2);
        List<DBProtectedSignal> initialSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Initial signals for BUYER_1").that(initialSignalsBuyer1).hasSize(1);
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, initialSignalsBuyer1.get(0));
        List<DBProtectedSignal> initialSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer());
        assertWithMessage("Initial signals for BUYER_1").that(initialSignalsBuyer2).hasSize(1);
        assertEqualsExceptId(
                DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE,
                initialSignalsBuyer2.get(0));
        DBEncodedPayload initialPersistedEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Initial encoded payload for BUYER_1")
                .that(initialPersistedEncodedPayloadBuyer1)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer1, initialPersistedEncodedPayloadBuyer1);
        DBEncodedPayload initialPersistedEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer());
        assertWithMessage("Initial encoded payload for BUYER_2")
                .that(initialPersistedEncodedPayloadBuyer2)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer2, initialPersistedEncodedPayloadBuyer2);

        mProtectedSignalsDao.deleteSignalsByPackage(
                ImmutableList.of(DBProtectedSignalFixture.SIGNAL.getPackageName()));

        DBEncodedPayload postDeletionEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(DBProtectedSignalFixture.SIGNAL.getBuyer());
        assertWithMessage("Encoded payload for BUYER_1 after deletion")
                .that(postDeletionEncodedPayloadBuyer1)
                .isNull();
        DBEncodedPayload postDeletionEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(
                        DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer());
        assertWithMessage("Encoded payload for BUYER_2 after deletion")
                .that(postDeletionEncodedPayloadBuyer2)
                .isNotNull();
        assertDBEncodedPayloadsAreEqual(
                expectedEncodedPayloadBuyer2, postDeletionEncodedPayloadBuyer2);
    }

    @Test
    public void testHasSignalsFromBuyer() {
        // Assert no signals are present in an empty DB.
        boolean hasSignalsFromBuyer = mProtectedSignalsDao.hasSignalsFromBuyer(BUYER_1);
        assertThat(hasSignalsFromBuyer).isFalse();

        // Insert a signal and assert its presence in the DB.
        mProtectedSignalsDao.insertSignals(List.of(DBProtectedSignalFixture.SIGNAL));
        hasSignalsFromBuyer = mProtectedSignalsDao.hasSignalsFromBuyer(BUYER_1);
        assertThat(hasSignalsFromBuyer).isTrue();

        // Delete all signals from the buyer and assert absence of signals.
        mProtectedSignalsDao.deleteByBuyers(List.of(BUYER_1));
        hasSignalsFromBuyer = mProtectedSignalsDao.hasSignalsFromBuyer(BUYER_1);
        assertThat(hasSignalsFromBuyer).isFalse();
    }
}
