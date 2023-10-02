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

package com.android.adservices.service.signals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.CommonFixture;
import android.content.pm.PackageManager;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Clock;

public class SignalsMaintenanceTasksWorkerTest {

    @Rule public final MockitoRule rule = MockitoJUnit.rule();
    @Mock private ProtectedSignalsDao mProtectedSignalsDaoMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private Flags mFlagsMock;
    @Mock private Clock mClockMock;
    @Mock private PackageManager mPackageManagerMock;

    SignalsMaintenanceTasksWorker mSignalsMaintenanceTasksWorker;

    @Before
    public void setup() {
        mSignalsMaintenanceTasksWorker =
                new SignalsMaintenanceTasksWorker(
                        mFlagsMock,
                        mProtectedSignalsDaoMock,
                        mEnrollmentDaoMock,
                        mClockMock,
                        mPackageManagerMock);
        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW);
    }

    @Test
    public void testClearInvalidSignalsEnrollmentEnabled() throws Exception {
        when(mFlagsMock.getDisableFledgeEnrollmentCheck()).thenReturn(false);

        mSignalsMaintenanceTasksWorker.clearInvalidSignals();

        verify(mProtectedSignalsDaoMock)
                .deleteSignalsBeforeTime(
                        CommonFixture.FIXED_NOW.minusSeconds(ProtectedSignal.EXPIRATION_SECONDS));
        verify(mFlagsMock).getDisableFledgeEnrollmentCheck();
        verify(mProtectedSignalsDaoMock).deleteDisallowedBuyerSignals(any());
        verify(mProtectedSignalsDaoMock).deleteAllDisallowedPackageSignals(any(), any());
    }

    @Test
    public void testClearInvalidSignalsEnrollmentDisabled() throws Exception {
        when(mFlagsMock.getDisableFledgeEnrollmentCheck()).thenReturn(true);

        mSignalsMaintenanceTasksWorker.clearInvalidSignals();

        verify(mProtectedSignalsDaoMock)
                .deleteSignalsBeforeTime(
                        CommonFixture.FIXED_NOW.minusSeconds(ProtectedSignal.EXPIRATION_SECONDS));
        verify(mFlagsMock).getDisableFledgeEnrollmentCheck();
    }
}
