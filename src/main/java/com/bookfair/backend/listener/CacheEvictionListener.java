package com.bookfair.backend.listener;

import static java.util.Objects.*;
import java.util.UUID;


import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bookfair.backend.event.cache.GenreUpdatedEvent;
import com.bookfair.backend.event.cache.HallUpdatedEvent;
import com.bookfair.backend.event.cache.PricingRuleUpdatedEvent;
import com.bookfair.backend.event.cache.VenueCreatedEvent;
import com.bookfair.backend.event.cache.VenueUpdatedEvent;
import com.bookfair.backend.event.organization.OrganizationCreatedEvent;
import com.bookfair.backend.event.organization.OrganizationDeactivatedEvent;
import com.bookfair.backend.event.organization.UserJoinedOrganizationEvent;
import com.bookfair.backend.event.cache.OrganizationUpdatedEvent;
import com.bookfair.backend.event.cache.EventUpdatedEvent;
import com.bookfair.backend.event.user.UserUpdatedEvent;
import com.bookfair.backend.repository.EventRepository;
import com.bookfair.backend.repository.OrganizationMemberRepository;
import com.bookfair.backend.event.cache.LayoutUpdatedEvent;
import com.bookfair.backend.event.hierarchy.VenueDeactivatedEvent;
import com.bookfair.backend.event.stall.StallCreatedEvent;
import com.bookfair.backend.event.stall.StallDeactivatedEvent;
import com.bookfair.backend.event.stall.StallStatusChangedEvent;
import com.bookfair.backend.event.user.UserDeletedEvent;

import org.springframework.lang.NonNull;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheEvictionListener {

    private final CacheManager cacheManager;
    private final EventRepository eventRepository;
    private final OrganizationMemberRepository memberRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPricingRuleUpdated(PricingRuleUpdatedEvent event) {
        log.info("Evicting pricing rule cache after commit for rule ID: {}", event.ruleId());
        evictCache("pricingRules");
        evictCache("activeRules");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVenueCreated(VenueCreatedEvent event) {
        log.info("Evicting venue cache after commit for newly created venue ID: {}", event.venueId());
        evictCache("venues");
        evictCache("venueMap");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVenueUpdated(VenueUpdatedEvent event) {
        log.info("Evicting venue cache after commit for venue ID: {}", event.venueId());
        evictCache("venues");
        evictCache("venueMap");
        evictCacheEntry("venue", requireNonNull(event.venueId(), "Venue ID cannot be null"));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHallUpdated(HallUpdatedEvent event) {
        log.info("Evicting hall cache after commit for hall ID: {}", event.hallId());
        evictCache("halls");
        evictCache("hallLayout");
        evictCacheEntry("hall", requireNonNull(event.hallId(), "Hall ID cannot be null"));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGenreUpdated(GenreUpdatedEvent event) {
        log.info("Evicting genre cache after commit for genre ID: {}", event.genreId());
        evictCache("genres");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVenueDeactivated(VenueDeactivatedEvent event) {
        log.info("Evicting venue cache after deactivation commit for venue ID: {}", event.venueId());
        evictCache("venues");
        evictCache("venueMap");
        evictCacheEntry("venue", requireNonNull(event.venueId(), "Venue ID cannot be null"));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrganizationCreated(OrganizationCreatedEvent event) {
        log.info("Evicting organizationList cache after commit for created organization ID: {}", event.organizationId());
        evictCache("organizationList");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrganizationUpdated(OrganizationUpdatedEvent event) {
        log.info("Evicting organization, organizationList, events, and userProfiles cache after commit for organization ID: {}",
                event.organizationId());
        evictForOrganization(event.organizationId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrganizationDeactivated(OrganizationDeactivatedEvent event) {
        log.info("Evicting organization, organizationList, events, and userProfiles cache after deactivation commit for organization ID: {}",
                event.organizationId());
        evictForOrganization(event.organizationId());
    }

    private void evictForOrganization(UUID organizationId) {
        eventRepository.findAllByOrganizerIdAndActiveTrue(organizationId).forEach((orgEvent) -> {
            evictCacheEntry("events", requireNonNull(orgEvent.getId(), "Event ID cannot be null"));
        });

        memberRepository.findAllByOrganizationIdAndActiveTrue(organizationId).forEach((member) -> {
            UUID memberUserId = requireNonNull(member.getUser().getId(), "User ID cannot be null");
            evictCacheEntry("userProfiles", memberUserId);
            evictCacheEntry("userOrganizations", memberUserId);
        });

        evictCacheEntry("organization", requireNonNull(organizationId, "Organization ID cannot be null"));
        evictCache("organizationList");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventUpdated(EventUpdatedEvent event) {
        log.info("Evicting events and eventStalls cache after commit for event ID: {}", event.eventId());
        evictCache("events");
        evictCache("eventStalls");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserUpdated(UserUpdatedEvent event) {
        log.info("Evicting userProfiles cache after commit for user ID: {} / username: {}", event.userId(),
                event.username());
        evictCacheEntry("userProfiles", requireNonNull(event.userId()));
    }



    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLayoutUpdated(LayoutUpdatedEvent event) {
        log.info("Evicting hallLayout cache after commit for hall ID: {}", event.hallId());
        evictCache("hallLayout");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStallCreated(StallCreatedEvent event) {
        log.info("Evicting hallLayout cache after commit for created stall ID: {}", event.stallId());
        evictCache("hallLayout");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStallStatusChanged(StallStatusChangedEvent event) {
        log.info("Evicting hallLayout cache after commit for status change on stall ID: {}", event.stallId());
        evictCache("hallLayout");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStallDeactivated(StallDeactivatedEvent event) {
        log.info("Evicting hallLayout cache after commit for deactivated stall ID: {}", event.stallId());
        evictCache("hallLayout");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDeleted(UserDeletedEvent event) {
        log.info("Evicting userProfiles cache after commit for deleted user ID: {}", event.userId());
        evictCacheEntry("userProfiles", requireNonNull(event.userId()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserJoinedOrganization(UserJoinedOrganizationEvent event) {
        log.info("Evicting userOrganizations cache after commit for user ID: {}", event.userId());
        evictCacheEntry("userOrganizations", event.userId());
        evictCache("organizations");
    }

    private void evictCache(@NonNull String cacheName) {
        if (cacheManager != null && cacheName != null) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.debug("Cleared cache: {}", cacheName);
            }
        }
    }

    private void evictCacheEntry(@NonNull String cacheName, @NonNull Object key) {
        if (cacheManager != null && cacheName != null && key != null) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                log.debug("Evicted entry {} from cache: {}", key, cacheName);
            }
        }
    }
}
