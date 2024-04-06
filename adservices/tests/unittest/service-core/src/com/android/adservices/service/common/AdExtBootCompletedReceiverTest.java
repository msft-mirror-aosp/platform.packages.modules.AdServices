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

package com.android.adservices.service.common;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import android.content.Intent;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

@SmallTest
@SpyStatic(AdServicesBackCompatInit.class)
public class AdExtBootCompletedReceiverTest extends AdServicesExtendedMockitoTestCase {

    @Mock private AdServicesBackCompatInit mMockBackCompatInit;

    @Test
    public void testExecuteBackCompatInit() {
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        doReturn(mMockBackCompatInit).when(() -> AdServicesBackCompatInit.getInstance());

        bootCompletedReceiver.onReceive(mContext, new Intent());

        verify(mMockBackCompatInit).initializeComponents();
    }

    @Test
    public void testExecuteBackCompatInit_exception_shouldNotThrow() {
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        doReturn(mMockBackCompatInit).when(() -> AdServicesBackCompatInit.getInstance());
        doThrow(IllegalArgumentException.class).when(mMockBackCompatInit).initializeComponents();

        // No exception expected, so no need to explicitly handle any exceptions here.
        bootCompletedReceiver.onReceive(mContext, new Intent());
    }
}
