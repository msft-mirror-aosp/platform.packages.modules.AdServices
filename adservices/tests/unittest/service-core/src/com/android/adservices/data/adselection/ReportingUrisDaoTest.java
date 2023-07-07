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

package com.android.adservices.data.adselection;

import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.mockito.MockitoSession;

public class ReportingUrisDaoTest {
    private ReportingUrisDao mReportingUrisDao;
    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(PackageManagerCompatUtils.class)
                        .initMocks(this)
                        .startMocking();
        mReportingUrisDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionServerDatabase.class)
                        .build()
                        .reportingUrisDao();
    }

    @After
    public void cleanup() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testInsertAndRead_validInput_success() {
        long adSelectionId = 12345678L;
        Uri sellerReportingUri = Uri.parse("seller.reporting.uri");
        Uri buyerReportingUri = Uri.parse("buyer.reporting.uri");
        DBReportingUris dbReportingUris =
                DBReportingUris.create(adSelectionId, sellerReportingUri, buyerReportingUri);

        mReportingUrisDao.insertReportingUris(dbReportingUris);
        Assert.assertEquals(dbReportingUris, mReportingUrisDao.getReportingUris(adSelectionId));
    }

    @Test
    public void testInsertAndRead_nullValues_failure() {
        long adSelectionId = 12345678L;
        ThrowingRunnable runnable = () -> DBReportingUris.create(adSelectionId, null, null);

        Assert.assertThrows(NullPointerException.class, runnable);
    }
}
