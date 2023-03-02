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

package com.android.tests.sdksandbox.ui;

import android.app.sdksandbox.testutils.SdkSandboxUiTestRule;
import android.view.SurfaceView;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkSandboxScreenshotTest {

    // Slight delay used to ensure view has rendered before screenshot testing.
    private static final int RENDERING_DELAY_MS = 100;

    private static final String IMG_IDENTIFIER = "colors";

    @Rule
    public SdkSandboxUiTestRule mUiTestRule =
            new SdkSandboxUiTestRule(
                    InstrumentationRegistry.getInstrumentation().getContext(),
                    UiTestActivity.class);

    @Test
    public void testSimpleRemoteRender() throws Exception {
        renderAndVerifyView(R.id.rendered_view, 500, 500);
    }

    @Test
    public void testAnotherSimpleRemoteRender() throws Exception {
        renderAndVerifyView(R.id.rendered_view2, 500, 500);
    }

    /**
     * Renders a remote view of a given {@code width} and {@code height} in pixels into {@code
     * viewResourceId}, and asserts that the view is rendered correctly.
     */
    private void renderAndVerifyView(int viewResourceId, int width, int height) throws Exception {
        mUiTestRule.renderInView(viewResourceId, width, height);
        Thread.sleep(RENDERING_DELAY_MS);
        mUiTestRule
                .getActivityScenario()
                .onActivity(
                        activity -> {
                            SurfaceView view = activity.findViewById(viewResourceId);
                            int[] location = new int[2];
                            view.getLocationOnScreen(location);
                            mUiTestRule.assertMatches(
                                    location[0], location[1], width, height, IMG_IDENTIFIER);
                        });
    }
}
