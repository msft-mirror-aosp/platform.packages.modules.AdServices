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

package com.android.adservices;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

/** Rule that checks for network connectivity before running a test */
public class TlsConnectionAvailableRule implements TestRule {

    private static final String HOST = "example.com";
    private static final int PORT = 443;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (!assertTlsConnectionSucceeds()) {
                    throw new AssumptionViolatedException(
                            "Don't run Key Attestation tests on devices without network"
                                    + " connectivity");
                }
                base.evaluate();
            }
        };
    }

    private static boolean assertTlsConnectionSucceeds() {
        return assertSslSocketSucceeds(HOST, PORT) && assertUrlConnectionSucceeds(HOST, PORT);
    }

    private static boolean assertSslSocketSucceeds(String host, int port) {
        try {
            SSLSocket s =
                    (SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket(host, port);
            s.startHandshake();
            return true;
        } catch (IOException | NoSuchAlgorithmException e) {
            return false;
        }
    }

    private static boolean assertUrlConnectionSucceeds(String host, int port) {
        try {
            URL url = new URL("https://" + host + ":" + port);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.getInputStream();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
