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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ATOMIC_FILE_DATASTORE_READ_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ATOMIC_FILE_DATASTORE_WRITE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.adservices.shared.util.LogUtil.DEBUG;
import static com.android.adservices.shared.util.LogUtil.VERBOSE;
import static com.android.adservices.shared.util.Preconditions.checkState;
import static com.android.internal.util.Preconditions.checkArgumentNonnegative;

import android.annotation.Nullable;
import android.os.PersistableBundle;
import android.util.AtomicFile;

import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
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
public final class AtomicFileDatastore {
    public static final int NO_PREVIOUS_VERSION = -1;

    /**
     * Argument to {@link #dump(PrintWriter, String, String[])} so it includes the database content.
     */
    public static final String DUMP_ARG_INCLUDE_CONTENTS = "--include_contents";

    /** Convenience reference to dump args that only contains {@link #DUMP_ARG_INCLUDE_CONTENTS}. */
    public static final String[] DUMP_ARGS_INCLUDE_CONTENTS_ONLY =
            new String[] {DUMP_ARG_INCLUDE_CONTENTS};

    private final int mDatastoreVersion;
    private final AdServicesErrorLogger mAdServicesErrorLogger;

    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();
    private final Lock mReadLock = mReadWriteLock.readLock();
    private final Lock mWriteLock = mReadWriteLock.writeLock();

    private final AtomicFile mAtomicFile;
    private final Map<String, Object> mLocalMap = new HashMap<>();

    private final String mVersionKey;
    private int mPreviousStoredVersion;

    public AtomicFileDatastore(
            File file,
            int datastoreVersion,
            String versionKey,
            AdServicesErrorLogger adServicesErrorLogger) {
        this(new AtomicFile(validFile(file)), datastoreVersion, versionKey, adServicesErrorLogger);
    }

    @VisibleForTesting // AtomicFileDatastoreTest must spy on AtomicFile
    AtomicFileDatastore(
            AtomicFile atomicFile,
            int datastoreVersion,
            String versionKey,
            AdServicesErrorLogger adServicesErrorLogger) {
        mAtomicFile = Objects.requireNonNull(atomicFile, "atomicFile cannot be null");
        mDatastoreVersion =
                checkArgumentNonnegative(datastoreVersion, "datastoreVersion must not be negative");

        mVersionKey = checkValid("versionKey", versionKey);
        mAdServicesErrorLogger =
                Objects.requireNonNull(
                        adServicesErrorLogger, "adServicesErrorLogger cannot be null");
    }

    /**
     * Loads data from the datastore file.
     *
     * @throws IOException if file read fails
     */
    public void initialize() throws IOException {
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

    // Writes the {@code localMap} to a PersistableBundle which is then written to file.
    @GuardedBy("mWriteLock")
    private void writeToFile(Map<String, Object> localMap) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PersistableBundle bundleToWrite = new PersistableBundle();

        for (Map.Entry<String, Object> entry : localMap.entrySet()) {
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
            LogUtil.v("Write to file %s failed", mAtomicFile.getBaseFile());
            mAdServicesErrorLogger.logError(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ATOMIC_FILE_DATASTORE_WRITE_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
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
            LogUtil.v("Read from store file %s failed", mAtomicFile.getBaseFile());
            mAdServicesErrorLogger.logError(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ATOMIC_FILE_DATASTORE_READ_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
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
     */
    public void putBoolean(String key, boolean value) throws IOException {
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
     */
    public void putInt(String key, int value) throws IOException {
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
     */
    public void putString(String key, @Nullable String value) throws IOException {
        put(key, value);
    }

    private void put(String key, @Nullable Object value) throws IOException {
        checkValidKey(key);

        mWriteLock.lock();
        Object oldValue = mLocalMap.get(key);
        try {
            mLocalMap.put(key, value);
            writeToFile(mLocalMap);
        } catch (IOException ex) {
            LogUtil.v(
                    "put(): failed to write to file %s, reverting value of %s on local map.",
                    mAtomicFile.getBaseFile(), key);
            if (oldValue == null) {
                mLocalMap.remove(key);
            } else {
                mLocalMap.put(key, oldValue);
            }
            throw ex;
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
     */
    public boolean putBooleanIfNew(String key, boolean value) throws IOException {
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
     */
    public int putIntIfNew(String key, int value) throws IOException {
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
     */
    public String putStringIfNew(String key, String value) throws IOException {
        return putIfNew(key, value, String.class);
    }

    private <T> T putIfNew(String key, T value, Class<T> valueType) throws IOException {
        checkValidKey(key);
        Objects.requireNonNull(valueType, "valueType cannot be null");

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
        Object valueInLocalMap = mLocalMap.get(key);
        try {
            if (valueInLocalMap != null) {
                return checkValueType(valueInLocalMap, valueType);
            } else {
                mLocalMap.put(key, value);
                writeToFile(mLocalMap);
                return value;
            }
        } catch (IOException ex) {
            LogUtil.v(
                    "putIfNew(): failed to write to file %s, removing key %s on local map.",
                    mAtomicFile.getBaseFile(), key);
            mLocalMap.remove(key);
            throw ex;
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
     */
    @Nullable
    public Boolean getBoolean(String key) {
        return get(key, Boolean.class);
    }

    /**
     * Retrieves an integer value from the loaded datastore file.
     *
     * @param key A non-null, non-empty String key to fetch a value from
     * @return The value stored against a {@code key}, or null if it doesn't exist
     * @throws IllegalArgumentException if {@code key} is an empty string
     */
    @Nullable
    public Integer getInt(String key) {
        return get(key, Integer.class);
    }

    /**
     * Retrieves a String value from the loaded datastore file.
     *
     * @param key A non-null, non-empty String key to fetch a value from
     * @return The value stored against a {@code key}, or null if it doesn't exist
     * @throws IllegalArgumentException if {@code key} is an empty string
     */
    @Nullable
    public String getString(String key) {
        return get(key, String.class);
    }

    @Nullable
    private <T> T get(String key, Class<T> valueType) {
        checkValidKey(key);

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
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return get(key, defaultValue, Boolean.class);
    }

    /**
     * Retrieves an integer value from the loaded datastore file.
     *
     * @param key A non-null, non-empty String key to fetch a value from
     * @param defaultValue Value to return if this key does not exist.
     * @return The value stored against a {@code key}, or {@code defaultValue} if it doesn't exist
     * @throws IllegalArgumentException if {@code key} is an empty string
     */
    public int getInt(String key, int defaultValue) {
        return get(key, defaultValue, Integer.class);
    }

    /**
     * Retrieves a String value from the loaded datastore file.
     *
     * @param key A non-null, non-empty String key to fetch a value from
     * @param defaultValue Value to return if this key does not exist.
     * @return The value stored against a {@code key}, or {@code defaultValue} if it doesn't exist
     * @throws IllegalArgumentException if {@code key} is an empty string
     */
    public String getString(String key, String defaultValue) {
        return get(key, defaultValue, String.class);
    }

    private <T> T get(String key, T defaultValue, Class<T> valueType) {
        checkValidKey(key);
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
    public Set<String> keySet() {
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
    public Set<String> keySetTrue() {
        return keySetFilter(true);
    }

    /**
     * Retrieves a Set of all keys with value {@code false} loaded from the datastore file.
     *
     * @return A Set of String keys currently in the loaded datastore that have value {@code false}
     */
    public Set<String> keySetFalse() {
        return keySetFilter(false);
    }

    /**
     * Clears all entries from the datastore file.
     *
     * <p>This change is committed immediately to file.
     *
     * @throws IOException if file write fails
     */
    public void clear() throws IOException {
        mWriteLock.lock();
        if (DEBUG) {
            LogUtil.d(
                    "Clearing all (%d) entries from datastore (%s)",
                    mLocalMap.size(), mAtomicFile.getBaseFile());
        }
        Map<String, Object> previousLocalMap = new HashMap<>(mLocalMap);
        try {
            mLocalMap.clear();
            writeToFile(mLocalMap);
        } catch (IOException ex) {
            LogUtil.v(
                    "clear(): failed to clear the file %s, reverting local map back to previous "
                            + "state.",
                    mAtomicFile.getBaseFile());
            mLocalMap.putAll(previousLocalMap);
            throw ex;
        } finally {
            mWriteLock.unlock();
        }
    }

    private void clearByFilter(Object filter) throws IOException {
        mWriteLock.lock();
        Map<String, Object> previousLocalMap = new HashMap<>(mLocalMap);
        try {
            mLocalMap.entrySet().removeIf(entry -> entry.getValue().equals(filter));
            writeToFile(mLocalMap);
        } catch (IOException ex) {
            LogUtil.v(
                    "clearByFilter(): failed to clear keys for filter %s for file %s, reverting"
                            + " local map back to previous state.",
                    filter, mAtomicFile.getBaseFile());
            mLocalMap.putAll(previousLocalMap);
            throw ex;
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
     */
    public void remove(String key) throws IOException {
        checkValidKey(key);

        mWriteLock.lock();
        Object oldValue = mLocalMap.get(key);
        try {
            mLocalMap.remove(key);
            writeToFile(mLocalMap);
        } catch (IOException ex) {
            LogUtil.v(
                    "remove(): failed to remove key %s in file %s, adding it back",
                    key, mAtomicFile.getBaseFile());
            if (oldValue != null) {
                mLocalMap.put(key, oldValue);
            }
            throw ex;
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
     * @throws IllegalArgumentException if {@code prefix} is an empty string
     * @throws IOException if file write fails
     */
    public void removeByPrefix(String prefix) throws IOException {
        checkValid("prefix", prefix);

        mWriteLock.lock();
        Map<String, Object> previousLocalMap = new HashMap<>(mLocalMap);
        try {
            Set<String> allKeys = mLocalMap.keySet();
            Set<String> keysToDelete =
                    allKeys.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toSet());
            allKeys.removeAll(keysToDelete); // Modifying the keySet updates the underlying map
            writeToFile(mLocalMap);
        } catch (IOException ex) {
            LogUtil.v(
                    "removeByPrefix(): failed to remove key by prefix %s in file %s, adding it"
                            + " back",
                    prefix, mAtomicFile.getBaseFile());
            mLocalMap.putAll(previousLocalMap);
            throw ex;
        } finally {
            mWriteLock.unlock();
        }
    }

    /**
     * Updates the file and local map by applying the {@code transform} to the most recently
     * persisted value.
     *
     * @param transform The {@link BatchUpdateOperation} to apply to the data.
     * @throws IOException if file write fails
     */
    public void update(BatchUpdateOperation transform) throws IOException {
        mWriteLock.lock();
        try {
            BatchUpdaterImpl updater = new BatchUpdaterImpl(mLocalMap);
            transform.apply(updater);

            // Write to file if contents in map are changed
            if (updater.isChanged()) {
                writeToFile(updater.mUpdatedCachedData);
                mLocalMap.clear();
                mLocalMap.putAll(updater.mUpdatedCachedData);
            }
        } finally {
            mWriteLock.unlock();
        }
    }

    /** Dumps its internal state. */
    public void dump(PrintWriter writer, String prefix, @Nullable String[] args) {
        writer.printf("%smDatastoreVersion: %d\n", prefix, mDatastoreVersion);
        writer.printf("%smPreviousStoredVersion: %d\n", prefix, mPreviousStoredVersion);
        writer.printf("%smVersionKey: %s\n", prefix, mVersionKey);
        writer.printf("%smAtomicFile: %s", prefix, mAtomicFile.getBaseFile().getAbsolutePath());
        if (SdkLevel.isAtLeastS()) {
            writer.printf(" (last modified at %d)\n", mAtomicFile.getLastModifiedTime());
        }

        boolean dumpAll = args != null && args[0].equals(DUMP_ARG_INCLUDE_CONTENTS);
        int size = mLocalMap.size();
        writer.printf("%s%d entries", prefix, size);
        if (!dumpAll || size == 0) {
            writer.println();
            return;
        }
        writer.println(":");
        String prefix2 = prefix + prefix;
        mLocalMap.forEach((k, v) -> writer.printf("%s%s: %s\n", prefix2, k, v));
    }

    /** Returns the version that was written prior to the device starting. */
    public int getPreviousStoredVersion() {
        return mPreviousStoredVersion;
    }

    /** Gets the version key. */
    public String getVersionKey() {
        return mVersionKey;
    }

    @Override
    public String toString() {
        StringBuilder string =
                new StringBuilder("AtomicFileDatastore[path=")
                        .append(mAtomicFile.getBaseFile().getAbsolutePath())
                        .append(", version=")
                        .append(mDatastoreVersion)
                        .append(", previousVersion=")
                        .append(mPreviousStoredVersion)
                        .append(", versionKey=")
                        .append(mVersionKey)
                        .append(", entries=");
        mReadLock.lock();
        try {
            string.append(mLocalMap.size());
        } finally {
            mReadLock.unlock();
        }
        return string.append(']').toString();
    }

    /**
     * Helper method to support various data types.
     *
     * <p>Equivalent to calling {@link android.os.BaseBundle#putObject(String, Object)}, which is
     * hidden.
     */
    private void addToBundle(PersistableBundle bundle, String key, Object value) {
        Objects.requireNonNull(key, "cannot add null key");
        Objects.requireNonNull(value, "cannot add null value for key " + key);

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

    private static File validFile(File file) {
        Objects.requireNonNull(file, "file cannot be null");
        File parent = file.getParentFile();
        if (!parent.exists()) {
            throw new IllegalArgumentException(
                    "parentPath doesn't exist: " + parent.getAbsolutePath());
        }
        return file;
    }

    // TODO(b/335869310): change it to using ImmutableSet.
    private static <T> Set<T> getSafeSetCopy(Set<T> sourceSet) {
        return new HashSet<>(sourceSet);
    }

    private static void checkValidKey(String key) {
        checkValid("key", key);
    }

    private static String checkValid(String what, String value) {
        if (value == null) {
            throw new NullPointerException(what + " must not be null");
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException(what + " must not be empty");
        }
        return value;
    }

    private static <T> T checkValueType(Object valueInLocalMap, Class<T> expectedType) {
        checkState(
                expectedType.isInstance(valueInLocalMap),
                "Value returned is not of %s type",
                expectedType.getSimpleName());
        return expectedType.cast(valueInLocalMap);
    }

    /** A functional interface to perform batch operations on the datastore */
    @FunctionalInterface
    public interface BatchUpdateOperation {
        /**
         * Represents series of update operations to be applied on the datastore using the provided
         * {@link BatchUpdater}.
         */
        void apply(BatchUpdater updater);
    }

    /**
     * Interface for staging batch update operations on a datastore.
     *
     * <p>Provides methods for adding different data types (boolean, int , String) to a batch update
     * operation.
     */
    public interface BatchUpdater {
        /**
         * Adds a boolean value to be updated in the batch operation.
         *
         * @throws IllegalArgumentException if {@code key} is an empty string
         */
        void putBoolean(String key, boolean value);

        /**
         * Adds an integer value to be updated in the batch operation.
         *
         * @throws IllegalArgumentException if {@code key} is an empty string
         */
        void putInt(String key, int value);

        /**
         * Adds a String value to be updated in the batch operation.
         *
         * @throws IllegalArgumentException if {@code key} is an empty string
         */
        void putString(String key, String value);

        /**
         * Adds a boolean value only if the key does not already exist to be updated in the batch
         * operation.
         *
         * @throws IllegalArgumentException if {@code key} is an empty string
         */
        void putBooleanIfNew(String key, boolean value);

        /**
         * Adds an integer value only if the key does not already exist to be updated in the batch
         * operation.
         *
         * @throws IllegalArgumentException if {@code key} is an empty string
         */
        void putIntIfNew(String key, int value);

        /**
         * Adds a String value only if the key does not already exist to be updated in the batch
         * operation.
         *
         * @throws IllegalArgumentException if {@code key} is an empty string
         */
        void putStringIfNew(String key, String value);
    }

    private static final class BatchUpdaterImpl implements BatchUpdater {
        private final Map<String, Object> mUpdatedCachedData;
        private boolean mChanged;

        BatchUpdaterImpl(Map<String, Object> localMap) {
            mUpdatedCachedData = new HashMap<>(localMap);
        }

        @Override
        public void putBoolean(String key, boolean value) {
            putInternal(key, value);
        }

        @Override
        public void putInt(String key, int value) {
            putInternal(key, value);
        }

        @Override
        public void putString(String key, String value) {
            putInternal(key, value);
        }

        @Override
        public void putBooleanIfNew(String key, boolean value) {
            putIfNewInternal(key, value, Boolean.class);
        }

        @Override
        public void putIntIfNew(String key, int value) {
            putIfNewInternal(key, value, Integer.class);
        }

        @Override
        public void putStringIfNew(String key, String value) {
            putIfNewInternal(key, value, String.class);
        }

        boolean isChanged() {
            return mChanged;
        }

        private void putInternal(String key, Object value) {
            checkValidKey(key);
            Object oldValue = mUpdatedCachedData.get(key);
            if (!value.equals(oldValue)) {
                mUpdatedCachedData.put(key, value);
                mChanged = true;
            }
        }

        private <T> void putIfNewInternal(String key, T value, Class<T> valueType) {
            checkValidKey(key);

            Object valueInLocalMap = mUpdatedCachedData.get(key);
            if (valueInLocalMap != null) {
                checkValueType(valueInLocalMap, valueType);
                return;
            }
            mUpdatedCachedData.put(key, value);
            mChanged = true;
        }
    }
}
