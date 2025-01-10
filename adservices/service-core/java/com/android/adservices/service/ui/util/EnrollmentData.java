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

import static android.adservices.common.AdServicesCommonManager.MODULE_STATE_UNKNOWN;
import static android.adservices.common.AdServicesCommonManager.Module;
import static android.adservices.common.AdServicesCommonManager.ModuleState;
import static android.adservices.common.AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesModuleUserChoice;
import android.adservices.common.AdServicesModuleUserChoice.ModuleUserChoiceCode;
import android.adservices.common.Module.ModuleCode;
import android.util.SparseIntArray;

import com.android.adservices.LogUtil;

import com.google.common.base.Strings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * Container object that has enrollment data. Should not be used as storage. This is only a helper
 * class to more easily read/write enrollment data.
 */
public class EnrollmentData implements Serializable {
    // IMPORTANT: New data must be serialized/deserialized correctly to be stored
    private final SparseIntArray mModuleStates = new SparseIntArray();
    private final SparseIntArray mUserChoices = new SparseIntArray();

    private static final String MODULE_STATE_JSON_KEY = "mModuleStates";

    private static final String USER_CHOICE_JSON_KEY = "mUserChoices";

    /**
     * Serializes module enrollment state data to string.
     *
     * @param data Data to serialize.
     * @return Serialized string.
     */
    public static String serialize(EnrollmentData data) {
        JSONObject jsonObject = new JSONObject();
        String enrollmentDataStr = "";
        try {
            // Serialize mModuleStates
            JSONArray moduleStatesArray = getJsonArrFromSparseArr(data.mModuleStates);
            jsonObject.put(MODULE_STATE_JSON_KEY, moduleStatesArray);

            // Serialize mUserChoices
            JSONArray userChoicesArray = getJsonArrFromSparseArr(data.mUserChoices);
            jsonObject.put(USER_CHOICE_JSON_KEY, userChoicesArray);
            enrollmentDataStr = jsonObject.toString();
        } catch (JSONException e) {
            LogUtil.e("Enrollment Data serializing error:" + e);
        }
        return enrollmentDataStr;
    }

    /**
     * Deserializes module enrolment state data from string.
     *
     * @param string String to deserialize.
     * @return Object with enrollment data.
     */
    public static EnrollmentData deserialize(String jsonStr) {
        EnrollmentData enrollmentData = new EnrollmentData();
        if (Strings.isNullOrEmpty(jsonStr)) {
            return enrollmentData;
        }
        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(jsonStr);
            // Deserialize mModuleStates
            JSONArray moduleStatesArray = jsonObject.getJSONArray(MODULE_STATE_JSON_KEY);
            populateSparseArrFromJsonArr(moduleStatesArray, enrollmentData.mModuleStates);

            // Deserialize mUserChoices
            JSONArray userChoicesArray = jsonObject.getJSONArray(USER_CHOICE_JSON_KEY);
            populateSparseArrFromJsonArr(userChoicesArray, enrollmentData.mUserChoices);
        } catch (JSONException e) {
            LogUtil.e("Enrollment Data deserializing error:" + e);
        }
        return enrollmentData;
    }

    /**
     * Gets all the module states currently stored.
     *
     * @return all module states.
     */
    public SparseIntArray getModuleStates() {
        return mModuleStates;
    }

    /**
     * Gets the module state for the given module. If null, then returns {@link
     * AdServicesCommonManager#MODULE_STATE_UNKNOWN}.
     *
     * @param key Key of desired module.
     * @return module state for given module.
     */
    @ModuleState
    public int getModuleState(@ModuleCode int key) {
        return mModuleStates.get(key, MODULE_STATE_UNKNOWN);
    }

    /**
     * Stores the state for the given module.
     *
     * @param module Code for desired module.
     * @param state Module choice object to update in enrollment data.
     */
    public void putModuleState(@Module int module, @ModuleState int state) {
        mModuleStates.put(module, state);
    }

    /**
     * Gets all the user choices currently stored.
     *
     * @return all user choices.
     */
    @ModuleUserChoiceCode
    public SparseIntArray getUserChoices() {
        return mUserChoices;
    }

    /**
     * Gets the user choice for the given module. If null, then returns {@link
     * AdServicesModuleUserChoice#USER_CHOICE_UNKNOWN}.
     *
     * @param key Key of desired module.
     * @return User choice for given module.
     */
    @ModuleUserChoiceCode
    public int getUserChoice(@ModuleCode int key) {
        return mUserChoices.get(key, USER_CHOICE_UNKNOWN);
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

    private static JSONArray getJsonArrFromSparseArr(SparseIntArray inputArr) throws JSONException {
        JSONArray jsonArr = new JSONArray();
        for (int i = 0; i < inputArr.size(); i++) {
            JSONObject jsonObj = new JSONObject();
            jsonObj.put(String.valueOf(inputArr.keyAt(i)), String.valueOf(inputArr.valueAt(i)));

            jsonArr.put(jsonObj);
        }
        return jsonArr;
    }

    private static void populateSparseArrFromJsonArr(JSONArray jsonArr, SparseIntArray outputArr)
            throws JSONException {
        for (int i = 0; i < jsonArr.length(); i++) {
            JSONObject jsonObj = jsonArr.getJSONObject(i);
            for (int j = 0; j < jsonObj.names().length(); j++) {
                String key = jsonObj.names().getString(j);
                outputArr.put(Integer.parseInt(key), Integer.parseInt(jsonObj.getString(key)));
            }
        }
    }
}
