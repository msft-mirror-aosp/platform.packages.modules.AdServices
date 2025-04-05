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

package com.android.adservices.service.stats;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerFactory;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerImpl;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerNoLoggingImpl;

import org.junit.Test;

public final class UpdateSignalsProcessReportedLoggerFactoryTest extends AdServicesUnitTestCase {

    private UpdateSignalsProcessReportedLoggerFactory mUpdateSignalsProcessReportedLoggerFactory =
            new UpdateSignalsProcessReportedLoggerFactory(/* pasProductMetricsV1Enabled= */ true);

    @Test
    public void testGetInstance_returnTheImplInstance() {
        expect.withMessage("Call getInstance with pasProductMetricsV1Enabled=true")
                .that(mUpdateSignalsProcessReportedLoggerFactory.getLoggerInstance())
                .isInstanceOf(UpdateSignalsProcessReportedLoggerImpl.class);
    }

    @Test
    public void testGetInstance_returnNoLoggingInstance() {
        mUpdateSignalsProcessReportedLoggerFactory =
                new UpdateSignalsProcessReportedLoggerFactory(
                        /* pasProductMetricsV1Enabled= */ false);
        expect.withMessage("Call getInstance with pasProductMetricsV1Enabled=false")
                .that(mUpdateSignalsProcessReportedLoggerFactory.getLoggerInstance())
                .isInstanceOf(UpdateSignalsProcessReportedLoggerNoLoggingImpl.class);
    }
}
