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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.DecisionLogic;
import android.os.Parcel;

import org.junit.Test;

public final class DecisionLogicTest extends CtsAdServicesDeviceTestCase {
    private static final String DECISION_LOGIC = "reportWin()";

    @Test
    public void testBuildValid_Success() {
        DecisionLogic valid = new DecisionLogic(DECISION_LOGIC);
        assertThat(valid.getLogic()).isEqualTo(DECISION_LOGIC);
    }

    @Test
    public void testParcelValid_Success() {
        DecisionLogic valid = new DecisionLogic(DECISION_LOGIC);

        Parcel p = Parcel.obtain();
        valid.writeToParcel(p, 0);
        p.setDataPosition(0);

        DecisionLogic fromParcel = DecisionLogic.CREATOR.createFromParcel(p);
        assertThat(fromParcel.getLogic()).isEqualTo(DECISION_LOGIC);
    }

    @Test
    public void testBuildWith_NullDecisionLogic_Failure() {
        assertThrows(NullPointerException.class, () -> new DecisionLogic(null));
    }

    @Test
    public void testDescribeContents() {
        DecisionLogic obj = new DecisionLogic(DECISION_LOGIC);
        assertThat(obj.describeContents()).isEqualTo(0);
    }
}
