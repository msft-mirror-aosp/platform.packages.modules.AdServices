/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.adservices.consent;

import static com.android.server.adservices.consent.ConsentManager.NOTIFICATION_DISPLAYED_ONCE;
import static com.android.server.adservices.consent.ConsentManager.STORAGE_VERSION;
import static com.android.server.adservices.consent.ConsentManager.STORAGE_XML_IDENTIFIER;

import static com.google.common.truth.Truth.assertThat;

import android.app.adservices.ConsentParcel;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/** Tests for {@link ConsentManager} */
public class ConsentManagerTest {
    private static final Context PPAPI_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String BASE_DIR = PPAPI_CONTEXT.getFilesDir().getAbsolutePath();

    private BooleanFileDatastore mDatastore;

    @Before
    public void setup() {
        mDatastore =
                new BooleanFileDatastore(
                        PPAPI_CONTEXT.getFilesDir().getAbsolutePath(),
                        STORAGE_XML_IDENTIFIER,
                        STORAGE_VERSION);
    }

    @After
    public void tearDown() {
        mDatastore.tearDownForTesting();
    }

    @Test
    public void testGetConsentDataStoreDir() {
        // The Data store is in folder with the following format.
        // /data/system/adservices/user_id/consent/data_schema_version/
        assertThat(
                        ConsentManager.getConsentDataStoreDir(
                                /* baseDir */ "/data/system/adservices", /* userIdentifier */ 0))
                .isEqualTo("/data/system/adservices/0/consent/1");
        assertThat(
                        ConsentManager.getConsentDataStoreDir(
                                /* baseDir */ "/data/system/adservices", /* userIdentifier */ 1))
                .isEqualTo("/data/system/adservices/1/consent/1");
    }

    @Test
    public void testCreateAndInitBooleanFileDatastore() {
        BooleanFileDatastore datastore = null;
        try {
            datastore = ConsentManager.createAndInitBooleanFileDatastore(BASE_DIR);
        } catch (IOException e) {
            Assert.fail("Fail to create the DataStore");
        }

        // Assert that the DataStore is created and initialized with NOTIFICATION_DISPLAYED_ONCE
        // is false.
        assertThat(datastore.get(NOTIFICATION_DISPLAYED_ONCE)).isFalse();
    }

    @Test
    public void testGetConsent_unSet() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        // Newly initialized ConsentManager has consent = false.
        assertThat(consentManager.getConsent().isIsGiven()).isFalse();
    }

    @Test
    public void testGetAndSetConsent_null() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        consentManager.setConsent(new ConsentParcel.Builder().setIsGiven(null).build());

        // null means the consent is not given (false).
        assertThat(consentManager.getConsent().isIsGiven()).isFalse();
    }

    @Test
    public void testGetAndSetConsent_nonNull() throws IOException {
        // Create a ConsentManager for user 0.
        ConsentManager consentManager0 =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        consentManager0.setConsent(new ConsentParcel.Builder().setIsGiven(false).build());
        assertThat(consentManager0.getConsent().isIsGiven()).isFalse();

        consentManager0.setConsent(new ConsentParcel.Builder().setIsGiven(true).build());
        assertThat(consentManager0.getConsent().isIsGiven()).isTrue();

        consentManager0.setConsent(new ConsentParcel.Builder().setIsGiven(false).build());
        assertThat(consentManager0.getConsent().isIsGiven()).isFalse();

        // Create another ConsentManager for user 1 to make sure ConsentManagers
        // are isolated by users.
        ConsentManager consentManager1 =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 1);
        // By default, this ConsentManager has isGiven false.
        assertThat(consentManager0.getConsent().isIsGiven()).isFalse();

        consentManager1.setConsent(new ConsentParcel.Builder().setIsGiven(true).build());
        assertThat(consentManager1.getConsent().isIsGiven()).isTrue();

        // This validates that the consentManager for user 0 was not changed when updating
        // ConsentManager for user 1.
        assertThat(consentManager0.getConsent().isIsGiven()).isFalse();
    }

    @Test
    public void testRecordNotificationDisplayed() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        // First, the notification displayed is false.
        assertThat(consentManager.wasNotificationDisplayed()).isFalse();
        consentManager.recordNotificationDisplayed();

        assertThat(consentManager.wasNotificationDisplayed()).isTrue();
    }
}
