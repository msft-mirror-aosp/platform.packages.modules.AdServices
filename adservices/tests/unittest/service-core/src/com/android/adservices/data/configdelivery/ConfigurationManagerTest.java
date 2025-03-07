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
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public class ConfigurationManagerTest {
    private ConfigurationDatabase configurationDatabase;

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

    @Before
    public void setUp() {
        configurationDatabase = ConfigurationDatabase.getInstance();
    }

    @After
    public void tearDown() {
        configurationDatabase.clearAllTables();
    }

    @Test
    public void insertConfigurationsIfNotExist_insertsNewConfigurations() {
        ConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ConfigurationManager configurationManager =
                ConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ConfigurationManager.DataConsistencyStrategy.USE_LATEST_VERSION);

        List<Configuration> configurations = configurationManager.getConfigurations();

        assertThat(configurations.size()).isEqualTo(1);
        assertThat(configurations.get(0).getId()).isEqualTo("id1_v1");
    }

    @Test
    public void insertConfigurationsIfNotExist_doesNotInsertExistingConfigurations() {
        ConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);

        long lastConfigRowIdBeforeExistingConfigInsert =
                ConfigurationDatabase.getInstance().configurationDao().getLastConfigRowId();
        ConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        long lastConfigRowIdAfterExistingConfigInsert =
                ConfigurationDatabase.getInstance().configurationDao().getLastConfigRowId();

        assertThat(lastConfigRowIdBeforeExistingConfigInsert)
                .isEqualTo(lastConfigRowIdAfterExistingConfigInsert);
    }

    @Test
    public void createConfigurationManager_withUseLatestVersionStrategy_alwaysUsesLatestVersion() {
        ConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ConfigurationManager configurationManager =
                ConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ConfigurationManager.DataConsistencyStrategy.USE_LATEST_VERSION);

        List<Configuration> configurationsBeforeV2Download =
                configurationManager.getConfigurations();

        ConfigurationDatabase.getInstance()
                .configurationDao()
                .insertConfigurations(ENROLLMENT_CONFIG_V2);

        List<Configuration> configurationsAfterV2Download =
                configurationManager.getConfigurations();

        assertThat(configurationsBeforeV2Download.get(0).getId()).isEqualTo("id1_v1");
        assertThat(configurationsAfterV2Download.get(0).getId()).isEqualTo("id1_v2");
    }

    @Test
    public void
            createConfManager_withUseVersionAtInstantiationStrategy_usesSameVersionPerInstance() {
        ConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ConfigurationManager configurationManagerInstance1 =
                ConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ConfigurationManager.DataConsistencyStrategy.USE_VERSION_AT_INSTANTIATION);

        List<Configuration> configurationsBeforeV2DownloadFromInstance1 =
                configurationManagerInstance1.getConfigurations();

        ConfigurationDatabase.getInstance()
                .configurationDao()
                .insertConfigurations(ENROLLMENT_CONFIG_V2);

        List<Configuration> configurationsAfterV2DownloadFromInstance1 =
                configurationManagerInstance1.getConfigurations();

        ConfigurationManager configurationManagerInstance2 =
                ConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ConfigurationManager.DataConsistencyStrategy.USE_VERSION_AT_INSTANTIATION);

        List<Configuration> configurationsAfterV2DownloadFromInstance2 =
                configurationManagerInstance2.getConfigurations();

        assertThat(configurationsBeforeV2DownloadFromInstance1.get(0).getId()).isEqualTo("id1_v1");
        assertThat(configurationsAfterV2DownloadFromInstance1.get(0).getId()).isEqualTo("id1_v1");
        assertThat(configurationsAfterV2DownloadFromInstance2.get(0).getId()).isEqualTo("id1_v2");
    }

    @Test
    public void createConfigurationManager_withProcessConsistentStrategy_alwaysUsesSameVersion() {
        ConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ConfigurationManager configurationManager =
                ConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ConfigurationManager.DataConsistencyStrategy.PROCESS_CONSISTENT);

        List<Configuration> configurationsBeforeV2Download =
                configurationManager.getConfigurations();

        ConfigurationDatabase.getInstance()
                .configurationDao()
                .insertConfigurations(ENROLLMENT_CONFIG_V2);

        List<Configuration> configurationsAfterV2Download =
                configurationManager.getConfigurations();

        assertThat(configurationsBeforeV2Download.get(0).getId()).isEqualTo("id1_v1");
        assertThat(configurationsAfterV2Download.get(0).getId()).isEqualTo("id1_v1");
    }

    @Test
    public void getConfigurationById_returnsMatchingConfiguration() {
        ConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ConfigurationManager configurationManager =
                ConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ConfigurationManager.DataConsistencyStrategy.USE_LATEST_VERSION);

        Configuration configuration = configurationManager.getConfigurationById("id1_v1");
        RbEnrollment rbEnrollment = configuration.getValue(RbEnrollment.getDefaultInstance());

        assertThat(configuration.getId()).isEqualTo("id1_v1");
        assertThat(rbEnrollment.getEnrolledSite()).isEqualTo("https://example.com");
    }

    @Test
    public void getConfigurationById_withEmptyTable_returnsNull() {
        ConfigurationManager configurationManager =
                ConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ConfigurationManager.DataConsistencyStrategy.USE_LATEST_VERSION);

        Configuration configuration = configurationManager.getConfigurationById("id1_v1");

        assertThat(configuration).isNull();
    }

    @Test
    public void getConfigurationsByAnyLabel_returnsMatchingConfigurations() {
        ConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ConfigurationManager configurationManager =
                ConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ConfigurationManager.DataConsistencyStrategy.PROCESS_CONSISTENT);

        List<Configuration> configuration =
                configurationManager.getConfigurationsByAnyLabel(
                        Set.of("id1_v1#label1", "id1_v1#label2"));

        assertThat(configuration.size()).isEqualTo(1);
        assertThat(configuration.get(0).getId()).isEqualTo("id1_v1");
    }

    @Test
    public void getConfigurationsByAnyLabel_withEmptyTable_returnsEmptyList() {
        ConfigurationManager configurationManager =
                ConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ConfigurationManager.DataConsistencyStrategy.PROCESS_CONSISTENT);

        List<Configuration> configuration =
                configurationManager.getConfigurationsByAnyLabel(
                        Set.of("id1_v1#label1", "id1_v1#label2"));

        assertThat(configuration.size()).isEqualTo(0);
    }

    @Test
    public void getConfigurationsByAllLabels_returnsMatchingConfigurations() {
        ConfigurationManager.insertConfigurationsIfNotExist(ENROLLMENT_CONFIG_V1);
        ConfigurationManager configurationManager =
                ConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ConfigurationManager.DataConsistencyStrategy.PROCESS_CONSISTENT);

        List<Configuration> configuration =
                configurationManager.getConfigurationsByAllLabels(
                        Set.of("id1_v1#label1", "id1_v1#label2"));

        assertThat(configuration.size()).isEqualTo(1);
        assertThat(configuration.get(0).getId()).isEqualTo("id1_v1");
    }

    @Test
    public void getConfigurationsByAllLabels_withEmptyTable_returnsEmptyList() {
        ConfigurationManager configurationManager =
                ConfigurationManager.getInstance(
                        ConfigurationType.TYPE_RB_ENROLLMENT,
                        ConfigurationManager.DataConsistencyStrategy.PROCESS_CONSISTENT);

        List<Configuration> configuration =
                configurationManager.getConfigurationsByAllLabels(
                        Set.of("id1_v1#label1", "id1_v1#label2"));

        assertThat(configuration.size()).isEqualTo(0);
    }
}
