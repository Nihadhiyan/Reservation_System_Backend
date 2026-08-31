package com.bookfair.backend.security;

import com.bookfair.backend.model.*;
import com.bookfair.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationSecurityEvaluatorTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private VenueRepository venueRepository;
    @Mock private EventRepository eventRepository;
    @Mock private BuildingRepository buildingRepository;
    @Mock private FloorRepository floorRepository;
    @Mock private HallRepository hallRepository;
    @Mock private StallRepository stallRepository;
    @Mock private LayoutMarkerRepository layoutMarkerRepository;

    @InjectMocks
    private OrganizationSecurityEvaluator evaluator;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
    }

    private Authentication authWith(String... authorities) {
        List<SimpleGrantedAuthority> granted = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new TestingAuthenticationToken("user", "pw", granted);
    }

    private Organization activeVendorOrg() {
        Organization org = new Organization();
        org.setId(orgId);
        org.setActive(true);
        org.setCapabilities(java.util.Set.of(com.bookfair.backend.model.enums.OrganizationCapability.OPERATES_STALLS));
        return org;
    }

    @Test
    void superAdmin_bypassesEverythingWithoutTouchingRepositories() {
        Authentication auth = authWith("ROLE_SUPER_ADMIN");

        assertThat(evaluator.isOrgAdmin(auth, orgId)).isTrue();
        assertThat(evaluator.isVenueOwnerAdminByHall(auth, UUID.randomUUID())).isTrue();

        org.mockito.Mockito.verifyNoInteractions(organizationRepository, hallRepository);
    }

    @Test
    void isOrgAdmin_true_whenOrgAdminAuthorityAndOrgActive() {
        Authentication auth = authWith("ORG_" + orgId + "_ORG_ADMIN");
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(activeVendorOrg()));

        assertThat(evaluator.isOrgAdmin(auth, orgId)).isTrue();
    }

    @Test
    void isOrgAdmin_false_whenOnlyOrgMemberAuthority() {
        Authentication auth = authWith("ORG_" + orgId + "_ORG_MEMBER");

        assertThat(evaluator.isOrgAdmin(auth, orgId)).isFalse();
    }

    @Test
    void isOrgAdmin_false_whenNotAMemberAtAll() {
        Authentication auth = authWith("ORG_" + UUID.randomUUID() + "_ORG_ADMIN");

        assertThat(evaluator.isOrgAdmin(auth, orgId)).isFalse();
    }

    @Test
    void isOrgAdmin_false_whenOrgIsInactive() {
        // Regression test for the inactive-org gap fix: checkPermission must use
        // findByIdAndActiveTrue, not findById — an admin of a deactivated org must
        // not pass authorization checks.
        Authentication auth = authWith("ORG_" + orgId + "_ORG_ADMIN");
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.empty());

        assertThat(evaluator.isOrgAdmin(auth, orgId)).isFalse();
    }

    @Test
    void isVendorAdmin_checksVendorCapabilitySpecifically() {
        Authentication auth = authWith("ORG_" + orgId + "_ORG_ADMIN");
        Organization nonVendorOrg = new Organization();
        nonVendorOrg.setId(orgId);
        nonVendorOrg.setActive(true);
        nonVendorOrg.setCapabilities(java.util.Set.of(com.bookfair.backend.model.enums.OrganizationCapability.ORGANIZES_EVENTS));
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(nonVendorOrg));

        assertThat(evaluator.isVendorAdmin(auth, orgId)).isFalse();
    }

    @Test
    void isVenueOwnerAdminByHall_resolvesThroughFullChain() {
        Organization owner = new Organization();
        owner.setId(orgId);
        owner.setActive(true);
        owner.setCapabilities(java.util.Set.of(com.bookfair.backend.model.enums.OrganizationCapability.OWNS_VENUES));

        Venue venue = new Venue();
        venue.setOwner(owner);
        Building building = new Building();
        building.setVenue(venue);
        Floor floor = new Floor();
        floor.setBuilding(building);
        Hall hall = new Hall();
        hall.setFloor(floor);

        UUID hallId = UUID.randomUUID();
        Authentication auth = authWith("ORG_" + orgId + "_ORG_ADMIN");
        when(hallRepository.findById(hallId)).thenReturn(Optional.of(hall));
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(owner));

        assertThat(evaluator.isVenueOwnerAdminByHall(auth, hallId)).isTrue();
    }

    @Test
    void isOrganizerAdminByEvent_resolvesThroughEventOrganizer() {
        Organization organizer = new Organization();
        organizer.setId(orgId);
        organizer.setActive(true);
        organizer.setCapabilities(java.util.Set.of(com.bookfair.backend.model.enums.OrganizationCapability.ORGANIZES_EVENTS));

        Event event = new Event();
        event.setOrganizer(organizer);

        UUID eventId = UUID.randomUUID();
        Authentication auth = authWith("ORG_" + orgId + "_ORG_ADMIN");
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(organizer));

        assertThat(evaluator.isOrganizerAdminByEvent(auth, eventId)).isTrue();
    }

    @Test
    void isMemberOf_trueForEitherRole() {
        Authentication auth = authWith("ORG_" + orgId + "_ORG_MEMBER");

        assertThat(evaluator.isMemberOf(auth, orgId)).isTrue();
    }

    @Test
    void isMemberOf_falseWhenNotAMember() {
        Authentication auth = authWith("ORG_" + UUID.randomUUID() + "_ORG_ADMIN");

        assertThat(evaluator.isMemberOf(auth, orgId)).isFalse();
    }
}
