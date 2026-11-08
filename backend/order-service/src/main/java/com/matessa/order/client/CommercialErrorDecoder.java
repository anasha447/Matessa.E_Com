package com.matessa.order.client;

import com.matessa.order.exception.BadRequestException;
import com.matessa.order.exception.ConflictException;
import feign.Response;
import feign.codec.ErrorDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Turns commercial-service's business refusals back into this service's own exceptions.
 *
 * <p>Without this, a 409 from commercial arrives as a generic {@code FeignException}, the
 * circuit breaker treats it as a transport failure, the fallback replaces it with a 503, and
 * the customer is told the store is down when the real answer was "that is out of stock". It
 * would also count business conflicts towards opening the circuit, so a run of out-of-stock
 * checkouts could trip the breaker and take the catalog calls down with it.
 *
 * <p>Only the two statuses that carry a business meaning are translated. Everything else —
 * 404, 5xx, timeouts — keeps the default behaviour and ends up at the fallback as a 503,
 * which is what the cart relies on to degrade a line to "unavailable".
 */
public class CommercialErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 409) {
            return new ConflictException(messageOf(response, "That is no longer available."));
        }
        if (response.status() == 400) {
            return new BadRequestException(messageOf(response, "The catalog rejected that request."));
        }
        return defaultDecoder.decode(methodKey, response);
    }

    /**
     * Pulls the message out of the standard error body. Commercial-service and this service
     * share the same error shape, so the downstream wording can be surfaced as-is rather than
     * replaced with something vaguer.
     */
    private String messageOf(Response response, String fallback) {
        if (response.body() == null) {
            return fallback;
        }
        try (InputStream body = response.body().asInputStream()) {
            String raw = new String(body.readAllBytes(), StandardCharsets.UTF_8);
            String marker = "\"message\":\"";
            int start = raw.indexOf(marker);
            if (start < 0) {
                return fallback;
            }
            start += marker.length();
            int end = raw.indexOf('"', start);
            return end > start ? raw.substring(start, end) : fallback;
        } catch (IOException | RuntimeException ex) {
            return fallback;
        }
    }
}
