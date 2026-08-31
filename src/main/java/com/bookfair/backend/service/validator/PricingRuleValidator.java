package com.bookfair.backend.service.validator;

import java.util.Set;
import org.springframework.stereotype.Component;

import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.model.enums.ConditionType;

@Component
public class PricingRuleValidator {

    private static final Set<String> VALID_ORG_TYPES = Set.of(
            "NON_PROFIT", "CORPORATE", "STARTUP", "GOVERNMENT", "EDUCATION"
    );

    public void validate(ConditionType type, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException("Condition value cannot be empty", ErrorCode.VALIDATION_ERROR);
        }

        switch (type) {
            case ORG_TYPE:
                if (!VALID_ORG_TYPES.contains(value.toUpperCase())) {
                    throw new BusinessException("Invalid ORG_TYPE value. Must be one of: " + VALID_ORG_TYPES, ErrorCode.VALIDATION_ERROR);
                }
                break;

            case DURATION:
                if (!value.matches("^>\\d+_DAYS$")) {
                    throw new BusinessException("Invalid DURATION value. Must match pattern ^>\\d+_DAYS$ (e.g., >7_DAYS)", ErrorCode.VALIDATION_ERROR);
                }
                break;

            case SEASONAL:
                if (!value.equalsIgnoreCase("SUMMER") && !value.equalsIgnoreCase("WINTER") &&
                    !value.equalsIgnoreCase("SPRING") && !value.equalsIgnoreCase("FALL")) {
                    throw new BusinessException("Invalid SEASONAL value. Must be SUMMER, WINTER, SPRING, or FALL", ErrorCode.VALIDATION_ERROR);
                }
                break;

            default:
                throw new BusinessException("Unknown ConditionType: " + type, ErrorCode.VALIDATION_ERROR);
        }
    }
}
