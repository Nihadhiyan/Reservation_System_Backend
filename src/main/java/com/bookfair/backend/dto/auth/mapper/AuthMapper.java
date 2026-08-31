package com.bookfair.backend.dto.auth.mapper;

import java.time.Instant;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bookfair.backend.dto.auth.request.RegisterRequest;
import com.bookfair.backend.dto.auth.response.AuthResponse;
import com.bookfair.backend.dto.auth.response.UserProfileResponse;
import com.bookfair.backend.dto.common.Mapper.CommonMapper;
import com.bookfair.backend.dto.config.GlobalMapperConfig;
import com.bookfair.backend.model.RefreshToken;
import com.bookfair.backend.model.User;

@Mapper(config = GlobalMapperConfig.class, uses = { CommonMapper.class })
public interface AuthMapper {
    AuthResponse toAuthResponse(User user, String accessToken, String refreshToken, Long expiresIn);

    UserProfileResponse toUserProfileResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true) // Ignore the raw password for security
    @Mapping(target = "active", constant = "true") // Automatically set active to true
    @Mapping(target = "emailVerified", constant = "false") // Automatically set verified to false
    @Mapping(target = "systemRole", ignore = true)
    User toUserFromRegisterRequest(RegisterRequest registerRequest);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "jti", source = "tokenJti")
    @Mapping(target = "expiryDate", source = "expiryDate")
    @Mapping(target = "ipAddress", source = "ipAddress")
    @Mapping(target = "deviceInfo", source = "deviceInfo")
    @Mapping(target = "familyId", source = "familyId")
    @Mapping(target = "revoked", constant = "false")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    RefreshToken toRefreshToken(User user, String tokenJti, Instant expiryDate,
            String ipAddress, String deviceInfo, String familyId);
}
