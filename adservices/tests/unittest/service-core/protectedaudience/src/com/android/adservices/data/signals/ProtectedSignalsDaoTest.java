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

import static android.adservices.common.CommonFixture.FIXED_NEXT_ONE_DAY;
import static android.adservices.common.CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME_1;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME_2;
import static android.adservices.common.CommonFixture.VALID_BUYER_1;
import static android.adservices.common.CommonFixture.VALID_BUYER_2;

import static com.android.adservices.data.signals.DBEncodedPayloadFixture.expectDBEncodedPayloadsAreEqual;
import static com.android.adservices.data.signals.DBProtectedSignalFixture.LATER_TIME_SIGNAL;
import static com.android.adservices.data.signals.DBProtectedSignalFixture.LATER_TIME_SIGNAL_OTHER_BUYER;
import static com.android.adservices.data.signals.DBProtectedSignalFixture.SIGNAL;
import static com.android.adservices.data.signals.DBProtectedSignalFixture.SIGNAL_OTHER_BUYER;
import static com.android.adservices.data.signals.DBProtectedSignalFixture.SIGNAL_OTHER_BUYER_OTHER_PACKAGE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.room.Room;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.annotations.SetPasAppAllowList;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.signals.evict.EvictionPriority;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;

@MockStatic(PackageManagerCompatUtils.class)
@RequiresSdkLevelAtLeastT
public final class ProtectedSignalsDaoTest extends AdServicesExtendedMockitoTestCase {
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
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        assertWithMessage("readResult").that(readResult).isEmpty();

        mProtectedSignalsDao.insertSignals(ImmutableList.of(SIGNAL));
        readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        expect.withMessage("readResult").that(readResult).containsExactly(SIGNAL);
    }

    @Test
    public void testInsertThenDeleteThenRead() {
        mProtectedSignalsDao.insertSignals(ImmutableList.of(SIGNAL));
        // Need to read before deleting, so that we have the correct id to delete
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        assertWithMessage("readResult").that(readResult).containsExactly(SIGNAL);

        mProtectedSignalsDao.deleteSignals(readResult);
        readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        expect.withMessage("readResult").that(readResult).isEmpty();
    }

    @Test
    public void testTwoIdenticalSignals() {
        List<DBProtectedSignal> signals = ImmutableList.of(SIGNAL, SIGNAL);

        mProtectedSignalsDao.insertSignals(signals);
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        expect.withMessage("readResult").that(readResult).containsExactlyElementsIn(signals);
    }

    @Test
    public void testTwoSignalsOneDelete() {
        DBProtectedSignal signal1 =
                SIGNAL.toBuilder().setEvictionPriority(EvictionPriority.EVICT_SOONER).build();
        DBProtectedSignal signal2 =
                SIGNAL.toBuilder().setEvictionPriority(EvictionPriority.EVICT_LATER).build();

        mProtectedSignalsDao.insertSignals(ImmutableList.of(signal1, signal2));
        // Need to read before deleting, so that we have the correct id to delete
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        assertWithMessage("readResult").that(readResult).containsExactly(signal1, signal2);

        mProtectedSignalsDao.deleteSignals(readResult.subList(0, 1));
        readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        expect.withMessage("readResult").that(readResult).containsExactly(signal2);
    }

    @Test
    public void testTwoSignalsDeleteAll() {
        List<DBProtectedSignal> signals = ImmutableList.of(SIGNAL, SIGNAL);

        mProtectedSignalsDao.insertSignals(signals);
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        assertWithMessage("readResult").that(readResult).containsExactlyElementsIn(signals);

        mProtectedSignalsDao.deleteAllSignals();
        readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        expect.withMessage("readResult").that(readResult).isEmpty();
    }

    @Test
    public void testTwoBuyers() {
        mProtectedSignalsDao.insertSignals(ImmutableList.of(SIGNAL, SIGNAL_OTHER_BUYER));
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        expect.withMessage("readResult").that(readResult).containsExactly(SIGNAL);

        readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_2);

        expect.withMessage("readResult").that(readResult).containsExactly(SIGNAL_OTHER_BUYER);
    }

    @Test
    public void testInsertAndDelete() {
        // Insert two signals.
        Instant firstSignalsUpdatedTime = FIXED_NOW_TRUNCATED_TO_MILLI;
        mProtectedSignalsDao.insertAndDelete(
                VALID_BUYER_1,
                firstSignalsUpdatedTime,
                ImmutableList.of(SIGNAL, SIGNAL),
                ImmutableList.of());
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        expect.withMessage("lastSignalsUpdatedTime")
                .that(
                        mProtectedSignalsDao
                                .getSignalsUpdateMetadata(VALID_BUYER_1)
                                .getLastSignalsUpdatedTime())
                .isEqualTo(firstSignalsUpdatedTime);
        assertWithMessage("readResult").that(readResult).containsExactly(SIGNAL, SIGNAL);

        // Delete one of the signals and insert two more
        Instant secondSignalsUpdatedTime = firstSignalsUpdatedTime.plusMillis(100L);
        mProtectedSignalsDao.insertAndDelete(
                VALID_BUYER_1,
                secondSignalsUpdatedTime,
                ImmutableList.of(SIGNAL, SIGNAL),
                readResult.subList(0, 1));
        readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        // Check that the deletions and insertion occurred.
        expect.withMessage("lastSignalsUpdatedTime")
                .that(
                        mProtectedSignalsDao
                                .getSignalsUpdateMetadata(VALID_BUYER_1)
                                .getLastSignalsUpdatedTime())
                .isEqualTo(secondSignalsUpdatedTime);
        expect.withMessage("readResult").that(readResult).containsExactly(SIGNAL, SIGNAL, SIGNAL);
    }

    @Test
    public void testInsertAndDelete_deletesOrphanedEncodedPayloads() {
        DBEncodedPayload expectedEncodedPayloadBuyer1 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(SIGNAL.getBuyer()).build();
        DBEncodedPayload expectedEncodedPayloadBuyer2 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(VALID_BUYER_2).build();

        mProtectedSignalsDao.insertSignals(ImmutableList.of(SIGNAL, SIGNAL_OTHER_BUYER));
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer1);
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer2);

        List<DBProtectedSignal> initialSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);
        List<DBProtectedSignal> initialSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_2);
        DBEncodedPayload initialPersistedEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL.getBuyer());
        DBEncodedPayload initialPersistedEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL_OTHER_BUYER.getBuyer());

        expectDBEncodedPayloadsAreEqual(
                expect, initialPersistedEncodedPayloadBuyer1, expectedEncodedPayloadBuyer1);
        expectDBEncodedPayloadsAreEqual(
                expect, initialPersistedEncodedPayloadBuyer2, expectedEncodedPayloadBuyer2);
        expect.withMessage("Initial signals for BUYER_1")
                .that(initialSignalsBuyer1)
                .containsExactly(SIGNAL);
        assertWithMessage("Initial signals for BUYER_2")
                .that(initialSignalsBuyer2)
                .containsExactly(SIGNAL_OTHER_BUYER);

        mProtectedSignalsDao.insertAndDelete(
                SIGNAL.getBuyer(),
                FIXED_NOW_TRUNCATED_TO_MILLI,
                /* signalsToInsert= */ ImmutableList.of(),
                /* signalsToDelete= */ initialSignalsBuyer1);

        List<DBProtectedSignal> postDeletionSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL.getBuyer());
        DBEncodedPayload postDeletionEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL.getBuyer());
        DBEncodedPayload postDeletionEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL_OTHER_BUYER.getBuyer());

        expect.withMessage("Signals for BUYER_1 after deletion")
                .that(postDeletionSignalsBuyer1)
                .isEmpty();
        expect.withMessage("Encoded payload for BUYER_1 after deletion")
                .that(postDeletionEncodedPayloadBuyer1)
                .isNull();
        expectDBEncodedPayloadsAreEqual(
                expect, postDeletionEncodedPayloadBuyer2, expectedEncodedPayloadBuyer2);
    }

    @Test
    public void testDeleteExpiredSignalsAndUpdateSignalsUpdateMetadata() {
        // Insert two signals.
        mProtectedSignalsDao.insertAndDelete(
                VALID_BUYER_1,
                FIXED_NOW_TRUNCATED_TO_MILLI,
                ImmutableList.of(SIGNAL, LATER_TIME_SIGNAL),
                ImmutableList.of());

        assertWithMessage("lastSignalsUpdatedTime")
                .that(
                        mProtectedSignalsDao
                                .getSignalsUpdateMetadata(VALID_BUYER_1)
                                .getLastSignalsUpdatedTime())
                .isEqualTo(FIXED_NOW_TRUNCATED_TO_MILLI);

        // Delete the older signal.
        int numSignalsDeleted =
                mProtectedSignalsDao.deleteExpiredSignalsAndUpdateSignalsUpdateMetadata(
                        SIGNAL.getCreationTime().plusMillis(1), FIXED_NOW_TRUNCATED_TO_MILLI);

        expect.withMessage("numSignalsDeleted").that(numSignalsDeleted).isEqualTo(1);

        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        expect.withMessage("lastSignalsUpdatedTime")
                .that(
                        mProtectedSignalsDao
                                .getSignalsUpdateMetadata(VALID_BUYER_1)
                                .getLastSignalsUpdatedTime())
                .isEqualTo(FIXED_NOW_TRUNCATED_TO_MILLI);
        expect.withMessage("readResult").that(readResult).containsExactly(LATER_TIME_SIGNAL);
    }

    @Test
    public void
            testDeleteExpiredSignalsAndUpdateSignalsUpdateMetadata_deletesOrphanedEncodedPayloads() {
        DBEncodedPayload expectedEncodedPayloadBuyer1 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(SIGNAL.getBuyer()).build();
        DBEncodedPayload expectedEncodedPayloadBuyer2 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(
                                LATER_TIME_SIGNAL_OTHER_BUYER.getBuyer())
                        .build();

        mProtectedSignalsDao.insertSignals(ImmutableList.of(SIGNAL, LATER_TIME_SIGNAL_OTHER_BUYER));
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer1);
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer2);

        List<DBProtectedSignal> initialSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL.getBuyer());
        List<DBProtectedSignal> initialSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(LATER_TIME_SIGNAL_OTHER_BUYER.getBuyer());
        DBEncodedPayload initialPersistedEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL.getBuyer());
        DBEncodedPayload initialPersistedEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(LATER_TIME_SIGNAL_OTHER_BUYER.getBuyer());

        expectDBEncodedPayloadsAreEqual(
                expect, initialPersistedEncodedPayloadBuyer1, expectedEncodedPayloadBuyer1);
        expectDBEncodedPayloadsAreEqual(
                expect, initialPersistedEncodedPayloadBuyer2, expectedEncodedPayloadBuyer2);
        expect.withMessage("Initial signals for BUYER_1")
                .that(initialSignalsBuyer1)
                .containsExactly(SIGNAL);
        assertWithMessage("Initial signals for BUYER_2")
                .that(initialSignalsBuyer2)
                .containsExactly(LATER_TIME_SIGNAL_OTHER_BUYER);

        mProtectedSignalsDao.deleteExpiredSignalsAndUpdateSignalsUpdateMetadata(
                SIGNAL.getCreationTime().plus(Duration.ofMillis(1)), FIXED_NOW_TRUNCATED_TO_MILLI);

        List<DBProtectedSignal> postDeletionSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL.getBuyer());
        DBEncodedPayload postDeletionEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL.getBuyer());
        DBEncodedPayload postDeletionEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(LATER_TIME_SIGNAL_OTHER_BUYER.getBuyer());

        expect.withMessage("Signals for BUYER_1 after deletion")
                .that(postDeletionSignalsBuyer1)
                .isEmpty();
        expect.withMessage("Encoded payload for BUYER_1 after deletion")
                .that(postDeletionEncodedPayloadBuyer1)
                .isNull();
        expectDBEncodedPayloadsAreEqual(
                expect, postDeletionEncodedPayloadBuyer2, expectedEncodedPayloadBuyer2);
    }

    @Test
    public void testDeleteDisallowedBuyerSignalsNoSignals() {
        int numSignalsDeleted =
                mProtectedSignalsDao.deleteDisallowedBuyerSignals(mEnrollmentDaoMock);
        expect.withMessage("numSignalsDeleted").that(numSignalsDeleted).isEqualTo(0);
    }

    @Test
    public void testDeleteDisallowedBuyerSignalsAllAllowed() {
        DBProtectedSignal signal1 =
                DBProtectedSignalFixture.getBuilder().setBuyer(VALID_BUYER_1).build();
        DBProtectedSignal signal2 =
                DBProtectedSignalFixture.getBuilder().setBuyer(VALID_BUYER_2).build();

        // Insert two signals
        mProtectedSignalsDao.insertAndDelete(
                VALID_BUYER_1,
                FIXED_NOW_TRUNCATED_TO_MILLI,
                /* signalsToInsert= */ ImmutableList.of(signal1, signal2),
                /* signalsToDelete= */ ImmutableList.of());

        when(mEnrollmentDaoMock.getAllFledgeEnrolledAdTechs())
                .thenReturn(new HashSet<>(ImmutableList.of(VALID_BUYER_1, VALID_BUYER_2)));
        int numSignalsDeleted =
                mProtectedSignalsDao.deleteDisallowedBuyerSignals(mEnrollmentDaoMock);

        assertWithMessage("numSignalsDeleted").that(numSignalsDeleted).isEqualTo(0);

        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        // Check that no deletion occurred
        expect.withMessage("lastSignalsUpdatedTime")
                .that(
                        mProtectedSignalsDao
                                .getSignalsUpdateMetadata(VALID_BUYER_1)
                                .getLastSignalsUpdatedTime())
                .isEqualTo(FIXED_NOW_TRUNCATED_TO_MILLI);
        assertWithMessage("readResult").that(readResult).containsExactly(signal1);

        readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_2);

        expect.withMessage("signalsUpdateMetadata")
                .that(mProtectedSignalsDao.getSignalsUpdateMetadata(VALID_BUYER_2))
                .isNull();
        expect.withMessage("readResult").that(readResult).containsExactly(signal2);
    }

    @Test
    public void testDeleteDisallowedBuyerSignals() {
        DBProtectedSignal signal1 =
                DBProtectedSignalFixture.getBuilder().setBuyer(VALID_BUYER_1).build();
        DBProtectedSignal signal2 =
                DBProtectedSignalFixture.getBuilder().setBuyer(VALID_BUYER_2).build();

        // Insert two signals.
        mProtectedSignalsDao.insertAndDelete(
                VALID_BUYER_1, FIXED_NOW_TRUNCATED_TO_MILLI, List.of(signal1), ImmutableList.of());
        mProtectedSignalsDao.insertAndDelete(
                VALID_BUYER_2, FIXED_NOW_TRUNCATED_TO_MILLI, List.of(signal2), ImmutableList.of());

        expect.withMessage("lastSignalsUpdatedTime for BUYER_1")
                .that(
                        mProtectedSignalsDao
                                .getSignalsUpdateMetadata(VALID_BUYER_1)
                                .getLastSignalsUpdatedTime())
                .isEqualTo(FIXED_NOW_TRUNCATED_TO_MILLI);
        assertWithMessage("lastSignalsUpdatedTime for BUYER_2")
                .that(
                        mProtectedSignalsDao
                                .getSignalsUpdateMetadata(VALID_BUYER_2)
                                .getLastSignalsUpdatedTime())
                .isEqualTo(FIXED_NOW_TRUNCATED_TO_MILLI);

        when(mEnrollmentDaoMock.getAllFledgeEnrolledAdTechs())
                .thenReturn(ImmutableSet.of(VALID_BUYER_1));
        int numSignalsDeleted =
                mProtectedSignalsDao.deleteDisallowedBuyerSignals(mEnrollmentDaoMock);

        expect.withMessage("numSignalsDeleted").that(numSignalsDeleted).isEqualTo(1);
        expect.withMessage("lastSignalsUpdatedTime for BUYER_1")
                .that(
                        mProtectedSignalsDao
                                .getSignalsUpdateMetadata(VALID_BUYER_1)
                                .getLastSignalsUpdatedTime())
                .isEqualTo(FIXED_NOW_TRUNCATED_TO_MILLI);
        assertWithMessage("signalsUpdateMetadata for BUYER_2")
                .that(mProtectedSignalsDao.getSignalsUpdateMetadata(VALID_BUYER_2))
                .isNull();

        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        // Check that the correct deletion occurred.
        expect.withMessage("readResult").that(readResult).containsExactly(signal1);
    }

    @Test
    public void testDeleteDisallowedBuyerSignals_deletesOrphanedEncodedPayloads() {
        DBEncodedPayload expectedEncodedPayloadBuyer1 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(SIGNAL.getBuyer()).build();
        DBEncodedPayload expectedEncodedPayloadBuyer2 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(SIGNAL_OTHER_BUYER.getBuyer())
                        .build();

        mProtectedSignalsDao.insertSignals(ImmutableList.of(SIGNAL, SIGNAL_OTHER_BUYER));
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer1);
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer2);

        List<DBProtectedSignal> initialSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL.getBuyer());
        List<DBProtectedSignal> initialSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL_OTHER_BUYER.getBuyer());
        DBEncodedPayload initialPersistedEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL.getBuyer());
        DBEncodedPayload initialPersistedEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL_OTHER_BUYER.getBuyer());

        expectDBEncodedPayloadsAreEqual(
                expect, initialPersistedEncodedPayloadBuyer1, expectedEncodedPayloadBuyer1);
        expectDBEncodedPayloadsAreEqual(
                expect, initialPersistedEncodedPayloadBuyer2, expectedEncodedPayloadBuyer2);
        expect.withMessage("Initial signals for BUYER_1")
                .that(initialSignalsBuyer1)
                .containsExactly(SIGNAL);
        assertWithMessage("Initial signals for BUYER_2")
                .that(initialSignalsBuyer2)
                .containsExactly(SIGNAL_OTHER_BUYER);

        when(mEnrollmentDaoMock.getAllFledgeEnrolledAdTechs())
                .thenReturn(ImmutableSet.of(SIGNAL_OTHER_BUYER.getBuyer()));
        mProtectedSignalsDao.deleteDisallowedBuyerSignals(mEnrollmentDaoMock);

        List<DBProtectedSignal> postDeletionSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL.getBuyer());
        List<DBProtectedSignal> postDeletionSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL_OTHER_BUYER.getBuyer());
        DBEncodedPayload postDeletionEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL.getBuyer());
        DBEncodedPayload postDeletionEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL_OTHER_BUYER.getBuyer());

        expect.withMessage("Signals for BUYER_1 after deletion")
                .that(postDeletionSignalsBuyer1)
                .isEmpty();
        expect.withMessage("Signals for BUYER_1 after deletion")
                .that(postDeletionSignalsBuyer2)
                .containsExactly(SIGNAL_OTHER_BUYER);
        expect.withMessage("Encoded payload for BUYER_1 after deletion")
                .that(postDeletionEncodedPayloadBuyer1)
                .isNull();
        expectDBEncodedPayloadsAreEqual(
                expect, postDeletionEncodedPayloadBuyer2, expectedEncodedPayloadBuyer2);
    }

    @Test
    public void testDeleteDisallowedPackageSignalsNoSignals() {
        int numSignalsDeleted =
                mProtectedSignalsDao.deleteAllDisallowedPackageSignalsAndUpdateSignalUpdateMetadata(
                        mPackageManagerMock, mFakeFlags, FIXED_NOW_TRUNCATED_TO_MILLI);
        expect.withMessage("numSignalsDeleted").that(numSignalsDeleted).isEqualTo(0);
    }

    @Test
    @SetPasAppAllowList(value = AllowLists.ALLOW_ALL)
    public void testDeleteAllDisallowedPackageSignalsAllAllowed() {
        DBProtectedSignal signal1 =
                DBProtectedSignalFixture.getBuilder().setPackageName(TEST_PACKAGE_NAME_1).build();
        DBProtectedSignal signal2 =
                DBProtectedSignalFixture.getBuilder().setPackageName(TEST_PACKAGE_NAME_2).build();

        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = TEST_PACKAGE_NAME_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = TEST_PACKAGE_NAME_2;
        doReturn(ImmutableList.of(installedPackage1, installedPackage2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        // Insert two signals.
        mProtectedSignalsDao.insertSignals(ImmutableList.of(signal1, signal2));
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);
        assertWithMessage("readResult").that(readResult).containsExactly(signal1, signal2);

        int numSignalsDeleted =
                mProtectedSignalsDao.deleteAllDisallowedPackageSignalsAndUpdateSignalUpdateMetadata(
                        mPackageManagerMock, mFakeFlags, FIXED_NOW_TRUNCATED_TO_MILLI);
        readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        // Check that no deletion occurred.
        expect.withMessage("numSignalsDeleted").that(numSignalsDeleted).isEqualTo(0);
        expect.withMessage("readResult").that(readResult).containsExactly(signal1, signal2);
    }

    @Test
    @SetPasAppAllowList(value = {TEST_PACKAGE_NAME_1})
    public void testDeleteAllDisallowedPackageSignals() {
        DBProtectedSignal signal1 =
                DBProtectedSignalFixture.getBuilder().setPackageName(TEST_PACKAGE_NAME_1).build();
        DBProtectedSignal signal2 =
                DBProtectedSignalFixture.getBuilder().setPackageName(TEST_PACKAGE_NAME_2).build();

        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = TEST_PACKAGE_NAME_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = TEST_PACKAGE_NAME_2;
        doReturn(ImmutableList.of(installedPackage1, installedPackage2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        mProtectedSignalsDao.insertSignals(ImmutableList.of(signal1, signal2));
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        assertWithMessage("readResult").that(readResult).containsExactly(signal1, signal2);

        int numSignalsDeleted =
                mProtectedSignalsDao.deleteAllDisallowedPackageSignalsAndUpdateSignalUpdateMetadata(
                        mContext.getPackageManager(), mFakeFlags, FIXED_NOW_TRUNCATED_TO_MILLI);
        readResult = mProtectedSignalsDao.getSignalsByBuyer(VALID_BUYER_1);

        expect.withMessage("numSignalsDeleted").that(numSignalsDeleted).isEqualTo(1);
        expect.withMessage("readResult").that(readResult).containsExactly(signal1);
    }

    @Test
    public void testPersistMetadata() {
        assertWithMessage("signalsUpdateMetadata")
                .that(mProtectedSignalsDao.getSignalsUpdateMetadata(VALID_BUYER_1))
                .isNull();

        DBSignalsUpdateMetadata signalsUpdateMetadata =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(VALID_BUYER_1)
                        .setLastSignalsUpdatedTime(FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        long numMetadataInserted =
                mProtectedSignalsDao.persistSignalsUpdateMetadata(signalsUpdateMetadata);

        assertWithMessage("numMetadataInserted").that(numMetadataInserted).isEqualTo(1);
    }

    @Test
    public void testDeleteMetadata() {
        assertWithMessage("signalsUpdateMetadata")
                .that(mProtectedSignalsDao.getSignalsUpdateMetadata(VALID_BUYER_1))
                .isNull();

        DBSignalsUpdateMetadata signalsUpdateMetadata =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(VALID_BUYER_1)
                        .setLastSignalsUpdatedTime(FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        long numMetadataInserted =
                mProtectedSignalsDao.persistSignalsUpdateMetadata(signalsUpdateMetadata);

        assertWithMessage("numMetadataInserted").that(numMetadataInserted).isEqualTo(1);

        mProtectedSignalsDao.deleteSignalsUpdateMetadata(VALID_BUYER_1);

        assertWithMessage("signalsUpdateMetadata")
                .that(mProtectedSignalsDao.getSignalsUpdateMetadata(VALID_BUYER_1))
                .isNull();
    }

    @Test
    public void testQuerySignalsUpdateMetadata() {
        assertWithMessage("signalsUpdateMetadata")
                .that(mProtectedSignalsDao.getSignalsUpdateMetadata(VALID_BUYER_1))
                .isNull();

        DBSignalsUpdateMetadata expectedSignalsUpdateMetadata =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(VALID_BUYER_1)
                        .setLastSignalsUpdatedTime(FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        long numMetadataInserted =
                mProtectedSignalsDao.persistSignalsUpdateMetadata(expectedSignalsUpdateMetadata);

        assertWithMessage("numMetadataInserted").that(numMetadataInserted).isEqualTo(1);

        DBSignalsUpdateMetadata signalsUpdateMetadata =
                mProtectedSignalsDao.getSignalsUpdateMetadata(VALID_BUYER_1);

        assertWithMessage("signalsUpdateMetadata")
                .that(signalsUpdateMetadata)
                .isEqualTo(expectedSignalsUpdateMetadata);
    }

    @Test
    public void testPersistMetadataReplacesExisting() {
        assertWithMessage("signalsUpdateMetadata")
                .that(mProtectedSignalsDao.getSignalsUpdateMetadata(VALID_BUYER_1))
                .isNull();

        DBSignalsUpdateMetadata expectedSignalsUpdateMetadata =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(VALID_BUYER_1)
                        .setLastSignalsUpdatedTime(FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();

        mProtectedSignalsDao.persistSignalsUpdateMetadata(expectedSignalsUpdateMetadata);
        DBSignalsUpdateMetadata signalsUpdateMetadata =
                mProtectedSignalsDao.getSignalsUpdateMetadata(VALID_BUYER_1);

        assertWithMessage("signalsUpdateMetadata")
                .that(signalsUpdateMetadata)
                .isEqualTo(expectedSignalsUpdateMetadata);

        expectedSignalsUpdateMetadata =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(VALID_BUYER_1)
                        .setLastSignalsUpdatedTime(
                                FIXED_NEXT_ONE_DAY.truncatedTo(ChronoUnit.MILLIS))
                        .build();

        mProtectedSignalsDao.persistSignalsUpdateMetadata(expectedSignalsUpdateMetadata);
        signalsUpdateMetadata = mProtectedSignalsDao.getSignalsUpdateMetadata(VALID_BUYER_1);

        assertWithMessage("signalsUpdateMetadata")
                .that(signalsUpdateMetadata)
                .isEqualTo(expectedSignalsUpdateMetadata);
    }

    @Test
    public void testDeleteRawSignalsByPackage_onlyDeletesRawSignalsFromPackage() {
        mProtectedSignalsDao.insertSignals(
                ImmutableList.of(SIGNAL, SIGNAL_OTHER_BUYER_OTHER_PACKAGE));

        List<DBProtectedSignal> initialSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL.getBuyer());
        List<DBProtectedSignal> initialSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer());

        expect.withMessage("Initial signals for BUYER_1")
                .that(initialSignalsBuyer1)
                .containsExactly(SIGNAL);
        assertWithMessage("Initial signals for BUYER_2")
                .that(initialSignalsBuyer2)
                .containsExactly(SIGNAL_OTHER_BUYER_OTHER_PACKAGE);

        mProtectedSignalsDao.deleteRawSignalsByPackage(ImmutableList.of(SIGNAL.getPackageName()));

        List<DBProtectedSignal> postDeletionSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL.getBuyer());
        List<DBProtectedSignal> postDeletionSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer());

        expect.withMessage("Signals for BUYER_1 after deletion")
                .that(postDeletionSignalsBuyer1)
                .isEmpty();
        expect.withMessage("Signals for BUYER_2 after deletion")
                .that(postDeletionSignalsBuyer2)
                .containsExactly(SIGNAL_OTHER_BUYER_OTHER_PACKAGE);
    }

    @Test
    public void
            testDeleteEncodedPayloadsWithMissingRawSignals_onlyDeletesOrphanedEncodedPayloads() {
        DBEncodedPayload expectedEncodedPayloadBuyer1 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(SIGNAL.getBuyer()).build();
        DBEncodedPayload expectedEncodedPayloadBuyer2 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(SIGNAL_OTHER_BUYER.getBuyer())
                        .build();

        mProtectedSignalsDao.insertSignals(ImmutableList.of(SIGNAL));
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer1);
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer2);

        List<DBProtectedSignal> initialSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL.getBuyer());
        DBEncodedPayload initialPersistedEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL.getBuyer());
        DBEncodedPayload initialPersistedEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL_OTHER_BUYER.getBuyer());

        expectDBEncodedPayloadsAreEqual(
                expect, initialPersistedEncodedPayloadBuyer1, expectedEncodedPayloadBuyer1);
        expectDBEncodedPayloadsAreEqual(
                expect, initialPersistedEncodedPayloadBuyer2, expectedEncodedPayloadBuyer2);
        assertWithMessage("Initial signals for BUYER_1")
                .that(initialSignalsBuyer1)
                .containsExactly(SIGNAL);

        mProtectedSignalsDao.deleteEncodedPayloadsWithMissingRawSignals();

        DBEncodedPayload postDeletionEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL.getBuyer());
        DBEncodedPayload postDeletionEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL_OTHER_BUYER.getBuyer());

        expectDBEncodedPayloadsAreEqual(
                expect, postDeletionEncodedPayloadBuyer1, expectedEncodedPayloadBuyer1);
        expect.withMessage("Encoded payload for BUYER_2 after deletion")
                .that(postDeletionEncodedPayloadBuyer2)
                .isNull();
    }

    @Test
    public void testDeleteSignalsByPackage_deletesRawSignalsFromPackage() {
        mProtectedSignalsDao.insertSignals(
                ImmutableList.of(SIGNAL, SIGNAL_OTHER_BUYER_OTHER_PACKAGE));

        List<DBProtectedSignal> initialSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL.getBuyer());
        List<DBProtectedSignal> initialSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer());

        expect.withMessage("Initial signals for BUYER_1")
                .that(initialSignalsBuyer1)
                .containsExactly(SIGNAL);
        assertWithMessage("Initial signals for BUYER_2")
                .that(initialSignalsBuyer2)
                .containsExactly(SIGNAL_OTHER_BUYER_OTHER_PACKAGE);

        mProtectedSignalsDao.deleteSignalsByPackage(ImmutableList.of(SIGNAL.getPackageName()));

        List<DBProtectedSignal> postDeletionSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL.getBuyer());
        List<DBProtectedSignal> postDeletionSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer());

        expect.withMessage("Signals for BUYER_1 after deletion")
                .that(postDeletionSignalsBuyer1)
                .isEmpty();
        expect.withMessage("Signals for BUYER_2 after deletion")
                .that(postDeletionSignalsBuyer2)
                .containsExactly(SIGNAL_OTHER_BUYER_OTHER_PACKAGE);
    }

    @Test
    public void testDeleteSignalsByPackage_deletesOrphanedEncodedPayloads() {
        DBEncodedPayload expectedEncodedPayloadBuyer1 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(SIGNAL.getBuyer()).build();
        DBEncodedPayload expectedEncodedPayloadBuyer2 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder(
                                SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer())
                        .build();

        mProtectedSignalsDao.insertSignals(
                ImmutableList.of(SIGNAL, SIGNAL_OTHER_BUYER_OTHER_PACKAGE));
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer1);
        mEncodedPayloadDao.persistEncodedPayload(expectedEncodedPayloadBuyer2);

        List<DBProtectedSignal> initialSignalsBuyer1 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL.getBuyer());
        List<DBProtectedSignal> initialSignalsBuyer2 =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer());
        DBEncodedPayload initialPersistedEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL.getBuyer());
        DBEncodedPayload initialPersistedEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer());

        expectDBEncodedPayloadsAreEqual(
                expect, initialPersistedEncodedPayloadBuyer1, expectedEncodedPayloadBuyer1);
        expectDBEncodedPayloadsAreEqual(
                expect, initialPersistedEncodedPayloadBuyer2, expectedEncodedPayloadBuyer2);
        expect.withMessage("Initial signals for BUYER_1")
                .that(initialSignalsBuyer1)
                .containsExactly(SIGNAL);
        assertWithMessage("Initial signals for BUYER_2")
                .that(initialSignalsBuyer2)
                .containsExactly(SIGNAL_OTHER_BUYER_OTHER_PACKAGE);

        mProtectedSignalsDao.deleteSignalsByPackage(ImmutableList.of(SIGNAL.getPackageName()));

        DBEncodedPayload postDeletionEncodedPayloadBuyer1 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL.getBuyer());
        DBEncodedPayload postDeletionEncodedPayloadBuyer2 =
                mEncodedPayloadDao.getEncodedPayload(SIGNAL_OTHER_BUYER_OTHER_PACKAGE.getBuyer());

        expect.withMessage("Encoded payload for BUYER_1 after deletion")
                .that(postDeletionEncodedPayloadBuyer1)
                .isNull();
        expectDBEncodedPayloadsAreEqual(
                expect, postDeletionEncodedPayloadBuyer2, expectedEncodedPayloadBuyer2);
    }

    @Test
    public void testHasSignalsFromBuyer() {
        // Assert no signals are present in an empty DB.
        boolean hasSignalsFromBuyer = mProtectedSignalsDao.hasSignalsFromBuyer(VALID_BUYER_1);
        assertWithMessage("hasSignalsFromBuyer").that(hasSignalsFromBuyer).isFalse();

        // Insert a signal and assert its presence in the DB.
        mProtectedSignalsDao.insertSignals(List.of(SIGNAL));
        hasSignalsFromBuyer = mProtectedSignalsDao.hasSignalsFromBuyer(VALID_BUYER_1);

        assertWithMessage("hasSignalsFromBuyer").that(hasSignalsFromBuyer).isTrue();

        // Delete all signals from the buyer and assert absence of signals.
        mProtectedSignalsDao.deleteByBuyers(List.of(VALID_BUYER_1));
        hasSignalsFromBuyer = mProtectedSignalsDao.hasSignalsFromBuyer(VALID_BUYER_1);

        assertWithMessage("hasSignalsFromBuyer").that(hasSignalsFromBuyer).isFalse();
    }
}
