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
import com.android.adservices.service.proto.config_delivery.VersionedConfiguration;

import java.util.ArrayList;
import java.util.Set;
import java.util.List;

@Dao
public abstract class ConfigurationDao {
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
    protected abstract List<LabelEntity> getLabelEntitiesByConfigRowId(long configRowId);

    /**
     * Gets the maximum config_row_id from the configurations table.
     *
     * @return The maximum config_row_id.
     */
    @Query("SELECT MAX(config_row_id) FROM configurations")
    public abstract long getLastConfigRowId();

    @Query(
            """
                SELECT MAX(version) FROM configurations
                WHERE type = :type
                ORDER BY version DESC
                LIMIT 1
            """)
    public abstract long getLatestVersion(int type);

    /**
     * Gets all versions of the configuration entities of a given type, ordered from latest to
     * oldest.
     *
     * @param type The type of the configuration entities.
     * @return A list of all versions of the configuration entities of the given type.
     */
    @Query("SELECT DISTINCT version FROM configurations WHERE type = :type ORDER BY version DESC")
    public abstract List<Long> getAllVersions(int type);

    /**
     * Gets the configuration entities of a given type and version.
     *
     * @param type The type of the configuration entities.
     * @param version The version of the configuration entities.
     * @return The latest configuration entities of the given type, or empty list if no such
     *     entities exist.
     */
    @Query("SELECT * FROM configurations WHERE type = :type AND version = :version")
    public abstract List<ConfigurationEntity> getConfigurationEntities(int type, long version);

    /**
     * Gets the number of configuration entities existing for the given type and version.
     *
     * @param type The type of the configuration entities.
     * @param version The version of the configuration entities.
     * @return The count of latest configuration entities of the given type.
     */
    @Query("SELECT COUNT(*) FROM configurations WHERE type = :type AND version = :version")
    public abstract long getConfigurationEntitiesCount(int type, long version);

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
    @VisibleForTesting
    public abstract void deleteConfigurationEntities(int type, List<Long> versions);

    /**
     * Inserts the given configuration entities and label entities.
     *
     * @param versionedConfiguration The versioned configuration to insert.
     */
    @Transaction
    public void insertConfigurations(VersionedConfiguration versionedConfiguration) {
        List<ConfigurationEntity> configurationEntities = new ArrayList<>();
        List<LabelEntity> labelEntities = new ArrayList<>();

        Configuration configuration = versionedConfiguration.getConfiguration();
        long currentConfigRowId = getLastConfigRowId() + 1;
        for (ConfigurationRecord configurationRecord :
                configuration.getConfigurationRecordsList()) {
            configurationEntities.add(
                    ConfigurationEntity.builder()
                            .setConfigRowId(currentConfigRowId)
                            .setType(configuration.getConfigurationType().getNumber())
                            .setVersion(versionedConfiguration.getVersion())
                            .setId(configurationRecord.getId())
                            .setValue(configurationRecord.getValue().toByteArray())
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
            int type, long version, String id);

    /**
     * Gets the configuration entities associated with any of the specified labels for the given
     * type and version
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
    public abstract List<ConfigurationEntity> getConfigurationEntitiesByAnyLabel(
            int configurationType, long version, Set<String> labels);

    /**
     * Gets the configuration entities associated with all of the specified labels for the given
     * type and version
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
    public abstract List<ConfigurationEntity> getConfigurationEntitiesByAllLabels(
            int configurationType, long version, Set<String> labels, int labelsCount);
}
