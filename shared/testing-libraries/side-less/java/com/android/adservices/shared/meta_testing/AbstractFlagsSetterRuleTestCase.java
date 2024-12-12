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

import static com.android.adservices.shared.meta_testing.TestAnnotations.setDoubleFlag;
import static com.android.adservices.shared.meta_testing.TestAnnotations.setFlagDisabled;
import static com.android.adservices.shared.meta_testing.TestAnnotations.setFlagEnabled;
import static com.android.adservices.shared.meta_testing.TestAnnotations.setFlagFalse;
import static com.android.adservices.shared.meta_testing.TestAnnotations.setFlagTrue;
import static com.android.adservices.shared.meta_testing.TestAnnotations.setFloatFlag;
import static com.android.adservices.shared.meta_testing.TestAnnotations.setIntegerFlag;
import static com.android.adservices.shared.meta_testing.TestAnnotations.setLongFlag;
import static com.android.adservices.shared.meta_testing.TestAnnotations.setStringArrayFlag;
import static com.android.adservices.shared.meta_testing.TestAnnotations.setStringArrayWithSeparatorFlag;
import static com.android.adservices.shared.meta_testing.TestAnnotations.setStringFlag;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassHasNoNothingAtAll;
import com.android.adservices.shared.testing.AbstractFlagsSetterRule;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.annotations.SetDoubleFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetFlagFalse;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.adservices.shared.testing.annotations.SetFloatFlag;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.annotations.SetLongFlag;
import com.android.adservices.shared.testing.annotations.SetStringArrayFlag;
import com.android.adservices.shared.testing.annotations.SetStringFlag;
import com.android.adservices.shared.testing.device.DeviceGateway;

import org.junit.Test;
import org.junit.runner.Description;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractFlagsSetterRuleTestCase<R extends AbstractFlagsSetterRule<R>>
        extends SharedSidelessTestCase {

    protected final FakeFlagsSetter mFakeFlagsSetter = new FakeFlagsSetter();
    protected final FakeDeviceGateway mFakeDeviceGateway = new FakeDeviceGateway();

    protected final SimpleStatement mTest = new SimpleStatement();
    protected final Description mTestDescription =
            Description.createTestDescription(AClassHasNoNothingAtAll.class, "butItHasATest");

    /** Gets a new concrete implementation of the rule. */
    protected abstract R newRule(DeviceGateway deviceGateway, Consumer<NameValuePair> flagsSetter);

    protected final R newRule() {
        var rule = newRule(mFakeDeviceGateway, mFakeFlagsSetter);
        assertWithMessage("rule returned by subclass").that(rule).isNotNull();
        return rule;
    }

    @Test
    public void testSetFlagMethods_nullNames() {
        R rule = newRule();

        expectNameCannotBeNull(() -> rule.setFlag(null, ""));
        expectNameCannotBeNull(
                () -> rule.setArrayFlagWithExplicitSeparator(null, "'", new String[] {""}));
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
                    rule.setFlag("iStringArray", "StringArray", "the name is Inside StringArray");
                    rule.setArrayFlagWithExplicitSeparator(
                            "iExplicitStringArray", "|",
                            "ExplicitStringArray", "the name is Inside ExplicitStringArray");
                    rule.setFlag("iBoolean", false);
                    rule.setFlag("iInteger", 42);
                    rule.setFlag("iLong", 4815162342L);
                    rule.setFlag("iFloat", 4.2F);
                    rule.setFlag("iDouble", 0.42D);
                });

        // Calls made before test - should be cached (and set during the test)
        rule.setFlag("bString", "String, the name is Before String");
        rule.setFlag("bStringArray", "StringArray", "the name is Before StringArray");
        rule.setArrayFlagWithExplicitSeparator(
                "bExplicitStringArray", "|",
                "ExplicitStringArray", "the name is Before ExplicitStringArray");
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
                                "bStringArray", "StringArray,the name is Before StringArray", ","),
                        new NameValuePair(
                                "bExplicitStringArray",
                                "ExplicitStringArray|the name is Before ExplicitStringArray",
                                "|"),
                        new NameValuePair("bBoolean", "true"),
                        new NameValuePair("bInteger", "108"),
                        new NameValuePair("bLong", "4223161584"),
                        new NameValuePair("bFloat", "10.8"),
                        new NameValuePair("bDouble", "1.08"))
                .inOrder();

        expect.withMessage("calls made during the test")
                .that(mFakeFlagsSetter.getAndResetCalls())
                .containsExactly(
                        new NameValuePair("iString", "String, the name is Inside String"),
                        new NameValuePair(
                                "iStringArray", "StringArray,the name is Inside StringArray", ","),
                        new NameValuePair(
                                "iExplicitStringArray",
                                "ExplicitStringArray|the name is Inside ExplicitStringArray",
                                "|"),
                        new NameValuePair("iBoolean", "false"),
                        new NameValuePair("iInteger", "42"),
                        new NameValuePair("iLong", "4815162342"),
                        new NameValuePair("iFloat", "4.2"),
                        new NameValuePair("iDouble", "0.42"))
                .inOrder();

        // Calls made after test - should be ignored
        rule.setFlag("aString", "String, the name is After String");
        rule.setFlag("aStringArray", "StringArray", "the name is After StringArray");
        rule.setArrayFlagWithExplicitSeparator(
                "aExplicitStringArray", "|",
                "ExplicitStringArray", "the name is After ExplicitStringArray");
        rule.setFlag("aBoolean", true);
        rule.setFlag("aInteger", 666);
        rule.setFlag("aLong", 66666666666666666L);
        rule.setFlag("aFloat", 66.6F);
        rule.setFlag("aDouble", 6.66);

        expect.withMessage("calls made after the test").that(mFakeFlagsSetter.getCalls()).isEmpty();
    }

    @Test
    public void testFlagsFromConfig() throws Throwable {
        R rule = newRule();
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));
        Map<String, String> argMap = new HashMap<String, String>();
        argMap.put("some_prefix:name_one", "value1");
        argMap.put("some_prefix:name_two", "value2");
        argMap.put("name_three", "value3");
        argMap.put("name_four", "value4");

        rule.setFlagsFromConfig(argMap, "some_prefix", ":");
        runTest(rule);

        expect.withMessage("cached calls")
                .that(cachedCalls)
                .containsExactly(
                        new NameValuePair("name_one", "value1"),
                        new NameValuePair("name_two", "value2"));
    }

    @Test
    public void testFlagsFromConfig_invalidParams() throws Throwable {
        R rule = newRule();
        Map<String, String> argMap = new HashMap<String, String>();

        Exception e =
                assertThrows(
                        NullPointerException.class,
                        () -> rule.setFlagsFromConfig(argMap, null, ":"));
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("prefix cannot be null");

        e =
                assertThrows(
                        NullPointerException.class,
                        () -> rule.setFlagsFromConfig(null, "prefix", ":"));
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("configArgs cannot be null");

        e =
                assertThrows(
                        NullPointerException.class,
                        () -> rule.setFlagsFromConfig(argMap, "prefix", null));
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("separator cannot be null");

        e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> rule.setFlagsFromConfig(argMap, "", ":"));
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("prefix or separator cannot be empty");

        e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> rule.setFlagsFromConfig(argMap, "prefix", "="));
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("separator cannot be one of (=,_)");
    }

    @Test
    public void testFlagsFromConfig_cornerCases() throws Throwable {
        R rule = newRule();
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));
        Map<String, String> argMap = new HashMap<String, String>();
        argMap.put("pre:name", "invalid wrong prefix");
        argMap.put("name", "invalid no prefix");
        argMap.put("prefix:name_one", "validvalue1");
        argMap.put("prefix:prefix:name", "invalid too many separators");
        argMap.put("prefix:name_two", "validvalue2");
        argMap.put("prefix:", "invalid empty named group");
        argMap.put("prefix_name", "invalid wrong separator");
        argMap.put("prefix", "invalid no separator");

        rule.setFlagsFromConfig(argMap, "prefix", ":");
        runTest(rule);

        expect.withMessage("cached calls")
                .that(cachedCalls)
                .containsExactly(
                        new NameValuePair("name_one", "validvalue1"),
                        new NameValuePair("name_two", "validvalue2"));
    }

    @Test
    public void testSetStringArray_invalidArgs() throws Throwable {
        R rule = newRule();

        Exception e =
                assertThrows(
                        NullPointerException.class, () -> rule.setFlag("DaName", (String[]) null));
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("values cannot be null");

        e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> rule.setFlag("DaName", new String[0]));
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("no values (name=DaName)");

        e = assertThrows(IllegalArgumentException.class, () -> rule.setFlag("DaName"));
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
        rule.setFlag("null", new String[] {null});
        rule.setFlag("null, not stirred", (String) null);
        rule.setFlag("nulls", null, null);
        rule.setFlag("empty", new String[] {""});
        rule.setFlag("empty, not stirred", "");
        rule.setFlag("empties", "", "");
        rule.setFlag("null and empty", new String[] {null, ""});
        rule.setFlag("mixed", "4", null, "2", "");
        rule.setFlag("one", new String[] {"is the loniest number"});
        rule.setFlag("one, not stirred", "is the loniest number");

        runTest(rule);

        expect.withMessage("cached calls")
                .that(cachedCalls)
                .containsExactly(
                        new NameValuePair("null", null),
                        new NameValuePair("null, not stirred", null),
                        new NameValuePair("nulls", "null,null", ","),
                        new NameValuePair("empty", ""),
                        new NameValuePair("empty, not stirred", ""),
                        new NameValuePair("empties", ",", ","),
                        new NameValuePair("null and empty", "null,", ","),
                        new NameValuePair("mixed", "4,null,2,", ","),
                        new NameValuePair("one", "is the loniest number"),
                        new NameValuePair("one, not stirred", "is the loniest number"));
    }

    @Test
    public void testSetArrayFlagWithExplicitSeparator_invalidArgs() throws Throwable {
        R rule = newRule();

        Exception e =
                assertThrows(
                        NullPointerException.class,
                        () ->
                                rule.setArrayFlagWithExplicitSeparator(
                                        "DaName", ",", /* values...= */ (String[]) null));
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("values cannot be null");

        e =
                assertThrows(
                        NullPointerException.class,
                        () ->
                                rule.setArrayFlagWithExplicitSeparator(
                                        "DaName", /* separator= */ null, new String[] {"D'OH"}));
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("separator cannot be null");

        e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> rule.setArrayFlagWithExplicitSeparator("DaName", "|", new String[0]));
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("no values (name=DaName)");

        e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> rule.setArrayFlagWithExplicitSeparator("DaName", "|"));
        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .isEqualTo("no values (name=DaName)");
    }

    @Test
    public void testSetArrayFlagWithExplicitSeparator_cornerCases() throws Throwable {
        R rule = newRule();
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));

        // TODO(b/303901926, 340882758): should probably ignore null and empty, but it's better to
        // make this change in a separate CL (just in case it breaks stuff), and AFTER this class
        // has tests for the annotation as well
        rule.setArrayFlagWithExplicitSeparator("null", "|", new String[] {null});
        rule.setArrayFlagWithExplicitSeparator("nulls", "|", new String[] {null, null});
        rule.setArrayFlagWithExplicitSeparator("empty", "|", new String[] {""});
        rule.setArrayFlagWithExplicitSeparator("empties", "|", new String[] {"", ""});
        rule.setArrayFlagWithExplicitSeparator("null and empty", "|", new String[] {null, ""});
        rule.setArrayFlagWithExplicitSeparator("mixed", "|", new String[] {"4", null, "2", ""});
        rule.setArrayFlagWithExplicitSeparator("one", "|", new String[] {"is the loniest number"});

        runTest(rule);

        expect.withMessage("cached calls")
                .that(cachedCalls)
                .containsExactly(
                        new NameValuePair("null", null),
                        new NameValuePair("nulls", "null|null", "|"),
                        new NameValuePair("empty", ""),
                        new NameValuePair("empties", "|", "|"),
                        new NameValuePair("null and empty", "null|", "|"),
                        new NameValuePair("mixed", "4|null|2|", "|"),
                        new NameValuePair("one", "is the loniest number"));
    }

    @Test
    public final void testSetFlagAnnotationsOnMethods() throws Throwable {
        R rule = newRule();
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));

        Description testClass =
                Description.createTestDescription(
                        AClassHasNoNothingAtAll.class,
                        "butItHasAMethodFullOfAnnotations",
                        setStringFlag("string1", "One, the name is String One"),
                        setStringFlag("string2", "Two, the name is String Two"),
                        setStringArrayFlag("strArray1", "One", "the name is StringArray One"),
                        setStringArrayWithSeparatorFlag(
                                "strArray2", "|", "Two", "the name is StringArray Two"),
                        setFlagTrue("true1"),
                        setFlagTrue("true2"),
                        setFlagFalse("false1"),
                        setFlagFalse("false2"),
                        setFlagEnabled("enabled1"),
                        setFlagEnabled("enabled2"),
                        setFlagDisabled("disabled1"),
                        setFlagDisabled("disabled2"),
                        setIntegerFlag("int1", 42),
                        setIntegerFlag("int2", 108),
                        setLongFlag("long1", 4815162342L),
                        setLongFlag("long2", 4223161584L),
                        setFloatFlag("float1", 0.42f),
                        setFloatFlag("float2", 42.0f),
                        setDoubleFlag("double1", 42.4815162342),
                        setDoubleFlag("double2", 108.4815162342));

        runTest(rule, testClass);

        assertTwoCallsForEachFlagType(cachedCalls);
    }

    @Test
    public final void testSetFlagAnnotationsOnClass() throws Throwable {
        R rule = newRule();
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));

        Description testClass =
                Description.createTestDescription(
                        AClassHasTwoAnnotationsOfEachType.class, "andHasNoMethods");

        runTest(rule, testClass);

        assertTwoCallsForEachFlagType(cachedCalls);
    }

    @Test
    public final void testSetFlagAnnotationsOnClassAndMethod() throws Throwable {
        R rule = newRule();
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));

        Description testClass =
                Description.createTestDescription(
                        AClassHasOneAnnotationOfEachType.class,
                        "andAMethodWithAnotherAnnotationOfEachType",
                        setStringFlag("string2", "Two, the name is String Two"),
                        setStringArrayWithSeparatorFlag(
                                "strArray2", "|", "Two", "the name is StringArray Two"),
                        setFlagTrue("true2"),
                        setFlagFalse("false2"),
                        setFlagEnabled("enabled2"),
                        setFlagDisabled("disabled2"),
                        setIntegerFlag("int2", 108),
                        setLongFlag("long2", 4223161584L),
                        setFloatFlag("float2", 42.0f),
                        setDoubleFlag("double2", 108.4815162342));

        runTest(rule, testClass);

        assertTwoCallsForEachFlagType(cachedCalls);
    }

    @Test
    public final void testSetFlagAnnotationsOnClassAndMethod_properOrder() throws Throwable {
        // Cannot check all annotations because the order is not guaranteed, so we're just using one
        // pair of any "random" type
        R rule = newRule();
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));

        Description testClass =
                Description.createTestDescription(
                        AClassHasOneAnnotationOnly.class,
                        "andAMethodWithAnotherAnnotationOfThatSame",
                        setStringFlag("string2", "Two, the name is String Two"));

        runTest(rule, testClass);

        expect.withMessage("cached calls")
                .that(cachedCalls)
                .containsExactly(
                        new NameValuePair("string1", "One, the name is String One"),
                        new NameValuePair("string2", "Two, the name is String Two"))
                .inOrder();
    }

    // TODO(b/340882758): add more tests like:
    // - Check what happens when test fail
    // - etc...

    protected final void runTest(R rule) throws Throwable {
        runTest(rule, mTestDescription);
    }

    protected final void runTest(R rule, Description testDescription) throws Throwable {
        rule.apply(mTest, testDescription).evaluate();

        mTest.assertEvaluated();
    }

    private void assertTwoCallsForEachFlagType(List<NameValuePair> cachedCalls) {
        expect.withMessage("cached calls")
                .that(cachedCalls)
                .containsExactly(
                        new NameValuePair("string1", "One, the name is String One"),
                        new NameValuePair("string2", "Two, the name is String Two"),
                        new NameValuePair("strArray1", "One,the name is StringArray One", ","),
                        new NameValuePair("strArray2", "Two|the name is StringArray Two", "|"),
                        new NameValuePair("true1", "true"),
                        new NameValuePair("true2", "true"),
                        new NameValuePair("false1", "false"),
                        new NameValuePair("false2", "false"),
                        new NameValuePair("enabled1", "true"),
                        new NameValuePair("enabled2", "true"),
                        new NameValuePair("disabled1", "false"),
                        new NameValuePair("disabled2", "false"),
                        new NameValuePair("int1", "42"),
                        new NameValuePair("int2", "108"),
                        new NameValuePair("long1", "4815162342"),
                        new NameValuePair("long2", "4223161584"),
                        new NameValuePair("float1", "0.42"),
                        new NameValuePair("float2", "42.0"),
                        new NameValuePair("double1", "42.4815162342"),
                        new NameValuePair("double2", "108.4815162342"));
    }

    @SetStringFlag(name = "string1", value = "One, the name is String One")
    @SetStringFlag(name = "string2", value = "Two, the name is String Two")
    @SetStringArrayFlag(
            name = "strArray1",
            value = {"One", "the name is StringArray One"})
    @SetStringArrayFlag(
            name = "strArray2",
            separator = "|",
            value = {"Two", "the name is StringArray Two"})
    @SetFlagTrue("true1")
    @SetFlagTrue("true2")
    @SetFlagFalse("false1")
    @SetFlagFalse("false2")
    @SetFlagEnabled("enabled1")
    @SetFlagEnabled("enabled2")
    @SetFlagDisabled("disabled1")
    @SetFlagDisabled("disabled2")
    @SetIntegerFlag(name = "int1", value = 42)
    @SetIntegerFlag(name = "int2", value = 108)
    @SetLongFlag(name = "long1", value = 4815162342L)
    @SetLongFlag(name = "long2", value = 4223161584L)
    @SetFloatFlag(name = "float1", value = 0.42f)
    @SetFloatFlag(name = "float2", value = 42.0f)
    @SetDoubleFlag(name = "double1", value = 42.4815162342)
    @SetDoubleFlag(name = "double2", value = 108.4815162342)
    private static final class AClassHasTwoAnnotationsOfEachType {}

    @SetStringFlag(name = "string1", value = "One, the name is String One")
    @SetStringArrayFlag(
            name = "strArray1",
            value = {"One", "the name is StringArray One"})
    @SetFlagTrue("true1")
    @SetFlagFalse("false1")
    @SetFlagEnabled("enabled1")
    @SetFlagDisabled("disabled1")
    @SetIntegerFlag(name = "int1", value = 42)
    @SetLongFlag(name = "long1", value = 4815162342L)
    @SetFloatFlag(name = "float1", value = 0.42f)
    @SetDoubleFlag(name = "double1", value = 42.4815162342)
    private static final class AClassHasOneAnnotationOfEachType {}

    @SetStringFlag(name = "string1", value = "One, the name is String One")
    private static final class AClassHasOneAnnotationOnly {}
}
