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

import static com.google.common.truth.Truth.assertThat;

import static java.util.stream.Collectors.toList;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.google.cobalt.CobaltRegistry;
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
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CobaltRegistryLoaderTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    @Test
    public void cobaltRegistryIsValidated_isTrue() throws Exception {
        assertThat(CobaltRegistryValidated.IS_REGISTRY_VALIDATED).isTrue();
    }

    @Test
    public void getRegistry_registryCanBeLoaded() throws Exception {
        CobaltRegistry registry = CobaltRegistryLoader.getRegistry(CONTEXT);
        assertThat(registry).isNotEqualTo(CobaltRegistry.getDefaultInstance());
    }

    @Test
    public void getRegistry_unsupportedFeaturesNotInRegistry() throws Exception {
        CobaltRegistry registry = CobaltRegistryLoader.getRegistry(CONTEXT);
        assertThat(registry.getCustomersCount()).isEqualTo(1);
        assertThat(registry.getCustomers(0).getProjectsCount()).isEqualTo(1);

        List<MetricDefinition> metrics = registry.getCustomers(0).getProjects(0).getMetricsList();
        for (MetricDefinition metric : metrics) {
            assertThat(metric.getMetricType()).isEqualTo(MetricType.OCCURRENCE);
        }

        List<ReportDefinition> reports =
                metrics.stream().flatMap(m -> m.getReportsList().stream()).collect(toList());
        for (ReportDefinition report : reports) {
            assertThat(report.getReportType()).isEqualTo(ReportType.FLEETWIDE_OCCURRENCE_COUNTS);
            assertThat(report.getReportingInterval()).isEqualTo(ReportingInterval.DAYS_1);
            assertThat(report.getLocalAggregationProcedure())
                    .isEqualTo(LocalAggregationProcedure.LOCAL_AGGREGATION_PROCEDURE_UNSET);
            assertThat(report.getSystemProfileFieldList()).isEmpty();
            assertThat(report.getExperimentIdList()).isEmpty();
            assertThat(report.getSystemProfileSelection())
                    .isEqualTo(SystemProfileSelectionPolicy.REPORT_ALL);
            assertThat(report.getStringSketchParams())
                    .isEqualTo(StringSketchParameters.getDefaultInstance());
            assertThat(report.getIntBuckets()).isEqualTo(IntegerBuckets.getDefaultInstance());
            assertThat(report.getLocalAggregationProcedurePercentileN()).isEqualTo(0);
            assertThat(report.getLocalAggregationPeriod()).isEqualTo(WindowSize.UNSET);
            assertThat(report.getStringBufferMax()).isEqualTo(0);
            assertThat(report.getExpeditedSending()).isFalse();
            assertThat(report.getExperimentIdList()).isEmpty();
        }
    }
}
