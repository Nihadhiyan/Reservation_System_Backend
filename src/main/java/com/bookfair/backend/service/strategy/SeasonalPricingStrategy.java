package com.bookfair.backend.service.strategy;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component("SEASONAL")
public class SeasonalPricingStrategy implements PricingStrategy {

    @Override
    public boolean matches(String conditionValue, PricingContext context) {
        if (conditionValue == null || context.eventStartDate() == null) return false;

        int month = context.eventStartDate().atZone(ZoneOffset.UTC).getMonthValue();

        return switch (conditionValue.toUpperCase()) {
            case "SUMMER" -> month >= 6 && month <= 8;  // June, July, August
            case "FALL" -> month >= 9 && month <= 11;   // September, October, November
            case "WINTER" -> month == 12 || month <= 2; // December, January, February
            case "SPRING" -> month >= 3 && month <= 5;  // March, April, May
            default -> false;
        };
    }

    @Override
    public BigDecimal apply(BigDecimal currentPrice, BigDecimal multiplier) {
        if (multiplier == null) return currentPrice;
        return currentPrice.multiply(multiplier);
    }
}
