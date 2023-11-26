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
package com.android.adservices.common;

import com.android.adservices.shared.testing.common.ApplicationContextSingletonRule;

import com.google.common.truth.Expect;

import org.junit.Rule;

/**
 * Base class for all unit tests.
 *
 * <p>Contains only the bare minimum functionality required by them, like custom JUnit rules.
 *
 * <p>In fact, this class "reserves" the first 10 rules (as defined by order), so subclasses should
 * start defining rules with {@code order = 11} (although for now they can use {@code order = 0} for
 * {@code SdkLevelSupportRule}, as that rule cannot be defined here yet.
 */
public abstract class AdServicesUnitTestCase {

    protected final String mTag = getClass().getSimpleName();

    // TODO(b/285014040, 295269584: add rule with order 0 for SdkLevelSupportRule.forAtLeastS(),
    // once that rule supports per-class annotation

    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Rule(order = 5)
    public final ApplicationContextSingletonRule appContext = new ApplicationContextSingletonRule();

    @Rule(order = 6)
    public final ProcessLifeguardRule processLifeguard = new ProcessLifeguardRule();

    @Rule(order = 7)
    public final Expect expect = Expect.create();
}
