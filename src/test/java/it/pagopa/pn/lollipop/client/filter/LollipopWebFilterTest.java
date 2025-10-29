package it.pagopa.pn.lollipop.client.filter;


import com.fasterxml.jackson.core.JsonProcessingException;
import it.pagopa.tech.lollipop.consumer.command.LollipopConsumerCommand;
import it.pagopa.tech.lollipop.consumer.command.LollipopConsumerCommandBuilder;
import it.pagopa.tech.lollipop.consumer.model.CommandResult;
import it.pagopa.tech.lollipop.consumer.model.LollipopConsumerRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static it.pagopa.pn.commons.lollipop.LollipopHeaders.LOLLIPOP_ASSERTION_TYPE;
import static it.pagopa.tech.lollipop.consumer.command.impl.LollipopConsumerCommandImpl.VERIFICATION_SUCCESS_CODE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LollipopWebFilterTest {

    @Mock
    private LollipopConsumerCommandBuilder commandBuilder;

    @Mock
    private LollipopConsumerCommand command;

    private LollipopWebFilter webFilter;

    private static final String HEADER_FIELD = "x-pagopa-pn-src-ch";
    private static final String HEADER_FIELD_NAME = "x-pagopa-lollipop-user-name";
    private static final String HEADER_FIELD_FAMILY_NAME = "x-pagopa-lollipop-user-family-name";
    private static final String HEADER_VALUE = "IO";
    private static final String HEADER_VALUE_FAMILY_NAME = "Rossi";
    private static final String HEADER_VALUE_NAME = "Paolo";
    private static final String HEADER_USER_ID = "x-pagopa-lollipop-user-id";
    private static final String HEADER_USER_ID_VALUE = "fe09520f-fa96-43ad-96b5-d46924c21b73";


    @BeforeEach
    void setup() {
        String whiteListConfig = "fe09520f-fa96-43ad-96b5-d46924c21b73:Mario:Rossi;Ge09520x-fa96-43ad-96b5-d46924c21b44:Carlo:Verdi";
        webFilter = new LollipopWebFilter(commandBuilder, whiteListConfig);
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
        Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());

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


}
