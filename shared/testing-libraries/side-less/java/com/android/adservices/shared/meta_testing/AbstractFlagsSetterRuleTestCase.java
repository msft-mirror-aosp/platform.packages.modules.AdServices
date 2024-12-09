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
package com.android.adservices.shared.meta_testing;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassHasNoNothingAtAll;
import com.android.adservices.shared.testing.AbstractFlagsSetterRule;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.device.DeviceGateway;

import org.junit.Test;
import org.junit.runner.Description;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class AbstractFlagsSetterRuleTestCase<R extends AbstractFlagsSetterRule<R>>
        extends SharedSidelessTestCase {

    private final FakeFlagsSetter mFakeFlagsSetter = new FakeFlagsSetter();
    private final FakeDeviceGateway mFakeDeviceGateway = new FakeDeviceGateway();

    private final SimpleStatement mTest = new SimpleStatement();
    private final Description mTestDescription =
            Description.createTestDescription(AClassHasNoNothingAtAll.class, "butItHasATest");

    /** Gets a new concrete implementation of the rule. */
    protected abstract R newRule(DeviceGateway deviceGateway, Consumer<NameValuePair> flagsSetter);

    private R newRule() {
        var rule = newRule(mFakeDeviceGateway, mFakeFlagsSetter);
        assertWithMessage("rule returned by subclass").that(rule).isNotNull();
        return rule;
    }

    @Test
    public void testSetFlagMethods_nullNames() {
        R rule = newRule();

        expectNameCannotBeNull(() -> rule.setFlag(null, ""));
        expectNameCannotBeNull(() -> rule.setFlag(null, new String[] {""}, "'"));
        expectNameCannotBeNull(() -> rule.setFlag(null, true));
        expectNameCannotBeNull(() -> rule.setFlag(null, 42));
        expectNameCannotBeNull(() -> rule.setFlag(null, 4815162342L));
        expectNameCannotBeNull(() -> rule.setFlag(null, 4.2F));
        expectNameCannotBeNull(() -> rule.setFlag(null, 0.42D));
    }

    private void expectNameCannotBeNull(Runnable r) {
        var e = assertThrows(NullPointerException.class, () -> r.run());
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("name cannot be null");
    }

    // TODO(b/340882758): should also test that values are restored AFTER the test, but it would
    // require mocking / faking the current DeviceConfig class, which is deprecated (in favor of the
    // one used by FlagsPreparerRule)
    @Test
    public void testSetFlagMethods() throws Throwable {
        R rule = newRule();
        List<NameValuePair> cachedCalls = new ArrayList<>();

        mTest.onEvaluate(
                () -> {
                    cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls());

                    // Calls made inside the test are prefixed with i
                    rule.setFlag("iString", "String, the name is Inside String");
                    rule.setFlag(
                            "iStringArray",
                            new String[] {"StringArray", "the name is Inside StringArray"},
                            ", ");
                    rule.setFlag("iBoolean", false);
                    rule.setFlag("iInteger", 42);
                    rule.setFlag("iLong", 4815162342L);
                    rule.setFlag("iFloat", 4.2F);
                    rule.setFlag("iDouble", 0.42D);
                });

        // Calls made before test - should be cached (and set during the test)
        rule.setFlag("bString", "String, the name is Before String");
        rule.setFlag(
                "bStringArray",
                new String[] {"StringArray", "the name is Before StringArray"},
                ", ");
        rule.setFlag("bBoolean", true);
        rule.setFlag("bInteger", 108);
        rule.setFlag("bLong", 4223161584L);
        rule.setFlag("bFloat", 10.8F);
        rule.setFlag("bDouble", 1.08D);

        runTest(rule);

        expect.withMessage("cached calls")
                .that(cachedCalls)
                .containsExactly(
                        new NameValuePair("bString", "String, the name is Before String"),
                        new NameValuePair(
                                "bStringArray", "StringArray, the name is Before StringArray", ","),
                        new NameValuePair("bBoolean", "true"),
                        new NameValuePair("bInteger", "108"),
                        new NameValuePair("bLong", "4223161584"),
                        new NameValuePair("bFloat", "10.8"),
                        new NameValuePair("bDouble", "1.08"));

        expect.withMessage("calls made during the test")
                .that(mFakeFlagsSetter.getAndResetCalls())
                .containsExactly(
                        new NameValuePair("iString", "String, the name is Inside String"),
                        new NameValuePair(
                                "iStringArray", "StringArray, the name is Inside StringArray", ","),
                        new NameValuePair("iBoolean", "false"),
                        new NameValuePair("iInteger", "42"),
                        new NameValuePair("iLong", "4815162342"),
                        new NameValuePair("iFloat", "4.2"),
                        new NameValuePair("iDouble", "0.42"));

        // Calls made after test - should be ignored
        rule.setFlag("aString", "String, the name is After String");
        rule.setFlag(
                "aStringArray",
                new String[] {"StringArray", "the name is After StringArray"},
                ", ");
        rule.setFlag("aBoolean", true);
        rule.setFlag("aInteger", 666);
        rule.setFlag("aLong", 66666666666666666L);
        rule.setFlag("aFloat", 66.6F);
        rule.setFlag("aDouble", 6.66);

        expect.withMessage("calls made after the test").that(mFakeFlagsSetter.getCalls()).isEmpty();
    }

    @Test
    public void testSetStringArray_invalidArgs() throws Throwable {
        R rule = newRule();

        Exception e =
                assertThrows(NullPointerException.class, () -> rule.setFlag("DaName", null, ","));
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("value cannot be null");

        e =
                assertThrows(
                        NullPointerException.class,
                        () -> rule.setFlag("DaName", new String[] {"D'OH"}, null));
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("separator cannot be null");

        e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> rule.setFlag("DaName", new String[0], ","));
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("no values (name=DaName)");
    }

    @Test
    public void testSetStringArray_cornerCases() throws Throwable {
        R rule = newRule();
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));

        // TODO(b/303901926, 340882758): should probably ignore null and empty, but it's better to
        // make this change in a separate CL (just in case it breaks stuff), and AFTER this class
        // has tests for the annotation as well
        rule.setFlag("null", new String[] {null}, ",");
        rule.setFlag("nulls", new String[] {null, null}, ",");
        rule.setFlag("empty", new String[] {""}, ",");
        rule.setFlag("empties", new String[] {"", ""}, ",");
        rule.setFlag("null and empty", new String[] {null, ""}, ",");
        rule.setFlag("mixed", new String[] {"4", null, "2", ""}, ",");
        rule.setFlag("one", new String[] {"is the loniest number"}, ",");

        runTest(rule);

        expect.withMessage("cached calls")
                .that(cachedCalls)
                .containsExactly(
                        new NameValuePair("null", null),
                        new NameValuePair("nulls", "null,null", ","),
                        new NameValuePair("empty", ""),
                        new NameValuePair("empties", ",", ","),
                        new NameValuePair("null and empty", "null,", ","),
                        new NameValuePair("mixed", "4,null,2,", ","),
                        new NameValuePair("one", "is the loniest number"));
    }

    // TODO(b/340882758): add more tests like:
    // - Annotation support
    // - Check what happens when test fail
    // - etc...

    private void runTest(R rule) throws Throwable {
        rule.apply(mTest, mTestDescription).evaluate();

        mTest.assertEvaluated();
    }
}
