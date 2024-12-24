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

import static com.android.adservices.service.FakeFlagsFactory.SetDefaultFledgeFlags;

import com.android.adservices.service.Flags;
import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassHasNoNothingAtAll;
import com.android.adservices.shared.meta_testing.SimpleStatement;

import org.junit.Test;
import org.junit.runner.Description;

import java.util.Objects;
import java.util.function.BiConsumer;

// TODO(b/373446366): ideally it should extend AbstractAdServicesFlagsSetterRuleTestCase, but it
// would cause failures on other tests due to how the rules are constructed (using a Consumer
// flagsSetter). We might reconsider as we refactor AbstractFlagsSetterRule to support the new
// DeviceConfig abstractions.
/** Base test class for {@link AdServicesFlagsSetterRuleForUnitTests} implementations. */
abstract class AdServicesFlagsSetterRuleForUnitTestsTestCase<
                R extends AdServicesFlagsSetterRuleForUnitTests<R, F>, F extends Flags>
        extends AdServicesUnitTestCase {

    /** Creates a new instance of the rule. */
    protected abstract R newRule();

    @Test
    public final void testSetDefaultFledgeFlagsMethod() throws Throwable {
        onTest(
                (rule, flags) -> {
                    R returnedRule = rule.setDefaultFledgeFlags();
                    expect.withMessage("setFlagsForTests()")
                            .that(returnedRule)
                            .isSameInstanceAs(rule);
                    assertDefaultFledgeFlags(flags);
                });
    }

    @Test
    public final void testSetDefaultFledgeFlagsAnnotation() throws Throwable {
        Description description =
                Description.createTestDescription(
                        AClassSetsAllDefaultFledgeTags.class, "butItHasATest");
        onTest(
                description,
                (rule, flags) -> {
                    assertDefaultFledgeFlags(flags);
                });
    }

    private void assertDefaultFledgeFlags(F flags) {
        // TODO(b/384798806): pass R as well and assert size of changed flags is 16
        expect.withMessage("getAdSelectionBiddingTimeoutPerCaMs()")
                .that(flags.getAdSelectionBiddingTimeoutPerCaMs())
                .isEqualTo(10_000);
        expect.withMessage("getAdSelectionScoringTimeoutMs()")
                .that(flags.getAdSelectionScoringTimeoutMs())
                .isEqualTo(10_000);
        expect.withMessage("getAdSelectionOverallTimeoutMs()")
                .that(flags.getAdSelectionOverallTimeoutMs())
                .isEqualTo(600_000);
        expect.withMessage("getFledgeRegisterAdBeaconEnabled()")
                .that(flags.getFledgeRegisterAdBeaconEnabled())
                .isTrue();
        expect.withMessage("getDisableFledgeEnrollmentCheck()")
                .that(flags.getDisableFledgeEnrollmentCheck())
                .isTrue();
        expect.withMessage("getFledgeFetchCustomAudienceEnabled()")
                .that(flags.getFledgeFetchCustomAudienceEnabled())
                .isTrue();
        expect.withMessage("getFledgeScheduleCustomAudienceUpdateEnabled()")
                .that(flags.getFledgeScheduleCustomAudienceUpdateEnabled())
                .isTrue();
        expect.withMessage("getFledgeScheduleCustomAudienceMinDelayMinsOverride()")
                .that(flags.getFledgeScheduleCustomAudienceMinDelayMinsOverride())
                .isEqualTo(-100);
        expect.withMessage("getEnableLoggedTopic()").that(flags.getEnableLoggedTopic()).isTrue();
        expect.withMessage("getEnableDatabaseSchemaVersion8()")
                .that(flags.getEnableDatabaseSchemaVersion8())
                .isTrue();
        expect.withMessage("getFledgeAuctionServerEnabled()")
                .that(flags.getFledgeAuctionServerEnabled())
                .isTrue();
        expect.withMessage("getEnableLogggetFledgeEventLevelDebugReportingEnablededTopic()")
                .that(flags.getFledgeEventLevelDebugReportingEnabled())
                .isTrue();
        expect.withMessage("getFledgeBeaconReportingMetricsEnabled()")
                .that(flags.getFledgeBeaconReportingMetricsEnabled())
                .isTrue();
        expect.withMessage("getFledgeAppPackageNameLoggingEnabled()")
                .that(flags.getFledgeAppPackageNameLoggingEnabled())
                .isTrue();
        expect.withMessage("getFledgeAuctionServerKeyFetchMetricsEnabled()")
                .that(flags.getFledgeAuctionServerKeyFetchMetricsEnabled())
                .isTrue();
        expect.withMessage("getPasExtendedMetricsEnabled()")
                .that(flags.getPasExtendedMetricsEnabled())
                .isTrue();
    }

    // NOTE: we don't need to test all setters (it would be impractical), so we're testing the
    // global kill switch one as is the "mother of all flags".
    @Test
    public final void testSetGlobalKillSwitch() throws Throwable {
        onTest(
                (rule, flags) -> {
                    rule.setGlobalKillSwitch(true);
                    expect.withMessage("getGlobalKillSwitch()")
                            .that(flags.getGlobalKillSwitch())
                            .isTrue();

                    rule.setGlobalKillSwitch(false);
                    expect.withMessage("getGlobalKillSwitch()")
                            .that(flags.getGlobalKillSwitch())
                            .isFalse();
                });
    }

    /**
     * Evaluates a rule inside a test.
     *
     * @param consumer consumer of flags instantiated by {@link #newFlags()} and a rule instantiated
     *     by {@link #newRule(Flags)} (passing that flags).
     */
    protected void onTest(BiConsumer<R, F> consumer) throws Throwable {
        onTest(
                Description.createTestDescription(AClassHasNoNothingAtAll.class, "butItHasATest"),
                consumer);
    }

    /**
     * Evaluates a rule inside a test.
     *
     * @param description test description
     * @param consumer consumer of flags instantiated by {@link #newFlags()} and a rule instantiated
     *     by {@link #newRule(Flags)} (passing that flags).
     */
    protected void onTest(Description description, BiConsumer<R, F> consumer) throws Throwable {
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(consumer, "consumer cannot be null");

        R rule = newRule();
        if (rule == null) {
            throw new IllegalStateException("newRule() returned null");
        }
        F flags = rule.getFlags();
        if (flags == null) {
            throw new IllegalStateException("rule.getFlags() returned null");
        }

        SimpleStatement test = new SimpleStatement();

        test.onEvaluate(() -> consumer.accept(rule, flags));
        rule.apply(test, description).evaluate();
        test.assertEvaluated();

    }

    @SetDefaultFledgeFlags
    private static final class AClassSetsAllDefaultFledgeTags {}
}
