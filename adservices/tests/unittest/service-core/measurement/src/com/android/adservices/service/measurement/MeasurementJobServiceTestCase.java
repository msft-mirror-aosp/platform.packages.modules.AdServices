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

import android.annotation.CallSuper;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;

import com.android.adservices.common.AdServicesJobServiceTestCase;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.spe.AdServicesJobServiceLogger;

import org.junit.Before;
import org.mockito.Mock;

public abstract class MeasurementJobServiceTestCase extends AdServicesJobServiceTestCase {

    // TODO(b/354932043): move 2 fields below (and check on assertValidTestCaseFixtures())
    // to superclass
    @Mock protected JobScheduler mMockJobScheduler;
    @Mock protected JobParameters mMockJobParameters;

    protected AdServicesJobServiceLogger mSpyLogger;

    @Mock protected DatastoreManager mMockDatastoreManager;

    @Before
    public void setMeasurementJobServiceTestCaseFixtures() {
        mSpyLogger = jobMocker.getSpiedAdServicesJobServiceLogger(mContext, mMockFlags);
    }

    // TODO(b/361555631): rename to testMeasurementJobServiceTestCaseFixtures() and annotate
    // it with @MetaTest
    @CallSuper
    @Override
    protected void assertValidTestCaseFixtures() throws Exception {
        super.assertValidTestCaseFixtures();

        assertTestClassHasNoFieldsFromSuperclass(
                MeasurementJobServiceTestCase.class,
                "mMockJobScheduler",
                "mMockJobParameters",
                "mSpyLogger",
                "mMockDatastoreManager");
        assertTestClassHasNoSuchField(
                "mMockJobParams", "should use (existing) mMockJobParameters instead");
    }
}
