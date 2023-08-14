/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.mockito;

import com.android.modules.utils.testing.AbstractExtendedMockitoRule;
import com.android.modules.utils.testing.AbstractExtendedMockitoRule.AbstractBuilder;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.StaticMockFixture;

import java.util.function.Supplier;

/** Abstraction for {@link ExtendedMockitoRule}. */
public class AdServicesExtendedMockitoRule
        extends AbstractExtendedMockitoRule<
                AdServicesExtendedMockitoRule, AdServicesExtendedMockitoRule.Builder> {

    public AdServicesExtendedMockitoRule(Builder builder) {
        super(builder);
    }

    @SafeVarargs
    public AdServicesExtendedMockitoRule(Supplier<? extends StaticMockFixture>... suppliers) {
        super(new Builder().addStaticMockFixtures(suppliers));
    }

    public static final class Builder
            extends AbstractBuilder<AdServicesExtendedMockitoRule, Builder> {

        public Builder() {
            super();
        }

        public Builder(Object testClassInstance) {
            super(testClassInstance);
        }

        @Override
        public AdServicesExtendedMockitoRule build() {
            return new AdServicesExtendedMockitoRule(this);
        }
    }
}
