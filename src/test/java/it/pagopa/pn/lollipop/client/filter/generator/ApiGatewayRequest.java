package it.pagopa.pn.lollipop.client.filter.generator;

import lombok.Data;

import java.util.Map;
import java.util.List;

@Data
// Classe principale che mappa l'intero payload JSON
public class ApiGatewayRequest {
    private String type;
    private String methodArn;
    private String resource;
    private String path;
    private String httpMethod;

    // Mappa per gli header a valore singolo
    private Map<String, String> headers;

    // Mappa per gli header a valori multipli
    private Map<String, List<String>> multiValueHeaders;

    // Mappe per parametri (query, path, stage)
    private Map<String, String> queryStringParameters;
    private Map<String, List<String>> multiValueQueryStringParameters;
    private Map<String, String> pathParameters;
    private Map<String, String> stageVariables;

    private RequestContext requestContext;

}