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

package com.android.adservices.shared.testing.flags;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.FakeDeviceConfig;
import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.device.DeviceConfig;

import org.junit.Test;

public final class DeviceConfigActionTest extends SharedSidelessTestCase {

    private final FakeDeviceConfig mFakeDeviceConfig = new FakeDeviceConfig();

    @Test
    public void testConstructor_null() {
        assertThrows(
                NullPointerException.class,
                () -> new ConcreteDeviceConfigAction(mLog, /* deviceConfig= */ null));
        assertThrows(
                NullPointerException.class,
                () -> new ConcreteDeviceConfigAction(/* logger= */ null, mFakeDeviceConfig));
    }

    @Test
    public void testExecuteTwice() throws Exception {
        ConcreteDeviceConfigAction action = new ConcreteDeviceConfigAction(mLog, mFakeDeviceConfig);

        boolean result = action.execute();
        expect.withMessage("first call to execute()").that(result).isTrue();

        assertThrows(IllegalStateException.class, () -> action.execute());
    }

    @Test
    public void testRevertBeforeExecute() {
        ConcreteDeviceConfigAction action = new ConcreteDeviceConfigAction(mLog, mFakeDeviceConfig);
        assertThrows(IllegalStateException.class, () -> action.revert());
    }

    private static final class ConcreteDeviceConfigAction extends DeviceConfigAction {
        ConcreteDeviceConfigAction(Logger logger, DeviceConfig deviceConfig) {
            super(logger, deviceConfig);
        }

        @Override
        public boolean onExecuteLocked() {
            return true;
        }

        @Override
        public void onRevertLocked() {}

        @Override
        protected void onResetLocked() {}
    }
}
