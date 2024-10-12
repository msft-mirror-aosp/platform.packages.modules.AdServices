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

import android.adservices.common.AdSelectionSignals;
import android.os.Parcel;

import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public final class AdSelectionSignalsTest extends CtsAdServicesDeviceTestCase {

    private static final String SIGNALS_STRING = "{\"a\":\"b\"}";
    private static final String DIFFERENT_SIGNALS_STRING = "{\"a\":\"c\"}";
    private static final int ARRAY_SIZE = 10;
    private static final int DESCRIBE_CONTENTS_EXPECTATION = 0;

    @Test
    public void testAdSelectionSignalsGetSizeInBytes() {
        AdSelectionSignals identicalId = AdSelectionSignals.fromString(SIGNALS_STRING);
        expect.that(identicalId.toString().getBytes(StandardCharsets.UTF_8).length)
                .isEqualTo(SIGNALS_STRING.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    public void testAdSelectionSignalsCreatorArray() {
        Assert.assertArrayEquals(
                new AdSelectionSignals[ARRAY_SIZE],
                AdSelectionSignals.CREATOR.newArray(ARRAY_SIZE));
    }

    @Test
    public void testAdSelectionSignalsParceling() {
        AdSelectionSignals preParcel = AdSelectionSignals.fromString(SIGNALS_STRING);
        Parcel parcel = Parcel.obtain();
        preParcel.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdSelectionSignals postParcel = AdSelectionSignals.CREATOR.createFromParcel(parcel);
        expect.that(preParcel).isEqualTo(postParcel);
    }

    @Test
    public void testAdSelectionSignalsDescribeContents() {
        expect.that(AdSelectionSignals.fromString(SIGNALS_STRING).describeContents())
                .isEqualTo(DESCRIBE_CONTENTS_EXPECTATION);
    }

    @Test
    public void testBuildValidAdSelectionSignalsSuccess() {
        AdSelectionSignals validSignals = AdSelectionSignals.fromString(SIGNALS_STRING);
        expect.that(validSignals.toString()).isEqualTo(SIGNALS_STRING);
    }

    @Test
    public void testBuildValidAdSelectionSignalsSuccessNoValidation() {
        AdSelectionSignals validSignals = AdSelectionSignals.fromString(SIGNALS_STRING, false);
        expect.that(validSignals.toString()).isEqualTo(SIGNALS_STRING);
    }

    @Test(expected = NullPointerException.class)
    public void testBuildAdSelectionSignalsNull() {
        AdSelectionSignals.fromString(null);
    }

    @Test
    public void testAdSelectionSignalsEquality() {
        AdSelectionSignals identicalId1 = AdSelectionSignals.fromString(SIGNALS_STRING);
        AdSelectionSignals identicalId2 = AdSelectionSignals.fromString(SIGNALS_STRING);
        AdSelectionSignals differentId = AdSelectionSignals.fromString(DIFFERENT_SIGNALS_STRING);

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(identicalId1, identicalId2);
        et.expectObjectsAreNotEqual(identicalId1, differentId);
        et.expectObjectsAreNotEqual(identicalId2, differentId);
    }

    @Test
    public void testAdSelectionSignalsToString() {
        AdSelectionSignals validSignals = AdSelectionSignals.fromString(SIGNALS_STRING);
        expect.that(validSignals.toString()).isEqualTo(SIGNALS_STRING);
    }
}
