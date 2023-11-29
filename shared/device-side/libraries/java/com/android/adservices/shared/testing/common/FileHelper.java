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

import static com.android.adservices.shared.testing.common.ShellHelper.invokeWithShellPermissions;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Provides helpers for file-related operations. */
public final class FileHelper {

    private static final String TAG = FileHelper.class.getSimpleName();

    private static final String ADSERVICES_TEST_DIR = "adservices-tests";
    private static final String SD_CARD_DIR = "/sdcard";

    // TODO(b/313646338): add unit tests
    /** Writes a text file to {@link #getAdServicesTestsOutputDir()}. */
    public static void writeFile(String filename, String contents) {
        String userFriendlyFilename = filename;
        try {
            File dir = getAdServicesTestsOutputDir();
            Path filePath = Paths.get(dir.getAbsolutePath(), filename);
            userFriendlyFilename = filePath.toString();
            logI("Creating file %s", userFriendlyFilename);
            Files.createFile(filePath);
            byte[] bytes = contents.getBytes();
            logD("Writing %d bytes to %s", bytes.length, filePath);
            Files.write(filePath, bytes);
            logD("Saul Goodman!");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save " + userFriendlyFilename, e);
        }
    }

    // TODO(b/313646338): add unit tests
    /**
     * Writes a file to {@value #SD_CARD_DIR} under {@value #ADSERVICES_TEST_DIR} (or a standard
     * directory if it fails).
     *
     * <p>In order to use this method, your test project should:
     *
     * <ol>
     *   <li>Add {@code android:requestLegacyExternalStorage='true'>} to the {@code application} tag
     *       in the Android manifest.
     *   <li>Add the {@code android.permission.MANAGE_EXTERNAL_STORAGE} permission in the Android
     *       manifest.
     *   <li>Optionally, add a {@code com.android.tradefed.device.metric.FilePullerLogCollector} in
     *       the test manifest, pointing to {@code /sdcard/adservices-tests}, so tests written to
     *       this directory surface as artifacts when the test fails in the cloud.
     * </ol>
     *
     * <p><b>NOTE: </b>If the Android manifest doesn't have the first 2 steps above, this method
     * might invoke {@code Shell} permissions to create the output directory, which in turn could
     * interfere with other tests.
     */
    public static File getAdServicesTestsOutputDir() {
        String path = SD_CARD_DIR + "/" + ADSERVICES_TEST_DIR;
        File dir = new File(path);
        if (dir.exists()) {
            return dir;
        }
        boolean created = false;
        if (Environment.isExternalStorageManager()) {
            logD(
                    "Directory %s doesn't exist, trying to create it with test app's permission",
                    path);
            created = dir.mkdirs();
        } else {
            logD(
                    "Directory %s doesn't exist and cannot be created with app's permission (most"
                        + " likely test app is missing the"
                        + " android.permission.MANAGE_EXTERNAL_STORAGE permission and the"
                        + " android:requestLegacyExternalStorage='true' option in the Manifest's"
                        + " application tag); trying to create it with Shell permission",
                    path);
            created = invokeWithShellPermissions(() -> dir.mkdirs());
        }
        if (created) {
            return dir;
        }
        String alternativePath = SD_CARD_DIR + "/" + Environment.DIRECTORY_DOCUMENTS;
        logD(
                "Failed to create directory %s; returning a standard directory (%s) instead",
                path, alternativePath);
        File alternativeDir = new File(alternativePath);
        if (!alternativeDir.exists()) {
            throw new IllegalStateException(
                    "Alternative directory doesn't exist: " + alternativePath);
        }
        return alternativeDir;
    }

    private static void logI(String format, Object... args) {
        Log.i(TAG, String.format(format, args));
    }

    private static void logD(String format, Object... args) {
        Log.d(TAG, String.format(format, args));
    }

    private FileHelper() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
