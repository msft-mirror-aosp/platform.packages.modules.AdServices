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

package com.android.adservices.service.measurement.attribution;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.android.adservices.data.measurement.DatabaseE2ETest;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.DbState;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.measurement.Trigger;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * E2E tests for {@link AttributionJobHandler}
 */
@RunWith(Parameterized.class)
public class AttributionJobHandlerE2ETest extends DatabaseE2ETest {

    @Parameterized.Parameters(name = "{2}")
    public static Collection<Object[]> data() throws IOException, JSONException {
        InputStream inputStream = sContext.getAssets().open("attribution_service_test.json");
        return DatabaseE2ETest.getTestCasesFrom(inputStream, /*prepareAdditionalData=*/null);
    }

    // The 'name' parameter is needed for the JUnit parameterized
    // test, although it's ostensibly unused by this constructor.
    public AttributionJobHandlerE2ETest(DbState input, DbState output, String name) {
        super(input, output);
    }

    @Override
    public void runActionToTest() throws DatastoreException {
        DatastoreManager datastoreManager = spy(
                DatastoreManagerFactory.getDatastoreManager(sContext));
        // Mocking the randomized trigger data to always return the truth value.
        IMeasurementDao dao = spy(datastoreManager.getMeasurementDao());
        when(datastoreManager.getMeasurementDao()).thenReturn(dao);
        doAnswer((Answer<Trigger>) triggerInvocation -> {
            Trigger trigger = spy((Trigger) triggerInvocation.callRealMethod());
            doAnswer((Answer<Long>) triggerDataInvocation ->
                    trigger.getTruncatedTriggerData(
                            triggerDataInvocation.getArgument(0)))
                    .when(trigger)
                    .getRandomizedTriggerData(any());
            return trigger;
        }).when(dao).getTrigger(anyString());

        Assert.assertTrue("Attribution failed.",
                (new AttributionJobHandler(datastoreManager))
                        .performPendingAttributions());
    }
}
