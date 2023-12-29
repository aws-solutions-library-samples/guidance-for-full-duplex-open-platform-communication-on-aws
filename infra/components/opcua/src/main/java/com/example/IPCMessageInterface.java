/*
 * // Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * // SPDX-License-Identifier: MIT-0
 */
package com.example;

import software.amazon.awssdk.aws.greengrass.model.SubscriptionResponseMessage;

public interface IPCMessageInterface {
    void onIPCMessage(SubscriptionResponseMessage ipcMessage);

    void onIPCConnectionError(String errorMessage);

    void onIPCConnectionClosed();
}
