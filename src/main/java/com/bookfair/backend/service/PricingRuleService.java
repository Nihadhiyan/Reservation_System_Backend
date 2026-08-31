package com.bookfair.backend.service;

import static java.util.Objects.*;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.dto.pricing.mapper.PricingMapper;
import com.bookfair.backend.dto.pricing.request.PricingRuleRequest;
import com.bookfair.backend.dto.pricing.response.PricingRuleResponse;
import com.bookfair.backend.model.PricingRule;
import com.bookfair.backend.repository.PricingRuleRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import com.bookfair.backend.event.cache.PricingRuleUpdatedEvent;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.service.validator.PricingRuleValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingRuleService {

    private final PricingRuleRepository pricingRuleRepository;
    private final PricingRuleValidator pricingRuleValidator;
    private final PricingMapper pricingMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Cacheable(value = "pricingRules", key = "'active'")
    @Transactional(readOnly = true)
    public List<PricingRuleResponse> getActiveRules() {
        return pricingRuleRepository.findAllByActiveTrue().stream()
                .map(pricingMapper::toPricingRuleResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PricingRuleResponse createPricingRule(PricingRuleRequest request) {
        pricingRuleValidator.validate(request.conditionType(), request.conditionValue());

        PricingRule rule = pricingMapper.toPricingRule(request);
        rule.setActive(true);

        PricingRule saved = pricingRuleRepository.save(rule);
        eventPublisher.publishEvent(new PricingRuleUpdatedEvent(saved.getId()));

        log.info("Created new pricing rule: {}", saved.getName());
        return pricingMapper.toPricingRuleResponse(saved);
    }

    @Transactional
    public PricingRuleResponse updatePricingRule(UUID id, PricingRuleRequest request) {
        pricingRuleValidator.validate(request.conditionType(), request.conditionValue());
        PricingRule rule = pricingRuleRepository.findById(requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rule not found", ErrorCode.PRICING_RULE_NOT_FOUND));
        rule.setName(request.name());
        rule.setDescription(request.description());
        rule.setConditionType(request.conditionType());
        rule.setConditionValue(request.conditionValue());
        rule.setMultiplier(request.multiplier());

        PricingRule saved = pricingRuleRepository.save(rule);
        eventPublisher.publishEvent(new PricingRuleUpdatedEvent(saved.getId()));
        return pricingMapper.toPricingRuleResponse(saved);
    }

    @Transactional
    public void deletePricingRule(UUID id) {
        PricingRule rule = pricingRuleRepository.findById(requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rule not found", ErrorCode.PRICING_RULE_NOT_FOUND));
        rule.setActive(false);
        pricingRuleRepository.save(rule);
        eventPublisher.publishEvent(new PricingRuleUpdatedEvent(id));
        log.info("Soft deleted pricing rule: {}", id);
    }
}
