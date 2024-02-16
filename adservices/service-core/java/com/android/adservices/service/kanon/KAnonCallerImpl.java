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

package com.android.adservices.service.kanon;

import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.CONTENT_LENGTH_HDR;
import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.REQUEST_PROPERTIES_OHTTP_CONTENT_TYPE;
import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE;
import static com.android.adservices.service.kanon.KAnonMessageEntity.KanonMessageEntityStatus.FAILED;
import static com.android.adservices.service.kanon.KAnonMessageEntity.KanonMessageEntityStatus.JOINED;
import static com.android.adservices.service.kanon.KAnonMessageEntity.KanonMessageEntityStatus.SIGNED;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;

import android.adservices.exceptions.AdServicesNetworkException;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.kanon.ClientParametersDao;
import com.android.adservices.data.kanon.DBClientParameters;
import com.android.adservices.data.kanon.DBServerParameters;
import com.android.adservices.data.kanon.ServerParametersDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.encryption.KAnonObliviousHttpEncryptorImpl;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.UserProfileIdManager;
import com.android.adservices.service.common.bhttp.BinaryHttpMessage;
import com.android.adservices.service.common.bhttp.BinaryHttpMessageDeserializer;
import com.android.adservices.service.common.bhttp.Fields;
import com.android.adservices.service.common.bhttp.RequestControlData;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpUtil;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.KAnonSignJoinException;
import com.android.adservices.service.kanon.KAnonMessageEntity.KanonMessageEntityStatus;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.json.JSONException;
import org.json.JSONObject;

import private_join_and_compute.anonymous_counting_tokens.AndroidRequestMetadata;
import private_join_and_compute.anonymous_counting_tokens.ClientParameters;
import private_join_and_compute.anonymous_counting_tokens.ClientPrivateParameters;
import private_join_and_compute.anonymous_counting_tokens.ClientPublicParameters;
import private_join_and_compute.anonymous_counting_tokens.GeneratedTokensRequestProto;
import private_join_and_compute.anonymous_counting_tokens.GetServerPublicParamsResponse;
import private_join_and_compute.anonymous_counting_tokens.GetTokensRequest;
import private_join_and_compute.anonymous_counting_tokens.GetTokensResponse;
import private_join_and_compute.anonymous_counting_tokens.MessagesSet;
import private_join_and_compute.anonymous_counting_tokens.RegisterClientRequest;
import private_join_and_compute.anonymous_counting_tokens.RegisterClientResponse;
import private_join_and_compute.anonymous_counting_tokens.RequestMetadata;
import private_join_and_compute.anonymous_counting_tokens.SchemeParameters;
import private_join_and_compute.anonymous_counting_tokens.ServerPublicParameters;
import private_join_and_compute.anonymous_counting_tokens.Token;
import private_join_and_compute.anonymous_counting_tokens.TokensResponse;
import private_join_and_compute.anonymous_counting_tokens.TokensSet;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class KAnonCallerImpl implements KAnonCaller {

    @NonNull private ObliviousHttpEncryptor mKAnonObliviousHttpEncryptor;
    @NonNull private ListeningExecutorService mLightweightExecutorService;
    @NonNull private AnonymousCountingTokens mAnonymousCountingTokens;
    @NonNull private Flags mFlags;
    @NonNull private UserProfileIdManager mUserProfileIdManager;
    @NonNull private AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private KAnonMessageManager mKAnonMessageManager;
    @NonNull private ClientParametersDao mClientParametersDao;
    @NonNull private ServerParametersDao mServerParametersDao;
    @NonNull private BinaryHttpMessageDeserializer mBinaryHttpMessageDeserializer;
    @NonNull private AdServicesLogger mAdServicesLogger;
    @NonNull private UUID mClientId;
    @Nullable private String mServerParamVersion;
    @Nullable private String mClientParamsVersion;
    @Nullable private SchemeParameters mSchemeParameters;
    @Nullable private ClientParameters mClientParameters;
    @Nullable private ServerPublicParameters mServerPublicParameters;
    @Nullable private RequestMetadata mRequestMetadata;

    private final DevContext DEV_CONTEXT_DISABLED = DevContext.createForDevOptionsDisabled();
    private final int SIGN_BATCH_SIZE;
    // TODO(b/324536970): Control this via flag
    private final String BINARY_HTTP_AUTHORITY_URL =
            "staging-chromekanonymity-pa.sandbox.googleapis.com";
    private final String JOIN_VERSION = "/v2/";
    private final String BB_SIGNATURE_JSON_KEY = "bb_signature";
    private final String NONCE_BYTES_JSON_KEY = "nonce_bytes";
    private final String TOKEN_V0_JSON_KEY = "token_v0";
    private final String ACT_JSON_KEY = "act";
    private final String HTTPS = "https";

    public KAnonCallerImpl(
            @NonNull ExecutorService lightweightExecutorService,
            @NonNull AnonymousCountingTokens anonymousCountingTokens,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull ClientParametersDao clientParametersDao,
            @NonNull ServerParametersDao serverParametersDao,
            @NonNull UserProfileIdManager userProfileIdManager,
            @NonNull BinaryHttpMessageDeserializer binaryHttpMessageDeserializer,
            @NonNull Flags flags,
            @NonNull ObliviousHttpEncryptor kAnonObliviousHttpEncryptor,
            @NonNull KAnonMessageManager kAnonMessageManager,
            @NonNull AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(anonymousCountingTokens);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(clientParametersDao);
        Objects.requireNonNull(serverParametersDao);
        Objects.requireNonNull(userProfileIdManager);
        Objects.requireNonNull(kAnonObliviousHttpEncryptor);
        Objects.requireNonNull(binaryHttpMessageDeserializer);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(kAnonMessageManager);
        Objects.requireNonNull(adServicesLogger);

        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mAnonymousCountingTokens = anonymousCountingTokens;
        mUserProfileIdManager = userProfileIdManager;
        mKAnonObliviousHttpEncryptor = kAnonObliviousHttpEncryptor;
        mKAnonMessageManager = kAnonMessageManager;
        mServerParametersDao = serverParametersDao;
        mClientParametersDao = clientParametersDao;
        mBinaryHttpMessageDeserializer = binaryHttpMessageDeserializer;
        mFlags = flags;
        mClientId = mUserProfileIdManager.getOrCreateId();
        mAdServicesLogger = adServicesLogger;
        mRequestMetadata =
                RequestMetadata.newBuilder()
                        .setAndroidRequestMetadata(
                                AndroidRequestMetadata.newBuilder()
                                        .setClientId(
                                                ByteString.copyFrom(
                                                        mClientId.toString().getBytes()))
                                        .build())
                        .setAuthTypeValue(RequestMetadata.AuthType.AUTH_DEVICE_ATTESTATION_VALUE)
                        .build();
        mFlags = flags;
        SIGN_BATCH_SIZE = mFlags.getFledgeKAnonSignBatchSize();
        mAdServicesHttpsClient = adServicesHttpsClient;
        mSchemeParameters = KAnonUtil.getSchemeParameters();
    }

    /**
     * This method takes a list of {@link KAnonMessageEntity} as an argument and processes them by
     * making sign and join calls.
     *
     * <p>The flow of this method is as follows:
     *
     * <p>1. Check if the client parameters are present in the database. If not, fetch server params
     * generate new client params and register them with the server.
     *
     * <p>2. For each message in the list of messages, generate a token request, make a GET call to
     * the server to get the tokens and recover the tokens from the response using ACT library.
     *
     * <p>3. For each message in the list of messages, make a POST call to the server to perform
     * join call by sending the corresponding token along with the message.
     */
    @Override
    public void signAndJoinMessages(List<KAnonMessageEntity> messageEntities) {
        ListenableFuture<Void> getOrUpdateClientAndServerParametersFuture =
                FluentFuture.from(getOrUpdateServerAndClientParameters());

        ListenableFuture<Void> signJoinFuture =
                FluentFuture.from(getOrUpdateClientAndServerParametersFuture)
                        .transformAsync(
                                ignoredVoid -> performSignAndJoinInBatches(messageEntities),
                                mLightweightExecutorService);

        Futures.addCallback(
                signJoinFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        LogUtil.d("Sign join process finished");
                        mAdServicesLogger.logKAnonSignJoinStatus();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // Because we are using Futures#whenAllComplete, we will not be catching
                        // errors here.
                        mAdServicesLogger.logKAnonSignJoinStatus();
                    }
                },
                mLightweightExecutorService);
    }

    /**
     * This method creates batches of the given list of {@link KAnonMessageEntity} and performs sign
     * and join requests.
     */
    private ListenableFuture<Void> performSignAndJoinInBatches(List<KAnonMessageEntity> messages) {
        List<ListenableFuture<Void>> signAndJoinBatchesFutures = new ArrayList<>();
        for (int i = 0; i < messages.size(); i = i + SIGN_BATCH_SIZE) {
            List<KAnonMessageEntity> messagesSublist =
                    messages.subList(i, Math.min(messages.size(), i + SIGN_BATCH_SIZE));
            signAndJoinBatchesFutures.add(
                    FluentFuture.from(immediateVoidFuture())
                            .transformAsync(
                                    ignoredVoid -> performSignRequest(messagesSublist),
                                    mLightweightExecutorService)
                            .catchingAsync(
                                    KAnonSignJoinException.class,
                                    e -> {
                                        LogUtil.d("Error in sign request method");
                                        return FluentFuture.from(
                                                        updateMessagesStatusInDatabase(
                                                                messagesSublist, FAILED))
                                                .transformAsync(
                                                        ignoredVoid -> immediateFailedFuture(e),
                                                        mLightweightExecutorService);
                                    },
                                    mLightweightExecutorService)
                            .transformAsync(
                                    tokensSet -> performJoinRequest(messagesSublist, tokensSet),
                                    mLightweightExecutorService));
        }
        return Futures.whenAllComplete(signAndJoinBatchesFutures)
                .call(() -> null, mLightweightExecutorService);
    }

    /**
     * This method checks if the client and server parameters are present in the database. If not,
     * it fetches new server parameters and generates new client parameters.
     */
    private ListenableFuture<Void> getOrUpdateServerAndClientParameters() {
        Optional<DBClientParameters> optionalDBClientParameters =
                fetchActiveClientParametersFromDBIfPresent();
        List<DBServerParameters> dbServerParametersList =
                mServerParametersDao.getActiveServerParameters(Instant.now());

        if (optionalDBClientParameters.isPresent() && !dbServerParametersList.isEmpty()) {
            DBClientParameters dbClientParameters = optionalDBClientParameters.get();
            return Futures.submit(
                    () ->
                            updateServerAndClientParams(
                                    dbClientParameters, dbServerParametersList.get(0)),
                    mLightweightExecutorService);
        } else {
            // Get new server and client parameters.
            return FluentFuture.from(fetchNewServerParameters())
                    .transformAsync(
                            getServerPublicParamsResponse ->
                                    immediateFuture(
                                            generateNewClientParameters(
                                                    getServerPublicParamsResponse)),
                            mLightweightExecutorService)
                    .transformAsync(
                            getServerPublicParamsResponse ->
                                    registerNewClientParameters(getServerPublicParamsResponse),
                            mLightweightExecutorService)
                    .transformAsync(
                            clientAndServerResponsePair ->
                                    persistClientAndServerParams(clientAndServerResponsePair),
                            mLightweightExecutorService);
        }
    }

    /**
     * This method reads the given {@link DBServerParameters} and {@link DBClientParameters} and
     * updates the private fields of the class.
     */
    private void updateServerAndClientParams(
            DBClientParameters dbClientParameters, DBServerParameters dbServerParameters) {
        try {
            mClientParamsVersion = dbClientParameters.getClientParamsVersion();
            mClientParameters =
                    ClientParameters.newBuilder()
                            .setPrivateParameters(
                                    ClientPrivateParameters.parseFrom(
                                            dbClientParameters.getClientPrivateParameters()))
                            .setPublicParameters(
                                    ClientPublicParameters.parseFrom(
                                            dbClientParameters.getClientPublicParameters()))
                            .build();

            mServerParamVersion = dbServerParameters.getServerParamsVersion();
            mServerPublicParameters =
                    ServerPublicParameters.parseFrom(
                            dbServerParameters.getServerPublicParameters());
        } catch (InvalidProtocolBufferException t) {
            throw new KAnonSignJoinException(
                    "Error while parsing client and server params from from database");
        }
    }

    /** This method deletes the old server and client parameters and persists the new parameters. */
    private ListenableFuture<Void> persistClientAndServerParams(
            Pair<RegisterClientResponse, GetServerPublicParamsResponse>
                    clientAndServerResponsePair) {
        RegisterClientResponse registerClientResponse = clientAndServerResponsePair.first;
        GetServerPublicParamsResponse getServerPublicParamsResponse =
                clientAndServerResponsePair.second;
        mClientParamsVersion = registerClientResponse.getClientParamsVersion();
        byte[] clientPrivateParameterBytes = mClientParameters.getPrivateParameters().toByteArray();
        byte[] clientPublicParametersBytes = mClientParameters.getPublicParameters().toByteArray();
        long clientParamsExpiryInSeconds =
                registerClientResponse.getClientParamsExpiry().getSeconds();
        DBClientParameters clientParametersToSave =
                DBClientParameters.builder()
                        .setClientPrivateParameters(clientPrivateParameterBytes)
                        .setClientPublicParameters(clientPublicParametersBytes)
                        .setClientId(mClientId)
                        .setClientParametersExpiryInstant(
                                Instant.ofEpochSecond(clientParamsExpiryInSeconds))
                        .setClientParamsVersion(mClientParamsVersion)
                        .build();

        mServerParamVersion = getServerPublicParamsResponse.getServerParamsVersion();
        mServerPublicParameters = getServerPublicParamsResponse.getServerPublicParams();
        // TODO(b/324253516): remove sign/join expiry ttls
        DBServerParameters serverParametersToSave =
                DBServerParameters.builder()
                        .setServerPublicParameters(mServerPublicParameters.toByteArray())
                        .setCreationInstant(Instant.now())
                        .setServerParamsVersion(mServerParamVersion)
                        .setServerParamsJoinExpiryInstant(Instant.now().plusSeconds(10000))
                        .setServerParamsSignExpiryInstant(Instant.now().plusSeconds(10000))
                        .build();
        return Futures.submit(
                () -> {
                    mClientParametersDao.deleteAllClientParameters();
                    mServerParametersDao.deleteAllServerParameters();
                    mServerParametersDao.insertServerParameters(serverParametersToSave);
                    mClientParametersDao.insertClientParameters(clientParametersToSave);
                },
                mLightweightExecutorService);
    }

    /**
     * This method fetches the active client parameters from the database.
     *
     * <p>If the client parameters are not found in the database, it returns an empty optional.
     *
     * <p>If the client parameters are found in the database, but the client id does not match with
     * the user profile id, it returns an empty optional.
     *
     * <p>If the client parameters are found in the database and the client id matches with the user
     * profile id, it returns the client parameters.
     */
    private Optional<DBClientParameters> fetchActiveClientParametersFromDBIfPresent() {
        List<DBClientParameters> clientParametersFromDb =
                mClientParametersDao.getActiveClientParameters(Instant.now());
        if (clientParametersFromDb.isEmpty()) {
            LogUtil.d("Client parameters not found in the database");
            return Optional.empty();
        } else {
            // TODO(b/324392549): Remove this after updating client param fetch query to search
            // with client id as well.
            DBClientParameters dbClientParameters = clientParametersFromDb.get(0);
            if (!mClientId.equals(dbClientParameters.getClientId())) {
                LogUtil.d(
                        "Active client parameters found, but clientId  does not match with"
                                + " userProfileId");
                return Optional.empty();
            }
            return Optional.of(dbClientParameters);
        }
    }

    private ListenableFuture<GetServerPublicParamsResponse> fetchNewServerParameters()
            throws KAnonSignJoinException {
        LogUtil.d("Fetching server parameters for KAnon Sign requests");
        Uri getServerParamsUri = Uri.parse(mFlags.getFledgeKAnonFetchServerParamsUrl());
        return FluentFuture.from(
                        immediateFuture(
                                AdServicesHttpClientRequest.builder()
                                        .setUri(getServerParamsUri)
                                        .setRequestProperties(
                                                REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                                        .setHttpMethodType(AdServicesHttpUtil.HttpMethodType.GET)
                                        .setDevContext(DEV_CONTEXT_DISABLED)
                                        .build()))
                .transformAsync(
                        fetchParamRequest ->
                                mAdServicesHttpsClient.performRequestGetResponseInBase64String(
                                        fetchParamRequest),
                        mLightweightExecutorService)
                .catchingAsync(
                        AdServicesNetworkException.class,
                        e -> {
                            LogUtil.d("Failure in http call");
                            return immediateFailedFuture(e);
                        },
                        mLightweightExecutorService)
                .transformAsync(
                        response -> {
                            GetServerPublicParamsResponse getServerPublicParamsResponse;
                            try {
                                getServerPublicParamsResponse =
                                        GetServerPublicParamsResponse.parseFrom(
                                                BaseEncoding.base64()
                                                        .decode(response.getResponseBody()));
                            } catch (InvalidProtocolBufferException e) {
                                throw new KAnonSignJoinException(
                                        "Error while fetching server public response from server",
                                        e);
                            }
                            return immediateFuture(getServerPublicParamsResponse);
                        },
                        mLightweightExecutorService);
    }

    /** This method using {@link AnonymousCountingTokens} to generate new client parameters. */
    private GetServerPublicParamsResponse generateNewClientParameters(
            GetServerPublicParamsResponse getServerPublicParamsResponse) {
        try {
            mServerPublicParameters = getServerPublicParamsResponse.getServerPublicParams();
            mServerParamVersion = getServerPublicParamsResponse.getServerParamsVersion();
            mClientParameters =
                    mAnonymousCountingTokens.generateClientParameters(
                            mSchemeParameters, mServerPublicParameters);
        } catch (InvalidProtocolBufferException e) {
            throw new KAnonSignJoinException("Error while generating client params", e);
        }
        return getServerPublicParamsResponse;
    }

    /**
     * This method fetches the server parameters by making an HTTP call to the sign server, uses the
     * ACT library to generate the Client parameters and registers those client parameters by making
     * an HTTP call to the sign server.
     */
    private ListenableFuture<Pair<RegisterClientResponse, GetServerPublicParamsResponse>>
            registerNewClientParameters(GetServerPublicParamsResponse getServerPublicParamsResponse)
                    throws InvalidProtocolBufferException {

        RegisterClientRequest registerClientRequest =
                RegisterClientRequest.newBuilder()
                        .setClientPublicParams(mClientParameters.getPublicParameters())
                        .setRequestMetadata(mRequestMetadata)
                        .setServerParamsVersion(mServerParamVersion)
                        .build();
        Uri registerClientUri = Uri.parse(mFlags.getFledgeKAnonRegisterClientParametersUrl());

        return FluentFuture.from(
                        immediateFuture(
                                AdServicesHttpClientRequest.builder()
                                        .setUri(registerClientUri)
                                        .setRequestProperties(
                                                REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                                        .setHttpMethodType(AdServicesHttpUtil.HttpMethodType.POST)
                                        .setDevContext(DEV_CONTEXT_DISABLED)
                                        .setBodyInBytes(registerClientRequest.toByteArray())
                                        .build()))
                .transformAsync(
                        registerClientParametersRequest ->
                                mAdServicesHttpsClient.performRequestGetResponseInBase64String(
                                        registerClientParametersRequest),
                        mLightweightExecutorService)
                .catchingAsync(
                        AdServicesNetworkException.class,
                        e -> {
                            LogUtil.d("Failure in http call");
                            return immediateFailedFuture(e);
                        },
                        mLightweightExecutorService)
                .transformAsync(
                        response -> {
                            RegisterClientResponse registerClientResponse;
                            try {
                                byte[] responseInBytes =
                                        BaseEncoding.base64().decode(response.getResponseBody());
                                registerClientResponse =
                                        RegisterClientResponse.parseFrom(responseInBytes);
                            } catch (InvalidProtocolBufferException e) {
                                throw new KAnonSignJoinException(
                                        "Error while parsing Register Client Response");
                            }
                            return immediateFuture(
                                    Pair.create(
                                            registerClientResponse, getServerPublicParamsResponse));
                        },
                        mLightweightExecutorService);
    }

    private ListenableFuture<Void> updateMessagesStatusInDatabase(
            List<KAnonMessageEntity> messages, @KanonMessageEntityStatus int status) {
        LogUtil.d("Updating message status to : " + status);
        return Futures.submit(
                () -> mKAnonMessageManager.updateMessagesStatus(messages, status),
                mLightweightExecutorService);
    }

    /**
     * This method returns a {@link TokensSet} corresponding to the list of {@link
     * KAnonMessageEntity}. This method uses the ACT library to generate token request, makes an
     * HTTP call to getTokenRequest endpoint and recovers the tokens from the response of that HTTP
     * call.
     */
    private FluentFuture<TokensSet> performSignRequest(List<KAnonMessageEntity> messageEntities) {
        // Generate Tokens Request using ACT JNI wrapper
        List<String> messagesInString =
                messageEntities.stream()
                        .map(KAnonMessageEntity::getHashSet)
                        .collect(Collectors.toList());
        MessagesSet messagesSet = MessagesSet.newBuilder().addAllMessage(messagesInString).build();
        Pair<GeneratedTokensRequestProto, AdServicesHttpClientRequest>
                pairTokensRequestProtoHttpRequest = generateTokenRequest(messagesSet);
        GeneratedTokensRequestProto generatedTokensRequestProto =
                pairTokensRequestProtoHttpRequest.first;
        AdServicesHttpClientRequest httpGetTokenRequest = pairTokensRequestProtoHttpRequest.second;
        return FluentFuture.from(
                        // This method will fail if we try to get tokens for an already signed
                        // message.
                        mAdServicesHttpsClient.performRequestGetResponseInBase64String(
                                httpGetTokenRequest))
                .catchingAsync(
                        AdServicesNetworkException.class,
                        e -> {
                            LogUtil.d("Failure in http call");
                            return immediateFailedFuture(e);
                        },
                        mLightweightExecutorService)
                .transformAsync(
                        getTokensResponseHTTP ->
                                recoverTokens(
                                        messageEntities,
                                        messagesSet,
                                        generatedTokensRequestProto,
                                        getTokensResponseHTTP),
                        mLightweightExecutorService)
                .catchingAsync(
                        KAnonSignJoinException.class,
                        e -> {
                            LogUtil.d("Cannot recover tokens");
                            return immediateFailedFuture(e);
                        },
                        mLightweightExecutorService);
    }

    private Pair<GeneratedTokensRequestProto, AdServicesHttpClientRequest> generateTokenRequest(
            MessagesSet messagesSet) {
        try {
            GeneratedTokensRequestProto generatedTokensRequestProto =
                    mAnonymousCountingTokens.generateTokensRequest(
                            messagesSet,
                            mSchemeParameters,
                            mClientParameters.getPublicParameters(),
                            mClientParameters.getPrivateParameters(),
                            mServerPublicParameters);
            GetTokensRequest getTokensRequest =
                    GetTokensRequest.newBuilder()
                            .setRequestMetadata(mRequestMetadata)
                            .setClientParamsVersion(mClientParamsVersion)
                            .setTokensRequest(generatedTokensRequestProto.getTokenRequest())
                            .addAllClientFingerprintsBytes(
                                    generatedTokensRequestProto.getFingerprintsBytesList())
                            .build();
            AdServicesHttpClientRequest httpGetTokensRequest =
                    AdServicesHttpClientRequest.builder()
                            .setUri(Uri.parse(mFlags.getFledgeKAnonGetTokensUrl()))
                            .setRequestProperties(REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                            .setHttpMethodType(AdServicesHttpUtil.HttpMethodType.POST)
                            .setBodyInBytes(getTokensRequest.toByteArray())
                            .setDevContext(DEV_CONTEXT_DISABLED)
                            .build();
            return Pair.create(generatedTokensRequestProto, httpGetTokensRequest);
        } catch (IllegalArgumentException | InvalidProtocolBufferException e) {
            throw new KAnonSignJoinException("Error while generating token request", e);
        }
    }

    private ListenableFuture<TokensSet> recoverTokens(
            List<KAnonMessageEntity> messageEntities,
            MessagesSet messagesSet,
            GeneratedTokensRequestProto generatedTokensRequestProto,
            AdServicesHttpClientResponse getTokensResponseHTTP)
            throws KAnonSignJoinException {
        LogUtil.d("Starting to recover tokens from the GetTokensResponse");
        GetTokensResponse getTokensResponse;
        try {
            getTokensResponse =
                    GetTokensResponse.parseFrom(
                            BaseEncoding.base64().decode(getTokensResponseHTTP.getResponseBody()));
        } catch (InvalidProtocolBufferException e) {
            throw new KAnonSignJoinException("Error while parsing get tokens response");
        }
        TokensResponse tokensResponse = getTokensResponse.getTokensResponse();
        boolean isTokensResponseVerified =
                mAnonymousCountingTokens.verifyTokensResponse(
                        messagesSet,
                        generatedTokensRequestProto.getTokenRequest(),
                        generatedTokensRequestProto.getTokensRequestPrivateState(),
                        tokensResponse,
                        mSchemeParameters,
                        mClientParameters.getPublicParameters(),
                        mClientParameters.getPrivateParameters(),
                        mServerPublicParameters);

        if (isTokensResponseVerified) {
            LogUtil.d("Tokens have been verified");
            return FluentFuture.from(updateMessagesStatusInDatabase(messageEntities, SIGNED))
                    .transformAsync(
                            ignoredVoid ->
                                    immediateFuture(
                                            mAnonymousCountingTokens.recoverTokens(
                                                    messagesSet,
                                                    generatedTokensRequestProto.getTokenRequest(),
                                                    generatedTokensRequestProto
                                                            .getTokensRequestPrivateState(),
                                                    tokensResponse,
                                                    mSchemeParameters,
                                                    mClientParameters.getPublicParameters(),
                                                    mClientParameters.getPrivateParameters(),
                                                    mServerPublicParameters)),
                            mLightweightExecutorService)
                    .catchingAsync(
                            InvalidProtocolBufferException.class,
                            t -> {
                                LogUtil.d("Error while recovering tokens, marking them as failed");
                                return FluentFuture.from(
                                                updateMessagesStatusInDatabase(
                                                        messageEntities, FAILED))
                                        .transformAsync(
                                                ignoredVoid ->
                                                        immediateFailedFuture(
                                                                new KAnonSignJoinException(
                                                                        "Error while recovering"
                                                                                + " tokens",
                                                                        t)),
                                                mLightweightExecutorService);
                            },
                            mLightweightExecutorService);
        } else {
            LogUtil.d("Verify tokens failed");
            return FluentFuture.from(updateMessagesStatusInDatabase(messageEntities, FAILED))
                    .transformAsync(
                            ignoredVoid ->
                                    immediateFailedFuture(
                                            new KAnonSignJoinException(
                                                    "Verify tokens response failed")),
                            mLightweightExecutorService);
        }
    }

    private ListenableFuture<Void> performJoinRequest(
            List<KAnonMessageEntity> messageEntities, TokensSet tokensSet) {
        List<ListenableFuture<Void>> joinFuturesList = new ArrayList<>();
        for (int i = 0; i < messageEntities.size(); i++) {
            LogUtil.d(
                    "Making join request for message with message adselection id: "
                            + messageEntities.get(i).getAdSelectionId());
            Token token = tokensSet.getTokens(i);
            KAnonMessageEntity currentMessage = messageEntities.get(i);
            joinFuturesList.add(
                    FluentFuture.from(doJoinRequest(currentMessage, token))
                            .transformAsync(
                                    joinResponse ->
                                            deserializeJoinRequest(
                                                    joinResponse,
                                                    currentMessage.getAdSelectionId()),
                                    mLightweightExecutorService)
                            .transformAsync(
                                    deserializedJoinRequest ->
                                            readAndUpdateStatusFromBinaryHttp(
                                                    deserializedJoinRequest, currentMessage),
                                    mLightweightExecutorService)
                            .catchingAsync(
                                    KAnonSignJoinException.class,
                                    Futures::immediateFailedFuture,
                                    mLightweightExecutorService));
        }
        return Futures.whenAllComplete(joinFuturesList)
                .call(() -> null, mLightweightExecutorService);
    }

    private BinaryHttpMessage createBinaryHttpRequest(
            KAnonMessageEntity message, Token currentToken) throws JSONException {

        String bbSignature =
                BaseEncoding.base64()
                        .encode(currentToken.getTokenV0().getBbSignature().toByteArray());
        JSONObject tokenV0 = new JSONObject().put(BB_SIGNATURE_JSON_KEY, bbSignature);
        JSONObject token = new JSONObject();
        String nonBytes = BaseEncoding.base64().encode(currentToken.getNonceBytes().toByteArray());
        token.put(NONCE_BYTES_JSON_KEY, nonBytes);
        token.put(TOKEN_V0_JSON_KEY, tokenV0);
        JSONObject objectForBhttp = new JSONObject().put(ACT_JSON_KEY, token);
        String body = objectForBhttp.toString();

        // Create a binary http message object for this request.
        return BinaryHttpMessage.knownLengthRequestBuilder(
                        RequestControlData.builder()
                                .setMethod(AdServicesHttpUtil.HttpMethodType.POST.name())
                                .setScheme(HTTPS)
                                .setAuthority(BINARY_HTTP_AUTHORITY_URL)
                                .setPath(JOIN_VERSION + message.getHashSet() + ":join")
                                .build())
                .setHeaderFields(
                        Fields.builder()
                                .appendField(
                                        CONTENT_LENGTH_HDR,
                                        Integer.toString(body.getBytes().length))
                                .appendField(
                                        "Date",
                                        DateTimeFormatter.ofPattern(
                                                        "EEE, dd MMM yyyy HH:mm:ss z",
                                                        Locale.ENGLISH)
                                                .withZone(ZoneId.of("GMT"))
                                                .format(Instant.now()))
                                .build())
                .setContent(body.getBytes())
                .build();
    }

    /** This method makes a JOIN for the given message and token. */
    private FluentFuture<AdServicesHttpClientResponse> doJoinRequest(
            KAnonMessageEntity message, Token currentToken) {
        try {
            BinaryHttpMessage binaryHttpMessage = createBinaryHttpRequest(message, currentToken);
            byte[] dataInBinaryHttpMessage = binaryHttpMessage.serialize();
            return FluentFuture.from(
                            mKAnonObliviousHttpEncryptor.encryptBytes(
                                    dataInBinaryHttpMessage,
                                    message.getAdSelectionId(),
                                    mFlags.getFledgeAuctionServerAuctionKeyFetchTimeoutMs()))
                    .transformAsync(
                            byteRequest ->
                                    immediateFuture(
                                            AdServicesHttpClientRequest.builder()
                                                    .setUri(
                                                            Uri.parse(
                                                                    mFlags.getFledgeKAnonJoinUrl()))
                                                    .setRequestProperties(
                                                            REQUEST_PROPERTIES_OHTTP_CONTENT_TYPE)
                                                    .setHttpMethodType(
                                                            AdServicesHttpUtil.HttpMethodType.POST)
                                                    .setBodyInBytes(byteRequest)
                                                    .setDevContext(DEV_CONTEXT_DISABLED)
                                                    .build()),
                            mLightweightExecutorService)
                    .transformAsync(
                            joinRequest ->
                                    mAdServicesHttpsClient.performRequestGetResponseInBase64String(
                                            joinRequest),
                            mLightweightExecutorService)
                    .catchingAsync(
                            AdServicesNetworkException.class,
                            Futures::immediateFailedFuture,
                            mLightweightExecutorService);
        } catch (Throwable t) {
            throw new KAnonSignJoinException("Error while making the join request", t);
        }
    }

    /**
     * This method decrypts the join call response using {@link KAnonObliviousHttpEncryptorImpl} and
     * then parses it into {@link BinaryHttpMessage}.
     */
    private ListenableFuture<BinaryHttpMessage> deserializeJoinRequest(
            AdServicesHttpClientResponse joinResponse, long contextId) {
        LogUtil.d("Deserializing the decrypted response");
        byte[] decryptedOhttpResponse =
                mKAnonObliviousHttpEncryptor.decryptBytes(
                        BaseEncoding.base64().decode(joinResponse.getResponseBody()), contextId);
        return immediateFuture(mBinaryHttpMessageDeserializer.deserialize(decryptedOhttpResponse));
    }

    /**
     * This method reads the {@link BinaryHttpMessage} response for the JOIN call and updates the
     * status for {@link KAnonMessageEntity} in the database.
     */
    private ListenableFuture<Void> readAndUpdateStatusFromBinaryHttp(
            BinaryHttpMessage binaryHttpMessage, KAnonMessageEntity kAnonMessageEntity) {
        if (binaryHttpMessage.getResponseControlData().getFinalStatusCode() == 200) {
            LogUtil.d(
                    "Updating message status in database for message : "
                            + kAnonMessageEntity.getAdSelectionId());
            return updateMessagesStatusInDatabase(List.of(kAnonMessageEntity), JOINED);
        } else {
            throw new KAnonSignJoinException(
                    "Join called failed: Binary Http message status: "
                            + binaryHttpMessage.getResponseControlData().getFinalStatusCode());
        }
    }
}
