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
package com.android.adservices.shared.testing.concurrency;

import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

abstract class IBinderSyncCallbackTestCase<CB extends IBinderSyncCallback & FreezableToString>
        extends SyncCallbackTestCase<CB> {

    // TODO(b/342639109): make sure it's the right order and/or move to superclass
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAnyLevel();

    protected IBinderSyncCallbackTestCase() {
        super(AndroidLogger.getInstance());
    }

    @Test
    public final void testAsBinder() {
        var cb = newCallback(mDefaultSettings);
        expect.withMessage("asBinder()").that(cb.asBinder()).isNull();
    }
}
