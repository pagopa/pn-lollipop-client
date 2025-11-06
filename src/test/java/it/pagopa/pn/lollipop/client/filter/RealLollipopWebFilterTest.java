package it.pagopa.pn.lollipop.client.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.lollipop.client.config.PNHttpVerifierConfiguration;
import it.pagopa.pn.lollipop.client.filter.generator.ApiGatewayRequest;
import it.pagopa.tech.lollipop.consumer.assertion.AssertionService;
import it.pagopa.tech.lollipop.consumer.assertion.AssertionServiceFactory;
import it.pagopa.tech.lollipop.consumer.assertion.client.AssertionClient;
import it.pagopa.tech.lollipop.consumer.assertion.client.simple.AssertionSimpleClientConfig;
import it.pagopa.tech.lollipop.consumer.assertion.client.simple.AssertionSimpleClientProvider;
import it.pagopa.tech.lollipop.consumer.assertion.impl.AssertionServiceFactoryImpl;
import it.pagopa.tech.lollipop.consumer.assertion.storage.SimpleAssertionStorageProvider;
import it.pagopa.tech.lollipop.consumer.assertion.storage.StorageConfig;
import it.pagopa.tech.lollipop.consumer.command.LollipopConsumerCommand;
import it.pagopa.tech.lollipop.consumer.command.LollipopConsumerCommandBuilder;
import it.pagopa.tech.lollipop.consumer.model.CommandResult;
import it.pagopa.tech.lollipop.consumer.model.SamlAssertion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.reactive.filter.OrderedWebFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {PNHttpVerifierConfiguration.class})
class RealLollipopWebFilterTest {

    @Autowired
    private LollipopWebFilter webFilter;

    // client SDK mockato
    @MockBean
    private AssertionServiceFactory assertionServiceFactory;

    @Mock
    private AssertionClient assertionClient;

    private AssertionService assertionService;


    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Path getRequestFile = Paths.get("src/test/resources/getRequest.json");
    private static final Path getAssertionFile = Paths.get("src/test/resources/getAssertion.xml");
    private static final String assertionRef = "sha256-Iz4GEYGtznLdLyHrbtKEkzb6qSJpOkKvsOsCxgXkIhI" ;
    private static final Path postRequestFile = Paths.get("src/test/resources/postRequest.json");
    private static final Path postAssertionFile = Paths.get("src/test/resources/postAssertion.json");
    //private static final String assertionRefPost = "sha256-Iz4GEYGtznLdLyHrbtKEkzb6qSJpOkKvsOsCxgXkIhI";
    public static final String JWT = "Bearer aValidJWT";

    @BeforeEach
    void setup() {
        reset(assertionServiceFactory, assertionClient);
        assertionService = mock(AssertionService.class);
        when(assertionServiceFactory.create()).thenReturn(assertionService);
    }

    @ParameterizedTest(name = "Filtro reale con sorgente: {0}")
    @MethodSource("requestSources")
    void filterRequestTest(String tipo, Path requestFile, Path assertionFile) throws Exception {
        System.out.println("=== INIZIO TEST [" + tipo + "] ===");

        String jsonContent = Files.readString(requestFile);
        String jsonAssertionContent = Files.readString(assertionFile);

        ApiGatewayRequest apiRequest = objectMapper.readValue(jsonContent, ApiGatewayRequest.class);
        HttpMethod httpMethod = HttpMethod.valueOf(apiRequest.getHttpMethod());

        URI fullUri = new URI("http://localhost");
        MockServerHttpRequest.BodyBuilder reqBuilder = MockServerHttpRequest.method(httpMethod, fullUri);
        MockServerHttpRequest request = reqBuilder.body("esempio");
        if (apiRequest.getHeaders() != null) apiRequest.getHeaders().forEach(reqBuilder::header);
        ServerWebExchange exchange = MockServerWebExchange.builder(request).build();
        System.out.println("Exchange body: "+exchange.getRequest().getBody());

        //mock sdk
        SamlAssertion samlAssertion = new SamlAssertion();
        samlAssertion.setAssertionRef(assertionRef);
        samlAssertion.setAssertionData(jsonAssertionContent);

        when(assertionService.getAssertion(anyString(), anyString()))
                .thenReturn(samlAssertion);

        when(assertionServiceFactory.create()).thenReturn(assertionService);

        // il filtro vero
        WebHandler handler = swe -> Mono.empty();
        WebFilterChain chain = new DefaultWebFilterChain(handler, Collections.emptyList());

        StepVerifier.create(webFilter.filter(exchange, chain))
                .verifyComplete();

        assertNull(exchange.getResponse().getStatusCode());
        System.out.println("=== END TEST [" + tipo + "] ===");
    }

    private static Stream<Arguments> requestSources() {
        return Stream.of(
                Arguments.of("GET_REQUEST", getRequestFile, getAssertionFile),
                Arguments.of("POST_REQUEST", postRequestFile, postAssertionFile)
        );
    }
}
