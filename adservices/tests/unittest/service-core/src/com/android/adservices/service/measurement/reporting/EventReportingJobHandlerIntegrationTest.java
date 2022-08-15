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
import java.util.Objects;

/** Integration tests for {@link EventReportingJobHandler} */
@RunWith(Parameterized.class)
public class EventReportingJobHandlerIntegrationTest extends AbstractDbIntegrationTest {
    private final JSONObject mParam;

    @Parameterized.Parameters(name = "{3}")
    public static Collection<Object[]> data() throws IOException, JSONException {
        InputStream inputStream = sContext.getAssets().open("event_report_service_test.json");
        return AbstractDbIntegrationTest.getTestCasesFrom(
                inputStream, (testObj) -> ((JSONObject) testObj).getJSONObject("param"));
    }

    // The 'name' parameter is needed for the JUnit parameterized
    // test, although it's ostensibly unused by this constructor.
    public EventReportingJobHandlerIntegrationTest(
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
        EventReportingJobHandler spyReportingService =
                Mockito.spy(new EventReportingJobHandler(datastoreManager));
        try {
            Mockito.doReturn(returnCode)
                    .when(spyReportingService)
                    .makeHttpPostRequest(Mockito.any(), Mockito.any());
        } catch (IOException e) {
            Assert.fail();
        }

        switch (Action.valueOf(action)) {
            case ALL_REPORTS:
                final long startValue = ((Number) Objects.requireNonNull(get("start"))).longValue();
                final long endValue = ((Number) Objects.requireNonNull(get("end"))).longValue();
                Assert.assertTrue(
                        "Event report failed.",
                        spyReportingService.performScheduledPendingReportsInWindow(
                                startValue, endValue));
                break;
            case ALL_REPORTS_FOR_APP:
                final Uri appName = Uri.parse((String) get("appName"));
                Assert.assertTrue(
                        "Event report failed.",
                        spyReportingService.performAllPendingReportsForGivenApp(appName));
                break;
            case SINGLE_REPORT:
                final int result = ((Number) Objects.requireNonNull(get("result"))).intValue();
                final String id = (String) get("id");
                Assert.assertEquals(
                        "Event report failed.", result, spyReportingService.performReport(id));
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
