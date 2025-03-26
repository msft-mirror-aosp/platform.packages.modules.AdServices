/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.service.signals.updateprocessors.evictionpriority;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import org.junit.Test;

@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class EvictionPriorityHandlerFactoryTest extends AdServicesUnitTestCase {

    @Test
    public void testGetHandler_prioritizedEvictionEnabled() {
        EvictionPriorityHandlerFactory evictionPriorityHandlerFactory =
                new EvictionPriorityHandlerFactory(/* enablePrioritizedEviction= */ true);

        expect.withMessage("Returned handler subclass")
                .that(evictionPriorityHandlerFactory.getHandler())
                .isInstanceOf(EvictionPriorityHandlerImpl.class);
    }

    @Test
    public void testGetHandler_prioritizedEvictionDisabled() {
        EvictionPriorityHandlerFactory evictionPriorityHandlerFactory =
                new EvictionPriorityHandlerFactory(/* enablePrioritizedEviction= */ false);

        expect.withMessage("Returned handler subclass")
                .that(evictionPriorityHandlerFactory.getHandler())
                .isInstanceOf(EvictionPriorityHandlerNoOpImpl.class);
    }
}
