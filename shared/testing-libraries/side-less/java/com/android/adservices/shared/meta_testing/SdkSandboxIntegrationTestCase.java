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
package com.android.adservices.shared.meta_testing;

import static com.android.adservices.shared.testing.AndroidSdk.SC;
import static com.android.adservices.shared.testing.AndroidSdk.SC_V2;
import static com.android.adservices.shared.testing.SdkSandbox.State.DISABLED;
import static com.android.adservices.shared.testing.SdkSandbox.State.ENABLED;
import static com.android.adservices.shared.testing.SdkSandbox.State.UNSUPPORTED;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.shared.testing.SdkSandbox;
import com.android.adservices.shared.testing.SdkSandbox.State;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;

import org.junit.Test;

/**
 * Integration test for {@link SdkSandbox} implementations.
 *
 * <p>It executes the commands and checks the result; it's needed to make sure {@code cmd} and
 * {@code dumpsys} output are properly parsed.
 *
 * @param <T> implementation type
 */
public abstract class SdkSandboxIntegrationTestCase<T extends SdkSandbox>
        extends IntegrationTestCase {

    /** Creates a new instance of the {@link SdkSandbox} implementation being tested */
    protected abstract T newFixture();

    @Test
    @RequiresSdkRange(atLeast = SC, atMost = SC_V2)
    public final void testGet_TMinus() {
        T sandbox = newFixture();

        expect.withMessage("getState()").that(sandbox.getState()).isEqualTo(UNSUPPORTED);

        // SetState should be ignored
        sandbox.setState(ENABLED);
        expect.withMessage("getState()").that(sandbox.getState()).isEqualTo(UNSUPPORTED);
        sandbox.setState(DISABLED);
        expect.withMessage("getState()").that(sandbox.getState()).isEqualTo(UNSUPPORTED);
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public final void testGet_TPlus() {
        T sandbox = newFixture();

        // Gets the initial state so it's restored at the end
        State initialState = sandbox.getState();
        assertWithMessage("getState()").that(initialState).isNotNull();

        State flippedState = null;
        switch (initialState) {
            case DISABLED:
                flippedState = ENABLED;
                break;
            case ENABLED:
                flippedState = DISABLED;
                break;
            default:
                assertWithMessage("Invalid initial state: %s", initialState).fail();
        }

        try {
            sandbox.setState(flippedState);
            expect.withMessage("getState() after setState(%s)", flippedState)
                    .that(sandbox.getState())
                    .isEqualTo(flippedState);
        } finally {
            sandbox.setState(initialState);
        }
    }
}
