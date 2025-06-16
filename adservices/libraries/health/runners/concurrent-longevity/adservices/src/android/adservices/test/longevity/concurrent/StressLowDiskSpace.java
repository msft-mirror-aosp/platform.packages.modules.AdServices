/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.adservices.test.longevity.concurrent;

import android.content.Context;
import android.os.StatFs;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

/** Stress test used to utilize disk space while running in parallel with CUJs. */
@Scenario
@RunWith(JUnit4.class)
public final class StressLowDiskSpace extends StressScenarioTestAction {
    private static final int DEFAULT_ALLOCATE_PERCENTAGE = 50;
    private static final String TAG = "StressLowDiskSpace";

    private File mTempFile;
    private Context mContext;
    private long mInitialFreeSpace;
    private long mSpaceToFill;

    private static final int BUFFER_SIZE = 8 * 1024 * 1024;

    @Test
    public void utilizeDiskSpace() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();

        File cacheDir = mContext.getCacheDir();
        if (cacheDir == null) {
            Log.e(TAG, "Cache directory is null. Cannot proceed.");
            throw new IOException("Cache directory is null.");
        }
        Log.d(TAG, "Cache directory: " + cacheDir.getAbsolutePath());

        StatFs statFs = new StatFs(cacheDir.getAbsolutePath());
        long availableBlocks = statFs.getAvailableBlocksLong();
        long blockSize = statFs.getBlockSizeLong();
        mInitialFreeSpace = availableBlocks * blockSize;
        Log.i(TAG, "Initial free space: " + formatSize(mInitialFreeSpace));

        if (mInitialFreeSpace == 0) {
            Log.e(TAG, "No free space available.");
            throw new IOException("No free space available.");
        } else {
            if (mAllocateDiskAmountOption.get() != -1) {
                mSpaceToFill = mAllocateDiskAmountOption.get();
            } else if (mAllocateDiskPercentageOption.get() != -1) {
                mSpaceToFill = mInitialFreeSpace / 100 * mAllocateDiskPercentageOption.get();
            } else {
                mSpaceToFill = mInitialFreeSpace / 100 * DEFAULT_ALLOCATE_PERCENTAGE;
            }
        }

        Log.i(TAG, "Target space to fill: " + formatSize(mSpaceToFill));

        if (mSpaceToFill <= 0) {
            Log.e(TAG, "No space to fill or initial free space was zero.");
            throw new IOException("No space to fill or initial free space was zero.");
        }

        String fileName = "stress_test_temp_file_" + UUID.randomUUID().toString() + ".tmp";
        mTempFile = new File(cacheDir, fileName);
        Log.d(TAG, "Temporary file path: " + mTempFile.getAbsolutePath());

        Log.i(
                TAG,
                "Attempting to fill " + formatSize(mSpaceToFill) + " into " + mTempFile.getName());
        long bytesWritten = 0;
        try (OutputStream os = new FileOutputStream(mTempFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (bytesWritten < mSpaceToFill) {
                long remainingBytes = mSpaceToFill - bytesWritten;
                int bytesToWrite = (int) Math.min(BUFFER_SIZE, remainingBytes);

                os.write(buffer, 0, bytesToWrite);
                bytesWritten += bytesToWrite;
            }
            os.flush();
            Log.i(
                    TAG,
                    "Successfully written "
                            + formatSize(bytesWritten)
                            + " to "
                            + mTempFile.getName());
        } catch (IOException e) {
            Log.e(TAG, "Error writing to temporary file: " + e.getMessage(), e);

            if (mTempFile.exists()) {
                mTempFile.delete();
            }
            throw e;
        }

        long freeSpaceAfterWrite = new StatFs(cacheDir.getAbsolutePath()).getAvailableBytes();
        Log.i(TAG, "Free space after write: " + formatSize(freeSpaceAfterWrite));
        Log.i(TAG, "Space actually consumed by file: " + formatSize(mTempFile.length()));
        Log.i(TAG, "Expected space reduction: " + formatSize(mSpaceToFill));
        Log.i(
                TAG,
                "Actual space reduction: " + formatSize(mInitialFreeSpace - freeSpaceAfterWrite));
    }

    @Test
    public void releaseUtilizedDiskSpace() throws Exception {
        if (mTempFile != null && mTempFile.exists()) {
            Log.i(TAG, "Attempting to delete temporary file: " + mTempFile.getAbsolutePath());
            long fileSize = mTempFile.length();
            boolean deleted = mTempFile.delete();
            if (deleted) {
                Log.i(
                        TAG,
                        "Successfully deleted temporary file. Freed approx: "
                                + formatSize(fileSize));
            } else {
                Log.e(TAG, "Failed to delete temporary file: " + mTempFile.getAbsolutePath());
            }
            mTempFile = null;
        } else if (mSpaceToFill > 0) {
            Log.w(TAG, "Temporary file was null or did not exist at teardown.");
        } else {
            Log.i(TAG, "No temporary file to delete as no space was targeted to be filled.");
        }

        if (mContext != null) {
            File cacheDir = mContext.getCacheDir();
            if (cacheDir != null) {
                long freeSpaceAfterCleanup =
                        new StatFs(cacheDir.getAbsolutePath()).getAvailableBytes();
                Log.i(TAG, "Free space after cleanup: " + formatSize(freeSpaceAfterCleanup));
            }
        }
    }

    private static String formatSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[] {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1; // Cap at TB
        return String.format("%.2f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
