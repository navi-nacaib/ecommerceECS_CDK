package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

import java.util.HashMap;
import java.util.Map;

public class EcommerceEcsCdkApp {
    public static void main(final String[] args) {
        App app = new App();

        StsClient stsClient = StsClient.builder()
                .region(Region.US_WEST_1)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();

        GetCallerIdentityResponse response = stsClient.getCallerIdentity(GetCallerIdentityRequest.builder().build());
        String accountId = response.account();

        Environment environment = Environment.builder()
                .account(accountId)
                .region(Region.US_WEST_1.toString())
                .build();

        Map<String, String> infraTags = new HashMap<>();
        infraTags.put("team", "PersonalProject");
        infraTags.put("cost", "ECommerceInfrastructure");

        EcrStack ecrStack = new EcrStack(app, "Ecr", StackProps.builder()
                .env(environment)
                .tags(infraTags)
                .build());

        // Destroy this stack to reduce infrastructure costs since NAT gateways incur costs
        VpcStack vpcStack = new VpcStack(app, "Vpc", StackProps.builder()
                .env(environment)
                .tags(infraTags)
                .build());

        app.synth();
    }
}

