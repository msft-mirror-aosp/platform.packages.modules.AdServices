/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.shared.testing;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

public final class DeviceSideTestCaseTest extends SidelessTestCase {

    private final MyDeviceSideTestCase mTestCase = new MyDeviceSideTestCase();

    @Test
    public void testContextReferences() {
        Context expectedContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        expect.withMessage("mContext")
                .that(mTestCase.getContext())
                .isSameInstanceAs(expectedContext);
        expect.withMessage("sContext")
                .that(mTestCase.getStaticContext())
                .isSameInstanceAs(expectedContext);

        String expectedPackageName = expectedContext.getPackageName();
        expect.withMessage("mPackageName")
                .that(mTestCase.getPackageName())
                .isSameInstanceAs(expectedPackageName);
        expect.withMessage("sPackageName")
                .that(mTestCase.getStaticPackageName())
                .isSameInstanceAs(expectedPackageName);
    }

    @Test
    public void testTag() {
        expect.withMessage("mTag").that(mTestCase.getTag()).isEqualTo("MyDeviceSideTestCase");
    }

    private static final class MyDeviceSideTestCase extends DeviceSideTestCase {

        public Context getContext() {
            return mContext;
        }

        public Context getStaticContext() {
            return sContext;
        }

        public String getPackageName() {
            return mPackageName;
        }

        public String getStaticPackageName() {
            return sPackageName;
        }

        public String getTag() {
            return mTag;
        }
    }
}
