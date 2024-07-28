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

import static com.android.adservices.shared.testing.common.DumpHelper.assertDumpHasPrefix;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.util.Pair;

import com.android.adservices.shared.SharedExtendedMockitoTestCase;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.truth.IterableSubject;
import com.google.common.truth.StringSubject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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
    private static final String TEST_VERSION_KEY = "version_key";

    private final AtomicFileDatastore mDatastore =
            new AtomicFileDatastore(VALID_DIR, FILENAME, DATASTORE_VERSION, TEST_VERSION_KEY);

    @Before
    public void initializeDatastore() throws IOException {
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
                                TEST_VERSION_KEY));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AtomicFileDatastore(
                                /* parentPath= */ "",
                                FILENAME,
                                DATASTORE_VERSION,
                                TEST_VERSION_KEY));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AtomicFileDatastore(
                                VALID_DIR,
                                /* filename= */ null,
                                DATASTORE_VERSION,
                                TEST_VERSION_KEY));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AtomicFileDatastore(
                                VALID_DIR,
                                /* filename= */ "",
                                DATASTORE_VERSION,
                                TEST_VERSION_KEY));

        // File constructor
        assertThrows(
                NullPointerException.class,
                () ->
                        new AtomicFileDatastore(
                                /* file= */ null, DATASTORE_VERSION, TEST_VERSION_KEY));
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
                                TEST_VERSION_KEY));
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
                                    path, FILENAME, DATASTORE_VERSION, TEST_VERSION_KEY));
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
        assertThrows(NullPointerException.class, () -> mDatastore.putBoolean(null, true));

        assertThrows(IllegalArgumentException.class, () -> mDatastore.putBoolean("", true));

        assertThrows(NullPointerException.class, () -> mDatastore.putBooleanIfNew(null, true));

        assertThrows(IllegalArgumentException.class, () -> mDatastore.putBooleanIfNew("", true));

        assertThrows(NullPointerException.class, () -> mDatastore.getBoolean(null));

        assertThrows(IllegalArgumentException.class, () -> mDatastore.getBoolean(""));

        assertThrows(NullPointerException.class, () -> mDatastore.remove(null));

        assertThrows(IllegalArgumentException.class, () -> mDatastore.remove(""));

        assertThrows(NullPointerException.class, () -> mDatastore.removeByPrefix(null));

        assertThrows(IllegalArgumentException.class, () -> mDatastore.removeByPrefix(""));
    }

    @Test
    public void testGetVersionKey() {
        assertWithMessage("getVersionKey()")
                .that(mDatastore.getVersionKey())
                .isEqualTo(TEST_VERSION_KEY);
    }

    @Test
    public void testWriteAndGetVersion() throws IOException {
        // Write value
        boolean insertedValue = false;
        mDatastore.putBoolean(TEST_KEY, insertedValue);

        // Re-initialize datastore (reads from the file again)
        mDatastore.initialize();

        assertWithMessage("getPreviousStoredVersion()")
                .that(mDatastore.getPreviousStoredVersion())
                .isEqualTo(DATASTORE_VERSION);
    }

    @Test
    public void testGetVersionWithNoPreviousWrite() {
        assertWithMessage("getPreviousStoredVersion()")
                .that(mDatastore.getPreviousStoredVersion())
                .isEqualTo(AtomicFileDatastore.NO_PREVIOUS_VERSION);
    }

    @Test
    public void testPutGetUpdateRemove() throws IOException {
        // Should not exist yet
        assertWithMessage("get(%s)", TEST_KEY).that(mDatastore.getBoolean(TEST_KEY)).isNull();

        // Create
        boolean insertedValue = false;
        mDatastore.putBoolean(TEST_KEY, insertedValue);

        // Read
        Boolean readValue = mDatastore.getBoolean(TEST_KEY);
        assertWithMessage("get(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("get(%s)", TEST_KEY).that(readValue).isEqualTo(insertedValue);

        // Update
        insertedValue = true;
        mDatastore.putBoolean(TEST_KEY, insertedValue);
        readValue = mDatastore.getBoolean(TEST_KEY);
        assertWithMessage("get(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("get(%s)", TEST_KEY).that(readValue).isEqualTo(insertedValue);

        assertWithMessage("keys").that(mDatastore.keySet()).containsExactly(TEST_KEY);

        // Delete
        mDatastore.remove(TEST_KEY);
        assertWithMessage("get(%s)", TEST_KEY).that(mDatastore.getBoolean(TEST_KEY)).isNull();
        assertWithMessage("keys").that(mDatastore.keySet()).isEmpty();

        // Should not throw when removing a nonexistent key
        mDatastore.remove(TEST_KEY);
    }

    @Test
    public void testPutIfNew() throws IOException {
        // Should not exist yet
        assertWithMessage("get(%s)", TEST_KEY).that(mDatastore.getBoolean(TEST_KEY)).isNull();

        // Create because it's new
        assertWithMessage("putIfNew(%s, false)", TEST_KEY)
                .that(mDatastore.putBooleanIfNew(TEST_KEY, false))
                .isFalse();
        Boolean readValue = mDatastore.getBoolean(TEST_KEY);
        assertWithMessage("get(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("get(%s)", TEST_KEY).that(readValue).isFalse();

        // Force overwrite
        mDatastore.putBoolean(TEST_KEY, true);
        readValue = mDatastore.getBoolean(TEST_KEY);
        assertWithMessage("get(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("get(%s)", TEST_KEY).that(readValue).isTrue();

        // Put should read the existing value
        assertWithMessage("putIfNew(%s, false)", TEST_KEY)
                .that(mDatastore.putBooleanIfNew(TEST_KEY, false))
                .isTrue();
        readValue = mDatastore.getBoolean(TEST_KEY);
        assertWithMessage("get(%s)", TEST_KEY).that(readValue).isNotNull();
        assertWithMessage("get(%s)", TEST_KEY).that(readValue).isTrue();
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
            expect.withMessage("get(%s)", entry.first)
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
