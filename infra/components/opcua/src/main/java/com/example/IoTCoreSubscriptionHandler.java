/*
 * // Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * // SPDX-License-Identifier: MIT-0
 */
package com.example;

import software.amazon.awssdk.aws.greengrass.model.IoTCoreMessage;
import software.amazon.awssdk.eventstreamrpc.StreamResponseHandler;

// logger
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IoTCoreSubscriptionHandler implements StreamResponseHandler<IoTCoreMessage> {
    private static final Logger logger = LoggerFactory.getLogger(IoTCoreSubscriptionHandler.class);

    private IoTCoreMessageInterface iotCoreMessageInterface;

    public IoTCoreSubscriptionHandler(IoTCoreMessageInterface iotCoreMessageInterface) {
        this.iotCoreMessageInterface = iotCoreMessageInterface;
    }

    @Override
    public void onStreamEvent(IoTCoreMessage ioTCoreMessage) {
        try {

            // trigger the message callback
            iotCoreMessageInterface.onIoTCoreMessage(ioTCoreMessage);

        } catch (Exception e) {
            logger.error("Exception occurred while processing subscription response " +
                    "message.");
            e.printStackTrace();
        }
    }

    @Override
    public boolean onStreamError(Throwable error) {
        // trigger the message callback
        iotCoreMessageInterface.onIoTCoreConnectionError(error.getMessage());
        error.printStackTrace();
        return false; // return true to close the stream
    }

    @Override
    public void onStreamClosed() {
        // trigger the message callback
        iotCoreMessageInterface.onIoTCoreConnectionClosed();
    }
}
