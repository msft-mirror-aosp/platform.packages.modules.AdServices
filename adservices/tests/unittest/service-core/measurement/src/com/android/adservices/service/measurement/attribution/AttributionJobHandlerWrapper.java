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

import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.spy;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.actions.UriConfig;
import com.android.adservices.service.measurement.noising.SourceNoiseHandler;
import com.android.adservices.service.measurement.reporting.AggregateDebugReportApi;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.reporting.EventReportWindowCalcDelegate;
import com.android.adservices.service.stats.AdServicesLogger;

import org.mockito.Mockito;

import java.util.List;

/**
 * A wrapper class to expose a constructor for AttributionJobHandler in testing.
 */
public class AttributionJobHandlerWrapper {
    private final AttributionJobHandler mAttributionJobHandler;

    public AttributionJobHandlerWrapper(
            DatastoreManager datastoreManager,
            Flags flags,
            DebugReportApi debugReportApi,
            EventReportWindowCalcDelegate eventReportWindowCalcDelegate,
            SourceNoiseHandler sourceNoiseHandler,
            AdServicesLogger logger,
            AggregateDebugReportApi adrApi) {
        this.mAttributionJobHandler =
                spy(
                        new AttributionJobHandler(
                                datastoreManager,
                                flags,
                                debugReportApi,
                                eventReportWindowCalcDelegate,
                                sourceNoiseHandler,
                                logger,
                                new XnaSourceCreator(flags),
                                adrApi,
                                ApplicationProvider.getApplicationContext()));
    }

    /** Perform attribution. */
    public boolean performPendingAttributions() {
        return AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED
                == mAttributionJobHandler.performPendingAttributions();
    }

    /** Prepare noising related to aggregate reports. */
    public void prepareAggregateReportNoising(UriConfig uriConfig) {
        List<Long> nullAggregatableReportsDays = uriConfig.getNullAggregatableReportsDays();
        if (nullAggregatableReportsDays == null) {
            return;
        }
        if (nullAggregatableReportsDays.contains(0L)) {
            Mockito.doReturn(-1.0D).when(mAttributionJobHandler).getRandom();
        }
        Mockito.doReturn(nullAggregatableReportsDays)
                .when(mAttributionJobHandler)
                        .getNullAggregatableReportsDays(anyLong(), anyFloat());
    }
}
