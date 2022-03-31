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

import com.android.adservices.data.measurement.DatabaseE2ETest;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.DbState;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
    public void runActionToTest() {
        Assert.assertTrue("Attribution failed.",
                (new AttributionJobHandler(
                        DatastoreManagerFactory.getDatastoreManager(sContext)))
                        .performPendingAttributions());
    }
}
