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

package com.android.adservices.data.measurement;

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.errorlogging.AdServicesErrorLogger;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.json.JSONException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * Tests for {@link MeasurementDao} app deletion that affect the database.
 */
@RunWith(Parameterized.class)
public class DeleteExpiredIntegrationTest extends AbstractDbIntegrationTest {
    private final boolean mRetryLimitingEnabled;

    @Parameterized.Parameters(name = "{3}")
    public static Collection<Object[]> data() throws IOException, JSONException {
        InputStream inputStream = sContext.getAssets().open(
                "measurement_delete_expired_test.json");
        return AbstractDbIntegrationTest.getTestCasesFrom(
                inputStream, (testObj) -> testObj.getBoolean("retry-limit"));
    }

    // The 'name' parameter is needed for the JUnit parameterized
    // test, although it's ostensibly unused by this constructor.
    public DeleteExpiredIntegrationTest(
            DbState input, DbState output, boolean retryLimitingEnabled, String name) {
        super(input, output);

        this.mRetryLimitingEnabled = retryLimitingEnabled;
    }

    public void runActionToTest() {
        Flags mockFlags = Mockito.mock(Flags.class);
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(Flags.MEASUREMENT_REPORT_RETRY_LIMIT)
                .when(mockFlags)
                .getMeasurementReportingRetryLimit();
        ExtendedMockito.doReturn(mRetryLimitingEnabled)
                .when(mockFlags)
                .getMeasurementReportingRetryLimitEnabled();

        long earliestValidInsertion =
                System.currentTimeMillis() - Flags.MEASUREMENT_DATA_EXPIRY_WINDOW_MS;
        int retryLimit = Flags.MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST;

        AdServicesErrorLogger errorLogger = Mockito.mock(AdServicesErrorLogger.class);
        new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), errorLogger)
                .runInTransaction(
                        dao -> dao.deleteExpiredRecords(earliestValidInsertion, retryLimit));
    }
}
