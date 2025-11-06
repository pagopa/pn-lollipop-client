package it.pagopa.pn.lollipop.client.filter;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.pagopa.pn.lollipop.client.config.LollipopProperties;
import it.pagopa.pn.lollipop.client.config.PNHttpVerifierConfiguration;
import it.pagopa.pn.lollipop.client.filter.generator.ApiGatewayRequest;
import it.pagopa.tech.lollipop.consumer.assertion.AssertionServiceFactory;
import it.pagopa.tech.lollipop.consumer.command.LollipopConsumerCommand;
import it.pagopa.tech.lollipop.consumer.command.LollipopConsumerCommandBuilder;
import it.pagopa.tech.lollipop.consumer.command.impl.LollipopConsumerCommandBuilderImpl;
import it.pagopa.tech.lollipop.consumer.config.LollipopConsumerRequestConfig;
import it.pagopa.tech.lollipop.consumer.exception.LollipopDigestException;
import it.pagopa.tech.lollipop.consumer.exception.LollipopSignatureException;
import it.pagopa.tech.lollipop.consumer.helper.LollipopConsumerFactoryHelper;
import it.pagopa.tech.lollipop.consumer.http_verifier.HttpMessageVerifier;
import it.pagopa.tech.lollipop.consumer.http_verifier.HttpMessageVerifierFactory;
import it.pagopa.tech.lollipop.consumer.idp.IdpCertProviderFactory;
import it.pagopa.tech.lollipop.consumer.logger.LollipopLoggerService;
import it.pagopa.tech.lollipop.consumer.logger.LollipopLoggerServiceFactory;
import it.pagopa.tech.lollipop.consumer.model.CommandResult;
import it.pagopa.tech.lollipop.consumer.model.LollipopConsumerRequest;
import it.pagopa.tech.lollipop.consumer.service.LollipopConsumerRequestValidationService;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static it.pagopa.pn.commons.lollipop.LollipopHeaders.LOLLIPOP_ASSERTION_TYPE;
import static it.pagopa.tech.lollipop.consumer.command.impl.LollipopConsumerCommandImpl.VERIFICATION_SUCCESS_CODE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        PNHttpVerifierConfiguration.class,
        LollipopProperties.class, LollipopWebFilterTest.class
})
class LollipopWebFilterTest {

    private static final Path getRequestFile = Paths.get("src/test/resources/getRequest.json");
    private static final Path postRequestFile = Paths.get("src/test/resources/postRequest.json");

    @MockBean
    private LollipopConsumerCommandBuilder commandBuilder;


    @MockBean
    private LollipopConsumerCommand command;

    @Autowired
    private LollipopWebFilter webFilter;

    @Autowired
    private PNHttpVerifierConfiguration configuration;
    @Autowired
    private LollipopConsumerFactoryHelper lollipopConsumerFactoryHelper;
    @Autowired
    private LollipopConsumerCommandBuilder commandBuilderLollipop;

    private static final String HEADER_FIELD = "x-pagopa-pn-src-ch";
    private static final String HEADER_FIELD_NAME = "x-pagopa-lollipop-user-name";
    private static final String HEADER_FIELD_FAMILY_NAME = "x-pagopa-lollipop-user-family-name";
    private static final String HEADER_VALUE = "IO";
    private static final String HEADER_VALUE_FAMILY_NAME = "Rossi";
    private static final String HEADER_VALUE_NAME = "Paolo";
    private static final String HEADER_USER_ID = "x-pagopa-lollipop-user-id";
    private static final String HEADER_USER_TAX_ID = "x-pagopa-cx-taxid";
    private static final String HEADER_USER_ID_VALUE = "fe09520f-fa96-43ad-96b5-d46924c21b73";


    @BeforeEach
    void setup() {
        System.out.println("=== SETUP REAL TEST ===");
        String whiteListConfig = "fe09520f-fa96-43ad-96b5-d46924c21b73:Mario:Rossi;Ge09520x-fa96-43ad-96b5-d46924c21b44:Carlo:Verdi";
        configuration = new PNHttpVerifierConfiguration();

        // Factory reali o stub minimi (non mockate)
        LollipopLoggerServiceFactory loggerFactory = () -> null;

        HttpMessageVerifierFactory httpMessageVerifierFactory = () -> new HttpMessageVerifier() {
            @Override
            public boolean verifyDigest(String digest, String requestBody, String encoding) {
                System.out.println("Verifica digest simulata");
                return true; // Simula digest valido
            }

            @Override
            public boolean verifyHttpSignature(String method, String url, Map<String, String> headers) {
                System.out.println("Verifica firma HTTP simulata");
                return true; // Simula firma valida
            }
        };

        IdpCertProviderFactory idpCertProviderFactory = () -> null;

        AssertionServiceFactory assertionServiceFactory = () -> null;

        LollipopConsumerRequestValidationService validationService = request -> {
            System.out.println("Validazione richiesta simulata");
        };

        LollipopConsumerRequestConfig config = new LollipopConsumerRequestConfig();

        // Crea helper reale
        LollipopConsumerFactoryHelper helper = new LollipopConsumerFactoryHelper(
                loggerFactory,
                httpMessageVerifierFactory,
                idpCertProviderFactory,
                assertionServiceFactory,
                validationService,
                config
        );

        // Ottieni il builder reale dalla configurazione
        PNHttpVerifierConfiguration configuration = new PNHttpVerifierConfiguration();
        LollipopConsumerCommandBuilder builder = configuration.lollipopConsumerCommandBuilder(helper);

//        //commandBuilder.createCommand();
//        LollipopLoggerServiceFactory loggerServiceFactory = new LollipopLoggerServiceFactory();
//        HttpMessageVerifierFactory httpMessageVerifierFactory = new HttpMessageVerifierFactory();
//        IdpCertProviderFactory idpCertProviderFactory = new IdpCertProviderFactory();
//        AssertionServiceFactory assertionServiceFactory = new AssertionServiceFactory();
//
//        LollipopConsumerRequestValidationService validationService = new LollipopConsumerRequestValidationService();
//        LollipopConsumerRequestConfig requestConfig = new LollipopConsumerRequestConfig();
//
//        LollipopConsumerFactoryHelper lollipopConsumerFactoryHelper = new LollipopConsumerFactoryHelper();
//        LollipopConsumerCommandBuilder commandBuilder =
//                new LollipopConsumerCommandBuilderImpl(lollipopConsumerFactoryHelper);
     //   LollipopLoggerServiceFactory loggerFactory = () -> null;

//
//        HttpMessageVerifierFactory httpMessageVerifierFactory = () -> null;
//        LollipopLoggerServiceFactory loggerFactory = () -> null;
//
//        IdpCertProviderFactory idpCertProviderFactory = () -> null;
//        AssertionServiceFactory assertionServiceFactory = () -> null;
//        LollipopConsumerRequestValidationService validationService = request -> {};
//        LollipopConsumerRequestConfig config = new LollipopConsumerRequestConfig();
//
//        LollipopConsumerFactoryHelper helper = new LollipopConsumerFactoryHelper(
//                loggerFactory,
//                httpMessageVerifierFactory,
//                idpCertProviderFactory,
//                assertionServiceFactory,
//                validationService,
//                config
//        );
//
//        PNHttpVerifierConfiguration configuration = new PNHttpVerifierConfiguration();
//        LollipopConsumerCommandBuilder builder = configuration.lollipopConsumerCommandBuilder(helper);

        webFilter = new LollipopWebFilter(builder, whiteListConfig);
    }

    @Test
    void testFilterWithValidRequest() {
        MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost")
                .header(HEADER_FIELD, HEADER_VALUE)
                .header(LOLLIPOP_ASSERTION_TYPE, "SAML").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        WebHandler webHandler = serverWebExchange -> {
            Assertions.assertEquals("IO", HEADER_VALUE);
            return Mono.empty();
        };

        CommandResult commandResult =
                new CommandResult(VERIFICATION_SUCCESS_CODE, "request validation success");

        when(commandBuilder.createCommand(any(LollipopConsumerRequest.class))).thenReturn(command);

        when(command.doExecute()).thenReturn( commandResult );

        WebFilterChain filterChain = new DefaultWebFilterChain(webHandler, Collections.emptyList());

        assertDoesNotThrow( () -> {
            webFilter.filter(exchange, filterChain).block();
        });

    }

    @Test
    void testFilterWithValidRequestWithLollipopHeaderPresent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost")
                .header(HEADER_FIELD, HEADER_VALUE)
                .header(HEADER_USER_ID,HEADER_USER_ID_VALUE)
                .header(LOLLIPOP_ASSERTION_TYPE, "SAML").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        WebHandler webHandler = serverWebExchange -> {
            Assertions.assertEquals(HEADER_VALUE, serverWebExchange.getRequest().getHeaders().getFirst(HEADER_FIELD));
            return Mono.empty();
        };

        WebFilterChain filterChain = new DefaultWebFilterChain(webHandler, Collections.emptyList());

        assertDoesNotThrow(() -> webFilter.filter(exchange, filterChain).block());

    }

    @Test
    void testFilterWithValidRequestWithLollipopHeaderNotPresent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost")
                .header(HEADER_FIELD, HEADER_VALUE)
                .header(HEADER_USER_ID, "user-id-not-in-whitelist")
                .header(LOLLIPOP_ASSERTION_TYPE, "SAML").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        WebHandler webHandler = serverWebExchange -> {
            Assertions.assertEquals(HEADER_VALUE, serverWebExchange.getRequest().getHeaders().getFirst(HEADER_FIELD));
            return Mono.empty();
        };
        CommandResult commandResult =
                new CommandResult(VERIFICATION_SUCCESS_CODE, "request validation success");

        Mockito.when(commandBuilder.createCommand(Mockito.any(LollipopConsumerRequest.class))).thenReturn(command);

        Mockito.when(command.doExecute()).thenReturn( commandResult );

        WebFilterChain filterChain = new DefaultWebFilterChain(webHandler, Collections.emptyList());

        assertDoesNotThrow(() -> webFilter.filter(exchange, filterChain).block());

    }

    @Test
    void testFilterWithValidPostRequest() {
        MockServerHttpRequest request = MockServerHttpRequest.post("http://localhost")
                .header(HEADER_FIELD, HEADER_VALUE).build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        WebHandler webHandler = serverWebExchange -> {
            Assertions.assertEquals("IO", HEADER_VALUE);
            return Mono.empty();
        };

        CommandResult commandResult =
                new CommandResult(VERIFICATION_SUCCESS_CODE, "request validation success");

        Mockito.when(commandBuilder.createCommand(Mockito.any(LollipopConsumerRequest.class))).thenReturn(command);

        Mockito.when(command.doExecute()).thenReturn( commandResult );

        WebFilterChain filterChain = new DefaultWebFilterChain(webHandler, Collections.emptyList());

        assertDoesNotThrow( () -> {
            webFilter.filter(exchange, filterChain).block();
        });

    }

    @Test
    void testFilterWithValidPostRequestWithNameAndFamilyName() {
        MockServerHttpRequest request = MockServerHttpRequest.post("http://localhost")
                .header(HEADER_FIELD, HEADER_VALUE)
                .header(HEADER_FIELD_NAME, HEADER_VALUE_NAME)
                .header(HEADER_FIELD_FAMILY_NAME, HEADER_VALUE_FAMILY_NAME)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        WebHandler webHandler = serverWebExchange -> {
            Assertions.assertEquals("IO", HEADER_VALUE);
            return Mono.empty();
        };

        CommandResult commandResult =
                new CommandResult(VERIFICATION_SUCCESS_CODE, "request validation success", "Paolo", "Rossi");

        Mockito.when(commandBuilder.createCommand(Mockito.any(LollipopConsumerRequest.class))).thenReturn(command);

        when(command.doExecute()).thenReturn( commandResult );

        WebFilterChain filterChain = new DefaultWebFilterChain(webHandler, Collections.emptyList());

        assertDoesNotThrow( () -> {
            webFilter.filter(exchange, filterChain).block();
        });

    }

    @Test
    void testFilterWithoutIOHeaderRequest() {
        MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        WebHandler webHandler = serverWebExchange -> {
            Assertions.assertEquals("IO", HEADER_VALUE);
            return Mono.empty();
        };

        WebFilterChain filterChain = new DefaultWebFilterChain(webHandler, Collections.emptyList());

        assertDoesNotThrow( () -> {
            webFilter.filter(exchange, filterChain).block();
        });

    }

    @Test
    void testFilterWithInvalidRequest() {
        MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost")
                .header(HEADER_FIELD, HEADER_VALUE).build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        WebHandler webHandler = serverWebExchange -> {
            Assertions.assertEquals("IO", HEADER_VALUE);
            return Mono.empty();
        };

        CommandResult commandResult =
                new CommandResult("UNSUCCESSFUL_CODE", "request validation error");

        when(commandBuilder.createCommand(any(LollipopConsumerRequest.class))).thenReturn(command);

        when(command.doExecute()).thenReturn( commandResult );

        WebFilterChain filterChain = new DefaultWebFilterChain(webHandler, Collections.emptyList());

        assertDoesNotThrow( () -> {
            webFilter.filter(exchange, filterChain).block();
        });

        Assertions.assertNotNull( exchange.getResponse().getStatusCode() );
        Assertions.assertEquals( 400, exchange.getResponse().getStatusCode().value() );

    }

    @Test
    void testFilterWithEmptyNameFamilyNameShouldLogWarning() {
        MockServerHttpRequest request = MockServerHttpRequest.post("http://localhost")
                .header(HEADER_FIELD, HEADER_VALUE)
                .header(HEADER_FIELD_NAME, "")
                .header(HEADER_FIELD_FAMILY_NAME, "")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        WebHandler webHandler = serverWebExchange -> {
            Assertions.assertEquals("IO", HEADER_VALUE);
            return Mono.empty();
        };

        CommandResult commandResult =
                new CommandResult(VERIFICATION_SUCCESS_CODE, "request validation success","","");

        Mockito.when(commandBuilder.createCommand(Mockito.any(LollipopConsumerRequest.class))).thenReturn(command);

        Mockito.when(command.doExecute()).thenReturn( commandResult );

        WebFilterChain filterChain = new DefaultWebFilterChain(webHandler, Collections.emptyList());

        assertDoesNotThrow( () -> {
            webFilter.filter(exchange, filterChain).block();
        });

    }

    @Test
    void testFilterWithNullNameFamilyNameShouldLogWarning() {
        MockServerHttpRequest request = MockServerHttpRequest.post("http://localhost")
                .header(HEADER_FIELD, HEADER_VALUE)
                .header(HEADER_FIELD_NAME, "")
                .header(HEADER_FIELD_FAMILY_NAME, "")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        WebHandler webHandler = serverWebExchange -> {
            Assertions.assertEquals("IO", HEADER_VALUE);
            return Mono.empty();
        };

        CommandResult commandResult =
                new CommandResult(VERIFICATION_SUCCESS_CODE, "request validation success");

        Mockito.when(commandBuilder.createCommand(Mockito.any(LollipopConsumerRequest.class))).thenReturn(command);

        Mockito.when(command.doExecute()).thenReturn( commandResult );

        WebFilterChain filterChain = new DefaultWebFilterChain(webHandler, Collections.emptyList());

        assertDoesNotThrow( () -> {
            webFilter.filter(exchange, filterChain).block();
        });

    }

    @Test
    void testFilterWithInvalidRequest_ShouldReturnProblemJson() throws JsonProcessingException {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("http://localhost")
                .header(HEADER_FIELD, HEADER_VALUE)
                .body("request-body");

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebHandler webHandler = serverWebExchange -> Mono.empty();

        CommandResult commandResult = new CommandResult("UNSUCCESSFUL_CODE", "request validation error");

        when(commandBuilder.createCommand(any(LollipopConsumerRequest.class)))
                .thenReturn(command);
        when(command.doExecute()).thenReturn(commandResult);

        WebFilterChain filterChain = new DefaultWebFilterChain(webHandler, Collections.emptyList());

        webFilter.filter(exchange, filterChain).block();

        MockServerHttpResponse response = exchange.getResponse();

        Assertions.assertEquals(400, response.getStatusCode().value());
        Assertions.assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());

        String problemJson = response.getBodyAsString().block();
        System.out.println("Problem JSON:\n" + problemJson);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(problemJson);
        JsonNode errors = root.get("errors");

        Assertions.assertNotNull(problemJson);
        Assertions.assertEquals(400, root.get("status").asInt());
        Assertions.assertEquals("PN_LOLLIPOP_AUTH", root.get("title").asText());
        Assertions.assertEquals("UNSUCCESSFUL_CODE", root.get("detail").asText());

        Assertions.assertTrue(errors.isArray());
        Assertions.assertFalse(errors.isEmpty(), "La lista errors non deve essere vuota");
        Assertions.assertEquals("UNSUCCESSFUL_CODE", errors.get(0).get("code").asText());
        Assertions.assertEquals("request validation error", errors.get(0).get("detail").asText());
    }

    @Test
    void testFilterWithInvalidTaxIdHeaders() throws JsonProcessingException {
        MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost")
                .header(HEADER_FIELD, HEADER_VALUE)
                .header(HEADER_USER_ID, "CCC")
                .header(HEADER_USER_TAX_ID, "CCCD")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebHandler webHandler = serverWebExchange -> Mono.empty();

        WebFilterChain filterChain = new DefaultWebFilterChain(webHandler, Collections.emptyList());

        webFilter.filter(exchange, filterChain).block();

        MockServerHttpResponse response = exchange.getResponse();

        Assertions.assertEquals(400, response.getStatusCode().value());
        Assertions.assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());

        String problemJson = response.getBodyAsString().block();
        System.out.println("Problem JSON:\n" + problemJson);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(problemJson);
        JsonNode errors = root.get("errors");

        Assertions.assertNotNull(problemJson);
        Assertions.assertEquals(400, root.get("status").asInt());
        Assertions.assertEquals("PN_LOLLIPOP_AUTH", root.get("title").asText());
        Assertions.assertEquals("ERROR_MISMATCH_BETWEEN_TAXID_AND_USERID", root.get("detail").asText());

        Assertions.assertTrue(errors.isArray());
        Assertions.assertFalse(errors.isEmpty(), "La lista errors non deve essere vuota");
        Assertions.assertEquals("ERROR_MISMATCH_BETWEEN_TAXID_AND_USERID", errors.get(0).get("code").asText());
        Assertions.assertEquals("Error mismatch between x-pagopa-cx-taxid and x-pagopa-lollipop-user-id", errors.get(0).get("detail").asText());

    }

    @Test
    void testFilterWithTaxIdHeadersOk() throws JsonProcessingException {
        MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost")
                .header(HEADER_FIELD, HEADER_VALUE)
                .header(HEADER_USER_ID, "CCC")
                .header(HEADER_USER_TAX_ID, "CCC")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebHandler webHandler = serverWebExchange -> Mono.empty();

        CommandResult commandResult =
                new CommandResult(VERIFICATION_SUCCESS_CODE, "request validation success");

        Mockito.when(commandBuilder.createCommand(Mockito.any(LollipopConsumerRequest.class))).thenReturn(command);

        Mockito.when(command.doExecute()).thenReturn( commandResult );

        WebFilterChain filterChain = new DefaultWebFilterChain(webHandler, Collections.emptyList());

        webFilter.filter(exchange, filterChain).block();
        assertDoesNotThrow( () -> {
            webFilter.filter(exchange, filterChain).block();
        });
    }

    @ParameterizedTest(name = "Verifica filterRequestTest con sorgente: {0}")
    @MethodSource("requestSources")
    void filterRequestTest(String tipo, Path requestFile) {
        System.out.println("=== INIZIO TEST [" + tipo + "] ===");

        try {
            // 🔹 1. Carica JSON di input
            String jsonContent = Files.readString(requestFile);
            assertNotNull(jsonContent);
            System.out.println("jsonContent size: " + jsonContent.length());

            // 🔹 2. Deserializza la request simulata
            ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
            ApiGatewayRequest apiRequest = objectMapper.readValue(jsonContent, ApiGatewayRequest.class);
            assertNotNull(apiRequest.getRequestContext().getAccountId());

            // 🔹 3. Crea MockServerRequest
            HttpMethod httpMethod = HttpMethod.valueOf(apiRequest.getHttpMethod());
            URI fullUri = new URI("http://localhost" + apiRequest.getPath());
            MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest.method(httpMethod, fullUri);

            Map<String, String> headers = apiRequest.getHeaders();
            if (headers != null) headers.forEach(requestBuilder::header);
            MockServerHttpRequest request = requestBuilder.build();

            // 🔹 4. Crea WebExchange
            ServerWebExchange exchange = MockServerWebExchange.from(request);
            if (apiRequest.getPathParameters() != null && !apiRequest.getPathParameters().isEmpty()) {
                exchange.getAttributes().put(
                        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                        apiRequest.getPathParameters()
                );
            }

            // 🔹 5. Filtro reale
            WebHandler webHandler = serverWebExchange -> Mono.empty();
            WebFilterChain filterChain = new DefaultWebFilterChain(webHandler, Collections.emptyList());

            assertDoesNotThrow(() -> {
                webFilter.filter(exchange, filterChain).block();
                assertNull(exchange.getResponse().getStatusCode());
            });

        } catch (Exception e) {
            e.printStackTrace();
            fail("Errore durante il test: " + e.getMessage());
        }
    }

    @ParameterizedTest(name = "Verifica filterRequestTest con sorgente: {0}")
    @MethodSource("requestSources")
    void filterRequestTest2(String tipo, Path requestFile){
        System.out.println("=== INIZIO TEST [" + tipo + "] ===");
        try {
            String jsonContent = Files.readString(requestFile);
            Assertions.assertNotNull(jsonContent);
            if(jsonContent != null)
                System.out.println("jsonContent size: " + jsonContent.length());

            ObjectMapper objectMapper = new ObjectMapper();
            ApiGatewayRequest apiRequest = objectMapper.readValue(jsonContent, ApiGatewayRequest.class);
            if(apiRequest != null)
                System.out.println("apiRequest AccountId: " + apiRequest.getRequestContext().getAccountId()); //644374009812
            Assertions.assertNotNull(apiRequest.getRequestContext().getAccountId());

            HttpMethod httpMethod = HttpMethod.valueOf(apiRequest.getHttpMethod());
            String path = apiRequest.getPath();
            URI fullUri = new URI("http://localhost" + path);
            MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest.method(httpMethod, fullUri);
            Map<String, String> headers = apiRequest.getHeaders();
            if (headers != null) {
                System.out.println("headers size: " + headers.size());
                headers.forEach(requestBuilder::header);
            }
            //request
            MockServerHttpRequest request = requestBuilder.build();
            if(request.getHeaders()!= null )
                System.out.println("HttpHeaders size: " + request.getHeaders().size());
            Assertions.assertEquals(headers.size(), request.getHeaders().size());

            //response
            MockServerHttpResponse response = new MockServerHttpResponse();

            ServerWebExchange exchange = MockServerWebExchange.from(request);

            Map<String, String> pathParams = apiRequest.getPathParameters();
            final String PATH_VARIABLES_ATTRIBUTE = HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;
            if (pathParams != null && !pathParams.isEmpty()) {
                // Aggiungi i Path Parameters alla mappa degli Attributi dell'Exchange
                exchange.getAttributes().put( PATH_VARIABLES_ATTRIBUTE, pathParams);
            }

     //       configuration.lollipopConsumerCommandBuilder(lollipopConsumerFactoryHelper);

            //LollipopConsumerFactoryHelper lollipopConsumerFactoryHelper = new LollipopConsumerFactoryHelper();
            //configuration.lollipopConsumerCommandBuilder(lollipopConsumerFactoryHelper);
//            CommandResult commandResult =
//                    new CommandResult(VERIFICATION_SUCCESS_CODE, "request validation success");
//            commandBuilder.createCommand(Mockito.any(LollipopConsumerRequest.class));
//            command.doExecute();

            WebHandler webHandler = serverWebExchange -> Mono.empty();
            WebFilterChain filterChain = new DefaultWebFilterChain(webHandler, Collections.emptyList());

            assertDoesNotThrow( () -> {
                webFilter.filter(exchange, filterChain).block();
                assertNull( exchange.getResponse().getStatusCode());
            });


        }catch(Exception e ){
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private static Stream<Arguments> requestSources() throws IOException, DecoderException {
        return Stream.of(
                Arguments.of("GET_REQUEST", getRequestFile),
                Arguments.of("POST_REQUEST", postRequestFile)
        );
    }
}
