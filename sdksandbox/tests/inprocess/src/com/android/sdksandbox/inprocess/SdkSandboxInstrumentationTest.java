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

package com.android.sdksandbox.inprocess;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the instrumentation running the Sdk sanbdox tests. */
@RunWith(JUnit4.class)
public class SdkSandboxInstrumentationTest {

    private Context mContext;
    private Context mTargetContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testInstrumentationContextVsTargetContext() throws Exception {
        assumeFalse(isSdkInSandbox());
        assertWithMessage("getContext and getTargetContext should be different objects.")
                .that(mContext)
                .isNotSameInstanceAs(mTargetContext);
    }

    @Test
    public void testInstrumentationContextVsTargetContext_sdkInSandbox() throws Exception {
        assumeTrue(isSdkInSandbox());
        assertWithMessage("getContext and getTargetContext should return the same object.")
                .that(mContext)
                .isSameInstanceAs(mTargetContext);
    }

    private boolean isSdkInSandbox() {
        // Tests running in sdk-in-sandbox mode use an Sdk Uid in the instrumentation context.
        // TODO: find a better wy to check sdk-in-sandbox.
        return Process.isSdkSandboxUid(mContext.getApplicationInfo().uid);
    }
}
