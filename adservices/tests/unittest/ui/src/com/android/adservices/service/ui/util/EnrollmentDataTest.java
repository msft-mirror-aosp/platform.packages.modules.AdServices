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

package com.android.adservices.service.ui.util;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesModuleState;
import android.adservices.common.AdServicesModuleUserChoice;
import android.adservices.common.Module;

import org.junit.Test;

public class EnrollmentDataTest {
    @Test
    public void moduleStateTest() {
        EnrollmentData data = new EnrollmentData();
        AdServicesModuleState moduleStateMeasurement =
                new AdServicesModuleState.Builder()
                        .setModule(Module.MEASUREMENT)
                        .setModuleState(AdServicesModuleState.MODULE_STATE_ENABLED)
                        .build();
        AdServicesModuleState moduleStatePa =
                new AdServicesModuleState.Builder()
                        .setModule(Module.PA)
                        .setModuleState(AdServicesModuleState.MODULE_STATE_DISABLED)
                        .build();
        AdServicesModuleState moduleStateTopic =
                new AdServicesModuleState.Builder()
                        .setModule(Module.TOPIC)
                        .setModuleState(AdServicesModuleState.MODULE_STATE_UNKNOWN)
                        .build();
        data.putModuleState(moduleStateMeasurement);
        data.putModuleState(moduleStatePa);
        data.putModuleState(moduleStateTopic);

        assertThat(data.getModuleState(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_ENABLED);
        assertThat(data.getModuleState(Module.PA))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_DISABLED);
        assertThat(data.getModuleState(Module.TOPIC))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_UNKNOWN);
        assertThat(data.getModuleState(Module.ADID))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_UNKNOWN);
        assertThat(data.getModuleState(Module.PAS))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_UNKNOWN);
        assertThat(data.getModuleState(Module.ODP))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_UNKNOWN);
    }

    @Test
    public void userChoiceTest() {
        EnrollmentData data = new EnrollmentData();
        AdServicesModuleUserChoice userChoiceAdid =
                new AdServicesModuleUserChoice.Builder()
                        .setModule(Module.ADID)
                        .setUserChoice(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN)
                        .build();
        AdServicesModuleUserChoice userChoicePas =
                new AdServicesModuleUserChoice.Builder()
                        .setModule(Module.PAS)
                        .setUserChoice(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT)
                        .build();
        AdServicesModuleUserChoice userChoiceOdp =
                new AdServicesModuleUserChoice.Builder()
                        .setModule(Module.ODP)
                        .setUserChoice(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN)
                        .build();
        data.putUserChoice(userChoiceAdid);
        data.putUserChoice(userChoicePas);
        data.putUserChoice(userChoiceOdp);
        data.putUserChoice(Module.PA, AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);

        assertThat(data.getUserChoice(Module.ADID))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        assertThat(data.getUserChoice(Module.PAS))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        assertThat(data.getUserChoice(Module.ODP))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
        assertThat(data.getUserChoice(Module.PA))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        assertThat(data.getUserChoice(Module.TOPIC))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
        assertThat(data.getUserChoice(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
    }

    @Test
    public void serializationTest() {
        EnrollmentData data = EnrollmentData.deserialize("");
        AdServicesModuleUserChoice userChoiceMeasurement =
                new AdServicesModuleUserChoice.Builder()
                        .setModule(Module.MEASUREMENT)
                        .setUserChoice(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN)
                        .build();
        AdServicesModuleUserChoice userChoicePa =
                new AdServicesModuleUserChoice.Builder()
                        .setModule(Module.PA)
                        .setUserChoice(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT)
                        .build();
        AdServicesModuleUserChoice userChoiceTopic =
                new AdServicesModuleUserChoice.Builder()
                        .setModule(Module.TOPIC)
                        .setUserChoice(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN)
                        .build();
        data.putUserChoice(userChoiceMeasurement);
        data.putUserChoice(userChoicePa);
        data.putUserChoice(userChoiceTopic);

        AdServicesModuleState moduleStateMeasurement =
                new AdServicesModuleState.Builder()
                        .setModule(Module.MEASUREMENT)
                        .setModuleState(AdServicesModuleState.MODULE_STATE_ENABLED)
                        .build();
        AdServicesModuleState moduleStatePa =
                new AdServicesModuleState.Builder()
                        .setModule(Module.PA)
                        .setModuleState(AdServicesModuleState.MODULE_STATE_DISABLED)
                        .build();
        AdServicesModuleState moduleStateTopic =
                new AdServicesModuleState.Builder()
                        .setModule(Module.TOPIC)
                        .setModuleState(AdServicesModuleState.MODULE_STATE_UNKNOWN)
                        .build();
        data.putModuleState(moduleStateMeasurement);
        data.putModuleState(moduleStatePa);
        data.putModuleState(moduleStateTopic);

        assertThat(data.getModuleState(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_ENABLED);
        assertThat(data.getModuleState(Module.PA))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_DISABLED);
        assertThat(data.getModuleState(Module.TOPIC))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_UNKNOWN);

        assertThat(data.getUserChoice(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        assertThat(data.getUserChoice(Module.PA))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        assertThat(data.getUserChoice(Module.TOPIC))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);

        String result = data.serialize();

        EnrollmentData newData = EnrollmentData.deserialize(result);

        assertThat(newData.getModuleState(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_ENABLED);
        assertThat(newData.getModuleState(Module.PA))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_DISABLED);
        assertThat(newData.getModuleState(Module.TOPIC))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_UNKNOWN);

        assertThat(newData.getUserChoice(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        assertThat(newData.getUserChoice(Module.PA))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        assertThat(newData.getUserChoice(Module.TOPIC))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
    }

    @Test
    public void serializationBase64Test() {
        EnrollmentData data = EnrollmentData.deserializeFromBase64("");
        AdServicesModuleUserChoice userChoiceMeasurement =
                new AdServicesModuleUserChoice.Builder()
                        .setModule(Module.MEASUREMENT)
                        .setUserChoice(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN)
                        .build();
        AdServicesModuleUserChoice userChoicePa =
                new AdServicesModuleUserChoice.Builder()
                        .setModule(Module.PA)
                        .setUserChoice(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT)
                        .build();
        AdServicesModuleUserChoice userChoiceTopic =
                new AdServicesModuleUserChoice.Builder()
                        .setModule(Module.TOPIC)
                        .setUserChoice(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN)
                        .build();
        data.putUserChoice(userChoiceMeasurement);
        data.putUserChoice(userChoicePa);
        data.putUserChoice(userChoiceTopic);

        AdServicesModuleState moduleStateMeasurement =
                new AdServicesModuleState.Builder()
                        .setModule(Module.MEASUREMENT)
                        .setModuleState(AdServicesModuleState.MODULE_STATE_ENABLED)
                        .build();
        AdServicesModuleState moduleStatePa =
                new AdServicesModuleState.Builder()
                        .setModule(Module.PA)
                        .setModuleState(AdServicesModuleState.MODULE_STATE_DISABLED)
                        .build();
        AdServicesModuleState moduleStateTopic =
                new AdServicesModuleState.Builder()
                        .setModule(Module.TOPIC)
                        .setModuleState(AdServicesModuleState.MODULE_STATE_UNKNOWN)
                        .build();
        data.putModuleState(moduleStateMeasurement);
        data.putModuleState(moduleStatePa);
        data.putModuleState(moduleStateTopic);

        assertThat(data.getModuleState(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_ENABLED);
        assertThat(data.getModuleState(Module.PA))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_DISABLED);
        assertThat(data.getModuleState(Module.TOPIC))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_UNKNOWN);

        assertThat(data.getUserChoice(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        assertThat(data.getUserChoice(Module.PA))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        assertThat(data.getUserChoice(Module.TOPIC))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);

        String result = EnrollmentData.serializeBase64(data);

        EnrollmentData newData = EnrollmentData.deserializeFromBase64(result);

        assertThat(newData.getModuleState(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_ENABLED);
        assertThat(newData.getModuleState(Module.PA))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_DISABLED);
        assertThat(newData.getModuleState(Module.TOPIC))
                .isEqualTo(AdServicesModuleState.MODULE_STATE_UNKNOWN);

        assertThat(newData.getUserChoice(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        assertThat(newData.getUserChoice(Module.PA))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        assertThat(newData.getUserChoice(Module.TOPIC))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
    }
}
