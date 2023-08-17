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

package com.android.adservices.data.signals;

import android.adservices.common.AdTechIdentifier;
import android.content.Context;
import android.util.AtomicFile;

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manager that handles persistence and retrieval of encoding logic for buyers. Imposes per buyer
 * read, write & delete atomicity
 */
public class EncoderPersistenceManager {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    /**
     * TODO(ag/24355874) : remove this once ag/24355874 gets merged and utility for filename prefix
     * becomes available
     */
    @VisibleForTesting static final String ADSERVICES_PREFIX = "adservices_";

    @VisibleForTesting static final String ENCODERS_DIR = ADSERVICES_PREFIX + "encoders";
    @VisibleForTesting static final String ENCODER_FILE_SUFFIX = ".encoder";

    @NonNull private File mFilesDir;
    private static final Object SINGLETON_LOCK = new Object();
    private static volatile EncoderPersistenceManager sInstance;

    @VisibleForTesting final Map<String, ReentrantReadWriteLock> mFileLocks;

    private EncoderPersistenceManager(Context context) {
        this.mFilesDir = context.getFilesDir();
        this.mFileLocks = new HashMap<>();
    }

    /** Provides a singleton instance of {@link EncoderPersistenceManager} */
    public static EncoderPersistenceManager getInstance(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must not be null");

        EncoderPersistenceManager singleInstance = sInstance;
        if (singleInstance != null) {
            return singleInstance;
        }

        synchronized (SINGLETON_LOCK) {
            if (sInstance == null) {
                sInstance = new EncoderPersistenceManager(context);
            }
        }
        return sInstance;
    }

    /**
     * Stores encoding logic for a buyer.
     *
     * <p>Acquires lock specific to the buyer. This ensures Atomic transactions across reads, writes
     * and delete per buyer, and does not block other buyers
     *
     * @param buyer Ad tech for which encoding logic needs to be persisted
     * @param encodingLogic for encoding raw signals
     * @return file path, if successfully created and written
     */
    public boolean persistEncoder(@NonNull AdTechIdentifier buyer, @NonNull String encodingLogic) {
        File encoderDir = createEncodersDirectoryIfDoesNotExist();

        String uniqueFileNamePerBuyer = generateFileNameForBuyer(buyer);
        ReentrantReadWriteLock lock = getFileLock(uniqueFileNamePerBuyer);
        boolean isSuccessful = false;
        if (lock.writeLock().tryLock()) {
            File encoderFile = createFileInDirectory(encoderDir, uniqueFileNamePerBuyer);
            isSuccessful = writeDataToFile(encoderFile, encodingLogic);
            lock.writeLock().unlock();
        } else {
            sLogger.v("Could not capture write lock");
        }
        return isSuccessful;
    }

    /**
     * Fetches encoding logic for a buyer
     *
     * <p>Acquires lock specific to the buyer. This ensures Atomic transactions across reads, writes
     * and delete per buyer, and does not block other buyers
     *
     * @param buyer Ad tech for which encoding logic is persisted
     * @return the encoding logic as a String, if not present or in error returns an empty string
     */
    public String getEncoder(@NonNull AdTechIdentifier buyer) {
        File encoderDir = createEncodersDirectoryIfDoesNotExist();

        String uniqueFileNamePerBuyer = generateFileNameForBuyer(buyer);
        ReentrantReadWriteLock lock = getFileLock(uniqueFileNamePerBuyer);
        String data = "";
        if (lock.readLock().tryLock()) {
            data = readDataFromFile(encoderDir, uniqueFileNamePerBuyer);
            lock.readLock().unlock();
        } else {
            sLogger.v("Could not capture read lock");
        }
        return data;
    }

    /**
     * Deletes encoding logic for a buyer
     *
     * <p>Acquires lock specific to the buyer. This ensures Atomic transactions across reads, writes
     * and delete per buyer, and does not block other buyers
     *
     * @param buyer Ad tech for which encoding logic needs to be deleted
     * @return true if the encoding logic never existed or was successfully deleted
     */
    public boolean deleteEncoder(@NonNull AdTechIdentifier buyer) {
        String uniqueFileNamePerBuyer = generateFileNameForBuyer(buyer);
        ReentrantReadWriteLock lock = getFileLock(uniqueFileNamePerBuyer);
        boolean deletionComplete = false;
        if (lock.writeLock().tryLock()) {

            File file = new File(new File(mFilesDir, ENCODERS_DIR), uniqueFileNamePerBuyer);
            if (!file.exists()) {
                deletionComplete = true;
            } else {
                AtomicFile atomicFile = new AtomicFile(file);
                atomicFile.delete();
                deletionComplete = !file.exists();
            }
            lock.writeLock().unlock();
        } else {
            sLogger.v("Could not capture write lock");
        }
        return deletionComplete;
    }

    /**
     * Deletes all encoders persisted ever persisted
     *
     * <p>Acquires lock specific to the buyer. So any ongoing read or writes will prevent their
     * encoder from being deleted, while allowing rest to be removed
     *
     * @return true if the encoding logics were all deleted
     */
    public boolean deleteAllEncoders() {
        return deleteDirectory(createEncodersDirectoryIfDoesNotExist());
    }

    @VisibleForTesting
    File createEncodersDirectoryIfDoesNotExist() {
        // This itself does not create a directory or file
        File encodersDir = new File(mFilesDir, ENCODERS_DIR);
        if (!encodersDir.exists()) {

            // This creates the actual directory
            if (encodersDir.mkdirs()) {
                sLogger.v("New Encoders directory creation succeeded");
            } else {
                sLogger.e("New Encoders directory creation failed");
            }
        } else {
            sLogger.v("Encoders directory already exists at :" + encodersDir.getPath());
        }
        return encodersDir;
    }

    @VisibleForTesting
    File createFileInDirectory(File directory, String fileName) {
        // This itself does not create a directory or file
        File file = new File(directory, fileName);
        if (!file.isFile()) {
            try {
                // This creates the actual file
                if (file.createNewFile()) {
                    sLogger.v("New Encoder file creation succeeded");
                } else {
                    sLogger.e("New Encoder file creation failed");
                }
            } catch (IOException e) {
                sLogger.e("Exception trying to create the file");
            }
        } else {
            sLogger.v("Encoder file already exists at :" + file.getPath());
        }
        return file;
    }

    @VisibleForTesting
    boolean writeDataToFile(File file, String data) {
        FileOutputStream fos = null;
        AtomicFile atomicFile = new AtomicFile(file);
        try {
            fos = atomicFile.startWrite();
            fos.write(data.getBytes(StandardCharsets.UTF_8));
            atomicFile.finishWrite(fos);
            // If successful return true
            return true;
        } catch (FileNotFoundException e) {
            sLogger.e(String.format("Could not find file: %s", file.getName()));
        } catch (IOException e) {
            sLogger.e(String.format("Could not write to file: %s", file.getName()));
        } finally {
            if (fos != null) {
                atomicFile.failWrite(fos);
            }
        }
        return false;
    }

    @VisibleForTesting
    String readDataFromFile(File directory, String fileName) {
        try {
            // This does not create a new file
            File file = new File(directory, fileName);
            AtomicFile atomicFile = new AtomicFile(file);
            byte[] fileContents = atomicFile.readFully();
            return new String(fileContents, StandardCharsets.UTF_8);
        } catch (IOException e) {
            sLogger.e(String.format("Exception trying to read file: %s", fileName));
        }
        return "";
    }

    @VisibleForTesting
    boolean deleteDirectory(File directory) {
        if (directory.exists() && directory.isDirectory()) {
            File[] children = directory.listFiles();
            if (children != null) {
                for (File child : children) {
                    sLogger.v(
                            String.format(
                                    "Deleting from path: %s , file: %s",
                                    child.getPath(), child.getName()));
                    ReentrantReadWriteLock lock = getFileLock(child.getName());
                    if (lock.writeLock().tryLock()) {
                        AtomicFile atomicFile = new AtomicFile(child);
                        atomicFile.delete();
                        lock.writeLock().unlock();
                    }
                }
            }
        }
        // This only succeeds if the children files have been deleted first
        return directory.delete();
    }

    /**
     * Explicitly avoids filename format being changed across systems for a buyer, by giving control
     * to the persistence layer on how to decide a filename.
     *
     * <p>The unique name for buyer is also used for synchronizing the read, writes and deletes for
     * files to maintain atomicity and thread safety across operations, for one buyer
     *
     * @param buyer Ad tech for which the file has to be stored
     * @return the String representing filename for the buyer
     */
    @VisibleForTesting
    String generateFileNameForBuyer(@NonNull AdTechIdentifier buyer) {
        Objects.requireNonNull(buyer);
        return ADSERVICES_PREFIX + buyer + ENCODER_FILE_SUFFIX;
    }

    /**
     * For increased performance we have per file locks, this prevents blocking reads and writes for
     * other files across buyers.
     *
     * <p>We use re-entrant locks so that multiple reads are allowed in parallel, but only single
     * write
     */
    @VisibleForTesting
    ReentrantReadWriteLock getFileLock(String fileName) {
        synchronized (mFileLocks) {
            ReentrantReadWriteLock lock = mFileLocks.get(fileName);
            if (lock == null) {
                lock = new ReentrantReadWriteLock();
                mFileLocks.put(fileName, lock);
            }
            return lock;
        }
    }
}
