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

package com.android.adservices.shared.spe.framework;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.when;

import android.app.job.JobParameters;
import android.os.PersistableBundle;

import com.android.adservices.common.AdServicesMockitoTestCase;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/** Unit tests for {@link ExecutionRuntimeParameters} */
public final class ExecutionRuntimeParametersTest extends AdServicesMockitoTestCase {
    private static final String KEY = "testKey";
    private static final String VAL = "testValue";

    private final PersistableBundle mExtras = new PersistableBundle();

    @Before
    public void setup() {
        mExtras.putString(KEY, VAL);
    }

    @Mock private JobParameters mMockParameters;

    @Test
    public void testGettersAndSetters() {
        ExecutionRuntimeParameters params =
                new ExecutionRuntimeParameters.Builder().setExtras(mExtras).build();

        expect.that(params.getExtras()).isNotNull();
        expect.that(params.getExtras().getString(KEY)).isEqualTo(VAL);
    }

    @Test
    public void testToString() {
        expect.that(mExtras.toString()).isEqualTo("PersistableBundle[{" + KEY + "=" + VAL + "}]");
    }

    @Test
    public void testConvertJobParameters() {
        when(mMockParameters.getExtras()).thenReturn(mExtras);

        assertWithMessage("ExecutionRuntimeParameters.getString(%s)", KEY)
                .that(
                        ExecutionRuntimeParameters.convertJobParameters(mMockParameters)
                                .getExtras()
                                .getString(KEY))
                .isEqualTo(VAL);
    }
}
