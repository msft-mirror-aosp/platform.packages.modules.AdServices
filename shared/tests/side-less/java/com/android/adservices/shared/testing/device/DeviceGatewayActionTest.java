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

package com.android.adservices.shared.testing.device;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.FakeDeviceGateway;
import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.Logger;

import org.junit.Test;

public final class DeviceGatewayActionTest extends SharedSidelessTestCase {

    private final FakeDeviceGateway mFakeDeviceGateway = new FakeDeviceGateway();

    @Test
    public void testConstructor_null() {
        assertThrows(
                NullPointerException.class,
                () -> new ConcreteDeviceGatewayAction(mLog, /* deviceGateway= */ null));
        assertThrows(
                NullPointerException.class,
                () -> new ConcreteDeviceGatewayAction(/* logger= */ null, mFakeDeviceGateway));
    }

    @Test
    public void testExecuteTwice() throws Exception {
        ConcreteDeviceGatewayAction action =
                new ConcreteDeviceGatewayAction(mLog, mFakeDeviceGateway);

        boolean result = action.execute();
        expect.withMessage("first call to execute()").that(result).isTrue();

        assertThrows(IllegalStateException.class, () -> action.execute());
    }

    @Test
    public void testRevertBeforeExecute() {
        ConcreteDeviceGatewayAction action =
                new ConcreteDeviceGatewayAction(mLog, mFakeDeviceGateway);
        assertThrows(IllegalStateException.class, () -> action.revert());
    }

    private static final class ConcreteDeviceGatewayAction extends DeviceGatewayAction {
        ConcreteDeviceGatewayAction(Logger logger, DeviceGateway deviceGateway) {
            super(logger, deviceGateway);
        }

        @Override
        public boolean onExecuteLocked() {
            return true;
        }

        @Override
        public void onRevertLocked() {}

        @Override
        public void onResetLocked() {}
    }
}
