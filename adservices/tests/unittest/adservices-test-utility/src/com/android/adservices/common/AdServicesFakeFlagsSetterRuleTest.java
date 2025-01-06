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

import static com.android.adservices.common.MissingFlagBehavior.THROWS_EXCEPTION;
import static com.android.adservices.common.MissingFlagBehavior.USES_EXPLICIT_DEFAULT;
import static com.android.adservices.common.MissingFlagBehavior.USES_JAVA_LANGUAGE_DEFAULT;
import static com.android.adservices.service.Flags.FLEDGE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE_COOLDOWN_SECONDS;
import static com.android.adservices.service.Flags.GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_REGISTER_WEB_TRIGGER_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;
import static com.android.adservices.service.Flags.UI_OTA_STRINGS_MANIFEST_FILE_URL;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class AdServicesFakeFlagsSetterRuleTest
        extends AdServicesFlagsSetterRuleForUnitTestsTestCase<AdServicesFakeFlagsSetterRule> {

    @Override
    protected AdServicesFakeFlagsSetterRule newRule() {
        return new AdServicesFakeFlagsSetterRule();
    }

    @Test
    public void testSetMissingFlagBehavior_null() {
        var rule = newRule();

        assertThrows(NullPointerException.class, () -> rule.setMissingFlagBehavior(null));
    }

    @Test
    public void testSetMissingFlagBehavior_defaultConstructor() {
        var rule = newRule();

        testSetMissingFlagBehaviorDefaultBehavior(rule, "by default");
    }

    @Test
    public void testSetMissingFlagBehavior_setUsesExplicitDefault() {
        var rule = newRule();
        var behavior = USES_EXPLICIT_DEFAULT;

        expect.withMessage("getMissingFlagBehavior()")
                .that(rule.getMissingFlagBehavior())
                .isEqualTo(behavior);
        var self = rule.setMissingFlagBehavior(behavior);
        expect.withMessage("result of setMissingFlagBehavior(%s)", behavior)
                .that(self)
                .isSameInstanceAs(rule);

        testSetMissingFlagBehaviorDefaultBehavior(rule, "by default");
    }

    private void testSetMissingFlagBehaviorDefaultBehavior(
            AdServicesFakeFlagsSetterRule rule, String when) {
        var flags = rule.getFlags();

        expect.withMessage("getMissingFlagBehavior()")
                .that(rule.getMissingFlagBehavior())
                .isEqualTo(USES_EXPLICIT_DEFAULT);

        // Need to check one flag of each primitive type (whose default value is different than
        // Java's)

        // boolean
        expect.withMessage("getGlobalKillSwitch()%s", when)
                .that(flags.getGlobalKillSwitch())
                .isEqualTo(GLOBAL_KILL_SWITCH);
        // String
        expect.withMessage("getUiOtaStringsManifestFileUrl()%s", when)
                .that(flags.getUiOtaStringsManifestFileUrl())
                .isEqualTo(UI_OTA_STRINGS_MANIFEST_FILE_URL);
        // int
        expect.withMessage("getTopicsPercentageForRandomTopic()%s", when)
                .that(flags.getTopicsPercentageForRandomTopic())
                .isEqualTo(TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
        // long
        expect.withMessage("getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds()%s", when)
                .that(flags.getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds())
                .isEqualTo(FLEDGE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE_COOLDOWN_SECONDS);
        // float
        expect.withMessage("getMeasurementRegisterWebTriggerRequestPermitsPerSecond()%s", when)
                .that(flags.getMeasurementRegisterWebTriggerRequestPermitsPerSecond())
                .isEqualTo(MEASUREMENT_REGISTER_WEB_TRIGGER_REQUEST_PERMITS_PER_SECOND);
    }

    @Test
    public void testSetMissingFlagBehavior_setUsesJavaLanguageDefault() {
        var rule = newRule();
        var flags = rule.getFlags();
        var behavior = USES_JAVA_LANGUAGE_DEFAULT;

        var self = rule.setMissingFlagBehavior(behavior);
        expect.withMessage("result of setMissingFlagBehavior(%s)", behavior)
                .that(self)
                .isSameInstanceAs(rule);
        expect.withMessage("getMissingFlagBehavior()")
                .that(rule.getMissingFlagBehavior())
                .isEqualTo(behavior);

        expect.withMessage("getGlobalKillSwitch() after calling setMockingMode(true)")
                .that(flags.getGlobalKillSwitch())
                .isFalse();
        expect.withMessage("getUiOtaStringsManifestFileUrl() after calling setMockingMode(true)")
                .that(flags.getUiOtaStringsManifestFileUrl())
                .isNull();
        expect.withMessage("getTopicsPercentageForRandomTopic() after calling setMockingMode(true)")
                .that(flags.getTopicsPercentageForRandomTopic())
                .isEqualTo(0);
        expect.withMessage(
                        "getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds() after calling"
                                + " setMockingMode(true)")
                .that(flags.getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds())
                .isEqualTo(0L);
        expect.withMessage(
                        "getMeasurementRegisterWebTriggerRequestPermitsPerSecond() after calling"
                                + " setMockingMode(true)")
                .that(flags.getMeasurementRegisterWebTriggerRequestPermitsPerSecond())
                .isEqualTo(0f);

        rule.setMissingFlagBehavior(USES_EXPLICIT_DEFAULT);
        testSetMissingFlagBehaviorDefaultBehavior(
                rule, "after calling setMockingMode(USES_EXPLICIT_DEFAULT)");
    }

    @Test
    public void testSetMissingFlagBehavior_setThrowsException() {
        var rule = newRule();
        var flags = rule.getFlags();
        var behavior = THROWS_EXCEPTION;

        var self = rule.setMissingFlagBehavior(behavior);
        expect.withMessage("result of setMissingFlagBehavior(%s)", behavior)
                .that(self)
                .isSameInstanceAs(rule);
        expect.withMessage("getMissingFlagBehavior()")
                .that(rule.getMissingFlagBehavior())
                .isEqualTo(behavior);

        assertThrows(IllegalStateException.class, () -> flags.getGlobalKillSwitch());
        assertThrows(IllegalStateException.class, () -> flags.getUiOtaStringsManifestFileUrl());
        assertThrows(IllegalStateException.class, () -> flags.getTopicsPercentageForRandomTopic());
        assertThrows(
                IllegalStateException.class,
                () -> flags.getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds());
        assertThrows(
                IllegalStateException.class,
                () -> flags.getMeasurementRegisterWebTriggerRequestPermitsPerSecond());

        rule.setMissingFlagBehavior(USES_EXPLICIT_DEFAULT);
        testSetMissingFlagBehaviorDefaultBehavior(
                rule, "after calling setMockingMode(USES_EXPLICIT_DEFAULT)");
    }
}
