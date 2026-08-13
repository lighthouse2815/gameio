ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE user_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_identities_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_user_identities_provider_user UNIQUE (provider, user_id)
);

CREATE INDEX idx_user_identities_user ON user_identities(user_id);
