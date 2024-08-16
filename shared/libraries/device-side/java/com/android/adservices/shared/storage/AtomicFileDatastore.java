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

package com.android.adservices.shared.storage;

import static com.android.adservices.shared.util.LogUtil.DEBUG;
import static com.android.adservices.shared.util.LogUtil.VERBOSE;
import static com.android.adservices.shared.util.Preconditions.checkState;
import static com.android.internal.util.Preconditions.checkArgumentNonnegative;
import static com.android.internal.util.Preconditions.checkStringNotEmpty;

import android.annotation.Nullable;
import android.os.PersistableBundle;
import android.util.AtomicFile;

import com.android.adservices.shared.util.LogUtil;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * A simple datastore utilizing {@link android.util.AtomicFile} and {@link
 * android.os.PersistableBundle} to read/write a simple key/value map to file.
 *
 * <p>The datastore is loaded from file only when initialized and written to file on every write.
 * When using this datastore, it is up to the caller to ensure that each datastore file is accessed
 * by exactly one datastore object. If multiple writing threads or processes attempt to use
 * different instances pointing to the same file, transactions may be lost.
 *
 * <p>Keys must be non-{@code null}, non-empty strings, and values can be booleans, integers or
 * strings.
 *
 * @threadsafe
 */
public class AtomicFileDatastore {
    public static final int NO_PREVIOUS_VERSION = -1;

    private final int mDatastoreVersion;

    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();
    private final Lock mReadLock = mReadWriteLock.readLock();
    private final Lock mWriteLock = mReadWriteLock.writeLock();

    private final AtomicFile mAtomicFile;
    private final Map<String, Object> mLocalMap = new HashMap<>();

    private final String mVersionKey;
    private int mPreviousStoredVersion;

    public AtomicFileDatastore(
            String parentPath, String filename, int datastoreVersion, String versionKey) {
        this(newFile(parentPath, filename), datastoreVersion, versionKey);
    }

    public AtomicFileDatastore(File file, int datastoreVersion, String versionKey) {
        mAtomicFile = new AtomicFile(Objects.requireNonNull(file));
        mDatastoreVersion =
                checkArgumentNonnegative(datastoreVersion, "Version must not be negative");

        mVersionKey = Objects.requireNonNull(versionKey);
    }

    /**
     * Loads data from the datastore file.
     *
     * @throws IOException if file read fails
     */
    public final void initialize() throws IOException {
        if (DEBUG) {
            LogUtil.d("Reading from store file: %s", mAtomicFile.getBaseFile());
        }
        mReadLock.lock();
        try {
            readFromFile();
        } finally {
            mReadLock.unlock();
        }

        // In the future, this could be a good place for upgrade/rollback for schemas
    }

    // Writes the class member map to a PersistableBundle which is then written to file.
    @GuardedBy("mWriteLock")
    private void writeToFile() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PersistableBundle bundleToWrite = new PersistableBundle();

        for (Map.Entry<String, Object> entry : mLocalMap.entrySet()) {
            addToBundle(bundleToWrite, entry.getKey(), entry.getValue());
        }

        // Version unused for now. May be needed in the future for handling migrations.
        bundleToWrite.putInt(mVersionKey, mDatastoreVersion);
        bundleToWrite.writeToStream(outputStream);

        FileOutputStream out = null;
        try {
            out = mAtomicFile.startWrite();
            out.write(outputStream.toByteArray());
            mAtomicFile.finishWrite(out);
        } catch (IOException e) {
            if (out != null) {
                mAtomicFile.failWrite(out);
            }
            LogUtil.e(e, "Write to file failed");
            throw e;
        }
    }

    // Note that this completely replaces the loaded datastore with the file's data, instead of
    // appending new file data.
    @GuardedBy("mReadLock")
    private void readFromFile() throws IOException {
        try {
            final ByteArrayInputStream inputStream =
                    new ByteArrayInputStream(mAtomicFile.readFully());
            final PersistableBundle bundleRead = PersistableBundle.readFromStream(inputStream);

            mPreviousStoredVersion = bundleRead.getInt(mVersionKey, NO_PREVIOUS_VERSION);
            bundleRead.remove(mVersionKey);
            mLocalMap.clear();
            for (String key : bundleRead.keySet()) {
                mLocalMap.put(key, bundleRead.get(key));
            }
        } catch (FileNotFoundException e) {
            if (VERBOSE) {
                LogUtil.v("File not found; continuing with clear database");
            }
            mPreviousStoredVersion = NO_PREVIOUS_VERSION;
            mLocalMap.clear();
        } catch (IOException e) {
            LogUtil.e(e, "Read from store file failed");
            throw e;
        }
    }

    /**
     * Stores a boolean value to the datastore file.
     *
     * <p>This change is committed immediately to file.
     *
     * @param key A non-null, non-empty String key to store the {@code value} against
     * @param value A boolean to be stored
     * @throws IllegalArgumentException if {@code key} is an empty string
     * @throws IOException if file write fails
     * @throws NullPointerException if {@code key} is null
     */
    public final void putBoolean(String key, boolean value) throws IOException {
        put(key, value);
    }

    /**
     * Stores an integer value to the datastore file.
     *
     * <p>This change is committed immediately to file.
     *
     * @param key A non-null, non-empty String key to store the {@code value} against
     * @param value An integer to be stored
     * @throws IllegalArgumentException if {@code key} is an empty string
     * @throws IOException if file write fails
     * @throws NullPointerException if {@code key} is null
     */
    public final void putInt(String key, int value) throws IOException {
        put(key, value);
    }

    /**
     * Stores a string value to the datastore file.
     *
     * <p>This change is committed immediately to file.
     *
     * @param key A non-null, non-empty String key to store the {@code value} against
     * @param value A string to be stored
     * @throws IllegalArgumentException if {@code key} is an empty string
     * @throws IOException if file write fails
     * @throws NullPointerException if {@code key} is null
     */
    public final void putString(String key, String value) throws IOException {
        put(key, value);
    }

    private void put(String key, Object value) throws IOException {
        Objects.requireNonNull(key);
        checkStringNotEmpty(key, "Key must not be empty");

        mWriteLock.lock();
        try {
            mLocalMap.put(key, value);
            writeToFile();
        } finally {
            mWriteLock.unlock();
        }
    }

    /**
     * Stores a boolean value to the datastore file, but only if the key does not already exist.
     *
     * <p>If a change is made to the datastore, it is committed immediately to file.
     *
     * @param key A non-null, non-empty String key to store the {@code value} against
     * @param value A boolean to be stored
     * @return the value that exists in the datastore after the operation completes
     * @throws IllegalArgumentException if {@code key} is an empty string
     * @throws IOException if file write fails
     * @throws NullPointerException if {@code key} is null
     */
    public final boolean putBooleanIfNew(String key, boolean value) throws IOException {
        return putIfNew(key, value, Boolean.class);
    }

    /**
     * Stores an integer value to the datastore file, but only if the key does not already exist.
     *
     * <p>If a change is made to the datastore, it is committed immediately to file.
     *
     * @param key A non-null, non-empty String key to store the {@code value} against
     * @param value An integer to be stored
     * @return the value that exists in the datastore after the operation completes
     * @throws IllegalArgumentException if {@code key} is an empty string
     * @throws IOException if file write fails
     * @throws NullPointerException if {@code key} is null
     */
    public final int putIntIfNew(String key, int value) throws IOException {
        return putIfNew(key, value, Integer.class);
    }

    /**
     * Stores a String value to the datastore file, but only if the key does not already exist.
     *
     * <p>If a change is made to the datastore, it is committed immediately to file.
     *
     * @param key A non-null, non-empty String key to store the {@code value} against
     * @param value A String to be stored
     * @return the value that exists in the datastore after the operation completes
     * @throws IllegalArgumentException if {@code key} is an empty string
     * @throws IOException if file write fails
     * @throws NullPointerException if {@code key} is null
     */
    public final String putStringIfNew(String key, String value) throws IOException {
        return putIfNew(key, value, String.class);
    }

    private <T> T putIfNew(String key, T value, Class<T> valueType) throws IOException {
        Objects.requireNonNull(key);
        checkStringNotEmpty(key, "Key must not be empty");

        // Try not to block readers first before trying to write
        mReadLock.lock();
        try {
            Object valueInLocalMap = mLocalMap.get(key);
            if (valueInLocalMap != null) {
                return checkValueType(valueInLocalMap, valueType);
            }

        } finally {
            mReadLock.unlock();
        }

        // Double check that the key wasn't written after the first check
        mWriteLock.lock();
        try {
            Object valueInLocalMap = mLocalMap.get(key);
            if (valueInLocalMap != null) {
                return checkValueType(valueInLocalMap, valueType);
            } else {
                mLocalMap.put(key, value);
                writeToFile();
                return value;
            }
        } finally {
            mWriteLock.unlock();
        }
    }

    /**
     * Retrieves a boolean value from the loaded datastore file.
     *
     * @param key A non-null, non-empty String key to fetch a value from
     * @return The value stored against a {@code key}, or null if it doesn't exist
     * @throws IllegalArgumentException if {@code key} is an empty string
     * @throws NullPointerException if {@code key} is null
     */
    @Nullable
    public final Boolean getBoolean(String key) {
        return get(key, Boolean.class);
    }

    /**
     * Retrieves an integer value from the loaded datastore file.
     *
     * @param key A non-null, non-empty String key to fetch a value from
     * @return The value stored against a {@code key}, or null if it doesn't exist
     * @throws IllegalArgumentException if {@code key} is an empty string
     * @throws NullPointerException if {@code key} is null
     */
    @Nullable
    public final Integer getInt(String key) {
        return get(key, Integer.class);
    }

    /**
     * Retrieves a String value from the loaded datastore file.
     *
     * @param key A non-null, non-empty String key to fetch a value from
     * @return The value stored against a {@code key}, or null if it doesn't exist
     * @throws IllegalArgumentException if {@code key} is an empty string
     * @throws NullPointerException if {@code key} is null
     */
    @Nullable
    public final String getString(String key) {
        return get(key, String.class);
    }

    @Nullable
    private <T> T get(String key, Class<T> valueType) {
        Objects.requireNonNull(key);
        checkStringNotEmpty(key, "Key must not be empty");

        mReadLock.lock();
        try {
            if (mLocalMap.containsKey(key)) {
                Object valueInLocalMap = mLocalMap.get(key);
                return checkValueType(valueInLocalMap, valueType);
            }
            return null;
        } finally {
            mReadLock.unlock();
        }
    }

    /**
     * Retrieves a boolean value from the loaded datastore file.
     *
     * @param key A non-null, non-empty String key to fetch a value from
     * @param defaultValue Value to return if this key does not exist.
     * @return The value stored against a {@code key}, or {@code defaultValue} if it doesn't exist
     * @throws IllegalArgumentException if {@code key} is an empty string
     * @throws NullPointerException if {@code key} is null
     */
    public final boolean getBoolean(String key, boolean defaultValue) {
        return get(key, defaultValue, Boolean.class);
    }

    /**
     * Retrieves an integer value from the loaded datastore file.
     *
     * @param key A non-null, non-empty String key to fetch a value from
     * @param defaultValue Value to return if this key does not exist.
     * @return The value stored against a {@code key}, or {@code defaultValue} if it doesn't exist
     * @throws IllegalArgumentException if {@code key} is an empty string
     * @throws NullPointerException if {@code key} is null
     */
    public final int getInt(String key, int defaultValue) {
        return get(key, defaultValue, Integer.class);
    }

    /**
     * Retrieves a String value from the loaded datastore file.
     *
     * @param key A non-null, non-empty String key to fetch a value from
     * @param defaultValue Value to return if this key does not exist.
     * @return The value stored against a {@code key}, or {@code defaultValue} if it doesn't exist
     * @throws IllegalArgumentException if {@code key} is an empty string
     * @throws NullPointerException if {@code key} is null
     */
    public final String getString(String key, String defaultValue) {
        return get(key, defaultValue, String.class);
    }

    private <T> T get(String key, T defaultValue, Class<T> valueType) {
        Objects.requireNonNull(key);
        checkStringNotEmpty(key, "Key must not be empty");
        Objects.requireNonNull(defaultValue, "Default value must not be null");

        mReadLock.lock();
        try {
            if (mLocalMap.containsKey(key)) {
                Object valueInLocalMap = mLocalMap.get(key);
                return checkValueType(valueInLocalMap, valueType);
            }
            return defaultValue;
        } finally {
            mReadLock.unlock();
        }
    }

    /**
     * Retrieves a {@link Set} of all keys loaded from the datastore file.
     *
     * @return A {@link Set} of {@link String} keys currently in the loaded datastore
     */
    public final Set<String> keySet() {
        mReadLock.lock();
        try {
            return getSafeSetCopy(mLocalMap.keySet());
        } finally {
            mReadLock.unlock();
        }
    }

    private Set<String> keySetFilter(Object filter) {
        mReadLock.lock();
        try {
            return getSafeSetCopy(
                    mLocalMap.entrySet().stream()
                            .filter(entry -> entry.getValue().equals(filter))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toSet()));
        } finally {
            mReadLock.unlock();
        }
    }

    /**
     * Retrieves a Set of all keys with value {@code true} loaded from the datastore file.
     *
     * @return A Set of String keys currently in the loaded datastore that have value {@code true}
     */
    public final Set<String> keySetTrue() {
        return keySetFilter(true);
    }

    /**
     * Retrieves a Set of all keys with value {@code false} loaded from the datastore file.
     *
     * @return A Set of String keys currently in the loaded datastore that have value {@code false}
     */
    public final Set<String> keySetFalse() {
        return keySetFilter(false);
    }

    /**
     * Clears all entries from the datastore file.
     *
     * <p>This change is committed immediately to file.
     *
     * @throws IOException if file write fails
     */
    public final void clear() throws IOException {
        if (DEBUG) {
            LogUtil.d("Clearing all entries from datastore");
        }

        mWriteLock.lock();
        try {
            mLocalMap.clear();
            writeToFile();
        } finally {
            mWriteLock.unlock();
        }
    }

    private void clearByFilter(Object filter) throws IOException {
        mWriteLock.lock();
        try {
            mLocalMap.entrySet().removeIf(entry -> entry.getValue().equals(filter));
            writeToFile();
        } finally {
            mWriteLock.unlock();
        }
    }

    /**
     * Clears all entries from the datastore file that have value {@code true}. Entries with value
     * {@code false} are not removed.
     *
     * <p>This change is committed immediately to file.
     *
     * @throws IOException if file write fails
     */
    public void clearAllTrue() throws IOException {
        clearByFilter(true);
    }

    /**
     * Clears all entries from the datastore file that have value {@code false}. Entries with value
     * {@code true} are not removed.
     *
     * <p>This change is committed immediately to file.
     *
     * @throws IOException if file write fails
     */
    public void clearAllFalse() throws IOException {
        clearByFilter(false);
    }

    /**
     * Removes an entry from the datastore file.
     *
     * <p>This change is committed immediately to file.
     *
     * @param key A non-null, non-empty String key to remove
     * @throws IllegalArgumentException if {@code key} is an empty string
     * @throws IOException if file write fails
     * @throws NullPointerException if {@code key} is null
     */
    public void remove(String key) throws IOException {
        checkValidKey(key);

        mWriteLock.lock();
        try {
            mLocalMap.remove(key);
            writeToFile();
        } finally {
            mWriteLock.unlock();
        }
    }

    /**
     * Removes all entries that begin with the specified prefix from the datastore file.
     *
     * <p>This change is committed immediately to file.
     *
     * @param prefix A non-null, non-empty string that all keys are matched against
     * @throws NullPointerException if {@code prefix} is null
     * @throws IllegalArgumentException if {@code prefix} is an empty string
     * @throws IOException if file write fails
     */
    public void removeByPrefix(String prefix) throws IOException {
        Objects.requireNonNull(prefix);
        checkStringNotEmpty(prefix, "Prefix must not be empty");

        mWriteLock.lock();
        try {
            Set<String> allKeys = mLocalMap.keySet();
            Set<String> keysToDelete =
                    allKeys.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toSet());
            allKeys.removeAll(keysToDelete); // Modifying the keySet updates the underlying map
            writeToFile();
        } finally {
            mWriteLock.unlock();
        }
    }

    /** Dumps its internal state. */
    public void dump(PrintWriter writer, String prefix) {
        writer.printf("%smDatastoreVersion: %d\n", prefix, mDatastoreVersion);
        writer.printf("%smPreviousStoredVersion: %d\n", prefix, mPreviousStoredVersion);
        writer.printf("%smVersionKey: %s\n", prefix, mVersionKey);
        writer.printf("%smAtomicFile: %s", prefix, mAtomicFile.getBaseFile().getAbsolutePath());
        if (SdkLevel.isAtLeastS()) {
            writer.printf(" (last modified at %d)", mAtomicFile.getLastModifiedTime());
        }
        int size = mLocalMap.size();
        writer.printf(":\n%s%d entries\n", prefix, size);

        // TODO(b/299942046): decide whether it's ok to dump the entries themselves (perhaps passing
        // an argument).
    }

    /** Returns the version that was written prior to the device starting. */
    public final int getPreviousStoredVersion() {
        return mPreviousStoredVersion;
    }

    /** Gets the version key. */
    public final String getVersionKey() {
        return mVersionKey;
    }

    /** For tests only */
    @VisibleForTesting
    public void tearDownForTesting() {
        mWriteLock.lock();
        try {
            mAtomicFile.delete();
            mLocalMap.clear();
        } finally {
            mWriteLock.unlock();
        }
    }

    /**
     * Helper method to support various data types. Equivalent to calling {@link
     * android.os.BaseBundle#putObject(String, Object)}, which is hidden.
     */
    private void addToBundle(PersistableBundle bundle, String key, Object value) {
        Objects.requireNonNull(value, String.format("Failed to insert null value for key %s", key));
        if (value instanceof Boolean) {
            bundle.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            bundle.putInt(key, (Integer) value);
        } else if (value instanceof String) {
            bundle.putString(key, (String) value);
        } else {
            throw new IllegalArgumentException(
                    "Failed to insert unsupported type: "
                            + value.getClass()
                            + " value for key: "
                            + key);
        }
    }

    private static File newFile(String parentPath, String filename) {
        checkStringNotEmpty(parentPath, "parentPath must not be empty or null");
        checkStringNotEmpty(filename, "filename must not be empty or null");
        File parent = new File(parentPath);
        if (!parent.exists()) {
            throw new IllegalArgumentException(
                    "parentPath doesn't exist: " + parent.getAbsolutePath());
        }
        if (!parent.isDirectory()) {
            throw new IllegalArgumentException(
                    "parentPath is not a directory: " + parent.getAbsolutePath());
        }
        return new File(parent, filename);
    }

    // TODO(b/335869310): change it to using ImmutableSet.
    private static <T> Set<T> getSafeSetCopy(Set<T> sourceSet) {
        return new HashSet<>(sourceSet);
    }

    private void checkValidKey(String key) {
        Objects.requireNonNull(key, "Key must not be null");
        checkStringNotEmpty(key, "Key must not be empty");
    }

    private <T> T checkValueType(Object valueInLocalMap, Class<T> expectedType) {
        checkState(
                expectedType.isInstance(valueInLocalMap),
                "Value returned is not of %s type",
                expectedType.getSimpleName());
        return expectedType.cast(valueInLocalMap);
    }
}
