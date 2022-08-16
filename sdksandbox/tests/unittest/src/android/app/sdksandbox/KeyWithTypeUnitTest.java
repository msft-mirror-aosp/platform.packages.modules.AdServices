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

package android.app.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link KeyWithType} APIs. */
@RunWith(JUnit4.class)
public class KeyWithTypeUnitTest {

    @Test
    public void testKeyWithType_DescribeContents() throws Exception {
        final KeyWithType keyWithType = new KeyWithType("key", KeyWithType.KEY_TYPE_LONG);
        assertThat(keyWithType.describeContents()).isEqualTo(0);
    }

    @Test
    public void testKeyWithType_GetName() throws Exception {
        final KeyWithType keyWithType = new KeyWithType("key", KeyWithType.KEY_TYPE_LONG);
        assertThat(keyWithType.getName()).isEqualTo("key");
    }

    @Test
    public void testKeyWithType_GetType() throws Exception {
        final KeyWithType keyWithType = new KeyWithType("key", KeyWithType.KEY_TYPE_LONG);
        assertThat(keyWithType.getType()).isEqualTo(KeyWithType.KEY_TYPE_LONG);
    }

    @Test
    public void testKeyWithType_IsParcelable() throws Exception {
        final KeyWithType keyWithType = new KeyWithType("key", KeyWithType.KEY_TYPE_LONG);

        final Parcel p = Parcel.obtain();
        keyWithType.writeToParcel(p, /*flags=*/ 0);

        // Create KeyWithType with the same parcel
        p.setDataPosition(0); // rewind
        final KeyWithType newKeyWithType = KeyWithType.CREATOR.createFromParcel(p);

        assertThat(newKeyWithType.getName()).isEqualTo(keyWithType.getName());
        assertThat(newKeyWithType.getType()).isEqualTo(keyWithType.getType());
    }
}
