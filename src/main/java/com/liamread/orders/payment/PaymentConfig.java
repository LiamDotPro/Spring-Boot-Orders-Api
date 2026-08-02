package com.liamread.orders.payment;

import com.liamread.orders.payment.event.LoggingPaymentOutcomePublisher;
import com.liamread.orders.payment.event.PaymentOutcomePublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentConfig {

    /**
     * Registered only while nothing else provides a {@link PaymentOutcomePublisher}. Add your own
     * {@code @Component} implementation and this one steps aside.
     *
     * <p>{@code @ConditionalOnMissingBean} is officially only guaranteed inside auto-configuration,
     * because it depends on bean definitions being registered before this method is evaluated —
     * component-scanned beans are, so a {@code @Component} implementation wins. If you ever see a
     * {@code NoUniqueBeanDefinitionException} for this type, put {@code @Primary} on yours.
     */
    @Bean
    @ConditionalOnMissingBean(PaymentOutcomePublisher.class)
    public PaymentOutcomePublisher loggingPaymentOutcomePublisher() {
        return new LoggingPaymentOutcomePublisher();
    }
}
