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

package com.android.adservices.service.measurement;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.service.measurement.actions.Action;
import com.android.adservices.service.measurement.actions.ReportObjects;

import org.json.JSONException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Collection;

/**
 * End-to-end test from source and trigger registration to attribution reporting, using mocked HTTP
 * requests.
 */
@RunWith(Parameterized.class)
public class E2EDenoisedMockTest extends E2EMockTest {
    private static final String TEST_DIR_NAME = "msmt_e2e_tests";

    @Parameterized.Parameters(name = "{2}")
    public static Collection<Object[]> getData() throws IOException, JSONException {
        return data(TEST_DIR_NAME);
    }

    public E2EDenoisedMockTest(Collection<Action> actions, ReportObjects expectedOutput,
            String name) throws DatastoreException {
        super(actions, expectedOutput, name);
        mAttributionHelper = TestObjectProvider.getAttributionJobHandler(
                TestObjectProvider.Type.DENOISED, sDatastoreManager);
        mMeasurementImpl = TestObjectProvider.getMeasurementImpl(TestObjectProvider.Type.DENOISED,
                sDatastoreManager, mSourceFetcher, mTriggerFetcher);
    }
}
