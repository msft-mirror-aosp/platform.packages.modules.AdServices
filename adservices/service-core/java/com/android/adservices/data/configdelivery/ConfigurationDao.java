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

import androidx.annotation.VisibleForTesting;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.android.adservices.service.proto.config_delivery.Configuration;
import com.android.adservices.service.proto.config_delivery.ConfigurationRecord;
import com.android.adservices.service.proto.config_delivery.ConfigurationType;
import com.android.adservices.service.proto.config_delivery.VersionedConfiguration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;

/** DAO for querying configurations related to Argon Config Delivery System. */
@Dao
public abstract class ConfigurationDao {

    @VisibleForTesting protected static int MAX_VERSIONS_TO_RETAIN_PER_TYPE = 100;

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @VisibleForTesting
    public abstract void insertConfigurationEntities(
            List<ConfigurationEntity> configurationEntities);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @VisibleForTesting
    protected abstract void insertLabelEntities(List<LabelEntity> labelEntities);

    /**
     * Gets the list of label entities associated with the given configuration row id.
     *
     * @return A list of label entities associated with the given configuration row id or empty list
     *     if no labels are associated with the given configuration row id.
     */
    @VisibleForTesting
    @Query("SELECT * FROM labels WHERE config_row_id = :configRowId")
    protected abstract ImmutableList<LabelEntity> getLabelEntitiesByConfigRowId(long configRowId);

    /**
     * Gets the maximum config_row_id from the configurations table.
     *
     * @return The maximum config_row_id.
     */
    @Query("SELECT MAX(config_row_id) FROM configurations")
    public abstract long getLastConfigRowId();

    /**
     * Gets the latest version of the configuration entities of a given type.
     *
     * @param type The type of the configuration entities.
     * @return The latest version of the configuration entities of the given type, or null if no
     *     configurations of the given type exist.
     */
    @Query("SELECT MAX(version) FROM configurations WHERE type = :type")
    public abstract Long getLatestVersion(ConfigurationType type);

    /**
     * Gets all versions of the configuration entities of a given type, ordered from latest to
     * oldest.
     *
     * @param type The type of the configuration entities.
     * @return A list of all versions of the configuration entities of the given type.
     */
    @Query("SELECT DISTINCT version FROM configurations WHERE type = :type ORDER BY version DESC")
    public abstract ImmutableList<Long> getAllVersions(ConfigurationType type);

    @Query("SELECT DISTINCT type FROM configurations")
    public abstract ImmutableList<ConfigurationType> getAllConfigurationTypes();

    @Transaction
    public ImmutableMap<ConfigurationType, Set<Long>> getAllConfigurationTypesToVersionsMap() {
        return getAllConfigurationTypes().stream()
                .distinct()
                .collect(
                        ImmutableMap.toImmutableMap(
                                Function.identity(),
                                configType -> ImmutableSet.copyOf(getAllVersions(configType))));
    }

    /**
     * Gets the configuration entities of a given type and version.
     *
     * @param type The type of the configuration entities.
     * @param version The version of the configuration entities.
     * @return The latest configuration entities of the given type, or empty list if no such
     *     entities exist.
     */
    @Query("SELECT * FROM configurations WHERE type = :type AND version = :version")
    public abstract ImmutableList<ConfigurationEntity> getConfigurationEntities(
            ConfigurationType type, long version);

    /**
     * Gets the number of configuration entities existing for the given type and version.
     *
     * @param type The type of the configuration entities.
     * @param version The version of the configuration entities.
     * @return The count of latest configuration entities of the given type.
     */
    @Query("SELECT COUNT(*) FROM configurations WHERE type = :type AND version = :version")
    public abstract long getConfigurationEntitiesCount(ConfigurationType type, long version);

    /**
     * Deletes configurations associated with the given type and versions.
     *
     * <p>Note: The labels associated with the deleted configurations will also automatically be
     * removed. This is due to a foreign key constraint between configuration entities and label
     * entities with "on delete cascade" enabled.
     */
    @Query(
            """
                DELETE FROM configurations
                    WHERE configurations.type = :type AND version IN (:versions)
            """)
    public abstract void deleteConfigurationEntities(ConfigurationType type, Set<Long> versions);

    /**
     * Inserts the given configuration entities and label entities.
     *
     * @param versionedConfiguration The versioned configuration to insert.
     */
    @Transaction
    protected void insertConfigurations(VersionedConfiguration versionedConfiguration) {
        List<ConfigurationEntity> configurationEntities = new ArrayList<>();
        List<LabelEntity> labelEntities = new ArrayList<>();

        Configuration configuration = versionedConfiguration.getConfiguration();
        long currentConfigRowId = getLastConfigRowId() + 1;
        for (ConfigurationRecord configurationRecord :
                configuration.getConfigurationRecordsList()) {
            configurationEntities.add(
                    ConfigurationEntity.builder()
                            .setConfigRowId(currentConfigRowId)
                            .setType(configuration.getConfigurationType())
                            .setVersion(versionedConfiguration.getVersion())
                            .setId(configurationRecord.getId())
                            .setValue(configurationRecord.getValue())
                            .build());
            for (String label : configurationRecord.getLabelsList()) {
                labelEntities.add(
                        LabelEntity.builder()
                                .setConfigRowId(currentConfigRowId)
                                .setLabel(label)
                                .build());
            }
            currentConfigRowId++;
        }
        insertConfigurationEntities(configurationEntities);
        insertLabelEntities(labelEntities);

        List<Long> versions = getAllVersions(configuration.getConfigurationType());

        // This is in order to prevent unintentional adding of too many versions.
        if (versions.size() > MAX_VERSIONS_TO_RETAIN_PER_TYPE) {
            deleteConfigurationEntities(
                    configuration.getConfigurationType(),
                    new HashSet<>(
                            versions.subList(MAX_VERSIONS_TO_RETAIN_PER_TYPE, versions.size())));
        }
    }

    /**
     * Gets the configuration entity of a given type, version and ID.
     *
     * @param type The type of the configuration entity.
     * @param id The ID of the configuration entity.
     * @param version The version of the configuration entity.
     * @return The latest configuration entity of the given type and ID, or null if no such entity
     *     exists.
     */
    @Query(
            """
                SELECT * FROM configurations
                WHERE type = :type AND id = :id AND version = :version
            """)
    public abstract ConfigurationEntity getConfigurationEntityById(
            ConfigurationType type, long version, String id);

    /**
     * Gets the configuration entities associated with any of the specified labels for the given
     * type and version. In other words, matching configs have at least one of the specified labels.
     *
     * @param configurationType The type of the configuration entity.
     * @param labels The set of labels to filter by.
     * @param version The version of the configuration entity.
     * @return A list of configuration entities that are associated with any of the specified
     *     labels.
     */
    @Query(
            """
                SELECT DISTINCT c.* FROM configurations c
                INNER JOIN labels l ON c.config_row_id = l.config_row_id
                WHERE c.type = :configurationType AND c.version = :version AND l.label IN (:labels)
            """)
    public abstract ImmutableList<ConfigurationEntity> getConfigurationEntitiesByAnyLabel(
            ConfigurationType configurationType, long version, Set<String> labels);

    /**
     * Gets the configuration entities that are associated with all of the specified labels for the
     * given type and version. In other words, matchings config have all the specified labels.
     *
     * @param configurationType The type of the configuration entity.
     * @param labels The set of labels to filter by.
     * @param version The version of the configuration entity.
     * @param labelsCount The number of labels passed in labels parameter.
     * @return A list of configuration entities that are associated with all of the specified
     *     labels.
     */
    @Query(
            """
                SELECT c.* FROM configurations c
                INNER JOIN labels l ON c.config_row_id = l.config_row_id
                WHERE c.type = :configurationType
                    AND c.version = :version
                    AND l.label IN (:labels)
                GROUP BY c.id, c.version, c.type
                HAVING COUNT(DISTINCT l.label) = :labelsCount
            """)
    public abstract ImmutableList<ConfigurationEntity> getConfigurationEntitiesByAllLabels(
            ConfigurationType configurationType, long version, Set<String> labels, int labelsCount);
}
