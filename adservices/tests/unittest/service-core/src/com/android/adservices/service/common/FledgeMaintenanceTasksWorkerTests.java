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
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;

import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;

public class FledgeMaintenanceTasksWorkerTests {
    private static final Flags TEST_FLAGS = FlagsFactory.getFlagsForTest();
    @Mock private AdSelectionEntryDao mAdSelectionEntryDaoMock;
    private FledgeMaintenanceTasksWorker mFledgeMaintenanceTasksWorker;
    private MockitoSession mMockitoSession;

    @Before
    public void setup() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .initMocks(this)
                        .startMocking();
        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        mFledgeMaintenanceTasksWorker = new FledgeMaintenanceTasksWorker(mAdSelectionEntryDaoMock);
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

        verify(mAdSelectionEntryDaoMock).removeExpiredAdSelection(any());
        verify(mAdSelectionEntryDaoMock).removeExpiredBuyerDecisionLogic();
        verify(mAdSelectionEntryDaoMock).removeExpiredRegisteredAdInteractions();
        verifyNoMoreInteractions(mAdSelectionEntryDaoMock);
    }
}
