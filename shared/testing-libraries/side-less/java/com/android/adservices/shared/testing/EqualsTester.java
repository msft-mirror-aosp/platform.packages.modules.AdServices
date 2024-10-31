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

import com.google.common.truth.StandardSubjectBuilder;

import java.util.Objects;

/** Helper class used to test the implementation of {@code equals()} and {@code hashCode()}. */
public final class EqualsTester {

    private final StandardSubjectBuilder mExpect;

    /** Default constructor. */
    public EqualsTester(StandardSubjectBuilder expect) {
        mExpect = Objects.requireNonNull(expect);
    }

    // TODO(b/336615269): refactor to take Object... instead
    /**
     * Helper method that uses {@code expect} to assert the class properly implement {@code
     * equals()} and {@code hashCode()}.
     *
     * @param obj1 object that is equals to {@code obj2}
     * @param obj2 object that is equals to {@code obj1}
     */
    public void expectObjectsAreEqual(Object obj1, Object obj2) {
        Objects.requireNonNull(obj1, "1st arg cannot be null");
        Objects.requireNonNull(obj2, "2nd arg cannot be null");

        if (!obj1.equals(obj2)) {
            mExpect.withMessage("1st obj (%s) is not equal to 2nd (%s)", obj1, obj2).fail();
        }
        if (!obj2.equals(obj1)) {
            mExpect.withMessage("2nd obj (%s) is not equal to 1st (%s)", obj2, obj1).fail();
        }
        int hashCode1 = obj1.hashCode();
        int hashCode2 = obj2.hashCode();
        if (hashCode1 != hashCode2) {
            mExpect.withMessage(
                            "hashcode (%s) of 1st object (%s) is not equal to hashcode (%s) of 2nd"
                                    + " object (%s)",
                            hashCode1, obj1, hashCode2, obj2)
                    .fail();
        }
    }

    // TODO(b/336615269): refactor to take Object... instead
    /**
     * Helper method that uses {@code expect} to assert the class properly implement {@code
     * equals()}.
     *
     * @param obj1 object that is not equal to {@code obj2}
     * @param obj2 object that is not equal to {@code obj1}
     */
    public void expectObjectsAreNotEqual(Object obj1, @Nullable Object obj2) {
        Objects.requireNonNull(obj1, "1st arg cannot be null");

        mExpect.withMessage("1st obj (%s)", obj1).that(obj1).isNotEqualTo(obj2);
        mExpect.withMessage("2nd obj (%s)", obj2).that(obj2).isNotEqualTo(obj1);
    }
}
