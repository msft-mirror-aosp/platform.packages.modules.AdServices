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

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.adservices.service.signals.updateprocessors.append.AppendV0;
import com.android.adservices.service.signals.updateprocessors.put.PutV0;
import com.android.adservices.service.signals.updateprocessors.putifnotpresent.PutIfNotPresentV0;
import com.android.adservices.service.signals.updateprocessors.remove.RemoveV0;
import com.android.adservices.service.signals.updateprocessors.updateencoder.UpdateEncoderV0;
import com.android.adservices.shared.testing.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class UpdateProcessorSelectorTest {

    public UpdateProcessorSelector mUpdateProcessorSelector = new UpdateProcessorSelector();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testInvalid() {
        assertThrows(
                "Selector should throw an exception when given an invalid processor name",
                IllegalArgumentException.class,
                () -> mUpdateProcessorSelector.getUpdateProcessor("Not a valid command"));
    }

    @Test
    public void testValidCommands() {
        UpdateProcessor[] processors = {
            new AppendV0(),
            new PutV0(),
            new PutIfNotPresentV0(),
            new RemoveV0(),
            new UpdateEncoderV0()
        };
        for (UpdateProcessor processor : processors) {
            UpdateProcessor fetchedProcessor =
                    mUpdateProcessorSelector.getUpdateProcessor(processor.getName());
            assertTrue(processor.getClass().isInstance(fetchedProcessor));
        }
    }
}
