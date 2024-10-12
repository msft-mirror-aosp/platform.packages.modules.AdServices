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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.cobalt.ReleaseStage.DOGFOOD;
import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.res.AssetManager;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.cobalt.domain.Project;
import com.android.cobalt.registry.RegistryValidator;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.cobalt.CobaltRegistry;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.ReportDefinition;
import com.google.common.io.ByteStreams;

import org.junit.Test;
import org.mockito.Mock;

import java.io.InputStream;
import java.util.Optional;

@SpyStatic(CobaltDownloadRegistryManager.class)
public final class CobaltRegistryLoaderTest extends AdServicesExtendedMockitoTestCase {
    private static final String REPORT_NAME_DOGFOOD_SUFFIX = "_dogfood";
    private static final String TEST_REGISTRY_ASSET_FILE = "cobalt/cobalt_registry_test.binarypb";
    private static final String MDD_TEST_REPORT_NAME = "fleetwide_counts_mdd_testonly";
    private static final int TOPICS_METRICS_ID = 1;
    private static final int TOPIC_EVENT_COUNT = 447;
    private static final int PACKAGE_API_ERRORS_ID = 2;
    private static final int TOPIC_DIMENSION_IDX = 0;
    private static final int API_DIMENSION_IDX = 0;
    private static final int API_EVENT_COUNT = 34;
    private static final int ERROR_DIMENSION_IDX = 1;
    private static final int ERROR_EVENT_COUNT = 27;

    @Mock private CobaltDownloadRegistryManager mMockCobaltDownloadRegistryManager;

    @Test
    public void testCobaltRegistryIsValidated_isTrue() {
        assertThat(CobaltRegistryValidated.IS_REGISTRY_VALIDATED).isTrue();
    }

    @Test
    public void testGetRegistry_registryCanBeLoaded() throws Exception {
        Project registry = CobaltRegistryLoader.getRegistry(mContext, mMockFlags);
        assertThat(registry).isNotNull();
    }

    @Test
    public void testGetRegistry_unsupportedFeaturesNotInRegistry() throws Exception {
        Project registry = CobaltRegistryLoader.getRegistry(mContext, mMockFlags);

        for (MetricDefinition metric : registry.getMetrics()) {
            for (ReportDefinition report : metric.getReportsList()) {
                assertThat(RegistryValidator.isValid(metric, report)).isTrue();
                if (report.getReportName().endsWith(REPORT_NAME_DOGFOOD_SUFFIX)) {
                    assertThat(report.getMaxReleaseStage()).isEqualTo(DOGFOOD);
                }
            }
        }
    }

    @Test
    public void testGetRegistry_metricDimensions() throws Exception {
        Project registry = CobaltRegistryLoader.getRegistry(mContext, mMockFlags);
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

    @Test
    public void testGetRegistry_outOfBandRegistryEnabled() throws Exception {
        mockCobaltDownloadRegistryManager();
        CobaltRegistry mddRegistry = getMddRegistry();
        when(mMockCobaltDownloadRegistryManager.getMddRegistry())
                .thenReturn(Optional.of(mddRegistry));

        Project registry = CobaltRegistryLoader.getRegistry(mContext, mMockFlags);

        assertMergedRegistry(registry);
    }

    @Test
    public void testGetRegistry_fallBackToDefaultBaseRegistryEnabled() throws Exception {
        mockCobaltDownloadRegistryManager();
        CobaltRegistry mddRegistry = getMddRegistry();
        when(mMockFlags.getCobaltFallBackToDefaultBaseRegistry()).thenReturn(true);
        when(mMockCobaltDownloadRegistryManager.getMddRegistry())
                .thenReturn(Optional.of(mddRegistry));

        Project registry = CobaltRegistryLoader.getRegistry(mContext, mMockFlags);

        assertBaseRegistry(registry);
    }

    @Test
    public void testGetRegistry_nullMddRegistry() throws Exception {
        mockCobaltDownloadRegistryManager();
        when(mMockCobaltDownloadRegistryManager.getMddRegistry()).thenReturn(Optional.empty());

        Project registry = CobaltRegistryLoader.getRegistry(mContext, mMockFlags);

        assertBaseRegistry(registry);
    }

    private static void assertBaseRegistry(Project registry) {
        assertThat(registry).isNotNull();
        for (MetricDefinition metric : registry.getMetrics()) {
            if (metric.getId() == TOPICS_METRICS_ID) {
                assertThat(metric.getReportsCount()).isEqualTo(5);
            }
            if (metric.getId() == PACKAGE_API_ERRORS_ID) {
                assertThat(metric.getReportsCount()).isEqualTo(2);
            }
        }
    }

    private void assertMergedRegistry(Project registry) {
        assertThat(registry).isNotNull();
        for (MetricDefinition metric : registry.getMetrics()) {
            if (metric.getId() == TOPICS_METRICS_ID) {
                assertThat(metric.getReportsCount()).isEqualTo(6);
                expect.withMessage("metric.getReportName()")
                        .that(metric.getReports(5).getReportName())
                        .isEqualTo(MDD_TEST_REPORT_NAME);
            }
        }
    }

    private void mockCobaltDownloadRegistryManager() {
        when(mMockFlags.getCobaltRegistryOutOfBandUpdateEnabled()).thenReturn(true);
        doReturn(mMockCobaltDownloadRegistryManager)
                .when(CobaltDownloadRegistryManager::getInstance);
    }

    private CobaltRegistry getMddRegistry() throws Exception {
        AssetManager assertManager = mContext.getAssets();
        InputStream inputStream = assertManager.open(TEST_REGISTRY_ASSET_FILE);
        return CobaltRegistry.parseFrom(ByteStreams.toByteArray(inputStream));
    }
}
