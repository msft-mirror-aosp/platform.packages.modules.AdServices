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

package com.android.cobalt.registry;

import static com.android.cobalt.registry.RegistryMerger.mergeRegistries;

import static com.google.cobalt.MetricDefinition.MetricType.OCCURRENCE;
import static com.google.cobalt.ReportDefinition.PrivacyMechanism.DE_IDENTIFICATION;
import static com.google.cobalt.ReportDefinition.PrivacyMechanism.SHUFFLED_DIFFERENTIAL_PRIVACY;
import static com.google.cobalt.ReportDefinition.ReportType.FLEETWIDE_OCCURRENCE_COUNTS;
import static com.google.cobalt.ReportDefinition.ReportType.STRING_COUNTS;
import static com.google.cobalt.ReportDefinition.ReportingInterval.DAYS_1;
import static com.google.cobalt.SystemProfileSelectionPolicy.REPORT_ALL;

import static org.junit.Assert.assertEquals;

import com.android.adservices.common.AdServicesUnitTestCase;

import com.google.cobalt.CobaltRegistry;
import com.google.cobalt.CustomerConfig;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.Metadata;
import com.google.cobalt.ProjectConfig;
import com.google.cobalt.ReleaseStage;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.ReportType;

import org.junit.Test;

public final class RegistryMergerTest extends AdServicesUnitTestCase {
    private static final int CUSTOMER_ID = 1;
    private static final int PROJECT_ID = 2;
    private static final int METRIC_ID = 3;
    private static final int REPORT_ID = 4;

    private static final CobaltRegistry EMPTY_REGISTRY = CobaltRegistry.getDefaultInstance();
    private static final CustomerConfig EMPTY_CUSTOMER = CustomerConfig.getDefaultInstance();
    private static final ProjectConfig EMPTY_PROJECT = ProjectConfig.getDefaultInstance();
    private static final ReportDefinition INVALID_REPORT =
            ReportDefinition.newBuilder()
                    .setReportType(STRING_COUNTS)
                    .setPrivacyMechanism(SHUFFLED_DIFFERENTIAL_PRIVACY)
                    .build();

    @Test
    public void testMergeRegistries_baseEmpty_emptyReturned() {
        CobaltRegistry mergeInRegistry = makeRegistry(CUSTOMER_ID, makeProject(PROJECT_ID));

        assertEquals(EMPTY_REGISTRY, mergeRegistries(EMPTY_REGISTRY, mergeInRegistry));
    }

    @Test
    public void testMergeRegistries_mergeInMissingCustomer_baseReturned() {
        CobaltRegistry baseRegistry = makeRegistry(CUSTOMER_ID, makeProject(PROJECT_ID));

        assertEquals(baseRegistry, mergeRegistries(baseRegistry, EMPTY_REGISTRY));
    }

    @Test
    public void testMergeRegistries_baseMissingProject_baseReturned() {
        CobaltRegistry baseRegistry = makeRegistry(CUSTOMER_ID, EMPTY_PROJECT);
        CobaltRegistry mergeInRegistry = makeRegistry(CUSTOMER_ID, makeProject(PROJECT_ID));

        assertEquals(baseRegistry, mergeRegistries(baseRegistry, mergeInRegistry));
    }

    @Test
    public void testMergeRegistries_mergeInMissingProject_baseReturned() {
        CobaltRegistry baseRegistry = makeRegistry(CUSTOMER_ID, makeProject(PROJECT_ID));
        CobaltRegistry mergeInRegistry = makeRegistry(CUSTOMER_ID, EMPTY_PROJECT);

        assertEquals(baseRegistry, mergeRegistries(baseRegistry, mergeInRegistry));
    }

    @Test
    public void testMergeRegistries_customerIdsNotEquals_baseReturned() {
        CobaltRegistry baseRegistry = makeRegistry(101, makeProject(PROJECT_ID));
        CobaltRegistry mergeInRegistry = makeRegistry(202, makeProject(PROJECT_ID));

        assertEquals(baseRegistry, mergeRegistries(baseRegistry, mergeInRegistry));
    }

    @Test
    public void testMergeRegistries_projectIdsNotEquals_baseReturned() {
        CobaltRegistry baseRegistry = makeRegistry(CUSTOMER_ID, makeProject(101));
        CobaltRegistry mergeInRegistry = makeRegistry(CUSTOMER_ID, makeProject(202));

        assertEquals(baseRegistry, mergeRegistries(baseRegistry, mergeInRegistry));
    }

    @Test
    public void testMergeRegistries_deletedMetricsListsMerged() {
        CobaltRegistry baseRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(PROJECT_ID).toBuilder().addDeletedMetricIds(1).build());
        CobaltRegistry mergeInRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(PROJECT_ID).toBuilder().addDeletedMetricIds(2).build());
        CobaltRegistry mergedRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(PROJECT_ID).toBuilder()
                                .addDeletedMetricIds(1)
                                .addDeletedMetricIds(2)
                                .build());

        assertEquals(mergedRegistry, mergeRegistries(baseRegistry, mergeInRegistry));
    }

    @Test
    public void testMergeRegistries_deletedMetricsNotIncluded_fromBaseRegistry() {
        // This situation is _not_ expected since the base registry would have to be invalid but
        // it's tested for completeness.
        CobaltRegistry baseRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(PROJECT_ID, makeMetric(METRIC_ID)).toBuilder()
                                .addDeletedMetricIds(METRIC_ID)
                                .build());
        CobaltRegistry mergeInRegistry = makeRegistry(CUSTOMER_ID, makeProject(PROJECT_ID));
        CobaltRegistry mergedRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(PROJECT_ID).toBuilder().addDeletedMetricIds(METRIC_ID).build());

        assertEquals(mergedRegistry, mergeRegistries(baseRegistry, mergeInRegistry));
    }

    @Test
    public void testMergeRegistries_deletedMetricsNotIncluded_fromMergeInRegistry() {
        CobaltRegistry baseRegistry =
                makeRegistry(CUSTOMER_ID, makeProject(PROJECT_ID, makeMetric(METRIC_ID)));
        CobaltRegistry mergeInRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(PROJECT_ID).toBuilder().addDeletedMetricIds(METRIC_ID).build());
        CobaltRegistry mergedRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(PROJECT_ID).toBuilder().addDeletedMetricIds(METRIC_ID).build());

        assertEquals(mergedRegistry, mergeRegistries(baseRegistry, mergeInRegistry));
    }

    @Test
    public void testMergeRegistries_mergeInMetricsNotIncluded() {
        CobaltRegistry baseRegistry =
                makeRegistry(CUSTOMER_ID, makeProject(PROJECT_ID, makeMetric(101)));
        CobaltRegistry mergeInRegistry =
                makeRegistry(
                        CUSTOMER_ID, makeProject(PROJECT_ID, makeMetric(202), makeMetric(303)));
        CobaltRegistry mergedRegistry =
                makeRegistry(CUSTOMER_ID, makeProject(PROJECT_ID, makeMetric(101)));

        assertEquals(mergedRegistry, mergeRegistries(baseRegistry, mergeInRegistry));
    }

    @Test
    public void testMergeRegistries_deletedReportsListsMerged() {
        CobaltRegistry baseRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(
                                PROJECT_ID,
                                makeMetric(METRIC_ID)
                                        .newBuilder()
                                        .addDeletedReportIds(101)
                                        .build()));
        CobaltRegistry mergeInRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(
                                PROJECT_ID,
                                makeMetric(METRIC_ID)
                                        .newBuilder()
                                        .addDeletedReportIds(202)
                                        .addDeletedReportIds(303)
                                        .build()));
        CobaltRegistry mergedRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(
                                PROJECT_ID,
                                makeMetric(METRIC_ID)
                                        .newBuilder()
                                        .addDeletedReportIds(101)
                                        .addDeletedReportIds(202)
                                        .addDeletedReportIds(303)
                                        .build()));

        assertEquals(mergedRegistry, mergeRegistries(baseRegistry, mergeInRegistry));
    }

    @Test
    public void testMergeRegistries_deletedReportsNotIncluded_fromBaseRegistry() {
        // This situation is _not_ expected since the base registry would have to be invalid but
        // it's tested for completeness.
        CobaltRegistry baseRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(
                                PROJECT_ID,
                                makeMetric(METRIC_ID, makeReport(REPORT_ID))
                                        .newBuilder()
                                        .addDeletedReportIds(REPORT_ID)
                                        .build()));
        CobaltRegistry mergeInRegistry = makeRegistry(CUSTOMER_ID, makeProject(PROJECT_ID));
        CobaltRegistry mergedRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(
                                PROJECT_ID,
                                makeMetric(METRIC_ID)
                                        .newBuilder()
                                        .addDeletedReportIds(REPORT_ID)
                                        .build()));

        assertEquals(mergedRegistry, mergeRegistries(baseRegistry, mergeInRegistry));
    }

    @Test
    public void testMergeRegistries_deletedReportsNotIncluded_fromMergeInRegistry() {
        CobaltRegistry baseRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(PROJECT_ID, makeMetric(METRIC_ID, makeReport(REPORT_ID))));
        CobaltRegistry mergeInRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(
                                PROJECT_ID,
                                makeMetric(METRIC_ID).toBuilder()
                                        .addDeletedReportIds(REPORT_ID)
                                        .build()));
        CobaltRegistry mergedRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(
                                PROJECT_ID,
                                makeMetric(METRIC_ID).toBuilder()
                                        .addDeletedReportIds(REPORT_ID)
                                        .build()));

        assertEquals(mergedRegistry, mergeRegistries(baseRegistry, mergeInRegistry));
    }

    @Test
    public void testMergeRegistries_baseReportsNotUpdated() {
        CobaltRegistry baseRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(
                                PROJECT_ID,
                                makeMetric(
                                        METRIC_ID,
                                        makeReport(REPORT_ID, FLEETWIDE_OCCURRENCE_COUNTS))));
        CobaltRegistry mergeInRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(
                                PROJECT_ID,
                                makeMetric(METRIC_ID, makeReport(REPORT_ID, STRING_COUNTS))));
        CobaltRegistry mergedRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(
                                PROJECT_ID,
                                makeMetric(
                                        METRIC_ID,
                                        makeReport(REPORT_ID, FLEETWIDE_OCCURRENCE_COUNTS))));

        assertEquals(mergedRegistry, mergeRegistries(baseRegistry, mergeInRegistry));
    }

    @Test
    public void testMergeRegistries_invalidReportsNotIncluded() {
        CobaltRegistry baseRegistry =
                makeRegistry(CUSTOMER_ID, makeProject(PROJECT_ID, makeMetric(METRIC_ID)));
        CobaltRegistry mergeInRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(PROJECT_ID, makeMetric(METRIC_ID, INVALID_REPORT)));
        CobaltRegistry mergedRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(
                                PROJECT_ID,
                                makeMetric(METRIC_ID).toBuilder()
                                        .addDeletedReportIds(INVALID_REPORT.getId())
                                        .build()));

        assertEquals(mergedRegistry, mergeRegistries(baseRegistry, mergeInRegistry));
    }

    @Test
    public void testMergeRegistries_validReportsMerged() {
        MetricDefinition metric =
                MetricDefinition.newBuilder()
                        .setId(METRIC_ID)
                        .setMetricType(OCCURRENCE)
                        .setMetaData(Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.GA))
                        .build();
        ReportDefinition mergeInReport =
                ReportDefinition.newBuilder()
                        .setId(202)
                        .setReportType(FLEETWIDE_OCCURRENCE_COUNTS)
                        .setPrivacyMechanism(DE_IDENTIFICATION)
                        .setReportingInterval(DAYS_1)
                        .setSystemProfileSelection(REPORT_ALL)
                        .setMaxReleaseStage(ReleaseStage.GA)
                        .build();
        assertEquals(
                true,
                RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, mergeInReport));
        CobaltRegistry baseRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(
                                PROJECT_ID,
                                metric.toBuilder().addReports(makeReport(101)).build()));
        CobaltRegistry mergeInRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(
                                PROJECT_ID, metric.toBuilder().addReports(mergeInReport).build()));
        CobaltRegistry mergedRegistry =
                makeRegistry(
                        CUSTOMER_ID,
                        makeProject(
                                PROJECT_ID,
                                metric.toBuilder()
                                        .addReports(makeReport(101))
                                        .addReports(mergeInReport)
                                        .build()));

        assertEquals(mergedRegistry, mergeRegistries(baseRegistry, mergeInRegistry));
    }

    private ReportDefinition makeReport(int reportId) {
        return ReportDefinition.newBuilder().setId(reportId).build();
    }

    private ReportDefinition makeReport(int reportId, ReportType reportType) {
        return ReportDefinition.newBuilder().setId(reportId).setReportType(reportType).build();
    }

    private MetricDefinition makeMetric(int metricId, ReportDefinition... reports) {
        MetricDefinition.Builder metric = MetricDefinition.newBuilder().setId(metricId);
        for (int i = 0; i < reports.length; ++i) {
            metric.addReports(reports[i]);
        }
        return metric.build();
    }

    private ProjectConfig makeProject(int projectId, MetricDefinition... metrics) {
        ProjectConfig.Builder project = ProjectConfig.newBuilder().setProjectId(projectId);
        for (int i = 0; i < metrics.length; ++i) {
            project.addMetrics(metrics[i]);
        }
        return project.build();
    }

    private CobaltRegistry makeRegistry(int customerId, ProjectConfig project) {
        return CobaltRegistry.newBuilder()
                .addCustomers(
                        CustomerConfig.newBuilder().setCustomerId(customerId).addProjects(project))
                .build();
    }
}
