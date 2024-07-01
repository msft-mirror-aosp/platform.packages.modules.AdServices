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

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.cobalt.domain.Project;
import com.android.cobalt.registry.RegistryValidator;

import com.google.cobalt.MetricDefinition;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.StringSketchParameters;

import org.junit.Test;

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
            for (ReportDefinition report : metric.getReportsList()) {
                assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                        .isTrue();
                if (report.getReportName().endsWith(REPORT_NAME_DOGFOOD_SUFFIX)) {
                    assertThat(report.getMaxReleaseStage()).isEqualTo(DOGFOOD);
                }
                assertThat(report.getStringSketchParams())
                        .isEqualTo(StringSketchParameters.getDefaultInstance());
            }
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
