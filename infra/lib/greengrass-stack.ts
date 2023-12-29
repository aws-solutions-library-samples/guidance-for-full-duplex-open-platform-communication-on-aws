/*
 * // Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * // SPDX-License-Identifier: MIT-0
 */
import { Stack, StackProps, CfnOutput } from "aws-cdk-lib";
import { Construct } from "constructs";
import * as iam from "aws-cdk-lib/aws-iam";
import * as iot from "aws-cdk-lib/aws-iot";
import {
	BlockPublicAccess,
	Bucket,
	BucketEncryption,
} from "aws-cdk-lib/aws-s3";
import { NagSuppressions } from "cdk-nag";

export class GreengrassStack extends Stack {
	public temporaryCredentialsRole: iam.Role;
	constructor(scope: Construct, id: string, props: StackProps) {
		super(scope, id, props);
		const logBucket = new Bucket(this, "AccessLogBucket", {
			blockPublicAccess: BlockPublicAccess.BLOCK_ALL,
			encryption: BucketEncryption.S3_MANAGED,
			enforceSSL: true,
			serverAccessLogsPrefix: "buckets-access-logs",
		});

		const artifactsBucket = new Bucket(this, "GreengrassArtifactsBucket", {
			bucketName: `greengrass-artifacts-${Stack.of(this).region}-${Stack.of(this).account
				}`,
			blockPublicAccess: BlockPublicAccess.BLOCK_ALL,
			encryption: BucketEncryption.S3_MANAGED,
			enforceSSL: true,
			serverAccessLogsBucket: logBucket,
			serverAccessLogsPrefix: "artifact-access-logs",
		});

		const tokenExchangeRole = new iam.Role(
			this,
			"GreengrassV2TokenExchangeRole",
			{
				roleName: "NodeRedTokenExchangeRole",
				assumedBy: new iam.ServicePrincipal("credentials.iot.amazonaws.com"),

			}
		);

		const tokenExchangePolicy = new iam.Policy(
			this,
			"TokenExchangeRolePolicy",
			{
				statements: [
					new iam.PolicyStatement({
						effect: iam.Effect.ALLOW,
						actions: [
							"iot:DescribeCertificate",
							"logs:CreateLogGroup",
							"logs:CreateLogStream",
							"logs:PutLogEvents",
							"logs:DescribeLogStreams",
							"s3:GetBucketLocation",
							"s3:GetObject",
							"iam:AttachRolePolicy",
							"iam:CreatePolicy",
							"iam:CreateRole",
							"iam:GetPolicy",
							"iam:GetRole",
							"iam:PassRole"
						],
						resources: ["*"],
					}),
				],
			}
		);
		NagSuppressions.addResourceSuppressions(tokenExchangePolicy, [
			{
				id: "AwsSolutions-IAM5",
				reason:
					"Needed service role permission. See : https://docs.aws.amazon.com/greengrass/v2/developerguide/device-service-role.html#device-service-role-permissions",
			},
		]);

		tokenExchangeRole.attachInlinePolicy(tokenExchangePolicy);

		const artifactsBucketAccessPolicy = new iam.Policy(
			this,
			"ArtifactsBucketAccessPolicy",
			{
				statements: [
					new iam.PolicyStatement({
						effect: iam.Effect.ALLOW,
						actions: ["s3:GetObject"],
						resources: [
							`${artifactsBucket.bucketArn}`,
							`${artifactsBucket.bucketArn}/*`,
						],
					}),
				],
			}
		);
		NagSuppressions.addResourceSuppressions(artifactsBucketAccessPolicy, [
			{
				id: "AwsSolutions-IAM5",
				reason: "Arbitrary named objects must be accessible.",
			},
		]);

		tokenExchangeRole.attachInlinePolicy(artifactsBucketAccessPolicy);

		const tokenExchangeRoleAlias = new iot.CfnRoleAlias(
			this,
			"GreengrassV2TokenExchangeRoleAlias",
			{
				roleArn: tokenExchangeRole.roleArn,
			}
		);

		// this policy MUST be fine tuned as per production specification
		// https://docs.aws.amazon.com/greengrass/v2/developerguide/device-auth.html#client-device-support-minimal-iot-policy
		//https://docs.aws.amazon.com/greengrass/v2/developerguide/fleet-provisioning-setup.html#create-iot-policy
		const greengrassIoTPolicy = new iot.CfnPolicy(this, "GreengrassIoTPolicy", {
			policyDocument: {
				Version: "2012-10-17",
				Statement: [
					{
						Effect: "Allow",
						Action: [
							"iot:*", // as per manual provisioning guide; may not be suitable for production
							"greengrass:PutCertificateAuthorities",
							"greengrass:VerifyClientDeviceIdentity",
							"greengrass:VerifyClientDeviceIoTCertificateAssociation",
							"greengrass:GetConnectivityInfo",
							"greengrass:UpdateConnectivityInfo",
							"greengrass:GetComponentVersionArtifact",
							"greengrass:ResolveComponentCandidates"
						],
						Resource: ["*"],
					},
					// minimal core device policy
					{
						Effect: "Allow",
						Action: ["iot:Connect", "iot:Publish"],
						Resource: [
							`arn:aws:iot:${Stack.of(this).region}:${Stack.of(this).account
							}:*`,
							`arn:aws:iot:${Stack.of(this).region}:${Stack.of(this).account
							}:topic/*`,
							`arn:aws:iot:${Stack.of(this).region}:${Stack.of(this).account
							}:topic/$aws/things/*-gci/shadow/get`,
						],
					},
					{
						Effect: "Allow",
						Action: ["iot:Subscribe", "iot:Receive"],
						Resource: [
							`arn:aws:iot:${Stack.of(this).region}:${Stack.of(this).account
							}:*`, // for testing only, remove this
							`arn:aws:iot:${Stack.of(this).region}:${Stack.of(this).account
							}:topic/*`,
							`arn:aws:iot:${Stack.of(this).region}:${Stack.of(this).account
							}:topicfilter/*`,
							`arn:aws:iot:${Stack.of(this).region}:${Stack.of(this).account
							}:topicfilter/$aws/things/*-gci/shadow/update/delta`,
							`arn:aws:iot:${Stack.of(this).region}:${Stack.of(this).account
							}:topicfilter/$aws/things/*-gci/shadow/get/accepted`,
						],
					},

					{
						Effect: "Allow",
						Action: [
							"greengrass:Discover",
							"greengrass:VerifyClientDeviceIoTCertificateAssociation",
							"greengrass:ListThingGroupsForCoreDevice",
							"greengrass:VerifyClientDeviceIdentity",
						],
						Resource: [
							`arn:aws:iot:${Stack.of(this).region}:${Stack.of(this).account
							}:thing/*`,
						],
					},
					{
						Effect: "Allow",
						Action: [
							"greengrass:GetConnectivityInfo",
							"greengrass:UpdateConnectivityInfo",
						],
						Resource: [
							`arn:aws:iot:${Stack.of(this).region}:${Stack.of(this).account
							}:thing/*`,
						],
					},
				],
			},
		});

		this.temporaryCredentialsRole = new iam.Role(
			this,
			"TemporaryCredentialsRole",
			{
				assumedBy: new iam.AccountPrincipal(Stack.of(this).account),

			}
		);

		this.temporaryCredentialsRole.addToPolicy(
			new iam.PolicyStatement({
				effect: iam.Effect.ALLOW,
				actions: [
					"iam:AttachRolePolicy",
					"iam:CreatePolicy",
					"iam:CreateRole",
					"iam:GetPolicy",
					"iam:GetRole",
					"iam:PassRole",
					"iot:*",
					"greengrass:*",
				],
				resources: ["*"], // GG core device creation involves creation on IoT keys & Certs on * resource
			})
		);

		// Outputs
		new CfnOutput(this, "TokenExchangeRole", {
			value: tokenExchangeRole.roleName,
			description:
				"Token Exchange Role name to be used when creating Greengrass Core.",
			exportName: "TokenExchangeRole",
		});

		new CfnOutput(this, "TokenExchangeRoleAlias", {
			value: tokenExchangeRoleAlias.ref,
			description:
				"Token Exchange Role Alias to be used when creating Greengrass Core.",
			exportName: "TokenExchangeRoleAlias",
		});

		new CfnOutput(this, "ThingPolicy", {
			value: greengrassIoTPolicy.ref,
			description: "Thing Policy to be assigned to Greengrass Core.",
			exportName: "ThingPolicy",
		});

		new CfnOutput(this, "TemporaryCredentialsAssumeRole", {
			value: this.temporaryCredentialsRole.roleArn,
			description: "Use this role to get temporary credentials.",
			exportName: "TemporaryCredentialsAssumeRoleArn",
		});
	}
}
