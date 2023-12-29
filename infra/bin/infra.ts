#!/usr/bin/env node
/*
 * // Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * // SPDX-License-Identifier: MIT-0
 */

import "source-map-support/register";
import * as cdk from "aws-cdk-lib";

// cdk-nag
import { AwsSolutionsChecks, NagSuppressions } from "cdk-nag";
import { EC2SimulatorStack } from "../lib/ec2-simulator-stack";
import { GreengrassStack } from "../lib/greengrass-stack";


const app = new cdk.App();
const greengrassStack = new GreengrassStack(app, "NodeRedGreengrass", {
	description: 'Guidance for Full Duplex Open Platform Communication on AWS (SO9174)',
	env: {
		account: process.env.CDK_DEFAULT_ACCOUNT,
		region: process.env.CDK_DEFAULT_REGION,
	},
});

NagSuppressions.addStackSuppressions(greengrassStack, [
	{
		id: "AwsSolutions-IAM5",
		reason: "Permissive policy for sample.",
	},
]);

const ec2SimulatorStack = new EC2SimulatorStack(app, "NodeRedSimulator", {
	description: 'Guidance for Full Duplex Open Platform Communication on AWS (SO9174)',
	temporaryCredentialsRole: greengrassStack.temporaryCredentialsRole,
	env: {
		account: process.env.CDK_DEFAULT_ACCOUNT,
		region: process.env.CDK_DEFAULT_REGION,
	},
});

NagSuppressions.addStackSuppressions(ec2SimulatorStack, [
	{
		id: "AwsSolutions-IAM5",
		reason: "Permissive policy for sample.",
	},
]);

cdk.Aspects.of(app).add(new AwsSolutionsChecks());
