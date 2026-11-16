package com.matessa.order.service.impl;

import com.matessa.order.entity.PaymentMethod;
import com.matessa.order.entity.PaymentStatus;
import com.matessa.order.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Production payments: cash on delivery only, and the mock processor can never load there. */
class CodOnlyPaymentServiceTest {

    private final CodOnlyPaymentService service = new CodOnlyPaymentService();

    @Test
    void codIsPendingUntilDelivery() {
        assertThat(service.charge(PaymentMethod.COD, new BigDecimal("499.00")).status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(service.supportedMethods()).containsExactly(PaymentMethod.COD);
    }

    @Test
    void onlineMethodsAreRefused_neverMarkedPaid() {
        for (PaymentMethod m : new PaymentMethod[]{PaymentMethod.CARD, PaymentMethod.ONLINE, PaymentMethod.PAYPAL}) {
            assertThatThrownBy(() -> service.charge(m, BigDecimal.TEN)).isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void profilesKeepTheMockOutOfProduction() {
        assertThat(MockPaymentService.class.getAnnotation(Profile.class).value()).containsExactly("!prod");
        assertThat(CodOnlyPaymentService.class.getAnnotation(Profile.class).value()).containsExactly("prod");
    }
}
