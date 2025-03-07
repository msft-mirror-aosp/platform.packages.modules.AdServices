/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.service.measurement.reporting;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.android.adservices.common.DbTestUtil;
import com.android.adservices.data.measurement.AbstractDbIntegrationTest;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DbState;
import com.android.adservices.data.measurement.SQLDatastoreManager;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKeyManager;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Integration tests for {@link CountUniqueReportingJobHandler} */
@RunWith(Parameterized.class)
public class CountUniqueReportingJobHandlerIntegrationTest extends AbstractDbIntegrationTest {
    private final JSONObject mParam;
    private final AdServicesErrorLogger mErrorLogger;

    // The 'name' parameter is needed for the JUnit parameterized
    // test, although it's ostensibly unused by this constructor.
    public CountUniqueReportingJobHandlerIntegrationTest(
            DbState input,
            DbState output,
            Map<String, String> flagsMap,
            JSONObject param,
            String name) {
        super(input, output, flagsMap);
        mParam = param;
        mErrorLogger = mock(AdServicesErrorLogger.class);
        setCountUniqueFlags();
    }

    @Parameterized.Parameters(name = "{4}")
    public static Collection<Object[]> data() throws IOException, JSONException {
        InputStream inputStream =
                sContext.getAssets().open("count_unique_report_service_test.json");
        return AbstractDbIntegrationTest.getTestCasesFrom(
                inputStream, (testObj) -> ((JSONObject) testObj).getJSONObject("param"));
    }

    @Override
    protected void runActionToTest() throws DatastoreException {
        final Integer returnCode = (Integer) get("response_code");
        final String registration_origin = (String) get("registration_origin");

        AggregateEncryptionKeyManager mockKeyManager = mock(AggregateEncryptionKeyManager.class);
        ArgumentCaptor<Integer> captorNumberOfKeys = ArgumentCaptor.forClass(Integer.class);
        when(mockKeyManager.getAggregateEncryptionKeys(any(), captorNumberOfKeys.capture()))
                .thenAnswer(
                        invocation -> {
                            List<AggregateEncryptionKey> keys = new ArrayList<>();
                            for (int i = 0; i < captorNumberOfKeys.getValue(); i++) {
                                keys.add(AggregateCryptoFixture.getKey());
                            }
                            return keys;
                        });
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);

        CountUniqueReportingJobHandler spyReportingService =
                spy(
                        new CountUniqueReportingJobHandler(
                                datastoreManager, mockKeyManager, mFakeFlags, sContext));

        try {
            Mockito.doReturn(returnCode)
                    .when(spyReportingService)
                    .makeHttpPostRequest(Mockito.eq(Uri.parse(registration_origin)), any());
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        assertThat(spyReportingService.performScheduledPendingReports()).isTrue();
    }

    private Object get(String name) {
        try {
            return mParam.has(name) ? mParam.get(name) : null;
        } catch (JSONException e) {
            throw new IllegalArgumentException("error reading " + name);
        }
    }

    private void setCountUniqueFlags() {
        mFlagsMap.putIfAbsent(FlagsConstants.KEY_MEASUREMENT_ENABLE_COUNT_UNIQUE_SERVICE, "true");
    }
}
