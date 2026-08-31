package com.bookfair.backend.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import com.bookfair.backend.model.LayoutMarker;
import com.bookfair.backend.model.enums.OrganizationCapability;
import com.bookfair.backend.model.enums.OrganizationRole;
import com.bookfair.backend.repository.BuildingRepository;
import com.bookfair.backend.repository.EventRepository;
import com.bookfair.backend.repository.FloorRepository;
import com.bookfair.backend.repository.HallRepository;
import com.bookfair.backend.repository.LayoutMarkerRepository;
import com.bookfair.backend.repository.OrganizationRepository;
import com.bookfair.backend.repository.StallRepository;
import com.bookfair.backend.repository.VenueRepository;

import lombok.RequiredArgsConstructor;

@Component("orgAuth")
@RequiredArgsConstructor
public class OrganizationSecurityEvaluator {

    private final OrganizationRepository organizationRepository;
    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final HallRepository hallRepository;
    private final StallRepository stallRepository;
    private final LayoutMarkerRepository layoutMarkerRepository;

    public boolean isOrganizerAdmin(Authentication authentication, UUID orgId) {
        return checkPermission(authentication, orgId, OrganizationCapability.ORGANIZES_EVENTS, OrganizationRole.ORG_ADMIN);
    }

    public boolean isOrgAdmin(Authentication authentication, UUID orgId) {
        return checkPermission(authentication, orgId, null, OrganizationRole.ORG_ADMIN);
    }

    public boolean isVendorAdmin(Authentication authentication, UUID orgId) {
        return checkPermission(authentication, orgId, OrganizationCapability.OPERATES_STALLS, OrganizationRole.ORG_ADMIN);
    }

    public boolean isVenueOwnerAdmin(Authentication authentication, UUID orgId) {
        return checkPermission(authentication, orgId, OrganizationCapability.OWNS_VENUES, OrganizationRole.ORG_ADMIN);
    }

    public boolean isVenueOwnerAdminByVenue(Authentication authentication, UUID venueId) {
        if (isSuperAdmin(authentication)) return true;
        return venueRepository.findById(Objects.requireNonNull(venueId, "Venue ID cannot be null"))
                .map(venue -> isVenueOwnerAdmin(authentication, venue.getOwner().getId()))
                .orElse(false);
    }

    public boolean isVenueOwnerAdminByBuilding(Authentication authentication, UUID buildingId) {
        if (isSuperAdmin(authentication)) return true;
        return buildingRepository.findById(Objects.requireNonNull(buildingId, "Building ID cannot be null"))
                .map(building -> building.getVenue().getOwner().getId())
                .map(orgId -> isVenueOwnerAdmin(authentication, orgId))
                .orElse(false);
    }

    public boolean isVenueOwnerAdminByFloor(Authentication authentication, UUID floorId) {
        if (isSuperAdmin(authentication)) return true;
        return floorRepository.findById(Objects.requireNonNull(floorId, "Floor ID cannot be null"))
                .map(floor -> floor.getBuilding().getVenue().getOwner().getId())
                .map(orgId -> isVenueOwnerAdmin(authentication, orgId))
                .orElse(false);
    }

    public boolean isVenueOwnerAdminByHall(Authentication authentication, UUID hallId) {
        if (isSuperAdmin(authentication)) return true;
        return hallRepository.findById(Objects.requireNonNull(hallId, "Hall ID cannot be null"))
                .map(hall -> hall.getFloor().getBuilding().getVenue().getOwner().getId())
                .map(orgId -> isVenueOwnerAdmin(authentication, orgId))
                .orElse(false);
    }

    public boolean isVenueOwnerAdminByStall(Authentication authentication, UUID stallId) {
        if (isSuperAdmin(authentication)) return true;
        return stallRepository.findById(Objects.requireNonNull(stallId, "Stall ID cannot be null"))
                .map(stall -> stall.getHall().getFloor().getBuilding().getVenue().getOwner().getId())
                .map(orgId -> isVenueOwnerAdmin(authentication, orgId))
                .orElse(false);
    }

    // For creating a new LayoutMarker: exactly one of venueId/buildingId/hallId is populated
    // in the request (validated by the entity itself), so check whichever one is set.
    public boolean isVenueOwnerAdminForNewLayoutMarker(Authentication authentication, UUID venueId, UUID buildingId,
            UUID hallId) {
        if (isSuperAdmin(authentication)) return true;
        if (venueId != null) return isVenueOwnerAdminByVenue(authentication, venueId);
        if (buildingId != null) return isVenueOwnerAdminByBuilding(authentication, buildingId);
        if (hallId != null) return isVenueOwnerAdminByHall(authentication, hallId);
        return false;
    }

    public boolean isVenueOwnerAdminByLayoutMarker(Authentication authentication, UUID markerId) {
        if (isSuperAdmin(authentication)) return true;
        LayoutMarker marker = layoutMarkerRepository.findById(Objects.requireNonNull(markerId, "Marker ID cannot be null"))
                .orElse(null);
        if (marker == null) return false;
        if (marker.getVenue() != null) return isVenueOwnerAdmin(authentication, marker.getVenue().getOwner().getId());
        if (marker.getBuilding() != null) return isVenueOwnerAdmin(authentication, marker.getBuilding().getVenue().getOwner().getId());
        if (marker.getHall() != null) return isVenueOwnerAdmin(authentication, marker.getHall().getFloor().getBuilding().getVenue().getOwner().getId());
        return false;
    }

    public boolean isOrganizerAdminByEvent(Authentication authentication, UUID eventId) {
        if (isSuperAdmin(authentication)) return true;
        return eventRepository.findById(Objects.requireNonNull(eventId, "Event ID cannot be null"))
                .map(event -> isOrganizerAdmin(authentication, event.getOrganizer().getId()))
                .orElse(false);
    }

    public boolean isMemberOf(Authentication authentication, UUID orgId) {
        if (isSuperAdmin(authentication)) return true;
        Objects.requireNonNull(orgId, "Organization ID cannot be null");
        Map<String, String> orgRoles = extractOrgRoles(authentication);
        return orgRoles.containsKey(orgId.toString());
    }

    private boolean checkPermission(Authentication authentication, UUID orgId, OrganizationCapability requiredCapability,
            OrganizationRole requiredRole) {
        if (isSuperAdmin(authentication)) return true;
        Objects.requireNonNull(orgId, "Organization ID cannot be null");

        Map<String, String> orgRoles = extractOrgRoles(authentication);
        String orgIdStr = orgId.toString();

        if (!orgRoles.containsKey(orgIdStr)) {
            return false; // Not a member of this org at all
        }

        String actualRole = orgRoles.get(orgIdStr);
        if (!actualRole.equalsIgnoreCase(requiredRole.name())) {
            return false;
        }

        return organizationRepository.findByIdAndActiveTrue(orgId)
                .map(org -> {
                    if (requiredCapability == null) return true;
                    return switch (requiredCapability) {
                        case ORGANIZES_EVENTS -> org.isEventOrganizer();
                        case OPERATES_STALLS -> org.isVendor();
                        case OWNS_VENUES -> org.isVenueOwner();
                    };
                })
                .orElse(false);
    }

    private boolean isSuperAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }

    private Map<String, String> extractOrgRoles(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Map.of(); // Empty map if not authenticated properly
        }

        Map<String, String> orgRoles = new HashMap<>();

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String authString = authority.getAuthority();

            if (authString != null && authString.startsWith("ORG_")) {
                String withoutPrefix = authString.substring("ORG_".length());

                int underscoreIndex = withoutPrefix.indexOf('_');

                if (underscoreIndex != -1) {
                    String orgIdStr = withoutPrefix.substring(0, underscoreIndex);
                    String role = withoutPrefix.substring(underscoreIndex + 1);

                        try {
                            UUID orgId = UUID.fromString(orgIdStr);
                            OrganizationRole orgRole = OrganizationRole.valueOf(role.toUpperCase());
                            
                            if (orgRole == OrganizationRole.ORG_ADMIN) {
                                orgRoles.put(orgId.toString(), orgRole.name());
                            } else if (orgRole == OrganizationRole.ORG_MEMBER) {
                                orgRoles.putIfAbsent(orgId.toString(), orgRole.name());
                            }
                        } catch (IllegalArgumentException e) {
                            continue;
                        }
                }
            }

        }

        return orgRoles;
    }
}