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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.signals.SignalUpdates.UpdateSchemaVersion;
import com.android.adservices.service.signals.updateprocessors.append.Append;
import com.android.adservices.service.signals.updateprocessors.evictionpriority.EvictionPriorityHandlerFactory;
import com.android.adservices.service.signals.updateprocessors.evictionpriority.EvictionPriorityHandlerNoOpImpl;
import com.android.adservices.service.signals.updateprocessors.put.Put;
import com.android.adservices.service.signals.updateprocessors.putifnotpresent.PutIfNotPresent;
import com.android.adservices.service.signals.updateprocessors.remove.Remove;
import com.android.adservices.service.signals.updateprocessors.updateencoder.UpdateEncoder;
import com.android.adservices.service.signals.updateprocessors.updateproperties.UpdateProperties;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class UpdateProcessorSelectorTest extends AdServicesMockitoTestCase {
    @Mock private EvictionPriorityHandlerFactory mEvictionPriorityHandlerFactoryMock;
    public UpdateProcessorSelector mUpdateProcessorSelector;

    @Before
    public void setup() {
        when(mEvictionPriorityHandlerFactoryMock.getHandler())
                .thenReturn(new EvictionPriorityHandlerNoOpImpl());

        mUpdateProcessorSelector = new UpdateProcessorSelector(mEvictionPriorityHandlerFactoryMock);
    }

    @Test
    public void testInvalidKey() {
        assertThrows(
                "Expected exception",
                IllegalArgumentException.class,
                () ->
                        mUpdateProcessorSelector.getUpdateProcessor(
                                "Not a valid update type", UpdateSchemaVersion.V1));
    }

    @Test
    public void testInvalidVersion() {
        assertThrows(
                "Expected exception",
                IllegalArgumentException.class,
                () -> mUpdateProcessorSelector.getUpdateProcessor(APPEND, -1));
    }

    @Test
    public void testValidInputs() {
        expect.withMessage(APPEND)
                .that(mUpdateProcessorSelector.getUpdateProcessor(APPEND, UpdateSchemaVersion.V1))
                .isInstanceOf(Append.class);

        expect.withMessage(PUT)
                .that(mUpdateProcessorSelector.getUpdateProcessor(PUT, UpdateSchemaVersion.V1))
                .isInstanceOf(Put.class);

        expect.withMessage(PUT_IF_NOT_PRESENT)
                .that(
                        mUpdateProcessorSelector.getUpdateProcessor(
                                PUT_IF_NOT_PRESENT, UpdateSchemaVersion.V1))
                .isInstanceOf(PutIfNotPresent.class);

        expect.withMessage(REMOVE)
                .that(mUpdateProcessorSelector.getUpdateProcessor(REMOVE, UpdateSchemaVersion.V1))
                .isInstanceOf(Remove.class);

        expect.withMessage(UPDATE_ENCODER)
                .that(
                        mUpdateProcessorSelector.getUpdateProcessor(
                                UPDATE_ENCODER, UpdateSchemaVersion.V1))
                .isInstanceOf(UpdateEncoder.class);

        expect.withMessage(UPDATE_ENCODER)
                .that(
                        mUpdateProcessorSelector.getUpdateProcessor(
                                UPDATE_PROPERTIES, UpdateSchemaVersion.V1))
                .isInstanceOf(UpdateProperties.class);
    }
}
