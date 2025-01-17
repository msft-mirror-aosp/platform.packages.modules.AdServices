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
package com.android.adservices.shared.testing;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;

import org.junit.Test;

public final class FakeNameValuePairContainerTest extends SharedSidelessTestCase {

    private static final String TAG_NAME = "Tag me if you can";

    private static final String NAME1 = "The Name is";
    private static final String VALUE1 = "Bond, James Bond";
    private static final String NAME2 = "NOT, The Name isn't";
    private static final String VALUE2 = "007";

    private final FakeNameValuePairContainer mContainer = new FakeNameValuePairContainer(TAG_NAME);

    @Test
    public void testSet_null() {
        assertThrows(NullPointerException.class, () -> mContainer.set(null));
    }

    @Test
    public void testNormalWorkflow() {
        // Initial state
        expect.withMessage("get(%s) initially", NAME1).that(mContainer.get(NAME1)).isNull();
        expect.withMessage("getAll() initially").that(mContainer.getAll()).isEmpty();
        expect.withMessage("getCalls() initially").that(mContainer.getCalls()).isEmpty();

        // Add 1st NVP
        var name1Value1 = new NameValuePair(NAME1, VALUE1);
        expect.withMessage("return of set(%s)", name1Value1)
                .that(mContainer.set(name1Value1))
                .isNull();
        expect.withMessage("get(%s) after adding 1st", NAME1)
                .that(mContainer.get(NAME1))
                .isSameInstanceAs(name1Value1);
        expect.withMessage("getAll() after adding 1st")
                .that(mContainer.getAll())
                .containsExactly(NAME1, name1Value1);
        expect.withMessage("getCalls() after adding 1st")
                .that(mContainer.getCalls())
                .containsExactly(name1Value1);

        // Update value of 1st NVP
        var name1Value2 = new NameValuePair(NAME1, VALUE2);
        expect.withMessage("return of set(%s)", name1Value2)
                .that(mContainer.set(name1Value2))
                .isSameInstanceAs(name1Value1);
        expect.withMessage("get(%s) after updating 1st", NAME1)
                .that(mContainer.get(NAME1))
                .isSameInstanceAs(name1Value2);
        expect.withMessage("getAll() after updating 1st")
                .that(mContainer.getAll())
                .containsExactly(NAME1, name1Value2);
        expect.withMessage("getCalls() after updating 1st")
                .that(mContainer.getCalls())
                .containsExactly(name1Value1, name1Value2);

        // Add 2nd NVP
        var name2Value1 = new NameValuePair(NAME2, VALUE1);
        expect.withMessage("return of set(%s)", name2Value1)
                .that(mContainer.set(name2Value1))
                .isNull();
        expect.withMessage("get(%s) after adding 2nd", NAME2)
                .that(mContainer.get(NAME2))
                .isSameInstanceAs(name2Value1);
        expect.withMessage("getAll() after adding 2nd")
                .that(mContainer.getAll())
                .containsExactly(NAME1, name1Value2, NAME2, name2Value1);
        expect.withMessage("getCalls() after adding 2nd")
                .that(mContainer.getCalls())
                .containsExactly(name1Value1, name1Value2, name2Value1);

        // We could check toString() on every step, but it'd be an overkill
        String toString = mContainer.toString();
        expect.withMessage("toString() after adding 2nd")
                .that(toString)
                .contains("mCalls=" + mContainer.getCalls());
        expect.withMessage("toString() after adding 2nd")
                .that(toString)
                .contains("mMap=" + mContainer.getAll());

        // Reset calls
        expect.withMessage("getAndResetCalls()")
                .that(mContainer.getAndResetCalls())
                .containsExactly(name1Value1, name1Value2, name2Value1);
        expect.withMessage("getCalls() after getAndResetCalls()")
                .that(mContainer.getCalls())
                .isEmpty();
        // Other state didn't change
        expect.withMessage("get(%s) after getAndResetCalls()", NAME1)
                .that(mContainer.get(NAME1))
                .isSameInstanceAs(name1Value2);
        expect.withMessage("get(%s) after getAndResetCalls()", NAME2)
                .that(mContainer.get(NAME2))
                .isSameInstanceAs(name2Value1);
        expect.withMessage("getAll() after getAndResetCalls()")
                .that(mContainer.getAll())
                .containsExactly(NAME1, name1Value2, NAME2, name2Value1);
    }

    @Test
    public void testRemove() {
        var nvp = new NameValuePair(NAME1, VALUE1);
        mContainer.set(nvp);

        mContainer.set(new NameValuePair(NAME1, null));

        expect.withMessage("get(%s) after remove", NAME1).that(mContainer.get(NAME1)).isNull();
        expect.withMessage("getAll() after remove").that(mContainer.getAll()).isEmpty();
    }

    @Test
    public void testOnSetThrows() {
        var nvp = new NameValuePair(NAME1, VALUE1);
        var e = new RuntimeException("D'OH!");
        mContainer.onSetThrows(NAME1, e);
        expect.withMessage("toString()").that(mContainer.toString()).contains(NAME1 + "=" + e);

        var thrown = assertThrows(Exception.class, () -> mContainer.set(nvp));

        expect.withMessage("exception thrown by set()").that(thrown).isSameInstanceAs(e);

        // Make sure other name is fine
        mContainer.set(new NameValuePair("Thou should Pass", null));

        // reset it
        mContainer.onSetThrows(NAME1, null);
        mContainer.set(nvp);

        expect.withMessage("get()").that(mContainer.get(NAME1)).isSameInstanceAs(nvp);
    }
}
