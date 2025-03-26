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

package com.android.adservices.service.signals.updateprocessors.putifnotpresent;

import static com.android.adservices.service.signals.updateprocessors.putifnotpresent.PutIfNotPresent.PUT_IF_NOT_PRESENT;

import com.android.adservices.service.signals.SignalUpdates.UpdateSchemaVersion;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorFactory;
import com.android.adservices.service.signals.updateprocessors.evictionpriority.EvictionPriorityHandlerFactory;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Locale;

/** Factory class for the {@link PutIfNotPresent} update processor. */
public class PutIfNotPresentFactory extends UpdateProcessorFactory {
    private static final List<Integer> SUPPORTED_VERSIONS =
            ImmutableList.of(UpdateSchemaVersion.V0);

    private final EvictionPriorityHandlerFactory mEvictionPriorityHandlerFactory;

    public PutIfNotPresentFactory(EvictionPriorityHandlerFactory evictionPriorityHandlerFactory) {
        mEvictionPriorityHandlerFactory = evictionPriorityHandlerFactory;
    }

    @Override
    public PutIfNotPresent getUpdateProcessor(@UpdateSchemaVersion int version) {
        return switch (version) {
            case UpdateSchemaVersion.V0 -> new PutIfNotPresentV0();
            case UpdateSchemaVersion.V1 ->
                    new PutIfNotPresentV1(mEvictionPriorityHandlerFactory.getHandler());
            default ->
                    throw new IllegalArgumentException(
                            String.format(
                                    Locale.ENGLISH,
                                    UNSUPPORTED_VERSION_ERROR_MESSAGE,
                                    PUT_IF_NOT_PRESENT,
                                    version,
                                    SUPPORTED_VERSIONS));
        };
    }
}
