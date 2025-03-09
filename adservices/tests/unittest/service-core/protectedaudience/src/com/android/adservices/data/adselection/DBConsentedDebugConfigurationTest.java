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

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class DBConsentedDebugConfigurationTest extends AdServicesUnitTestCase {
    private static final boolean IS_CONSENTED = true;
    private static final Instant CREATION_TIMESTAMP = Instant.now();
    private static final Instant EXPIRY_TIMESTAMP = Instant.now().plus(Duration.ofDays(1));
    private static final String DEBUG_TOKEN = UUID.randomUUID().toString();

    @Test
    public void test_create_success() {
        long primaryKey = 123L;
        DBConsentedDebugConfiguration dbConsentedDebugConfiguration =
                DBConsentedDebugConfiguration.create(
                        primaryKey,
                        IS_CONSENTED,
                        DEBUG_TOKEN,
                        CREATION_TIMESTAMP,
                        EXPIRY_TIMESTAMP);
        DBConsentedDebugConfiguration expected =
                DBConsentedDebugConfiguration.builder()
                        .setConsentedDebugConfigurationPrimaryKey(primaryKey)
                        .setDebugToken(DEBUG_TOKEN)
                        .setIsConsentProvided(IS_CONSENTED)
                        .setCreationTimestamp(CREATION_TIMESTAMP)
                        .setExpiryTimestamp(EXPIRY_TIMESTAMP)
                        .build();
        assertThat(dbConsentedDebugConfiguration).isEqualTo(expected);
    }

    @Test
    public void test_create_primaryKeyNull() {
        DBConsentedDebugConfiguration dbConsentedDebugConfiguration =
                DBConsentedDebugConfiguration.create(
                        null, IS_CONSENTED, DEBUG_TOKEN, CREATION_TIMESTAMP, EXPIRY_TIMESTAMP);
        DBConsentedDebugConfiguration expected =
                DBConsentedDebugConfiguration.builder()
                        .setConsentedDebugConfigurationPrimaryKey(null)
                        .setDebugToken(DEBUG_TOKEN)
                        .setIsConsentProvided(IS_CONSENTED)
                        .setCreationTimestamp(CREATION_TIMESTAMP)
                        .setExpiryTimestamp(EXPIRY_TIMESTAMP)
                        .build();
        assertThat(dbConsentedDebugConfiguration).isEqualTo(expected);
    }

    @Test
    public void test_create_debugTokenEmpty() {
        long primaryKey = 123L;
        DBConsentedDebugConfiguration dbConsentedDebugConfiguration =
                DBConsentedDebugConfiguration.create(
                        primaryKey, IS_CONSENTED, "", CREATION_TIMESTAMP, EXPIRY_TIMESTAMP);
        DBConsentedDebugConfiguration expected =
                DBConsentedDebugConfiguration.builder()
                        .setConsentedDebugConfigurationPrimaryKey(primaryKey)
                        .setDebugToken("")
                        .setIsConsentProvided(IS_CONSENTED)
                        .setCreationTimestamp(CREATION_TIMESTAMP)
                        .setExpiryTimestamp(EXPIRY_TIMESTAMP)
                        .build();
        assertThat(dbConsentedDebugConfiguration).isEqualTo(expected);
    }

    @Test
    public void test_create_debugTokenNull_throwsException() {
        Assert.assertThrows(
                NullPointerException.class,
                () ->
                        DBConsentedDebugConfiguration.create(
                                null, IS_CONSENTED, null, CREATION_TIMESTAMP, EXPIRY_TIMESTAMP));
    }

    @Test
    public void test_create_creationTimeStampNull_throwsException() {
        Assert.assertThrows(
                NullPointerException.class,
                () ->
                        DBConsentedDebugConfiguration.create(
                                null, IS_CONSENTED, DEBUG_TOKEN, null, EXPIRY_TIMESTAMP));
    }

    @Test
    public void test_create_expiryNull_throwsException() {
        Assert.assertThrows(
                NullPointerException.class,
                () ->
                        DBConsentedDebugConfiguration.create(
                                null, IS_CONSENTED, DEBUG_TOKEN, CREATION_TIMESTAMP, null));
    }
}
