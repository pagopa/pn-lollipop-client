package it.pagopa.pn.lollipop.client.filter.generator;

import lombok.Data;


@Data
public class RequestContext {

    private String resourceId;
    private String resourcePath;
    private String operationName;
    private String httpMethod;
    private String extendedRequestId;
    private String requestTime;
    private String path;
    private String accountId;
    private String protocol;
    private String stage;
    private String domainPrefix;
    private long requestTimeEpoch;
    private String requestId;
    private Identity identity; // Oggetto nidificato
    private String domainName;
    private String deploymentId;
    private String apiId;


}
