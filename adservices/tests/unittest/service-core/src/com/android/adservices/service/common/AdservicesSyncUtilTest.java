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

package com.android.adservices.service.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import android.content.Context;

import com.android.adservices.common.AdServicesMockitoTestCase;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.function.BiConsumer;

public final class AdservicesSyncUtilTest extends AdServicesMockitoTestCase {
    private AdServicesSyncUtil mAdservicesSyncUtil;
    @Mock private BiConsumer<Context, Boolean> mConsumer;

    @Before
    public void setup() {
        mAdservicesSyncUtil = AdServicesSyncUtil.getInstance();
        doNothing().when(mConsumer).accept(any(Context.class), any(Boolean.class));
    }

    @Test
    public void testRegisterConsumerAndExecute() {
        mAdservicesSyncUtil.register(mConsumer);
        mAdservicesSyncUtil.execute(mMockContext, true);
        verify(mConsumer).accept(mMockContext, true);
    }
}
