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

import static com.android.adservices.service.measurement.reporting.AggregateDebugReportApi.AGGREGATE_DEBUG_REPORT_API;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.aggregation.AggregateDebugReportRecord;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.AggregateReport;

import com.google.android.libraries.mobiledatadownload.TimeSource;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@RunWith(MockitoJUnitRunner.class)
public class AggregateDebugReportApiTest {
    private static final String AGGREGATE_API_VERSION = "0.1";

    AggregateDebugReportApi mAggregateDebugReportApi;
    @Mock private Flags mFlags;
    @Mock private IMeasurementDao mMeasurementDao;
    @Mock private TimeSource mTimeSource;

    @Before
    public void setup() {
        mAggregateDebugReportApi = new AggregateDebugReportApi(mFlags, mTimeSource);

        when(mFlags.getMeasurementEnableAggregateDebugReporting()).thenReturn(true);
        when(mFlags.getMeasurementAdrBudgetOriginXPublisherXWindow())
                .thenReturn(Flags.MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW);
        when(mFlags.getMeasurementAdrBudgetPublisherXWindow())
                .thenReturn(Flags.MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW);
        when(mFlags.getMeasurementAdrBudgetWindowLengthMillis())
                .thenReturn(Flags.MEASUREMENT_ADR_BUDGET_WINDOW_LENGTH_MILLIS);
        when(mTimeSource.currentTimeMillis()).thenReturn(System.currentTimeMillis());
        when(mFlags.getMeasurementDefaultAggregationCoordinatorOrigin())
                .thenReturn(Flags.MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN);
        when(mFlags.getMeasurementEnableAggregatableReportPayloadPadding()).thenReturn(true);
        when(mFlags.getMeasurementMaxAggregateKeysPerSourceRegistration())
                .thenReturn(Flags.MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION);
    }

    @Test
    public void scheduleTriggerReport_typeDefinedExplicitly_generatesTriggerDebugReport()
            throws DatastoreException, JSONException {
        // Setup
        when(mMeasurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong()))
                .thenReturn(100);
        when(mMeasurementDao.sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong()))
                .thenReturn(100);
        Trigger trigger = TriggerFixture.getValidTrigger();
        Source source = SourceFixture.getValidSource();

        // Execution
        mAggregateDebugReportApi.scheduleTriggerAttributionErrorWithSourceDebugReport(
                source,
                trigger,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED,
                mMeasurementDao);

        // Verification
        AggregateReport expectedAggregateReport =
                createAggregateReportBuilder(
                                source,
                                trigger,
                                new AggregateHistogramContribution.Builder()
                                        // 0x444 or 0x222 or 0x100 = 1894 (decimal)
                                        .setKey(new BigInteger("1894"))
                                        .setValue(444)
                                        .build())
                        .build();
        ArgumentCaptor<AggregateReport> aggregateReportArgumentCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportArgumentCaptor.capture());
        assertThat(aggregateReportArgumentCaptor.getValue()).isEqualTo(expectedAggregateReport);

        AggregateDebugReportRecord expectedRecord =
                createAggregateDebugReportRecord(
                        expectedAggregateReport,
                        444,
                        trigger.getRegistrant(),
                        trigger.getAttributionDestination());
        verify(mMeasurementDao).insertAggregateDebugReportRecord(eq(expectedRecord));

        ArgumentCaptor<Source> sourceArgumentCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao)
                .updateSourceAggregateDebugContributions(sourceArgumentCaptor.capture());
        // 100 + 444
        assertThat(sourceArgumentCaptor.getValue().getAggregateDebugReportContributions())
                .isEqualTo(544);
    }

    @Test
    public void scheduleTriggerReport_triggerWithoutCoordinator_generatesReportWithDefCoordinator()
            throws DatastoreException, JSONException {
        // Setup
        when(mMeasurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong()))
                .thenReturn(100);
        when(mMeasurementDao.sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong()))
                .thenReturn(100);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setAggregateDebugReportingString(
                                "{\"key_piece\":\"0x222\","
                                        + "\"debug_data\":["
                                        + "{"
                                        + "\"types\": [\"trigger-aggregate-insufficient-budget\", "
                                        + "\"trigger-aggregate-deduplicated\"],"
                                        + "\"key_piece\": \"0x333\","
                                        + "\"value\": 333"
                                        + "},"
                                        + "{"
                                        + "\"types\": [\"trigger-aggregate-report-window-passed\", "
                                        + "\"trigger-event-low-priority\"],"
                                        + "\"key_piece\": \"0x444\","
                                        + "\"value\": 444"
                                        + "},"
                                        + "{"
                                        + "\"types\": [\"default\"],"
                                        + "\"key_piece\": \"0x555\","
                                        + "\"value\": 555"
                                        + "}"
                                        + "]}")
                        .build();
        Source source = SourceFixture.getValidSource();

        // Execution
        mAggregateDebugReportApi.scheduleTriggerAttributionErrorWithSourceDebugReport(
                source,
                trigger,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED,
                mMeasurementDao);

        // Verification
        AggregateReport expectedAggregateReport =
                createAggregateReportBuilder(
                                source,
                                trigger,
                                new AggregateHistogramContribution.Builder()
                                        // 0x444 or 0x222 or 0x100 = 1894 (decimal)
                                        .setKey(new BigInteger("1894"))
                                        .setValue(444)
                                        .build())
                        .setAggregationCoordinatorOrigin(
                                Uri.parse(Flags.MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN))
                        .build();
        ArgumentCaptor<AggregateReport> aggregateReportArgumentCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportArgumentCaptor.capture());
        assertThat(aggregateReportArgumentCaptor.getValue()).isEqualTo(expectedAggregateReport);

        AggregateDebugReportRecord expectedRecord =
                createAggregateDebugReportRecord(
                        expectedAggregateReport,
                        444,
                        trigger.getRegistrant(),
                        trigger.getAttributionDestination());
        verify(mMeasurementDao).insertAggregateDebugReportRecord(eq(expectedRecord));

        ArgumentCaptor<Source> sourceArgumentCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao)
                .updateSourceAggregateDebugContributions(sourceArgumentCaptor.capture());
        // 100 + 444
        assertThat(sourceArgumentCaptor.getValue().getAggregateDebugReportContributions())
                .isEqualTo(544);
    }

    @Test
    public void scheduleTriggerReport_fallbackToDefault_generatesTriggerDebugReport()
            throws DatastoreException, JSONException {
        // Setup
        when(mMeasurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong()))
                .thenReturn(100);
        when(mMeasurementDao.sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong()))
                .thenReturn(100);
        Trigger trigger = TriggerFixture.getValidTrigger();
        Source source = SourceFixture.getValidSource();

        // Execution
        mAggregateDebugReportApi.scheduleTriggerAttributionErrorWithSourceDebugReport(
                source,
                trigger,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED,
                mMeasurementDao);

        // Verification
        AggregateReport expectedAggregateReport =
                createAggregateReportBuilder(
                                source,
                                trigger,
                                new AggregateHistogramContribution.Builder()
                                        // 0x444 or 0x222 or 0x100 = 0x766 = 1894 (decimal)
                                        .setKey(new BigInteger("1894"))
                                        .setValue(444)
                                        .build())
                        .build();
        ArgumentCaptor<AggregateReport> aggregateReportArgumentCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportArgumentCaptor.capture());
        assertThat(aggregateReportArgumentCaptor.getValue()).isEqualTo(expectedAggregateReport);

        AggregateDebugReportRecord expectedRecord =
                createAggregateDebugReportRecord(
                        expectedAggregateReport,
                        444,
                        trigger.getRegistrant(),
                        trigger.getAttributionDestination());
        ArgumentCaptor<AggregateDebugReportRecord> recordArgumentCaptor =
                ArgumentCaptor.forClass(AggregateDebugReportRecord.class);
        verify(mMeasurementDao).insertAggregateDebugReportRecord(recordArgumentCaptor.capture());
        assertThat(recordArgumentCaptor.getValue()).isEqualTo(expectedRecord);

        ArgumentCaptor<Source> sourceArgumentCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao)
                .updateSourceAggregateDebugContributions(sourceArgumentCaptor.capture());
        // 100 + 444
        assertThat(sourceArgumentCaptor.getValue().getAggregateDebugReportContributions())
                .isEqualTo(544);
    }

    @Test
    public void scheduleTriggerReport_perPublisherXOriginLimitExceeded_doesNotGenerateReport()
            throws DatastoreException, JSONException {
        // Setup
        when(mMeasurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong()))
                .thenReturn(Flags.MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW);
        Source source = SourceFixture.getValidSource();
        Trigger trigger = TriggerFixture.getValidTrigger();
        when(mFlags.getMeasurementEnableAggregateDebugReporting()).thenReturn(true);

        // Execution
        mAggregateDebugReportApi.scheduleTriggerAttributionErrorWithSourceDebugReport(
                source,
                trigger,
                DebugReportApi.Type.TRIGGER_AGGREGATE_DEDUPLICATED,
                mMeasurementDao);

        // Verification
        verify(mMeasurementDao)
                .insertAggregateReport(eq(generateNullAggregateReport(source, trigger)));
        verify(mMeasurementDao, never()).insertAggregateDebugReportRecord(any());
        verify(mMeasurementDao, never()).updateSourceAggregateDebugContributions(any());
        verify(mMeasurementDao)
                .sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong());
        verify(mMeasurementDao, never())
                .sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong());
    }

    @Test
    public void scheduleTriggerReport_perPublisherLimitExceeded_doesNotGenerateReport()
            throws DatastoreException, JSONException {
        // Setup
        when(mMeasurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong()))
                .thenReturn(100);
        when(mMeasurementDao.sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong()))
                .thenReturn(Flags.MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW);
        Source source = SourceFixture.getValidSource();
        Trigger trigger = TriggerFixture.getValidTrigger();
        when(mFlags.getMeasurementEnableAggregateDebugReporting()).thenReturn(true);

        // Execution
        mAggregateDebugReportApi.scheduleTriggerAttributionErrorWithSourceDebugReport(
                source, trigger, DebugReportApi.Type.TRIGGER_EVENT_DEDUPLICATED, mMeasurementDao);

        // Verification
        verify(mMeasurementDao)
                .insertAggregateReport(eq(generateNullAggregateReport(source, trigger)));
        verify(mMeasurementDao, never()).insertAggregateDebugReportRecord(any());
        verify(mMeasurementDao, never()).updateSourceAggregateDebugContributions(any());
        verify(mMeasurementDao)
                .sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong());
        verify(mMeasurementDao)
                .sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong());
    }

    @Test
    public void scheduleSourceRegErrorDebugReport_typeDefinedExplicitly_generatesSourceDebugReport()
            throws DatastoreException, JSONException {
        // Setup
        when(mMeasurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong()))
                .thenReturn(100);
        when(mMeasurementDao.sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong()))
                .thenReturn(100);
        Source source = SourceFixture.getValidSource();

        // Execution
        mAggregateDebugReportApi.scheduleSourceRegistrationErrorDebugReport(
                source, DebugReportApi.Type.SOURCE_DESTINATION_LIMIT, mMeasurementDao);

        // Verification
        AggregateReport expectedAggregateReport =
                createAggregateReportBuilder(
                                source,
                                new AggregateHistogramContribution.Builder()
                                        // 0x100 or 0x111  = 273 (decimal)
                                        .setKey(new BigInteger("273"))
                                        .setValue(111)
                                        .build())
                        .build();
        ArgumentCaptor<AggregateReport> aggregateReportArgumentCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportArgumentCaptor.capture());
        assertThat(aggregateReportArgumentCaptor.getValue()).isEqualTo(expectedAggregateReport);
        AggregateDebugReportRecord expectedRecord =
                createAggregateDebugReportRecord(
                        expectedAggregateReport,
                        111,
                        source.getRegistrant(),
                        source.getPublisher());
        verify(mMeasurementDao).insertAggregateDebugReportRecord(eq(expectedRecord));
        ArgumentCaptor<Source> sourceArgumentCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao)
                .updateSourceAggregateDebugContributions(sourceArgumentCaptor.capture());
        // 100 + 211
        assertThat(sourceArgumentCaptor.getValue().getAggregateDebugReportContributions())
                .isEqualTo(211);
    }

    @Test
    public void scheduleSourceRegErrorDebugReport_fallbackToDefault_generatesSourceDebugReport()
            throws DatastoreException, JSONException {
        // Setup
        when(mMeasurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong()))
                .thenReturn(100);
        when(mMeasurementDao.sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong()))
                .thenReturn(100);
        Source source = SourceFixture.getValidSource();

        // Execution
        mAggregateDebugReportApi.scheduleSourceRegistrationErrorDebugReport(
                source, DebugReportApi.Type.SOURCE_UNKNOWN_ERROR, mMeasurementDao);

        // Verification
        AggregateReport expectedAggregateReport =
                createAggregateReportBuilder(
                                source,
                                new AggregateHistogramContribution.Builder()
                                        // 0x100 or 0x222  = 0x322 (hex) = 273 (decimal)
                                        .setKey(new BigInteger("802"))
                                        .setValue(222)
                                        .build())
                        .build();
        ArgumentCaptor<AggregateReport> aggregateReportArgumentCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportArgumentCaptor.capture());
        assertThat(aggregateReportArgumentCaptor.getValue()).isEqualTo(expectedAggregateReport);

        AggregateDebugReportRecord expectedRecord =
                createAggregateDebugReportRecord(
                        expectedAggregateReport,
                        222,
                        source.getRegistrant(),
                        source.getPublisher());
        verify(mMeasurementDao).insertAggregateDebugReportRecord(eq(expectedRecord));
        ArgumentCaptor<Source> sourceArgumentCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao)
                .updateSourceAggregateDebugContributions(sourceArgumentCaptor.capture());
        // 100 + 222
        assertThat(sourceArgumentCaptor.getValue().getAggregateDebugReportContributions())
                .isEqualTo(322);
    }

    @Test
    public void scheduleSourceRegErrorDebugReport_flagDisabled_doesNotGenerateReport()
            throws DatastoreException {
        // Setup
        Source source = SourceFixture.getValidSource();
        when(mFlags.getMeasurementEnableAggregateDebugReporting()).thenReturn(false);

        // Execution
        mAggregateDebugReportApi.scheduleSourceRegistrationErrorDebugReport(
                source, DebugReportApi.Type.SOURCE_UNKNOWN_ERROR, mMeasurementDao);

        // Verification
        verify(mMeasurementDao, never()).insertAggregateReport(any());
        verify(mMeasurementDao, never()).insertAggregateDebugReportRecord(any());
        verify(mMeasurementDao, never()).updateSourceAggregateDebugContributions(any());
        verify(mMeasurementDao, never())
                .sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong());
        verify(mMeasurementDao, never())
                .sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong());
    }

    @Test
    public void scheduleSourceRegErrorDebugReport_missingAdrOnSource_doesNotGenerateReport()
            throws DatastoreException {
        // Setup
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setAggregateDebugReportingString(null)
                        .build();
        when(mFlags.getMeasurementEnableAggregateDebugReporting()).thenReturn(true);

        // Execution
        mAggregateDebugReportApi.scheduleSourceRegistrationErrorDebugReport(
                source, DebugReportApi.Type.SOURCE_UNKNOWN_ERROR, mMeasurementDao);

        // Verification
        verify(mMeasurementDao, never()).insertAggregateReport(any());
        verify(mMeasurementDao, never()).insertAggregateDebugReportRecord(any());
        verify(mMeasurementDao, never()).updateSourceAggregateDebugContributions(any());
        verify(mMeasurementDao, never())
                .sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong());
        verify(mMeasurementDao, never())
                .sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong());
    }

    @Test
    public void scheduleSourceRegErrorReport_perPubXOriginLimitExceeded_doesNotGenerateReport()
            throws DatastoreException, JSONException {
        // Setup
        when(mMeasurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong()))
                .thenReturn(Flags.MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW);
        Source source = SourceFixture.getValidSource();
        when(mFlags.getMeasurementEnableAggregateDebugReporting()).thenReturn(true);

        // Execution
        mAggregateDebugReportApi.scheduleSourceRegistrationErrorDebugReport(
                source, DebugReportApi.Type.SOURCE_UNKNOWN_ERROR, mMeasurementDao);

        // Verification
        verify(mMeasurementDao).insertAggregateReport(eq(generateNullAggregateReport(source)));
        verify(mMeasurementDao, never()).insertAggregateDebugReportRecord(any());
        verify(mMeasurementDao, never()).updateSourceAggregateDebugContributions(any());
        verify(mMeasurementDao)
                .sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong());
        verify(mMeasurementDao, never())
                .sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong());
    }

    @Test
    public void scheduleSourceRegErrorDebugReport_perPubLimitExceeded_doesNotGenerateReport()
            throws DatastoreException, JSONException {
        // Setup
        when(mMeasurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong()))
                .thenReturn(100);
        when(mMeasurementDao.sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong()))
                .thenReturn(Flags.MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW);
        Source source = SourceFixture.getValidSource();
        when(mFlags.getMeasurementEnableAggregateDebugReporting()).thenReturn(true);

        // Execution
        mAggregateDebugReportApi.scheduleSourceRegistrationErrorDebugReport(
                source, DebugReportApi.Type.SOURCE_UNKNOWN_ERROR, mMeasurementDao);

        // Verification
        ArgumentCaptor<AggregateReport> aggregateReportArgumentCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportArgumentCaptor.capture());
        assertThat(aggregateReportArgumentCaptor.getValue())
                .isEqualTo(generateNullAggregateReport(source));
        verify(mMeasurementDao, never()).insertAggregateDebugReportRecord(any());
        verify(mMeasurementDao, never()).updateSourceAggregateDebugContributions(any());
        verify(mMeasurementDao)
                .sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong());
        verify(mMeasurementDao)
                .sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong());
    }

    @Test
    public void scheduleTriggerNoMatchingReport_typeDefinedExplicitly_generatesSourceDebugReport()
            throws DatastoreException, JSONException {
        // Setup
        when(mMeasurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong()))
                .thenReturn(100);
        when(mMeasurementDao.sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong()))
                .thenReturn(100);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setAggregateDebugReportingString(
                                "{\"key_piece\":\"0x222\",\"debug_data\":[{\"types\":"
                                    + " [\"trigger-no-matching-source\", "
                                    + "\"trigger-aggregate-deduplicated\"],\"key_piece\":"
                                    + " \"0x333\",\"value\": 333}],"
                                    + "\"aggregation_coordinator_origin\":\"https://aws.example\"}")
                        .build();

        // Execution
        mAggregateDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao);

        // Verification
        AggregateReport expectedAggregateReport =
                createAggregateReportBuilder(
                                trigger,
                                new AggregateHistogramContribution.Builder()
                                        // 0x222 or 0x333  = 0x333 (hex) = 819 (decimal)
                                        .setKey(new BigInteger("819"))
                                        .setValue(333)
                                        .build())
                        .build();
        ArgumentCaptor<AggregateReport> aggregateReportArgumentCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportArgumentCaptor.capture());
        assertThat(aggregateReportArgumentCaptor.getValue()).isEqualTo(expectedAggregateReport);
        AggregateDebugReportRecord expectedRecord =
                createAggregateDebugReportRecord(
                        expectedAggregateReport,
                        333,
                        trigger.getRegistrant(),
                        trigger.getAttributionDestination());
        verify(mMeasurementDao).insertAggregateDebugReportRecord(eq(expectedRecord));
    }

    @Test
    public void scheduleTriggerNoMatchingReport_fallbackToDefault_generatesSourceDebugReport()
            throws DatastoreException, JSONException {
        // Setup
        when(mMeasurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong()))
                .thenReturn(100);
        when(mMeasurementDao.sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong()))
                .thenReturn(100);
        Trigger trigger = TriggerFixture.getValidTrigger();

        // Execution
        mAggregateDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao);

        // Verification
        AggregateReport expectedAggregateReport =
                createAggregateReportBuilder(
                                trigger,
                                new AggregateHistogramContribution.Builder()
                                        // 0x555 or 0x333  = 0x777 (hex) = 1911 (decimal)
                                        .setKey(new BigInteger("1911"))
                                        .setValue(555)
                                        .build())
                        .build();
        ArgumentCaptor<AggregateReport> aggregateReportArgumentCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportArgumentCaptor.capture());
        assertThat(aggregateReportArgumentCaptor.getValue()).isEqualTo(expectedAggregateReport);
        AggregateDebugReportRecord expectedRecord =
                createAggregateDebugReportRecord(
                        expectedAggregateReport,
                        555,
                        trigger.getRegistrant(),
                        trigger.getAttributionDestination());

        ArgumentCaptor<AggregateDebugReportRecord> recordArgumentCaptor =
                ArgumentCaptor.forClass(AggregateDebugReportRecord.class);
        verify(mMeasurementDao).insertAggregateDebugReportRecord(recordArgumentCaptor.capture());
        assertThat(recordArgumentCaptor.getValue()).isEqualTo(expectedRecord);
    }

    @Test
    public void scheduleTriggerNoMatchingReport_perPubXOriginLimitExceeded_doesNotGenerateReport()
            throws DatastoreException, JSONException {
        // Setup
        when(mMeasurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong()))
                .thenReturn(Flags.MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW);
        Trigger trigger = TriggerFixture.getValidTrigger();
        when(mFlags.getMeasurementEnableAggregateDebugReporting()).thenReturn(true);

        // Execution
        mAggregateDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao);

        // Verification
        verify(mMeasurementDao).insertAggregateReport(eq(generateNullAggregateReport(trigger)));
        verify(mMeasurementDao, never()).insertAggregateDebugReportRecord(any());
        verify(mMeasurementDao, never()).updateSourceAggregateDebugContributions(any());
        verify(mMeasurementDao)
                .sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong());
        verify(mMeasurementDao, never())
                .sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong());
    }

    @Test
    public void scheduleTriggerNoMatchingReport_perPubLimitExceeded_doesNotGenerateReport()
            throws DatastoreException, JSONException {
        // Setup
        when(mMeasurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong()))
                .thenReturn(100);
        when(mMeasurementDao.sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong()))
                .thenReturn(Flags.MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW);
        Trigger trigger = TriggerFixture.getValidTrigger();
        when(mFlags.getMeasurementEnableAggregateDebugReporting()).thenReturn(true);

        // Execution
        mAggregateDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao);

        // Verification
        verify(mMeasurementDao).insertAggregateReport(eq(generateNullAggregateReport(trigger)));
        verify(mMeasurementDao, never()).insertAggregateDebugReportRecord(any());
        verify(mMeasurementDao, never()).updateSourceAggregateDebugContributions(any());
        verify(mMeasurementDao)
                .sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        any(Uri.class), anyInt(), any(Uri.class), anyLong());
        verify(mMeasurementDao)
                .sumAggregateDebugReportBudgetXPublisherXWindow(
                        any(Uri.class), anyInt(), anyLong());
    }

    private AggregateReport.Builder createAggregateReportBuilder(
            Trigger trigger, AggregateHistogramContribution contributions) throws JSONException {
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
                .setApiVersion(AGGREGATE_API_VERSION)
                .setSourceId(null)
                .setTriggerId(trigger.getId())
                .setRegistrationOrigin(trigger.getRegistrationOrigin())
                .setApi(AGGREGATE_DEBUG_REPORT_API)
                .setAggregationCoordinatorOrigin(
                        trigger.getAggregateDebugReportingObject()
                                .getAggregationCoordinatorOrigin());
    }

    private AggregateReport.Builder createAggregateReportBuilder(
            Source source, AggregateHistogramContribution contributions) throws JSONException {
        return new AggregateReport.Builder()
                .setId(UUID.randomUUID().toString())
                .setPublisher(source.getPublisher())
                .setAttributionDestination(source.getAppDestinations().get(0))
                .setScheduledReportTime(source.getEventTime())
                .setEnrollmentId(source.getEnrollmentId())
                .setDebugCleartextPayload(
                        AggregateReport.generateDebugPayload(
                                getPaddedContributions(Collections.singletonList(contributions))))
                // We don't want to deliver regular aggregate reports
                .setStatus(AggregateReport.Status.MARKED_TO_DELETE)
                .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
                .setApiVersion(AGGREGATE_API_VERSION)
                .setSourceId(source.getId())
                .setRegistrationOrigin(source.getRegistrationOrigin())
                .setApi(AGGREGATE_DEBUG_REPORT_API)
                .setAggregationCoordinatorOrigin(
                        Uri.parse(Flags.MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN));
    }

    private AggregateReport.Builder createAggregateReportBuilder(
            Source source, Trigger trigger, AggregateHistogramContribution contributions)
            throws JSONException {
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
                .setApiVersion(AGGREGATE_API_VERSION)
                .setSourceId(source.getId())
                .setTriggerId(trigger.getId())
                .setRegistrationOrigin(trigger.getRegistrationOrigin())
                .setApi(AGGREGATE_DEBUG_REPORT_API)
                .setAggregationCoordinatorOrigin(
                        trigger.getAggregateDebugReportingObject()
                                .getAggregationCoordinatorOrigin());
    }

    private List<AggregateHistogramContribution> getPaddedContributions(
            List<AggregateHistogramContribution> contributions) {
        List<AggregateHistogramContribution> paddedContributions = new ArrayList<>(contributions);
        IntStream.range(0, Flags.MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION)
                .forEach(i -> paddedContributions.add(createPaddingContribution()));
        return paddedContributions;
    }

    private AggregateHistogramContribution createPaddingContribution() {
        return new AggregateHistogramContribution.Builder().setPaddingContribution().build();
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
                        trigger.getAggregateDebugReportingObject()
                                .getAggregationCoordinatorOrigin())
                .setPublisher(source.getPublisher())
                .build();
    }

    private AggregateReport generateNullAggregateReport(Source source) throws JSONException {
        return generateBaseNullReportBuilder()
                .setPublisher(source.getPublisher())
                .setRegistrationOrigin(source.getRegistrationOrigin())
                .setAttributionDestination(source.getAppDestinations().get(0))
                .setScheduledReportTime(source.getEventTime())
                .setSourceId(source.getId())
                .setAggregationCoordinatorOrigin(
                        Uri.parse(Flags.MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN))
                .build();
    }

    private AggregateReport generateNullAggregateReport(Trigger trigger) throws JSONException {
        return new AggregateReport.Builder()
                .setRegistrationOrigin(trigger.getRegistrationOrigin())
                .setAttributionDestination(trigger.getAttributionDestination())
                .setScheduledReportTime(trigger.getTriggerTime())
                .setTriggerId(trigger.getId())
                .setAggregationCoordinatorOrigin(
                        trigger.getAggregateDebugReportingObject()
                                .getAggregationCoordinatorOrigin())
                .build();
    }

    private AggregateReport.Builder generateBaseNullReportBuilder() throws JSONException {
        String debugPayload =
                AggregateReport.generateDebugPayload(
                        getPaddedContributions(Collections.emptyList()));
        return new AggregateReport.Builder()
                .setId(UUID.randomUUID().toString())
                .setApiVersion(AGGREGATE_API_VERSION)
                // exclude by default
                .setSourceRegistrationTime(null)
                .setDebugCleartextPayload(debugPayload)
                .setIsFakeReport(true)
                .setTriggerContextId(null)
                .setApi(AGGREGATE_DEBUG_REPORT_API)
                .setAggregationCoordinatorOrigin(
                        Uri.parse(Flags.MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN))
                .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
                .setStatus(AggregateReport.Status.MARKED_TO_DELETE);
    }
}
