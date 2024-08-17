/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.job.JobParameters;
import android.app.job.JobScheduler;

import com.android.adservices.common.AdServicesJobServiceTestCase;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.spe.AdServicesJobServiceLogger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public abstract class MeasurementJobServiceTestCase extends AdServicesJobServiceTestCase {

    // TODO(b/354932043): move to superclass
    @Mock protected JobScheduler mMockJobScheduler;

    // TODO(b/354932043): move to superclass
    @Mock protected JobParameters mMockJobParameters;

    protected AdServicesJobServiceLogger mSpyLogger;

    @Mock protected DatastoreManager mMockDatastoreManager;

    @Before
    public void setMeasurementJobServiceTestCaseFixtures() {
        mSpyLogger = jobMocker.getSpiedAdServicesJobServiceLogger(mContext, mMockFlags);
    }

    @Test
    public final void testMeasurementJobServiceTestCaseFixtures() throws Exception {
        assertTestClassHasNoFieldsFromSuperclass(
                MeasurementJobServiceTestCase.class,
                "mMockFlags",
                "mMockJobScheduler",
                "mMockJobParameters",
                "mSpyLogger",
                "mMockDatastoreManager");
        assertTestClassHasNoSuchField(
                "mMockJobParams", "should use (existing) mMockJobParameters instead");
    }
}
