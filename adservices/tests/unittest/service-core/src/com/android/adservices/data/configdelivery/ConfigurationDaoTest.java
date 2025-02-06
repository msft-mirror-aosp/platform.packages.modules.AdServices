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

import androidx.room.Room;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.android.adservices.service.proto.RbEnrollment;
import com.android.adservices.service.proto.config_delivery.Configuration;
import com.android.adservices.service.proto.config_delivery.ConfigurationRecord;
import com.android.adservices.service.proto.config_delivery.ConfigurationType;
import com.android.adservices.service.proto.config_delivery.VersionedConfiguration;

import com.google.protobuf.Any;

@RunWith(AndroidJUnit4.class)
public class ConfigurationDaoTest {
    private ConfigurationDatabase configurationDatabase;
    private ConfigurationDao configurationDao;

    private static final long VERSION_1 = 1;
    private static final long VERSION_2 = 2;
    private static final long VERSION_3 = 3;

    private static final long configurationEntity1_v1_row_id = 1;
    private static final long configurationEntity2_v1_row_id = 2;
    private static final long configurationEntity1_v2_row_id = 3;
    private static final long configurationEntity2_v2_row_id = 4;

    //  Rb enrollment version 1 test configuration data
    private static final ConfigurationEntity configurationEntity1_v1 =
            ConfigurationEntity.create(
                    configurationEntity1_v1_row_id,
                    ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(),
                    VERSION_1,
                    "id1",
                    "config1".getBytes());
    private static final ConfigurationEntity configurationEntity2_v1 =
            ConfigurationEntity.create(
                    configurationEntity2_v1_row_id,
                    ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(),
                    VERSION_1,
                    "id2",
                    "config2".getBytes());
    private static final LabelEntity labelEntity1_v1 =
            LabelEntity.create(configurationEntity1_v1_row_id, "label1");
    private static final LabelEntity labelEntity2_v1 =
            LabelEntity.create(configurationEntity2_v1_row_id, "label2");

    //  Rb enrollment version 2 test configuration data
    private static final ConfigurationEntity configurationEntity1_v2 =
            ConfigurationEntity.create(
                    configurationEntity1_v2_row_id,
                    ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(),
                    VERSION_2,
                    "id1",
                    "v2_config1".getBytes());
    private static final ConfigurationEntity configurationEntity2_v2 =
            ConfigurationEntity.create(
                    configurationEntity2_v2_row_id,
                    ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(),
                    VERSION_2,
                    "id2",
                    "v2_config2".getBytes());
    private static final LabelEntity labelEntity1_v2 =
            LabelEntity.create(configurationEntity1_v2_row_id, "label1");
    private static final LabelEntity labelEntity2_v2 =
            LabelEntity.create(configurationEntity2_v2_row_id, "label2");

    @Before
    public void setUp() {
        configurationDatabase =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                ConfigurationDatabase.class)
                        .build();
        configurationDao = configurationDatabase.configurationDao();
    }

    @After
    public void tearDown() {
        configurationDatabase.close();
    }

    @Test
    public void testGetLastConfigRowId_returnsLastRowId() {
        configurationDao.insertConfigurationEntities(
                Arrays.asList(configurationEntity1_v1, configurationEntity2_v1));

        long lastConfigRowId = configurationDao.getLastConfigRowId();

        assertThat(lastConfigRowId).isEqualTo(configurationEntity2_v1_row_id);
    }

    @Test
    public void testGetLastConfigRowId_returnsZeroWhenTableEmpty() {
        long lastConfigRowId = configurationDao.getLastConfigRowId();

        assertThat(lastConfigRowId).isEqualTo(0);
    }

    @Test
    public void testGetAllVersions_shouldReturnAllVersionsOrderedDescending() {
        configurationDao.insertConfigurationEntities(
                Arrays.asList(
                        configurationEntity1_v1,
                        configurationEntity2_v1,
                        ConfigurationEntity.create(
                                configurationEntity2_v1_row_id,
                                ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(),
                                VERSION_3,
                                "id1",
                                "config1".getBytes()),
                        configurationEntity1_v2,
                        configurationEntity2_v2));

        List<Long> allVersionsReturned =
                configurationDao.getAllVersions(ConfigurationType.TYPE_RB_ENROLLMENT.getNumber());

        List<Long> expectedVersions = Arrays.asList(3L, 2L, 1L);
        assertThat(allVersionsReturned).containsExactlyElementsIn(expectedVersions).inOrder();
    }

    @Test
    public void testGetAllVersions_shouldReturnEmptyListWhenNoVersions() {
        List<Long> allVersions =
                configurationDao.getAllVersions(ConfigurationType.TYPE_RB_ENROLLMENT.getNumber());

        assertThat(allVersions).isEmpty();
    }

    @Test
    public void testInsertConfigurations() throws Exception {
        RbEnrollment rbEnrollmentConfig1 =
                RbEnrollment.newBuilder().addSdkNames("com.sample.com").build();
        Any any = Any.parseFrom(rbEnrollmentConfig1.toByteString());
        VersionedConfiguration versionedConfiguration =
                VersionedConfiguration.newBuilder()
                        .setVersion(VERSION_1)
                        .setConfiguration(
                                Configuration.newBuilder()
                                        .setConfigurationType(ConfigurationType.TYPE_RB_ENROLLMENT)
                                        .addConfigurationRecords(
                                                ConfigurationRecord.newBuilder()
                                                        .setId("id1")
                                                        .addLabels("label1")
                                                        .addLabels("label2")
                                                        .setValue(any)
                                                        .build())
                                        .build())
                        .build();
        configurationDao.insertConfigurations(versionedConfiguration);

        List<ConfigurationEntity> returnedList =
                configurationDao.getConfigurationEntities(
                        ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(), VERSION_1);

        assertThat(returnedList).hasSize(1);
        assertThat(returnedList.get(0).getConfigRowId()).isEqualTo(1);
        assertThat(returnedList.get(0).getType()).isEqualTo(1);
        assertThat(returnedList.get(0).getVersion()).isEqualTo(1);
        assertThat(returnedList.get(0).getId()).isEqualTo("id1");
        assertThat(RbEnrollment.parseFrom(returnedList.get(0).getValue()).toString())
                .isEqualTo(rbEnrollmentConfig1.toString());
    }

    @Test
    public void testInsertConfigurations_withEmptyConfigurationsList() {
        configurationDao.insertConfigurations(
                VersionedConfiguration.newBuilder().setVersion(VERSION_1).build());

        List<ConfigurationEntity> returnedList =
                configurationDao.getConfigurationEntities(
                        ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(), VERSION_1);

        assertThat(returnedList).isEmpty();
    }

    @Test
    public void testInsertAndReadConfigurationEntity_conflictReplace() {
        configurationDao.insertConfigurationEntities(
                Collections.singletonList(configurationEntity1_v1));
        ConfigurationEntity updatedEntity =
                ConfigurationEntity.create(
                        configurationEntity1_v1_row_id,
                        ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(),
                        VERSION_1,
                        "id1",
                        "config1_updated".getBytes());
        configurationDao.insertConfigurationEntities(Collections.singletonList(updatedEntity));

        List<ConfigurationEntity> returnedList =
                configurationDao.getConfigurationEntities(
                        ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(), VERSION_1);

        assertThat(returnedList).containsExactly(updatedEntity);
    }

    @Test
    public void testInsertConfigurationEntities() {
        configurationDao.insertConfigurationEntities(
                Arrays.asList(configurationEntity1_v1, configurationEntity2_v1));

        List<ConfigurationEntity> returnedList =
                configurationDao.getConfigurationEntities(
                        ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(), VERSION_1);

        assertThat(returnedList).containsExactly(configurationEntity1_v1, configurationEntity2_v1);
    }

    @Test
    public void testInsertAndReadLabelEntity() {
        // There is no direct read method for LabelEntity, so we need to use
        // getConfigurationEntitiesByAnyLabel to indirectly verify the insertion.
        // We need to first insert corresponding ConfigurationEntity to make the join work.
        configurationDao.insertConfigurationEntities(
                Arrays.asList(configurationEntity1_v1, configurationEntity2_v1));
        configurationDao.insertLabelEntities(Arrays.asList(labelEntity1_v1, labelEntity2_v1));

        Set<String> labels = Set.of("label1", "label2");
        List<ConfigurationEntity> configurationEntities =
                configurationDao.getConfigurationEntitiesByAnyLabel(
                        ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(), VERSION_1, labels);

        assertThat(configurationEntities)
                .containsExactly(configurationEntity1_v1, configurationEntity2_v1);
    }

    @Test
    public void testInsertAndReadLabelEntity_conflictReplace() {
        // Populate configuration and label entities
        configurationDao.insertConfigurationEntities(
                Collections.singletonList(configurationEntity1_v1));
        configurationDao.insertLabelEntities(Collections.singletonList(labelEntity1_v1));
        long configRowId =
                configurationDao
                        .getConfigurationEntities(
                                ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(), VERSION_1)
                        .get(0)
                        .getConfigRowId();

        // Replace label
        configurationDao.insertLabelEntities(Collections.singletonList(labelEntity1_v1));

        List<LabelEntity> labelEntities =
                configurationDao.getLabelEntitiesByConfigRowId(configRowId);
        assertThat(labelEntities).containsExactly(labelEntity1_v1);
    }

    @Test
    public void testGetConfigurationEntitiesCount() {
        configurationDao.insertConfigurationEntities(
                Arrays.asList(configurationEntity1_v1, configurationEntity2_v1));

        long count =
                configurationDao.getConfigurationEntitiesCount(
                        ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(), VERSION_1);

        assertThat(count).isEqualTo(2);
    }

    @Test
    public void testDeleteConfigurationEntities() {
        configurationDao.insertConfigurationEntities(
                Arrays.asList(configurationEntity1_v1, configurationEntity2_v1));

        configurationDao.deleteConfigurationEntities(
                ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(),
                Collections.singletonList(VERSION_1));
        List<ConfigurationEntity> returnedList =
                configurationDao.getConfigurationEntities(
                        ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(), VERSION_1);

        assertThat(returnedList).isEmpty();
    }

    @Test
    public void testDeleteConfigurationEntities_deletesLabels() {
        // Insert configuration and label entities
        configurationDao.insertConfigurationEntities(
                Collections.singletonList(configurationEntity1_v1));
        configurationDao.insertLabelEntities(Collections.singletonList(labelEntity1_v1));
        long configRowId =
                configurationDao
                        .getConfigurationEntities(
                                ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(), VERSION_1)
                        .get(0)
                        .getConfigRowId();

        // Delete configuration entities
        configurationDao.deleteConfigurationEntities(
                ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(),
                Collections.singletonList(VERSION_1));

        // To verify that no configuration entities exist
        List<LabelEntity> labelEntities =
                configurationDao.getLabelEntitiesByConfigRowId(configRowId);
        assertThat(labelEntities).isEmpty();
    }

    @Test
    public void testGetConfigurationEntityById() {
        configurationDao.insertConfigurationEntities(
                Arrays.asList(
                        configurationEntity1_v1,
                        configurationEntity2_v1,
                        configurationEntity1_v2,
                        configurationEntity2_v2));

        ConfigurationEntity returnedEntity =
                configurationDao.getConfigurationEntityById(
                        ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(), VERSION_2, "id2");

        assertThat(returnedEntity).isEqualTo(configurationEntity2_v2);
    }

    @Test
    public void testGetConfigurationEntitiesByAnyLabel() {
        // Populate configuration and label entities with different versions
        configurationDao.insertConfigurationEntities(
                Arrays.asList(
                        configurationEntity1_v1,
                        configurationEntity2_v1,
                        configurationEntity1_v2,
                        configurationEntity2_v2));
        configurationDao.insertLabelEntities(
                Arrays.asList(
                        labelEntity1_v1,
                        labelEntity2_v1,
                        labelEntity1_v2,
                        labelEntity2_v2,
                        LabelEntity.create(configurationEntity2_v2_row_id, "label3")));

        List<ConfigurationEntity> returnedList =
                configurationDao.getConfigurationEntitiesByAnyLabel(
                        ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(),
                        VERSION_2,
                        Set.of("label1", "label3"));

        // Verify matching configuration entities with returned are from latest version
        assertThat(returnedList).containsExactly(configurationEntity1_v2, configurationEntity2_v2);
    }

    @Test
    public void testGetConfigurationEntitiesByAllLabels() {
        // Populate configuration and label entities with different versions
        configurationDao.insertConfigurationEntities(
                Arrays.asList(
                        configurationEntity1_v1,
                        configurationEntity2_v1,
                        configurationEntity1_v2,
                        configurationEntity2_v2));
        configurationDao.insertLabelEntities(
                Arrays.asList(
                        labelEntity1_v1,
                        LabelEntity.create(configurationEntity1_v1_row_id, "label3"),
                        labelEntity2_v1,
                        labelEntity1_v2,
                        LabelEntity.create(configurationEntity1_v2_row_id, "label3"),
                        labelEntity2_v2));

        Set<String> labels = Set.of("label1", "label3");
        List<ConfigurationEntity> returnedList =
                configurationDao.getConfigurationEntitiesByAllLabels(
                        ConfigurationType.TYPE_RB_ENROLLMENT.getNumber(),
                        VERSION_2,
                        labels,
                        labels.size());

        // Verify matching configuration entities with returned are from latest version
        assertThat(returnedList).containsExactly(configurationEntity1_v2);
    }
}
