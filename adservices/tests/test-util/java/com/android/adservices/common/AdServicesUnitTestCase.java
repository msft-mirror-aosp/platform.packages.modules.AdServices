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

import org.junit.Rule;

/**
 * Base class for all unit tests.
 *
 * <p>Contains only the bare minimum functionality required by them, like custom JUnit rules.
 *
 * <p>In fact, this class "reserves" the first 11 rules (as defined by order 0-10), so subclasses
 * should start defining rules with {@code order = 11}.
 */
public abstract class AdServicesUnitTestCase extends AdServicesTestCase {

    @Rule(order = 5)
    public final ApplicationContextSingletonRule appContext =
            new ApplicationContextSingletonRule(/* restoreAfter= */ false);

}
