/*
 * // Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * // SPDX-License-Identifier: MIT-0
 */
package com.example;

import software.amazon.awssdk.aws.greengrass.model.SubscriptionResponseMessage;

// logger
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IPCSubscriptionHandler {

    private IPCMessageInterface ipcMessageInterface;

    public IPCSubscriptionHandler(IPCMessageInterface ipcMessageInterface) {
        this.ipcMessageInterface = ipcMessageInterface;
    }

    private static final Logger logger = LoggerFactory.getLogger(IPCSubscriptionHandler.class);

    public void onStreamEvent(SubscriptionResponseMessage subscriptionResponseMessage) {
        try {
            // trigger the message callback
            ipcMessageInterface.onIPCMessage(subscriptionResponseMessage);
        } catch (Exception ex) {
            logger.error("Exception occurred while processing subscription response {}", ex.getMessage());
            ex.printStackTrace();
        }
    }

    public boolean onStreamError(Throwable error) {
        // trigger the message callback
        ipcMessageInterface.onIPCConnectionError(error.getMessage());
        error.printStackTrace();
        return false; // Return true to close stream, false to keep stream open.
    }

    public void onStreamClosed() {
        ipcMessageInterface.onIPCConnectionClosed();
    }
}