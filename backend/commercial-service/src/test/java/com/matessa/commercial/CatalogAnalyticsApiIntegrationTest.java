package com.matessa.commercial;

import com.matessa.commercial.entity.Category;
import com.matessa.commercial.entity.Coupon;
import com.matessa.commercial.entity.Discount;
import com.matessa.commercial.entity.DiscountType;
import com.matessa.commercial.entity.Product;
import com.matessa.commercial.entity.ProductVariant;
import com.matessa.commercial.entity.Review;
import com.matessa.commercial.entity.Wishlist;
import com.matessa.commercial.entity.WishlistItem;
import com.matessa.commercial.repository.CategoryRepository;
import com.matessa.commercial.repository.CouponRepository;
import com.matessa.commercial.repository.DiscountRepository;
import com.matessa.commercial.repository.ProductRepository;
import com.matessa.commercial.repository.ProductVariantRepository;
import com.matessa.commercial.repository.ReviewRepository;
import com.matessa.commercial.repository.WishlistItemRepository;
import com.matessa.commercial.repository.WishlistRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CatalogAnalyticsApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("commercial");

    private static RSAPrivateKey signingKey;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() { };

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
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

    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired WishlistRepository wishlistRepository;
    @Autowired WishlistItemRepository wishlistItemRepository;
    @Autowired CouponRepository couponRepository;
    @Autowired DiscountRepository discountRepository;

    private RestClient client() {
        return RestClient.builder()
                .defaultHeader("X-Tenant-ID", "matessa").baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    private String token(String role, String subject) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("matessa-user-service").subject(subject).claim("tenant", "matessa")
                .claim("roles", List.of(role))
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private Long variant(Long productId, String sku, int stock, Integer restock) {
        ProductVariant v = new ProductVariant();
        v.setProductId(productId);
        v.setSku(sku);
        v.setStockQuantity(stock);
        v.setRestockLevel(restock);
        v.setActive(true);
        return variantRepository.save(v).getId();
    }

    private void wishlist(long userId, Long variantId) {
        Wishlist w = new Wishlist();
        w.setUserId(userId);
        Long wid = wishlistRepository.save(w).getId();
        WishlistItem i = new WishlistItem();
        i.setWishlistId(wid);
        i.setProductVariantId(variantId);
        wishlistItemRepository.save(i);
    }

    private static double num(Map<String, Object> m, String k) {
        return ((Number) m.get(k)).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> m, String k) {
        return (Map<String, Object>) m.get(k);
    }

    @Test
    void catalogAnalytics_inventory_promos_lists_andRoleGate() throws Exception {
        Category c = new Category();
        c.setName("Helmets");
        c.setSlug("helmets");
        c.setActive(true);
        Long categoryId = categoryRepository.save(c).getId();

        Product p = new Product();
        p.setCategoryId(categoryId);
        p.setName("Trail Helmet");
        p.setSlug("trail-helmet");
        p.setBasePrice(new BigDecimal("100.00"));
        p.setActive(true);
        Long productId = productRepository.save(p).getId();

        Long v1 = variant(productId, "OK", 10, 3);     // healthy
        variant(productId, "LOW", 2, 5);               // low stock
        variant(productId, "OUT", 0, 2);               // out of stock (and low)

        Review r1 = new Review();
        r1.setProductId(productId); r1.setUserId(100L); r1.setRating(5);
        reviewRepository.save(r1);
        Review r2 = new Review();
        r2.setProductId(productId); r2.setUserId(200L); r2.setRating(4);
        reviewRepository.save(r2);

        wishlist(100, v1);
        wishlist(200, v1);   // v1 wishlisted twice

        Coupon coupon = new Coupon();
        coupon.setCode("SAVE10"); coupon.setDiscountType(DiscountType.PERCENTAGE);
        coupon.setDiscountValue(new BigDecimal("10")); coupon.setUsedCount(0);
        coupon.setValidFrom(Instant.now().minusSeconds(60)); coupon.setValidUntil(Instant.now().plusSeconds(86400));
        coupon.setActive(true);
        couponRepository.save(coupon);

        Discount discount = new Discount();
        discount.setProductId(productId); discount.setType(DiscountType.PERCENTAGE);
        discount.setValue(new BigDecimal("15")); discount.setValidFrom(Instant.now().minusSeconds(60));
        discount.setValidTo(Instant.now().plusSeconds(86400)); discount.setActive(true); discount.setCreatedBy(1L);
        discountRepository.save(discount);

        String manager = token("MANAGER", "1");
        Map<String, Object> body = client().get().uri("/api/analytics/catalog")
                .header("Authorization", "Bearer " + manager).retrieve().toEntity(MAP).getBody();

        assertThat(num(sub(body, "counts"), "products")).isEqualTo(1.0);
        assertThat(num(sub(body, "counts"), "variants")).isEqualTo(3.0);
        assertThat(num(sub(body, "counts"), "categories")).isEqualTo(1.0);
        assertThat(num(sub(body, "inventory"), "totalValue")).isEqualTo(1200.00); // (10+2+0)*100
        assertThat(num(sub(body, "inventory"), "lowStock")).isEqualTo(2.0);        // LOW + OUT
        assertThat(num(sub(body, "inventory"), "outOfStock")).isEqualTo(1.0);      // OUT
        assertThat(num(body, "totalReviews")).isEqualTo(2.0);
        assertThat(num(sub(body, "promotions"), "activeCoupons")).isEqualTo(1.0);
        assertThat(num(sub(body, "promotions"), "activeDiscounts")).isEqualTo(1.0);

        List<Map<String, Object>> low = client().get().uri("/api/analytics/catalog/low-stock")
                .header("Authorization", "Bearer " + manager).retrieve().toEntity(LIST).getBody();
        assertThat(low).hasSize(2);

        List<Map<String, Object>> rated = client().get().uri("/api/analytics/catalog/top-rated")
                .header("Authorization", "Bearer " + manager).retrieve().toEntity(LIST).getBody();
        assertThat(num(rated.get(0), "averageRating")).isEqualTo(4.5);

        List<Map<String, Object>> wished = client().get().uri("/api/analytics/catalog/most-wishlisted")
                .header("Authorization", "Bearer " + manager).retrieve().toEntity(LIST).getBody();
        assertThat(((Number) wished.get(0).get("productVariantId")).longValue()).isEqualTo(v1);
        assertThat(num(wished.get(0), "wishlistCount")).isEqualTo(2.0);

        // customers cannot see store analytics
        ResponseEntity<String> forbidden = client().get().uri("/api/analytics/catalog")
                .header("Authorization", "Bearer " + token("CUSTOMER", "100")).retrieve().toEntity(String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
