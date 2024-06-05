/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.cobalt;

import static com.google.cobalt.ReleaseStage.DOGFOOD;
import static com.google.common.truth.Truth.assertThat;

import static java.util.stream.Collectors.toList;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.cobalt.domain.Project;

import com.google.cobalt.IntegerBuckets;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.MetricType;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.LocalAggregationProcedure;
import com.google.cobalt.ReportDefinition.ReportType;
import com.google.cobalt.ReportDefinition.ReportingInterval;
import com.google.cobalt.StringSketchParameters;
import com.google.cobalt.SystemProfileSelectionPolicy;
import com.google.cobalt.WindowSize;

import org.junit.Test;

import java.util.List;

@RequiresSdkLevelAtLeastS
public final class CobaltRegistryLoaderTest extends AdServicesUnitTestCase {
    private static final String REPORT_NAME_DOGFOOD_SUFFIX = "_dogfood";
    private static final int TOPICS_METRICS_ID = 1;
    private static final int TOPIC_EVENT_COUNT = 447;
    private static final int PACKAGE_API_ERRORS_ID = 2;
    private static final int TOPIC_DIMENSION_IDX = 0;
    private static final int API_DIMENSION_IDX = 0;
    private static final int API_EVENT_COUNT = 34;
    private static final int ERROR_DIMENSION_IDX = 1;
    private static final int ERROR_EVENT_COUNT = 27;

    @Test
    public void testCobaltRegistryIsValidated_isTrue() {
        assertThat(CobaltRegistryValidated.IS_REGISTRY_VALIDATED).isTrue();
    }

    @Test
    public void testGetRegistry_registryCanBeLoaded() throws Exception {
        Project registry = CobaltRegistryLoader.getRegistry(sContext);
        assertThat(registry).isNotNull();
    }

    @Test
    public void testGetRegistry_unsupportedFeaturesNotInRegistry() throws Exception {
        Project registry = CobaltRegistryLoader.getRegistry(sContext);
        for (MetricDefinition metric : registry.getMetrics()) {
            assertThat(metric.getMetricType()).isAnyOf(MetricType.OCCURRENCE, MetricType.STRING);
        }

        List<ReportDefinition> reports =
                registry.getMetrics().stream()
                        .flatMap(m -> m.getReportsList().stream())
                        .collect(toList());
        for (ReportDefinition report : reports) {
            assertThat(report.getReportType())
                    .isAnyOf(ReportType.FLEETWIDE_OCCURRENCE_COUNTS, ReportType.STRING_COUNTS);
            if (report.getReportType() == ReportType.STRING_COUNTS) {
                assertThat(report.getPrivacyMechanism())
                        .isEqualTo(ReportDefinition.PrivacyMechanism.DE_IDENTIFICATION);
            }
            if (report.getReportName().endsWith(REPORT_NAME_DOGFOOD_SUFFIX)) {
                assertThat(report.getMaxReleaseStage()).isEqualTo(DOGFOOD);
            }
            assertThat(report.getReportingInterval()).isEqualTo(ReportingInterval.DAYS_1);
            assertThat(report.getLocalAggregationProcedure())
                    .isEqualTo(LocalAggregationProcedure.LOCAL_AGGREGATION_PROCEDURE_UNSET);
            assertThat(report.getExperimentIdList()).isEmpty();
            assertThat(report.getSystemProfileSelection())
                    .isEqualTo(SystemProfileSelectionPolicy.REPORT_ALL);
            assertThat(report.getStringSketchParams())
                    .isEqualTo(StringSketchParameters.getDefaultInstance());
            assertThat(report.getIntBuckets()).isEqualTo(IntegerBuckets.getDefaultInstance());
            assertThat(report.getLocalAggregationProcedurePercentileN()).isEqualTo(0);
            assertThat(report.getExpeditedSending()).isFalse();
            assertThat(report.getExperimentIdList()).isEmpty();

            // Sme parts of the code always assume a local aggregation period of 1 day independent
            // of the values in reports, e.g. database clean. Supporting larger windows in reports
            // must be done with a careful check of existing code.
            assertThat(report.getLocalAggregationPeriod()).isEqualTo(WindowSize.UNSET);
        }
    }

    @Test
    public void testGetRegistry_metricDimensions() throws Exception {
        Project registry = CobaltRegistryLoader.getRegistry(sContext);
        for (MetricDefinition metric : registry.getMetrics()) {
            if (metric.getId() == TOPICS_METRICS_ID) {
                expect.withMessage("topicsDimension.getEventCodesCount()")
                        .that(metric.getMetricDimensions(TOPIC_DIMENSION_IDX).getEventCodesCount())
                        .isEqualTo(TOPIC_EVENT_COUNT);
            }
            if (metric.getId() == PACKAGE_API_ERRORS_ID) {
                expect.withMessage("apiDimension.getEventCodesCount()")
                        .that(metric.getMetricDimensions(API_DIMENSION_IDX).getEventCodesCount())
                        .isEqualTo(API_EVENT_COUNT);
                expect.withMessage("errorDimension.getEventCodesCount()")
                        .that(metric.getMetricDimensions(ERROR_DIMENSION_IDX).getEventCodesCount())
                        .isEqualTo(ERROR_EVENT_COUNT);
            }
        }
    }
}
