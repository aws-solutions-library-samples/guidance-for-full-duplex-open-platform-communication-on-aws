/*
 * // Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * // SPDX-License-Identifier: MIT-0
 */
import * as cdk from "aws-cdk-lib";
import { Construct } from "constructs";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as iam from "aws-cdk-lib/aws-iam";
import * as logs from "aws-cdk-lib/aws-logs";
import { NagSuppressions } from "cdk-nag";


interface EC2SimulatorStackProps extends cdk.StackProps {
	temporaryCredentialsRole: iam.Role
}
export class EC2SimulatorStack extends cdk.Stack {
	constructor(scope: Construct, id: string, props: EC2SimulatorStackProps) {
		super(scope, id, props);
		const logGroup = new logs.LogGroup(this, "VpcFlowLogsLogGroup");

		const cloudWatchFlowLogsRole = new iam.Role(
			this,
			"CloudWatchFlowLogsRole",
			{
				assumedBy: new iam.ServicePrincipal("vpc-flow-logs.amazonaws.com"),
			}
		);

		const vpc = new ec2.Vpc(this, "NodeRedInstanceVPC", {
			subnetConfiguration: [
				{
					cidrMask: 24,
					name: "PrivateWithNatSubnet",
					subnetType: ec2.SubnetType.PUBLIC,
				},
			],
			flowLogs: {
				cloudWatch: {
					destination: ec2.FlowLogDestination.toCloudWatchLogs(
						logGroup,
						cloudWatchFlowLogsRole
					),
					trafficType: ec2.FlowLogTrafficType.ALL,
				},
			},
		});

		const nodeRedSecurityGroup = new ec2.SecurityGroup(
			this,
			"NodeRedSecurityGroup",
			{
				vpc,
				description: "Allow access to NodeRed via internet",
				allowAllOutbound: true,
			}
		);

		const instanceRole = new iam.Role(this, "instanceProfileRole", {
			assumedBy: new iam.ServicePrincipal("ec2.amazonaws.com"),
			managedPolicies: [
				iam.ManagedPolicy.fromAwsManagedPolicyName(
					"AmazonSSMManagedInstanceCore"
				),
			],
		});
		instanceRole.addToPolicy(
			new iam.PolicyStatement({
				resources: [props.temporaryCredentialsRole.roleArn],
				actions: ["sts:AssumeRole"],
			})
		);


		NagSuppressions.addResourceSuppressions(instanceRole, [
			{
				id: "AwsSolutions-IAM4",
				reason: "For SSM access to the instance, we need this managed policy.",
			},
		]);

		const windowsInstance = new ec2.Instance(this, "WindowsInstance", {
			vpc: vpc,
			instanceType: ec2.InstanceType.of(
				ec2.InstanceClass.T3,
				ec2.InstanceSize.LARGE
			),
			machineImage: ec2.MachineImage.latestWindows(
				ec2.WindowsVersion.WINDOWS_SERVER_2022_ENGLISH_FULL_BASE
			),
			role: instanceRole,
			allowAllOutbound: true,
			detailedMonitoring: true,
			securityGroup: nodeRedSecurityGroup,
			ssmSessionPermissions: true,
			keyName: "node-red-windows", //must create key-pair this before deploying CDK
		});

		NagSuppressions.addResourceSuppressions(windowsInstance, [
			{
				id: "AwsSolutions-EC29",
				reason:
					"EC2 termination protection is disabled since this a demo.",
			},
			{
				id: "AwsSolutions-EC26",
				reason: "Allow EBS auto creation to avoid unpredictable behavior.",
			},
		]);
	}
}
