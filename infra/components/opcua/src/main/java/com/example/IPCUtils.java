/*
 * // Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * // SPDX-License-Identifier: MIT-0
 */
package com.example;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// logger
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClientV2;
import software.amazon.awssdk.aws.greengrass.SubscribeToTopicResponseHandler;
import software.amazon.awssdk.aws.greengrass.model.*;

class IpcUtils {
    private static final Logger logger = LoggerFactory.getLogger(IpcUtils.class);

    private static IpcUtils instance;
    private static GreengrassCoreIPCClientV2 ipcClient;

    private IpcUtils() throws Exception {
        logger.debug("Initializing IPC Client");
        try {
            ipcClient = GreengrassCoreIPCClientV2.builder().build();

        } catch (Exception e) {
            logger.error("Exception occurred when initializing IPC.");
            e.printStackTrace();

        }
    }

    // singleton constructor
    public static IpcUtils getInstance() throws Exception {
        if (instance == null) {
            instance = new IpcUtils();
        }
        return instance;
    }

    // get Greengrass V2 IPC client
    // allows usage of one unique client per component
    public GreengrassCoreIPCClientV2 getIPCClient() {
        return ipcClient;
    }

    // publish messages to IOT Core
    protected static void publishToIoTCore(String topic, String message) throws TimeoutException, InterruptedException {
        PublishToIoTCoreRequest publishRequest = new PublishToIoTCoreRequest();
        publishRequest.setQos(QOS.AT_LEAST_ONCE);
        publishRequest.setTopicName(topic);
        // publish message as byte array
        publishRequest.withPayload(message.getBytes(StandardCharsets.UTF_8));
        // Try to send the message
        CompletableFuture<PublishToIoTCoreResponse> publishFuture = ipcClient
                .publishToIoTCoreAsync(publishRequest);

        try {
            publishFuture.get(60, TimeUnit.SECONDS);
            logger.debug("Successfully published IPC message to IoT Core");
        } catch (ExecutionException | TimeoutException ex) {
            logger.error("Failed to publish IPC message to IoT Core {}", ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // set interrupt flag
            logger.error("Publish IPC message to IoT Core interrupted {}", ex.getMessage());
            ex.printStackTrace();
        } catch (Exception ex) {
            logger.error("Publish IPC message failed {}", ex.getMessage());
            ex.printStackTrace();
        }
    }

    // subscribe for messages from IOT Core
    protected static void subscribeToIoTCore(String topic, IoTCoreMessageInterface iotCoreMessageInterface) {
        IoTCoreSubscriptionHandler iotCoreSubscriptionHandler = new IoTCoreSubscriptionHandler(iotCoreMessageInterface);
        SubscribeToIoTCoreRequest subscribeToIoTCoreRequest = new SubscribeToIoTCoreRequest();

        subscribeToIoTCoreRequest.setTopicName(topic);
        subscribeToIoTCoreRequest.setQos(QOS.AT_LEAST_ONCE);
        try {
            ipcClient.subscribeToIoTCore(subscribeToIoTCoreRequest,
                    iotCoreSubscriptionHandler);
            logger.debug("Successfully subscribed to IOT topic: {} ", topic);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // set interrupt flag
            logger.error("Subscribe to IoT Core interrupted {}", ex.getMessage());
            ex.printStackTrace();
        } catch (UnauthorizedError ex) {
            logger.error("Subscribe to IoT Core not authorized {}", ex.getMessage());
            ex.printStackTrace();
        } catch (Exception ex) {
            logger.error("Subscribe to IoT Core failed {}", ex.getMessage());
            ex.printStackTrace();
        }
    }

    // publish messages to IPC
    protected static void publishToIpc(String topic, String message)
            throws UnauthorizedError, InterruptedException {
        try {
            BinaryMessage binaryMessage = new BinaryMessage().withMessage(message.getBytes(StandardCharsets.UTF_8));
            PublishMessage publishMessage = new PublishMessage()
                    .withBinaryMessage(binaryMessage);
            PublishToTopicRequest publishToTopicRequest = new PublishToTopicRequest().withTopic(topic)
                    .withPublishMessage(publishMessage);
            PublishToTopicResponse resp = ipcClient.publishToTopic(publishToTopicRequest);
            logger.debug("Publish to IPC response: {}", resp);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // set interrupt flag
            logger.error("Publish to IPC interrupted {}", ex.getMessage());
            ex.printStackTrace();
        } catch (UnauthorizedError ex) {
            logger.error("Publish to IPC was not authorized {}", ex.getMessage());
            ex.printStackTrace();
        } catch (Exception ex) {
            logger.error("Publish to IPC was failed {}", ex.getMessage());
            ex.printStackTrace();
        }
    }

    // subscribe for messages from IPC
    protected static SubscribeToTopicResponseHandler subscribeToIpc(String topic,
            IPCMessageInterface ipcMessageInterface) {
        try {
            IPCSubscriptionHandler ipcSubscriptionHandler = new IPCSubscriptionHandler(ipcMessageInterface);
            SubscribeToTopicRequest request = new SubscribeToTopicRequest().withTopic(topic);
            GreengrassCoreIPCClientV2.StreamingResponse<SubscribeToTopicResponse, SubscribeToTopicResponseHandler> response = ipcClient
                    .subscribeToTopic(request, ipcSubscriptionHandler::onStreamEvent,
                            Optional.of(ipcSubscriptionHandler::onStreamError),
                            Optional.of(ipcSubscriptionHandler::onStreamClosed));

            logger.debug("Successfully subscribed to IPC topic: {} ", topic);
            response.getHandler(); // return handler to close the stream / unsubscribe later
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // set interrupt flag
            logger.error("Subscribe to IPC was interrupted {}", ex.getMessage());
            ex.printStackTrace();
        } catch (UnauthorizedError ex) {
            logger.error("Subscribe to IPC was not authorized {}", ex.getMessage());
            ex.printStackTrace();
        } catch (Exception ex) {
            logger.error("Subscribe to IPC failed {}", ex.getMessage());
            ex.printStackTrace();
        }

        // if in case of any error/exception return null
        return null;
    }

    protected static String getShadowData(String thingName, String shadowName) {
        try {
            GetThingShadowRequest getThingShadowRequest = new GetThingShadowRequest();
            getThingShadowRequest.setThingName(thingName);
            getThingShadowRequest.setShadowName(shadowName);
            GetThingShadowResponse shadowResponse = ipcClient.getThingShadow(getThingShadowRequest);
            logger.debug("getShadow response: {}", shadowResponse);
            return new String(shadowResponse.getPayload(), StandardCharsets.UTF_8);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // set interrupt flag
            logger.error("Getting Shadow interrupted {}", ex.getMessage());
            ex.printStackTrace();
        } catch (UnauthorizedError ex) {
            logger.error("Getting Shadow was not authorized {}", ex.getMessage());
            ex.printStackTrace();
        } catch (Exception ex) {
            logger.error("Getting Shadow failed {}", ex.getMessage());
            ex.printStackTrace();
        }

        return null;
    }

    protected static String updateShadow(String thingName, String shadowName, byte[] shadowPayload) {
        try {
            UpdateThingShadowRequest updateThingShadowRequest = new UpdateThingShadowRequest();
            updateThingShadowRequest.setThingName(thingName);
            updateThingShadowRequest.setShadowName(shadowName);
            updateThingShadowRequest.setPayload(shadowPayload);
            UpdateThingShadowResponse updateShadowResponse = ipcClient.updateThingShadow(updateThingShadowRequest);
            logger.debug("updateShadow response: {}", updateShadowResponse);
            return new String(updateShadowResponse.getPayload(), StandardCharsets.UTF_8);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // set interrupt flag
            logger.error("Getting Shadow interrupted {}", ex.getMessage());
            ex.printStackTrace();
        } catch (UnauthorizedError ex) {
            logger.error("Getting Shadow was not authorized {}", ex.getMessage());
            ex.printStackTrace();
        } catch (Exception ex) {
            logger.error("Getting Shadow failed {}", ex.getMessage());
            ex.printStackTrace();
        }

        return null;
    }

}
