package com.myorg;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcProps;
import software.constructs.Construct;

public class VpcStack extends Stack {

    private final Vpc vpc;

    public VpcStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        this.vpc = createVpc();
    }

    private Vpc createVpc() {
        return new Vpc(this, "Vpc", VpcProps.builder()
                .vpcName("ECommerceVPC")
                .maxAzs(2)
                // Do not do this in production
                // .natGateways(0)
                .build());
    }

    public Vpc getVpc() {
        return vpc;
    }
}
