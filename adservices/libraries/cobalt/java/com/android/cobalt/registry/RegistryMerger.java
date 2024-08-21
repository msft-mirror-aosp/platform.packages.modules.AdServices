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

import android.annotation.Nullable;

import com.google.cobalt.CobaltRegistry;
import com.google.cobalt.CustomerConfig;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.ProjectConfig;
import com.google.cobalt.ReportDefinition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.HashMap;
import java.util.Map;

/** Merges two registries to create a new registry with their combined content. */
public final class RegistryMerger {

    /**
     * Merges two Cobalt registries by the contents of the base registry and merging it with the
     * content of the merge in registry.
     *
     * <p>The only content added to the base registry is valid reports from the merge in registry,
     * if they're not already in the base registry.
     *
     * <p>Additionally, customer, project, metrics, reports may be deleted from the base registry if
     * they're deleted in the merge in registry.
     */
    public static CobaltRegistry mergeRegistries(
            CobaltRegistry baseRegistry, CobaltRegistry mergeInRegistry) {
        ImmutableList.Builder<CustomerConfig> mergedCustomers = ImmutableList.builder();
        ImmutableSet<Integer> deletedCustomerIds =
                ImmutableSet.<Integer>builder()
                        .addAll(baseRegistry.getDeletedCustomerIdsList())
                        .addAll(mergeInRegistry.getDeletedCustomerIdsList())
                        .build();

        ImmutableMap<Integer, CustomerConfig> mergeInCustomers = getCustomers(mergeInRegistry);
        for (CustomerConfig baseCustomer : baseRegistry.getCustomersList()) {
            if (deletedCustomerIds.contains(baseCustomer.getCustomerId())) {
                continue;
            }

            mergedCustomers.add(
                    mergeCustomers(
                            baseCustomer, mergeInCustomers.get(baseCustomer.getCustomerId())));
        }

        return baseRegistry.toBuilder()
                .clearCustomers()
                .addAllCustomers(mergedCustomers.build())
                .clearDeletedCustomerIds()
                .addAllDeletedCustomerIds(deletedCustomerIds)
                .build();
    }

    private static CustomerConfig mergeCustomers(
            CustomerConfig baseCustomer, @Nullable CustomerConfig mergeInCustomer) {
        if (mergeInCustomer == null) {
            return baseCustomer;
        }

        ImmutableList.Builder<ProjectConfig> mergedProjects = ImmutableList.builder();
        ImmutableSet<Integer> deletedProjectIds =
                ImmutableSet.<Integer>builder()
                        .addAll(baseCustomer.getDeletedProjectIdsList())
                        .addAll(mergeInCustomer.getDeletedProjectIdsList())
                        .build();

        ImmutableMap<Integer, ProjectConfig> mergeInProjects = getProjects(mergeInCustomer);
        for (ProjectConfig baseProject : baseCustomer.getProjectsList()) {
            if (deletedProjectIds.contains(baseProject.getProjectId())) {
                continue;
            }

            mergedProjects.add(
                    mergeProjects(baseProject, mergeInProjects.get(baseProject.getProjectId())));
        }

        return baseCustomer.toBuilder()
                .clearProjects()
                .addAllProjects(mergedProjects.build())
                .clearDeletedProjectIds()
                .addAllDeletedProjectIds(deletedProjectIds)
                .build();
    }

    private static ProjectConfig mergeProjects(
            ProjectConfig baseProject, @Nullable ProjectConfig mergeInProject) {
        if (mergeInProject == null) {
            return baseProject;
        }

        ImmutableList.Builder<MetricDefinition> mergedMetrics = ImmutableList.builder();
        ImmutableSet<Integer> deletedMetricIds =
                ImmutableSet.<Integer>builder()
                        .addAll(baseProject.getDeletedMetricIdsList())
                        .addAll(mergeInProject.getDeletedMetricIdsList())
                        .build();

        ImmutableMap<Integer, MetricDefinition> mergeInMetrics = getMetrics(mergeInProject);
        for (MetricDefinition baseMetric : baseProject.getMetricsList()) {
            if (deletedMetricIds.contains(baseMetric.getId())) {
                continue;
            }

            mergedMetrics.add(mergeMetrics(baseMetric, mergeInMetrics.get(baseMetric.getId())));
        }

        return baseProject.toBuilder()
                .clearMetrics()
                .addAllMetrics(mergedMetrics.build())
                .clearDeletedMetricIds()
                .addAllDeletedMetricIds(deletedMetricIds)
                .build();
    }

    private static MetricDefinition mergeMetrics(
            MetricDefinition baseMetric, @Nullable MetricDefinition mergeInMetric) {
        if (mergeInMetric == null) {
            return baseMetric;
        }

        Map<Integer, ReportDefinition> mergedReports = new HashMap<>();
        ImmutableSet<Integer> deletedReportIds =
                ImmutableSet.<Integer>builder()
                        .addAll(baseMetric.getDeletedReportIdsList())
                        .addAll(mergeInMetric.getDeletedReportIdsList())
                        .build();

        ImmutableMap<Integer, ReportDefinition> baseReports = getReports(baseMetric);
        ImmutableMap<Integer, ReportDefinition> mergeInReports = getReports(mergeInMetric);

        for (Map.Entry<Integer, ReportDefinition> report : baseReports.entrySet()) {
            if (deletedReportIds.contains(report.getKey())) {
                continue;
            }

            mergedReports.put(report.getKey(), report.getValue());
        }
        for (Map.Entry<Integer, ReportDefinition> report : mergeInReports.entrySet()) {
            if (deletedReportIds.contains(report.getKey())) {
                continue;
            }
            if (mergedReports.containsKey(report.getKey())) {
                continue;
            }
            if (!RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                    baseMetric, report.getValue())) {
                // Invalid reports from the registry being merged in are considered deleted for
                // safety.
                deletedReportIds =
                        ImmutableSet.<Integer>builderWithExpectedSize(deletedReportIds.size() + 1)
                                .addAll(deletedReportIds)
                                .add(report.getKey())
                                .build();
                continue;
            }

            mergedReports.put(report.getKey(), report.getValue());
        }

        return baseMetric.toBuilder()
                .clearReports()
                .addAllReports(mergedReports.values())
                .clearDeletedReportIds()
                .addAllDeletedReportIds(deletedReportIds)
                .build();
    }

    private static ImmutableMap<Integer, CustomerConfig> getCustomers(CobaltRegistry registry) {
        ImmutableMap.Builder<Integer, CustomerConfig> customers = ImmutableMap.builder();
        for (CustomerConfig customer : registry.getCustomersList()) {
            customers.put(customer.getCustomerId(), customer);
        }
        return customers.build();
    }

    private static ImmutableMap<Integer, ProjectConfig> getProjects(CustomerConfig customer) {
        ImmutableMap.Builder<Integer, ProjectConfig> projects = ImmutableMap.builder();
        for (ProjectConfig project : customer.getProjectsList()) {
            projects.put(project.getProjectId(), project);
        }
        return projects.build();
    }

    private static ImmutableMap<Integer, MetricDefinition> getMetrics(ProjectConfig project) {
        ImmutableMap.Builder<Integer, MetricDefinition> metrics = ImmutableMap.builder();
        for (MetricDefinition metric : project.getMetricsList()) {
            metrics.put(metric.getId(), metric);
        }
        return metrics.build();
    }

    private static ImmutableMap<Integer, ReportDefinition> getReports(MetricDefinition metric) {
        ImmutableMap.Builder<Integer, ReportDefinition> reports = ImmutableMap.builder();
        for (ReportDefinition report : metric.getReportsList()) {
            reports.put(report.getId(), report);
        }
        return reports.build();
    }

    private RegistryMerger() {}
}
