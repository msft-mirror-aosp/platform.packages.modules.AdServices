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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.truth.Expect;

import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.lang.annotation.Annotation;

// TODO(b/315542995): provide host-side implementation
/**
 * Test case for {@link AbstractAdServicesDeviceSupportedRule} implementations.
 *
 * <p>By default, it uses a {@link
 * AbstractAdServicesDeviceSupportedRuleTest.FakeAdServicesDeviceSupportedRule bogus rule} so it can
 * be run by IDEs.
 */
public class AbstractAdServicesDeviceSupportedRuleTest {

    private final Logger.RealLogger mRealLogger = StandardStreamsLogger.getInstance();
    private final SimpleStatement mBaseStatement = new SimpleStatement();

    private FakeAdServicesDeviceSupportedRule mAdServicesDeviceSupportedRule;

    @Mock private AbstractDeviceSupportHelper mAbstractDeviceSupportHelper;

    @Rule public final Expect expect = Expect.create();
    @Rule public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Before
    public void setup() {
        mAdServicesDeviceSupportedRule =
                new FakeAdServicesDeviceSupportedRule(mRealLogger, mAbstractDeviceSupportHelper);
    }

    @Test
    public void testIsAdServicesSupported_supported() throws Exception {
        when(mAbstractDeviceSupportHelper.isDeviceSupported()).thenReturn(true);

        expect.that(mAdServicesDeviceSupportedRule.isAdServicesSupportedOnDevice()).isTrue();
    }

    @Test
    public void testIsAdServicesSupportedOnDeviceTest_notSupported() throws Exception {
        when(mAbstractDeviceSupportHelper.isDeviceSupported()).thenReturn(false);

        expect.that(mAdServicesDeviceSupportedRule.isAdServicesSupportedOnDevice()).isFalse();
    }

    @Test
    public void testIsLowRamDevice_returnsTrue() throws Exception {
        mockIsLowRamDevice(true);

        expect.that(mAdServicesDeviceSupportedRule.isLowRamDevice()).isTrue();
    }

    @Test
    public void testIsLowRamDevice_returnsFalse() throws Exception {
        mockIsLowRamDevice(false);

        expect.that(mAdServicesDeviceSupportedRule.isLowRamDevice()).isFalse();
    }

    @Test
    public void testAnnotatedWithLowRam_deviceNotLowRam() {
        mockIsLowRamDevice(false);
        Description description = createTestMethod(TestAnnotations.requiresLowRamDevice());

        AssumptionViolatedException e =
                assertThrows(
                        AssumptionViolatedException.class,
                        () ->
                                mAdServicesDeviceSupportedRule
                                        .apply(mBaseStatement, description)
                                        .evaluate());

        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .contains(
                        AbstractAdServicesDeviceSupportedRule
                                .REQUIRES_LOW_RAM_ASSUMPTION_FAILED_ERROR_MESSAGE);
        mBaseStatement.assertNotEvaluated();
    }

    @Test
    public void testAnnotatedWithLowRam_deviceLowRam() throws Throwable {
        mockIsLowRamDevice(true);
        Description description = createTestMethod(TestAnnotations.requiresLowRamDevice());

        mAdServicesDeviceSupportedRule.apply(mBaseStatement, description).evaluate();

        mBaseStatement.assertEvaluated();
    }

    /** Bogus implementation of {@link AbstractAdServicesDeviceSupportedRule}. */
    private static final class FakeAdServicesDeviceSupportedRule
            extends AbstractAdServicesDeviceSupportedRule {
        /** Default constructor. */
        private FakeAdServicesDeviceSupportedRule(
                Logger.RealLogger logger, AbstractDeviceSupportHelper deviceSupportHelper) {
            super(logger, deviceSupportHelper);
        }
    }

    private Description createTestMethod(Annotation... annotations) {
        return Description.createTestDescription(
                AbstractAdServicesDeviceSupportedRuleTest.class, "method_name", annotations);
    }

    private void mockIsLowRamDevice(boolean isLowRam) {
        when(mAbstractDeviceSupportHelper.isLowRamDevice()).thenReturn(isLowRam);
    }
}
