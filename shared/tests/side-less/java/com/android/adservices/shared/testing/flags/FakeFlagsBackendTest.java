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
package com.android.adservices.shared.testing.flags;

import static com.android.adservices.shared.testing.flags.MissingFlagBehavior.THROWS_EXCEPTION;
import static com.android.adservices.shared.testing.flags.MissingFlagBehavior.USES_EXPLICIT_DEFAULT;
import static com.android.adservices.shared.testing.flags.MissingFlagBehavior.USES_JAVA_LANGUAGE_DEFAULT;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.NameValuePair;

import org.junit.Test;
import org.junit.function.ThrowingRunnable;

public final class FakeFlagsBackendTest extends SharedSidelessTestCase {

    private static final String TAG = FakeFlagsBackendTest.class.getSimpleName();
    private static final String NAME = "A Flag Has No Name";

    private final FakeFlagsBackend mBackend = new FakeFlagsBackend(TAG);

    @Test
    public void testNullConstructor() {
        assertThrows(NullPointerException.class, () -> new FakeFlagsBackend(/* tagName= */ null));
    }

    @Test
    public void testCloneForSnapshot() {
        var clone = mBackend.cloneForSnapshot();

        assertWithMessage("cloneForSnapshot()").that(clone).isNotNull();
        assertWithMessage("cloneForSnapshot()").that(clone).isNotSameInstanceAs(mBackend);
        expect.withMessage("tag on cloneForSnapshot()").that(clone.getTagName()).isEqualTo(TAG);

        // Make sure it's immutable
        var thrown =
                assertThrows(
                        UnsupportedOperationException.class, () -> clone.setFlag("dude", "sweet"));
        expect.withMessage("message on exception")
                .that(thrown)
                .hasMessageThat()
                .isEqualTo("setFlag(dude, sweet): not supported on snapshot");
        expect.withMessage("getFlags() on clone").that(mBackend.getFlags()).isEmpty();

        // Make sure it didn't affect source
        expect.withMessage("getFlags() on source").that(mBackend.getFlags()).isEmpty();
    }

    @Test
    public void testGetFlags() {
        var flags1 = mBackend.getFlags();
        assertWithMessage("getFlags() on empty").that(flags1).isNotNull();
        expect.withMessage("getFlags() on empty").that(flags1).isEmpty();

        mBackend.setFlag(NAME, "of the Rose");
        var flags2 = mBackend.getFlags();
        assertWithMessage("getFlags() after adding").that(flags2).isNotNull();
        expect.withMessage("getFlags() after adding").that(flags2).isNotSameInstanceAs(flags1);
        expect.withMessage("getFlags() after adding")
                .that(flags2)
                .containsExactly(NAME, new NameValuePair(NAME, "of the Rose"));
    }

    @Test
    public void testSetMissingFlagsBehavior_null() {
        assertThrows(NullPointerException.class, () -> mBackend.setMissingFlagBehavior(null));
    }

    @Test
    public void testGetAndSetMissingFlagsBehavior() {
        expect.withMessage("initial getMissingFlagsBehavior()")
                .that(mBackend.getMissingFlagBehavior())
                .isEqualTo(USES_EXPLICIT_DEFAULT);

        for (var behavior : MissingFlagBehavior.values()) {
            mBackend.setMissingFlagBehavior(behavior);
            expect.withMessage(
                            "getMissingFlagsBehavior() after setMissingFlagsBehavior(%s)", behavior)
                    .that(mBackend.getMissingFlagBehavior())
                    .isEqualTo(behavior);
        }
    }

    @Test
    public void testGetFlag_generic() {
        var thrown =
                assertThrows(UnsupportedOperationException.class, () -> mBackend.getFlag(NAME));

        expect.withMessage("exception message").that(thrown).hasMessageThat().contains(NAME);
    }

    @Test
    public void testGetFlagMethods_notFound_useExplicitlyDefault() {
        mBackend.setMissingFlagBehavior(MissingFlagBehavior.USES_EXPLICIT_DEFAULT);

        expect.withMessage("getFlag(boolean)").that(mBackend.getFlag(NAME, true)).isEqualTo(true);
        expect.withMessage("getFlag(String)")
                .that(mBackend.getFlag(NAME, "Bond, James Bond!"))
                .isEqualTo("Bond, James Bond!");
        expect.withMessage("getFlag(int)").that(mBackend.getFlag(NAME, 42)).isEqualTo(42);
        expect.withMessage("getFlag(long)")
                .that(mBackend.getFlag(NAME, 4815162342L))
                .isEqualTo(4815162342L);
        expect.withMessage("getFlag(float)")
                .that(mBackend.getFlag(NAME, 108.666F))
                .isEqualTo(108.666F);
    }

    @Test
    public void testGetFlagMethods_notFound_useJavaLanguageDefault() {
        mBackend.setMissingFlagBehavior(USES_JAVA_LANGUAGE_DEFAULT);

        expect.withMessage("getFlag(boolean)").that(mBackend.getFlag(NAME, true)).isEqualTo(false);
        expect.withMessage("getFlag(String)")
                .that(mBackend.getFlag(NAME, "Bond, James Bond!"))
                .isNull();
        expect.withMessage("getFlag(int)").that(mBackend.getFlag(NAME, 42)).isEqualTo(0);
        expect.withMessage("getFlag(long)").that(mBackend.getFlag(NAME, 4815162342L)).isEqualTo(0);
        expect.withMessage("getFlag(float)").that(mBackend.getFlag(NAME, 108.666F)).isEqualTo(0F);
    }

    @Test
    public void testGetFlagMethods_notFound_throwsException() {
        mBackend.setMissingFlagBehavior(THROWS_EXCEPTION);

        assertThrowsNotSet("getFlag(boolean)", () -> mBackend.getFlag(NAME, true));
        assertThrowsNotSet("getFlag(String)", () -> mBackend.getFlag(NAME, "Bond, James Bond!"));
        assertThrowsNotSet("getFlag(int)", () -> mBackend.getFlag(NAME, 42));
        assertThrowsNotSet("getFlag(long)", () -> mBackend.getFlag(NAME, 4815162342L));
        assertThrowsNotSet("getFlag(float)", () -> mBackend.getFlag(NAME, 108.666F));
    }

    @Test
    public void testSet_nullName() {
        assertThrows(
                NullPointerException.class,
                () -> mBackend.setFlag(/* name= */ null, /* value= */ "A Value has no Null"));
    }

    @Test
    public void testSet_boolean() {
        mBackend.setFlag(NAME, "false");

        assertWithMessage("get() after setFlag()")
                .that(mBackend.getFlag(NAME, true))
                .isEqualTo(false);

        mBackend.setFlag(NAME, null);
        assertWithMessage("get() after setFlag(null)")
                .that(mBackend.getFlag(NAME, true))
                .isEqualTo(true);
    }

    @Test
    public void testSet_string() {
        mBackend.setFlag(NAME, "Bond, James Bond");

        assertWithMessage("get() after setFlag()")
                .that(mBackend.getFlag(NAME, "I'm not set, therefore I am"))
                .isEqualTo("Bond, James Bond");

        mBackend.setFlag(NAME, null);
        assertWithMessage("get() after setFlag(null)")
                .that(mBackend.getFlag(NAME, "I'm not set, therefore I am"))
                .isEqualTo("I'm not set, therefore I am");
    }

    @Test
    public void testSet_int() {
        mBackend.setFlag(NAME, "42");

        assertWithMessage("get() after setFlag()").that(mBackend.getFlag(NAME, 666)).isEqualTo(42);

        mBackend.setFlag(NAME, null);
        assertWithMessage("get() after setFlag(null)")
                .that(mBackend.getFlag(NAME, 666))
                .isEqualTo(666);
    }

    @Test
    public void testSet_long() {
        mBackend.setFlag(NAME, "4815162342");

        assertWithMessage("get() after setFlag()")
                .that(mBackend.getFlag(NAME, 666L))
                .isEqualTo(4815162342L);

        mBackend.setFlag(NAME, null);
        assertWithMessage("get() after setFlag(null)")
                .that(mBackend.getFlag(NAME, 666))
                .isEqualTo(666L);
    }

    @Test
    public void testSet_float() {
        mBackend.setFlag(NAME, "108.666");

        assertWithMessage("get() after setFlag()")
                .that(mBackend.getFlag(NAME, 666F))
                .isEqualTo(108.666F);

        mBackend.setFlag(NAME, null);
        assertWithMessage("get() after setFlag(null)")
                .that(mBackend.getFlag(NAME, 666F))
                .isEqualTo(666F);
    }

    @Test
    public void testSet_nameValuePair_null() {
        assertThrows(NullPointerException.class, () -> mBackend.set(null));
    }

    @Test
    public void testSet_nameValuePair() {
        NameValuePair nvp = new NameValuePair(NAME, "Bond, James Bond");

        var firstPrevious = mBackend.set(nvp);

        expect.withMessage("get() after set()")
                .that(mBackend.getFlag(NAME, "Y U NO IGNORE ME?"))
                .isEqualTo("Bond, James Bond");
        expect.withMessage("result of set(nvp)").that(firstPrevious).isNull();

        var secondPrevious = mBackend.set(new NameValuePair(NAME, null));

        assertWithMessage("get() after setFlag(null)")
                .that(mBackend.getFlag(NAME, "I'm not set, therefore I am"))
                .isEqualTo("I'm not set, therefore I am");
        expect.withMessage("result of set(nvp with null value)")
                .that(secondPrevious)
                .isEqualTo(nvp);
    }

    private void assertThrowsNotSet(String what, ThrowingRunnable r) {
        var thrown = assertThrows(IllegalStateException.class, r);

        expect.withMessage("message on exception from %s", what)
                .that(thrown)
                .hasMessageThat()
                .isEqualTo("Value of flag " + NAME + " not set");
    }
}
