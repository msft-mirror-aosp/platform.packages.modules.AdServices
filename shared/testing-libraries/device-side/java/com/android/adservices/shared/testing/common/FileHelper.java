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
package com.android.adservices.shared.testing.common;

import android.os.Environment;

import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

// TODO(b/381111873): add unit tests
/** Provides helpers for file-related operations. */
public final class FileHelper {

    private static final Logger sLog = new Logger(AndroidLogger.getInstance(), FileHelper.class);

    private static final String SD_CARD_DIR = "/sdcard";
    private static final String ADSERVICES_TEST_DIR =
            Environment.DIRECTORY_DOCUMENTS + "/adservices-tests";

    /** Writes a text file to {@link #getAdServicesTestsOutputDir()}. */
    public static void writeFile(String filename, String contents) {
        String userFriendlyFilename = filename;
        try {
            File dir = getAdServicesTestsOutputDir();
            Path filePath = Paths.get(dir.getAbsolutePath(), filename);
            userFriendlyFilename = filePath.toString();
            sLog.i("Creating file %s", userFriendlyFilename);
            Files.createFile(filePath);
            byte[] bytes = contents.getBytes();
            sLog.v("Writing %s bytes to %s", bytes.length, filePath);
            Files.write(filePath, bytes);
            sLog.d("Saul Goodman!");
        } catch (Exception e) {
            sLog.e(e, "Failed to save %s", userFriendlyFilename);
        }
    }

    /**
     * Writes a file to {@value #SD_CARD_DIR} under {@value #ADSERVICES_TEST_DIR}
     *
     * <p>NOTE: add a {@code com.android.tradefed.device.metric.FilePullerLogCollector} in the test
     * manifest, pointing to {@code /sdcard/Documents/adservices-tests}, so tests written to this
     * directory surface as artifacts when the test fails in the cloud.
     */
    public static File getAdServicesTestsOutputDir() {
        String path = SD_CARD_DIR + "/" + ADSERVICES_TEST_DIR;
        File dir = new File(path);
        if (dir.exists()) {
            return dir;
        }
        sLog.d("Directory %s doesn't exist, creating it", path);
        if (dir.mkdirs()) {
            sLog.i("Created directory %s", path);
            return dir;
        }
        throw new IllegalStateException("Could not create directory " + path);
    }

    /**
     * Deletes a file.
     *
     * <p>This is the same as {@code File.delete()}, but logging and throwing {@link IOException} if
     * the file could not be removed (for example, if it's a non-empty directory).
     *
     * @return reference to the file
     */
    public static File deleteFile(File file) throws IOException {
        sLog.i("deleteFile(%s)", file);
        Objects.requireNonNull(file, "file cannot be null");
        String path = file.getAbsolutePath();
        if (!file.exists()) {
            sLog.d("deleteFile(%s): file doesn't exist", path);
            return file;
        }
        if (file.delete()) {
            sLog.i("%s deleted", path);
            return file;
        }
        throw new IOException("File " + file + " was not deleted");
    }

    /**
     * Deletes a file.
     *
     * <p>This is the same as {@code File.delete()}, but logging and throwing {@link IOException} if
     * the file could not be removed (for example, if it's a non-empty directory).
     *
     * @return reference to the file
     */
    public static File deleteFile(String baseDir, String filename) throws IOException {
        sLog.i("createEmptyFile(%s, %s)", baseDir, filename);
        Objects.requireNonNull(baseDir, "baseDir cannot be null");
        Objects.requireNonNull(filename, "filename cannot be null");

        return deleteFile(new File(baseDir, filename));
    }

    /** Recursively removes the contents of a directory. */
    public static void deleteDirectory(File dir) throws IOException {
        sLog.i("deleteDirectory(%s))", dir);
        Objects.requireNonNull(dir, "dir cannot be null");

        deleteContents(dir);
    }

    // Copied from android.os.FileUtil.deleteContents()
    private static void deleteContents(File dir) throws IOException {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    sLog.v("calling deleteContents() on %s", file);
                    deleteContents(file);
                }
                sLog.v("calling File.delete() on %s)", file);
                if (!file.delete()) {
                    throw new IOException("Failed to delete " + file);
                }
            }
        }
    }

    private FileHelper() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
