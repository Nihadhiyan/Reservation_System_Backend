package com.bookfair.backend.service;

import org.springframework.stereotype.Service;

import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.enums.SystemRole;
import com.bookfair.backend.model.enums.OrganizationRole;
import com.bookfair.backend.model.OrganizationMember;
import com.bookfair.backend.model.User;
import com.bookfair.backend.repository.OrganizationMemberRepository;

import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReservationAuthorizationService {

    private final OrganizationMemberRepository memberRepository;

    public boolean canViewReservation(User user, Reservation reservation) {
        if (user.getSystemRole() == SystemRole.SUPER_ADMIN) {
            return true;
        }

        if (reservation.getUser().getId().equals(user.getId())) {
            return true;
        }

        Optional<OrganizationMember> memberOpt = memberRepository.findByUserIdAndOrganizationId(
                user.getId(), reservation.getEvent().getOrganizer().getId());

        return memberOpt.isPresent();
    }

    public boolean canManageReservation(User user, Reservation reservation) {
        if (user.getSystemRole() == SystemRole.SUPER_ADMIN) {
            return true;
        }

        if (reservation.getUser().getId().equals(user.getId())) {
            return true;
        }

        Optional<OrganizationMember> memberOpt = memberRepository.findByUserIdAndOrganizationId(
                user.getId(), reservation.getEvent().getOrganizer().getId());

        return memberOpt.map(member -> member.getRole() == OrganizationRole.ORG_ADMIN).orElse(false);
    }

    public boolean canConfirmReservation(User user, Reservation reservation) {
        if (user.getSystemRole() == SystemRole.SUPER_ADMIN) {
            return true;
        }

        Optional<OrganizationMember> memberOpt = memberRepository.findByUserIdAndOrganizationId(
                user.getId(), reservation.getEvent().getOrganizer().getId());

        return memberOpt.isPresent(); // ORG_ADMIN or ORG_MEMBER can confirm
    }

    public boolean canApproveRefund(User user, Reservation reservation) {
        if (user.getSystemRole() == SystemRole.SUPER_ADMIN) {
            return true;
        }

        Optional<OrganizationMember> memberOpt = memberRepository.findByUserIdAndOrganizationId(
                user.getId(), reservation.getEvent().getOrganizer().getId());

        return memberOpt.map(member -> member.getRole() == OrganizationRole.ORG_ADMIN).orElse(false);
    }
}
