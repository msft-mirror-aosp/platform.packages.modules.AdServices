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

package com.android.cobalt.domain;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.google.cobalt.CobaltRegistry;
import com.google.cobalt.CustomerConfig;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.ProjectConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public final class ProjectTest {
    private static final int METRIC_ID = 3;

    @Test
    public void testCreate_populatesIdsAndMetrics_withEmptyMetrics() throws Exception {
        ProjectConfig projectConfig =
                ProjectConfig.newBuilder().setProjectId(Project.PROJECT_ID).build();
        CustomerConfig customerConfig =
                CustomerConfig.newBuilder()
                        .setCustomerId(Project.CUSTOMER_ID)
                        .addProjects(projectConfig)
                        .build();
        CobaltRegistry protoRegistry =
                CobaltRegistry.newBuilder().addCustomers(customerConfig).build();

        Project project = Project.create(protoRegistry);
        assertThat(project)
                .isEqualTo(Project.create(Project.CUSTOMER_ID, Project.PROJECT_ID, List.of()));
    }

    @Test
    public void testCreate_populatesIdsAndMetrics_withNonEmptyMetrics() throws Exception {
        MetricDefinition metric = MetricDefinition.newBuilder().setId(METRIC_ID).build();
        ProjectConfig projectConfig =
                ProjectConfig.newBuilder()
                        .setProjectId(Project.PROJECT_ID)
                        .addMetrics(metric)
                        .build();
        CustomerConfig customerConfig =
                CustomerConfig.newBuilder()
                        .setCustomerId(Project.CUSTOMER_ID)
                        .addProjects(projectConfig)
                        .build();
        CobaltRegistry protoRegistry =
                CobaltRegistry.newBuilder().addCustomers(customerConfig).build();

        Project project = Project.create(protoRegistry);
        assertThat(project)
                .isEqualTo(
                        Project.create(Project.CUSTOMER_ID, Project.PROJECT_ID, List.of(metric)));
    }

    @Test
    public void testCreate_moreThanOneCustomer_adServicesSelected() throws Exception {
        MetricDefinition metric = MetricDefinition.newBuilder().setId(METRIC_ID).build();
        ProjectConfig projectConfig =
                ProjectConfig.newBuilder()
                        .setProjectId(Project.PROJECT_ID)
                        .addMetrics(metric)
                        .build();
        CobaltRegistry protoRegistry =
                CobaltRegistry.newBuilder()
                        .addCustomers(
                                CustomerConfig.newBuilder()
                                        .setCustomerId(Project.CUSTOMER_ID)
                                        .addProjects(projectConfig))
                        .addCustomers(
                                CustomerConfig.newBuilder()
                                        .setCustomerId(Project.CUSTOMER_ID + 1)
                                        .addProjects(projectConfig))
                        .build();

        Project project = Project.create(protoRegistry);
        assertThat(project)
                .isEqualTo(
                        Project.create(Project.CUSTOMER_ID, Project.PROJECT_ID, List.of(metric)));
    }

    @Test
    public void testCreate_moreThanOneProject_adServicesSelected() throws Exception {
        MetricDefinition metric = MetricDefinition.newBuilder().setId(METRIC_ID).build();
        CustomerConfig customerConfig =
                CustomerConfig.newBuilder()
                        .setCustomerId(Project.CUSTOMER_ID)
                        .addProjects(
                                ProjectConfig.newBuilder()
                                        .setProjectId(Project.PROJECT_ID)
                                        .addMetrics(metric))
                        .addProjects(
                                ProjectConfig.newBuilder()
                                        .setProjectId(Project.PROJECT_ID + 1)
                                        .addMetrics(metric))
                        .build();
        CobaltRegistry protoRegistry =
                CobaltRegistry.newBuilder().addCustomers(customerConfig).build();

        Project project = Project.create(protoRegistry);
        assertThat(project)
                .isEqualTo(
                        Project.create(Project.CUSTOMER_ID, Project.PROJECT_ID, List.of(metric)));
    }

    @Test
    public void testCreate_nullProto_throwsNullPointerException() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    Project.create(null);
                });
    }

    @Test
    public void testCreate_nullMetrics_throwsNullPointerException() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    Project.create(Project.CUSTOMER_ID, Project.PROJECT_ID, null);
                });
    }

    @Test
    public void testCreate_noAdServicesCustomer_throwsIllegalArgumentException() throws Exception {
        MetricDefinition metric = MetricDefinition.newBuilder().setId(METRIC_ID).build();
        ProjectConfig projectConfig =
                ProjectConfig.newBuilder()
                        .setProjectId(Project.PROJECT_ID)
                        .addMetrics(metric)
                        .build();
        CustomerConfig customerConfig =
                CustomerConfig.newBuilder()
                        .setCustomerId(Project.CUSTOMER_ID + 1)
                        .addProjects(projectConfig)
                        .build();
        CobaltRegistry protoRegistry =
                CobaltRegistry.newBuilder().addCustomers(customerConfig).build();
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    Project.create(protoRegistry);
                });
    }

    @Test
    public void testCreate_noAdServicesProject_throwsIllegalArgumentException() throws Exception {
        MetricDefinition metric = MetricDefinition.newBuilder().setId(METRIC_ID).build();
        ProjectConfig projectConfig =
                ProjectConfig.newBuilder()
                        .setProjectId(Project.PROJECT_ID + 1)
                        .addMetrics(metric)
                        .build();
        CustomerConfig customerConfig =
                CustomerConfig.newBuilder()
                        .setCustomerId(Project.CUSTOMER_ID)
                        .addProjects(projectConfig)
                        .build();
        CobaltRegistry protoRegistry =
                CobaltRegistry.newBuilder().addCustomers(customerConfig).build();
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    Project.create(protoRegistry);
                });
    }
}
