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

import static com.android.adservices.data.configdelivery.ConfigurationManager.DataConsistencyStrategy.*;

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.service.proto.config_delivery.ConfigurationType;
import com.android.adservices.service.proto.config_delivery.VersionedConfiguration;
import com.android.internal.annotations.GuardedBy;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages configurations, providing methods to retrieve and manipulate configuration data based on
 * different consistency strategies.
 *
 * <p>The {@code ConfigurationManager} provides an interface to interact with configuration data
 * stored in the {@link ConfigurationDatabase}. It supports different {@link
 * DataConsistencyStrategy} options to handle how configuration updates and versioning are treated,
 * offering flexibility for various use cases.
 */
public class ConfigurationManager {

    /**
     * Enum defining data consistency strategies for the ConfigurationManager.
     *
     * <p>This enum controls how the ConfigurationManager handles updates and versioning of
     * configuration data. It offers different strategies to balance between always using the newest
     * configuration and maintaining consistency within a specific context (instance, process,
     * etc.).
     *
     * <p>See <a href="http://go/rb-config-manager-data-consistency-strategies">
     * go/rb-config-manager-data-consistency-strategies</a> for more details.
     */
    public enum DataConsistencyStrategy {

        /**
         * Always uses the latest available configuration version.
         *
         * <p>The ConfigurationManager will fetch and use the most recent configuration available in
         * the database *every time* an operation is performed. This guarantees that the operation
         * performed will always use the latest configuration available. Depending on your use case,
         * using this strategy might lead to data inconsistencies as the configuration might change
         * between operations within the same logical context.
         *
         * <p>Example:
         *
         * <ol>
         *   <li>ConfigurationManager instance is created. Version 1 is the latest.
         *   <li>A background job downloads Version 2.
         *   <li>A second operation using the *same* ConfigurationManager instance is performed. It
         *       uses Version 2.
         * </ol>
         */
        USE_LATEST_VERSION,

        /**
         * Uses the configuration version that was latest when the ConfigurationManager instance was
         * created.
         *
         * <p>This strategy ensures that a single instance of the ConfigurationManager operates on a
         * consistent snapshot of the configuration. Newer versions downloaded in the background
         * *will not* affect this instance. This is useful to prevent unexpected behavior changes
         * during the lifetime of a single ConfigurationManager instance.
         *
         * <p>Example:
         *
         * <ol>
         *   <li>ConfigurationManager instance is created. Version 1 is the latest.
         *   <li>A background job downloads Version 2.
         *   <li>A second operation using the *same* ConfigurationManager instance is performed. It
         *       *still* uses Version 1.
         * </ol>
         */
        USE_VERSION_AT_INSTANTIATION,

        /**
         * Uses the configuration version that was latest when the *first* ConfigurationManager
         * instance with PROCESS_CONSISTENT strategy was created in the current process.
         *
         * <p>This strategy provides process-wide consistency. All ConfigurationManager instances
         * will use the same configuration version, even if new instances are created later. This is
         * useful in long-running processes or services where you want all parts of the application
         * to use the same configuration version throughout life of the process.
         *
         * <p>Example:
         *
         * <ol>
         *   <li>ConfigurationManager instance is created in the process. Version 1 is the latest.
         *   <li>A background job downloads Version 2.
         *   <li>An operation is performed using the first instance (or any other instance created
         *       later). It uses Version 1.
         *   <li>A second ConfigurationManager instance is created.
         *   <li>An operation is performed using the second instance. It *also* uses Version 1.
         *   <li>The process restarts. The next ConfigurationManager instance will use the latest
         *       version at *that* time.
         * </ol>
         */
        PROCESS_CONSISTENT,
    }

    private static final Object processConsistentInstancesLock = new Object();

    @GuardedBy("processConsistentInstancesLock")
    private static final ConcurrentHashMap<ConfigurationType, ConfigurationManager>
            processConsistentInstances = new ConcurrentHashMap<>();

    private final DataConsistencyStrategy dataConsistencyStrategy;
    private final ConfigurationType configurationType;

    /**
     * The configuration version to use, or null if no configuration exists.
     *
     * <p>This version is only valid for {@link
     * DataConsistencyStrategy#USE_VERSION_AT_INSTANTIATION} and {@link
     * DataConsistencyStrategy#PROCESS_CONSISTENT} consistency. For {@link
     * DataConsistencyStrategy#USE_LATEST_VERSION}, this value is ignored and the latest version is
     * fetched every time an operation is performed.
     */
    private Long configurationVersion;

    private ConfigurationManager(
            DataConsistencyStrategy dataConsistencyStrategy, ConfigurationType configurationType) {
        this.dataConsistencyStrategy = dataConsistencyStrategy;
        this.configurationType = configurationType;
    }

    private ConfigurationManager(
            DataConsistencyStrategy dataConsistencyStrategy,
            ConfigurationType configurationType,
            Long configurationVersion) {
        this.dataConsistencyStrategy = dataConsistencyStrategy;
        this.configurationType = configurationType;
        this.configurationVersion = configurationVersion;
    }

    /**
     * Returns an instance of {@link ConfigurationManager} based on the provided {@link
     * ConfigurationType} and {@link DataConsistencyStrategy}.
     *
     * @param configurationType The type of configuration to query.
     * @param dataConsistencyStrategy The strategy to use for data consistency.
     * @return A {@link ConfigurationManager} instance.
     */
    public static ConfigurationManager getInstance(
            ConfigurationType configurationType, DataConsistencyStrategy dataConsistencyStrategy) {
        switch (dataConsistencyStrategy) {
            case USE_LATEST_VERSION:
                return new ConfigurationManager(USE_LATEST_VERSION, configurationType);
            case USE_VERSION_AT_INSTANTIATION:
                return new ConfigurationManager(
                        USE_VERSION_AT_INSTANTIATION,
                        configurationType,
                        ConfigurationDatabase.getInstance()
                                .configurationDao()
                                .getLatestVersion(configurationType));
            // For PROCESS_CONSISTENT, the ConfigurationManager must be a singleton.
            case PROCESS_CONSISTENT:
                // Initialization pattern recommended on page 334 of "Effective Java" 3rd edition.
                // Author states it provided 1.4x performance improvement.
                // Lint is not smart enough to understand the optimization.
                @SuppressWarnings("GuardedBy")
                ConfigurationManager singleReadResult =
                        processConsistentInstances.get(configurationType);
                if (singleReadResult != null) {
                    return singleReadResult;
                }
                synchronized (processConsistentInstancesLock) {
                    ConfigurationManager pSConfigurationManager =
                            processConsistentInstances.get(configurationType);
                    if (pSConfigurationManager == null) {
                        pSConfigurationManager =
                                new ConfigurationManager(
                                        PROCESS_CONSISTENT,
                                        configurationType,
                                        ConfigurationDatabase.getInstance()
                                                .configurationDao()
                                                .getLatestVersion(configurationType));
                        processConsistentInstances.put(configurationType, pSConfigurationManager);
                    }
                    return pSConfigurationManager;
                }
            default:
                throw new IllegalArgumentException(
                        "Unhandled data consistency strategy " + dataConsistencyStrategy.name());
        }
    }

    /**
     * Inserts Configurations into the Configurations database.
     *
     * <p>If a configuration with the *same type and version* already exists in the database, the
     * insertion will be skipped even if the data is different, this is because configuration
     * updates *must* be performed by creating a new {@link VersionedConfiguration} with an
     * incremented version number.
     *
     * @param versionedConfiguration Configurations to insert.
     */
    public static void insertConfigurationsIfNotExist(
            VersionedConfiguration versionedConfiguration) {
        if (ConfigurationDatabase.getInstance()
                        .configurationDao()
                        .getConfigurationEntitiesCount(
                                versionedConfiguration.getConfiguration().getConfigurationType(),
                                versionedConfiguration.getVersion())
                == 0) {
            ConfigurationDatabase.getInstance()
                    .configurationDao()
                    .insertConfigurations(versionedConfiguration);
            LogUtil.d(
                    "Inserted %s configurations version %d into configurations DB",
                    versionedConfiguration.getConfiguration().getConfigurationType().name(),
                    versionedConfiguration.getVersion());
        } else {
            LogUtil.d(
                    "%s configuration version %d already exists in DB. Skipped inserting"
                            + " configurations.",
                    versionedConfiguration.getConfiguration().getConfigurationType().name(),
                    versionedConfiguration.getVersion());
        }
    }

    /**
     * Returns the latest version of the configuration for the current {@link ConfigurationType}.
     *
     * @return the latest version of the configuration, or -1 if no configuration exists.
     */
    public Long getLatestVersion() {
        return ConfigurationDatabase.getInstance()
                .configurationDao()
                .getLatestVersion(configurationType);
    }

    /**
     * Retrieves all configurations for the current {@link ConfigurationType} and version.
     *
     * @return A list of all configurations for the current type and version.
     */
    public List<Configuration> getConfigurations() {
        Long configurationVersion = getConfigurationVersion();
        if (configurationVersion == null) {
            // Version is null, so no configurations exist.
            return Collections.emptyList();
        }
        return ConfigurationDatabase.getInstance()
                .configurationDao()
                .getConfigurationEntities(configurationType, configurationVersion)
                .stream()
                .map(ConfigurationManager::toConfiguration)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a configuration by its ID.
     *
     * @param configId The ID of the configuration to retrieve.
     * @return The configuration with the specified ID, or null if no such configuration exists.
     */
    @Nullable
    public Configuration getConfigurationById(String configId) {
        Long configurationVersion = getConfigurationVersion();
        if (configurationVersion == null) {
            // Version is null, so no configurations exist.
            return null;
        }
        return toConfiguration(
                ConfigurationDatabase.getInstance()
                        .configurationDao()
                        .getConfigurationEntityById(
                                configurationType, configurationVersion, configId));
    }

    /**
     * Retrieves a list of configurations that have at least one of the specified labels.
     *
     * @param labels The set of labels to search for.
     * @return A list of configurations that have at least one of the specified labels.
     */
    public List<Configuration> getConfigurationsByAnyLabel(Set<String> labels) {
        Long configurationVersion = getConfigurationVersion();
        if (configurationVersion == null) {
            // Version is null, so no configurations exist.
            return Collections.emptyList();
        }
        return ConfigurationDatabase.getInstance()
                .configurationDao()
                .getConfigurationEntitiesByAnyLabel(
                        configurationType, getConfigurationVersion(), labels)
                .stream()
                .map(ConfigurationManager::toConfiguration)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a list of configurations that have all the specified labels.
     *
     * @param labels The set of labels that must all be present in the configurations.
     * @return A list of configurations that have all the specified labels.
     */
    public List<Configuration> getConfigurationsByAllLabels(Set<String> labels) {
        Long configurationVersion = getConfigurationVersion();
        if (configurationVersion == null) {
            // Version is null, so no configurations exist.
            return Collections.emptyList();
        }
        return ConfigurationDatabase.getInstance()
                .configurationDao()
                .getConfigurationEntitiesByAllLabels(
                        configurationType, configurationVersion, labels, labels.size())
                .stream()
                .map(ConfigurationManager::toConfiguration)
                .collect(Collectors.toList());
    }

    /**
     * Converts a {@link ConfigurationEntity} to a {@link Configuration} object.
     *
     * @param configurationEntity The entity to convert.
     * @return The converted {@link Configuration} object, or null if the input is null.
     */
    @Nullable
    private static Configuration toConfiguration(ConfigurationEntity configurationEntity) {
        if (configurationEntity == null) {
            return null;
        }
        return new Configuration(configurationEntity.getId(), configurationEntity.getValue());
    }

    /**
     * Returns the configuration version based on the selected {@link DataConsistencyStrategy}.
     *
     * @return the configuration version to use, or null if no configuration exists.
     */
    private Long getConfigurationVersion() {
        if (this.dataConsistencyStrategy == USE_LATEST_VERSION) {
            return getLatestVersion();
        } else {
            return this.configurationVersion;
        }
    }
}
