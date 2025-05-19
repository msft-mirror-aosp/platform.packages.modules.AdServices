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

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/** Stress test used to utilize memory while running in parallel with CUJs. */
@Scenario
@RunWith(JUnit4.class)
public final class StressMemoryUtilization extends StressScenarioTestAction {
    private static final int DEFAULT_MEMORY_STRESS_CYCLES = 7;
    private static final String TAG = "StressMemoryUtilization";
    private static final String IMAGE_NAME = "test_image_uk-lon-6ps-resized";
    private static final long BYTES_IN_MEGABYTE = 1024 * 1024;

    private final List<Bitmap> mBitmaps = new ArrayList<>();

    @Test
    public void allocateMemory() {
        String imageName = "test_image_uk-lon-6ps";

        Context context = ApplicationProvider.getApplicationContext();

        if (mAllocateAmountOption.get() != -1) {
            allocateByAmount();
        } else if (mAllocatePercentageOption.get() != -1) {
            allocateByPercentage();
        } else {
            allocateByDefaultCycles();
        }

        debugLogAllocatedBitmapMemory();
        debugLogMemoryInfo();
    }

    /** Make sure all resources are cleaned up and garbage collected. */
    @Test
    public void releaseMemory() {
        mBitmaps.clear();
        System.gc();
        System.runFinalization();
        System.gc();
        Log.d(TAG, "Memory cleaned.");
        debugLogMemoryInfo();
    }

    private void allocateByAmount() {
        long targetBytesToAllocate = (long) mAllocateAmountOption.get() * BYTES_IN_MEGABYTE;
        Log.i(
                TAG,
                "Starting memory stress test. Allocating based on amount: "
                        + mAllocateAmountOption.get()
                        + " MB ("
                        + targetBytesToAllocate
                        + " bytes)");

        while (memoryAllocatedForBitmaps() < targetBytesToAllocate) {
            allocateBitmap();
        }

        Log.i(TAG, "Allocated memory for bitmaps: " + memoryAllocatedForBitmaps());
    }

    private void allocateByPercentage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long freeHeap = maxMemory - usedMemory;
        long targetBytesToAllocate = (long) (freeHeap / 100 * mAllocatePercentageOption.get());
        Log.i(
                TAG,
                "Starting memory stress test. Allocating based on percentage: "
                        + mAllocatePercentageOption.get()
                        + "%. Free memory: "
                        + freeHeap
                        + " MB, target to allocate: "
                        + convertBytesToMegabytes(targetBytesToAllocate)
                        + "MB");

        while (memoryAllocatedForBitmaps() < targetBytesToAllocate) {
            allocateBitmap();
        }

        Log.i(TAG, "Allocated memory for bitmaps: " + memoryAllocatedForBitmaps());
    }

    private void allocateByDefaultCycles() {
        Log.d(TAG, "Starting memory stress test. Cycles: " + DEFAULT_MEMORY_STRESS_CYCLES);
        for (int i = 0; i < DEFAULT_MEMORY_STRESS_CYCLES; i++) {
            allocateBitmap();
        }
    }

    private long memoryAllocatedForBitmaps() {
        long totalMemoryAllocatedForBitmaps = 0;
        for (Bitmap bitmap : mBitmaps) {
            totalMemoryAllocatedForBitmaps += bitmap.getAllocationByteCount();
        }
        return totalMemoryAllocatedForBitmaps;
    }

    private void allocateBitmap() {
        Context context = ApplicationProvider.getApplicationContext();
        int resourceId =
                context.getResources()
                        .getIdentifier(IMAGE_NAME, "drawable", context.getPackageName());
        if (resourceId == 0) {
            Log.e(TAG, "Image not found: " + IMAGE_NAME + ". Ensure it's in drawable resources.");
            return;
        }
        try {
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId);
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap, resourceId was valid.");
                return;
            }
            Log.d(
                    TAG,
                    "Bitmap allocated, Size: "
                            + (convertBytesToMegabytes(bitmap.getAllocationByteCount()))
                            + "MB");
                mBitmaps.add(bitmap);

            Log.i(TAG, "Allocated " + mBitmaps.size() + " bitmaps");
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "OutOfMemoryError during bitmap allocation.", oom);
            debugLogMemoryInfo();
            throw oom;
        } catch (Exception e) {
            Log.e(TAG, "Exception during bitmap allocation.", e);
            debugLogMemoryInfo();
            throw e;
        }
    }

    private void debugLogAllocatedBitmapMemory() {
        long totalMemoryAllocatedForBitmaps = 0;
        for (Bitmap bmp : mBitmaps) {
            if (bmp != null && !bmp.isRecycled()) {
                totalMemoryAllocatedForBitmaps += bmp.getAllocationByteCount();
            }
        }
        Log.d(
                TAG,
                "Total memory allocated for mBitmaps: "
                        + (totalMemoryAllocatedForBitmaps / (1024 * 1024))
                        + " MB. Number of bitmaps: "
                        + mBitmaps.size());
    }

    private void debugLogMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory =
                runtime.maxMemory(); // Maximum amount of memory the JVM will attempt to use
        long allocatedMemory =
                runtime.totalMemory(); // Total memory currently available to the JVM heap
        long freeMemoryInHeap = runtime.freeMemory(); // Amount of free memory within the JVM heap
        long usedMemoryInHeap = allocatedMemory - freeMemoryInHeap;
        long actualFreeMemoryInHeap =
                maxMemory - usedMemoryInHeap; // More representative of available heap

        Log.d(TAG, "==== JVM HEAP MEMORY (MB) ====");
        Log.d(
                TAG,
                "Max Heap Memory (runtime.maxMemory()):       "
                        + (convertBytesToMegabytes(maxMemory)));
        Log.d(
                TAG,
                "Allocated Heap Memory (runtime.totalMemory()): "
                        + (convertBytesToMegabytes(allocatedMemory)));
        Log.d(
                TAG,
                "Used Heap Memory (total - free):             "
                        + (convertBytesToMegabytes(usedMemoryInHeap)));
        Log.d(
                TAG,
                "Free Memory in Allocated Heap (runtime.freeMemory()): "
                        + (convertBytesToMegabytes(freeMemoryInHeap)));
        Log.d(
                TAG,
                "Actual Free Heap (max - used):               "
                        + (convertBytesToMegabytes(actualFreeMemoryInHeap)));

        Context context = ApplicationProvider.getApplicationContext();
        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
            Log.d(TAG, "==== SYSTEM MEMORY (MB) ====");
            Log.d(
                    TAG,
                    "Available System Memory (memoryInfo.availMem):  "
                            + (convertBytesToMegabytes(memoryInfo.availMem)));
            Log.d(
                    TAG,
                    "Total System Memory (memoryInfo.totalMem):    "
                            + (convertBytesToMegabytes(memoryInfo.totalMem)));
            Log.d(
                    TAG,
                    "Low Memory Threshold (memoryInfo.threshold): "
                            + (convertBytesToMegabytes(memoryInfo.threshold)));
            Log.d(TAG, "Is System in Low Memory (memoryInfo.lowMemory): " + memoryInfo.lowMemory);
        } else {
            Log.w(TAG, "ActivityManager not available, cannot log system memory info.");
        }
        Log.d(TAG, "==============================");
    }

    private long convertBytesToMegabytes(long bytes) {
        return bytes / BYTES_IN_MEGABYTE;
    }
}
