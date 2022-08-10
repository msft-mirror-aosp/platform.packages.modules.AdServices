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

package android.adservices.debuggablects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.net.Uri;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.KeyEvent;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MeasurementApiParamsCtsTest {

    @Test
    public void testDeletionRequest() {
        Instant start = Instant.now().minusSeconds(60);
        Instant end = Instant.now().plusSeconds(60);

        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                        .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                        .setStart(start)
                        .setEnd(end)
                        .setDomainUris(
                                List.of(
                                        Uri.parse("https://d-foo.com"),
                                        Uri.parse("https://d-bar.com")))
                        .setOriginUris(
                                List.of(
                                        Uri.parse("https://o-foo.com"),
                                        Uri.parse("https://o-bar.com")))
                        .build();

        assertEquals(DeletionRequest.DELETION_MODE_ALL, deletionRequest.getDeletionMode());
        assertEquals(DeletionRequest.MATCH_BEHAVIOR_DELETE, deletionRequest.getMatchBehavior());
        assertEquals(start, deletionRequest.getStart());
        assertEquals(end, deletionRequest.getEnd());
        assertEquals("https://d-foo.com", deletionRequest.getDomainUris().get(0).toString());
        assertEquals("https://d-bar.com", deletionRequest.getDomainUris().get(1).toString());
        assertEquals("https://o-foo.com", deletionRequest.getOriginUris().get(0).toString());
        assertEquals("https://o-bar.com", deletionRequest.getOriginUris().get(1).toString());
    }

    @Test
    public void testWebSourceParams() {
        WebSourceParams webSourceParams =
                new WebSourceParams.Builder(Uri.parse("https://registration-uri"))
                        .setDebugKeyAllowed(true)
                        .build();
        assertEquals("https://registration-uri", webSourceParams.getRegistrationUri().toString());
        assertTrue(webSourceParams.isDebugKeyAllowed());
        assertEquals(0, webSourceParams.describeContents());
    }

    @Test
    public void testWebSourceRegistrationRequest() {
        WebSourceRegistrationRequest request =
                new WebSourceRegistrationRequest.Builder(
                                List.of(
                                        new WebSourceParams.Builder(
                                                        Uri.parse("https://registration-uri"))
                                                .setDebugKeyAllowed(true)
                                                .build()),
                                Uri.parse("https://top-origin"))
                        .setInputEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1))
                        .setVerifiedDestination(Uri.parse("https://verified-destination"))
                        .setAppDestination(Uri.parse("android-app://app-destination"))
                        .setWebDestination(Uri.parse("https://web-destination"))
                        .build();
        assertEquals(0, request.describeContents());
        assertEquals(
                "https://registration-uri",
                request.getSourceParams().get(0).getRegistrationUri().toString());
        assertTrue(request.getSourceParams().get(0).isDebugKeyAllowed());
        assertEquals("https://top-origin", request.getTopOriginUri().toString());
        assertEquals(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1).getAction(),
                ((KeyEvent) request.getInputEvent()).getAction());
        assertEquals("https://verified-destination", request.getVerifiedDestination().toString());
        assertEquals("android-app://app-destination", request.getAppDestination().toString());
        assertEquals("https://web-destination", request.getWebDestination().toString());
    }

    @Test
    public void testWebTriggerParams() {
        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder(Uri.parse("https://registration-uri"))
                        .setDebugKeyAllowed(true)
                        .build();

        assertEquals(0, webTriggerParams.describeContents());
        assertEquals("https://registration-uri", webTriggerParams.getRegistrationUri().toString());
        assertTrue(webTriggerParams.isDebugKeyAllowed());
    }

    @Test
    public void testWebTriggerRegistrationRequest() {
        WebTriggerRegistrationRequest request =
                new WebTriggerRegistrationRequest.Builder(
                                List.of(
                                        new WebTriggerParams.Builder(
                                                        Uri.parse("https://registration-uri"))
                                                .setDebugKeyAllowed(true)
                                                .build()),
                                Uri.parse("https://destination"))
                        .build();

        assertEquals(0, request.describeContents());
        assertEquals(
                "https://registration-uri",
                request.getTriggerParams().get(0).getRegistrationUri().toString());
        assertTrue(request.getTriggerParams().get(0).isDebugKeyAllowed());
        assertEquals("https://destination", request.getDestination().toString());
    }
}
