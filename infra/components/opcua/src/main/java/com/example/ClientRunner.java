/*
 * // Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * // SPDX-License-Identifier: MIT-0
 */
package com.example;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.client.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

public class ClientRunner {
    static {
        // Required for SecurityPolicy.Aes256_Sha256_RsaPss
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final Logger logger = LoggerFactory.getLogger(ClientRunner.class);

    private final CompletableFuture<OpcUaClient> future = new CompletableFuture<>();

    private DefaultTrustListManager trustListManager;

    private final ClientConfig clientConfig;
    private final String opcuaEndpointUrl;

    public ClientRunner(ClientConfig clientConfig, String opcuaEndpointUrl) throws Exception {
        this(clientConfig, opcuaEndpointUrl, false);
    }

    public ClientRunner(ClientConfig clientConfig, String opcuaEndpointUrl, boolean serverRequired) throws Exception {
        this.clientConfig = clientConfig;
        this.opcuaEndpointUrl = opcuaEndpointUrl;
        logger.info("Ignoring built in server. User has requested: {}", serverRequired);
    }

    private OpcUaClient createClient() throws Exception {
        try {
            Path securityTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "client", "security");
            Files.createDirectories(securityTempDir);
            if (!Files.exists(securityTempDir)) {
                throw new Exception("unable to create security dir: " + securityTempDir);
            }

            File pkiDir = securityTempDir.resolve("pki").toFile();

            logger.info("security dir: {}", securityTempDir.toAbsolutePath());
            logger.info("security pki dir: {}", pkiDir.getAbsolutePath());

            logger.info("Client Endpoint URL: {} ", this.opcuaEndpointUrl);

            KeyStoreLoader loader = new KeyStoreLoader().load(securityTempDir);

            trustListManager = new DefaultTrustListManager(pkiDir);

            DefaultClientCertificateValidator certificateValidator = new DefaultClientCertificateValidator(
                    trustListManager);

            return OpcUaClient.create(
                    this.opcuaEndpointUrl,
                    endpoints -> endpoints.stream()
                            .filter(clientConfig.endpointFilter())
                            .findFirst(),
                    configBuilder -> configBuilder
                            .setApplicationName(LocalizedText.english("eclipse milo opc-ua client"))
                            .setApplicationUri("urn:eclipse:milo:example:client")
                            .setKeyPair(loader.getClientKeyPair())
                            .setCertificate(loader.getClientCertificate())
                            .setCertificateChain(loader.getClientCertificateChain())
                            .setCertificateValidator(certificateValidator)
                            .setIdentityProvider(clientConfig.getIdentityProvider())
                            .setRequestTimeout(uint(5000))
                            .build());
        } catch (Exception e) {
            logger.error("Could not load keys {}", e.toString());
            return null;
        }
    }

    public void run() {
        logger.info("Creating OPC-UA Client");
        try {
            OpcUaClient client = createClient();

            // For the sake of the examples we will create mutual trust between the client
            // and
            // server so we can run them with security enabled by default.
            // If the client example is pointed at another server then the rejected
            // certificate
            // will need to be moved from the security "pki/rejected" directory to the
            // "pki/trusted/certs" directory.

            // Make the example server trust the example client certificate by default.
            // client.getConfig().getCertificate().ifPresent(
            // certificate ->
            // exampleServer.getServer().getConfig().getTrustListManager().addTrustedCertificate(certificate)
            // );

            // // Make the example client trust the example server certificate by default.
            // exampleServer.getServer().getConfig().getCertificateManager().getCertificates().forEach(
            // certificate ->
            // trustListManager.addTrustedCertificate(certificate)
            // );

            future.whenCompleteAsync((c, ex) -> {
                if (ex != null) {

                    logger.error("Error running example: {}", ex.getMessage(), ex);
                }

                try {
                    client.disconnect().get();

                    Stack.releaseSharedResources();
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Error disconnecting: {}", e.getMessage(), e);
                }

                try {
                    Thread.sleep(1000);
                    System.exit(0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });

            try {
                clientConfig.run(client, future);
                future.get(15, TimeUnit.SECONDS);
            } catch (Throwable t) {
                logger.error("Error running client example: {}", t.toString());
                future.completeExceptionally(t);
            }
        } catch (Throwable t) {
            logger.error("Error getting client for endpoint {}  -  {}", this.opcuaEndpointUrl, t.toString());

            future.completeExceptionally(t);

            try {
                Thread.sleep(1000);
                System.exit(0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // try {
        // Thread.sleep(999_999_999);
        // } catch (InterruptedException e) {
        // e.printStackTrace();
        // }
    }

}