/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.signals.updateprocessors;

import static com.android.adservices.service.signals.updateprocessors.append.Append.APPEND;
import static com.android.adservices.service.signals.updateprocessors.put.Put.PUT;
import static com.android.adservices.service.signals.updateprocessors.putifnotpresent.PutIfNotPresent.PUT_IF_NOT_PRESENT;
import static com.android.adservices.service.signals.updateprocessors.remove.Remove.REMOVE;
import static com.android.adservices.service.signals.updateprocessors.updateencoder.UpdateEncoder.UPDATE_ENCODER;
import static com.android.adservices.service.signals.updateprocessors.updateproperties.UpdateProperties.UPDATE_PROPERTIES;

import com.android.adservices.service.signals.SignalUpdates.UpdateSchemaVersion;
import com.android.adservices.service.signals.updateprocessors.append.AppendFactory;
import com.android.adservices.service.signals.updateprocessors.evictionpriority.EvictionPriorityHandlerFactory;
import com.android.adservices.service.signals.updateprocessors.put.PutFactory;
import com.android.adservices.service.signals.updateprocessors.putifnotpresent.PutIfNotPresentFactory;
import com.android.adservices.service.signals.updateprocessors.remove.RemoveFactory;
import com.android.adservices.service.signals.updateprocessors.updateencoder.UpdateEncoderFactory;
import com.android.adservices.service.signals.updateprocessors.updateproperties.UpdatePropertiesFactory;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/** Selector class for getting the appropriate update processor */
public class UpdateProcessorSelector {
    private final Map<String, UpdateProcessorFactory> mProcessorFactoryMap;

    public UpdateProcessorSelector(EvictionPriorityHandlerFactory evictionPriorityHandlerFactory) {
        mProcessorFactoryMap =
                ImmutableMap.of(
                        APPEND, new AppendFactory(evictionPriorityHandlerFactory),
                        PUT, new PutFactory(evictionPriorityHandlerFactory),
                        PUT_IF_NOT_PRESENT,
                                new PutIfNotPresentFactory(evictionPriorityHandlerFactory),
                        REMOVE, new RemoveFactory(),
                        UPDATE_ENCODER, new UpdateEncoderFactory(),
                        UPDATE_PROPERTIES,
                                new UpdatePropertiesFactory(evictionPriorityHandlerFactory));
    }

    /**
     * Get the appropriate update processor given a String taken from the signals update JSON top
     * level keys.
     *
     * @param key The JSON key representing the update type.
     * @return The appropriate update processor.
     */
    public UpdateProcessor getUpdateProcessor(String key, @UpdateSchemaVersion int version) {
        if (!mProcessorFactoryMap.containsKey(key)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid signal update command, valid commands are %s",
                            mProcessorFactoryMap.keySet()));
        }
        return mProcessorFactoryMap.get(key).getUpdateProcessor(version);
    }
}
