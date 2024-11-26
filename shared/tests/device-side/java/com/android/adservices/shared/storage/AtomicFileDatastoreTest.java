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
import static com.android.adservices.shared.storage.AtomicFileDatastore.DUMP_ARGS_INCLUDE_CONTENTS_ONLY;
import static com.android.adservices.shared.testing.common.DumpHelper.assertDumpHasPrefix;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.adservices.shared.testing.common.FileHelper.deleteFile;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.PersistableBundle;
import android.util.AtomicFile;
import android.util.Pair;

import com.android.adservices.shared.SharedExtendedMockitoTestCase;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;

import com.google.common.truth.IterableSubject;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class AtomicFileDatastoreTest extends SharedExtendedMockitoTestCase {

    private static final String VALID_DIR = sContext.getFilesDir().getAbsolutePath();
    private static final String FILENAME = "AtomicFileDatastoreTest.xml";

    private static final int DATASTORE_VERSION = 1;
    private static final String NULL_KEY = null;
    private static final String TEST_KEY = "key";
    private static final String TEST_KEY_1 = "key1";
    private static final String TEST_KEY_2 = "key2";
    private static final String TEST_KEY_3 = "key3";
    private static final String TEST_KEY_4 = "key4";
    private static final String TEST_KEY_5 = "key5";
    private static final String TEST_KEY_6 = "key6";
    private static final String TEST_KEY_7 = "key7";
    private static final String TEST_VERSION_KEY = "version_key";

    private static final String[] DUMP_NO_ARGS = null;

    @Mock private AdServicesErrorLogger mMockAdServicesErrorLogger;

    private AtomicFileDatastore mDatastore;

    @Before
    public void initializeDatastore() throws Exception {
        mDatastore = newInitializedDataStore(/* wipeFile= */ true);
    }

    private AtomicFileDatastore newInitializedDataStore(boolean wipeFile) throws IOException {
        File datastoreFile = new File(VALID_DIR, FILENAME);
        if (wipeFile) {
            deleteFile(datastoreFile);
        }
        var datastore =
                new AtomicFileDatastore(
                        datastoreFile,
                        DATASTORE_VERSION,
                        TEST_VERSION_KEY,
                        mMockAdServicesErrorLogger);
        datastore.initialize();
        return datastore;
    }

    @Test
    public void testConstructor_emptyOrNullArgs() {
        File datastoreFile = new File(VALID_DIR, FILENAME);
        assertThrows(
                NullPointerException.class,
                () ->
                        new AtomicFileDatastore(
                                (File) null,
                                DATASTORE_VERSION,
                                TEST_VERSION_KEY,
                                mMockAdServicesErrorLogger));

        assertThrows(
                NullPointerException.class,
                () ->
                        new AtomicFileDatastore(
                                datastoreFile,
                                DATASTORE_VERSION,
                                /* versionKey= */ null,
                                mMockAdServicesErrorLogger));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AtomicFileDatastore(
                                datastoreFile,
                                DATASTORE_VERSION,
                                /* versionKey= */ "",
                                mMockAdServicesErrorLogger));

        assertThrows(
                NullPointerException.class,
                () ->
                        new AtomicFileDatastore(
                                datastoreFile,
                                DATASTORE_VERSION,
                                TEST_VERSION_KEY,
                                /* adServicesErrorLogger= */ null));
    }

    @Test
    public void testConstructor_parentPathDirectoryDoesNotExist() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AtomicFileDatastore(
                                new File("I can't believe this is a valid dir", FILENAME),
                                DATASTORE_VERSION,
                                TEST_VERSION_KEY,
                                mMockAdServicesErrorLogger));
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

        assertThrows(
                exceptionClass,
                () -> mDatastore.update(updateOperation -> updateOperation.putBoolean(key, true)));
        assertThrows(
                exceptionClass,
                () -> mDatastore.update(updateOperation -> updateOperation.putInt(key, 7)));
        assertThrows(
                exceptionClass,
                () -> mDatastore.update(updateOperation -> updateOperation.putString(key, "foo")));
        assertThrows(
                exceptionClass,
                () ->
                        mDatastore.update(
                                updateOperation -> updateOperation.putBooleanIfNew(key, true)));
        assertThrows(
                exceptionClass,
                () -> mDatastore.update(updateOperation -> updateOperation.putIntIfNew(key, 7)));
        assertThrows(
                exceptionClass,
                () ->
                        mDatastore.update(
                                updateOperation -> updateOperation.putStringIfNew(key, "foo")));
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
    public void testNullKeyFails() {
        assertThrows(NullPointerException.class, () -> mDatastore.putBoolean(NULL_KEY, true));
        assertThrows(NullPointerException.class, () -> mDatastore.putInt(NULL_KEY, 42));
        assertThrows(NullPointerException.class, () -> mDatastore.putString(NULL_KEY, "D'OH!"));

        assertThrows(NullPointerException.class, () -> mDatastore.putBooleanIfNew(NULL_KEY, true));
        assertThrows(NullPointerException.class, () -> mDatastore.putIntIfNew(NULL_KEY, 42));

        assertThrows(
                NullPointerException.class, () -> mDatastore.putStringIfNew(NULL_KEY, "D'OH!"));

        assertThrows(NullPointerException.class, () -> mDatastore.getBoolean(NULL_KEY));
        assertThrows(NullPointerException.class, () -> mDatastore.getBoolean(NULL_KEY, true));
        assertThrows(NullPointerException.class, () -> mDatastore.getInt(NULL_KEY));
        assertThrows(NullPointerException.class, () -> mDatastore.getInt(NULL_KEY, 42));
        assertThrows(NullPointerException.class, () -> mDatastore.getString(NULL_KEY));
        assertThrows(NullPointerException.class, () -> mDatastore.getString(NULL_KEY, "D'OH!"));
    }

    @Test
    public void testEmptKeyFails() {
        assertThrows(IllegalArgumentException.class, () -> mDatastore.putBoolean("", true));
        assertThrows(IllegalArgumentException.class, () -> mDatastore.putInt("", 42));
        assertThrows(IllegalArgumentException.class, () -> mDatastore.putString("", "D'OH!"));

        assertThrows(IllegalArgumentException.class, () -> mDatastore.putBooleanIfNew("", true));
        assertThrows(IllegalArgumentException.class, () -> mDatastore.putIntIfNew("", 42));

        assertThrows(IllegalArgumentException.class, () -> mDatastore.putStringIfNew("", "D'OH!"));

        assertThrows(IllegalArgumentException.class, () -> mDatastore.getBoolean(""));
        assertThrows(IllegalArgumentException.class, () -> mDatastore.getBoolean("", true));
        assertThrows(IllegalArgumentException.class, () -> mDatastore.getInt(""));
        assertThrows(IllegalArgumentException.class, () -> mDatastore.getInt("", 42));
        assertThrows(IllegalArgumentException.class, () -> mDatastore.getString(""));
        assertThrows(IllegalArgumentException.class, () -> mDatastore.getString("", "D'OH!"));
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
    public void testPutBoolean_ioExceptionOccurs_noWriteToFile() throws Exception {
        datastoreIoExceptionTestHelper(datastore -> datastore.putBoolean(TEST_KEY, true));
    }

    @Test
    public void testPutInt_ioExceptionOccurs_noWriteToFile() throws Exception {
        datastoreIoExceptionTestHelper(datastore -> datastore.putInt(TEST_KEY_1, 6));
    }

    @Test
    public void testPutString_ioExceptionOccurs_noWriteToFile() throws Exception {
        datastoreIoExceptionTestHelper(datastore -> datastore.putString(TEST_KEY_2, "abc"));
    }

    @Test
    public void testPutBooleanIfNew_ioExceptionOccurs_noWriteToFile() throws Exception {
        datastoreIoExceptionTestHelper(datastore -> datastore.putBooleanIfNew(TEST_KEY_5, true));
    }

    @Test
    public void testPutIntIfNew_ioExceptionOccurs_noWriteToFile() throws Exception {
        datastoreIoExceptionTestHelper(datastore -> datastore.putIntIfNew(TEST_KEY_6, 8));
    }

    @Test
    public void testPutStringIfNew_ioExceptionOccurs_noWriteToFile() throws Exception {
        datastoreIoExceptionTestHelper(datastore -> datastore.putStringIfNew(TEST_KEY_7, "hello"));
    }

    @Test
    public void testClearAllTrue_ioExceptionOccurs_noWriteToFile() throws Exception {
        datastoreIoExceptionTestHelper(AtomicFileDatastore::clearAllTrue);
    }

    @Test
    public void testClearAllFalse_ioExceptionOccurs_noWriteToFile() throws Exception {
        datastoreIoExceptionTestHelper(AtomicFileDatastore::clearAllFalse);
    }

    @Test
    public void testRemove_ioExceptionOccurs_noWriteToFile() throws Exception {
        datastoreIoExceptionTestHelper(datastore -> datastore.remove(TEST_KEY_1));
    }

    @Test
    public void testRemoveByPrefix_ioExceptionOccurs_noWriteToFile() throws Exception {
        datastoreIoExceptionTestHelper(datastore -> datastore.removeByPrefix(TEST_KEY));
    }

    @Test
    public void testUpdate_ioExceptionOccurs_noWriteToFile() throws Exception {
        datastoreIoExceptionTestHelper(
                datastore ->
                        datastore.update(
                                updateOperation -> updateOperation.putBoolean(TEST_KEY, true)));
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
        Integer readValue = mDatastore.getInt(TEST_KEY);
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

        String dump = dump(pw -> mDatastore.dump(pw, prefix, DUMP_NO_ARGS));

        assertCommonDumpContents(dump, prefix);
        expect.withMessage("contents of dump() (# keys)").that(dump).containsMatch("0 entries\n");
    }

    @Test
    public void testDump_fullContent_noEntries() throws Exception {
        String prefix = "_";

        String dump = dump(pw -> mDatastore.dump(pw, prefix, DUMP_ARGS_INCLUDE_CONTENTS_ONLY));

        assertCommonDumpContents(dump, prefix);
        expect.withMessage("contents of dump() (# keys)").that(dump).containsMatch("0 entries\n");
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "getLastModifiedTime() is not available on R")
    public void testDump() throws Exception {
        String keyUnlikelyToBeOnDump = "I can't believe it's dumper!";
        mDatastore.putBoolean(keyUnlikelyToBeOnDump, true);

        String prefix = "_";
        String dump = dump(pw -> mDatastore.dump(pw, prefix, DUMP_NO_ARGS));
        mLog.d("Contents of dump: \n%s", dump);

        assertCommonDumpContents(dump, prefix);

        expect.withMessage("contents of dump() (# keys)").that(dump).containsMatch("1 entries\n");
        // Make sure content of datastore itself is not dumped, as it could contain PII
        expect.withMessage("contents of dump()").that(dump).doesNotContain(keyUnlikelyToBeOnDump);
        expect.withMessage("contents of dump() - atomic file").that(dump).contains("last modified");
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "getLastModifiedTime() is not available on R")
    public void testDump_fullContent() throws Exception {
        mDatastore.putBoolean("BOO!", true);
        mDatastore.putInt("INT?", 42);

        String prefix = "_";
        String dump = dump(pw -> mDatastore.dump(pw, "_", DUMP_ARGS_INCLUDE_CONTENTS_ONLY));
        mLog.d("Contents of dump: \n%s", dump);

        assertCommonDumpContents(dump, prefix);

        expect.withMessage("contents of dump() (# keys)")
                .that(dump)
                .containsMatch("_2 entries:\n__BOO\\!: true\n__INT\\?: 42");
        expect.withMessage("contents of dump() - atomic file").that(dump).contains("last modified");
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

    @Test
    public void testUpdate() throws Exception {
        File file = deleteFile(VALID_DIR, FILENAME);
        AtomicFile atomicFile = spy(new AtomicFile(file));
        AtomicFileDatastore datastore =
                new AtomicFileDatastore(
                        atomicFile,
                        DATASTORE_VERSION,
                        TEST_VERSION_KEY,
                        mMockAdServicesErrorLogger);
            datastore.update(
                    updateOperation -> {
                        updateOperation.putBoolean(TEST_KEY, false);
                        updateOperation.putInt(TEST_KEY_1, 3);
                        updateOperation.putString(TEST_KEY_2, "bar");
                        updateOperation.putBooleanIfNew(TEST_KEY, true);
                        updateOperation.putIntIfNew(TEST_KEY_1, 2);
                        updateOperation.putStringIfNew(TEST_KEY_2, "newBar");
                        updateOperation.putBooleanIfNew(TEST_KEY_3, true);
                        updateOperation.putIntIfNew(TEST_KEY_4, 7);
                        updateOperation.putStringIfNew(TEST_KEY_5, "foo");
                    });

            Map<String, Object> expectedMap =
                    Map.of(
                            TEST_KEY,
                            false,
                            TEST_KEY_1,
                            3,
                            TEST_KEY_2,
                            "bar",
                            TEST_KEY_3,
                            true,
                            TEST_KEY_4,
                            7,
                            TEST_KEY_5,
                            "foo",
                            TEST_VERSION_KEY,
                            1);

            assertWithMessage("getBoolean(%s)", TEST_KEY)
                    .that(datastore.getBoolean(TEST_KEY))
                    .isEqualTo(false);
            assertWithMessage("getInt(%s)", TEST_KEY_1)
                    .that(datastore.getInt(TEST_KEY_1))
                    .isEqualTo(3);
            assertWithMessage("getString(%s)", TEST_KEY_2)
                    .that(datastore.getString(TEST_KEY_2))
                    .isEqualTo("bar");
            assertWithMessage("getBoolean(%s)", TEST_KEY_3)
                    .that(datastore.getBoolean(TEST_KEY_3))
                    .isEqualTo(true);
            assertWithMessage("getInt(%s)", TEST_KEY_4)
                    .that(datastore.getInt(TEST_KEY_4))
                    .isEqualTo(7);

            assertWithMessage("getString(%s)", TEST_KEY_5)
                    .that(datastore.getString(TEST_KEY_5))
                    .isEqualTo("foo");

            assertWithMessage("datastore content check")
                    .that(readFromAtomicFile(atomicFile))
                    .containsExactlyEntriesIn(expectedMap);

            verify(atomicFile).startWrite();
    }

    @Test
    public void testUpdate_noUpdateOnSameData() throws Exception {
        File file = deleteFile(VALID_DIR, FILENAME);
        AtomicFile atomicFile = spy(new AtomicFile(file));
        AtomicFileDatastore datastore =
                new AtomicFileDatastore(
                        atomicFile,
                        DATASTORE_VERSION,
                        TEST_VERSION_KEY,
                        mMockAdServicesErrorLogger);

            datastore.update(
                    updateOperation -> {
                        updateOperation.putBoolean(TEST_KEY, false);
                        updateOperation.putInt(TEST_KEY_1, 3);
                        updateOperation.putString(TEST_KEY_2, "bar");
                        updateOperation.putBooleanIfNew(TEST_KEY_3, true);
                        updateOperation.putIntIfNew(TEST_KEY_4, 7);
                        updateOperation.putStringIfNew(TEST_KEY_5, "foo");
                    });

            verify(atomicFile).startWrite();

            // verify no update to file if writing the same data
            datastore.update(
                    updateOperation -> {
                        updateOperation.putBoolean(TEST_KEY, false);
                        updateOperation.putInt(TEST_KEY_1, 3);
                        updateOperation.putString(TEST_KEY_2, "bar");
                        updateOperation.putBooleanIfNew(TEST_KEY_3, true);
                        updateOperation.putIntIfNew(TEST_KEY_4, 7);
                        updateOperation.putStringIfNew(TEST_KEY_5, "foo");
                    });
            verify(atomicFile).startWrite();
    }

    @Test
    public void testUpdate_putIfType_overwriteIncorrectWrite() throws Exception {
        mDatastore.update(
                updateOperation -> {
                    updateOperation.putBoolean(TEST_KEY, false);
                    updateOperation.putInt(TEST_KEY_1, 3);
                    updateOperation.putString(TEST_KEY_2, "foo");
                });

        assertThrows(
                IllegalStateException.class,
                () ->
                        mDatastore.update(
                                updateOperation -> updateOperation.putIntIfNew(TEST_KEY, 5)));
        assertThrows(
                IllegalStateException.class,
                () ->
                        mDatastore.update(
                                updateOperation ->
                                        updateOperation.putStringIfNew(TEST_KEY, "test")));
        assertThrows(
                IllegalStateException.class,
                () ->
                        mDatastore.update(
                                updateOperation ->
                                        updateOperation.putBooleanIfNew(TEST_KEY_1, true)));
        assertThrows(
                IllegalStateException.class,
                () ->
                        mDatastore.update(
                                updateOperation ->
                                        updateOperation.putStringIfNew(TEST_KEY_1, "test")));
        assertThrows(
                IllegalStateException.class,
                () ->
                        mDatastore.update(
                                updateOperation ->
                                        updateOperation.putBooleanIfNew(TEST_KEY_2, true)));
        assertThrows(
                IllegalStateException.class,
                () ->
                        mDatastore.update(
                                updateOperation -> updateOperation.putIntIfNew(TEST_KEY_2, 6)));
    }

    @Test
    public void testClear() throws Exception {
        mDatastore.update(txn -> txn.putBoolean(TEST_KEY, true));

        mDatastore.clear();

        expect.withMessage("number of entries after clear()").that(mDatastore.keySet()).isEmpty();

        // Create a new datastore using the same file - it should be empty
        var clonedDatastore = newInitializedDataStore(/* wipeFile= */ false);
        expect.withMessage("number of entries on new datastore using same file")
                .that(clonedDatastore.keySet())
                .isEmpty();
    }

    @Test
    public void testToString() throws Exception {
        String before = mDatastore.toString();
        expect.withMessage("empty toString()").that(before).startsWith("AtomicFileDatastore[");
        expect.withMessage("empty toString()")
                .that(before)
                .contains("path=" + new File(VALID_DIR, FILENAME).getAbsolutePath());
        expect.withMessage("empty toString()").that(before).contains("entries=0");
        expect.withMessage("empty toString()")
                .that(before)
                .contains("version=" + DATASTORE_VERSION);
        expect.withMessage("empty toString()").that(before).endsWith("]");
        mDatastore.update(txn -> txn.putBoolean(TEST_KEY, false));

        expect.withMessage("toString() after adding 1 entry")
                .that(mDatastore.toString())
                .contains("entries=1");

        mDatastore.update(txn -> txn.putBoolean(TEST_KEY, true));
        expect.withMessage("toString() after updating existing entry")
                .that(mDatastore.toString())
                .contains("entries=1");
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

    private static Map<String, Object> readFromAtomicFile(AtomicFile atomicFile) throws Exception {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(atomicFile.readFully())) {
            PersistableBundle bundleRead = PersistableBundle.readFromStream(inputStream);
            return bundleRead.keySet().stream()
                    .collect(Collectors.toMap(key -> key, key -> bundleRead.get(key)));
        }
    }

    private void datastoreIoExceptionTestHelper(ThrowingRunnable runnable) throws Exception {
        File file = deleteFile(VALID_DIR, FILENAME);
        AtomicFile atomicFile = spy(new AtomicFile(file));
        AtomicFileDatastore datastore =
                new AtomicFileDatastore(
                        atomicFile,
                        DATASTORE_VERSION,
                        TEST_VERSION_KEY,
                        mMockAdServicesErrorLogger);

        datastore.putBoolean(TEST_KEY, false);
        datastore.putInt(TEST_KEY_1, 3);
        datastore.putString(TEST_KEY_2, "bar");
        datastore.putBoolean(TEST_KEY_3, true);
        datastore.putBoolean(TEST_KEY_4, true);

        Map<String, Object> expectedMap =
                Map.of(
                        TEST_KEY,
                        false,
                        TEST_KEY_1,
                        3,
                        TEST_KEY_2,
                        "bar",
                        TEST_KEY_3,
                        true,
                        TEST_KEY_4,
                        true,
                        TEST_VERSION_KEY,
                        1);

        assertWithMessage("datastore content check")
                .that(readFromAtomicFile(atomicFile))
                .containsExactlyEntriesIn(expectedMap);

        // No file and local map update when exception occurs
        when(atomicFile.startWrite()).thenThrow(new IOException("write failure"));

        assertThrows(IOException.class, () -> runnable.run(datastore));

        assertWithMessage("getBoolean(%s)", TEST_KEY)
                .that(datastore.getBoolean(TEST_KEY))
                .isEqualTo(false);
        assertWithMessage("getInt(%s)", TEST_KEY_1).that(datastore.getInt(TEST_KEY_1)).isEqualTo(3);
        assertWithMessage("getString(%s)", TEST_KEY_2)
                .that(datastore.getString(TEST_KEY_2))
                .isEqualTo("bar");
        assertWithMessage("getBoolean(%s)", TEST_KEY_3)
                .that(datastore.getBoolean(TEST_KEY_3))
                .isEqualTo(true);
        assertWithMessage("getBoolean(%s)", TEST_KEY_4)
                .that(datastore.getBoolean(TEST_KEY_4))
                .isEqualTo(true);

        assertWithMessage("getBoolean(%s)", TEST_KEY_5)
                .that(datastore.getBoolean(TEST_KEY_5))
                .isNull();
        assertWithMessage("getInt(%s)", TEST_KEY_6).that(datastore.getInt(TEST_KEY_6)).isNull();
        assertWithMessage("getString(%s)", TEST_KEY_7)
                .that(datastore.getString(TEST_KEY_7))
                .isNull();

        assertWithMessage("datastore content check after write failure")
                .that(readFromAtomicFile(atomicFile))
                .containsExactlyEntriesIn(expectedMap);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run(AtomicFileDatastore datastore) throws Exception;
    }
}
