/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.data.configdelivery;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.service.proto.RbEnrollment;
import com.android.adservices.service.proto.config_delivery.ConfigurationRecord;
import com.android.adservices.service.proto.config_delivery.ConfigurationType;
import com.android.adservices.service.proto.config_delivery.VersionedConfiguration;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.After;
import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ArgonConfigurationManagerTest {

    private static final VersionedConfiguration ENROLLMENT_CONFIG_V1;
    private static final VersionedConfiguration ENROLLMENT_CONFIG_V2;

    static {
        try {
            ENROLLMENT_CONFIG_V1 =
                    VersionedConfiguration.newBuilder()
                            .setVersion(1)
                            .setConfiguration(
                                    com.android.adservices.service.proto.config_delivery
                                            .Configuration.newBuilder()
                                            .setConfigurationType(
                                                    ConfigurationType.TYPE_RB_ENROLLMENT)
                                            .addConfigurationRecords(
                                                    ConfigurationRecord.newBuilder()
                                                            .setId("id1_v1")
                                                            .addLabels("id1_v1#label1")
                                                            .addLabels("id1_v1#label2")
                                                            .setValue(
                                                                    Any.parseFrom(
                                                                            RbEnrollment
                                                                                    .newBuilder()
                                                                                    .setEnrolledSite(
                                                                                            "https://example.com")
                                                                                    .build()
                                                                                    .toByteArray()))
                                                            .build())
                                            .build())
                            .build();
            ENROLLMENT_CONFIG_V2 =
                    VersionedConfiguration.newBuilder()
                            .setVersion(2)
                            .setConfiguration(
                                    com.android.adservices.service.proto.config_delivery
                                            .Configuration.newBuilder()
                                            .setConfigurationType(
                                                    ConfigurationType.TYPE_RB_ENROLLMENT)
                                            .addConfigurationRecords(
                                                    ConfigurationRecord.newBuilder()
                                                            .setId("id1_v2")
                                                            .addLabels("id1_v2#label1")
                                                            .addLabels("id1_v2#label2")
                                                            .build())
                                            .build())
                            .build();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    @After
    // processConsistentInstancesLock is safe to read outside of the lock.
    @SuppressWarnings("GuardedBy")
    public void tearDown() {
        ConfigurationDatabase.getInstance().clearAllTables();
        ArgonConfigurationManager.processConsistentInstances.clear();
        ArgonConfigurationManager.instantiationConsistentInstances.clear();
    }

    @Test
    public void insertConfigurationsIfNotExist_insertsNewConfigurations() {
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ArgonConfigurationManager argonConfigurationManager =
                ArgonConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ArgonConfigurationManager.DataConsistencyStrategy.USE_LATEST_VERSION);

        List<Configuration> configurations = argonConfigurationManager.getConfigurations();

        assertThat(configurations.size()).isEqualTo(1);
        assertThat(configurations.get(0).getId()).isEqualTo("id1_v1");
    }

    @Test
    public void insertConfigurationsIfNotExist_doesNotInsertExistingConfigurations() {
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);

        long lastConfigRowIdBeforeExistingConfigInsert =
                ConfigurationDatabase.getInstance().configurationDao().getLastConfigRowId();
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        long lastConfigRowIdAfterExistingConfigInsert =
                ConfigurationDatabase.getInstance().configurationDao().getLastConfigRowId();

        assertThat(lastConfigRowIdBeforeExistingConfigInsert)
                .isEqualTo(lastConfigRowIdAfterExistingConfigInsert);
    }

    @Test
    public void createConfigurationManager_withUseLatestVersionStrategy_alwaysUsesLatestVersion() {
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ArgonConfigurationManager argonConfigurationManager =
                ArgonConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ArgonConfigurationManager.DataConsistencyStrategy.USE_LATEST_VERSION);

        List<Configuration> configurationsBeforeV2Download =
                argonConfigurationManager.getConfigurations();

        ConfigurationDatabase.getInstance()
                .configurationDao()
                .insertConfigurations(ENROLLMENT_CONFIG_V2);

        List<Configuration> configurationsAfterV2Download =
                argonConfigurationManager.getConfigurations();

        assertThat(configurationsBeforeV2Download.get(0).getId()).isEqualTo("id1_v1");
        assertThat(configurationsAfterV2Download.get(0).getId()).isEqualTo("id1_v2");
    }

    @Test
    public void
            createConfManager_withUseVersionAtInstantiationStrategy_usesSameVersionPerInstance() {
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ArgonConfigurationManager argonConfigurationManagerInstance1 =
                ArgonConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ArgonConfigurationManager.DataConsistencyStrategy
                                .USE_VERSION_AT_INSTANTIATION);

        List<Configuration> configurationsBeforeV2DownloadFromInstance1 =
                argonConfigurationManagerInstance1.getConfigurations();

        ConfigurationDatabase.getInstance()
                .configurationDao()
                .insertConfigurations(ENROLLMENT_CONFIG_V2);

        List<Configuration> configurationsAfterV2DownloadFromInstance1 =
                argonConfigurationManagerInstance1.getConfigurations();

        ArgonConfigurationManager argonConfigurationManagerInstance2 =
                ArgonConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ArgonConfigurationManager.DataConsistencyStrategy
                                .USE_VERSION_AT_INSTANTIATION);

        List<Configuration> configurationsAfterV2DownloadFromInstance2 =
                argonConfigurationManagerInstance2.getConfigurations();

        assertThat(configurationsBeforeV2DownloadFromInstance1.get(0).getId()).isEqualTo("id1_v1");
        assertThat(configurationsAfterV2DownloadFromInstance1.get(0).getId()).isEqualTo("id1_v1");
        assertThat(configurationsAfterV2DownloadFromInstance2.get(0).getId()).isEqualTo("id1_v2");
    }

    @Test
    public void createConfigurationManager_withProcessConsistentStrategy_alwaysUsesSameVersion() {
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ArgonConfigurationManager argonConfigurationManager =
                ArgonConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ArgonConfigurationManager.DataConsistencyStrategy.PROCESS_CONSISTENT);

        List<Configuration> configurationsBeforeV2Download =
                argonConfigurationManager.getConfigurations();

        ConfigurationDatabase.getInstance()
                .configurationDao()
                .insertConfigurations(ENROLLMENT_CONFIG_V2);

        List<Configuration> configurationsAfterV2Download =
                argonConfigurationManager.getConfigurations();

        assertThat(configurationsBeforeV2Download.get(0).getId()).isEqualTo("id1_v1");
        assertThat(configurationsAfterV2Download.get(0).getId()).isEqualTo("id1_v1");
    }

    @Test
    public void getConfigurationById_returnsMatchingConfiguration() {
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ArgonConfigurationManager argonConfigurationManager =
                ArgonConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ArgonConfigurationManager.DataConsistencyStrategy.USE_LATEST_VERSION);

        Configuration configuration = argonConfigurationManager.getConfigurationById("id1_v1");
        RbEnrollment rbEnrollment =
                Objects.requireNonNull(configuration).getValue(RbEnrollment.getDefaultInstance());

        assertThat(configuration.getId()).isEqualTo("id1_v1");
        assertThat(rbEnrollment).isNotNull();
        assertThat(rbEnrollment.getEnrolledSite()).isEqualTo("https://example.com");
    }

    @Test
    public void getConfigurationById_withEmptyTable_returnsNull() {
        ArgonConfigurationManager argonConfigurationManager =
                ArgonConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ArgonConfigurationManager.DataConsistencyStrategy.USE_LATEST_VERSION);

        Configuration configuration = argonConfigurationManager.getConfigurationById("id1_v1");

        assertThat(configuration).isNull();
    }

    @Test
    public void getConfigurationsByAnyLabel_returnsMatchingConfigurations() {
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ArgonConfigurationManager argonConfigurationManager =
                ArgonConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ArgonConfigurationManager.DataConsistencyStrategy.PROCESS_CONSISTENT);

        List<Configuration> configuration =
                argonConfigurationManager.getConfigurationsByAnyLabel(
                        Set.of("id1_v1#label1", "id1_v1#label2"));

        assertThat(configuration.size()).isEqualTo(1);
        assertThat(configuration.get(0).getId()).isEqualTo("id1_v1");
    }

    @Test
    public void getConfigurationsByAnyLabel_withEmptyTable_returnsEmptyList() {
        ArgonConfigurationManager argonConfigurationManager =
                ArgonConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ArgonConfigurationManager.DataConsistencyStrategy.PROCESS_CONSISTENT);

        List<Configuration> configuration =
                argonConfigurationManager.getConfigurationsByAnyLabel(
                        Set.of("id1_v1#label1", "id1_v1#label2"));

        assertThat(configuration.size()).isEqualTo(0);
    }

    @Test
    public void getConfigurationsByAllLabels_returnsMatchingConfigurations() {
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ArgonConfigurationManager argonConfigurationManager =
                ArgonConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ArgonConfigurationManager.DataConsistencyStrategy.PROCESS_CONSISTENT);

        List<Configuration> configuration =
                argonConfigurationManager.getConfigurationsByAllLabels(
                        Set.of("id1_v1#label1", "id1_v1#label2"));

        assertThat(configuration.size()).isEqualTo(1);
        assertThat(configuration.get(0).getId()).isEqualTo("id1_v1");
    }

    @Test
    public void getConfigurationsByAllLabels_withEmptyTable_returnsEmptyList() {
        ArgonConfigurationManager argonConfigurationManager =
                ArgonConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ArgonConfigurationManager.DataConsistencyStrategy.PROCESS_CONSISTENT);

        List<Configuration> configuration =
                argonConfigurationManager.getConfigurationsByAllLabels(
                        Set.of("id1_v1#label1", "id1_v1#label2"));

        assertThat(configuration.size()).isEqualTo(0);
    }


    @Test
    public void cleanupUnusedOlderConfigurations_withEmptyTable_notThrowsException() {
        ArgonConfigurationManager.cleanupUnusedOlderConfigurations();
    }

    @Test
    public void cleanupUnusedOlderConfigurations_retainsLatestConfigs() {
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V2);

        ArgonConfigurationManager.cleanupUnusedOlderConfigurations();

        List<Long> versions =
                ConfigurationDatabase.getInstance()
                        .configurationDao()
                        .getAllVersions(ConfigurationType.TYPE_RB_ENROLLMENT);
        assertThat(versions).containsExactlyElementsIn(List.of(2L)).inOrder();
    }

    @Test
    public void cleanupUnusedOlderConfigurations_retainsConfigsInUseByProcessConsistentStrategy() {
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ArgonConfigurationManager.getInstance(
                ConfigurationType.TYPE_RB_ENROLLMENT,
                ArgonConfigurationManager.DataConsistencyStrategy.PROCESS_CONSISTENT);
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V2);

        ArgonConfigurationManager.cleanupUnusedOlderConfigurations();

        List<Long> versions =
                ConfigurationDatabase.getInstance()
                        .configurationDao()
                        .getAllVersions(ConfigurationType.TYPE_RB_ENROLLMENT);
        assertThat(versions).containsExactlyElementsIn(List.of(2L, 1L)).inOrder();
    }

    @Test
    public void
    cleanupUnusedOlderConfigurations_retainsConfigsInUseByInstantiationConsistentStrategy() {
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ArgonConfigurationManager argonConfigurationManager =
                ArgonConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ArgonConfigurationManager.DataConsistencyStrategy
                                .USE_VERSION_AT_INSTANTIATION);
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V2);

        ArgonConfigurationManager.cleanupUnusedOlderConfigurations();
        // This will usage prevent the ArgonConfigurationManager instance from being garbage
        // collected.
        argonConfigurationManager.getConfigurations();

        List<Long> versions =
                ConfigurationDatabase.getInstance()
                        .configurationDao()
                        .getAllVersions(ConfigurationType.TYPE_RB_ENROLLMENT);
        assertThat(versions).containsExactlyElementsIn(List.of(2L, 1L)).inOrder();
    }

    @Test
    public void
    cleanupUnusedOlderConfigurations_deletesConfigsReleasedByInstantiationConsistentStrategy()
            throws InterruptedException {
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        WeakReference<ArgonConfigurationManager> argonConfigurationManagerWeakRef =
                new WeakReference<>(
                        ArgonConfigurationManager.getInstance(
                                ConfigurationType.TYPE_RB_ENROLLMENT,
                                ArgonConfigurationManager.DataConsistencyStrategy
                                        .USE_VERSION_AT_INSTANTIATION));
        ArgonConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V2);
        // This ensures the instance is garbage collected before triggering cleanup
        @SuppressWarnings("ModifiedButNotUsed")
        List<Byte[]> memoryPressure = new ArrayList<>();
        while (argonConfigurationManagerWeakRef.get() != null) {
            int allocationSize = 1024 * 1024; // allocate 1MB on each attempt
            memoryPressure.add(new Byte[allocationSize]);
            System.gc();
            TimeUnit.SECONDS.sleep(1);
        }

        ArgonConfigurationManager.cleanupUnusedOlderConfigurations();

        List<Long> versions =
                ConfigurationDatabase.getInstance()
                        .configurationDao()
                        .getAllVersions(ConfigurationType.TYPE_RB_ENROLLMENT);
        assertThat(versions).containsExactlyElementsIn(List.of(2L));
    }
}
