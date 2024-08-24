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

package com.android.adservices.service.signals;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.signals.ProtectedSignalsDao;

import org.junit.Test;
import org.mockito.Mock;

public class SignalsProviderAndArgumentFactoryTest extends AdServicesMockitoTestCase {

    @Mock private ProtectedSignalsDao mMockProtectedSignalsDao;

    private SignalsProviderAndArgumentFactory mSignalsProviderAndArgumentFactory;

    @Test
    public void test_getSignalsProvider_flagOff_returnsOGImpl() {
        mSignalsProviderAndArgumentFactory =
                new SignalsProviderAndArgumentFactory(mMockProtectedSignalsDao, false);

        SignalsProvider signalsProvider = mSignalsProviderAndArgumentFactory.getSignalsProvider();

        assertThat(signalsProvider).isInstanceOf(SignalsProviderImpl.class);
    }

    @Test
    public void test_getSignalsProvider_flagOn_returnsFastImpl() {
        mSignalsProviderAndArgumentFactory =
                new SignalsProviderAndArgumentFactory(mMockProtectedSignalsDao, true);

        SignalsProvider signalsProvider = mSignalsProviderAndArgumentFactory.getSignalsProvider();

        assertThat(signalsProvider).isInstanceOf(SignalsProviderFastImpl.class);
    }

    @Test
    public void test_getProtectedSignalsArgument_flagOff_returnsOGImpl() {
        mSignalsProviderAndArgumentFactory =
                new SignalsProviderAndArgumentFactory(mMockProtectedSignalsDao, false);

        ProtectedSignalsArgument protectedSignalsArgument =
                mSignalsProviderAndArgumentFactory.getProtectedSignalsArgument();

        assertThat(protectedSignalsArgument).isInstanceOf(ProtectedSignalsArgumentImpl.class);
    }

    @Test
    public void test_getProtectedSignalsArgument_flagOn_returnsFastImpl() {
        mSignalsProviderAndArgumentFactory =
                new SignalsProviderAndArgumentFactory(mMockProtectedSignalsDao, true);

        ProtectedSignalsArgument protectedSignalsArgument =
                mSignalsProviderAndArgumentFactory.getProtectedSignalsArgument();

        assertThat(protectedSignalsArgument).isInstanceOf(ProtectedSignalsArgumentFastImpl.class);
    }
}
