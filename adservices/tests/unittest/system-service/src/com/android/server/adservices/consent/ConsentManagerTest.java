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

import static com.android.adservices.shared.testing.common.DumpHelper.assertDumpHasPrefix;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.server.adservices.consent.ConsentManager.NOTIFICATION_DISPLAYED_ONCE;
import static com.android.server.adservices.consent.ConsentManager.STORAGE_VERSION;
import static com.android.server.adservices.consent.ConsentManager.STORAGE_XML_IDENTIFIER;
import static com.android.server.adservices.consent.ConsentManager.VERSION_KEY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.app.adservices.consent.ConsentParcel;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.storage.AtomicFileDatastore;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.adservices.Flags;
import com.android.server.adservices.FlagsFactory;
import com.android.server.adservices.feature.PrivacySandboxEnrollmentChannelCollection;
import com.android.server.adservices.feature.PrivacySandboxFeatureType;
import com.android.server.adservices.feature.PrivacySandboxUxCollection;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/** Tests for {@link ConsentManager} */
@ExtendedMockitoRule.SpyStatic(FlagsFactory.class)
public final class ConsentManagerTest extends AdServicesExtendedMockitoTestCase {
    private static final Context PPAPI_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String BASE_DIR = PPAPI_CONTEXT.getFilesDir().getAbsolutePath();

    @Rule(order = 11)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.withoutAdoptingShellPermissions().setDefaultLogcatTags();

    @Mock private AdServicesErrorLogger mMockAdServicesErrorLogger;

    // TODO(b/358120731): create an AdServicesServerExtendedMockitoTestCase class instead, which
    // contains a mMockServerFlags
    @Mock private Flags mMockServerFlags;

    private AtomicFileDatastore mDatastore;

    @Before
    public void setup() {
        mDatastore =
                new AtomicFileDatastore(
                        PPAPI_CONTEXT.getFilesDir().getAbsolutePath(),
                        STORAGE_XML_IDENTIFIER,
                        STORAGE_VERSION,
                        VERSION_KEY,
                        mMockAdServicesErrorLogger);
        when(FlagsFactory.getFlags()).thenReturn(mMockServerFlags);
        mockGetEnableAtomicFileDatastoreBatchUpdateApiInSystemServer(false);
    }

    @After
    public void tearDown() {
        mDatastore.tearDownForTesting();

    }

    @Test
    public void testGetConsentDataStoreDir() {
        // The Data store is in folder with the following format.
        // /data/system/adservices/user_id/consent/
        assertThat(
                        ConsentDatastoreLocationHelper.getConsentDataStoreDir(
                                /* baseDir */ "/data/system/adservices", /* userIdentifier */ 0))
                .isEqualTo("/data/system/adservices/0/consent");
        assertThat(
                        ConsentDatastoreLocationHelper.getConsentDataStoreDir(
                                /* baseDir */ "/data/system/adservices", /* userIdentifier */ 1))
                .isEqualTo("/data/system/adservices/1/consent");
        assertThrows(
                NullPointerException.class,
                () -> ConsentDatastoreLocationHelper.getConsentDataStoreDir(null, 0));
    }

    @Test
    public void testCreateAndInitAtomicFileDatastore() throws Exception {
        testCreateAndInitAtomicFileDatastoreSteps();
    }

    @Test
    public void testCreateAndInitAtomicFileDatastore_atomic() throws Exception {
        mockGetEnableAtomicFileDatastoreBatchUpdateApiInSystemServer(true);
        testCreateAndInitAtomicFileDatastoreSteps();
    }

    @Test
    public void testGetConsent_unSet() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        // Newly initialized ConsentManager has consent = false.
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();
        assertThat(consentManager.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();
        assertThat(consentManager.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();
        assertThat(consentManager.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();
    }

    @Test
    public void testGetAndSetConsent_null() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        consentManager.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.ALL_API)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        consentManager.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.TOPICS)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(consentManager.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();

        consentManager.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.FLEDGE)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(consentManager.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();

        consentManager.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.MEASUREMENT)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(consentManager.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();
    }

    @Test
    public void testGetAndSetConsent_nonNull() throws Exception {
        testConsentNonNull();
    }

    @Test
    public void testGetAndSetConsent_nonNull_atomic() throws Exception {
        mockGetEnableAtomicFileDatastoreBatchUpdateApiInSystemServer(true);
        testConsentNonNull();
    }

    @Test
    public void testGetAndSetConsent_upgrade() throws Exception {
        // Create a ConsentManager for user 0.
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        // Test upgrading from 1 consent to 3 consents.
        consentManager.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API));
        assertThat(consentManager.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();
        assertThat(consentManager.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();
        assertThat(consentManager.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();

        consentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(consentManager.getConsent(ConsentParcel.TOPICS).isIsGiven()).isTrue();
        assertThat(consentManager.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isTrue();
        assertThat(consentManager.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isTrue();
    }

    @Test
    public void testGetAndSetConsent_downgrade() throws Exception {
        // Create a ConsentManager for user 0.
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        // Test downgrading from 3 consents to 1 consent.
        // For Topics.
        consentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();
        // Now even setting only Topics to false, the ALL_API will get false value too.
        consentManager.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.TOPICS));
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        // For FLEDGE
        consentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();
        // Now even setting only FLEDGE to false, the ALL_API will get false value too.
        consentManager.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE));
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        // For Measurement
        consentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();
        // Now even setting only Measurement to false, the ALL_API will get false value too.
        consentManager.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.MEASUREMENT));
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        // Now setting 3 consents to true and the ALL_API will be true too.
        consentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.TOPICS));
        consentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE));
        consentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.MEASUREMENT));
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();
    }

    @Test
    public void testGetConsent_unSetConsentApiType() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        // Newly initialized ConsentManager has consent = false.
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    consentManager.setConsent(
                            new ConsentParcel.Builder()
                                    // Not set the ConsentApiType.
                                    // .setConsentApiType(xxx)
                                    .setIsGiven(true)
                                    .build());
                });
    }

    @Test
    public void testRecordNotificationDisplayed() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        // First, the notification displayed is false.
        assertThat(consentManager.wasNotificationDisplayed()).isFalse();
        consentManager.recordNotificationDisplayed(true);

        assertThat(consentManager.wasNotificationDisplayed()).isTrue();
    }

    @Test
    public void testGaUxRecordNotificationDisplayed() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        // First, the notification displayed is false.
        assertThat(consentManager.wasGaUxNotificationDisplayed()).isFalse();
        consentManager.recordGaUxNotificationDisplayed(true);

        assertThat(consentManager.wasGaUxNotificationDisplayed()).isTrue();
    }

    @Test
    public void testDeleteConsentDataStoreDir() throws Exception {
        int userIdentifier = 0;
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, userIdentifier);
        String consentDataStoreDir =
                ConsentDatastoreLocationHelper.getConsentDataStoreDirAndCreateDir(
                        BASE_DIR, userIdentifier);
        Path packageDir = Paths.get(consentDataStoreDir);
        assertThat(Files.exists(packageDir)).isTrue();
        String userDirectoryPath = BASE_DIR + "/" + userIdentifier;
        assertThat(consentManager.deleteUserDirectory(new File(userDirectoryPath))).isTrue();

        assertThat(Files.exists(packageDir)).isFalse();
    }

    @Test
    public void testSetUserManualInteractionWithConsentToTrue() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        consentManager.recordUserManualInteractionWithConsent(1);

        assertThat(consentManager.getUserManualInteractionWithConsent()).isEqualTo(1);
    }

    @Test
    public void testSetUserManualInteractionWithConsentToFalse() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        consentManager.recordUserManualInteractionWithConsent(-1);

        assertThat(consentManager.getUserManualInteractionWithConsent()).isEqualTo(-1);
    }

    @Test
    public void testSetUserManualInteractionWithConsentToUnknown() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        consentManager.recordUserManualInteractionWithConsent(0);

        assertThat(consentManager.getUserManualInteractionWithConsent()).isEqualTo(0);
    }

    @Test
    public void testSetCurrentPrivacySandboxFeature() throws Exception {
        testSetCurrentPrivacySandboxFeatureSteps();
    }

    @Test
    public void testSetCurrentPrivacySandboxFeature_atomic() throws Exception {
        mockGetEnableAtomicFileDatastoreBatchUpdateApiInSystemServer(true);
        testSetCurrentPrivacySandboxFeatureSteps();
    }

    @Test
    public void uxConformanceTest() throws Exception {
        uxConformanceTestSteps();
    }

    @Test
    public void uxConformanceTest_atomic() throws Exception {
        mockGetEnableAtomicFileDatastoreBatchUpdateApiInSystemServer(true);
        uxConformanceTestSteps();
    }

    @Test
    public void testDeleteConsentDataStoreDirUserIdentifierNotPresent() throws Exception {
        int userIdentifier = 0;
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, userIdentifier);
        String consentDataStoreDir =
                ConsentDatastoreLocationHelper.getConsentDataStoreDirAndCreateDir(
                        BASE_DIR, userIdentifier);
        Path packageDir = Paths.get(consentDataStoreDir);

        int userIdentifierNotPresent = 3;
        // Try deleting with non-existent user id. Nothing should happen and ensure userIdentifier
        // is present.
        assertThat(
                        consentManager.deleteUserDirectory(
                                new File(BASE_DIR + userIdentifierNotPresent)))
                .isFalse();
        assertThat(Files.exists(packageDir)).isTrue();
    }

    @Test
    public void isAdIdEnabledTest() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        assertThat(consentManager.isAdIdEnabled()).isFalse();
        consentManager.setAdIdEnabled(true);

        assertThat(consentManager.isAdIdEnabled()).isTrue();
    }

    @Test
    public void isU18AccountTest() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        assertThat(consentManager.isU18Account()).isFalse();
        consentManager.setU18Account(true);

        assertThat(consentManager.isU18Account()).isTrue();
    }

    @Test
    public void isEntryPointEnabledTest() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        assertThat(consentManager.isEntryPointEnabled()).isFalse();
        consentManager.setEntryPointEnabled(true);

        assertThat(consentManager.isEntryPointEnabled()).isTrue();
    }

    @Test
    public void isAdultAccountTest() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        assertThat(consentManager.isAdultAccount()).isFalse();
        consentManager.setAdultAccount(true);

        assertThat(consentManager.isAdultAccount()).isTrue();
    }

    @Test
    public void wasU18NotificationDisplayedTest() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        assertThat(consentManager.wasU18NotificationDisplayed()).isFalse();
        consentManager.setU18NotificationDisplayed(true);

        assertThat(consentManager.wasU18NotificationDisplayed()).isTrue();
    }

    @Test
    public void enrollmentChannelConformanceTest() throws Exception {
        enrollmentChannelConformanceTestSteps();
    }

    @Test
    public void enrollmentChannelConformanceTest_atomic() throws Exception {
        mockGetEnableAtomicFileDatastoreBatchUpdateApiInSystemServer(true);
        enrollmentChannelConformanceTestSteps();
    }

    @Test
    public void testDump() throws Exception {
        String prefix = "_";
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier= */ 0);

        String dump = dump(pw -> consentManager.dump(pw, prefix));

        assertWithMessage("content of dump()").that(dump).startsWith(prefix + "ConsentManager:");
        assertDumpHasPrefix(dump, prefix);
        // ConsentManager only dumps the datastore, which is not accessible, so there's nothing else
        // to check
    }

    @Test
    public void isMeasurementDataResetTest() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        assertThat(consentManager.isMeasurementDataReset()).isFalse();
        consentManager.setMeasurementDataReset(true);

        assertThat(consentManager.isMeasurementDataReset()).isTrue();
    }

    @Test
    public void isPaDataResetTest() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        assertThat(consentManager.isPaDataReset()).isFalse();
        consentManager.setPaDataReset(true);

        assertThat(consentManager.isPaDataReset()).isTrue();
    }

    private void mockGetEnableAtomicFileDatastoreBatchUpdateApiInSystemServer(boolean enable) {
        doReturn(enable)
                .when(mMockServerFlags)
                .getEnableAtomicFileDatastoreBatchUpdateApiInSystemServer();
    }

    private void testCreateAndInitAtomicFileDatastoreSteps() throws Exception {
        AtomicFileDatastore datastore = null;

        datastore = ConsentManager.createAndInitAtomicFileDatastore(BASE_DIR);

        // Assert that the DataStore is created and initialized with NOTIFICATION_DISPLAYED_ONCE
        // is false.
        assertThat(datastore.getBoolean(NOTIFICATION_DISPLAYED_ONCE)).isFalse();
    }

    private void testConsentNonNull() throws Exception {
        // Create a ConsentManager for user 0.
        ConsentManager consentManager0 =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        consentManager0.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API));
        expect.withMessage("consentManager0 consent ALL_API")
                .that(consentManager0.getConsent(ConsentParcel.ALL_API).isIsGiven())
                .isFalse();

        consentManager0.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        expect.withMessage("consentManager0 consent ALL_API")
                .that(consentManager0.getConsent(ConsentParcel.ALL_API).isIsGiven())
                .isTrue();

        consentManager0.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.TOPICS));
        expect.withMessage("consentManager0 consent TOPICS")
                .that(consentManager0.getConsent(ConsentParcel.TOPICS).isIsGiven())
                .isFalse();

        consentManager0.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.TOPICS));
        expect.withMessage("consentManager0 consent TOPICS")
                .that(consentManager0.getConsent(ConsentParcel.TOPICS).isIsGiven())
                .isTrue();

        consentManager0.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE));
        expect.withMessage("consentManager0 consent FLEDGE")
                .that(consentManager0.getConsent(ConsentParcel.FLEDGE).isIsGiven())
                .isFalse();

        consentManager0.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE));
        expect.withMessage("consentManager0 consent FLEDGE")
                .that(consentManager0.getConsent(ConsentParcel.FLEDGE).isIsGiven())
                .isTrue();

        consentManager0.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.MEASUREMENT));
        expect.withMessage("consentManager0 consent MEASUREMENT")
                .that(consentManager0.getConsent(ConsentParcel.MEASUREMENT).isIsGiven())
                .isFalse();

        consentManager0.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.MEASUREMENT));
        expect.withMessage("consentManager0 consent MEASUREMENT")
                .that(consentManager0.getConsent(ConsentParcel.MEASUREMENT).isIsGiven())
                .isTrue();

        // Create another ConsentManager for user 1 to make sure ConsentManagers
        // are isolated by users.
        ConsentManager consentManager1 =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 1);
        // By default, this ConsentManager has isGiven false.
        expect.withMessage("consentManager1 consent ALL_API")
                .that(consentManager1.getConsent(ConsentParcel.ALL_API).isIsGiven())
                .isFalse();

        // Set the user 0 to revoked.
        consentManager0.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API));
        expect.withMessage("consentManager0 consent ALL_API")
                .that(consentManager0.getConsent(ConsentParcel.ALL_API).isIsGiven())
                .isFalse();

        // Set the user 1 to given.
        consentManager1.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        expect.withMessage("consentManager1 consent ALL_API")
                .that(consentManager1.getConsent(ConsentParcel.ALL_API).isIsGiven())
                .isTrue();

        // This validates that the consentManager for user 0 was not changed when updating
        // ConsentManager for user 1.
        expect.withMessage("consentManager0 consent ALL_API")
                .that(consentManager0.getConsent(ConsentParcel.ALL_API).isIsGiven())
                .isFalse();
    }

    private void testSetCurrentPrivacySandboxFeatureSteps() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        // All bits are false in the beginning.
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED))
                .isFalse();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT))
                .isFalse();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT))
                .isFalse();

        consentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name());
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT))
                .isTrue();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED))
                .isFalse();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT))
                .isFalse();

        consentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT.name());
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT))
                .isTrue();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT))
                .isFalse();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED))
                .isFalse();

        consentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name());
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED))
                .isTrue();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT))
                .isFalse();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT))
                .isFalse();
    }

    private void uxConformanceTestSteps() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        // All bits are fall in the beginning.
        assertWithMessage("ConsentManager get ux should be unsupported")
                .that(consentManager.getUx())
                .isEqualTo(PrivacySandboxUxCollection.UNSUPPORTED_UX.toString());

        Stream.of(PrivacySandboxUxCollection.values())
                .forEach(
                        ux -> {
                            consentManager.setUx(ux.toString());
                            expect.withMessage("consentManager get ux")
                                    .that(consentManager.getUx())
                                    .isEqualTo(ux.toString());
                        });
    }

    private void enrollmentChannelConformanceTestSteps() throws Exception {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        // All bits are fall in the beginning.
        assertWithMessage("consentManager get enrollment channel should be null")
                .that(consentManager.getEnrollmentChannel())
                .isNull();

        Stream.of(PrivacySandboxEnrollmentChannelCollection.values())
                .forEach(
                        channel -> {
                            consentManager.setEnrollmentChannel(channel.toString());
                            expect.withMessage("consentManager get enrollment channel")
                                    .that(consentManager.getEnrollmentChannel())
                                    .isEqualTo(channel.toString());
                        });
    }
}
