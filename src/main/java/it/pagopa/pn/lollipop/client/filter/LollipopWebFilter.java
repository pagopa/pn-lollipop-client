package it.pagopa.pn.lollipop.client.filter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.pagopa.pn.common.rest.error.v1.dto.Problem;
import it.pagopa.pn.lollipop.client.config.FakeUser;
import it.pagopa.tech.lollipop.consumer.command.LollipopConsumerCommand;
import it.pagopa.tech.lollipop.consumer.command.LollipopConsumerCommandBuilder;
import it.pagopa.tech.lollipop.consumer.model.CommandResult;
import it.pagopa.tech.lollipop.consumer.model.LollipopConsumerRequest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.boot.web.reactive.filter.OrderedWebFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static it.pagopa.pn.commons.exceptions.PnExceptionsCodes.ERROR_CODE_LOLLIPOP_AUTH;
import static it.pagopa.pn.commons.utils.MDCUtils.MDC_TRACE_ID_KEY;
import static it.pagopa.tech.lollipop.consumer.command.impl.LollipopConsumerCommandImpl.VERIFICATION_SUCCESS_CODE;

@Slf4j
public class LollipopWebFilter implements OrderedWebFilter {
    private final LollipopConsumerCommandBuilder consumerCommandBuilder;
    private final ObjectMapper objectMapper;
    private final Map<String, FakeUser> whiteList;
    private static final String HEADER_FIELD = "x-pagopa-pn-src-ch";
    private static final String HEADER_VALUE = "IO";
    private static final String HEADER_USER_ID = "x-pagopa-lollipop-user-id";


    public LollipopWebFilter(LollipopConsumerCommandBuilder consumerCommandBuilder, String whiteListConfig) {
        this.consumerCommandBuilder = consumerCommandBuilder;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule( new JavaTimeModule() );
        this.objectMapper.setSerializationInclusion( JsonInclude.Include.NON_NULL );
        this.objectMapper.configure( SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false );
        this.whiteList = parseWhiteList(whiteListConfig);
    }

    @Override
    public @NotNull Mono<Void> filter(@NotNull ServerWebExchange exchange, @NotNull WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = exchange.getRequest().getHeaders();

        if (headers.containsKey(HEADER_FIELD)
                && Objects.equals(headers.getFirst(HEADER_FIELD), HEADER_VALUE)) {

            log.debug("Before Lollipop filter");
            HttpMethod method = request.getMethod();
            //controllo lollipop-user-id-header: se è presente nella lista settiamo dei valori fittizi al name e familyName e non viene fatta alcuna validazione
            if(headers.containsKey(HEADER_USER_ID) && headers.getFirst(HEADER_USER_ID) != null) {
                String userId = headers.getFirst(HEADER_USER_ID);
                if(whiteList.containsKey(userId)) {
                    log.info("In Lollipop filter - Lollipop userId is not null {}", userId);
                    FakeUser fakeUser = whiteList.get(userId);
                    log.debug("In Lollipop filter - White list user detected: {}", userId);

                    ServerHttpRequest mutatedRequest = request.mutate()
                            .header("x-pagopa-lollipop-user-name", fakeUser.name())
                            .header("x-pagopa-lollipop-user-family-name", fakeUser.familyName())
                            .build();

                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(mutatedRequest)
                            .build();

                    return chain.filter(mutatedExchange);
                }
            }
            // Get request body as String
            if (method != HttpMethod.GET && method != HttpMethod.DELETE) {
                return request.getBody()
                        .map(buffer -> buffer.toString(StandardCharsets.UTF_8))
                        .defaultIfEmpty("")
                        .flatMap(reqBody -> validateRequest(exchange, request, reqBody))
                        .collectList()
                        .doOnNext(objects -> log.debug("After Lollipop Filter"))
                        .flatMap(requests -> chain.filter(exchange));
            } else {
                return validateRequest(exchange, request, null)
                        .doOnNext(objects -> log.debug("After Lollipop Filter"))
                        .flatMap(stringMono -> chain.filter(exchange));
            }
        }
        return chain.filter(exchange);
    }

    private Mono<Object> validateRequest(@NotNull ServerWebExchange exchange, ServerHttpRequest request, String requestBody) {
        // Get request parameters as Map<String, String[]>
        MultiValueMap<String, String> queryParams = request.getQueryParams();
        Map<String, String[]> requestParams = new HashMap<>();
        queryParams.forEach((key, values) -> requestParams.put(key, values.toArray(new String[0])));

        // Get header parameters as Map<String, String>
        Map<String, String> headerParams = request.getHeaders().toSingleValueMap();

        // Create LollipopConsumerRequest object
        LollipopConsumerRequest consumerRequest = LollipopConsumerRequest.builder()
                .requestBody( requestBody )
                .requestParams( requestParams )
                .headerParams( headerParams )
                .build();

        LollipopConsumerCommand command = consumerCommandBuilder.createCommand(consumerRequest);
        CommandResult commandResult = command.doExecute();

        if (!commandResult.getResultCode().equals(VERIFICATION_SUCCESS_CODE)) {
            exchange.getResponse().setStatusCode( HttpStatus.NOT_FOUND );
            // Non voglio restituire l'errore al client ma lo loggo a livello warning
            log.warn("Lollipop auth response={}, detail={}", commandResult.getResultCode(), commandResult.getResultMessage());
            byte[] problemJsonBytes = getProblemJsonInBytes(commandResult.getResultCode());
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(problemJsonBytes);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return Mono.from( exchange.getResponse().writeWith(Flux.just(buffer)) );
        }
        // nuovi header
        String name = commandResult.getName();
        String familyName = commandResult.getFamilyName();
        if(name==null || name.isEmpty() || familyName==null || familyName.isEmpty()){
            log.warn("Lollipop header name or familyName is null or empty");
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header("x-pagopa-lollipop-user-name", name)
                .header("x-pagopa-lollipop-user-family-name", familyName)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // exchange aggiornato
        return Mono.just(mutatedExchange);
    }

    private byte[] getProblemJsonInBytes(String resultCode) {
        Problem problem = new Problem()
                .timestamp(Instant.now().atOffset(ZoneOffset.UTC))
                .detail(resultCode)
                .traceId(MDC.get(MDC_TRACE_ID_KEY))
                .title(ERROR_CODE_LOLLIPOP_AUTH);
        try {
            return objectMapper.writeValueAsBytes(problem);
        }
        catch (JsonProcessingException e) {
            log.warn("Error transform Problem to JSON");
            return new byte[]{};
        }
    }

    @Override
    public int getOrder() {
        return 1;
    }


    private Map<String, FakeUser> parseWhiteList(String config) {
        Map<String, FakeUser> map = new HashMap<>();
        if (config != null && !config.isBlank()) {
            for (String entry : config.split(";")) {
                String[] parts = entry.split(":");
                if (parts.length == 3) {
                    map.put(parts[0], new FakeUser(parts[1], parts[2]));
                } else {
                    log.warn("Invalid whiteList entry: {}", entry);
                }
            }
        }
        return map;
    }
}
