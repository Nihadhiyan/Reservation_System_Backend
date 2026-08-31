package com.bookfair.backend.listener;

import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.bookfair.backend.event.organization.OrganizationCapabilityChangedEvent;
import com.bookfair.backend.event.organization.OrganizationDeactivatedEvent;
import com.bookfair.backend.model.OrganizationMember;
import com.bookfair.backend.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationEventListener {

    private final OrganizationMemberRepository memberRepository;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrganizationCapabilityChanged(OrganizationCapabilityChangedEvent event) {
        log.info("Capability changed for organization {}", event.organizationId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeactivated(OrganizationDeactivatedEvent event) {
        log.info("Processing deactivation for organization: {}", event.organizationId());

        // Find all active memberships for this organization
        List<OrganizationMember> members = memberRepository.findByOrganizationIdAndActiveTrue(event.organizationId());

        if (!members.isEmpty()) {
            members.forEach(member -> member.setActive(false));
            memberRepository.saveAll(members);
            log.info("Deactivated {} members for organization {}", members.size(), event.organizationId());
        }
    }
}
