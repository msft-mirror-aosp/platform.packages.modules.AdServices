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

import android.adservices.signals.ProtectedSignalsManager;
import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.service.FlagsConstants;
import com.android.adservices.shared.testing.SdkLevelSupportRule;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

import com.google.common.truth.Expect;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

@SmallTest
@SetFlagEnabled(FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED)
public class ProtectedSignalsManagerTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public final Expect expect = Expect.create();

    @Test
    public void testAdSelectionManagerCtor_TPlus() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);
        final Context context = ApplicationProvider.getApplicationContext();
        expect.that(ProtectedSignalsManager.get(context)).isNotNull();
        expect.that(context.getSystemService(ProtectedSignalsManager.class)).isNotNull();
    }

    @Test
    public void testAdSelectionManagerCtor_SMinus() {
        Assume.assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU);
        final Context context = ApplicationProvider.getApplicationContext();
        expect.that(ProtectedSignalsManager.get(context)).isNotNull();
        expect.that(context.getSystemService(ProtectedSignalsManager.class)).isNull();
    }
}
