CREATE TABLE friendships (
    id UUID PRIMARY KEY,
    user_low_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_high_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_by_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    entity_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_friendships_user_pair UNIQUE (user_low_id, user_high_id),
    CONSTRAINT ck_friendships_distinct_users CHECK (user_low_id <> user_high_id),
    CONSTRAINT ck_friendships_canonical_order CHECK (
        CAST(user_low_id AS VARCHAR(36)) < CAST(user_high_id AS VARCHAR(36))
    ),
    CONSTRAINT ck_friendships_requester_is_member CHECK (
        requested_by_id = user_low_id OR requested_by_id = user_high_id
    ),
    CONSTRAINT ck_friendships_status CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT ck_friendships_acceptance_time CHECK (
        (status = 'PENDING' AND accepted_at IS NULL)
        OR (status = 'ACCEPTED' AND accepted_at IS NOT NULL)
    )
);

CREATE INDEX idx_friendships_low_status ON friendships(user_low_id, status);
CREATE INDEX idx_friendships_high_status ON friendships(user_high_id, status);
CREATE INDEX idx_friendships_requester_status ON friendships(requested_by_id, status);
