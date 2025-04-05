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

package android.adservices.test.scenario.adservices.measurement.load.scenarios;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;
import android.os.Trace;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.base.Stopwatch;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Scenario
@RunWith(JUnit4.class)
public class CallRegisterSource extends AbstractTestAction {
    AtomicInteger mCookieGenerator = new AtomicInteger();

    @Test
    public void registerSourceView() {
        runRegisterSource(null);
    }

    @Test
    public void repeatedlyRegisterSourceView() {
        for (int i = 0; i < repeatCountOption.get(); i++) {
            delayPre();
            runRegisterSource(null);
        }
    }

    @Test
    public void registerSourceClick() {
        runRegisterSource(dispatchMotionEvent());
    }

    @Test
    public void repeatedlyRegisterSourceClick() {
        for (int i = 0; i < repeatCountOption.get(); i++) {
            delayPre();
            runRegisterSource(dispatchMotionEvent());
        }
    }

    private void runRegisterSource(@Nullable MotionEvent clickEvent) {
        final String path = SERVER_BASE_URI + ":" + DEFAULT_PORT + SOURCE_PATH;

        int cookie = mCookieGenerator.getAndIncrement();
        // TODO(b/407757222): Migrate to RbATrace after the flag rollout.
        final String metricSuffix;
        if (clickEvent == null) {
            metricSuffix = "_view";
        } else {
            metricSuffix = "_click";
        }

        Trace.beginAsyncSection("Test_CallRegisterSource#runRegisterSource" + metricSuffix, cookie);

        Stopwatch timer = Stopwatch.createStarted();
        MEASUREMENT_MANAGER.registerSource(
                Uri.parse(path),
                /* inputEvent */ clickEvent,
                CALLBACK_EXECUTOR,
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object ignoredResult) {
                        timer.stop();
                        Log.i(
                                TAG,
                                generateLog(
                                        "Latency=%dms, SDK=%s"
                                                .formatted(
                                                        timer.elapsed(TimeUnit.MILLISECONDS),
                                                        sdkOption.get())));
                        // TODO(b/407757222): Migrate to RbATrace after the flag rollout.
                        Trace.endAsyncSection(
                                "Test_CallRegisterSource#runRegisterSource" + metricSuffix, cookie);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        timer.stop();
                        Log.i(
                                TAG,
                                generateLog(
                                        "Latency=%dms, SDK=%s Error:%s"
                                                .formatted(
                                                        timer.elapsed(TimeUnit.MILLISECONDS),
                                                        sdkOption.get(),
                                                        error.getMessage())));
                        // TODO(b/407757222): Migrate to RbATrace after the flag rollout.
                        Trace.endAsyncSection(
                                "Test_CallRegisterSource#runRegisterSource" + metricSuffix, cookie);
                    }
                });
    }

    private MotionEvent dispatchMotionEvent() {
        MotionEvent me =
                MotionEvent.obtain(200, 300, MotionEvent.ACTION_BUTTON_PRESS, 1.0f, 0.0f, 0);
        me.setSource(InputDevice.SOURCE_ANY);
        InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(me, true);
        return me;
    }
}
