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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.net.Uri;
import android.os.Parcel;
import android.view.KeyEvent;

import com.android.adservices.common.WebUtil;

import org.junit.Test;

import java.time.Instant;
import java.util.List;

public final class MeasurementApiParamsCtsTest extends CtsAdServicesDeviceTestCase {

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
                                        WebUtil.validUri("https://d-foo.test"),
                                        WebUtil.validUri("https://d-bar.test")))
                        .setOriginUris(
                                List.of(
                                        WebUtil.validUri("https://o-foo.test"),
                                        WebUtil.validUri("https://o-bar.test")))
                        .build();

        expect.that(deletionRequest.getDeletionMode()).isEqualTo(DeletionRequest.DELETION_MODE_ALL);
        expect.that(deletionRequest.getMatchBehavior())
                .isEqualTo(DeletionRequest.MATCH_BEHAVIOR_DELETE);
        expect.that(deletionRequest.getStart()).isEqualTo(start);
        expect.that(deletionRequest.getEnd()).isEqualTo(end);

        assertThat(deletionRequest.getDomainUris()).hasSize(2);
        expect.that(deletionRequest.getDomainUris().get(0).toString())
                .isEqualTo(WebUtil.validUrl("https://d-foo.test"));
        expect.that(deletionRequest.getDomainUris().get(1).toString())
                .isEqualTo(WebUtil.validUrl("https://d-bar.test"));

        assertThat(deletionRequest.getOriginUris()).hasSize(2);
        expect.that(deletionRequest.getOriginUris().get(0).toString())
                .isEqualTo(WebUtil.validUrl("https://o-foo.test"));
        expect.that(deletionRequest.getOriginUris().get(1).toString())
                .isEqualTo(WebUtil.validUrl("https://o-bar.test"));
    }

    private WebSourceParams createWebSourceParamsExample() {
        return new WebSourceParams.Builder(Uri.parse("https://registration-uri"))
                .setDebugKeyAllowed(true)
                .build();
    }

    @Test
    public void testWebSourceParams() {
        WebSourceParams webSourceParams = createWebSourceParamsExample();
        expect.that(webSourceParams.getRegistrationUri().toString())
                .isEqualTo("https://registration-uri");
        expect.that(webSourceParams.isDebugKeyAllowed()).isTrue();
        expect.that(webSourceParams.describeContents()).isEqualTo(0);
    }

    @Test
    public void testWebSourceParamsParceling() {
        Parcel p = Parcel.obtain();
        WebSourceParams exampleParams = createWebSourceParamsExample();
        exampleParams.writeToParcel(p, 0);
        p.setDataPosition(0);

        WebSourceParams webSourceParams = WebSourceParams.CREATOR.createFromParcel(p);
        expect.that(webSourceParams.getRegistrationUri().toString())
                .isEqualTo(exampleParams.getRegistrationUri().toString());
        expect.that(webSourceParams.isDebugKeyAllowed())
                .isEqualTo(exampleParams.isDebugKeyAllowed());
        p.recycle();
    }

    private WebSourceRegistrationRequest createWebSourceRegistrationRequestExample() {
        return new WebSourceRegistrationRequest.Builder(
                        List.of(
                                new WebSourceParams.Builder(Uri.parse("https://registration-uri"))
                                        .setDebugKeyAllowed(true)
                                        .build()),
                        Uri.parse("https://top-origin"))
                .setInputEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1))
                .setVerifiedDestination(Uri.parse("https://verified-destination"))
                .setAppDestination(Uri.parse("android-app://app-destination"))
                .setWebDestination(Uri.parse("https://web-destination"))
                .build();
    }

    @Test
    public void testWebSourceRegistrationRequest() {
        WebSourceRegistrationRequest request = createWebSourceRegistrationRequestExample();

        expect.that(request.describeContents()).isEqualTo(0);
        expect.that(request.getSourceParams().get(0).getRegistrationUri().toString())
                .isEqualTo("https://registration-uri");
        expect.that(request.getSourceParams().get(0).isDebugKeyAllowed()).isTrue();
        expect.that(request.getTopOriginUri().toString()).isEqualTo("https://top-origin");
        expect.that(((KeyEvent) request.getInputEvent()).getAction())
                .isEqualTo(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1).getAction());
        expect.that(request.getVerifiedDestination().toString())
                .isEqualTo("https://verified-destination");
        expect.that(request.getAppDestination().toString())
                .isEqualTo("android-app://app-destination");
        expect.that(request.getWebDestination().toString()).isEqualTo("https://web-destination");
    }

    @Test
    public void testWebSourceRegistrationRequestParceling() {
        Parcel p = Parcel.obtain();
        WebSourceRegistrationRequest exampleRequest = createWebSourceRegistrationRequestExample();
        exampleRequest.writeToParcel(p, 0);
        p.setDataPosition(0);
        WebSourceRegistrationRequest request =
                WebSourceRegistrationRequest.CREATOR.createFromParcel(p);

        expect.that(request.describeContents()).isEqualTo(exampleRequest.describeContents());
        expect.that(request.getSourceParams().get(0).getRegistrationUri().toString())
                .isEqualTo(exampleRequest.getSourceParams().get(0).getRegistrationUri().toString());
        expect.that(request.getSourceParams().get(0).isDebugKeyAllowed())
                .isEqualTo(exampleRequest.getSourceParams().get(0).isDebugKeyAllowed());
        expect.that(request.getTopOriginUri().toString())
                .isEqualTo(exampleRequest.getTopOriginUri().toString());
        expect.that(((KeyEvent) request.getInputEvent()).getAction())
                .isEqualTo(((KeyEvent) exampleRequest.getInputEvent()).getAction());
        expect.that(request.getVerifiedDestination().toString())
                .isEqualTo(exampleRequest.getVerifiedDestination().toString());
        expect.that(request.getAppDestination().toString())
                .isEqualTo(exampleRequest.getAppDestination().toString());
        expect.that(request.getWebDestination().toString())
                .isEqualTo(exampleRequest.getWebDestination().toString());
        p.recycle();
    }

    private WebTriggerParams createWebTriggerParamsExample() {
        return new WebTriggerParams.Builder(Uri.parse("https://registration-uri"))
                .setDebugKeyAllowed(true)
                .build();
    }

    @Test
    public void testWebTriggerParams() {
        WebTriggerParams webTriggerParams = createWebTriggerParamsExample();

        expect.that(webTriggerParams.describeContents()).isEqualTo(0);
        expect.that(webTriggerParams.getRegistrationUri().toString())
                .isEqualTo("https://registration-uri");
        expect.that(webTriggerParams.isDebugKeyAllowed()).isTrue();
    }

    @Test
    public void testWebTriggerParamsParceling() {
        Parcel p = Parcel.obtain();
        WebTriggerParams exampleParams = createWebTriggerParamsExample();
        exampleParams.writeToParcel(p, 0);
        p.setDataPosition(0);
        WebTriggerParams params = WebTriggerParams.CREATOR.createFromParcel(p);

        expect.that(params.describeContents()).isEqualTo(exampleParams.describeContents());
        expect.that(params.getRegistrationUri().toString())
                .isEqualTo(exampleParams.getRegistrationUri().toString());
        expect.that(params.isDebugKeyAllowed()).isEqualTo(exampleParams.isDebugKeyAllowed());
        p.recycle();
    }

    private WebTriggerRegistrationRequest createWebTriggerRegistrationRequestExample() {
        return new WebTriggerRegistrationRequest.Builder(
                        List.of(
                                new WebTriggerParams.Builder(Uri.parse("https://registration-uri"))
                                        .setDebugKeyAllowed(true)
                                        .build()),
                        Uri.parse("https://destination"))
                .build();
    }

    @Test
    public void testWebTriggerRegistrationRequest() {
        WebTriggerRegistrationRequest request = createWebTriggerRegistrationRequestExample();

        expect.that(request.describeContents()).isEqualTo(0);
        expect.that(request.getTriggerParams().get(0).getRegistrationUri().toString())
                .isEqualTo("https://registration-uri");
        expect.that(request.getTriggerParams().get(0).isDebugKeyAllowed()).isTrue();
        expect.that(request.getDestination().toString()).isEqualTo("https://destination");
    }

    @Test
    public void testWebTriggerRegistrationRequestParceling() {
        Parcel p = Parcel.obtain();
        WebTriggerRegistrationRequest exampleRequest = createWebTriggerRegistrationRequestExample();
        exampleRequest.writeToParcel(p, 0);
        p.setDataPosition(0);
        WebTriggerRegistrationRequest request =
                WebTriggerRegistrationRequest.CREATOR.createFromParcel(p);

        expect.that(request.describeContents()).isEqualTo(exampleRequest.describeContents());
        expect.that(request.getTriggerParams().get(0).getRegistrationUri().toString())
                .isEqualTo(
                        exampleRequest.getTriggerParams().get(0).getRegistrationUri().toString());
        expect.that(request.getTriggerParams().get(0).isDebugKeyAllowed())
                .isEqualTo(exampleRequest.getTriggerParams().get(0).isDebugKeyAllowed());
        expect.that(request.getDestination().toString())
                .isEqualTo(exampleRequest.getDestination().toString());
        p.recycle();
    }
}
