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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.DecisionLogic;
import android.adservices.adselection.PerBuyerDecisionLogic;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.os.Parcel;

import com.android.adservices.shared.testing.EqualsTester;

import com.google.common.collect.ImmutableMap;

import org.junit.Test;

import java.util.Map;

public final class PerBuyerDecisionLogicTest extends CtsAdServicesDeviceTestCase {

    private static final AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    private static final AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;
    private static final String DECISION_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final DecisionLogic DECISION_LOGIC = new DecisionLogic(DECISION_LOGIC_JS);

    @Test
    public void testBuildValidSuccess() {
        PerBuyerDecisionLogic obj =
                new PerBuyerDecisionLogic(
                        ImmutableMap.of(BUYER_1, DECISION_LOGIC, BUYER_2, DECISION_LOGIC));
        expect.that(obj.getPerBuyerLogicMap()).containsEntry(BUYER_1, DECISION_LOGIC);
        expect.that(obj.getPerBuyerLogicMap()).containsEntry(BUYER_2, DECISION_LOGIC);
    }

    @Test
    public void testParcelValid_Success() {
        PerBuyerDecisionLogic valid =
                new PerBuyerDecisionLogic(
                        ImmutableMap.of(BUYER_1, DECISION_LOGIC, BUYER_2, DECISION_LOGIC));

        Parcel p = Parcel.obtain();
        valid.writeToParcel(p, 0);
        p.setDataPosition(0);

        PerBuyerDecisionLogic fromParcel = PerBuyerDecisionLogic.CREATOR.createFromParcel(p);
        Map<AdTechIdentifier, DecisionLogic> mapFromParcel = fromParcel.getPerBuyerLogicMap();
        assertThat(mapFromParcel).isNotNull();
        expect.that(mapFromParcel.get(BUYER_1)).isEqualTo(DECISION_LOGIC);
        expect.that(mapFromParcel.get(BUYER_2)).isEqualTo(DECISION_LOGIC);
    }

    @Test
    public void testDescribeContents() {
        PerBuyerDecisionLogic obj = new PerBuyerDecisionLogic(ImmutableMap.of());
        assertThat(obj.describeContents()).isEqualTo(0);
    }

    @Test
    public void testDefaultEmpty() {
        PerBuyerDecisionLogic empty = PerBuyerDecisionLogic.EMPTY;
        assertThat(empty.getPerBuyerLogicMap().size()).isEqualTo(0);
    }

    @Test
    public void testAssertEquals() {
        PerBuyerDecisionLogic obj =
                new PerBuyerDecisionLogic(
                        ImmutableMap.of(BUYER_1, DECISION_LOGIC, BUYER_2, DECISION_LOGIC));
        PerBuyerDecisionLogic obj2 =
                new PerBuyerDecisionLogic(
                        ImmutableMap.of(BUYER_1, DECISION_LOGIC, BUYER_2, DECISION_LOGIC));

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(obj, obj2);
    }
}
