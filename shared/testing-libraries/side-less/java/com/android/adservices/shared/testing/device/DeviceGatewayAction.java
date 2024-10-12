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

import com.android.adservices.shared.testing.AbstractAction;
import com.android.adservices.shared.testing.Logger;

import java.util.Objects;

/**
 * Base class for actions that need a {@code DeviceGateway}.
 *
 * <p>Typically extended by actions that will run a shell command.
 */
public abstract class DeviceGatewayAction extends AbstractAction {

    protected final DeviceGateway mDeviceGateway;

    protected DeviceGatewayAction(Logger logger, DeviceGateway deviceGateway) {
        super(logger);
        mDeviceGateway = Objects.requireNonNull(deviceGateway, "deviceGateway cannot be null");
    }
}
