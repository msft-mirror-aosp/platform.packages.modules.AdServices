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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/** Stress test used to utilize memory while running in parallel with CUJs. */
@Scenario
@RunWith(JUnit4.class)
public final class StressMemoryUtilization extends StressScenarioTestAction {
    private static final int MEMORY_STRESS_CYCLES = 7;
    private final List<Bitmap> mBitmaps = new ArrayList<>();

    private static final String TAG = "StressMemoryUtilization";

    /**
     * Make sure all resources are cleaned up and garbage collected before moving on to the next
     * test.
     */
    @After
    public void tearDown() {
        System.gc();
        System.runFinalization();
        System.gc();
    }

    @Test
    public void startMemoryStress() {
        String imageName = "test_image_uk-lon-6ps";

        Context context = ApplicationProvider.getApplicationContext();
        for (int i = 0; i < MEMORY_STRESS_CYCLES; i++) {
            int resourceId =
                    context.getResources()
                            .getIdentifier(imageName, "drawable", context.getPackageName());
            if (resourceId != 0) {
                Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId);
                Log.d(TAG, "Bitmap allocated");
                mBitmaps.add(bitmap);
            } else {
                Log.e(TAG, "Image not found: " + imageName);
            }
        }

        holdUntilCujsRunning();

        mBitmaps.clear();
    }
}
