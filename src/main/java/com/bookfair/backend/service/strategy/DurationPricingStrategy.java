package com.bookfair.backend.service.strategy;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component("DURATION")
public class DurationPricingStrategy implements PricingStrategy {

    // Matches the same grammar PricingRuleValidator accepts for DURATION: >N_DAYS
    private static final Pattern DURATION_PATTERN = Pattern.compile("^>(\\d+)_DAYS$");

    @Override
    public boolean matches(String conditionValue, PricingContext context) {
        if (conditionValue == null)
            return false;

        Matcher matcher = DURATION_PATTERN.matcher(conditionValue);
        if (!matcher.matches()) {
            return false;
        }

        int threshold = Integer.parseInt(matcher.group(1));
        return context.durationDays() > threshold;
    }

    @Override
    public BigDecimal apply(BigDecimal currentPrice, BigDecimal multiplier) {
        if (multiplier == null)
            return currentPrice;
        return currentPrice.multiply(multiplier);
    }
}
