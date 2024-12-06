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

import org.junit.Test;

public final class NameValuePairTest extends SharedSidelessTestCase {

    @Test
    public void testNullConstructor() {
        assertThrows(
                NullPointerException.class, () -> new NameValuePair(/* name= */ null, "D'OH!"));
    }

    @Test
    public void testFields_noSeparator() {
        var nvp = new NameValuePair("name", "Bond, James Bond!");

        expect.withMessage("name").that(nvp.name).isEqualTo("name");
        expect.withMessage("value").that(nvp.value).isEqualTo("Bond, James Bond!");
        expect.withMessage("separator").that(nvp.separator).isNull();
        expect.withMessage("toString").that(nvp.toString()).isEqualTo("name=Bond, James Bond!");
    }

    @Test
    public void testFields_withSeparator() {
        var nvp = new NameValuePair("name", "Bond, James Bond!", "MI6");

        expect.withMessage("name").that(nvp.name).isEqualTo("name");
        expect.withMessage("value").that(nvp.value).isEqualTo("Bond, James Bond!");
        expect.withMessage("separator").that(nvp.separator).isEqualTo("MI6");
        expect.withMessage("toString")
                .that(nvp.toString())
                .isEqualTo("name=Bond, James Bond! (separator=MI6)");
    }

    @Test
    public void testFields_nullValue_noSeparator() {
        var nvp = new NameValuePair("nameless", /* value= */ null);

        expect.withMessage("name").that(nvp.name).isEqualTo("nameless");
        expect.withMessage("value").that(nvp.value).isNull();
        expect.withMessage("separator").that(nvp.separator).isNull();
        expect.withMessage("toString").that(nvp.toString()).isEqualTo("nameless=null");
    }

    @Test
    public void testFields_nullValue_withSeparator() {
        var nvp = new NameValuePair("nameless", /* value= */ null, "but separable");

        expect.withMessage("name").that(nvp.name).isEqualTo("nameless");
        expect.withMessage("value").that(nvp.value).isNull();
        expect.withMessage("separator").that(nvp.separator).isEqualTo("but separable");
        expect.withMessage("toString")
                .that(nvp.toString())
                .isEqualTo("nameless=null (separator=but separable)");
    }

    @Test
    public void testEqualsHashCode() {
        EqualsTester et = new EqualsTester(expect);
        var baseline = new NameValuePair("name", "Bond, James Bond!");
        var equal1 = new NameValuePair("name", "Bond, James Bond!");
        var equal2 = new NameValuePair("name", "Bond, James Bond!", "MI6");
        var diff1 = new NameValuePair("Name", "Bond, James Bond!");
        var diff2 = new NameValuePair("name", "Bond, James Bond");

        et.expectObjectsAreEqual(baseline, equal1);
        et.expectObjectsAreEqual(baseline, equal2);
        et.expectObjectsAreNotEqual(baseline, diff1);
        et.expectObjectsAreNotEqual(baseline, diff2);
    }
}
