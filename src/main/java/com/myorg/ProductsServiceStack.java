package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.LogGroupProps;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProductsServiceStack extends Stack {

    public ProductsServiceStack(final Construct scope, final String id, final StackProps stackProps, ProductServiceProps productServiceProps) {
        super(scope, id, null);

        FargateTaskDefinition taskDefinition = createProductsServiceTaskDefinition();
        AwsLogDriver awsLogDriver = createAwsLogDriver();

        Map<String, String> envVariables = new HashMap<>();
        envVariables.put("SERVER_PORT", "8080");

        addContainerToTaskDefinition(taskDefinition, productServiceProps, awsLogDriver, envVariables);

        ApplicationListener applicationListener = createApplicationListener(productServiceProps.applicationLoadBalancer());

        FargateService fargateService = createFargateService(productServiceProps, taskDefinition);

        productServiceProps.repository().grantPull(Objects.requireNonNull(taskDefinition.getExecutionRole()));

        fargateService.getConnections().getSecurityGroups().get(0).addIngressRule(Peer.anyIpv4(), Port.tcp(8080));

        addTargetsToApplicationListener(applicationListener, fargateService);

        NetworkListener networkListener = createNetworkListener(productServiceProps.networkLoadBalancer());

        addTargetsToNetworkListener(networkListener, fargateService);
    }

    private FargateTaskDefinition createProductsServiceTaskDefinition() {
        return new FargateTaskDefinition(this, "ProductsServiceTaskDefinition", FargateTaskDefinitionProps.builder()
                .family("products-service")
                .cpu(512)
                .memoryLimitMiB(1024)
                .build());
    }

    private AwsLogDriver createAwsLogDriver() {
        return new AwsLogDriver(AwsLogDriverProps.builder()
                .logGroup(new LogGroup(this, "ProductsServiceLogGroup", LogGroupProps.builder()
                        .logGroupName("products-service")
                        .removalPolicy(RemovalPolicy.DESTROY)
                        .retention(RetentionDays.ONE_MONTH)
                        .build()))
                .streamPrefix("products-service")
                .build());
    }

    private ApplicationListener createApplicationListener(ApplicationLoadBalancer applicationLoadBalancer) {
        return new ApplicationListener(this, "ProductServiceAlbListener", ApplicationListenerProps.builder()
                .port(8080)
                .protocol(ApplicationProtocol.HTTP)
                .loadBalancer(applicationLoadBalancer)
                .build());
    }

    private void addContainerToTaskDefinition(FargateTaskDefinition taskDefinition, ProductServiceProps productServiceProps, AwsLogDriver awsLogDriver, Map<String, String> envVariables) {
        taskDefinition.addContainer("ProductsServiceContainer",
                ContainerDefinitionProps.builder()
                        .image(ContainerImage.fromEcrRepository(productServiceProps.repository(), "1.0.0"))
                        .containerName("productsService")
                        .logging(awsLogDriver)
                        .portMappings(Collections.singletonList(PortMapping.builder()
                                .containerPort(8080)
                                .protocol(Protocol.TCP)
                                .build()))
                        .environment(envVariables)
                        .build());
    }

    private FargateService createFargateService(ProductServiceProps productServiceProps, FargateTaskDefinition fargateTaskDefinition) {
        return new FargateService(this, "ProductsService", FargateServiceProps.builder()
                .serviceName("ProductsService")
                .cluster(productServiceProps.cluster())
                .taskDefinition(fargateTaskDefinition)
                .desiredCount(2)
                .build());
    }

    private void addTargetsToApplicationListener(ApplicationListener applicationListener, FargateService fargateService) {
        applicationListener.addTargets("ProductsServiceAlbTarget",
                AddApplicationTargetsProps.builder()
                        .targetGroupName("productServiceAlb")
                        .port(8080)
                        .protocol(ApplicationProtocol.HTTP)
                        .targets(Collections.singletonList(fargateService))
                        .deregistrationDelay(Duration.seconds(30))
                        .healthCheck(HealthCheck.builder()
                                .enabled(true)
                                .interval(Duration.seconds(30))
                                .timeout(Duration.seconds(10))
                                .path("/actuator/health")
                                .port("8080")
                                .build())
                        .build());
    }

    private NetworkListener createNetworkListener(NetworkLoadBalancer networkLoadBalancer) {
        return networkLoadBalancer.addListener("ProductsServiceNlbListener",
                BaseNetworkListenerProps.builder()
                        .port(8080)
                        .protocol(software.amazon.awscdk.services.elasticloadbalancingv2.Protocol.TCP)
                        .build());
    }

    private void addTargetsToNetworkListener(NetworkListener networkListener, FargateService fargateService) {
        networkListener.addTargets("ProductsServiceNlbTarget",
                AddNetworkTargetsProps.builder()
                        .port(8080)
                        .protocol(software.amazon.awscdk.services.elasticloadbalancingv2.Protocol.TCP)
                        .targetGroupName("productsServiceNlb")
                        .targets(Collections.singletonList(fargateService.loadBalancerTarget(
                                LoadBalancerTargetOptions.builder()
                                        .containerName("productsService")
                                        .containerPort(8080)
                                        .protocol(Protocol.TCP)
                                        .build())))
                        .build());
    }
}

record ProductServiceProps(Vpc vpc, Cluster cluster, NetworkLoadBalancer networkLoadBalancer,
                           ApplicationLoadBalancer applicationLoadBalancer,
                           Repository repository) {
}