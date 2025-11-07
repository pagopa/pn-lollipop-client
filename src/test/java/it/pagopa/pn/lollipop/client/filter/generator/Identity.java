package it.pagopa.pn.lollipop.client.filter.generator;


import lombok.Data;

@Data
public class Identity {
    private String cognitoIdentityPoolId;
    private String cognitoIdentityId;
    private String apiKey;
    private String principalOrgId;
    private String cognitoAuthenticationType;
    private String userArn;
    private String apiKeyId;
    private String userAgent;
    private String accountId;
    private String caller;
    private String sourceIp;
    private String accessKey;
    private String cognitoAuthenticationProvider;
    private String user;

    // Getters e Setters, costruttore vuoto...
}
