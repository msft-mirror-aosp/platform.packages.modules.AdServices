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

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;

import com.google.common.truth.StandardSubjectBuilder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class EqualsTesterTest extends SharedSidelessTestCase {

    private final Object mObj1 = new Object();
    private final Object mObj2 = new Object();

    private final List<AssertionError> mErrors = new ArrayList<>();
    private final EqualsTester mEt =
            new EqualsTester(
                    StandardSubjectBuilder.forCustomFailureStrategy(error -> mErrors.add(error)));

    @Test
    public void testNullConstructor() {
        assertThrows(NullPointerException.class, () -> new EqualsTester(null));
    }

    @Test
    public void testExpectObjectsAreEqual_null() {
        assertThrows(NullPointerException.class, () -> mEt.expectObjectsAreEqual(mObj1, null));
        assertThrows(NullPointerException.class, () -> mEt.expectObjectsAreEqual(null, mObj1));
    }

    @Test
    public void testExpectObjectsAreEqual_equal() {
        mEt.expectObjectsAreEqual(mObj1, mObj1);

        assertNoErrors();
    }

    @Test
    public void testExpectObjectsAreEqual_notEqual() {
        mEt.expectObjectsAreEqual(mObj1, mObj2);

        assertHasErrors();
    }

    @Test
    public void testExpectObjectsAreEqual_notEqualButWithSameHashcode() {
        ObjectsWithSameHashCode obj1 = new ObjectsWithSameHashCode();
        ObjectsWithSameHashCode obj2 = new ObjectsWithSameHashCode();

        mEt.expectObjectsAreEqual(obj1, obj2);

        assertHasErrors();
    }

    @Test
    public void testExpectObjectsAreNotEqual_1stNull() {
        assertThrows(NullPointerException.class, () -> mEt.expectObjectsAreNotEqual(null, mObj1));

        assertNoErrors();
    }

    @Test
    public void testExpectObjectsAreNotEqual_2ndNull() {
        mEt.expectObjectsAreNotEqual(mObj1, null);
    }

    @Test
    public void testExpectObjectsAreNotEqual_equal() {
        mEt.expectObjectsAreNotEqual(mObj1, mObj1);

        assertHasErrors();
    }

    @Test
    public void testExpectObjectsAreNotEqual_notEqual() {
        mEt.expectObjectsAreNotEqual(mObj1, mObj2);

        assertNoErrors();
    }

    @Test
    public void testExpectObjectsAreNotEqual_notEqualButWithSameHashcode() {
        ObjectsWithSameHashCode obj1 = new ObjectsWithSameHashCode();
        ObjectsWithSameHashCode obj2 = new ObjectsWithSameHashCode();

        mEt.expectObjectsAreNotEqual(obj1, obj2);

        assertNoErrors();
    }

    private void assertNoErrors() {
        expect.withMessage("errors").that(mErrors).isEmpty();
    }

    private void assertHasErrors() {
        expect.withMessage("errors").that(mErrors).isNotEmpty();
    }

    private static final class ObjectsWithSameHashCode {
        private static int sId;
        private int mId = ++sId;

        @Override
        public boolean equals(Object obj) {
            return ((obj instanceof ObjectsWithSameHashCode)
                    && ((ObjectsWithSameHashCode) obj).mId == mId);
        }

        @Override
        public int hashCode() {
            return 42;
        }

        @Override
        public String toString() {
            return "ObjectsWithSameHashCode(id=" + mId + ")";
        }
    }
}
