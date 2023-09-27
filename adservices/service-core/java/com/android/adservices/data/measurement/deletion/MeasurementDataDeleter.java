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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_WIPEOUT;

import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.ReportSpecUtil;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.WipeoutStatus;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementWipeoutStats;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Facilitates deletion of measurement data from the database, for e.g. deletion of sources,
 * triggers, reports, attributions.
 */
public class MeasurementDataDeleter {
    static final String ANDROID_APP_SCHEME = "android-app";
    private static final int AGGREGATE_CONTRIBUTIONS_VALUE_MINIMUM_LIMIT = 0;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getMeasurementLogger();

    private final DatastoreManager mDatastoreManager;
    private final Flags mFlags;
    private final AdServicesLogger mLogger;

    public MeasurementDataDeleter(DatastoreManager datastoreManager, Flags flags) {
        this(datastoreManager, flags, AdServicesLoggerImpl.getInstance());
    }

    @VisibleForTesting
    public MeasurementDataDeleter(
            DatastoreManager datastoreManager, Flags flags, AdServicesLogger logger) {
        mDatastoreManager = datastoreManager;
        mFlags = flags;
        mLogger = logger;
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
                    List<String> asyncRegistrationIds =
                            dao.fetchMatchingAsyncRegistrations(
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
                    resetAggregateReportDedupKeys(dao, aggregateReports);
                    List<EventReport> eventReports;
                    if (mFlags.getMeasurementFlexibleEventReportingApiEnabled()) {
                        /*
                         Because some triggers may not be stored in the event report table in
                         the flexible event report API, we must extract additional related
                         triggers from the source table.
                        */
                        List<String> extendedSourceIds =
                                dao.fetchMatchingSourcesFlexibleEventApi(triggerIds);

                        extendedSourceIds.addAll(sourceIds);
                        // deduplication of the source ids
                        extendedSourceIds = new ArrayList<>(new HashSet<>(extendedSourceIds));

                        List<EventReport> mixedEventReportsFromSourceAndTrigger =
                                dao.fetchMatchingEventReports(extendedSourceIds, triggerIds);
                        eventReports =
                                filterReportFlexibleEventsAPI(
                                        dao, sourceIds, mixedEventReportsFromSourceAndTrigger);
                    } else {
                        eventReports = dao.fetchMatchingEventReports(sourceIds, triggerIds);
                        // If any of the source has flexible event report API previously turned
                        // on, need to check additional trigger and delete them
                        for (String sourceId : sourceIds) {
                            Source source = dao.getSource(sourceId);
                            if (!mFlags.getMeasurementFlexibleEventReportingApiEnabled()
                                    || source.getTriggerSpecs() == null
                                    || source.getTriggerSpecs().isEmpty()) {
                                continue;
                            }
                            try {
                                source.buildFlexibleEventReportApi();
                                triggerIds.addAll(
                                        source.getFlexEventReportSpec().getAllTriggerIds());
                            } catch (JSONException e) {
                                sLogger.e(
                                        "MeasurementDataDeleter::delete unable to build event "
                                                + "report spec");
                            }
                        }
                    }

                    resetDedupKeys(dao, eventReports);

                    dao.deleteAsyncRegistrations(asyncRegistrationIds);

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

                    // Log wipeout event triggered by request (from the delete registrations API)
                    WipeoutStatus wipeoutStatus = new WipeoutStatus();
                    wipeoutStatus.setWipeoutType(
                            WipeoutStatus.WipeoutType.DELETE_REGISTRATIONS_API);
                    logWipeoutStats(
                            wipeoutStatus,
                            getRegistrant(deletionParam.getAppPackageName()).toString());
                });
    }

    @VisibleForTesting
    void resetAggregateContributions(
            @NonNull IMeasurementDao dao, @NonNull List<AggregateReport> aggregateReports)
            throws DatastoreException {
        for (AggregateReport report : aggregateReports) {
            if (report.getSourceId() == null) {
                sLogger.d("SourceId is null on event report.");
                return;
            }

            Source source = dao.getSource(report.getSourceId());
            int aggregateHistogramContributionsSum =
                    report.extractAggregateHistogramContributions().stream()
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
                sLogger.d("resetDedupKeys: SourceId on the event report is null.");
                continue;
            }

            Source source = dao.getSource(report.getSourceId());

            if (mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()) {
                UnsignedLong dedupKey = report.getTriggerDedupKey();
                if (dedupKey != null) {
                    try {
                        source.buildAttributedTriggers();
                        source.getAttributedTriggers().removeIf(attributedTrigger ->
                                dedupKey.equals(attributedTrigger.getDedupKey())
                                        && Objects.equals(
                                                report.getTriggerId(),
                                                attributedTrigger.getTriggerId()));
                        // Flex API takes care of trigger removal from the attributed trigger list
                        // without the need for changes here.
                        if (!(mFlags.getMeasurementFlexibleEventReportingApiEnabled()
                                        && source.getTriggerSpecs() != null
                                        && !source.getTriggerSpecs().isEmpty())) {
                            dao.updateSourceAttributedTriggers(
                                    source.getId(), source.attributedTriggersToJson());
                        }
                    } catch (JSONException e) {
                        sLogger.e(e, "resetDedupKeys: failed to build attributed triggers.");
                    }
                }
            } else {
                source.getEventReportDedupKeys().remove(report.getTriggerDedupKey());
                dao.updateSourceEventReportDedupKeys(source);
            }
        }
    }

    void resetAggregateReportDedupKeys(
            @NonNull IMeasurementDao dao, @NonNull List<AggregateReport> aggregateReports)
            throws DatastoreException {
        for (AggregateReport report : aggregateReports) {
            if (report.getSourceId() == null) {
                sLogger.d("SourceId on the aggregate report is null.");
                continue;
            }

            Source source = dao.getSource(report.getSourceId());
            if (report.getDedupKey() == null) {
                continue;
            }
            source.getAggregateReportDedupKeys().remove(report.getDedupKey());
            dao.updateSourceAggregateReportDedupKeys(source);
        }
    }

    private Uri getRegistrant(String packageName) {
        return Uri.parse(ANDROID_APP_SCHEME + "://" + packageName);
    }

    private void logWipeoutStats(WipeoutStatus wipeoutStatus, String sourceRegistrant) {
        mLogger.logMeasurementWipeoutStats(
                new MeasurementWipeoutStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_WIPEOUT)
                        .setWipeoutType(wipeoutStatus.getWipeoutType().getValue())
                        .setSourceRegistrant(sourceRegistrant)
                        .build());
    }

    List<EventReport> filterReportFlexibleEventsAPI(
            IMeasurementDao dao, List<String> sourceIds, List<EventReport> eventReports)
            throws DatastoreException {
        List<EventReport> eventReportsToDelete = new ArrayList<>();
        for (EventReport eventReport : eventReports) {
            String sourceId = eventReport.getSourceId();
            if (sourceIds.contains(sourceId)) {
                /*
                Deletion can occur in multiple cases. If the deletion request is for the source,
                then all event reports related to this source should be deleted. No additional
                logic is required.
                 */
                eventReportsToDelete.add(eventReport);
            } else {
                Source source = dao.getSource(sourceId);
                try {
                    source.buildFlexibleEventReportApi();
                } catch (JSONException e) {
                    sLogger.d("Unable to read JSON from Database");
                    eventReportsToDelete.add(eventReport);
                    continue;
                }
                if (source.getFlexEventReportSpec() == null) {
                    // Not using flexible event API at deletion time. No need to do the filtering
                    // logic.
                    eventReportsToDelete.add(eventReport);
                } else {
                    int numDecrementalBucket =
                            ReportSpecUtil.numDecrementingBucket(
                                    source.getFlexEventReportSpec(), eventReport);
                    if (!source.getFlexEventReportSpec().deleteFromAttributedValue(eventReport)) {
                        /*
                        This indicates the case where the trigger record in the source table has
                        been deleted already. This case will occur when deleting a trigger that has
                        generated more than one event report. For example, if a summary bucket is
                         [1, 5, 10] and a trigger with value 7 generates two event reports, then
                         when this trigger is deleted, two event reports should be deleted. When
                         deleting the first event report, the trigger record will be deleted.
                         Therefore, when deleting the second event report, the trigger will not
                         be found in the source table, and we still need to delete the event report.
                         */
                        eventReportsToDelete.add(eventReport);
                        continue;
                    }
                    if (numDecrementalBucket > 0) {
                        eventReportsToDelete.add(eventReport);
                    }
                    dao.updateSourceAttributedTriggers(
                            source.getId(),
                            source.attributedTriggersToJsonFlexApi());
                    ReportSpecUtil.resetSummaryBucketForAllEventReport(source, dao);
                }
            }
        }
        return eventReportsToDelete;
    }
}
