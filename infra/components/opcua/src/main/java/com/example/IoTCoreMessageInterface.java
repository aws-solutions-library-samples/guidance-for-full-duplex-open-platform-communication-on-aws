/*
 * // Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * // SPDX-License-Identifier: MIT-0
 */
package com.example;

import software.amazon.awssdk.aws.greengrass.model.IoTCoreMessage;

public interface IoTCoreMessageInterface {
    void onIoTCoreMessage(IoTCoreMessage ioTCoreMessage);

    void onIoTCoreConnectionError(String errorMessage);

    void onIoTCoreConnectionClosed();
}
