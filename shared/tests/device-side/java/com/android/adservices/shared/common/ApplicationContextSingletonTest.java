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

import static org.junit.Assert.assertThrows;

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
        expect.withMessage("getForTests()")
                .that(ApplicationContextSingleton.getForTests())
                .isNull();
    }

    @Test
    public void testSet_nullContext() {
        assertThrows(NullPointerException.class, () -> ApplicationContextSingleton.set(null));
    }

    @Test
    public void testSet_nullAppContext() {
        mocker.mockGetApplicationContext(mMockContext, /* appContext= */ null);

        assertThrows(
                IllegalArgumentException.class,
                () -> ApplicationContextSingleton.set(mMockContext));
    }

    @Test
    public void testSet_once() {
        mocker.mockGetApplicationContext(mMockContext, mMockAppContext);

        ApplicationContextSingleton.set(mMockContext);

        expect.withMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mMockAppContext);
    }

    @Test
    public void testSet_twiceSameAppContext() {
        mocker.mockGetApplicationContext(mMockContext, mMockAppContext);
        mocker.mockGetApplicationContext(mMockOtherContext, mMockAppContext);

        ApplicationContextSingleton.set(mMockContext);
        ApplicationContextSingleton.set(mMockOtherContext);

        expect.withMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mMockAppContext);
    }

    @Test
    public void testSet_twiceDifferentAppContexts() {
        mocker.mockGetApplicationContext(mMockContext, mMockAppContext);
        mocker.mockGetApplicationContext(mMockOtherContext, mMockOtherAppContext);

        ApplicationContextSingleton.set(mMockContext);
        assertThrows(
                IllegalStateException.class,
                () -> ApplicationContextSingleton.set(mMockOtherContext));

        expect.withMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mMockAppContext);
    }

    @Test
    public void testSetAs_nullContext() {
        assertThrows(NullPointerException.class, () -> ApplicationContextSingleton.setAs(null));
    }

    @Test
    public void testSetAs() {
        ApplicationContextSingleton.setAs(mMockContext);

        expect.withMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mMockContext);
    }

    @Test
    public void testSetAs_twiceSameContext() {
        ApplicationContextSingleton.setAs(mMockContext);
        ApplicationContextSingleton.setAs(mMockContext);

        expect.withMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mMockContext);
    }

    @Test
    public void testSetAs_twiceDifferentContexts() {
        ApplicationContextSingleton.setAs(mMockContext);
        assertThrows(
                IllegalStateException.class,
                () -> ApplicationContextSingleton.setAs(mMockOtherContext));

        expect.withMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mMockContext);
    }

    // Note: in theory we should test combinations of set() and setAs(), but it'd be an overkill...

    @Test
    public void testSetForTests() {
        ApplicationContextSingleton.setForTests(mMockContext);

        expect.withMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mMockContext);
    }
}
