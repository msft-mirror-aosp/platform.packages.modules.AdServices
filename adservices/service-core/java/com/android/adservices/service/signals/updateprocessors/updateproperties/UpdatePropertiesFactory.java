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

package com.android.adservices.service.signals.updateprocessors.updateproperties;

import static com.android.adservices.service.signals.updateprocessors.updateproperties.UpdateProperties.UPDATE_PROPERTIES;

import com.android.adservices.service.signals.SignalUpdates;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessor;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorFactory;
import com.android.adservices.service.signals.updateprocessors.evictionpriority.EvictionPriorityHandlerFactory;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Locale;

/** Factory class for the {@link UpdateProperties} update processor. */
public class UpdatePropertiesFactory extends UpdateProcessorFactory {
    private static final List<Integer> SUPPORTED_VERSIONS =
            ImmutableList.of(SignalUpdates.UpdateSchemaVersion.V1);

    private final EvictionPriorityHandlerFactory mEvictionPriorityHandlerFactory;

    public UpdatePropertiesFactory(EvictionPriorityHandlerFactory evictionPriorityHandlerFactory) {
        mEvictionPriorityHandlerFactory = evictionPriorityHandlerFactory;
    }

    @Override
    public UpdateProcessor getUpdateProcessor(int version) {
        return switch (version) {
            case SignalUpdates.UpdateSchemaVersion.V0 -> throwVersionNotSupportedException(version);
            case SignalUpdates.UpdateSchemaVersion.V1 ->
                    new UpdatePropertiesV1(mEvictionPriorityHandlerFactory.getHandler());
            default -> throwVersionNotSupportedException(version);
        };
    }

    private UpdateProcessor throwVersionNotSupportedException(int version) {
        throw new IllegalArgumentException(
                String.format(
                        Locale.ENGLISH,
                        UNSUPPORTED_VERSION_ERROR_MESSAGE,
                        UPDATE_PROPERTIES,
                        version,
                        SUPPORTED_VERSIONS));
    }
}
