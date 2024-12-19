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
package com.android.adservices.common;

import static com.android.adservices.service.FlagsConstants.KEY_ADID_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_BACK_COMPAT;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SELECT_ADS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MDD_BACKGROUND_TASK_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MSMT_API_APP_ALLOW_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_APP_ALLOW_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_PPAPI_APP_ALLOW_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST;

import static org.junit.Assert.assertThrows;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.EnableAllApis;
import com.android.adservices.common.annotations.SetMsmtApiAppAllowList;
import com.android.adservices.common.annotations.SetMsmtWebContextClientAppAllowList;
import com.android.adservices.common.annotations.SetPpapiAppAllowList;
import com.android.adservices.shared.meta_testing.AbstractFlagsSetterRuleTestCase;
import com.android.adservices.shared.testing.NameValuePair;

import org.junit.Test;
import org.junit.runner.Description;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Test case for {@link AbstractAdServicesFlagsSetterRule} implementations. */
public abstract class AbstractAdServicesFlagsSetterRuleTestCase<
                R extends AbstractAdServicesFlagsSetterRule<R>>
        extends AbstractFlagsSetterRuleTestCase<R> {

    @Test
    public final void testKillSwitchSetters() throws Throwable {
        R rule = newRule();
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));

        rule.setGlobalKillSwitch(true);
        rule.setAdServicesEnabled(true);
        rule.setEnableBackCompat(true);
        rule.setMddBackgroundTaskKillSwitch(true);
        rule.setMeasurementRollbackDeletionAppSearchKillSwitch(true);
        rule.setTopicsKillSwitch(true);
        rule.setTopicsOnDeviceClassifierKillSwitch(true);

        runTest(rule);

        expect.withMessage("cached calls")
                .that(cachedCalls)
                .containsExactly(
                        new NameValuePair(KEY_GLOBAL_KILL_SWITCH, "true"),
                        new NameValuePair(KEY_ADSERVICES_ENABLED, "true"),
                        new NameValuePair(KEY_ENABLE_BACK_COMPAT, "true"),
                        new NameValuePair(KEY_MDD_BACKGROUND_TASK_KILL_SWITCH, "true"),
                        new NameValuePair(
                                KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH, "true"),
                        new NameValuePair(KEY_TOPICS_KILL_SWITCH, "true"),
                        new NameValuePair(KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH, "true"))
                .inOrder();
    }

    @Test
    public final void testSetPpApiAppAllowList() throws Throwable {
        appAllowListFlagTest(
                KEY_PPAPI_APP_ALLOW_LIST, (rule, apps) -> rule.setPpapiAppAllowList(apps));
    }

    @Test
    public final void testSetMsmtApiAppAllowList() throws Throwable {
        appAllowListFlagTest(
                KEY_MSMT_API_APP_ALLOW_LIST, (rule, apps) -> rule.setMsmtApiAppAllowList(apps));
    }

    @Test
    public final void testSetMsmtWebContextClientAllowList() throws Throwable {
        appAllowListFlagTest(
                KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST,
                (rule, apps) -> rule.setMsmtWebContextClientAllowList(apps));
    }

    @Test
    public final void testSetPasAppAllowList() throws Throwable {
        appAllowListThatAcceptsDefaultPackageFlagTest(
                KEY_PAS_APP_ALLOW_LIST, (rule, apps) -> rule.setPasAppAllowList(apps));
    }

    private void appAllowListFlagTest(String key, BiConsumer<R, String[]> setter) throws Throwable {
        R rule = newRule();

        // Try invalid args first...
        assertThrows(NullPointerException.class, () -> setter.accept(rule, /* apps= */ null));
        assertThrows(IllegalArgumentException.class, () -> setter.accept(rule, new String[0]));

        // ...then valid
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));

        setter.accept(rule, new String[] {""});
        setter.accept(rule, new String[] {"", ""});
        setter.accept(rule, new String[] {null});
        setter.accept(rule, new String[] {null, null});
        setter.accept(rule, new String[] {"mixed of null", null, "and empty", "", "."});
        setter.accept(rule, new String[] {"One Is The Loniest Number"});
        setter.accept(rule, new String[] {"4", "8", "15", "16", "23", "42"});

        runTest(rule);

        expect.withMessage("cached calls")
                .that(cachedCalls)
                .containsExactly(
                        new NameValuePair(key, ""),
                        new NameValuePair(key, ","),
                        new NameValuePair(key, null),
                        new NameValuePair(key, "null,null", ","),
                        new NameValuePair(key, "mixed of null,null,and empty,,.", ","),
                        new NameValuePair(key, "One Is The Loniest Number"),
                        new NameValuePair(key, "4,8,15,16,23,42", ","))
                .inOrder();
    }

    private void appAllowListThatAcceptsDefaultPackageFlagTest(
            String key, BiConsumer<R, String[]> setter) throws Throwable {
        R rule = newRule();

        // Try invalid args first...
        assertThrows(NullPointerException.class, () -> setter.accept(rule, /* apps= */ null));

        // ...then valid
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));

        setter.accept(rule, new String[0]);
        setter.accept(rule, new String[] {""});
        setter.accept(rule, new String[] {"", ""});
        setter.accept(rule, new String[] {null});
        setter.accept(rule, new String[] {null, null});
        setter.accept(rule, new String[] {"mixed of null", null, "and empty", "", "."});
        setter.accept(rule, new String[] {"One Is The Loniest Number"});
        setter.accept(rule, new String[] {"4", "8", "15", "16", "23", "42"});

        runTest(rule);

        expect.withMessage("cached calls")
                .that(cachedCalls)
                .containsExactly(
                        new NameValuePair(
                                key,
                                InstrumentationRegistry.getInstrumentation()
                                        .getContext()
                                        .getPackageName()),
                        new NameValuePair(key, ""),
                        new NameValuePair(key, ","),
                        new NameValuePair(key, null),
                        new NameValuePair(key, "null,null", ","),
                        new NameValuePair(key, "mixed of null,null,and empty,,.", ","),
                        new NameValuePair(key, "One Is The Loniest Number"),
                        new NameValuePair(key, "4,8,15,16,23,42", ","))
                .inOrder();
    }

    @Test
    public final void testEnableAllApis() throws Throwable {
        R rule = newRule();
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));

        rule.enableAllApis();
        runTest(rule);

        assertEnableAllApis(cachedCalls);
    }

    @Test
    public void testEnableAllApisAnnotation() throws Throwable {
        R rule = newRule();
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));
        Description testClass =
                Description.createTestDescription(AClassEnableAllApis.class, "andHasNoMethods");

        runTest(rule, testClass);

        assertEnableAllApis(cachedCalls);
    }

    private void assertEnableAllApis(List<NameValuePair> cachedCalls) {
        expect.withMessage("cached calls")
                .that(cachedCalls)
                .containsExactly(
                        new NameValuePair(KEY_GLOBAL_KILL_SWITCH, "false"),
                        new NameValuePair(KEY_TOPICS_KILL_SWITCH, "false"),
                        new NameValuePair(KEY_ADID_KILL_SWITCH, "false"),
                        new NameValuePair(KEY_MEASUREMENT_KILL_SWITCH, "false"),
                        new NameValuePair(KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH, "false"),
                        new NameValuePair(KEY_FLEDGE_SELECT_ADS_KILL_SWITCH, "false"),
                        new NameValuePair(
                                KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED, "true"))
                .inOrder();
    }

    // NOTE: there's no need to test annotations on both superclass and method - it would be an
    // overkill, as that logic is handled by AbstractFlagsSetterRule itself (and already tested)

    /**
     * Test most annotations that set flags - except those that would set same flags (for example
     * {@link EnableAllApis} is not included as it sets flags set by others - hence it's tested on
     * its own method)
     *
     * @throws Throwable
     */
    @Test
    public final void testMostCustomSetFlagAnnotations() throws Throwable {
        R rule = newRule();
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));

        Description testClass =
                Description.createTestDescription(
                        AClassHasMostCustomSetFlagAnnotations.class, "andHasNoMethods");

        runTest(rule, testClass);

        String testPackage =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageName();

        expect.withMessage("cached calls")
                .that(cachedCalls)
                .containsExactly(
                        new NameValuePair(KEY_GLOBAL_KILL_SWITCH, "false"),
                        new NameValuePair(KEY_PPAPI_APP_ALLOW_LIST, testPackage),
                        new NameValuePair(KEY_MSMT_API_APP_ALLOW_LIST, testPackage),
                        new NameValuePair(KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST, testPackage));
    }

    @Test
    public final void testAllowListAnnotationsWithCustomValues() throws Throwable {
        R rule = newRule();
        List<NameValuePair> cachedCalls = new ArrayList<>();
        mTest.onEvaluate(() -> cachedCalls.addAll(mFakeFlagsSetter.getAndResetCalls()));

        Description testClass =
                Description.createTestDescription(
                        AClassHasAllowListAnnotationsWithCustomValues.class, "andHasNoMethods");

        runTest(rule, testClass);

        expect.withMessage("cached calls")
                .that(cachedCalls)
                .containsExactly(
                        new NameValuePair(KEY_PPAPI_APP_ALLOW_LIST, "4,8,15,16,23,42", ","),
                        new NameValuePair(KEY_MSMT_API_APP_ALLOW_LIST, "42"),
                        new NameValuePair(KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST, "108,666", ","));
    }

    // TODO(b/340882758): add test for remaining stuff like:
    // - CompatMode setter / annotation (would require specific tests for each Android release)
    // - factory methods
    // - logcat tags

    @DisableGlobalKillSwitch
    @SetPpapiAppAllowList
    @SetMsmtApiAppAllowList
    @SetMsmtWebContextClientAppAllowList
    private static final class AClassHasMostCustomSetFlagAnnotations {}

    @SetPpapiAppAllowList({"4", "8", "15", "16", "23", "42"})
    @SetMsmtApiAppAllowList("42")
    @SetMsmtWebContextClientAppAllowList({"108", "666"})
    private static final class AClassHasAllowListAnnotationsWithCustomValues {}

    @EnableAllApis
    private static final class AClassEnableAllApis {}
}
