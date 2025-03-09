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
import static com.android.adservices.shared.testing.common.FileHelper.deleteDirectory;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;
import static com.android.server.adservices.consent.ConsentManager.NOTIFICATION_DISPLAYED_ONCE;
import static com.android.server.adservices.consent.ConsentManager.STORAGE_VERSION;
import static com.android.server.adservices.consent.ConsentManager.STORAGE_XML_IDENTIFIER;
import static com.android.server.adservices.consent.ConsentManager.VERSION_KEY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.app.adservices.consent.ConsentParcel;

import com.android.adservices.LogUtil;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.storage.AtomicFileDatastore;
import com.android.server.adservices.Flags;
import com.android.server.adservices.FlagsFactory;
import com.android.server.adservices.feature.PrivacySandboxEnrollmentChannelCollection;
import com.android.server.adservices.feature.PrivacySandboxFeatureType;
import com.android.server.adservices.feature.PrivacySandboxUxCollection;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.mockito.Mock;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@SpyStatic(FlagsFactory.class)
public final class ConsentManagerTest extends AdServicesExtendedMockitoTestCase {
    private static final File TEST_DIR = sContext.getFilesDir();
    private static final String BASE_DIR = TEST_DIR.getAbsolutePath();

    @Rule(order = 11)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.withoutAdoptingShellPermissions().setDefaultLogcatTags();

    @Mock private AdServicesErrorLogger mMockAdServicesErrorLogger;

    // TODO(b/358120731): create an AdServicesServerExtendedMockitoTestCase class instead, which
    // contains a mMockServerFlags
    @Mock private Flags mMockServerFlags;

    private AtomicFileDatastore mDatastore;

    private ConsentManager mConsentManager;

    // TODO(b/359055024): here to mention that this should be taken care of by the testing infra
    private final List<ThrowingRunnable> mCleanupActions = new ArrayList<ThrowingRunnable>();

    @Before
    public void setup() throws Exception {
        deleteDirectory(TEST_DIR);

        File datastoreFile = new File(TEST_DIR, STORAGE_XML_IDENTIFIER);
        mDatastore =
                new AtomicFileDatastore(
                        datastoreFile, STORAGE_VERSION, VERSION_KEY, mMockAdServicesErrorLogger);
        when(FlagsFactory.getFlags()).thenReturn(mMockServerFlags);
        mockGetEnableAtomicFileDatastoreBatchUpdateApiInSystemServer(false);
        mConsentManager = ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier= */ 0);
    }

    @After
    public void tearDown() {
        for (ThrowingRunnable action : mCleanupActions) {
            try {
                action.run();
            } catch (Throwable e) {
                LogUtil.e("failed to clean up %s", e.getMessage());
            }
        }
        mCleanupActions.clear();
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
        // Newly initialized ConsentManager has consent = false.
        assertThat(mConsentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();
        assertThat(mConsentManager.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();
        assertThat(mConsentManager.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();
        assertThat(mConsentManager.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();
    }

    @Test
    public void testGetAndSetConsent_null() throws Exception {
        mConsentManager.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.ALL_API)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(mConsentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        mConsentManager.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.TOPICS)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(mConsentManager.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();

        mConsentManager.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.FLEDGE)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(mConsentManager.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();

        mConsentManager.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.MEASUREMENT)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(mConsentManager.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();
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
        // Test upgrading from 1 consent to 3 consents.
        mConsentManager.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API));
        assertThat(mConsentManager.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();
        assertThat(mConsentManager.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();
        assertThat(mConsentManager.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();

        mConsentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(mConsentManager.getConsent(ConsentParcel.TOPICS).isIsGiven()).isTrue();
        assertThat(mConsentManager.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isTrue();
        assertThat(mConsentManager.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isTrue();
    }

    @Test
    public void testGetAndSetConsent_downgrade() throws Exception {
        // Test downgrading from 3 consents to 1 consent.
        // For Topics.
        mConsentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(mConsentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();
        // Now even setting only Topics to false, the ALL_API will get false value too.
        mConsentManager.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.TOPICS));
        assertThat(mConsentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        // For FLEDGE
        mConsentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(mConsentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();
        // Now even setting only FLEDGE to false, the ALL_API will get false value too.
        mConsentManager.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE));
        assertThat(mConsentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        // For Measurement
        mConsentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(mConsentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();
        // Now even setting only Measurement to false, the ALL_API will get false value too.
        mConsentManager.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.MEASUREMENT));
        assertThat(mConsentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        // Now setting 3 consents to true and the ALL_API will be true too.
        mConsentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.TOPICS));
        mConsentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE));
        mConsentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.MEASUREMENT));
        assertThat(mConsentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();
    }

    @Test
    public void testGetConsent_unSetConsentApiType() throws Exception {
        // Newly initialized ConsentManager has consent = false.
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mConsentManager.setConsent(
                            new ConsentParcel.Builder()
                                    // Not set the ConsentApiType.
                                    // .setConsentApiType(xxx)
                                    .setIsGiven(true)
                                    .build());
                });
    }

    @Test
    public void testRecordNotificationDisplayed() throws Exception {
        // First, the notification displayed is false.
        assertThat(mConsentManager.wasNotificationDisplayed()).isFalse();
        mConsentManager.recordNotificationDisplayed(true);

        assertThat(mConsentManager.wasNotificationDisplayed()).isTrue();
    }

    @Test
    public void testGaUxRecordNotificationDisplayed() throws Exception {
        // First, the notification displayed is false.
        assertThat(mConsentManager.wasGaUxNotificationDisplayed()).isFalse();
        mConsentManager.recordGaUxNotificationDisplayed(true);

        assertThat(mConsentManager.wasGaUxNotificationDisplayed()).isTrue();
    }

    @Test
    public void testDeleteConsentDataStoreDir() throws Exception {
        int userIdentifier = 0;
        mCleanupActions.add(() -> tearDownFile(userIdentifier));
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
        mConsentManager.recordUserManualInteractionWithConsent(1);

        assertThat(mConsentManager.getUserManualInteractionWithConsent()).isEqualTo(1);
    }

    @Test
    public void testSetUserManualInteractionWithConsentToFalse() throws Exception {
        mConsentManager.recordUserManualInteractionWithConsent(-1);

        assertThat(mConsentManager.getUserManualInteractionWithConsent()).isEqualTo(-1);
    }

    @Test
    public void testSetUserManualInteractionWithConsentToUnknown() throws Exception {
        mConsentManager.recordUserManualInteractionWithConsent(0);

        assertThat(mConsentManager.getUserManualInteractionWithConsent()).isEqualTo(0);
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
        mCleanupActions.add(() -> tearDownFile(userIdentifier));

        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, userIdentifier);
        String consentDataStoreDir =
                ConsentDatastoreLocationHelper.getConsentDataStoreDirAndCreateDir(
                        BASE_DIR, userIdentifier);
        Path packageDir = Paths.get(consentDataStoreDir);

        int userIdentifierNotPresent = 3;
        // Try deleting with non-existent user id. Nothing should happen and ensure
        // userIdentifier
        // is present.
        assertThat(
                        consentManager.deleteUserDirectory(
                                new File(BASE_DIR + userIdentifierNotPresent)))
                .isFalse();
        assertThat(Files.exists(packageDir)).isTrue();
    }

    @Test
    public void isAdIdEnabledTest() throws Exception {
        assertThat(mConsentManager.isAdIdEnabled()).isFalse();
        mConsentManager.setAdIdEnabled(true);

        assertThat(mConsentManager.isAdIdEnabled()).isTrue();
    }

    @Test
    public void isU18AccountTest() throws Exception {
        assertThat(mConsentManager.isU18Account()).isFalse();
        mConsentManager.setU18Account(true);

        assertThat(mConsentManager.isU18Account()).isTrue();
    }

    @Test
    public void isEntryPointEnabledTest() throws Exception {
        assertThat(mConsentManager.isEntryPointEnabled()).isFalse();
        mConsentManager.setEntryPointEnabled(true);

        assertThat(mConsentManager.isEntryPointEnabled()).isTrue();
    }

    @Test
    public void isAdultAccountTest() throws Exception {
        assertThat(mConsentManager.isAdultAccount()).isFalse();
        mConsentManager.setAdultAccount(true);

        assertThat(mConsentManager.isAdultAccount()).isTrue();
    }

    @Test
    public void wasU18NotificationDisplayedTest() throws Exception {
        assertThat(mConsentManager.wasU18NotificationDisplayed()).isFalse();
        mConsentManager.setU18NotificationDisplayed(true);

        assertThat(mConsentManager.wasU18NotificationDisplayed()).isTrue();
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

        String dump = dump(pw -> mConsentManager.dump(pw, prefix));

        assertWithMessage("content of dump()").that(dump).startsWith(prefix + "ConsentManager:");
        assertDumpHasPrefix(dump, prefix);
        // ConsentManager only dumps the datastore, which is not accessible, so there's nothing else
        // to check
    }

    @Test
    public void isMeasurementDataResetTest() throws Exception {
        assertThat(mConsentManager.isMeasurementDataReset()).isFalse();
        mConsentManager.setMeasurementDataReset(true);

        assertThat(mConsentManager.isMeasurementDataReset()).isTrue();
    }

    @Test
    public void isPaDataResetTest() throws Exception {
        assertThat(mConsentManager.isPaDataReset()).isFalse();
        mConsentManager.setPaDataReset(true);

        assertThat(mConsentManager.isPaDataReset()).isTrue();
    }

    private void mockGetEnableAtomicFileDatastoreBatchUpdateApiInSystemServer(boolean enable) {
        doReturn(enable)
                .when(mMockServerFlags)
                .getEnableAtomicFileDatastoreBatchUpdateApiInSystemServer();
    }

    private void testCreateAndInitAtomicFileDatastoreSteps() throws Exception {
        AtomicFileDatastore datastore = ConsentManager.createAndInitAtomicFileDatastore(BASE_DIR);

        // Assert that the DataStore is created and initialized with NOTIFICATION_DISPLAYED_ONCE
        // is false.
        assertThat(datastore.getBoolean(NOTIFICATION_DISPLAYED_ONCE)).isFalse();
    }

    private void testConsentNonNull() throws Exception {
        int userIdentifier0 = 0;
        int userIdentifier1 = 1;
        mCleanupActions.add(() -> tearDownFile(userIdentifier0));
        mCleanupActions.add(() -> tearDownFile(userIdentifier1));

        // Create a ConsentManager for user 0.
        ConsentManager consentManager0 =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ userIdentifier0);
        // Create another ConsentManager for user 1 to make sure ConsentManagers
        // are isolated by users.
        ConsentManager consentManager1 =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ userIdentifier1);

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

        // By default, ConsentManager1 has isGiven false.
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
        // All bits are false in the beginning.
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        mConsentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED))
                .isFalse();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        mConsentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT))
                .isFalse();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        mConsentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT))
                .isFalse();

        mConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name());
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        mConsentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT))
                .isTrue();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        mConsentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED))
                .isFalse();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        mConsentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT))
                .isFalse();

        mConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT.name());
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        mConsentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT))
                .isTrue();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        mConsentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT))
                .isFalse();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        mConsentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED))
                .isFalse();

        mConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name());
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        mConsentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED))
                .isTrue();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        mConsentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT))
                .isFalse();
        expect.withMessage("consentManager privacy feature enabled")
                .that(
                        mConsentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT))
                .isFalse();
    }

    private void uxConformanceTestSteps() throws Exception {
        // All bits are fall in the beginning.
        assertWithMessage("ConsentManager get ux should be unsupported")
                .that(mConsentManager.getUx())
                .isEqualTo(PrivacySandboxUxCollection.UNSUPPORTED_UX.toString());

        Stream.of(PrivacySandboxUxCollection.values())
                .forEach(
                        ux -> {
                            mConsentManager.setUx(ux.toString());
                            expect.withMessage("consentManager get ux")
                                    .that(mConsentManager.getUx())
                                    .isEqualTo(ux.toString());
                        });
    }

    private void enrollmentChannelConformanceTestSteps() throws Exception {
        // All bits are fall in the beginning.
        assertWithMessage("consentManager get enrollment channel should be null")
                .that(mConsentManager.getEnrollmentChannel())
                .isNull();

        Stream.of(PrivacySandboxEnrollmentChannelCollection.values())
                .forEach(
                        channel -> {
                            mConsentManager.setEnrollmentChannel(channel.toString());
                            expect.withMessage("consentManager get enrollment channel")
                                    .that(mConsentManager.getEnrollmentChannel())
                                    .isEqualTo(channel.toString());
                        });
    }

    private void tearDownFile(int userIdentifier) throws Exception {
        String consentDataStoreDir =
                ConsentDatastoreLocationHelper.getConsentDataStoreDirAndCreateDir(
                        BASE_DIR, userIdentifier);
        Path packageDir = Paths.get(consentDataStoreDir);
        if (Files.exists(packageDir)) {
            String userDirectoryPath = BASE_DIR + "/" + userIdentifier;
            LogUtil.d("delete data file for user %d", userIdentifier);
            mConsentManager.deleteUserDirectory(new File(userDirectoryPath));
        }
    }
}
