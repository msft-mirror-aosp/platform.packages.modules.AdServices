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

package com.android.adservices.data.measurement.deletion;

import android.annotation.NonNull;

import com.android.adservices.LogUtil;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Optional;

/**
 * Facilitates deletion of measurement data from the database, for e.g. deletion of sources,
 * triggers, reports, attributions.
 */
public class MeasurementDataDeleter {
    private static final int AGGREGATE_CONTRIBUTIONS_VALUE_MINIMUM_LIMIT = 0;

    private final DatastoreManager mDatastoreManager;

    @VisibleForTesting
    MeasurementDataDeleter(DatastoreManager datastoreManager) {
        mDatastoreManager = datastoreManager;
    }

    @VisibleForTesting
    void resetAggregateContributions(@NonNull List<String> triggerIdsToDelete) {
        Optional<List<AggregateReport>> aggregateReportsOpt =
                mDatastoreManager.runInTransactionWithResult(
                        (dao) -> dao.fetchMatchingAggregateReports(triggerIdsToDelete));

        if (!aggregateReportsOpt.isPresent()) {
            LogUtil.d("No aggregate reports found for provided triggers.");
            return;
        }

        aggregateReportsOpt
                .get()
                .forEach(
                        report ->
                                mDatastoreManager.runInTransaction(
                                        (dao) ->
                                                resetAggregateContributionsOfTriggersToDelete(
                                                        dao,
                                                        report,
                                                        dao.getSource(report.getSourceId()))));
    }

    private void resetAggregateContributionsOfTriggersToDelete(
            IMeasurementDao dao, AggregateReport report, Source source) throws DatastoreException {
        int aggregateHistogramContributionsSum =
                report.getAggregateAttributionData().getContributions().stream()
                        .mapToInt(AggregateHistogramContribution::getValue)
                        .sum();

        int newAggregateContributionsSum =
                Math.max(
                        (source.getAggregateContributions() - aggregateHistogramContributionsSum),
                        AGGREGATE_CONTRIBUTIONS_VALUE_MINIMUM_LIMIT);

        source.setAggregateContributions(newAggregateContributionsSum);

        // Update in the DB
        dao.updateSourceAggregateContributions(source);
    }
}
