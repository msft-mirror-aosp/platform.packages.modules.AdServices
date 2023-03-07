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

package com.android.adservices.service.adselection;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AdFiltererFactoryTest {

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    @Mock private Flags mFlagsMock;

    @Test
    public void testFiltersEnabled() {
        doReturn(true).when(mFlagsMock).getFledgeAdSelectionFilteringEnabled();
        assertTrue(AdFiltererFactory.getAdFilterer(CONTEXT, mFlagsMock) instanceof AdFiltererImpl);
    }

    @Test
    public void testFiltersDisabled() {
        doReturn(false).when(mFlagsMock).getFledgeAdSelectionFilteringEnabled();
        assertTrue(
                AdFiltererFactory.getAdFilterer(CONTEXT, mFlagsMock) instanceof AdFiltererNoOpImpl);
    }
}
