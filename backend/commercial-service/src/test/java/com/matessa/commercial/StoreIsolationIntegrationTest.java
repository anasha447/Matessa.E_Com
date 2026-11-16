package com.matessa.commercial;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema-per-store isolation in commercial-service: the same slug, coupon code and cache key
 * live independently in each store; a token only works in its own store; uploaded images are
 * filed under their store.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StoreIsolationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("commercial");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static RSAPrivateKey signingKey;
    private static Path mediaDir;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // test.redis.host lets a host whose IPv4 port forwarding misbehaves point at ::1.
        registry.add("spring.data.redis.host", () -> System.getProperty("test.redis.host", REDIS.getHost()));
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("app.tenancy.tenants", () -> "matessa,adventure");
        mediaDir = Files.createTempDirectory("media-test");
        registry.add("app.media.dir", () -> mediaDir.toString());

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        signingKey = (RSAPrivateKey) keyPair.getPrivate();
        Path dir = Files.createTempDirectory("commercial-test-keys");
        Path publicKey = dir.resolve("public.pem");
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPublic().getEncoded());
        Files.writeString(publicKey, "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n");
        publicKey.toFile().deleteOnExit();
        registry.add("jwt.public-key-path", () -> "file:" + publicKey.toAbsolutePath().toString().replace('\\', '/'));
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    StringRedisTemplate redis;

    private RestClient client(String store) {
        return RestClient.builder()
                .defaultHeader("X-Tenant-ID", store).baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    private String token(String store, String role) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("matessa-user-service").subject("1")
                .claim("tenant", store).claim("roles", List.of(role))
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private long createProduct(String store, String name) throws Exception {
        return createProduct(store, name, "shared-slug");
    }

    private long createProduct(String store, String name, String slug) throws Exception {
        String manager = token(store, "MANAGER");
        ResponseEntity<Map<String, Object>> category = client(store).post().uri("/api/categories")
                .header("Authorization", "Bearer " + manager).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Gear", "slug", "gear-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                        "displayOrder", 0)).retrieve().toEntity(MAP);
        assertThat(category.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", ((Number) category.getBody().get("id")).longValue());
        body.put("name", name);
        body.put("slug", slug);
        body.put("basePrice", 100.00);
        ResponseEntity<Map<String, Object>> product = client(store).post().uri("/api/products")
                .header("Authorization", "Bearer " + manager).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(MAP);
        assertThat(product.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) product.getBody().get("id")).longValue();
    }

    @Test
    void sameSlugCouponAndCacheKey_liveIndependentlyPerStore() throws Exception {
        long matessaId = createProduct("matessa", "Masala Mate");
        createProduct("adventure", "Trail Helmet");

        // Read twice per store: the second read is served from the per-store cache.
        for (int i = 0; i < 2; i++) {
            assertThat(client("matessa").get().uri("/api/products/slug/shared-slug").retrieve().toEntity(MAP)
                    .getBody().get("name")).isEqualTo("Masala Mate");
            assertThat(client("adventure").get().uri("/api/products/slug/shared-slug").retrieve().toEntity(MAP)
                    .getBody().get("name")).isEqualTo("Trail Helmet");
        }
        Set<String> keys = redis.keys("cat:v1:*");
        assertThat(keys).anyMatch(k -> k.startsWith("cat:v1:matessa:product::"));
        assertThat(keys).anyMatch(k -> k.startsWith("cat:v1:adventure:product::"));

        // Each store's catalogue lists only its own products.
        String matessaList = client("matessa").get().uri("/api/products").retrieve().toEntity(String.class).getBody();
        assertThat(matessaList).contains("Masala Mate").doesNotContain("Trail Helmet");
        assertThat(matessaId).isPositive();
        String adventureList = client("adventure").get().uri("/api/products").retrieve().toEntity(String.class).getBody();
        assertThat(adventureList).doesNotContain("Masala Mate");

        // The same coupon code can exist in both stores.
        for (String store : List.of("matessa", "adventure")) {
            Map<String, Object> coupon = new HashMap<>();
            coupon.put("code", "WELCOME10");
            coupon.put("discountType", "PERCENTAGE");
            coupon.put("discountValue", 10);
            coupon.put("validFrom", Instant.now().minusSeconds(60).toString());
            coupon.put("validUntil", Instant.now().plusSeconds(3600).toString());
            coupon.put("active", true);
            ResponseEntity<String> created = client(store).post().uri("/api/coupons")
                    .header("Authorization", "Bearer " + token(store, "MANAGER")).contentType(MediaType.APPLICATION_JSON)
                    .body(coupon).retrieve().toEntity(String.class);
            assertThat(created.getStatusCode()).as(store + " coupon").isEqualTo(HttpStatus.CREATED);
        }
    }

    @Test
    void tokenFromAnotherStore_isRefused() throws Exception {
        ResponseEntity<String> res = client("adventure").post().uri("/api/categories")
                .header("Authorization", "Bearer " + token("matessa", "MANAGER")).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "X", "slug", "x", "displayOrder", 0)).retrieve().toEntity(String.class);
        assertThat(res.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    void missingOrReservedStore_isRefused() {
        RestClient noStore = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
        assertThat(noStore.get().uri("/api/products").retrieve().toEntity(String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(client("public").get().uri("/api/products").retrieve().toEntity(String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void uploadedImage_isFiledUnderItsStore_andServed() throws Exception {
        long productId = createProduct("adventure", "Upload Target", "upload-target");
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);

        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(png.toByteArray()) {
            @Override
            public String getFilename() {
                return "pic.png";
            }
        });
        ResponseEntity<Map<String, Object>> uploaded = client("adventure").post()
                .uri("/api/products/" + productId + "/images/upload")
                .header("Authorization", "Bearer " + token("adventure", "MANAGER"))
                .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().toEntity(MAP);
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String url = (String) uploaded.getBody().get("url");
        assertThat(url).matches("/media/adventure/[a-f0-9]{32}\\.webp");

        ResponseEntity<byte[]> served = client("adventure").get().uri(url).retrieve().toEntity(byte[].class);
        assertThat(served.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(served.getHeaders().getContentType().toString()).isEqualTo("image/webp");
        assertThat(served.getHeaders().getCacheControl()).contains("immutable");
    }
}
