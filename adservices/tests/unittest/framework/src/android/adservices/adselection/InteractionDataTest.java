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

package android.adservices.adselection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import org.junit.Test;

public class InteractionDataTest {

    private static final String INTERACTION_DATA_STRING = "{key:value}";

    @Test
    public void testValidInteractionData() throws Exception {
        InteractionData interactionData = InteractionData.fromString(INTERACTION_DATA_STRING);
        assertEquals(INTERACTION_DATA_STRING, interactionData.toString());
    }

    @Test
    public void testInvalidStringInteractionData() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    InteractionData.fromString("hello");
                });
    }

    @Test
    public void testInvalidArrayInteractionData() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    InteractionData.fromString("[{key1:value1},{key2:value2}]");
                });
    }

    @Test
    public void testWriteToParcel() throws Exception {
        InteractionData interactionData = InteractionData.fromString(INTERACTION_DATA_STRING);

        Parcel p = Parcel.obtain();
        interactionData.writeToParcel(p, 0);
        p.setDataPosition(0);

        InteractionData fromParcel = InteractionData.CREATOR.createFromParcel(p);

        assertEquals(INTERACTION_DATA_STRING, fromParcel.toString());
        assertEquals(interactionData.getSizeInBytes(), fromParcel.getSizeInBytes());
    }

    @Test
    public void testNullInteractionDataThrowsException() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    InteractionData.fromString(null);
                });
    }
}
