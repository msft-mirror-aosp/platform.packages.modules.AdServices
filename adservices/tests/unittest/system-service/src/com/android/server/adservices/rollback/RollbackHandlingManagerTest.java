/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.adservices.rollback;

import static com.android.adservices.shared.testing.common.DumpHelper.assertDumpHasPrefix;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.adservices.shared.testing.common.FileHelper.deleteDirectory;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.server.adservices.rollback.RollbackHandlingManager.DUMP_PREFIX;
import static com.android.server.adservices.rollback.RollbackHandlingManager.STORAGE_XML_IDENTIFIER;
import static com.android.server.adservices.rollback.RollbackHandlingManager.VERSION_KEY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.app.adservices.AdServicesManager;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.storage.AtomicFileDatastore;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class RollbackHandlingManagerTest extends AdServicesExtendedMockitoTestCase {

    private static final File TEST_DIR = sContext.getFilesDir();
    private static final String BASE_DIR = TEST_DIR.getAbsolutePath();

    private static final int DATASTORE_VERSION = 339900900;

    @Mock private AdServicesErrorLogger mMockAdServicesErrorLogger;

    private AtomicFileDatastore mDatastore;

    @Before
    public void setup() throws Exception {
        deleteDirectory(TEST_DIR);

        mDatastore =
                new AtomicFileDatastore(
                        new File(TEST_DIR, STORAGE_XML_IDENTIFIER),
                        DATASTORE_VERSION,
                        VERSION_KEY,
                        mMockAdServicesErrorLogger);
    }

    @Test
    @SpyStatic(Files.class)
    public void testGetRollbackHandlingDataStoreDir() throws IOException {
        // The Datastore is in the directory with the following format.
        // /data/system/adservices/user_id/rollback/
        ExtendedMockito.doReturn(true).when(() -> Files.exists(any()));

        assertThat(
                        RollbackHandlingDatastoreLocationHelper
                                .getRollbackHandlingDataStoreDirAndCreateDir(
                                        /* baseDir */ "/data/system/adservices",
                                        /* userIdentifier */ 0))
                .isEqualTo("/data/system/adservices/0/rollback");

        assertThat(
                        RollbackHandlingDatastoreLocationHelper
                                .getRollbackHandlingDataStoreDirAndCreateDir(
                                        /* baseDir */ "/data/system/adservices",
                                        /* userIdentifier */ 1))
                .isEqualTo("/data/system/adservices/1/rollback");

        assertThrows(
                NullPointerException.class,
                () ->
                        RollbackHandlingDatastoreLocationHelper
                                .getRollbackHandlingDataStoreDirAndCreateDir(null, 0));
    }

    @Test
    public void testGetOrCreateAtomicFileDatastore() throws IOException {
        RollbackHandlingManager rollbackHandlingManager =
                RollbackHandlingManager.createRollbackHandlingManager(
                        BASE_DIR, /* userIdentifier */ 0, DATASTORE_VERSION);
        AtomicFileDatastore datastore =
                rollbackHandlingManager.getOrCreateAtomicFileDatastore(
                        AdServicesManager.MEASUREMENT_DELETION);

        // Assert that the DataStore is created.
        assertThat(datastore).isNotNull();
    }

    @Test
    public void testRecordMeasurementDeletionOccurred() throws IOException {
        RollbackHandlingManager rollbackHandlingManager =
                RollbackHandlingManager.createRollbackHandlingManager(
                        BASE_DIR, /* userIdentifier */ 0, DATASTORE_VERSION);

        // By default, the bit is false.
        assertThat(
                        rollbackHandlingManager.wasAdServicesDataDeleted(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isFalse();

        // Set the record bit and verify it is true.
        rollbackHandlingManager.recordAdServicesDataDeletion(
                AdServicesManager.MEASUREMENT_DELETION);
        assertThat(
                        rollbackHandlingManager.wasAdServicesDataDeleted(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isTrue();

        // Reset the record bit and verify it is false.
        rollbackHandlingManager.resetAdServicesDataDeletion(AdServicesManager.MEASUREMENT_DELETION);
        assertThat(
                        rollbackHandlingManager.wasAdServicesDataDeleted(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isFalse();
    }

    @Test
    public void testDump() throws Exception {
        RollbackHandlingManager rollbackHandlingManager =
                RollbackHandlingManager.createRollbackHandlingManager(
                        BASE_DIR, /* userIdentifier= */ 0, DATASTORE_VERSION);
        AtomicFileDatastore datastore =
                rollbackHandlingManager.getOrCreateAtomicFileDatastore(
                        AdServicesManager.MEASUREMENT_DELETION);
        String prefix = "_";

        String dump = dump(pw -> rollbackHandlingManager.dump(pw, prefix));

        assertWithMessage("content of dump()")
                .that(dump)
                .startsWith(prefix + "RollbackHandlingManager:");
        assertDumpHasPrefix(dump, prefix);
        assertWithMessage("content of dump()")
                .that(dump)
                .startsWith(prefix + "RollbackHandlingManager:");
        assertWithMessage("content of dump() (BASE_DIR)").that(dump).contains(BASE_DIR);
        assertWithMessage("content of dump() (DATASTORE_VERSION)")
                .that(dump)
                .contains(Integer.toString(DATASTORE_VERSION));
        assertWithMessage("content of dump() (# datastores)").that(dump).contains("1 datastores");

        String datastoreDump =
                dump(
                        pw ->
                                datastore.dump(
                                        pw,
                                        prefix + DUMP_PREFIX + DUMP_PREFIX + DUMP_PREFIX,
                                        AtomicFileDatastore.DUMP_ARGS_INCLUDE_CONTENTS_ONLY));
        assertWithMessage("content of dump() (datastore)").that(dump).contains(datastoreDump);
    }
}
