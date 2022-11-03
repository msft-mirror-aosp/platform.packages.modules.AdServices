/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;

import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.testing.StaticMockFixture;
import com.android.modules.utils.testing.StaticMockFixtureRule;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.mockito.stubbing.Answer;

/**
 * Combines TestableDeviceConfig with other needed static mocks.
 */
public final class E2EMockStatic implements StaticMockFixture {

    private final E2ETest.PrivacyParamsProvider mPrivacyParams;

    public E2EMockStatic(E2ETest.PrivacyParamsProvider privacyParamsProvider) {
        mPrivacyParams = privacyParamsProvider;
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public StaticMockitoSessionBuilder setUpMockedClasses(
            StaticMockitoSessionBuilder sessionBuilder) {
        sessionBuilder.spyStatic(PrivacyParams.class);
        return sessionBuilder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUpMockBehaviors() {
        doAnswer((Answer<Integer>) invocation ->
                mPrivacyParams.getMaxAttributionPerRateLimitWindow())
                    .when(() -> PrivacyParams.getMaxAttributionPerRateLimitWindow());
        doAnswer((Answer<Integer>) invocation ->
                mPrivacyParams.getNavigationTriggerDataCardinality())
                    .when(() -> PrivacyParams.getNavigationTriggerDataCardinality());
        doAnswer((Answer<Integer>) invocation ->
                mPrivacyParams.getMaxDistinctEnrollmentsPerPublisherXDestinationInAttribution())
                    .when(() -> PrivacyParams
                            .getMaxDistinctEnrollmentsPerPublisherXDestinationInAttribution());
        doAnswer((Answer<Integer>) invocation ->
                mPrivacyParams.getMaxDistinctDestinationsPerPublisherXEnrollmentInActiveSource())
                    .when(() -> PrivacyParams
                            .getMaxDistinctDestinationsPerPublisherXEnrollmentInActiveSource());
        doAnswer((Answer<Integer>) invocation ->
                mPrivacyParams.getMaxDistinctEnrollmentsPerPublisherXDestinationInSource())
                    .when(() -> PrivacyParams
                            .getMaxDistinctEnrollmentsPerPublisherXDestinationInSource());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown() { }

    public static class E2EMockStaticRule extends StaticMockFixtureRule {
        public E2EMockStaticRule(E2ETest.PrivacyParamsProvider privacyParamsProvider) {
            super(TestableDeviceConfig::new, () -> new E2EMockStatic(privacyParamsProvider));
        }
    }
}
