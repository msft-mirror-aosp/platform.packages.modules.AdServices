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

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesModuleUserChoice;
import android.adservices.common.Module;

import org.junit.Test;

public class EnrollmentDataTest {
    @Test
    public void moduleStateTest() {
        EnrollmentData data = new EnrollmentData();

        data.putModuleState(Module.MEASUREMENT, AdServicesCommonManager.MODULE_STATE_ENABLED);
        data.putModuleState(
                Module.PROTECTED_AUDIENCE, AdServicesCommonManager.MODULE_STATE_DISABLED);
        data.putModuleState(Module.TOPICS, AdServicesCommonManager.MODULE_STATE_UNKNOWN);

        assertThat(data.getModuleState(Module.MEASUREMENT))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_ENABLED);
        assertThat(data.getModuleState(Module.PROTECTED_AUDIENCE))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_DISABLED);
        assertThat(data.getModuleState(Module.TOPICS))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_UNKNOWN);
        assertThat(data.getModuleState(Module.ADID))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_UNKNOWN);
        assertThat(data.getModuleState(Module.PROTECTED_APP_SIGNALS))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_UNKNOWN);
        assertThat(data.getModuleState(Module.ON_DEVICE_PERSONALIZATION))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_UNKNOWN);
    }

    @Test
    public void userChoiceTest() {
        EnrollmentData data = new EnrollmentData();
        AdServicesModuleUserChoice userChoiceAdid =
                new AdServicesModuleUserChoice(
                        Module.ADID, AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        AdServicesModuleUserChoice userChoicePas =
                new AdServicesModuleUserChoice(
                        Module.PROTECTED_APP_SIGNALS,
                        AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        AdServicesModuleUserChoice userChoiceOdp =
                new AdServicesModuleUserChoice(
                        Module.ON_DEVICE_PERSONALIZATION,
                        AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
        data.putUserChoice(userChoiceAdid);
        data.putUserChoice(userChoicePas);
        data.putUserChoice(userChoiceOdp);
        data.putUserChoice(
                Module.PROTECTED_AUDIENCE, AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);

        assertThat(data.getUserChoice(Module.ADID))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        assertThat(data.getUserChoice(Module.PROTECTED_APP_SIGNALS))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        assertThat(data.getUserChoice(Module.ON_DEVICE_PERSONALIZATION))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
        assertThat(data.getUserChoice(Module.PROTECTED_AUDIENCE))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        assertThat(data.getUserChoice(Module.TOPICS))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
        assertThat(data.getUserChoice(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
    }

    @Test
    public void serializationTest() {
        EnrollmentData data = EnrollmentData.deserialize("");
        AdServicesModuleUserChoice userChoiceMeasurement =
                new AdServicesModuleUserChoice(
                        Module.MEASUREMENT, AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        AdServicesModuleUserChoice userChoicePa =
                new AdServicesModuleUserChoice(
                        Module.PROTECTED_AUDIENCE,
                        AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        AdServicesModuleUserChoice userChoiceTopic =
                new AdServicesModuleUserChoice(
                        Module.TOPICS, AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
        data.putUserChoice(userChoiceMeasurement);
        data.putUserChoice(userChoicePa);
        data.putUserChoice(userChoiceTopic);

        data.putModuleState(Module.MEASUREMENT, AdServicesCommonManager.MODULE_STATE_ENABLED);
        data.putModuleState(
                Module.PROTECTED_AUDIENCE, AdServicesCommonManager.MODULE_STATE_DISABLED);
        data.putModuleState(Module.TOPICS, AdServicesCommonManager.MODULE_STATE_UNKNOWN);

        assertThat(data.getModuleState(Module.MEASUREMENT))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_ENABLED);
        assertThat(data.getModuleState(Module.PROTECTED_AUDIENCE))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_DISABLED);
        assertThat(data.getModuleState(Module.TOPICS))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_UNKNOWN);

        assertThat(data.getUserChoice(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        assertThat(data.getUserChoice(Module.PROTECTED_AUDIENCE))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        assertThat(data.getUserChoice(Module.TOPICS))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);

        String result = EnrollmentData.serialize(data);

        EnrollmentData newData = EnrollmentData.deserialize(result);

        assertThat(newData.getModuleState(Module.MEASUREMENT))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_ENABLED);
        assertThat(newData.getModuleState(Module.PROTECTED_AUDIENCE))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_DISABLED);
        assertThat(newData.getModuleState(Module.TOPICS))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_UNKNOWN);

        assertThat(newData.getUserChoice(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        assertThat(newData.getUserChoice(Module.PROTECTED_AUDIENCE))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        assertThat(newData.getUserChoice(Module.TOPICS))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
    }

    @Test
    public void serializationBase64Test() {
        EnrollmentData data = EnrollmentData.deserialize("");
        AdServicesModuleUserChoice userChoiceMeasurement =
                new AdServicesModuleUserChoice(
                        Module.MEASUREMENT, AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        AdServicesModuleUserChoice userChoicePa =
                new AdServicesModuleUserChoice(
                        Module.PROTECTED_AUDIENCE,
                        AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        AdServicesModuleUserChoice userChoiceTopic =
                new AdServicesModuleUserChoice(
                        Module.TOPICS, AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
        data.putUserChoice(userChoiceMeasurement);
        data.putUserChoice(userChoicePa);
        data.putUserChoice(userChoiceTopic);

        data.putModuleState(Module.MEASUREMENT, AdServicesCommonManager.MODULE_STATE_ENABLED);
        data.putModuleState(
                Module.PROTECTED_AUDIENCE, AdServicesCommonManager.MODULE_STATE_DISABLED);
        data.putModuleState(Module.TOPICS, AdServicesCommonManager.MODULE_STATE_UNKNOWN);

        assertThat(data.getModuleState(Module.MEASUREMENT))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_ENABLED);
        assertThat(data.getModuleState(Module.PROTECTED_AUDIENCE))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_DISABLED);
        assertThat(data.getModuleState(Module.TOPICS))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_UNKNOWN);

        assertThat(data.getUserChoice(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        assertThat(data.getUserChoice(Module.PROTECTED_AUDIENCE))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        assertThat(data.getUserChoice(Module.TOPICS))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);

        String result = EnrollmentData.serialize(data);

        EnrollmentData newData = EnrollmentData.deserialize(result);

        assertThat(newData.getModuleState(Module.MEASUREMENT))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_ENABLED);
        assertThat(newData.getModuleState(Module.PROTECTED_AUDIENCE))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_DISABLED);
        assertThat(newData.getModuleState(Module.TOPICS))
                .isEqualTo(AdServicesCommonManager.MODULE_STATE_UNKNOWN);

        assertThat(newData.getUserChoice(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        assertThat(newData.getUserChoice(Module.PROTECTED_AUDIENCE))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        assertThat(newData.getUserChoice(Module.TOPICS))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
    }
}
