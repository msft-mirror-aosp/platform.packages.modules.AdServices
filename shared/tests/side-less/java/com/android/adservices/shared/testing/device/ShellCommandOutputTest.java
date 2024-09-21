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
package com.android.adservices.shared.testing.device;

import static com.android.adservices.shared.testing.device.ShellCommandOutput.EMPTY_RESULT;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.testing.SidelessTestCase;

import org.junit.Test;

public final class ShellCommandOutputTest extends SidelessTestCase {

    private final String mOut = "3 strikes...";
    private final String mErr = "D'OH!";
    private final ShellCommandOutput.Builder mBuilder = new ShellCommandOutput.Builder();

    @Test
    public void testBuilder_nullArgs() {
        assertThrows(NullPointerException.class, () -> mBuilder.setOut(null));
        assertThrows(NullPointerException.class, () -> mBuilder.setErr(null));
    }

    @Test
    public void testBuilder_buildWithoutSetting() {
        assertThrows(IllegalStateException.class, () -> mBuilder.build());
    }

    @Test
    public void testBuilder_outOnly() {
        var output = mBuilder.setOut(mOut).build();

        expect.withMessage("getOut()").that(output.getOut()).isEqualTo(mOut);
        expect.withMessage("getErr()").that(output.getErr()).isNull();
        expect.withMessage("toString()").that(output.toString()).isEqualTo(mOut);
    }

    @Test
    public void testBuilder_errOnly() {
        var output = mBuilder.setErr(mErr).build();

        expect.withMessage("getOut()").that(output.getOut()).isNull();
        expect.withMessage("getErr()").that(output.getErr()).isEqualTo(mErr);
        expect.withMessage("toString()").that(output.toString()).isEqualTo(mErr);
    }

    @Test
    public void testBuilder_both() {
        var output = mBuilder.setOut(mOut).setErr(mErr).build();

        expect.withMessage("getOut()").that(output.getOut()).isEqualTo(mOut);
        expect.withMessage("getErr()").that(output.getErr()).isEqualTo(mErr);
        expect.withMessage("toString()")
                .that(output.toString())
                .isEqualTo("ShellCommandOutput[out=3 strikes..., err=D'OH!]");
    }

    @Test
    public void testAlternateConstructor() {
        ShellCommandOutput output = new ShellCommandOutput(mOut);

        expect.withMessage("getOut()").that(output.getOut()).isEqualTo(mOut);
        expect.withMessage("getErr()").that(output.getErr()).isEmpty();
        expect.withMessage("toString()").that(output.toString()).isEqualTo(mOut);
    }

    @Test
    public void test_EMPTY_RESULT() {
        expect.withMessage("getOut()").that(EMPTY_RESULT.getOut()).isEmpty();
        expect.withMessage("getErr()").that(EMPTY_RESULT.getErr()).isEmpty();
        expect.withMessage("toString()").that(EMPTY_RESULT.toString()).isEmpty();
    }
}
