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

import static android.adservices.common.AdServicesModuleState.MODULE_STATE_UNKNOWN;
import static android.adservices.common.AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN;

import android.adservices.common.AdServicesModuleState;
import android.adservices.common.AdServicesModuleState.ModuleStateCode;
import android.adservices.common.AdServicesModuleUserChoice;
import android.adservices.common.AdServicesModuleUserChoice.ModuleUserChoiceCode;
import android.adservices.common.Module.ModuleCode;

import com.android.adservices.LogUtil;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.HashMap;
import java.util.Map;

/**
 * Container object that has enrollment data. Should not be used as storage. This is only a helper
 * class to more easily read/write enrollment data.
 */
public class EnrollmentData {
    private final Map<Integer, Integer> mModuleStates = new HashMap<>();

    private final Map<Integer, Integer> mUserChoices = new HashMap<>();

    /**
     * Serializes module enrollment state data to string.
     *
     * @param data Data to serialize.
     * @return Serialized string.
     */
    public static String serialize(EnrollmentData data) {
        return new Gson().toJson(data);
    }

    /**
     * Serializes module enrollment state data to string.
     *
     * @return Serialized string.
     */
    public String serialize() {
        return serialize(this);
    }

    /**
     * Deserializes module enrolment state data from string.
     *
     * @param string String to deserialize.
     * @return Object with enrollment data.
     */
    public static EnrollmentData deserialize(String string) {
        try {
            EnrollmentData data = new Gson().fromJson(string, EnrollmentData.class);
            if (data != null) {
                return data;
            }
        } catch (JsonSyntaxException e) {
            LogUtil.e("Enrollment Data deserializing error:" + e);
        }
        return new EnrollmentData();
    }

    /**
     * Get the user choice for the given module. If null, then returns {@link
     * AdServicesModuleState#MODULE_STATE_UNKNOWN}.
     *
     * @param key Key of desired module.
     * @return User choice for given module.
     */
    @ModuleStateCode
    public int getModuleState(@ModuleCode int key) {
        if (!mModuleStates.containsKey(key)) {
            return MODULE_STATE_UNKNOWN;
        }
        return mModuleStates.get(key);
    }

    /**
     * Stores the state for the given module.
     *
     * @param moduleState Module choice object to update in enrollment data.
     */
    public void putModuleState(AdServicesModuleState moduleState) {
        mModuleStates.put(moduleState.getModule(), moduleState.getModuleState());
    }

    /**
     * Get the user choice for the given module. If null, then returns {@link
     * AdServicesModuleUserChoice#USER_CHOICE_UNKNOWN}.
     *
     * @param key Key of desired module.
     * @return User choice for given module.
     */
    @ModuleUserChoiceCode
    public int getUserChoice(@ModuleCode int key) {
        if (!mUserChoices.containsKey(key)) {
            return USER_CHOICE_UNKNOWN;
        }
        return mUserChoices.get(key);
    }

    /**
     * Stores the user choice for the given module.
     *
     * @param userChoice User choice object to update in enrollment data.
     */
    public void putUserChoice(AdServicesModuleUserChoice userChoice) {
        putUserChoice(userChoice.getModule(), userChoice.getUserChoice());
    }

    /**
     * Stores the user choice for the given module code.
     *
     * @param moduleCode Code for desired module.
     * @param userChoiceCode User choice to store.
     */
    public void putUserChoice(
            @ModuleCode int moduleCode, @ModuleUserChoiceCode int userChoiceCode) {
        mUserChoices.put(moduleCode, userChoiceCode);
    }
}
