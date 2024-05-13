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
package com.android.adservices.shared.common;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.android.adservices.shared.SharedMockitoTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class ApplicationContextSingletonTest extends SharedMockitoTestCase {

    @Mock private Context mMockOtherContext;
    @Mock private Context mMockAppContext;
    @Mock private Context mMockOtherAppContext;

    @Before
    @After
    public void resetState() {
        ApplicationContextSingleton.setForTests(/* context= */ null);
    }

    @Test
    public void testGet_notSet() {
        assertThrows(IllegalStateException.class, () -> ApplicationContextSingleton.get());
        assertWithMessage("getForTests()").that(ApplicationContextSingleton.getForTests()).isNull();
    }

    @Test
    public void testSet_nullContext() {
        assertThrows(NullPointerException.class, () -> ApplicationContextSingleton.set(null));
    }

    @Test
    public void testSet_nullAppContext() {
        mockAppContext(mMockContext, /* appContext= */ null);

        assertThrows(
                IllegalArgumentException.class,
                () -> ApplicationContextSingleton.set(mMockContext));
    }

    @Test
    public void testSet_once() {
        mockAppContext(mMockContext, mMockAppContext);

        ApplicationContextSingleton.set(mMockContext);

        assertWithMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mMockAppContext);
    }

    @Test
    public void testSet_twiceSameAppContext() {
        mockAppContext(mMockContext, mMockAppContext);
        mockAppContext(mMockOtherContext, mMockAppContext);

        ApplicationContextSingleton.set(mMockContext);
        ApplicationContextSingleton.set(mMockOtherContext);

        assertWithMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mMockAppContext);
    }

    @Test
    public void testSet_twiceDifferentAppContexts() {
        mockAppContext(mMockContext, mMockAppContext);
        mockAppContext(mMockOtherContext, mMockOtherAppContext);

        ApplicationContextSingleton.set(mMockContext);
        assertThrows(
                IllegalStateException.class,
                () -> ApplicationContextSingleton.set(mMockOtherContext));

        assertWithMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mMockAppContext);
    }

    @Test
    public void testSetForTests() {
        ApplicationContextSingleton.setForTests(mMockContext);

        assertWithMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mMockContext);
    }

    static void mockAppContext(Context context, Context appContext) {
        when(context.getApplicationContext()).thenReturn(appContext);
    }
}
