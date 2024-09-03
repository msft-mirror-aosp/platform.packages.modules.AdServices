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

package com.android.adservices.shared.storage;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ATOMIC_FILE_DATASTORE_READ_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ATOMIC_FILE_DATASTORE_WRITE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.adservices.shared.testing.common.DumpHelper.assertDumpHasPrefix;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.util.AtomicFile;
import android.util.Pair;

import com.android.adservices.shared.SharedExtendedMockitoTestCase;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.truth.IterableSubject;
import com.google.common.truth.StringSubject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class AtomicFileDatastoreTest extends SharedExtendedMockitoTestCase {
    private static final String VALID_DIR = sContext.getFilesDir().getAbsolutePath();
    private static final String FILENAME = "AtomicFileDatastoreTest.xml";
    private static final int DATASTORE_VERSION = 1;
    private static final String TEST_KEY = "key";
    private static final String TEST_KEY_1 = "key1";
    private static final String TEST_KEY_2 = "key2";
    private static final String TEST_VERSION_KEY = "version_key";

    @Mock private AdServicesErrorLogger mMockAdServicesErrorLogger;

    private AtomicFileDatastore mDatastore;

    @Before
    public void initializeDatastore() throws IOException {
        mDatastore =
                new AtomicFileDatastore(
                        VALID_DIR,
                        FILENAME,
                        DATASTORE_VERSION,
                        TEST_VERSION_KEY,
                        mMockAdServicesErrorLogger);
        mDatastore.initialize();
    }

    @After
    public void cleanupDatastore() {
        mDatastore.tearDownForTesting();
    }

    @Test
    public void testConstructor_emptyOrNullArgs() {
        // String dir + name constructor
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AtomicFileDatastore(
                                /* parentPath= */ null,
                                FILENAME,
                                DATASTORE_VERSION,
                                TEST_VERSION_KEY,
                                mMockAdServicesErrorLogger));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AtomicFileDatastore(
                                /* parentPath= */ "",
                                FILENAME,
                                DATASTORE_VERSION,
                                TEST_VERSION_KEY,
                                mMockAdServicesErrorLogger));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AtomicFileDatastore(
                                VALID_DIR,
                                /* filename= */ null,
                                DATASTORE_VERSION,
                                TEST_VERSION_KEY,
                                mMockAdServicesErrorLogger));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AtomicFileDatastore(
                                VALID_DIR,
                                /* filename= */ "",
                                DATASTORE_VERSION,
                                TEST_VERSION_KEY,
                                mMockAdServicesErrorLogger));

        assertThrows(
                NullPointerException.class,
                () ->
                        new AtomicFileDatastore(
                                VALID_DIR,
                                FILENAME,
                                DATASTORE_VERSION,
                                TEST_VERSION_KEY,
                                /* adServicesErrorLogger= */ null));

        // File constructor
        assertThrows(
                NullPointerException.class,
                () ->
                        new AtomicFileDatastore(
                                /* file= */ (File) null,
                                DATASTORE_VERSION,
                                TEST_VERSION_KEY,
                                mMockAdServicesErrorLogger));
    }

    @Test
    public void testConstructor_parentPathDirectoryDoesNotExist() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AtomicFileDatastore(
                                "I can't believe this is a valid dir",
                                FILENAME,
                                DATASTORE_VERSION,
                                TEST_VERSION_KEY,
                                mMockAdServicesErrorLogger));
    }

    @Test
    public void testConstructor_parentPathDirectoryIsNotAFile() throws Exception {
        File file = new File(VALID_DIR, "file.IAm");
        String path = file.getAbsolutePath();
        mLog.d("path: %s", path);
        assertWithMessage("Could not create file %s", path).that(file.createNewFile()).isTrue();

        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new AtomicFileDatastore(
                                    path,
                                    FILENAME,
                                    DATASTORE_VERSION,
                                    TEST_VERSION_KEY,
                                    mMockAdServicesErrorLogger));
        } finally {
            if (!file.delete()) {
                mLog.e("Could not delete file %s at the end", path);
            }
        }
    }

    @Test
    public void testInitializeEmptyAtomicFileDatastore() {
        expect.withMessage("keys").that(mDatastore.keySet()).isEmpty();
    }

    @Test
    public void testNullOrEmptyKeyFails() {
        testKeyFails(NullPointerException.class, null);
        testKeyFails(IllegalArgumentException.class, "");
    }

    private void testKeyFails(Class<? extends Exception> exceptionClass, String key) {
        assertThrows(exceptionClass, () -> mDatastore.putBoolean(key, true));
        assertThrows(exceptionClass, () -> mDatastore.putInt(key, 7));
        assertThrows(exceptionClass, () -> mDatastore.putString(key, "foo"));

        assertThrows(exceptionClass, () -> mDatastore.putBooleanIfNew(key, true));
        assertThrows(exceptionClass, () -> mDatastore.putIntIfNew(key, 7));
        assertThrows(exceptionClass, () -> mDatastore.putStringIfNew(key, "foo"));

        assertThrows(exceptionClass, () -> mDatastore.getBoolean(key));
        assertThrows(exceptionClass, () -> mDatastore.getInt(key));
        assertThrows(exceptionClass, () -> mDatastore.getString(key));

        assertThrows(exceptionClass, () -> mDatastore.remove(key));

        assertThrows(exceptionClass, () -> mDatastore.removeByPrefix(key));
    }

    @Test
    public void testNullValueFails() {
        Boolean nullBoolean = null;
        Integer nullInt = null;
        String nullString = null;
        assertThrows(
                NullPointerException.class, () -> mDatastore.putBoolean(TEST_KEY, nullBoolean));
        assertThrows(NullPointerException.class, () -> mDatastore.putInt(TEST_KEY, nullInt));
        assertThrows(NullPointerException.class, () -> mDatastore.putString(TEST_KEY, nullString));

        assertThrows(
                NullPointerException.class,
                () -> mDatastore.putBooleanIfNew(TEST_KEY, nullBoolean));
        assertThrows(NullPointerException.class, () -> mDatastore.putIntIfNew(TEST_KEY, nullInt));
        assertThrows(
                NullPointerException.class, () -> mDatastore.putStringIfNew(TEST_KEY, nullString));

        assertThrows(
                NullPointerException.class, () -> mDatastore.getBoolean(TEST_KEY, nullBoolean));
        assertThrows(NullPointerException.class, () -> mDatastore.getInt(TEST_KEY, nullInt));
        assertThrows(NullPointerException.class, () -> mDatastore.getString(TEST_KEY, nullString));
    }

    @Test
    public void testGetVersionKey() {
        assertWithMessage("getVersionKey()")
                .that(mDatastore.getVersionKey())
                .isEqualTo(TEST_VERSION_KEY);
    }

    @Test
    public void testWriteAndGetVersion() throws IOException {
        // Write values
        mDatastore.putBoolean(TEST_KEY, false);
        mDatastore.putInt(TEST_KEY_1, 3);
        mDatastore.putString(TEST_KEY_2, "bar");

        // Re-initialize datastore (reads from the file again)
        mDatastore.initialize();

        assertWithMessage("getPreviousStoredVersion()")
                .that(mDatastore.getPreviousStoredVersion())
                .isEqualTo(DATASTORE_VERSION);
    }

    @Test
    public void testWriteToFile_ThrowsIoException() throws Exception {
        AtomicFile atomicFileMock = Mockito.mock(AtomicFile.class);
        when(atomicFileMock.startWrite()).thenThrow(new IOException("Write failure"));
        AtomicFileDatastore datastore =
                new AtomicFileDatastore(
                        atomicFileMock,
                        DATASTORE_VERSION,
                        TEST_VERSION_KEY,
                        mMockAdServicesErrorLogger);

        assertThrows(IOException.class, () -> datastore.putBoolean(TEST_KEY, false));

        verify(mMockAdServicesErrorLogger)
                .logError(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ATOMIC_FILE_DATASTORE_WRITE_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
    }

    @Test
    public void testReadFromFile_ThrowsIoException() throws Exception {
        AtomicFile atomicFileMock = Mockito.mock(AtomicFile.class);
        when(atomicFileMock.readFully()).thenThrow(new IOException("Read failure"));
        AtomicFileDatastore datastore =
                new AtomicFileDatastore(
                        atomicFileMock,
                        DATASTORE_VERSION,
                        TEST_VERSION_KEY,
                        mMockAdServicesErrorLogger);

        assertThrows(IOException.class, () -> datastore.initialize());
        verify(mMockAdServicesErrorLogger)
                .logError(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ATOMIC_FILE_DATASTORE_READ_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
    }

    @Test
    public void testGetVersionWithNoPreviousWrite() {
        assertWithMessage("getPreviousStoredVersion()")
                .that(mDatastore.getPreviousStoredVersion())
                .isEqualTo(AtomicFileDatastore.NO_PREVIOUS_VERSION);
    }

    @Test
    public void testPutGetUpdateRemoveBoolean() throws IOException {
        // Should not exist yet
        assertWithMessage("getBoolean(%s)", TEST_KEY)
                .that(mDatastore.getBoolean(TEST_KEY))
                .isNull();

        // Create
        boolean insertedValue = false;
        mDatastore.putBoolean(TEST_KEY, insertedValue);

        // Read
        Boolean readValue = mDatastore.getBoolean(TEST_KEY);
        assertWithMessage("getBoolean(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getBoolean(%s)", TEST_KEY).that(readValue).isEqualTo(insertedValue);

        // Update incorrect type
        mDatastore.putInt(TEST_KEY, 7);
        assertThrows(IllegalStateException.class, () -> mDatastore.getBoolean(TEST_KEY));
        mDatastore.putString(TEST_KEY, "testVal");
        assertThrows(IllegalStateException.class, () -> mDatastore.getBoolean(TEST_KEY));

        // Update correct type
        insertedValue = true;
        mDatastore.putBoolean(TEST_KEY, insertedValue);
        readValue = mDatastore.getBoolean(TEST_KEY);
        assertWithMessage("getBoolean(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getBoolean(%s)", TEST_KEY).that(readValue).isEqualTo(insertedValue);

        assertWithMessage("keys").that(mDatastore.keySet()).containsExactly(TEST_KEY);

        // Delete
        mDatastore.remove(TEST_KEY);
        assertWithMessage("getBoolean(%s)", TEST_KEY)
                .that(mDatastore.getBoolean(TEST_KEY))
                .isNull();
        assertWithMessage("keys").that(mDatastore.keySet()).isEmpty();

        // Should not throw when removing a nonexistent key
        mDatastore.remove(TEST_KEY);
    }

    @Test
    public void testPutGetUpdateRemoveInt() throws IOException {
        // Should not exist yet
        assertWithMessage("getInt(%s)", TEST_KEY).that(mDatastore.getInt(TEST_KEY)).isNull();

        // Create
        int insertedValue = 5;
        mDatastore.putInt(TEST_KEY, insertedValue);

        // Read
        Integer readValue = mDatastore.getInt(TEST_KEY);
        assertWithMessage("getInt(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getInt(%s)", TEST_KEY).that(readValue).isEqualTo(insertedValue);

        // Update incorrect type
        mDatastore.putBoolean(TEST_KEY, true);
        assertThrows(IllegalStateException.class, () -> mDatastore.getInt(TEST_KEY));
        mDatastore.putString(TEST_KEY, "testVal");
        assertThrows(IllegalStateException.class, () -> mDatastore.getInt(TEST_KEY));

        // Update correct type
        insertedValue = 4;
        mDatastore.putInt(TEST_KEY, insertedValue);
        readValue = mDatastore.getInt(TEST_KEY);
        assertWithMessage("getInt(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getInt(%s)", TEST_KEY).that(readValue).isEqualTo(insertedValue);

        assertWithMessage("keys").that(mDatastore.keySet()).containsExactly(TEST_KEY);

        // Delete
        mDatastore.remove(TEST_KEY);
        assertWithMessage("getInt(%s)", TEST_KEY).that(mDatastore.getInt(TEST_KEY)).isNull();
        assertWithMessage("keys").that(mDatastore.keySet()).isEmpty();

        // Should not throw when removing a nonexistent key
        mDatastore.remove(TEST_KEY);
    }

    @Test
    public void testPutGetUpdateRemoveString() throws IOException {
        // Should not exist yet
        assertWithMessage("getString(%s)", TEST_KEY).that(mDatastore.getString(TEST_KEY)).isNull();

        // Create
        String insertedValue = "foo";
        mDatastore.putString(TEST_KEY, insertedValue);

        // Read
        String readValue = mDatastore.getString(TEST_KEY);
        assertWithMessage("getString(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getString(%s)", TEST_KEY).that(readValue).isEqualTo(insertedValue);

        // Update incorrect type
        mDatastore.putInt(TEST_KEY, 7);
        assertThrows(IllegalStateException.class, () -> mDatastore.getString(TEST_KEY));
        mDatastore.putBoolean(TEST_KEY, false);
        assertThrows(IllegalStateException.class, () -> mDatastore.getString(TEST_KEY));

        // Update correct type
        insertedValue = "bar";
        mDatastore.putString(TEST_KEY, insertedValue);
        readValue = mDatastore.getString(TEST_KEY);
        assertWithMessage("getString(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getString(%s)", TEST_KEY).that(readValue).isEqualTo(insertedValue);

        assertWithMessage("keys").that(mDatastore.keySet()).containsExactly(TEST_KEY);

        // Delete
        mDatastore.remove(TEST_KEY);
        assertWithMessage("getString(%s)", TEST_KEY).that(mDatastore.getString(TEST_KEY)).isNull();
        assertWithMessage("keys").that(mDatastore.keySet()).isEmpty();

        // Should not throw when removing a nonexistent key
        mDatastore.remove(TEST_KEY);
    }

    @Test
    public void testPutBooleanIfNew() throws IOException {
        // Should not exist yet
        assertWithMessage("getBoolean(%s)", TEST_KEY)
                .that(mDatastore.getBoolean(TEST_KEY))
                .isNull();

        // Create because it's new
        assertWithMessage("putBooleanIfNew(%s, false)", TEST_KEY)
                .that(mDatastore.putBooleanIfNew(TEST_KEY, false))
                .isFalse();
        Boolean readValue = mDatastore.getBoolean(TEST_KEY);
        assertWithMessage("getBoolean(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getBoolean(%s)", TEST_KEY).that(readValue).isFalse();

        // Force overwrite
        mDatastore.putBoolean(TEST_KEY, true);
        readValue = mDatastore.getBoolean(TEST_KEY);
        assertWithMessage("getBoolean(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getBoolean(%s)", TEST_KEY).that(readValue).isTrue();

        // Put should read the existing value
        assertWithMessage("putBooleanIfNew(%s, false)", TEST_KEY)
                .that(mDatastore.putBooleanIfNew(TEST_KEY, false))
                .isTrue();
        readValue = mDatastore.getBoolean(TEST_KEY);
        assertWithMessage("getBoolean(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getBoolean(%s)", TEST_KEY).that(readValue).isTrue();

        // Overwrite with incorrect data type
        mDatastore.putInt(TEST_KEY, 4);
        assertThrows(
                IllegalStateException.class, () -> mDatastore.putBooleanIfNew(TEST_KEY, false));
    }

    @Test
    public void testPutIntIfNew() throws IOException {
        // Should not exist yet
        assertWithMessage("getInt(%s)", TEST_KEY).that(mDatastore.getInt(TEST_KEY)).isNull();

        // Create because it's new
        int insertedValue = 5;
        assertWithMessage(String.format("putIntIfNew(%s, %d)", TEST_KEY, insertedValue))
                .that(mDatastore.putIntIfNew(TEST_KEY, insertedValue))
                .isEqualTo(insertedValue);
        int readValue = mDatastore.getInt(TEST_KEY);
        assertWithMessage("getInt(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getInt(%s)", TEST_KEY).that(readValue).isEqualTo(insertedValue);

        // Force overwrite
        int overriddenValue = 7;
        mDatastore.putInt(TEST_KEY, overriddenValue);
        readValue = mDatastore.getInt(TEST_KEY);
        assertWithMessage("getInt(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getInt(%s)", TEST_KEY).that(readValue).isEqualTo(overriddenValue);

        // Put should read the existing value
        assertWithMessage("putIntIfNew(%s, 2)", TEST_KEY)
                .that(mDatastore.putIntIfNew(TEST_KEY, 2))
                .isEqualTo(overriddenValue);
        readValue = mDatastore.getInt(TEST_KEY);
        assertWithMessage("getInt(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getInt(%s)", TEST_KEY).that(readValue).isEqualTo(overriddenValue);

        // Overwrite with incorrect data type
        mDatastore.putString(TEST_KEY, "foo");
        assertThrows(IllegalStateException.class, () -> mDatastore.putIntIfNew(TEST_KEY, 6));
    }

    @Test
    public void testPutStringIfNew() throws IOException {
        // Should not exist yet
        assertWithMessage("getString(%s)", TEST_KEY).that(mDatastore.getString(TEST_KEY)).isNull();

        // Create because it's new
        String insertedValue = "foo";
        assertWithMessage(String.format("putStringIfNew(%s, %s)", TEST_KEY, insertedValue))
                .that(mDatastore.putStringIfNew(TEST_KEY, insertedValue))
                .isEqualTo(insertedValue);
        String readValue = mDatastore.getString(TEST_KEY);
        assertWithMessage("getString(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getString(%s)", TEST_KEY).that(readValue).isEqualTo(insertedValue);

        // Force overwrite
        String overriddenValue = "bar";
        mDatastore.putString(TEST_KEY, overriddenValue);
        readValue = mDatastore.getString(TEST_KEY);
        assertWithMessage("getString(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getString(%s)", TEST_KEY).that(readValue).isEqualTo(overriddenValue);

        // Put should read the existing value
        assertWithMessage("putStringIfNew(%s, let)", TEST_KEY)
                .that(mDatastore.putStringIfNew(TEST_KEY, "let"))
                .isEqualTo(overriddenValue);
        readValue = mDatastore.getString(TEST_KEY);
        assertWithMessage("getString(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("getString(%s)", TEST_KEY).that(readValue).isEqualTo(overriddenValue);

        // Overwrite with incorrect data type
        mDatastore.putInt(TEST_KEY, 3);
        assertThrows(
                IllegalStateException.class, () -> mDatastore.putStringIfNew(TEST_KEY, "test"));
    }

    @Test
    public void testKeySetClear() throws IOException {
        int numEntries = 10;
        for (int i = 0; i < numEntries; i++) {
            // Even entries are true, odd are false
            mDatastore.putBoolean(TEST_KEY + i, (i & 1) == 0);
        }

        Set<String> trueKeys = mDatastore.keySetTrue();
        Set<String> falseKeys = mDatastore.keySetFalse();

        expect.withMessage("keySetTrue()").that(mDatastore.keySetTrue()).hasSize(numEntries / 2);
        expect.withMessage("keySetFalse()").that(mDatastore.keySetFalse()).hasSize(numEntries / 2);

        for (int i = 0; i < numEntries; i++) {
            String expectedKey = TEST_KEY + i;
            if ((i & 1) == 0) {
                expect.withMessage("keySetTrue() / index %s", i)
                        .that(trueKeys)
                        .contains(expectedKey);
                expect.withMessage("keySetFalse() / index %s", i)
                        .that(falseKeys)
                        .doesNotContain(expectedKey);

            } else {
                expect.withMessage("keySetTrue() / index %s", i)
                        .that(trueKeys)
                        .doesNotContain(expectedKey);
                expect.withMessage("keySetFalse() / index %s", i)
                        .that(falseKeys)
                        .contains(expectedKey);
            }
        }

        mDatastore.clear();
        expect.withMessage("keys").that(mDatastore.keySet()).isEmpty();
    }

    @Test
    public void testKeySetClearAllTrue() throws IOException {
        int numEntries = 10;
        for (int i = 0; i < numEntries; i++) {
            // Even entries are true, odd are false
            mDatastore.putBoolean(TEST_KEY + i, (i & 1) == 0);
        }

        Set<String> trueKeys = mDatastore.keySetTrue();
        Set<String> falseKeys = mDatastore.keySetFalse();

        expect.withMessage("keySetTrue()").that(mDatastore.keySetTrue()).hasSize(numEntries / 2);
        expect.withMessage("keySetFalse()").that(mDatastore.keySetFalse()).hasSize(numEntries / 2);

        for (int i = 0; i < numEntries; i++) {
            String expectedKey = TEST_KEY + i;
            if ((i & 1) == 0) {
                expect.withMessage("keySetTrue() / index %s", i)
                        .that(trueKeys)
                        .contains(expectedKey);
                expect.withMessage("keySetFalse() / index %s", i)
                        .that(falseKeys)
                        .doesNotContain(expectedKey);

            } else {
                expect.withMessage("keySetTrue() / index %s", i)
                        .that(trueKeys)
                        .doesNotContain(expectedKey);
                expect.withMessage("keySetFalse() / index %s", i)
                        .that(falseKeys)
                        .contains(expectedKey);
            }
        }

        mDatastore.clearAllTrue();

        trueKeys = mDatastore.keySetTrue();
        falseKeys = mDatastore.keySetFalse();

        expect.withMessage("keySetTrue()").that(trueKeys).isEmpty();
        expect.withMessage("keySetFalse()").that(falseKeys).hasSize(numEntries / 2);

        for (int i = 0; i < numEntries; i++) {
            IterableSubject subject =
                    expect.withMessage("keySetFalse() at index %s", i).that(falseKeys);
            String expectedKey = TEST_KEY + i;
            if ((i & 1) != 0) {
                subject.contains(expectedKey);
            } else {
                subject.doesNotContain(expectedKey);
            }
        }
    }

    @Test
    public void testKeySetClearAllFalse() throws IOException {
        int numEntries = 10;
        for (int i = 0; i < numEntries; i++) {
            // Even entries are true, odd are false
            mDatastore.putBoolean(TEST_KEY + i, (i & 1) == 0);
        }

        Set<String> trueKeys = mDatastore.keySetTrue();
        Set<String> falseKeys = mDatastore.keySetFalse();

        expect.withMessage("keySetTrue()").that(mDatastore.keySetTrue()).hasSize(numEntries / 2);
        expect.withMessage("keySetFalse()").that(mDatastore.keySetFalse()).hasSize(numEntries / 2);

        for (int i = 0; i < numEntries; i++) {
            String expectedKey = TEST_KEY + i;
            if ((i & 1) == 0) {
                expect.withMessage("keySetTrue() / index %s", i)
                        .that(trueKeys)
                        .contains(expectedKey);
                expect.withMessage("keySetFalse() / index %s", i)
                        .that(falseKeys)
                        .doesNotContain(expectedKey);

            } else {
                expect.withMessage("keySetTrue() / index %s", i)
                        .that(trueKeys)
                        .doesNotContain(expectedKey);
                expect.withMessage("keySetFalse() / index %s", i)
                        .that(falseKeys)
                        .contains(expectedKey);
            }
        }

        mDatastore.clearAllFalse();

        trueKeys = mDatastore.keySetTrue();
        falseKeys = mDatastore.keySetFalse();

        expect.withMessage("keySetTrue()").that(trueKeys).hasSize(numEntries / 2);
        expect.withMessage("keySetFalse()").that(falseKeys).isEmpty();

        for (int i = 0; i < numEntries; i++) {
            String expectedKey = TEST_KEY + i;
            if ((i & 1) == 0) {
                expect.withMessage("keySetTrue() / index %s", i)
                        .that(trueKeys)
                        .contains(expectedKey);
                expect.withMessage("keySetFalse() / index %s", i)
                        .that(falseKeys)
                        .doesNotContain(expectedKey);
            }
        }
    }

    @Test
    public void testDump_noEntries() throws Exception {
        String prefix = "_";

        String dump = dump(pw -> mDatastore.dump(pw, prefix));

        assertCommonDumpContents(dump, prefix);
        expect.withMessage("contents of dump() (# keys)").that(dump).containsMatch("0 entries\n");
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testDump_R() throws Exception {
        dumpTest(/* isAtleastS= */ false);
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testDump_sPlus() throws Exception {
        dumpTest(/* isAtleastS= */ true);
    }

    private void dumpTest(boolean isAtleastS) throws Exception {
        mocker.mockIsAtLeastS(isAtleastS);

        String keyUnlikelyToBeOnDump = "I can't believe it's dumper!";
        mDatastore.putBoolean(keyUnlikelyToBeOnDump, true);

        String prefix = "_";
        String dump = dump(pw -> mDatastore.dump(pw, prefix));
        mLog.d("Contents of dump: \n%s", dump);

        assertCommonDumpContents(dump, prefix);

        expect.withMessage("contents of dump() (# keys)").that(dump).containsMatch("1 entries\n");
        // Make sure content of datastore itself is not dumped, as it could contain PII
        expect.withMessage("contents of dump()").that(dump).doesNotContain(keyUnlikelyToBeOnDump);

        StringSubject atomicFileSubject =
                expect.withMessage("contents of dump() - atomic file").that(dump);
        if (isAtleastS) {
            atomicFileSubject.contains("last modified");
        } else {
            atomicFileSubject.doesNotContain("last modified");
        }
    }

    @Test
    public void testRemovePrefix() throws IOException {
        // Create
        int numEntries = 10;
        // Keys begin with either TEST_KEY+0 or TEST_KEY+1. Even entries are true, odd are false.
        List<Pair<String, Boolean>> entriesToAdd =
                IntStream.range(0, numEntries)
                        .mapToObj(i -> new Pair<>(TEST_KEY + (i & 1) + i, (i & 1) == 0))
                        .collect(Collectors.toList());

        // Add to data store
        for (Pair<String, Boolean> entry : entriesToAdd) {
            expect.withMessage("getBoolean(%s)", entry.first)
                    .that(mDatastore.getBoolean(entry.first))
                    .isNull(); // Should not exist yet
            mDatastore.putBoolean(entry.first, entry.second);
        }

        // Delete everything beginning with TEST_KEY + 0.
        // This should leave behind all keys with starting with TEST_KEY + 1.
        mDatastore.removeByPrefix(TEST_KEY + 0);

        // Compute the expected set of entries that should remain.
        Set<Pair<String, Boolean>> entriesThatShouldRemain =
                entriesToAdd.stream()
                        .filter(s -> s.first.startsWith(TEST_KEY + 1))
                        .collect(Collectors.toSet());

        // Verify that all the expected keys remain in the data store, with the right values.
        assertWithMessage("keys that remain")
                .that(mDatastore.keySet())
                .hasSize(entriesThatShouldRemain.size());
        for (Pair<String, Boolean> item : entriesThatShouldRemain) {
            expect.withMessage("entry that should remain (key %s)", item.first)
                    .that(mDatastore.getBoolean(item.first))
                    .isEqualTo(item.second);
        }
    }

    private void assertCommonDumpContents(String dump, String prefix) {
        assertDumpHasPrefix(dump, prefix);
        expect.withMessage("contents of dump() (DATASTORE_VERSION)")
                .that(dump)
                .contains(Integer.toString(DATASTORE_VERSION));
        expect.withMessage("contents of dump() (FILENAME)").that(dump).contains(FILENAME);
        expect.withMessage("contents of dump() (TEST_VERSION_KEY)")
                .that(dump)
                .contains(TEST_VERSION_KEY);
    }
}
