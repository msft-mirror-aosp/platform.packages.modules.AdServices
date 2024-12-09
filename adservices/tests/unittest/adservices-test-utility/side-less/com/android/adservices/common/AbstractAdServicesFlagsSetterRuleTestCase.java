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

import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_BACK_COMPAT;
import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MDD_BACKGROUND_TASK_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MSMT_API_APP_ALLOW_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_PPAPI_APP_ALLOW_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.AbstractFlagsSetterRuleTestCase;
import com.android.adservices.shared.testing.NameValuePair;

import org.junit.Test;

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
                        new NameValuePair(KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH, "true"));
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
                        new NameValuePair(key, "4,8,15,16,23,42", ","));
    }
}
