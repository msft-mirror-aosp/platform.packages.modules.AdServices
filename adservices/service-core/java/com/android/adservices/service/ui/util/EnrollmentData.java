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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;

/**
 * Container object that has enrollment data. Should not be used as storage. This is only a helper
 * class to more easily read/write enrollment data.
 */
public class EnrollmentData implements Serializable {
    // IMPORTANT: New data must be serialized/deserialized correctly to be stored
    private SparseIntArray mModuleStates = new SparseIntArray();
    private SparseIntArray mUserChoices = new SparseIntArray();

    /**
     * Serializes module enrollment state data to string.
     *
     * @param data Data to serialize.
     * @return Serialized string.
     */
    public static String serialize(EnrollmentData data) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(getPairs(data.getModuleStates()));
            oos.writeObject(getPairs(data.getUserChoices()));
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            LogUtil.e("Enrollment Data serializing error:" + e);
            return "";
        }
    }

    /**
     * Deserializes module enrolment state data from string.
     *
     * @param string String to deserialize.
     * @return Object with enrollment data.
     */
    public static EnrollmentData deserialize(String byteString) {
        EnrollmentData data = new EnrollmentData();
        if (Strings.isNullOrEmpty(byteString)) {
            return data;
        }
        try (ByteArrayInputStream bis =
                        new ByteArrayInputStream(Base64.getDecoder().decode(byteString));
                ObjectInputStream ois = new ObjectInputStream(bis)) {
            data.mModuleStates = sparseIntArrayFromPairs((int[][]) ois.readObject());
            data.mUserChoices = sparseIntArrayFromPairs((int[][]) ois.readObject());
            return data;
        } catch (IOException | ClassNotFoundException e) {
            LogUtil.e("Enrollment Data deserializing error:" + e);
        }
        return new EnrollmentData();
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

    private static int[][] getPairs(SparseIntArray array) {
        int[][] pairs = new int[array.size()][];
        for (int i = 0; i < array.size(); i++) {
            pairs[i] = new int[] {array.keyAt(i), array.valueAt(i)};
        }
        return pairs;
    }

    private static SparseIntArray sparseIntArrayFromPairs(int[][] pairs) {
        SparseIntArray array = new SparseIntArray(pairs.length);
        for (int[] pair : pairs) {
            array.append(pair[0], pair[1]);
        }
        return array;
    }
}
