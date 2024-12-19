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

package com.android.adservices;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import org.junit.Test;

@RequiresSdkLevelAtLeastT(reason = "ServiceBinder is not available on S")
public final class NoOpServiceBinderTest extends AdServicesUnitTestCase {

    // NOTE: cannot use a mServiceBinder as class would not initialize on S

    @Test
    public void testGetService() {
        var serviceBinder = new NoOpServiceBinder<>();
        assertWithMessage("getService()").that(serviceBinder.getService()).isNull();
    }

    @Test
    public void testUnbindFromService() {
        var serviceBinder = new NoOpServiceBinder<>();
        // TODO(b/336558146): add logic here (for example, add method to assert it's unbound)
        serviceBinder.unbindFromService();
    }
}
