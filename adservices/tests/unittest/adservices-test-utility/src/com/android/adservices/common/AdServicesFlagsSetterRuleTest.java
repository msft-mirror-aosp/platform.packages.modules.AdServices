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

import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.device.DeviceGateway;

import java.util.function.Consumer;

public final class AdServicesFlagsSetterRuleTest
        extends AbstractAdServicesFlagsSetterRuleTestCase<AdServicesFlagsSetterRule> {

    @Override
    protected AdServicesFlagsSetterRule newRule(
            DeviceGateway deviceGateway, Consumer<NameValuePair> flagsSetter) {
        return new AdServicesFlagsSetterRule(AndroidLogger.getInstance(), flagsSetter);
    }
}
