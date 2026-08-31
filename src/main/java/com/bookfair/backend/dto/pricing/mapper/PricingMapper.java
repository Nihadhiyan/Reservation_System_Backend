package com.bookfair.backend.dto.pricing.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bookfair.backend.dto.common.SimpleEventDto;
import com.bookfair.backend.dto.common.SimpleStallDto;
import com.bookfair.backend.dto.common.Mapper.CommonMapper;
import com.bookfair.backend.dto.config.GlobalMapperConfig;
import com.bookfair.backend.dto.pricing.request.PricingRuleRequest;
import com.bookfair.backend.dto.pricing.response.PricingBreakdownResponse;
import com.bookfair.backend.dto.pricing.response.PricingRuleResponse;
import com.bookfair.backend.dto.pricing.response.StallPricingResponse;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.model.PricingRule;
import com.bookfair.backend.model.Stall;

@Mapper(config = GlobalMapperConfig.class, uses = { CommonMapper.class })
public interface PricingMapper {
    SimpleEventDto toSimpleEventDto(Event event);

    SimpleStallDto toSimpleStallDto(Stall stall);



    PricingRuleResponse toPricingRuleResponse(PricingRule pricingRule);

    PricingRule toPricingRule(PricingRuleRequest request);

    @Mapping(target = "stallId", source = "stallId")
    @Mapping(target = "stallName", source = "stallName")
    @Mapping(target = "basePrice", source = "basePrice")
    @Mapping(target = "finalPrice", source = "finalPrice")
    StallPricingResponse toStallPricingResponse(UUID stallId, String stallName,
            BigDecimal basePrice, BigDecimal finalPrice);

    @Mapping(target = "stalls", source = "stalls")
    @Mapping(target = "subtotal", source = "subtotal")
    @Mapping(target = "discountAmount", source = "discountAmount")
    @Mapping(target = "taxAmount", source = "taxAmount")
    @Mapping(target = "total", source = "total")
    @Mapping(target = "currency", source = "currency")
    PricingBreakdownResponse toPricingBreakdownResponse(
            List<StallPricingResponse> stalls,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal total,
            String currency);
}
