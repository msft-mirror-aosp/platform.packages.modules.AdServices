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

import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;

/**
 * Facilitates deletion of measurement data from the database, for e.g. deletion of sources,
 * triggers, reports, attributions.
 */
public class MeasurementDataDeleter {
    static final String ANDROID_APP_SCHEME = "android-app";
    private static final int AGGREGATE_CONTRIBUTIONS_VALUE_MINIMUM_LIMIT = 0;

    private final DatastoreManager mDatastoreManager;

    public MeasurementDataDeleter(DatastoreManager datastoreManager) {
        mDatastoreManager = datastoreManager;
    }

    /**
     * Deletes all measurement data owned by a registrant and optionally providing an origin uri
     * and/or a range of dates.
     *
     * @param deletionParam contains registrant, time range, sites to consider for deletion
     * @return true if deletion was successful, false otherwise
     */
    public boolean delete(@NonNull DeletionParam deletionParam) {
        return mDatastoreManager.runInTransaction(
                (dao) -> {
                    List<String> sourceIds =
                            dao.fetchMatchingSources(
                                    getRegistrant(deletionParam.getAppPackageName()),
                                    deletionParam.getStart(),
                                    deletionParam.getEnd(),
                                    deletionParam.getOriginUris(),
                                    deletionParam.getDomainUris(),
                                    deletionParam.getMatchBehavior());
                    List<String> triggerIds =
                            dao.fetchMatchingTriggers(
                                    getRegistrant(deletionParam.getAppPackageName()),
                                    deletionParam.getStart(),
                                    deletionParam.getEnd(),
                                    deletionParam.getOriginUris(),
                                    deletionParam.getDomainUris(),
                                    deletionParam.getMatchBehavior());

                    // Rest aggregate contributions and dedup keys on sources for triggers to be
                    // deleted.
                    List<AggregateReport> aggregateReports =
                            dao.fetchMatchingAggregateReports(sourceIds, triggerIds);
                    resetAggregateContributions(dao, aggregateReports);

                    List<EventReport> eventReports =
                            dao.fetchMatchingEventReports(sourceIds, triggerIds);
                    resetDedupKeys(dao, eventReports);

                    // Delete sources and triggers, that'll take care of deleting related reports
                    // and attributions
                    if (deletionParam.getDeletionMode() == DeletionRequest.DELETION_MODE_ALL) {
                        dao.deleteSources(sourceIds);
                        dao.deleteTriggers(triggerIds);
                        return;
                    }

                    // Mark reports for deletion for DELETION_MODE_EXCLUDE_INTERNAL_DATA
                    for (EventReport eventReport : eventReports) {
                        dao.markEventReportStatus(
                                eventReport.getId(), EventReport.Status.MARKED_TO_DELETE);
                    }

                    for (AggregateReport aggregateReport : aggregateReports) {
                        dao.markAggregateReportStatus(
                                aggregateReport.getId(), AggregateReport.Status.MARKED_TO_DELETE);
                    }

                    // Finally mark sources and triggers for deletion
                    dao.updateSourceStatus(sourceIds, Source.Status.MARKED_TO_DELETE);
                    dao.updateTriggerStatus(triggerIds, Trigger.Status.MARKED_TO_DELETE);
                });
    }

    @VisibleForTesting
    void resetAggregateContributions(
            @NonNull IMeasurementDao dao, @NonNull List<AggregateReport> aggregateReports)
            throws DatastoreException {
        for (AggregateReport report : aggregateReports) {
            if (report.getSourceId() == null) {
                LogUtil.e("SourceId is null on event report.");
                return;
            }

            Source source = dao.getSource(report.getSourceId());
            int aggregateHistogramContributionsSum =
                    report.getAggregateAttributionData().getContributions().stream()
                            .mapToInt(AggregateHistogramContribution::getValue)
                            .sum();

            int newAggregateContributionsSum =
                    Math.max(
                            (source.getAggregateContributions()
                                    - aggregateHistogramContributionsSum),
                            AGGREGATE_CONTRIBUTIONS_VALUE_MINIMUM_LIMIT);

            source.setAggregateContributions(newAggregateContributionsSum);

            // Update in the DB
            dao.updateSourceAggregateContributions(source);
        }
    }

    @VisibleForTesting
    void resetDedupKeys(@NonNull IMeasurementDao dao, @NonNull List<EventReport> eventReports)
            throws DatastoreException {
        for (EventReport report : eventReports) {
            if (report.getSourceId() == null) {
                LogUtil.e("SourceId on the event report is null.");
                return;
            }

            Source source = dao.getSource(report.getSourceId());
            source.getDedupKeys().remove(report.getTriggerDedupKey());
            dao.updateSourceDedupKeys(source);
        }
    }

    private Uri getRegistrant(String packageName) {
        return Uri.parse(ANDROID_APP_SCHEME + "://" + packageName);
    }
}
