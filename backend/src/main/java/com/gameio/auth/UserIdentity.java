package com.gameio.auth;

import com.gameio.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_identities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_identities_provider_subject", columnNames = {"provider", "provider_subject"}),
        @UniqueConstraint(name = "uk_user_identities_provider_user", columnNames = {"provider", "user_id"})
})
class UserIdentity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdentityProvider provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserIdentity() {
    }

    private UserIdentity(UserAccount user, IdentityProvider provider, String providerSubject, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.createdAt = createdAt;
    }

    static UserIdentity create(
            UserAccount user, IdentityProvider provider, String providerSubject, Instant createdAt) {
        return new UserIdentity(user, provider, providerSubject, createdAt);
    }

    UserAccount getUser() {
        return user;
    }
}
