package it.pagopa.pn.lollipop.client.filter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.pagopa.pn.common.rest.error.v1.dto.Problem;
import it.pagopa.pn.common.rest.error.v1.dto.ProblemError;
import it.pagopa.pn.lollipop.client.config.FakeUser;
import it.pagopa.tech.lollipop.consumer.command.LollipopConsumerCommand;
import it.pagopa.tech.lollipop.consumer.command.LollipopConsumerCommandBuilder;
import it.pagopa.tech.lollipop.consumer.model.CommandResult;
import it.pagopa.tech.lollipop.consumer.model.LollipopConsumerRequest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.boot.web.reactive.filter.OrderedWebFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
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
    private static final String HEADER_USER_TAX_ID = "x-pagopa-cx-taxid";
    private static final String ERROR_CODE_MISMATCH = "ERROR_MISMATCH_BETWEEN_TAXID_AND_USERID";
    private static final String ERROR_MESSAGE_MISMATCH = "Error mismatch between x-pagopa-cx-taxid and x-pagopa-lollipop-user-id";


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

                if(headers.containsKey(HEADER_USER_TAX_ID)) {
                    String taxId = headers.getFirst(HEADER_USER_TAX_ID);
                    if (taxId != null && !taxId.equalsIgnoreCase(userId)) {
                       return checkHeadersMatch(exchange);
                    }
                }
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
                return DataBufferUtils.join(request.getBody())
                        .map(dataBuffer ->
                        {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                       //     DataBufferUtils.release(dataBuffer);
                            return new String(bytes, StandardCharsets.UTF_8);
                        })
                        .defaultIfEmpty("")
                        .flatMap(reqBody ->
                                validateRequest(exchange, request, reqBody)
                                        .flatMap(chain::filter)
                                        .doOnNext(objects -> log.debug("After Lollipop Filter"))
                                        .switchIfEmpty(chain.filter(exchange)) // <── solo se valida, continua la chain
                        );
            } else {
                return validateRequest(exchange, request, null)
                        .flatMap(chain::filter)
                        .doOnNext(objects -> log.debug("After Lollipop Filter"))
                        .switchIfEmpty(chain.filter(exchange)); // <── idem
            }
        }
        return chain.filter(exchange);
    }

    //metodo che fa il controllo sul match tra x-pagopa-lollipop-user-id e x-pagopa-cx-taxid -> se non c'è match ritorna una response badRequest e un problem json
    private Mono<Void> checkHeadersMatch(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
        log.warn("Lollipop auth response={}, detail={}", ERROR_CODE_MISMATCH, ERROR_MESSAGE_MISMATCH);
        CommandResult result = new CommandResult(ERROR_CODE_MISMATCH, ERROR_MESSAGE_MISMATCH);
        byte[] problemJsonBytes = getProblemJsonInBytes(result);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(problemJsonBytes);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return exchange.getResponse().writeWith(Mono.just(buffer)).then(Mono.empty());
    }

    private Mono<ServerWebExchange> validateRequest(@NotNull ServerWebExchange exchange, ServerHttpRequest request, String requestBody) {
        MultiValueMap<String, String> queryParams = request.getQueryParams();
        Map<String, String[]> requestParams = new HashMap<>();
        queryParams.forEach((key, values) -> requestParams.put(key, values.toArray(new String[0])));

        Map<String, String> headerParams = request.getHeaders().toSingleValueMap();

        LollipopConsumerRequest consumerRequest = LollipopConsumerRequest.builder()
                .requestBody(requestBody)
                .requestParams(requestParams)
                .headerParams(headerParams)
                .build();

        LollipopConsumerCommand command = consumerCommandBuilder.createCommand(consumerRequest);
        CommandResult commandResult = command.doExecute();

        if (!commandResult.getResultCode().equals(VERIFICATION_SUCCESS_CODE)) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            log.warn("Lollipop auth response={}, detail={}", commandResult.getResultCode(), commandResult.getResultMessage());
            byte[] problemJsonBytes = getProblemJsonInBytes(commandResult);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(problemJsonBytes);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            return exchange.getResponse().writeWith(Mono.just(buffer)).then(Mono.empty());
        }

        String name = commandResult.getName();
        String familyName = commandResult.getFamilyName();
        if (name == null || name.isEmpty() || familyName == null || familyName.isEmpty()) {
            log.warn("Lollipop header name or familyName is null or empty");
        }

        exchange.getRequest().getBody();
        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header("x-pagopa-lollipop-user-name", name)
                .header("x-pagopa-lollipop-user-family-name", familyName)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        return Mono.just(mutatedExchange);
    }

    private byte[] getProblemJsonInBytes(CommandResult commandResult) {
        ProblemError problemError = new ProblemError()
                .code(commandResult.getResultCode())
                .detail(commandResult.getResultMessage());


        Problem problem = new Problem()
                .timestamp(Instant.now().atOffset(ZoneOffset.UTC))
                .detail(commandResult.getResultCode())
                .traceId(MDC.get(MDC_TRACE_ID_KEY))
                .title(ERROR_CODE_LOLLIPOP_AUTH)
                .status(HttpStatus.BAD_REQUEST.value())
                .errors(List.of(problemError));
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
