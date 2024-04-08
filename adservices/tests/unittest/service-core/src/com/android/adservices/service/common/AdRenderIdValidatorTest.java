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

package com.android.adservices.service.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.service.Flags;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class AdRenderIdValidatorTest {
    private static final String VERY_LONG_ID =
            "this is a very long ad render ID which should throw an error";
    private static final String SHORT_ID = "X";

    @Test
    public void testCreateInstance_disabled_returnsNoViolations() {
        AdRenderIdValidator validator =
                AdRenderIdValidator.createInstance(
                        new FlagsWithAuctionServerAdRenderIdSettings(
                                /* enabled= */ false, /* maxLength= */ 1));

        assertThat(validator.getValidationViolations(VERY_LONG_ID)).isEmpty();
        validator.validate(VERY_LONG_ID);
    }

    @Test
    public void testCreateInstance_enabled_returnsViolations() {
        AdRenderIdValidator validator =
                AdRenderIdValidator.createInstance(
                        new FlagsWithAuctionServerAdRenderIdSettings(
                                /* enabled= */ true, /* maxLength= */ 1));

        assertThat(validator.getValidationViolations(VERY_LONG_ID)).isNotEmpty();
        assertThrows(IllegalArgumentException.class, () -> validator.validate(VERY_LONG_ID));
    }

    @Test
    public void testCreateInstance_enabled_shortId_returnsNoViolations() {
        AdRenderIdValidator validator =
                AdRenderIdValidator.createInstance(
                        new FlagsWithAuctionServerAdRenderIdSettings(
                                /* enabled= */ true,
                                /* maxLength= */ SHORT_ID.getBytes(StandardCharsets.UTF_8).length));

        assertThat(validator.getValidationViolations(SHORT_ID)).isEmpty();
        validator.validate(SHORT_ID);
    }

    private static class FlagsWithAuctionServerAdRenderIdSettings implements Flags {
        private final boolean mEnabled;
        private final long mMaxLength;

        FlagsWithAuctionServerAdRenderIdSettings(boolean enabled, long maxLength) {
            mEnabled = enabled;
            mMaxLength = maxLength;
        }

        @Override
        public boolean getFledgeAuctionServerAdRenderIdEnabled() {
            return mEnabled;
        }

        @Override
        public long getFledgeAuctionServerAdRenderIdMaxLength() {
            return mMaxLength;
        }
    }
}
