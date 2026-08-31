package com.bookfair.backend.model;

import jakarta.persistence.*;
import com.bookfair.backend.model.enums.SystemRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.bookfair.backend.converter.PiiEncryptionConverter;
import com.fasterxml.jackson.annotation.JsonIgnore;


@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_active", columnList = "active"),
        @Index(name = "idx_user_system_role", columnList = "system_role")
})
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(unique = true, nullable = false)
    @NotBlank(message = "Username is required")
    private String username;

    @Column(unique = true, nullable = false)
    @Email(message = "Email should be valid")
    private String email;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(nullable = false)
    @NotBlank(message = "Password is required")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false)
    private SystemRole systemRole = SystemRole.CUSTOMER;

    @Column(name = "contact_number")
    @Convert(converter = PiiEncryptionConverter.class)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String contactNumber;

    @Column(name = "address")
    @Convert(converter = PiiEncryptionConverter.class)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String address;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Embedded
    private DeletionAudit deletionAudit;

}
