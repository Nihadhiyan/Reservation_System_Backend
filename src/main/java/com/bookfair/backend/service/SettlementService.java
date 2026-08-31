package com.bookfair.backend.service;

import static java.util.Objects.*;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.dto.settlement.mapper.SettlementMapper;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.model.EventSettlement;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.TransactionHistory;
import com.bookfair.backend.model.enums.RentType;
import com.bookfair.backend.model.enums.SettlementStatus;
import com.bookfair.backend.model.enums.TransactionRole;
import com.bookfair.backend.repository.EventSettlementRepository;
import com.bookfair.backend.repository.TransactionHistoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private final EventSettlementRepository settlementRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;
    private final PricingEngineService pricingEngineService;
    private final SettlementMapper settlementMapper;

    @Transactional
    public EventSettlement initializeSettlement(Event event) {
        if (settlementRepository.findByEventId(event.getId()).isPresent()) {
            throw new BusinessException("Settlement already initialized for this event",
                    ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        BigDecimal snapshottedRate = event.getVenue().getDailyRentRate() != null ? event.getVenue().getDailyRentRate()
                : BigDecimal.ZERO;
        RentType snapshottedType = event.getVenue().getRentType() != null
                ? event.getVenue().getRentType()
                : RentType.FLAT_DAILY;

        int durationDays = (int) java.time.Duration.between(event.getStartDateTime(), event.getEndDateTime()).toDays();
        if (durationDays <= 0)
            durationDays = 1;

        BigDecimal totalRent = pricingEngineService.calculateVenueRent(event.getVenue(), durationDays);
        SettlementStatus status = totalRent.compareTo(BigDecimal.ZERO) == 0
                ? SettlementStatus.LIABILITY_COVERED
                : SettlementStatus.LIABILITY_PENDING;

        EventSettlement settlement = settlementMapper.toEventSettlement(event, snapshottedRate, snapshottedType,
                totalRent, status);

        return settlementRepository.save(requireNonNull(settlement));
    }

    @Transactional
    public void processVendorPayment(Reservation reservation, BigDecimal amount) {
        Event event = reservation.getEvent();

        // 1. Log incoming transaction from Vendor to Organizer
        TransactionHistory vendorTx = settlementMapper.toTransactionHistory(
                event, amount, TransactionRole.VENDOR, TransactionRole.ORGANIZER,
                reservation, "Vendor payment for reservation " + reservation.getId());
        transactionHistoryRepository.save(requireNonNull(vendorTx));

        // 2. Fetch or initialize Settlement
        EventSettlement settlement = settlementRepository.findByEventId(event.getId())
                .orElseGet(() -> initializeSettlement(event));

        // 3. Waterfall Logic
        BigDecimal remainingRent = settlement.getRemainingBalance();

        if (remainingRent.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amountToCover = remainingRent.min(amount);

            settlement.setRemainingBalance(remainingRent.subtract(amountToCover));
            settlement.setAmountPaidToOwner(settlement.getAmountPaidToOwner().add(amountToCover));

            // Log Rent Coverage transaction (Organizer -> Venue Owner)
            TransactionHistory coverageTx = settlementMapper.toTransactionHistory(
                    event, amountToCover, TransactionRole.ORGANIZER,
                    TransactionRole.VENUE_OWNER,
                    null, "Automatic Rent Coverage Waterfall for Event " + event.getId());
            transactionHistoryRepository.save(requireNonNull(coverageTx));

            BigDecimal leftover = amount.subtract(amountToCover);
            if (leftover.compareTo(BigDecimal.ZERO) > 0) {
                settlement.setOrganizerProfit(settlement.getOrganizerProfit().add(leftover));
            }

            // Check if liability is crossed
            if (settlement.getRemainingBalance().compareTo(BigDecimal.ZERO) == 0
                    && settlement.getStatus() == SettlementStatus.LIABILITY_PENDING) {
                settlement.setStatus(SettlementStatus.LIABILITY_COVERED);
                log.info("Audit: Liability Covered threshold crossed for Event {}", event.getId());

                // Log this explicitly in TransactionHistory as an audit event
                TransactionHistory auditTx = settlementMapper.toTransactionHistory(
                        event, BigDecimal.ZERO, TransactionRole.PLATFORM,
                        TransactionRole.ORGANIZER,
                        null, "Liability Covered threshold crossed");
                transactionHistoryRepository.save(requireNonNull(auditTx));
            }
        } else {
            // Rent is fully covered, 100% goes to profit
            settlement.setOrganizerProfit(settlement.getOrganizerProfit().add(amount));

            if (settlement.getStatus() == SettlementStatus.LIABILITY_COVERED) {
                settlement.setStatus(SettlementStatus.PROFIT_RELEASED);
            }
        }

        settlementRepository.save(settlement);
    }

    @Transactional(readOnly = true)
    public boolean canOrganizerWithdraw(Event event, BigDecimal withdrawalAmount) {
        EventSettlement settlement = settlementRepository.findByEventId(event.getId())
                .orElseThrow(() -> new BusinessException("Settlement not found", ErrorCode.BUSINESS_RULE_VIOLATION));

        if (settlement.getStatus() != SettlementStatus.PROFIT_RELEASED
                && settlement.getStatus() != SettlementStatus.LIABILITY_COVERED) {
            throw new BusinessException("Cannot withdraw funds: Liability not yet covered for Event " + event.getId(),
                    ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        return settlement.getOrganizerProfit().compareTo(withdrawalAmount) >= 0;
    }
}
