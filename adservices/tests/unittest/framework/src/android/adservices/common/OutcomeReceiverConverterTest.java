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

package android.adservices.common;

import android.os.OutcomeReceiver;

import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

@SuppressWarnings("NewApi")
public class OutcomeReceiverConverterTest extends AdServicesUnitTestCase {
    @Test
    public void testToOutcomeReceiverNullInput() {
        expect.that(OutcomeReceiverConverter.toOutcomeReceiver(null)).isNull();
    }

    @Test
    public void testToOutcomeReceiver() throws Exception {
        Object obj = new Object();
        AdServicesOutcomeReceiverForTests<Object> adServicesOutcomeReceiver1 =
                new AdServicesOutcomeReceiverForTests<>();
        OutcomeReceiver<Object, Exception> converted1 =
                OutcomeReceiverConverter.toOutcomeReceiver(adServicesOutcomeReceiver1);

        converted1.onResult(obj);
        expect.withMessage("result callback").that(
                adServicesOutcomeReceiver1.assertSuccess()).isEqualTo(obj);

        Exception error = new Exception();
        AdServicesOutcomeReceiverForTests<Object> adServicesOutcomeReceiver2 =
                new AdServicesOutcomeReceiverForTests<>();
        OutcomeReceiver<Object, Exception> converted2 = OutcomeReceiverConverter.toOutcomeReceiver(
                adServicesOutcomeReceiver2);

        converted2.onError(error);
        expect.withMessage("error callback").that(
                adServicesOutcomeReceiver2.assertErrorReceived()).isEqualTo(error);
    }
}
