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
package com.android.adservices.flags;

import static com.android.adservices.service.Flags.FLEDGE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE_COOLDOWN_SECONDS;
import static com.android.adservices.service.Flags.GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_REGISTER_WEB_TRIGGER_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;
import static com.android.adservices.service.Flags.UI_OTA_STRINGS_MANIFEST_FILE_URL;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.shared.testing.flags.MissingFlagBehavior.THROWS_EXCEPTION;
import static com.android.adservices.shared.testing.flags.MissingFlagBehavior.USES_EXPLICIT_DEFAULT;
import static com.android.adservices.shared.testing.flags.MissingFlagBehavior.USES_JAVA_LANGUAGE_DEFAULT;

import static org.junit.Assert.assertThrows;

import com.android.adservices.service.FlagsConstants;
import com.android.adservices.shared.testing.flags.TestableFlagsBackend;

import org.junit.Test;

import java.util.Locale;

public final class AdServicesFakeFlagsSetterRuleTest
        extends AdServicesFlagsSetterRuleForUnitTestsTestCase<
                AdServicesFakeFlagsSetterRule, FakeFlags> {

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
    public void testGetFlagsSnapshot_beforeTest() {
        var rule = newRule();

        assertThrows(IllegalStateException.class, () -> rule.getFlagsSnapshot());
    }

    @Test
    public void smokeTest() throws Throwable {
        // This test checks some "crucial" flags; for example, non-final getters on RawFlags
        onTest(
                (rule, flags) -> {
                    long defaultTopicsEpochJobPeriodMs = TOPICS_EPOCH_JOB_PERIOD_MS;
                    expect.withMessage("getTopicsEpochJobPeriodMs() by default")
                            .that(flags.getTopicsEpochJobPeriodMs())
                            .isEqualTo(defaultTopicsEpochJobPeriodMs);

                    long newTopicsEpochJobPeriodMs = -defaultTopicsEpochJobPeriodMs;
                    rule.setFlag(KEY_TOPICS_EPOCH_JOB_PERIOD_MS, newTopicsEpochJobPeriodMs);
                    expect.withMessage("getTopicsEpochJobPeriodMs() after setting it")
                            .that(flags.getTopicsEpochJobPeriodMs())
                            .isEqualTo(newTopicsEpochJobPeriodMs);
                });
    }

    @Test
    public void testGetFlagsSnapshot() throws Throwable {
        onTest(
                (rule, flags) -> {
                    rule.setFlag(FlagsConstants.KEY_AD_ID_CACHE_TTL_MS, 4815162342L);
                    expect.withMessage("flags.getAdIdCacheTtlMs() after setting it")
                            .that(flags.getAdIdCacheTtlMs())
                            .isEqualTo(4815162342L);

                    var snapshot = rule.getFlagsSnapshot();
                    expect.withMessage("clonedFlags.getAdIdCacheTtlMs() after cloning")
                            .that(snapshot.getAdIdCacheTtlMs())
                            .isEqualTo(4815162342L);

                    rule.setFlag(FlagsConstants.KEY_AD_ID_CACHE_TTL_MS, 108);
                    expect.withMessage("flags.getAdIdCacheTtlMs() after updating it")
                            .that(flags.getAdIdCacheTtlMs())
                            .isEqualTo(108);
                    expect.withMessage("clonedFlags.getAdIdCacheTtlMs() after updating source")
                            .that(snapshot.getAdIdCacheTtlMs())
                            .isEqualTo(4815162342L);

                    assertThrows(
                            UnsupportedOperationException.class,
                            () ->
                                    snapshot.getBackend()
                                            .setFlag(FlagsConstants.KEY_AD_ID_CACHE_TTL_MS, "108"));
                    expect.withMessage("clonedFlags.getAdIdCacheTtlMs() after trying to change it")
                            .that(snapshot.getAdIdCacheTtlMs())
                            .isEqualTo(4815162342L);
                });
    }

    @Test
    public void testToString() throws Throwable {
        onTest(
                (rule, flags) -> {
                    String id = flags.getId();
                    String prefix = "FakeFlags#" + id + "{";
                    expect.withMessage("toString() right away")
                            .that(flags.toString())
                            .isEqualTo(prefix + "empty}");

                    rule.setFlag("dude", "sweet");
                    expect.withMessage("toString() after setting 1 flag")
                            .that(flags.toString())
                            .isEqualTo(prefix + "dude=sweet}");
                    rule.setFlag("sweet", "lord");
                    expect.withMessage("toString() after setting 2 flags")
                            .that(flags.toString())
                            .isEqualTo(prefix + "dude=sweet, sweet=lord}");
                    // make sure they're sorted
                    rule.setFlag("a flag", "has a name");
                    expect.withMessage("toString() after setting 3 flags")
                            .that(flags.toString())
                            .isEqualTo(prefix + "a flag=has a name, dude=sweet, sweet=lord}");
                    // update a value
                    rule.setFlag("dude", "SWEEET");
                    expect.withMessage("toString() after updating value of 1st flag")
                            .that(flags.toString())
                            .isEqualTo(prefix + "a flag=has a name, dude=SWEEET, sweet=lord}");
                });
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

    @Test
    public void testOnGetFlagThrows() {
        var rule = newRule();
        var flags = rule.getFlags();
        var reason = "Because I said so";

        rule.onGetFlagThrows(KEY_TOPICS_EPOCH_JOB_PERIOD_MS, reason);

        var thrown =
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> flags.getTopicsEpochJobPeriodMs());
        expect.withMessage("message exception")
                .that(thrown)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                Locale.ENGLISH,
                                TestableFlagsBackend.UNSUPPORTED_TEMPLATE,
                                KEY_TOPICS_EPOCH_JOB_PERIOD_MS,
                                reason));
    }
}
