/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_AD_SELECTION_ID_1;
import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_AD_SELECTION_ID_2;
import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_SELECTION_LOGIC_URI_1;
import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_SELECTION_LOGIC_URI_2;
import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_SELECTION_SIGNALS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.os.Parcel;

import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

import java.util.Collections;

public final class AdSelectionFromOutcomesConfigTest extends CtsAdServicesDeviceTestCase {
    @Test
    public void testBuildValidAdSelectionFromOutcomesConfigSuccess() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        assertThat(config.getAdSelectionIds()).hasSize(1);
        expect.that(config.getAdSelectionIds().get(0)).isEqualTo(SAMPLE_AD_SELECTION_ID_1);
        expect.that(config.getSelectionSignals()).isEqualTo(SAMPLE_SELECTION_SIGNALS);
        expect.that(config.getSelectionLogicUri()).isEqualTo(SAMPLE_SELECTION_LOGIC_URI_1);
    }

    @Test
    public void testParcelValidInputSuccess() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        Parcel p = Parcel.obtain();
        config.writeToParcel(p, 0);
        p.setDataPosition(0);
        AdSelectionFromOutcomesConfig fromParcel =
                AdSelectionFromOutcomesConfig.CREATOR.createFromParcel(p);

        expect.that(fromParcel.getAdSelectionIds()).isEqualTo(config.getAdSelectionIds());
        expect.that(fromParcel.getSelectionSignals()).isEqualTo(config.getSelectionSignals());
        expect.that(fromParcel.getSelectionLogicUri()).isEqualTo(config.getSelectionLogicUri());
    }

    @Test
    public void testBuildAdSelectionFromOutcomesConfigUnsetAdOutcomeIds() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdSelectionFromOutcomesConfig.Builder()
                                .setSelectionSignals(SAMPLE_SELECTION_SIGNALS)
                                .setSelectionLogicUri(SAMPLE_SELECTION_LOGIC_URI_1)
                                .build());
    }

    @Test
    public void testBuildAdSelectionFromOutcomesConfigUnsetSelectionSignals() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdSelectionFromOutcomesConfig.Builder()
                                .setAdSelectionIds(
                                        Collections.singletonList(SAMPLE_AD_SELECTION_ID_1))
                                .setSelectionLogicUri(SAMPLE_SELECTION_LOGIC_URI_1)
                                .build());
    }

    @Test
    public void testBuildAdSelectionFromOutcomesConfigUnsetSelectionUri() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdSelectionFromOutcomesConfig.Builder()
                                .setAdSelectionIds(
                                        Collections.singletonList(SAMPLE_AD_SELECTION_ID_1))
                                .setSelectionSignals(SAMPLE_SELECTION_SIGNALS)
                                .build());
    }

    @Test
    public void testAdSelectionFromOutcomesConfigDescribeContents() {
        AdSelectionFromOutcomesConfig obj =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        expect.that(obj.describeContents()).isEqualTo(0);
    }

    @Test
    public void testEqualInputsHaveSameHashCode() {
        AdSelectionFromOutcomesConfig obj1 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesConfig obj2 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(obj1, obj2);

        AdSelectionFromOutcomesConfig obj3 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        Collections.singletonList(SAMPLE_AD_SELECTION_ID_2));
        AdSelectionFromOutcomesConfig obj4 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        SAMPLE_SELECTION_LOGIC_URI_2);
        et.expectObjectsAreNotEqual(obj1, obj3);
        et.expectObjectsAreNotEqual(obj1, obj4);
        et.expectObjectsAreNotEqual(obj3, obj4);
    }
}
