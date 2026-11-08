package com.matessa.gateway.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides which address a request should be rate-limited against.
 *
 * <p>{@code X-Forwarded-For} is a plain request header, so any client can put anything in it
 * and change it on every request. Honouring it unconditionally made the per-IP limits
 * decorative: a login brute-force only had to vary the header to get a fresh counter each
 * time, so the 10-per-minute cap never fired.
 *
 * <p>Therefore the header is consulted <em>only</em> when the direct peer
 * ({@code getRemoteAddr()}) is a configured trusted proxy. The chain is then walked from the
 * <b>right</b>, skipping trusted hops, and the first non-trusted address is returned — that is
 * the furthest-upstream address there are actually grounds to believe. Taking the leftmost
 * entry instead is the classic error: that end of the chain is the part the client wrote.
 *
 * <p>When the peer is not trusted the header is ignored entirely and the socket address is
 * used, which cannot be forged.
 *
 * <p><b>Deployment note.</b> This is only as good as the chain. Browser traffic reaches the
 * gateway through each storefront's Next.js BFF, which forwards the inbound
 * {@code X-Forwarded-For} but cannot invent it — so a real reverse proxy (nginx/Caddy/Traefik)
 * in front of the storefronts has to set it. Without one, every browser request presents the
 * BFF container's address and the limits degrade to one shared bucket per storefront instead
 * of one per client. That is a throttling-accuracy problem, not a bypass.
 */
public final class ClientIpResolver {

    private final List<Cidr> trustedProxies;

    public ClientIpResolver(List<String> trustedProxyCidrs) {
        List<Cidr> parsed = new ArrayList<>();
        for (String spec : trustedProxyCidrs) {
            Cidr cidr = Cidr.parse(spec);
            if (cidr != null) {
                parsed.add(cidr);
            }
        }
        this.trustedProxies = List.copyOf(parsed);
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!isTrustedProxy(peer)) {
            return peer;          // untrusted peer: the header is not evidence of anything
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return peer;          // trusted peer that told us nothing
        }
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = normalize(hops[i]);
            if (hop.isEmpty()) {
                continue;
            }
            if (!isTrustedProxy(hop)) {
                return hop;       // first non-proxy walking right-to-left == the client
            }
        }
        return peer;              // the whole chain was our own proxies
    }

    private boolean isTrustedProxy(String address) {
        byte[] candidate = bytesOf(address);
        if (candidate == null) {
            return false;
        }
        for (Cidr cidr : trustedProxies) {
            if (cidr.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** Strips a port and IPv6 brackets, e.g. "[::1]:443" -> "::1", "1.2.3.4:567" -> "1.2.3.4". */
    private static String normalize(String raw) {
        String value = raw.trim();
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            return close > 0 ? value.substring(1, close) : value.substring(1);
        }
        int firstColon = value.indexOf(':');
        if (firstColon > -1 && value.indexOf(':', firstColon + 1) == -1) {
            return value.substring(0, firstColon);   // single colon == IPv4:port
        }
        return value;
    }

    private static byte[] bytesOf(String address) {
        try {
            // Literal addresses only — never a hostname, so this performs no DNS lookup.
            return InetAddress.getByName(normalize(address)).getAddress();
        } catch (UnknownHostException | SecurityException ex) {
            return null;
        }
    }

    private record Cidr(byte[] network, int prefixBits) {

        static Cidr parse(String spec) {
            String value = spec.trim();
            if (value.isEmpty()) {
                return null;
            }
            int slash = value.lastIndexOf('/');
            String host = slash > -1 ? value.substring(0, slash) : value;
            byte[] network = bytesOf(host);
            if (network == null) {
                return null;
            }
            int bits = network.length * 8;
            if (slash > -1) {
                try {
                    bits = Integer.parseInt(value.substring(slash + 1).trim());
                } catch (NumberFormatException ex) {
                    return null;
                }
                if (bits < 0 || bits > network.length * 8) {
                    return null;
                }
            }
            return new Cidr(network, bits);
        }

        boolean contains(byte[] candidate) {
            if (candidate.length != network.length) {
                return false;   // don't compare IPv4 against IPv6
            }
            int fullBytes = prefixBits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixBits % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
