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

public final class NameValuePairActionTest extends SharedSidelessTestCase {

    private static final String NAME = "The Name is";

    private final FakeNameValuePairSetter mSetter = new FakeNameValuePairSetter();

    @Test
    public void testConstructor_null() {
        var nvp = new NameValuePair(NAME, "Bond, James Bond");

        assertThrows(
                NullPointerException.class,
                () -> new NameValuePairAction(/* logger= */ null, mSetter, nvp));
        assertThrows(
                NullPointerException.class,
                () -> new NameValuePairAction(mFakeLogger, /* setter= */ null, nvp));
        assertThrows(
                NullPointerException.class,
                () -> new NameValuePairAction(mFakeLogger, mSetter, /* nvp= */ null));
    }

    @Test
    public void testGetNvp() {
        var nvp = new NameValuePair(NAME, "Bond, James Bond");
        var action = new NameValuePairAction(mFakeLogger, mSetter, nvp);

        expect.withMessage("getNvp()").that(action.getNvp()).isSameInstanceAs(nvp);
    }

    @Test
    public void testExecuteAndRevert_setFails() throws Exception {
        mSetter.onSetThrows(NAME, new RuntimeException("D'OH!"));
        var nvp = new NameValuePair(NAME, "Bond, James Bond");
        var action = new NameValuePairAction(mFakeLogger, mSetter, nvp);

        var result = action.execute();
        expect.withMessage("execute()").that(result).isFalse();
        expect.withMessage("setter.get(%s) after revert", NAME).that(mSetter.get(NAME)).isNull();
        expect.withMessage("setter.getAll()").that(mSetter.getAll()).isEmpty();

        action.revert();
        expect.withMessage("setter.get(%s) after revert", NAME).that(mSetter.get(NAME)).isNull();
        expect.withMessage("setter.getAll()").that(mSetter.getAll()).isEmpty();
    }

    @Test
    public void testExecuteAndRevert_notChanged() throws Exception {
        var nvp = new NameValuePair(NAME, "Bond, James Bond");
        mSetter.set(nvp);
        var action = new NameValuePairAction(mFakeLogger, mSetter, nvp);

        var result = action.execute();
        expect.withMessage("execute()").that(result).isFalse();
        expect.withMessage("setter.getAll() after execute")
                .that(mSetter.getAll())
                .containsExactly(nvp);

        action.revert();
        expect.withMessage("setter.get(%s) after revert", NAME)
                .that(mSetter.get(NAME))
                .isEqualTo(nvp);
        expect.withMessage("setter.getAll() after revert")
                .that(mSetter.getAll())
                .containsExactly(nvp);
    }

    @Test
    public void testExecuteAndRevert_previousReturnedNull() throws Exception {
        var nvp = new NameValuePair(NAME, "Bond, James Bond");
        var action = new NameValuePairAction(mFakeLogger, mSetter, nvp);

        var result = action.execute();
        expect.withMessage("execute()").that(result).isTrue();
        expect.withMessage("setter.getAll() after execute")
                .that(mSetter.getAll())
                .containsExactly(nvp);

        action.revert();
        expect.withMessage("setter.get(%s) after revert", NAME).that(mSetter.get(NAME)).isNull();
        expect.withMessage("setter.getAll() after revert").that(mSetter.getAll()).isEmpty();
    }

    @Test
    public void testExecuteAndRevert_changed() throws Exception {
        NameValuePair previousNvp = new NameValuePair(NAME, "Slim Shade");
        mSetter.set(previousNvp);
        var nvp = new NameValuePair(NAME, "Bond, James Bond");
        var action = new NameValuePairAction(mFakeLogger, mSetter, nvp);

        var result = action.execute();
        expect.withMessage("execute()").that(result).isTrue();
        expect.withMessage("value after execute").that(mSetter.get(NAME)).isEqualTo(nvp);
        expect.withMessage("setter.getAll()").that(mSetter.getAll()).containsExactly(nvp);

        action.revert();
        expect.withMessage("value after revert ").that(mSetter.get(NAME)).isEqualTo(previousNvp);
        expect.withMessage("setter.getAll()").that(mSetter.getAll()).containsExactly(previousNvp);
    }

    @Test
    public void testExecuteTwice() throws Exception {
        var nvp = new NameValuePair(NAME, "Bond, James Bond");
        var action = new NameValuePairAction(mFakeLogger, mSetter, nvp);

        boolean result = action.execute();
        expect.withMessage("first call to execute()").that(result).isTrue();

        assertThrows(IllegalStateException.class, () -> action.execute());
    }

    @Test
    public void testRevertBeforeExecute() {
        var nvp = new NameValuePair(NAME, "Bond, James Bond");
        var action = new NameValuePairAction(mFakeLogger, mSetter, nvp);

        assertThrows(IllegalStateException.class, () -> action.revert());
    }

    @Test
    public void testOnRevertWhenNotExecuted() throws Exception {
        // This is kind of an "overkill" test, as onRevert() should not be called directly, but it
        // doesn't hurt to be sure...
        var nvp = new NameValuePair(NAME, "Bond, James Bond");
        var action = new NameValuePairAction(mFakeLogger, mSetter, nvp);
        mSetter.set(nvp);

        action.execute();

        assertThrows(IllegalStateException.class, () -> action.onRevertLocked());
    }

    @Test
    public void testOnReset() throws Exception {
        NameValuePair previousNvp = new NameValuePair(NAME, "Slim Shade");
        mSetter.set(previousNvp);
        var action =
                new NameValuePairAction(
                        mFakeLogger, mSetter, new NameValuePair(NAME, "Bond, James Bond"));
        expect.withMessage("getPreviousNvp() initially").that(action.getPreviousNvp()).isNull();

        action.execute();
        expect.withMessage("getPreviousNvp() after execute()")
                .that(action.getPreviousNvp())
                .isEqualTo(previousNvp);

        action.onResetLocked();

        expect.withMessage("getPreviousNvp() after reset()").that(action.getPreviousNvp()).isNull();
    }

    @Test
    public void testEqualsAndHashCode() {
        var nvp1 = new NameValuePair(NAME, "Bond, James Bond");
        NameValuePair nvp2 = new NameValuePair(NAME, "Slim Shade");
        var baseline = new NameValuePairAction(mFakeLogger, mSetter, nvp1);
        var different = new NameValuePairAction(mFakeLogger, mSetter, nvp2);
        var equal1 =
                new NameValuePairAction(new Logger(mFakeRealLogger, "whatever"), mSetter, nvp1);
        var equal2 =
                new NameValuePairAction(
                        mFakeLogger,
                        /* setter= */ nvp -> {
                            return null;
                        },
                        nvp1);

        var et = new EqualsTester(expect);

        et.expectObjectsAreNotEqual(baseline, different);
        et.expectObjectsAreEqual(baseline, equal1);
        et.expectObjectsAreEqual(baseline, equal2);
    }

    @Test
    public void testToString() throws Exception {
        NameValuePair previousNvp = new NameValuePair(NAME, "Slim Shade");
        mSetter.set(previousNvp);
        var nvp = new NameValuePair(NAME, "Bond, James Bond");
        var action = new NameValuePairAction(mFakeLogger, mSetter, nvp);

        expect.withMessage("toString() before execute")
                .that(action.toString())
                .isEqualTo("NameValuePairAction[nvp=" + nvp + ", previousNvp=null, set=false]");

        action.execute();
        expect.withMessage("toString() after execute")
                .that(action.toString())
                .isEqualTo(
                        "NameValuePairAction[nvp="
                                + nvp
                                + ", previousNvp="
                                + previousNvp
                                + ", set=true]");
    }
}
