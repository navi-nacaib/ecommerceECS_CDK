package com.myorg;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.LogGroupProps;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ProductsServiceStack extends Stack {

    public ProductsServiceStack(final Construct scope, final String id, final StackProps stackProps, ProductServiceProps productServiceProps) {
        super(scope, id, null);

        FargateTaskDefinition taskDefinition = createProductsServiceTaskDefinition();
        AwsLogDriver awsLogDriver = createAwsLogDriver();

        Map<String, String> envVariables = new HashMap<>();
        envVariables.put("SERVER_PORT", "8080");

        taskDefinition.addContainer("ProductsServiceContainer", ContainerDefinitionProps.builder()
                .image(ContainerImage.fromEcrRepository(productServiceProps.repository(), "1.0.0"))
                .containerName("productsService")
                .logging(awsLogDriver)
                .portMappings(Collections.singletonList(PortMapping.builder()
                                .containerPort(8080)
                                .protocol(Protocol.TCP)
                        .build()))
                .environment(envVariables)
                .build());

        ApplicationListener applicationListener = productServiceProps.applicationLoadBalancer()
                .addListener("ProductServiceAlbListener", ApplicationListenerProps.builder()
                        .port(8080)
                        .protocol(ApplicationProtocol.HTTP)
                        .loadBalancer(productServiceProps.applicationLoadBalancer())
                        .build());
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
}

record ProductServiceProps(Vpc vpc, Cluster cluster, NetworkLoadBalancer networkLoadBalancer,
                           ApplicationLoadBalancer applicationLoadBalancer,
                           Repository repository) {
}