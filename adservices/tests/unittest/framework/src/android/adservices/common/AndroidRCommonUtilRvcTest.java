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

package android.adservices.common;

import static com.android.adservices.shared.testing.AndroidSdk.RVC;

import android.adservices.exceptions.AdServicesException;

import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;

import org.junit.Test;

@RequiresSdkRange(atMost = RVC)
public final class AndroidRCommonUtilRvcTest extends AdServicesUnitTestCase {

    @Test
    public void testInvokeCallbackOnErrorOnRvc_onR() throws Exception {
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        expect.that(AndroidRCommonUtil.invokeCallbackOnErrorOnRvc(callback)).isTrue();
        callback.assertFailure(AdServicesException.class);
    }
}
