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

import java.util.HashMap;
import java.util.Map;

public final class ImmutableNameValuePairContainerTest extends SharedSidelessTestCase {

    @Test
    public void testConstructor_null() {
        assertThrows(NullPointerException.class, () -> new ImmutableNameValuePairContainer(null));
    }

    @Test
    public void testEverythingAtOnce_defaultConstructor() {
        String name = "The name is";
        String value = "Bond, James Bond";
        NameValuePair nvp = new NameValuePair(name, value);
        Map<String, NameValuePair> source = new HashMap<>();
        source.put(name, nvp);

        var container = new ImmutableNameValuePairContainer(source);
        expect.withMessage("clone.getAll()").that(container.getAll()).containsExactly(name, nvp);
        expect.withMessage("clone.get(%s)", name).that(container.get(name)).isSameInstanceAs(nvp);

        // Make sure it's immutable
        var thrown =
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> container.set(new NameValuePair(name, "Mutable, In Mutable")));
        expect.withMessage("message on exception")
                .that(thrown)
                .hasMessageThat()
                .contains("name is=Mutable, In Mutable");
        expect.withMessage("getFlags() after set")
                .that(container.getAll())
                .containsExactly(name, nvp);

        // Make sure it didn't affect source
        expect.withMessage("getFlags() on source").that(source).containsExactly(name, nvp);

        // Make sure changing source doesn't affect it
        source.clear();
        expect.withMessage("getFlags() after set")
                .that(container.getAll())
                .containsExactly(name, nvp);
    }

    @Test
    public void testToString() {
        Map<String, NameValuePair> source = new HashMap<>();

        var empty = new ImmutableNameValuePairContainer(source);
        expect.withMessage("toString() when empty")
                .that(empty.toString())
                .isEqualTo("ImmutableNameValuePairContainer{empty}");

        source.put("Name", new NameValuePair("Name", "of the Rose"));
        var uno = new ImmutableNameValuePairContainer(source);
        expect.withMessage("toString() with 1 nvp")
                .that(uno.toString())
                .isEqualTo("ImmutableNameValuePairContainer{Name=of the Rose}");

        source.put("dude", new NameValuePair("dude", "sweet"));
        var duo = new ImmutableNameValuePairContainer(source);
        expect.withMessage("toString() with 2 nvps")
                .that(duo.toString())
                .isEqualTo("ImmutableNameValuePairContainer{Name=of the Rose, dude=sweet}");
    }
}
