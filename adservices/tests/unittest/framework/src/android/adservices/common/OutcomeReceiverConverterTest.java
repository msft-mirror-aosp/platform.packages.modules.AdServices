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

import static com.google.common.truth.Truth.assertThat;

import android.os.OutcomeReceiver;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;

@RequiresSdkLevelAtLeastS
public class OutcomeReceiverConverterTest extends AdServicesUnitTestCase {
    @Test
    public void testOutcomeReceiverConverterNullInput() {
        expect.that(OutcomeReceiverConverter.toAdServicesOutcomeReceiver(null)).isNull();
    }

    @Test
    public void testOutcomeReceiverConverter() {
        Object obj = new Object();
        CountDownLatch resultLatch = new CountDownLatch(1);

        Exception error = new Exception();
        CountDownLatch errorLatch = new CountDownLatch(1);

        OutcomeReceiver<Object, Exception> outcomeReceiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Object result) {
                        assertThat(result).isSameInstanceAs(obj);
                        resultLatch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        assertThat(e).isSameInstanceAs(error);
                        errorLatch.countDown();
                    }
                };

        AdServicesOutcomeReceiver<Object, Exception> converted =
                OutcomeReceiverConverter.toAdServicesOutcomeReceiver(outcomeReceiver);
        converted.onResult(obj);
        expect.withMessage("result callback").that(resultLatch.getCount()).isEqualTo(0);

        converted.onError(error);
        expect.withMessage("error callback").that(errorLatch.getCount()).isEqualTo(0);
    }
}
