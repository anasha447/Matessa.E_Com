package com.matessa.gateway.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The property under test is that a client cannot choose its own rate-limit bucket.
 * Before the trusted-proxy check existed, every one of the "spoof" cases below returned the
 * attacker-supplied value, which gave a brute-force attempt a fresh counter per request.
 */
class ClientIpResolverTest {

    private static final List<String> TRUSTED = List.of("127.0.0.1/32", "::1/128", "172.16.0.0/12");

    private final ClientIpResolver resolver = new ClientIpResolver(TRUSTED);

    private static MockHttpServletRequest request(String peer, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    @DisplayName("an untrusted peer cannot pick its bucket with X-Forwarded-For")
    void ignoresForwardedForFromUntrustedPeer() {
        assertThat(resolver.resolve(request("203.0.113.9", "1.2.3.4")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("a rotating X-Forwarded-For still maps to one bucket")
    void rotatingForwardedForDoesNotEscapeTheBucket() {
        String first = resolver.resolve(request("203.0.113.9", "10.0.0.1"));
        String second = resolver.resolve(request("203.0.113.9", "10.0.0.2"));
        String third = resolver.resolve(request("203.0.113.9", String.valueOf(Math.random())));
        assertThat(first).isEqualTo(second).isEqualTo(third).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("a trusted proxy's X-Forwarded-For is honoured")
    void honoursForwardedForFromTrustedProxy() {
        assertThat(resolver.resolve(request("172.18.0.5", "198.51.100.7")))
                .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("the chain is read right-to-left, so a client-prepended entry is ignored")
    void prefersTheRightmostNonProxyHop() {
        // The client wrote "1.2.3.4"; our own proxy appended the address it actually saw.
        assertThat(resolver.resolve(request("172.18.0.5", "1.2.3.4, 198.51.100.7")))
                .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("trusted hops are skipped when walking the chain")
    void skipsTrustedHopsInTheChain() {
        assertThat(resolver.resolve(request("172.18.0.5", "198.51.100.7, 172.18.0.9, 172.18.0.5")))
                .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("a trusted peer that sends no header falls back to the socket address")
    void fallsBackToPeerWhenHeaderAbsent() {
        assertThat(resolver.resolve(request("172.18.0.5", null))).isEqualTo("172.18.0.5");
        assertThat(resolver.resolve(request("172.18.0.5", "   "))).isEqualTo("172.18.0.5");
    }

    @Test
    @DisplayName("a chain of only our own proxies falls back to the peer")
    void fallsBackWhenEveryHopIsTrusted() {
        assertThat(resolver.resolve(request("172.18.0.5", "172.18.0.9, 127.0.0.1")))
                .isEqualTo("172.18.0.5");
    }

    @Test
    @DisplayName("ports and IPv6 brackets are stripped")
    void normalisesPortsAndBrackets() {
        assertThat(resolver.resolve(request("172.18.0.5", "198.51.100.7:51234")))
                .isEqualTo("198.51.100.7");
        assertThat(resolver.resolve(request("172.18.0.5", "[2001:db8::1]:443")))
                .isEqualTo("2001:db8::1");
    }

    @Test
    @DisplayName("IPv6 loopback is matched against its own family, not IPv4")
    void treatsAddressFamiliesSeparately() {
        assertThat(resolver.resolve(request("::1", "198.51.100.7"))).isEqualTo("198.51.100.7");
        // 127.0.0.1/32 must not be read as covering an IPv6 address.
        assertThat(resolver.resolve(request("2001:db8::99", "198.51.100.7")))
                .isEqualTo("2001:db8::99");
    }

    @Test
    @DisplayName("garbage in the header cannot crash or bypass the limiter")
    void toleratesUnparseableEntries() {
        assertThat(resolver.resolve(request("172.18.0.5", "not-an-ip, 198.51.100.7")))
                .isEqualTo("198.51.100.7");
        // An unparseable sole entry is not trusted, so it is returned as the bucket key
        // rather than silently widening access.
        assertThat(resolver.resolve(request("172.18.0.5", "not-an-ip"))).isEqualTo("not-an-ip");
    }

    @Test
    @DisplayName("an empty trusted list means no header is ever believed")
    void emptyTrustListIgnoresHeaderEntirely() {
        ClientIpResolver strict = new ClientIpResolver(List.of());
        assertThat(strict.resolve(request("172.18.0.5", "198.51.100.7"))).isEqualTo("172.18.0.5");
    }
}
