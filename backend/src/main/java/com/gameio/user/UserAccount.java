package com.gameio.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id
    private UUID id;

    @Column(nullable = false, length = 24)
    private String username;

    @Column(name = "username_normalized", nullable = false, length = 24, unique = true)
    private String usernameNormalized;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = 254, unique = true)
    private String emailNormalized;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private long exp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "entity_version", nullable = false)
    private long version;

    protected UserAccount() {
    }

    private UserAccount(UUID id, String username, String email, String passwordHash, Instant now) {
        this.id = id;
        this.username = username;
        this.usernameNormalized = normalize(username);
        this.email = email;
        this.emailNormalized = normalize(email);
        this.passwordHash = passwordHash;
        this.role = UserRole.USER;
        this.level = 1;
        this.exp = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static UserAccount create(String username, String email, String passwordHash, Instant now) {
        return new UserAccount(UUID.randomUUID(), username.trim(), email.trim(), passwordHash, now);
    }

    public static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public void grantExperience(long amount, int resultingLevel, Instant now) {
        if (amount < 0 || resultingLevel < level) {
            throw new IllegalArgumentException("Experience grant must be non-negative and cannot lower level");
        }
        this.exp = Math.addExact(this.exp, amount);
        this.level = resultingLevel;
        this.updatedAt = now;
    }

    public void updateProfile(String avatarUrl, Instant now) {
        this.avatarUrl = avatarUrl;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public UserRole getRole() {
        return role;
    }

    public int getLevel() {
        return level;
    }

    public long getExp() {
        return exp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof UserAccount user && Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
