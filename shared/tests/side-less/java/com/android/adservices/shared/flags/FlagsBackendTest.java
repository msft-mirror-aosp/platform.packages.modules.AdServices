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
package com.android.adservices.shared.flags;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.Nullable;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class FlagsBackendTest extends SharedSidelessTestCase {

    private final FakeFlagsBackend mBackend = new FakeFlagsBackend();

    private final String mNamePresent = "Bond, James";
    private final String mNameNotPresent = "GoldFinger";
    private final String mNameWithInvalidValue = "Klaus, Santa";
    private final String mNameWithNullValue = "NAME, Y U NOT NULL?";

    @Before
    public void setFixtures() {
        mBackend.setFlag(mNameWithInvalidValue, "I Can't believe this is valid primitive type");
        mBackend.setFlag(mNameWithNullValue, null);
    }

    @Test
    public void testGetters_null() {
        assertThrows(NullPointerException.class, () -> mBackend.getFlag(null, true));
        assertThrows(NullPointerException.class, () -> mBackend.getFlag(null, "Dude"));
        assertThrows(NullPointerException.class, () -> mBackend.getFlag(null, 42));
        assertThrows(NullPointerException.class, () -> mBackend.getFlag(null, 4815162342L));
    }

    @Test
    public void testGetBoolean() {
        mBackend.setFlag(mNamePresent, "true");

        expect.withMessage("getFlag(def=true) when not present")
                .that(mBackend.getFlag(mNameNotPresent, true))
                .isTrue();
        expect.withMessage("getFlag(def=false) when not present")
                .that(mBackend.getFlag(mNameNotPresent, false))
                .isFalse();
        expect.withMessage("getFlag(def=true) when present")
                .that(mBackend.getFlag(mNamePresent, true))
                .isTrue();
        expect.withMessage("getFlag(def=false) when present")
                .that(mBackend.getFlag(mNamePresent, false))
                .isTrue();
        expect.withMessage("getFlag(def=true) when value is invalid")
                .that(mBackend.getFlag(mNameWithInvalidValue, true))
                .isFalse();
        expect.withMessage("getFlag(def=false) when value is invalid")
                .that(mBackend.getFlag(mNameWithInvalidValue, false))
                .isFalse();
        expect.withMessage("getFlag(def=true) when value is null")
                .that(mBackend.getFlag(mNameWithNullValue, true))
                .isTrue();
        expect.withMessage("getFlag(def=false) when value is null")
                .that(mBackend.getFlag(mNameWithNullValue, false))
                .isFalse();
    }

    @Test
    public void testGetString() {
        mBackend.setFlag(mNamePresent, "Dude");

        expect.withMessage("getFlag(def=Sweet) when not present")
                .that(mBackend.getFlag(mNameNotPresent, "Sweet"))
                .isEqualTo("Sweet");
        expect.withMessage("getFlag(def=Sweet) when present")
                .that(mBackend.getFlag(mNamePresent, "Sweet"))
                .isEqualTo("Dude");
        expect.withMessage("getFlag(def=Sweet) when not present")
                .that(mBackend.getFlag(mNameNotPresent, "Sweet"))
                .isEqualTo("Sweet");
        expect.withMessage("getFlag(def=Sweet) when value is null")
                .that(mBackend.getFlag(mNameWithNullValue, "Sweet"))
                .isEqualTo("Sweet");
    }

    @Test
    public void testGetInt() {
        mBackend.setFlag(mNamePresent, "42");

        expect.withMessage("getFlag(def=108) when not present")
                .that(mBackend.getFlag(mNameNotPresent, 108))
                .isEqualTo(108);
        expect.withMessage("getFlag(def=108) when present")
                .that(mBackend.getFlag(mNamePresent, 108))
                .isEqualTo(42);
        expect.withMessage("getFlag(def=108) when not present")
                .that(mBackend.getFlag(mNameNotPresent, 108))
                .isEqualTo(108);
        expect.withMessage("getFlag(def=108) when value is null")
                .that(mBackend.getFlag(mNameWithNullValue, 108))
                .isEqualTo(108);
    }

    @Test
    public void testGetLong() {
        mBackend.setFlag(mNamePresent, "4815162342");

        expect.withMessage("getFlag(def=666L) when not present")
                .that(mBackend.getFlag(mNameNotPresent, 666L))
                .isEqualTo(666L);
        expect.withMessage("getFlag(def=666L) when present")
                .that(mBackend.getFlag(mNamePresent, 666L))
                .isEqualTo(4815162342L);
        expect.withMessage("getFlag(def=666L) when not present")
                .that(mBackend.getFlag(mNameNotPresent, 666L))
                .isEqualTo(666L);
        expect.withMessage("getFlag(def=666L) when value is null")
                .that(mBackend.getFlag(mNameWithNullValue, 666L))
                .isEqualTo(666L);
    }

    @Test
    public void testGetFloat() {
        assertThrows(NullPointerException.class, () -> mBackend.getFlag(null, 4.2F));

        mBackend.setFlag(mNamePresent, "4.2");

        expect.withMessage("getFlag(def=10.8) when not present")
                .that(mBackend.getFlag(mNameNotPresent, 10.8F))
                .isEqualTo(10.8F);
        expect.withMessage("getFlag(def=10.8) when present")
                .that(mBackend.getFlag(mNamePresent, 10.8F))
                .isEqualTo(4.2F);
        expect.withMessage("getFlag(def=10.8) when not present")
                .that(mBackend.getFlag(mNameNotPresent, 10.8F))
                .isEqualTo(10.8F);
        expect.withMessage("getFlag(def=10.8) when value is null")
                .that(mBackend.getFlag(mNameWithNullValue, 10.8F))
                .isEqualTo(10.8F);
    }

    private static final class FakeFlagsBackend implements FlagsBackend {

        private final Map<String, String> mFlags = new HashMap<String, String>();

        public void setFlag(String name, @Nullable String value) {
            Objects.requireNonNull(name, "name cannot be null");
            mFlags.put(name, value);
        }

        @Override
        public String getFlag(String name) {
            return mFlags.get(name);
        }
    }
}
