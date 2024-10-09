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
package com.android.adservices.mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.android.modules.utils.build.SdkLevel;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** {@link AndroidMocker} implementation that uses {@code Mockito}. */
public final class AndroidMockitoMocker extends AbstractMocker implements AndroidMocker {

    @Override
    public void mockQueryIntentService(PackageManager mockPm, ResolveInfo... resolveInfos) {
        List<ResolveInfo> list = resolveInfos == null ? null : Arrays.asList(resolveInfos);
        logV("mockQueryIntentService(%s, %s)", mockPm, list);

        Objects.requireNonNull(mockPm, "mockPm cannot be null");
        Objects.requireNonNull(resolveInfos, "resolveInfos cannot be null");
        assertIsMock("mockPm", mockPm);

        when(mockPm.queryIntentServices(any(), anyInt())).thenReturn(list);
        if (SdkLevel.isAtLeastT()) {
            when(mockPm.queryIntentServices(any(), any())).thenReturn(list);
        }
    }

    @Override
    public void mockGetApplicationContext(Context mockContext, Context appContext) {
        logV("mockGetApplicationContext(%s, %s)", mockContext, appContext);
        Objects.requireNonNull(mockContext, "mockContext cannot be null");
        assertIsMock("mockContext", mockContext);

        when(mockContext.getApplicationContext()).thenReturn(appContext);
    }
}
