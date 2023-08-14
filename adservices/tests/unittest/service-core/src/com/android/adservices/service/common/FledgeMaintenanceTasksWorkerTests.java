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

package com.android.adservices.service.common;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;

import android.adservices.common.CommonFixture;
import android.adservices.common.KeyedFrequencyCap;
import android.content.pm.PackageManager;

import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;

import java.time.Instant;

public class FledgeMaintenanceTasksWorkerTests {
    private static final Flags TEST_FLAGS = FlagsFactory.getFlagsForTest();
    @Mock private AdSelectionEntryDao mAdSelectionEntryDaoMock;
    @Mock private FrequencyCapDao mFrequencyCapDaoMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private PackageManager mPackageManagerMock;
    private FledgeMaintenanceTasksWorker mFledgeMaintenanceTasksWorker;
    private MockitoSession mMockitoSession;

    @Before
    public void setup() {
        mMockitoSession = ExtendedMockito.mockitoSession().initMocks(this).startMocking();

        mFledgeMaintenanceTasksWorker =
                new FledgeMaintenanceTasksWorker(
                        TEST_FLAGS,
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        mEnrollmentDaoMock,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI);
    }

    @After
    public void teardown() {
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testClearExpiredAdSelectionData_removesExpiredData() throws Exception {
        mFledgeMaintenanceTasksWorker.clearExpiredAdSelectionData();

        Instant expectedExpirationTime =
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(
                        TEST_FLAGS.getAdSelectionExpirationWindowS());

        verify(mAdSelectionEntryDaoMock).removeExpiredAdSelection(eq(expectedExpirationTime));
        verify(mAdSelectionEntryDaoMock).removeExpiredBuyerDecisionLogic();
        verify(mAdSelectionEntryDaoMock).removeExpiredRegisteredAdInteractions();
        verifyNoMoreInteractions(mAdSelectionEntryDaoMock);
    }

    @Test
    public void testClearExpiredFrequencyCapHistogramData_adFilteringEnabled_doesMaintenance() {
        final class FlagsWithAdFilteringFeatureEnabled implements Flags {
            @Override
            public boolean getFledgeAdSelectionFilteringEnabled() {
                return true;
            }

            @Override
            public boolean getDisableFledgeEnrollmentCheck() {
                return false;
            }
        }

        FledgeMaintenanceTasksWorker worker =
                new FledgeMaintenanceTasksWorker(
                        new FlagsWithAdFilteringFeatureEnabled(),
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        mEnrollmentDaoMock,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI);

        worker.clearInvalidFrequencyCapHistogramData(mPackageManagerMock);

        Instant expectedExpirationTime =
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(
                        KeyedFrequencyCap.MAX_INTERVAL.getSeconds());

        verify(mFrequencyCapDaoMock).deleteAllExpiredHistogramData(eq(expectedExpirationTime));
        verify(mFrequencyCapDaoMock)
                .deleteAllDisallowedBuyerHistogramData(any(EnrollmentDao.class));
        verify(mFrequencyCapDaoMock)
                .deleteAllDisallowedSourceAppHistogramData(
                        any(PackageManager.class), any(Flags.class));
        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void
            testClearExpiredFrequencyCapHistogramData_enrollmentDisabled_skipsBuyerMaintenance() {
        final class FlagsWithAdFilteringFeatureEnabled implements Flags {
            @Override
            public boolean getFledgeAdSelectionFilteringEnabled() {
                return true;
            }

            @Override
            public boolean getDisableFledgeEnrollmentCheck() {
                return true;
            }
        }

        FledgeMaintenanceTasksWorker worker =
                new FledgeMaintenanceTasksWorker(
                        new FlagsWithAdFilteringFeatureEnabled(),
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        mEnrollmentDaoMock,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI);

        worker.clearInvalidFrequencyCapHistogramData(mPackageManagerMock);

        Instant expectedExpirationTime =
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(
                        KeyedFrequencyCap.MAX_INTERVAL.getSeconds());

        verify(mFrequencyCapDaoMock).deleteAllExpiredHistogramData(eq(expectedExpirationTime));
        verify(mFrequencyCapDaoMock)
                .deleteAllDisallowedSourceAppHistogramData(
                        any(PackageManager.class), any(Flags.class));
        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testClearExpiredFrequencyCapHistogramData_adFilteringDisabled_skipsMaintenance() {
        final class FlagsWithAdFilteringFeatureDisabled implements Flags {
            @Override
            public boolean getFledgeAdSelectionFilteringEnabled() {
                return false;
            }

            @Override
            public boolean getDisableFledgeEnrollmentCheck() {
                return false;
            }
        }

        FledgeMaintenanceTasksWorker worker =
                new FledgeMaintenanceTasksWorker(
                        new FlagsWithAdFilteringFeatureDisabled(),
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        mEnrollmentDaoMock,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI);

        worker.clearInvalidFrequencyCapHistogramData(mPackageManagerMock);

        verifyNoMoreInteractions(mFrequencyCapDaoMock, mPackageManagerMock);
    }
}
