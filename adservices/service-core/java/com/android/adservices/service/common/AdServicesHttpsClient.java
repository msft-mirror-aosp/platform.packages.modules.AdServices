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

package com.android.adservices.service.common;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.LogUtil;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.HttpsURLConnection;

/**
 * This is an HTTPS client to be used by the PP API services. The primary uses of this client
 * include fetching payloads from ad tech-provided URLs and reporting on generated reporting URLs
 * through GET calls.
 */
public class AdServicesHttpsClient {

    private final int mConnectTimeoutMs;
    private final int mReadTimeoutMs;
    private final long mMaxBytes;
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    // Setting default max content size to 1024 * 1024 which is ~ 1MB
    private static final long DEFAULT_MAX_BYTES = 1048576;
    private static final String CONTENT_SIZE_ERROR = "Content size exceeds limit!";
    private final ListeningExecutorService mExecutorService;

    /**
     * Create an HTTPS client with the input {@link ExecutorService} and initial connect and read
     * timeouts (in milliseconds).
     *
     * @param executorService an {@link ExecutorService} that allows connection and fetching to be
     *     executed outside the main calling thread
     * @param connectTimeoutMs the timeout, in milliseconds, for opening an initial link with to a
     *     target resource using this client. If set to 0, this timeout is interpreted as infinite
     *     (see {@link URLConnection#setConnectTimeout(int)}).
     * @param readTimeoutMs the timeout, in milliseconds, for reading a response from a target
     *     address using this client. If set to 0, this timeout is interpreted as infinite (see
     *     {@link URLConnection#setReadTimeout(int)}).
     * @param maxBytes The maximum size of an HTTPS response in bytes.
     */
    public AdServicesHttpsClient(
            ExecutorService executorService,
            int connectTimeoutMs,
            int readTimeoutMs,
            long maxBytes) {
        mConnectTimeoutMs = connectTimeoutMs;
        mReadTimeoutMs = readTimeoutMs;
        this.mExecutorService = MoreExecutors.listeningDecorator(executorService);
        this.mMaxBytes = maxBytes;
    }

    /**
     * Create an HTTPS client with the input {@link ExecutorService} and default initial connect and
     * read timeouts. This will also contain the default size of an HTTPS response, 1 MB.
     *
     * @param executorService an {@link ExecutorService} that allows connection and fetching to be
     *     executed outside the main calling thread
     */
    public AdServicesHttpsClient(ExecutorService executorService) {
        this(executorService, DEFAULT_TIMEOUT_MS, DEFAULT_TIMEOUT_MS, DEFAULT_MAX_BYTES);
    }


    /** Opens the Url Connection */
    @NonNull
    private URLConnection openUrl(@NonNull URL url) throws IOException {
        Objects.requireNonNull(url);

        return url.openConnection();
    }

    @NonNull
    private URL toUrl(@NonNull Uri uri) {
        Objects.requireNonNull(uri);
        Preconditions.checkArgument(
                uri.getScheme().equalsIgnoreCase("https"), "Only HTTPS connections are allowed!");

        URL url;
        try {
            url = new URL(uri.toString());
        } catch (MalformedURLException e) {
            LogUtil.d(e, "Uri is malformed! ");
            throw new IllegalArgumentException("Uri is malformed!");
        }
        return url;
    }

    @NonNull
    private HttpsURLConnection setupConnection(@NonNull URL url) throws IOException {
        Objects.requireNonNull(url);

        // We validated that the URL is https in toUrl
        HttpsURLConnection urlConnection = (HttpsURLConnection) openUrl(url);
        urlConnection.setConnectTimeout(mConnectTimeoutMs);
        urlConnection.setReadTimeout(mReadTimeoutMs);
        // Setting true explicitly to follow redirects
        urlConnection.setInstanceFollowRedirects(true);
        return urlConnection;
    }

    /**
     * Performs a GET request on the given URI in order to fetch a payload.
     *
     * @param uri a {@link Uri} pointing to a target server, converted to a URL for fetching
     * @return a string containing the fetched payload
     */
    @NonNull
    public ListenableFuture<String> fetchPayload(@NonNull Uri uri) {
        Objects.requireNonNull(uri);

        return mExecutorService.submit(() -> doFetchPayload(uri));
    }

    private String doFetchPayload(@NonNull Uri uri) throws IOException {
        URL url = toUrl(uri);
        HttpsURLConnection urlConnection;

        try {
            urlConnection = setupConnection(url);
        } catch (IOException e) {
            LogUtil.d(e, "Failed to open URL");
            throw new IllegalArgumentException("Failed to open URL!");
        }

        try {
            // TODO(b/237342352): Both connect and read timeouts are kludged in this method and if
            //  necessary need to be separated
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            int responseCode = urlConnection.getResponseCode();
            if (isSuccessfulResponse(responseCode)) {
                return fromInputStream(in, urlConnection.getContentLengthLong());
            } else {
                InputStream errorStream = urlConnection.getErrorStream();
                if (!Objects.isNull(errorStream)) {
                    String errorMessage =
                            fromInputStream(
                                    urlConnection.getErrorStream(),
                                    urlConnection.getContentLengthLong());
                    String exceptionMessage =
                            String.format(
                                    Locale.US,
                                    "Server returned an error with code %d and message:" + " %s",
                                    responseCode,
                                    errorMessage);

                    LogUtil.d(exceptionMessage);
                    throw new IOException(exceptionMessage);
                } else {
                    String exceptionMessage =
                            String.format(
                                    Locale.US,
                                    "Server returned an error with code %d and null" + " message",
                                    responseCode);
                    LogUtil.d(exceptionMessage);
                    throw new IOException(exceptionMessage);
                }
            }
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timed out while reading response!", e);
        } finally {
            maybeDisconnect(urlConnection);
        }
    }

    /**
     * Performs a GET request on a Uri to perform reporting
     *
     * @param uri Provided as a result of invoking buyer or seller javascript.
     * @return an int that represents the HTTP response code in a successful case
     */
    public ListenableFuture<Void> reportUrl(@NonNull Uri uri) {
        Objects.requireNonNull(uri);

        return mExecutorService.submit(() -> doReportUrl(uri));
    }

    private Void doReportUrl(@NonNull Uri uri) throws IOException {
        URL url = toUrl(uri);
        HttpsURLConnection urlConnection;

        try {
            urlConnection = setupConnection(url);
        } catch (IOException e) {
            LogUtil.d(e, "Failed to open URL");
            throw new IllegalArgumentException("Failed to open URL!");
        }

        try {
            // TODO(b/237342352): Both connect and read timeouts are kludged in this method and if
            //  necessary need to be separated
            int responseCode = urlConnection.getResponseCode();
            if (isSuccessfulResponse(responseCode)) {
                LogUtil.d("Successfully reported for URl: " + url);
                return null;
            } else {
                LogUtil.w("Failed to report for URL: " + url);
                InputStream errorStream = urlConnection.getErrorStream();
                if (!Objects.isNull(errorStream)) {
                    String errorMessage =
                            fromInputStream(
                                    urlConnection.getErrorStream(),
                                    urlConnection.getContentLengthLong());
                    String exceptionMessage =
                            String.format(
                                    Locale.US,
                                    "Server returned an error with code %d and message:" + " %s",
                                    responseCode,
                                    errorMessage);

                    LogUtil.d(exceptionMessage);
                    throw new IOException(exceptionMessage);
                } else {
                    String exceptionMessage =
                            String.format(
                                    Locale.US,
                                    "Server returned an error with code %d and null" + " message",
                                    responseCode);
                    LogUtil.d(exceptionMessage);
                    throw new IOException(exceptionMessage);
                }
            }
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timed out while reading response!", e);
        } finally {
            maybeDisconnect(urlConnection);
        }
    }

    private static void maybeDisconnect(@Nullable URLConnection urlConnection) {
        if (urlConnection == null) {
            return;
        }

        if (urlConnection instanceof HttpURLConnection) {
            HttpURLConnection httpUrlConnection = (HttpURLConnection) urlConnection;
            httpUrlConnection.disconnect();
        }
    }

    /**
     * @return the connect timeout, in milliseconds, when opening an initial link to a target
     *     address with this client
     */
    public int getConnectTimeoutMs() {
        return mConnectTimeoutMs;
    }

    /**
     * @return the read timeout, in milliseconds, when reading the response from a target address
     *     with this client
     */
    public int getReadTimeoutMs() {
        return mReadTimeoutMs;
    }

    /**
     * @return true if responseCode matches 2.*, i.e. 200, 204, 206
     */
    public static boolean isSuccessfulResponse(int responseCode) {
        return (responseCode / 100) == 2;
    }

    /**
     * Reads a {@link InputStream} and returns a {@code String}. To enforce content size limits, we
     * employ the following strategy: 1. If {@link URLConnection} cannot determine the content size,
     * we invoke {@code manualStreamToString(InputStream)} where we manually apply the content
     * restriction. 2. Otherwise, we invoke {@code streamToString(InputStream, long)}.
     *
     * @throws IOException if content size limit of is exceeded
     */
    @NonNull
    private String fromInputStream(@NonNull InputStream in, long size) throws IOException {
        Objects.requireNonNull(in);
        if (size == 0) {
            return "";
        } else if (size < 0) {
            return manualStreamToString(in);
        } else {
            return streamToString(in, size);
        }
    }

    @NonNull
    private String streamToString(@NonNull InputStream in, long size) throws IOException {
        Objects.requireNonNull(in);
        if (size > mMaxBytes) {
            throw new IOException(CONTENT_SIZE_ERROR);
        }
        return new String(ByteStreams.toByteArray(in), Charsets.UTF_8);
    }

    @NonNull
    private String manualStreamToString(@NonNull InputStream in) throws IOException {
        Objects.requireNonNull(in);
        ByteArrayOutputStream into = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        long total = 0;
        for (int n; 0 < (n = in.read(buf)); ) {
            total += n;
            if (total <= mMaxBytes) {
                into.write(buf, 0, n);
            } else {
                into.close();
                throw new IOException(CONTENT_SIZE_ERROR);
            }
        }
        into.close();
        return into.toString(Charsets.UTF_8);
    }
}
