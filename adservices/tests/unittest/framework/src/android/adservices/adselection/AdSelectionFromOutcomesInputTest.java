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

package android.adservices.adselection;

import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_AD_SELECTION_ID_2;
import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_SELECTION_LOGIC_URI_2;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

import java.util.Collections;

public final class AdSelectionFromOutcomesInputTest extends AdServicesUnitTestCase {
    private static final String CALLER_PACKAGE_NAME = "com.app.test";

    @Test
    public void testBuildValidAdSelectionFromOutcomesInputSuccess() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        AdSelectionFromOutcomesInput inputParams = createAdSelectionFromOutcomesInput(config);

        expect.that(inputParams.getAdSelectionFromOutcomesConfig()).isEqualTo(config);
        expect.that(inputParams.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
    }

    @Test
    public void testParcelValidInputSuccess() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        AdSelectionFromOutcomesInput inputParams = createAdSelectionFromOutcomesInput(config);

        Parcel p = Parcel.obtain();
        inputParams.writeToParcel(p, 0);
        p.setDataPosition(0);
        AdSelectionFromOutcomesInput fromParcel =
                AdSelectionFromOutcomesInput.CREATOR.createFromParcel(p);

        expect.that(inputParams.getAdSelectionFromOutcomesConfig()).isEqualTo(config);
        expect.that(fromParcel.getCallerPackageName())
                .isEqualTo(inputParams.getCallerPackageName());
    }

    @Test
    public void testAdSelectionFromOutcomesInputUnsetAdOutcomesBuildFailure() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdSelectionFromOutcomesInput.Builder()
                                .setCallerPackageName(CALLER_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testAdSelectionFromOutcomesInputUnsetCallerPackageNameBuildFailure() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        assertThrows(
                NullPointerException.class,
                () ->
                        new AdSelectionFromOutcomesInput.Builder()
                                .setAdSelectionFromOutcomesConfig(config)
                                .build());
    }

    @Test
    public void testAdSelectionFromOutcomesInputDescribeContents() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesInput obj = createAdSelectionFromOutcomesInput(config);

        expect.that(obj.describeContents()).isEqualTo(0);
    }

    @Test
    public void testEqualInputs() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesInput obj1 = createAdSelectionFromOutcomesInput(config);
        AdSelectionFromOutcomesInput obj2 = createAdSelectionFromOutcomesInput(config);

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(obj1, obj2);
    }

    @Test
    public void testNotEqualInputsHaveDifferentHashCode() {
        AdSelectionFromOutcomesConfig config1 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesConfig config2 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        Collections.singletonList(SAMPLE_AD_SELECTION_ID_2));
        AdSelectionFromOutcomesConfig config3 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        SAMPLE_SELECTION_LOGIC_URI_2);

        AdSelectionFromOutcomesInput obj1 = createAdSelectionFromOutcomesInput(config1);
        AdSelectionFromOutcomesInput obj2 = createAdSelectionFromOutcomesInput(config2);
        AdSelectionFromOutcomesInput obj3 = createAdSelectionFromOutcomesInput(config3);

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreNotEqual(obj1, obj2);
        et.expectObjectsAreNotEqual(obj1, obj3);
        et.expectObjectsAreNotEqual(obj2, obj3);
    }

    private AdSelectionFromOutcomesInput createAdSelectionFromOutcomesInput(
            AdSelectionFromOutcomesConfig config) {
        return new AdSelectionFromOutcomesInput.Builder()
                .setAdSelectionFromOutcomesConfig(config)
                .setCallerPackageName(CALLER_PACKAGE_NAME)
                .build();
    }
}
