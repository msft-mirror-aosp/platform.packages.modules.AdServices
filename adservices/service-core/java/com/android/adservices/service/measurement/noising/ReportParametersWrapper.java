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

package com.android.adservices.service.measurement.noising;

import java.util.Arrays;
import java.util.Objects;

/** Wrapper of the parameters in the reporting to support dynamic programming */
class ReportParametersWrapper {
    private int mTotalCap;
    private final int[] mPerTypeNumWindowList;
    private final int[] mPerTypeCapList;

    /**
     * @param totalCap total incremental cap
     * @param perTypeNumWindowList reporting window per trigger data
     * @param perTypeCapList cap per trigger data
     */
    ReportParametersWrapper(int totalCap, int[] perTypeNumWindowList, int[] perTypeCapList) {
        this.mTotalCap = totalCap;
        this.mPerTypeNumWindowList =
                Arrays.copyOf(perTypeNumWindowList, perTypeNumWindowList.length);
        this.mPerTypeCapList = Arrays.copyOf(perTypeCapList, perTypeNumWindowList.length);
    }

    /** @return total cap */
    public int getTotalCap() {
        return this.mTotalCap;
    }

    /** @return reporting window per trigger data */
    public int[] getPerTypeNumWindowList() {
        return this.mPerTypeNumWindowList;
    }

    /** @return cap per trigger data */
    public int[] getPerTypeCapList() {
        return this.mPerTypeCapList;
    }

    /** @return the length of the trigger data */
    public int getLengthOfWindowList() {
        return this.mPerTypeNumWindowList.length;
    }

    /** @param value the value to be added */
    public void modifyLastElementPerTypeNumWindowList(int value) {
        this.mPerTypeNumWindowList[this.mPerTypeNumWindowList.length - 1] += value;
    }

    /** @param value the value to be added */
    public void modifyLastElementPerTypeCapListWrapper(int value) {
        this.mPerTypeCapList[this.mPerTypeCapList.length - 1] += value;
    }

    /** @param value the value to be added */
    public void modifyTotalCap(int value) {
        this.mTotalCap += value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReportParametersWrapper)) return false;
        ReportParametersWrapper that = (ReportParametersWrapper) o;
        return mTotalCap == that.mTotalCap
                && Arrays.equals(mPerTypeCapList, that.mPerTypeCapList)
                && Arrays.equals(mPerTypeNumWindowList, that.mPerTypeNumWindowList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTotalCap,
                Arrays.hashCode(mPerTypeNumWindowList),
                Arrays.hashCode(mPerTypeCapList));
    }
}
