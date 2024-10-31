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

package com.android.adservices.data.adselection;

import static android.adservices.common.CommonFixture.FIXED_NOW;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class ConsentedDebugConfigurationDaoTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final boolean IS_CONSENTED = true;
    private static final String DEBUG_TOKEN = UUID.randomUUID().toString();
    private static final Instant CREATION_TIMESTAMP = FIXED_NOW;
    private static final Duration EXPIRY_DURATION = Duration.ofDays(1);
    private static final Instant EXPIRY_TIMESTAMP = CREATION_TIMESTAMP.plus(EXPIRY_DURATION);
    private static final DBConsentedDebugConfiguration DB_CONSENTED_DEBUG_CONFIGURATION =
            DBConsentedDebugConfiguration.create(
                    null, IS_CONSENTED, DEBUG_TOKEN, CREATION_TIMESTAMP, EXPIRY_TIMESTAMP);
    private ConsentedDebugConfigurationDao mConsentedDebugConfigurationDao;

    @Before
    public void setup() {
        mConsentedDebugConfigurationDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionDatabase.class)
                        .build()
                        .consentedDebugConfigurationDao();
    }

    @Test
    public void testPersistAdSelectionDebugReporting() {
        mConsentedDebugConfigurationDao.persistConsentedDebugConfiguration(
                DB_CONSENTED_DEBUG_CONFIGURATION);
    }

    @Test
    public void testGetAllActiveConsentedDebugConfigurations() {
        mConsentedDebugConfigurationDao.persistConsentedDebugConfiguration(
                DB_CONSENTED_DEBUG_CONFIGURATION);

        List<DBConsentedDebugConfiguration> consentedDebugConfigurations =
                mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                        FIXED_NOW, 1);

        assertThat(consentedDebugConfigurations).isNotNull();
        assertThat(consentedDebugConfigurations).isNotEmpty();
        assertThat(consentedDebugConfigurations.size()).isEqualTo(1);
        DBConsentedDebugConfiguration actualConsentedDebugConfiguration =
                consentedDebugConfigurations.get(0);
        assertThat(actualConsentedDebugConfiguration.getDebugToken())
                .isEqualTo(DB_CONSENTED_DEBUG_CONFIGURATION.getDebugToken());
        assertThat(actualConsentedDebugConfiguration.getIsConsentProvided())
                .isEqualTo(DB_CONSENTED_DEBUG_CONFIGURATION.getIsConsentProvided());
        assertThat(actualConsentedDebugConfiguration.getExpiryTimestamp().getEpochSecond())
                .isEqualTo(DB_CONSENTED_DEBUG_CONFIGURATION.getExpiryTimestamp().getEpochSecond());
    }

    @Test
    public void testGetAllActiveConsentedDebugConfigurations_fetchesRecentlyCreated() {
        DBConsentedDebugConfiguration dbConsentedDebugConfigurationCreatedNow =
                DBConsentedDebugConfiguration.create(
                        null,
                        IS_CONSENTED,
                        UUID.randomUUID().toString(),
                        CREATION_TIMESTAMP,
                        EXPIRY_TIMESTAMP);
        DBConsentedDebugConfiguration dbConsentedDebugConfigurationCreatedOneHourLater =
                DBConsentedDebugConfiguration.create(
                        null,
                        IS_CONSENTED,
                        UUID.randomUUID().toString(),
                        CREATION_TIMESTAMP.plus(Duration.ofHours(1)),
                        EXPIRY_TIMESTAMP);
        mConsentedDebugConfigurationDao.persistConsentedDebugConfiguration(
                dbConsentedDebugConfigurationCreatedNow);
        mConsentedDebugConfigurationDao.persistConsentedDebugConfiguration(
                dbConsentedDebugConfigurationCreatedOneHourLater);

        List<DBConsentedDebugConfiguration> consentedDebugConfigurations =
                mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                        FIXED_NOW, 1);

        assertThat(consentedDebugConfigurations).isNotNull();
        assertThat(consentedDebugConfigurations).isNotEmpty();
        assertThat(consentedDebugConfigurations.size()).isEqualTo(1);
        DBConsentedDebugConfiguration actualConsentedDebugConfiguration =
                consentedDebugConfigurations.get(0);
        assertThat(actualConsentedDebugConfiguration.getDebugToken())
                .isEqualTo(dbConsentedDebugConfigurationCreatedOneHourLater.getDebugToken());
        assertThat(actualConsentedDebugConfiguration.getIsConsentProvided())
                .isEqualTo(dbConsentedDebugConfigurationCreatedOneHourLater.getIsConsentProvided());
        assertThat(actualConsentedDebugConfiguration.getExpiryTimestamp().getEpochSecond())
                .isEqualTo(
                        dbConsentedDebugConfigurationCreatedOneHourLater
                                .getExpiryTimestamp()
                                .getEpochSecond());
    }

    @Test
    public void testGetAllActiveConsentedDebugConfigurations_doesNotFetchExpiredEntries() {
        DBConsentedDebugConfiguration expiredConsentedDebugConfiguration =
                DBConsentedDebugConfiguration.create(
                        null,
                        IS_CONSENTED,
                        DEBUG_TOKEN,
                        CREATION_TIMESTAMP,
                        CREATION_TIMESTAMP.minus(Duration.ofDays(1)));
        mConsentedDebugConfigurationDao.persistConsentedDebugConfiguration(
                expiredConsentedDebugConfiguration);

        List<DBConsentedDebugConfiguration> consentedDebugConfigurations =
                mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                        FIXED_NOW, 1);

        assertThat(consentedDebugConfigurations).isNotNull();
        assertThat(consentedDebugConfigurations).isEmpty();
    }

    @Test
    public void testDeleteAllConsentedDebugConfigurations() {
        mConsentedDebugConfigurationDao.persistConsentedDebugConfiguration(
                DB_CONSENTED_DEBUG_CONFIGURATION);

        List<DBConsentedDebugConfiguration> consentedDebugConfigurationsBeforeDelete =
                mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                        FIXED_NOW, 1);
        mConsentedDebugConfigurationDao.deleteAllConsentedDebugConfigurations();
        List<DBConsentedDebugConfiguration> consentedDebugConfigurationsAfterDelete =
                mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                        FIXED_NOW, 1);

        assertThat(consentedDebugConfigurationsBeforeDelete).isNotNull();
        assertThat(consentedDebugConfigurationsBeforeDelete).isNotEmpty();
        assertThat(consentedDebugConfigurationsBeforeDelete.size()).isEqualTo(1);
        assertThat(consentedDebugConfigurationsAfterDelete).isNotNull();
        assertThat(consentedDebugConfigurationsAfterDelete).isEmpty();
    }

    @Test
    public void testDeleteExistingConsentedDebugConfigurationsAndPersist() {
        int largeLimit = 100;
        DBConsentedDebugConfiguration dbConsentedDebugConfigurationBeforeDelete =
                DBConsentedDebugConfiguration.create(
                        null,
                        IS_CONSENTED,
                        UUID.randomUUID().toString(),
                        CREATION_TIMESTAMP,
                        EXPIRY_TIMESTAMP);
        DBConsentedDebugConfiguration dbConsentedDebugConfigurationAfterDelete =
                DBConsentedDebugConfiguration.create(
                        null,
                        IS_CONSENTED,
                        UUID.randomUUID().toString(),
                        CREATION_TIMESTAMP.plus(Duration.ofHours(1)),
                        EXPIRY_TIMESTAMP);

        mConsentedDebugConfigurationDao.deleteExistingConsentedDebugConfigurationsAndPersist(
                dbConsentedDebugConfigurationBeforeDelete);
        List<DBConsentedDebugConfiguration> dbConsentedDebugConfigurationsBeforeDelete =
                mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                        FIXED_NOW, largeLimit);
        mConsentedDebugConfigurationDao.deleteExistingConsentedDebugConfigurationsAndPersist(
                dbConsentedDebugConfigurationAfterDelete);
        List<DBConsentedDebugConfiguration> dbConsentedDebugConfigurationsAfterDelete =
                mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                        FIXED_NOW, largeLimit);

        assertThat(dbConsentedDebugConfigurationsBeforeDelete).isNotNull();
        assertThat(dbConsentedDebugConfigurationsBeforeDelete).isNotEmpty();
        assertThat(dbConsentedDebugConfigurationsBeforeDelete.size()).isEqualTo(1);
        assertThat(dbConsentedDebugConfigurationsBeforeDelete.get(0).getDebugToken())
                .isEqualTo(dbConsentedDebugConfigurationBeforeDelete.getDebugToken());

        assertThat(dbConsentedDebugConfigurationsAfterDelete).isNotNull();
        assertThat(dbConsentedDebugConfigurationsAfterDelete).isNotEmpty();
        assertThat(dbConsentedDebugConfigurationsAfterDelete.size()).isEqualTo(1);
        assertThat(dbConsentedDebugConfigurationsAfterDelete.get(0).getDebugToken())
                .isEqualTo(dbConsentedDebugConfigurationAfterDelete.getDebugToken());
    }
}
