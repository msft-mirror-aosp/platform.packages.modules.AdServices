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

package android.adservices.common;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class AdSelectionSignalsTest {

    private static final String SIGNALS_STRING = "{\"a\":\"b\"}";
    private static final String DIFFERENT_SIGNALS_STRING = "{\"a\":\"c\"}";
    private static final int ARRAY_SIZE = 10;
    private static final int DESCRIBE_CONTENTS_EXPECTATION = 0;

    @Test
    public void testAdSelectionSignalsCreatorArray() {
        assertArrayEquals(
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
        assertEquals(preParcel, postParcel);
    }

    @Test
    public void testAdSelectionSignalsDescribeContents() {
        assertEquals(
                DESCRIBE_CONTENTS_EXPECTATION,
                AdSelectionSignals.fromString(SIGNALS_STRING).describeContents());
    }

    @Test
    public void testBuildValidAdSelectionSignalsSuccess() {
        AdSelectionSignals validSignals = AdSelectionSignals.fromString(SIGNALS_STRING);
        assertEquals(SIGNALS_STRING, validSignals.getStringForm());
    }

    @Test
    public void testBuildValidAdSelectionSignalsFromJsonSuccess() throws JSONException {
        JSONObject json = new JSONObject(SIGNALS_STRING);
        AdSelectionSignals validSignals = AdSelectionSignals.fromJson(json);
        assertEquals(SIGNALS_STRING, validSignals.getStringForm());
        assertEquals(json.toString(), validSignals.getJsonForm().toString());
    }

    @Test
    public void testBuildValidAdSelectionSignalsSuccessNoValidation() {
        AdSelectionSignals validSignals = AdSelectionSignals.fromString(SIGNALS_STRING, false);
        assertEquals(SIGNALS_STRING, validSignals.getStringForm());
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
        assertEquals(identicalId1, identicalId2);
        assertNotEquals(identicalId1, differentId);
        assertNotEquals(identicalId2, differentId);
    }

    @Test
    public void testAdSelectionSignalsHashCode() {
        AdSelectionSignals identicalId1 = AdSelectionSignals.fromString(SIGNALS_STRING);
        AdSelectionSignals identicalId2 = AdSelectionSignals.fromString(SIGNALS_STRING);
        AdSelectionSignals differentId = AdSelectionSignals.fromString(DIFFERENT_SIGNALS_STRING);
        assertEquals(identicalId1.hashCode(), identicalId2.hashCode());
        assertNotEquals(identicalId1.hashCode(), differentId.hashCode());
        assertNotEquals(identicalId2.hashCode(), differentId.hashCode());
    }

    @Test
    public void testAdSelectionSignalsToString() {
        AdSelectionSignals validSignals = AdSelectionSignals.fromString(SIGNALS_STRING);
        assertEquals(SIGNALS_STRING, validSignals.toString());
    }
}
