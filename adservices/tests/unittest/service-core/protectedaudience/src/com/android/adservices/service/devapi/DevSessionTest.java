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

package com.android.adservices.service.devapi;

import static com.android.adservices.service.devapi.DevSessionState.IN_DEV;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.devapi.DevSessionFixture;
import com.android.adservices.service.proto.DevSessionStorage;

import org.junit.Test;

import java.util.regex.Pattern;

public final class DevSessionTest extends AdServicesUnitTestCase {

    @Test
    public void testToAndFromProto() {
        DevSession devSession = DevSessionFixture.IN_DEV;

        expect.that(DevSession.fromProto(DevSession.toProto(devSession))).isEqualTo(devSession);
    }

    @Test
    public void testBuilder() {
        DevSession devSession = DevSession.builder().setState(IN_DEV).build();

        expect.that(devSession.getState()).isEqualTo(IN_DEV);
        expect.that(devSession.isServerAuctionTestKeysEnabled()).isEqualTo(false);
    }

    @Test
    public void testBuilder_withDefaultValues_emptyAppAllowlistPattern() {
        DevSession devSession = DevSession.builder().setState(IN_DEV).build();

        assertThat("anything%^&123").doesNotMatch(devSession.getNonDebuggableAppAllowlistPattern());
        assertThat("").matches(devSession.getNonDebuggableAppAllowlistPattern());
    }

    @Test
    public void testFromProto_withUninitializedPattern_emptyAppAllowlistPattern() {
        DevSessionStorage proto =
                DevSessionStorage.newBuilder()
                        .setState(DevSessionStorage.State.IN_DEV)
                        .setIsStorageInitialized(true)
                        .setServerAuctionTestKeysEnabled(false)
                        .setNonDebuggableAppAllowlistPattern("")
                        .build();

        DevSession devSession = DevSession.fromProto(proto);

        assertThat("anything%^&123").doesNotMatch(devSession.getNonDebuggableAppAllowlistPattern());
        assertThat("").matches(devSession.getNonDebuggableAppAllowlistPattern());
    }

    @Test
    public void testBuilder_withServerAuctionTestKeysEnabled() {
        DevSession devSession =
                DevSession.builder().setState(IN_DEV).setServerAuctionTestKeysEnabled(true).build();

        expect.that(devSession.getState()).isEqualTo(IN_DEV);
        expect.that(devSession.isServerAuctionTestKeysEnabled()).isEqualTo(true);
    }

    @Test
    public void testBuilder_withAppAllowlistPattern() {
        String patternString = "com\\.example\\..*";
        DevSession devSession =
                DevSession.builder()
                        .setState(IN_DEV)
                        .setNonDebuggableAppAllowlistPatternString(patternString)
                        .build();

        expect.that(devSession.getState()).isEqualTo(IN_DEV);
        expect.that(devSession.getNonDebuggableAppAllowlistPattern().pattern())
                .isEqualTo(patternString);
        assertThat("com.example.foo").matches(devSession.getNonDebuggableAppAllowlistPattern());
        assertThat("com.example").doesNotMatch(devSession.getNonDebuggableAppAllowlistPattern());
    }

    @Test
    public void testFromUninitializedProtoThrowsException() {
        assertThrows(
                IllegalStateException.class,
                () -> DevSession.fromProto(DevSessionStorage.getDefaultInstance()));
    }
}
