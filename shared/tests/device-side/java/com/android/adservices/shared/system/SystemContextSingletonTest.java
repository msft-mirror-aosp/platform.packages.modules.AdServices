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
package com.android.adservices.shared.system;

import static org.junit.Assert.assertThrows;

import android.content.Context;

import com.android.adservices.shared.SharedMockitoTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class SystemContextSingletonTest extends SharedMockitoTestCase {

    @Mock private Context mMockOtherContext;

    @Before
    @After
    public void resetState() {
        SystemContextSingleton.setForTests(/* context= */ null);
    }

    @Test
    public void testGet_notSet() {
        assertThrows(IllegalStateException.class, () -> SystemContextSingleton.get());
        expect.withMessage("getForTests()").that(SystemContextSingleton.getForTests()).isNull();
    }

    @Test
    public void testSet_nullContext() {
        assertThrows(NullPointerException.class, () -> SystemContextSingleton.set(null));
    }

    @Test
    public void testSet_once() {
        Context result = SystemContextSingleton.set(mMockContext);

        expect.withMessage("result of set()").that(result).isSameInstanceAs(mMockContext);

        expect.withMessage("get()")
                .that(SystemContextSingleton.get())
                .isSameInstanceAs(mMockContext);
    }

    @Test
    public void testSet_twiceSameContext() {
        Context result1 = SystemContextSingleton.set(mMockContext);
        expect.withMessage("result of 1st set() call").that(result1).isSameInstanceAs(mMockContext);

        Context result2 = SystemContextSingleton.set(mMockContext);
        expect.withMessage("result of 2nd set() call").that(result2).isSameInstanceAs(mMockContext);

        expect.withMessage("get()")
                .that(SystemContextSingleton.get())
                .isSameInstanceAs(mMockContext);
    }

    @Test
    public void testSet_twiceDifferentAppContexts() {
        SystemContextSingleton.set(mMockContext);
        assertThrows(
                IllegalStateException.class, () -> SystemContextSingleton.set(mMockOtherContext));

        expect.withMessage("get()")
                .that(SystemContextSingleton.get())
                .isSameInstanceAs(mMockContext);
    }

    @Test
    public void testSetForTests() {
        SystemContextSingleton.setForTests(mMockContext);

        expect.withMessage("get()")
                .that(SystemContextSingleton.get())
                .isSameInstanceAs(mMockContext);

        Context previous = SystemContextSingleton.setForTests(mMockOtherContext);

        expect.withMessage("result of setForTests()").that(previous).isSameInstanceAs(mMockContext);
    }
}
