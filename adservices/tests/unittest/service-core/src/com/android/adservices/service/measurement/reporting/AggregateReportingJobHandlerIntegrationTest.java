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

package com.android.adservices.service.measurement.reporting;

import android.net.Uri;

import com.android.adservices.data.measurement.AbstractDbIntegrationTest;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.DbState;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/** Integration tests for {@link AggregateReportingJobHandler} */
@RunWith(Parameterized.class)
public class AggregateReportingJobHandlerIntegrationTest extends AbstractDbIntegrationTest {
    private final JSONObject mParam;

    @Parameterized.Parameters(name = "{3}")
    public static Collection<Object[]> data() throws IOException, JSONException {
        InputStream inputStream = sContext.getAssets().open("aggregate_report_service_test.json");
        return AbstractDbIntegrationTest.getTestCasesFrom(
                inputStream, (testObj) -> ((JSONObject) testObj).getJSONObject("param"));
    }

    // The 'name' parameter is needed for the JUnit parameterized
    // test, although it's ostensibly unused by this constructor.
    public AggregateReportingJobHandlerIntegrationTest(
            DbState input, DbState output, JSONObject param, String name) {
        super(input, output);
        mParam = param;
    }

    public enum Action {
        SINGLE_REPORT,
        ALL_REPORTS,
        ALL_REPORTS_FOR_APP,
    }

    @Override
    public void runActionToTest() {
        final Integer returnCode = (Integer) get("responseCode");
        final String action = (String) get("action");

        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        AggregateReportingJobHandler spyReportingService =
                Mockito.spy(new AggregateReportingJobHandler(datastoreManager));
        try {
            Mockito.doReturn(returnCode)
                    .when(spyReportingService)
                    .makeHttpPostRequest(Mockito.any(), Mockito.any());
        } catch (IOException e) {
            Assert.fail();
        }

        switch (Action.valueOf(action)) {
            case ALL_REPORTS:
                final Long startValue = ((Number) get("start")).longValue();
                final Long endValue = ((Number) get("end")).longValue();
                Assert.assertTrue(
                        "Aggregate report failed.",
                        spyReportingService.performScheduledPendingReportsInWindow(
                                startValue, endValue));
                break;
            case ALL_REPORTS_FOR_APP:
                final Uri appName = Uri.parse((String) get("appName"));
                Assert.assertTrue(
                        "Aggregate report failed.",
                        spyReportingService.performAllPendingReportsForGivenApp(appName));
                break;
            case SINGLE_REPORT:
                final AggregateReportingJobHandler.PerformReportResult result =
                        AggregateReportingJobHandler.PerformReportResult.valueOf(
                                (String) get("result"));
                final String id = (String) get("id");
                Assert.assertEquals(
                        "Aggregate report failed.", result, spyReportingService.performReport(id));
                break;
        }
    }

    private Object get(String name) {
        try {
            return mParam.has(name) ? mParam.get(name) : null;
        } catch (JSONException e) {
            throw new IllegalArgumentException("error reading " + name);
        }
    }
}
