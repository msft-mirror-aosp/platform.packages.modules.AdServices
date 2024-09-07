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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateDebugReportData;
import com.android.adservices.service.measurement.aggregation.AggregateDebugReportRecord;
import com.android.adservices.service.measurement.aggregation.AggregateDebugReporting;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.AggregateReport;

import com.google.android.libraries.mobiledatadownload.TimeSource;

import org.json.JSONException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Generates and schedules aggregate debug reports in the supported ad-tech side erroneous cases.
 */
public class AggregateDebugReportApi {
    static final String AGGREGATE_DEBUG_REPORT_API = "attribution-reporting-debug";
    // TODO(b/364768862): Bump this to 1.0 based on flexible contribution filtering flag
    private static final String API_VERSION = "0.1";
    private final Flags mFlags;
    private final TimeSource mTimeSource;

    public AggregateDebugReportApi(Flags flags, TimeSource timeSource) {
        mFlags = flags;
        mTimeSource = timeSource;
    }

    /**
     * Schedule debug reports for all source registration errors related, i.e. "source-*" debug
     * reports.
     */
    public void scheduleSourceRegistrationErrorDebugReport(
            Source source, DebugReportApi.Type type, IMeasurementDao measurementDao) {
        if (!mFlags.getMeasurementEnableAggregateDebugReporting()
                || source.getAggregateDebugReportingString() == null) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "Aggregate debug reporting on source disabled; "
                                    + "flag=%s; "
                                    + "aggregatable_debug_reporting available=%s",
                            mFlags.getMeasurementEnableAggregateDebugReporting(),
                            source.getAggregateDebugReportingString() != null);
            return;
        }

        try {
            AggregateDebugReporting sourceAggregateDebugReporting =
                    source.getAggregateDebugReportingObject();
            Optional<AggregateDebugReportData> firstMatchingAggregateReportData =
                    getFirstMatchingAggregateReportData(
                            sourceAggregateDebugReporting.getAggregateDebugReportDataList(), type);
            if (firstMatchingAggregateReportData.isEmpty()) {
                LoggerFactory.getMeasurementLogger()
                        .d("No matching debug data to generate aggregate debug report.");
                measurementDao.insertAggregateReport(generateNullAggregateReport(source));
                return;
            }

            AggregateDebugReportData debugReportData = firstMatchingAggregateReportData.get();

            if (debugReportData.getValue() + source.getAggregateContributions()
                    > source.getAggregateDebugReportingObject().getBudget()) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "Not generating aggregate debug report of type=%s because it "
                                        + "exceeds source budget",
                                type);
                measurementDao.insertAggregateReport(generateNullAggregateReport(source));
                return;
            }

            if (!isWithinRateLimits(
                    source.getRegistrationOrigin(),
                    source.getPublisher(),
                    source.getPublisherType(),
                    measurementDao,
                    (mTimeSource.currentTimeMillis()
                            - mFlags.getMeasurementAdrBudgetWindowLengthMillis()),
                    debugReportData.getValue())) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "Not generating aggregate debug report of type=%s ;rate limit"
                                        + " exceeded",
                                type);
                measurementDao.insertAggregateReport(generateNullAggregateReport(source));
                return;
            }

            AggregateHistogramContribution contributions =
                    createContributions(
                            debugReportData, sourceAggregateDebugReporting.getKeyPiece());

            AggregateReport aggregateReport = createAggregateReport(source, contributions);
            measurementDao.insertAggregateReport(createAggregateReport(source, contributions));
            source.setAggregateDebugContributions(
                    debugReportData.getValue() + source.getAggregateDebugReportContributions());
            measurementDao.updateSourceAggregateDebugContributions(source);
            measurementDao.insertAggregateDebugReportRecord(
                    createAggregateDebugReportRecord(
                            aggregateReport,
                            contributions.getValue(),
                            source.getRegistrant(),
                            source.getPublisher()));
        } catch (JSONException e) {
            // This isn't expected as at this point all data is valid.
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        } catch (DatastoreException e) {
            // This isn't expected as at this point all data is valid.
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        }
    }

    /**
     * Schedule debug reports for all trigger attribution errors related, i.e. "trigger-*", debug
     * reports, except {@link DebugReportApi.Type#TRIGGER_NO_MATCHING_SOURCE}.
     */
    public void scheduleTriggerAttributionErrorWithSourceDebugReport(
            Source source,
            Trigger trigger,
            DebugReportApi.Type type,
            IMeasurementDao measurementDao) {
        if (!mFlags.getMeasurementEnableAggregateDebugReporting()
                || source.getAggregateDebugReportingString() == null
                || trigger.getAggregateDebugReportingString() == null) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "Aggregate debug reporting on source disabled; "
                                    + "flag=%s; "
                                    + "source_aggregatable_debug_reporting available=%s;"
                                    + "trigger_aggregatable debug_reporting available=%s",
                            mFlags.getMeasurementEnableAggregateDebugReporting(),
                            source.getAggregateDebugReportingString() != null,
                            trigger.getAggregateDebugReportingString() != null);
            return;
        }

        try {
            Optional<AggregateDebugReportData> firstMatchingAggregateReportData =
                    getFirstMatchingAggregateReportData(
                            trigger.getAggregateDebugReportingObject()
                                    .getAggregateDebugReportDataList(),
                            type);
            if (firstMatchingAggregateReportData.isEmpty()) {
                LoggerFactory.getMeasurementLogger()
                        .d("No matching debug data to generate aggregate debug report.");
                measurementDao.insertAggregateReport(generateNullAggregateReport(source, trigger));
                return;
            }

            AggregateDebugReportData debugReportData = firstMatchingAggregateReportData.get();
            if (debugReportData.getValue() + source.getAggregateContributions()
                    > source.getAggregateDebugReportingObject().getBudget()) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "Not generating aggregate debug report %s because it exceeds source"
                                        + " budget");
                measurementDao.insertAggregateReport(generateNullAggregateReport(source, trigger));
                return;
            }

            if (!isWithinRateLimits(
                    trigger.getRegistrationOrigin(),
                    trigger.getAttributionDestination(),
                    trigger.getDestinationType(),
                    measurementDao,
                    (mTimeSource.currentTimeMillis()
                            - mFlags.getMeasurementAdrBudgetWindowLengthMillis()),
                    debugReportData.getValue())) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "Not generating aggregate debug report of type=%s ;rate limit"
                                        + " exceeded",
                                type);
                measurementDao.insertAggregateReport(generateNullAggregateReport(source, trigger));
                return;
            }

            AggregateHistogramContribution contributions =
                    createContributions(
                            debugReportData,
                            source.getAggregateDebugReportingObject()
                                    .getKeyPiece()
                                    .or(trigger.getAggregateDebugReportingObject().getKeyPiece()));

            AggregateReport aggregateReport = createAggregateReport(source, trigger, contributions);
            measurementDao.insertAggregateReport(aggregateReport);
            measurementDao.insertAggregateDebugReportRecord(
                    createAggregateDebugReportRecord(
                            aggregateReport,
                            contributions.getValue(),
                            trigger.getRegistrant(),
                            trigger.getAttributionDestination()));

            source.setAggregateDebugContributions(
                    debugReportData.getValue() + source.getAggregateDebugReportContributions());
            measurementDao.updateSourceAggregateDebugContributions(source);
        } catch (JSONException e) {
            // This isn't expected as at this point all data is valid.
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        } catch (DatastoreException e) {
            // This isn't expected as at this point all data is valid.
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        }
    }

    /**
     * Create aggregate debug report for {@link DebugReportApi.Type#TRIGGER_NO_MATCHING_SOURCE}
     * case. It's different from {@link #scheduleTriggerAttributionErrorWithSourceDebugReport}
     * because source isn't available hence contribution budget doesn't apply.
     */
    public void scheduleTriggerNoMatchingSourceDebugReport(
            Trigger trigger, IMeasurementDao measurementDao) {
        if (!mFlags.getMeasurementEnableAggregateDebugReporting()
                || trigger.getAggregateDebugReportingString() == null) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "Aggregate debug reporting on source disabled; "
                                    + "flag=%s; "
                                    + "aggregatable_debug_reporting available=%s",
                            mFlags.getMeasurementEnableAggregateDebugReporting(),
                            trigger.getAggregateDebugReportingString() != null);
            return;
        }

        try {
            Optional<AggregateDebugReportData> firstMatchingAggregateReportData =
                    getFirstMatchingAggregateReportData(
                            trigger.getAggregateDebugReportingObject()
                                    .getAggregateDebugReportDataList(),
                            DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE);
            if (firstMatchingAggregateReportData.isEmpty()) {
                LoggerFactory.getMeasurementLogger()
                        .d("No matching debug data to generate aggregate debug report.");
                measurementDao.insertAggregateReport(generateNullAggregateReport(trigger));
                return;
            }

            AggregateDebugReportData errorDebugReportingData =
                    firstMatchingAggregateReportData.get();
            if (!isWithinRateLimits(
                    trigger.getRegistrationOrigin(),
                    trigger.getAttributionDestination(),
                    trigger.getDestinationType(),
                    measurementDao,
                    (mTimeSource.currentTimeMillis()
                            - mFlags.getMeasurementAdrBudgetWindowLengthMillis()),
                    errorDebugReportingData.getValue())) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "Not generating aggregate debug report of type=%s ;rate limit"
                                        + " exceeded",
                                DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE);
                measurementDao.insertAggregateReport(generateNullAggregateReport(trigger));
                return;
            }

            AggregateHistogramContribution contributions =
                    createContributions(
                            errorDebugReportingData,
                            trigger.getAggregateDebugReportingObject().getKeyPiece());

            AggregateReport aggregateReport = createAggregateReport(trigger, contributions);
            measurementDao.insertAggregateReport(aggregateReport);
            measurementDao.insertAggregateDebugReportRecord(
                    createAggregateDebugReportRecord(
                            aggregateReport,
                            contributions.getValue(),
                            trigger.getRegistrant(),
                            trigger.getAttributionDestination()));
        } catch (JSONException e) {
            // This isn't expected as at this point all data is valid.
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        } catch (DatastoreException e) {
            // This isn't expected as at this point all data is valid.
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        }
    }

    private Uri getTriggerOrDefaultCoordinatorOrigin(
            AggregateDebugReporting aggregateDebugReportingObject) {
        return Optional.ofNullable(aggregateDebugReportingObject.getAggregationCoordinatorOrigin())
                .orElse(Uri.parse(mFlags.getMeasurementDefaultAggregationCoordinatorOrigin()));
    }

    private AggregateReport createAggregateReport(
            Trigger trigger, AggregateHistogramContribution contributions) throws JSONException {
        Uri coordinatorOrigin =
                getTriggerOrDefaultCoordinatorOrigin(trigger.getAggregateDebugReportingObject());
        return new AggregateReport.Builder()
                .setId(UUID.randomUUID().toString())
                .setAttributionDestination(trigger.getAttributionDestination())
                .setPublisher(trigger.getAttributionDestination())
                .setScheduledReportTime(trigger.getTriggerTime())
                .setEnrollmentId(trigger.getEnrollmentId())
                .setDebugCleartextPayload(
                        AggregateReport.generateDebugPayload(
                                getPaddedContributions(Collections.singletonList(contributions))))
                // We don't want to deliver regular aggregate reports
                .setStatus(AggregateReport.Status.MARKED_TO_DELETE)
                .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
                .setApiVersion(API_VERSION)
                .setSourceId(null)
                .setTriggerId(trigger.getId())
                .setRegistrationOrigin(trigger.getRegistrationOrigin())
                .setApi(AGGREGATE_DEBUG_REPORT_API)
                .setAggregationCoordinatorOrigin(coordinatorOrigin)
                .build();
    }

    private AggregateReport createAggregateReport(
            Source source, AggregateHistogramContribution contributions) throws JSONException {
        return new AggregateReport.Builder()
                .setId(UUID.randomUUID().toString())
                .setPublisher(source.getPublisher())
                .setAttributionDestination(getSourceDestinationToReport(source))
                .setScheduledReportTime(source.getEventTime())
                .setEnrollmentId(source.getEnrollmentId())
                .setDebugCleartextPayload(
                        AggregateReport.generateDebugPayload(
                                getPaddedContributions(Collections.singletonList(contributions))))
                // We don't want to deliver regular aggregate reports
                .setStatus(AggregateReport.Status.MARKED_TO_DELETE)
                .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
                .setApiVersion(API_VERSION)
                .setSourceId(source.getId())
                .setRegistrationOrigin(source.getRegistrationOrigin())
                .setApi(AGGREGATE_DEBUG_REPORT_API)
                .setAggregationCoordinatorOrigin(
                        Uri.parse(mFlags.getMeasurementDefaultAggregationCoordinatorOrigin()))
                .build();
    }

    private AggregateReport createAggregateReport(
            Source source, Trigger trigger, AggregateHistogramContribution contributions)
            throws JSONException {
        Uri coordinatorOrigin =
                getTriggerOrDefaultCoordinatorOrigin(trigger.getAggregateDebugReportingObject());
        return new AggregateReport.Builder()
                .setId(UUID.randomUUID().toString())
                .setPublisher(source.getPublisher())
                .setAttributionDestination(trigger.getAttributionDestination())
                .setScheduledReportTime(trigger.getTriggerTime())
                .setEnrollmentId(source.getEnrollmentId())
                .setDebugCleartextPayload(
                        AggregateReport.generateDebugPayload(
                                getPaddedContributions(Collections.singletonList(contributions))))
                // We don't want to deliver regular aggregate reports
                .setStatus(AggregateReport.Status.MARKED_TO_DELETE)
                .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
                .setApiVersion(API_VERSION)
                .setSourceId(source.getId())
                .setTriggerId(trigger.getId())
                .setRegistrationOrigin(trigger.getRegistrationOrigin())
                .setApi(AGGREGATE_DEBUG_REPORT_API)
                .setAggregationCoordinatorOrigin(coordinatorOrigin)
                .build();
    }

    private boolean isWithinRateLimits(
            Uri origin,
            Uri topLevelSite,
            int topLevelSiteType,
            IMeasurementDao measurementDao,
            long windowStartTime,
            int newContributions)
            throws DatastoreException {
        // Per origin per topLevelSite limits
        if ((measurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                                topLevelSite, topLevelSiteType, origin, windowStartTime)
                        + newContributions)
                > mFlags.getMeasurementAdrBudgetOriginXPublisherXWindow()) {
            return false;
        }

        // Per topLevelSite limits
        if ((measurementDao.sumAggregateDebugReportBudgetXPublisherXWindow(
                                topLevelSite, topLevelSiteType, windowStartTime)
                        + newContributions)
                > mFlags.getMeasurementAdrBudgetOriginXPublisherXWindow()) {
            return false;
        }

        return true;
    }

    private Optional<AggregateDebugReportData> getFirstMatchingAggregateReportData(
            Collection<AggregateDebugReportData> aggregateDebugReportDataList,
            DebugReportApi.Type reportType) {
        return aggregateDebugReportDataList.stream()
                .filter(
                        data ->
                                data.getReportType().contains(reportType.getValue())
                                        || data.getReportType()
                                                .contains(DebugReportApi.Type.DEFAULT.getValue()))
                .findFirst();
    }

    private Uri getSourceDestinationToReport(Source source) {
        return (source.getAppDestinations() == null || source.getAppDestinations().isEmpty())
                ? Collections.min(source.getWebDestinations())
                : Collections.min(source.getAppDestinations());
    }

    private static AggregateHistogramContribution createContributions(
            AggregateDebugReportData errorDebugReportingData, BigInteger keyPiece) {
        return new AggregateHistogramContribution.Builder()
                .setKey(keyPiece.or(errorDebugReportingData.getKeyPiece()))
                .setValue(errorDebugReportingData.getValue())
                .build();
    }

    private static AggregateDebugReportRecord createAggregateDebugReportRecord(
            AggregateReport aggregateReport,
            int contributionValue,
            Uri registrantApp,
            Uri topLevelSite) {
        return new AggregateDebugReportRecord.Builder(
                        aggregateReport.getScheduledReportTime(),
                        topLevelSite,
                        registrantApp,
                        aggregateReport.getRegistrationOrigin(),
                        contributionValue)
                .setSourceId(aggregateReport.getSourceId())
                .setTriggerId(aggregateReport.getTriggerId())
                .build();
    }

    private AggregateReport generateNullAggregateReport(Source source, Trigger trigger)
            throws JSONException {
        return generateBaseNullReportBuilder()
                .setRegistrationOrigin(trigger.getRegistrationOrigin())
                .setAttributionDestination(trigger.getAttributionDestination())
                .setScheduledReportTime(trigger.getTriggerTime())
                .setSourceId(source.getId())
                .setTriggerId(trigger.getId())
                .setAggregationCoordinatorOrigin(
                        getTriggerOrDefaultCoordinatorOrigin(
                                trigger.getAggregateDebugReportingObject()))
                .setPublisher(source.getPublisher())
                .build();
    }

    private AggregateReport generateNullAggregateReport(Source source) throws JSONException {
        return generateBaseNullReportBuilder()
                .setPublisher(source.getPublisher())
                .setRegistrationOrigin(source.getRegistrationOrigin())
                .setAttributionDestination(getSourceDestinationToReport(source))
                .setScheduledReportTime(source.getEventTime())
                .setSourceId(source.getId())
                .setAggregationCoordinatorOrigin(
                        Uri.parse(mFlags.getMeasurementDefaultAggregationCoordinatorOrigin()))
                .build();
    }

    private AggregateReport generateNullAggregateReport(Trigger trigger) throws JSONException {
        return new AggregateReport.Builder()
                .setRegistrationOrigin(trigger.getRegistrationOrigin())
                .setAttributionDestination(trigger.getAttributionDestination())
                .setScheduledReportTime(trigger.getTriggerTime())
                .setTriggerId(trigger.getId())
                .setAggregationCoordinatorOrigin(
                        getTriggerOrDefaultCoordinatorOrigin(
                                trigger.getAggregateDebugReportingObject()))
                .build();
    }

    private AggregateReport.Builder generateBaseNullReportBuilder() throws JSONException {
        String debugPayload =
                AggregateReport.generateDebugPayload(
                        getPaddedContributions(Collections.emptyList()));
        return new AggregateReport.Builder()
                .setId(UUID.randomUUID().toString())
                .setApiVersion(API_VERSION)
                // exclude by default
                .setSourceRegistrationTime(null)
                .setDebugCleartextPayload(debugPayload)
                .setIsFakeReport(true)
                .setTriggerContextId(null)
                .setApi(AGGREGATE_DEBUG_REPORT_API)
                .setAggregationCoordinatorOrigin(
                        Uri.parse(mFlags.getMeasurementDefaultAggregationCoordinatorOrigin()))
                .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
                .setStatus(AggregateReport.Status.MARKED_TO_DELETE);
    }

    private List<AggregateHistogramContribution> getPaddedContributions(
            List<AggregateHistogramContribution> contributions) {
        List<AggregateHistogramContribution> paddedContributions = new ArrayList<>(contributions);
        if (mFlags.getMeasurementEnableAggregatableReportPayloadPadding()) {
            IntStream.range(0, mFlags.getMeasurementMaxAggregateKeysPerSourceRegistration())
                    .forEach(i -> paddedContributions.add(createPaddingContribution()));
        }
        return paddedContributions;
    }

    private AggregateHistogramContribution createPaddingContribution() {
        return new AggregateHistogramContribution.Builder().setPaddingContribution().build();
    }
}
