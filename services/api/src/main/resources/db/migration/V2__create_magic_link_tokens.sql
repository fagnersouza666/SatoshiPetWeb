CREATE TABLE magic_link_tokens (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_magic_link_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_magic_link_tokens_expiration_after_issue CHECK (expires_at > issued_at),
    CONSTRAINT chk_magic_link_tokens_consumed_after_issue
        CHECK (consumed_at IS NULL OR consumed_at >= issued_at)
);

CREATE INDEX idx_magic_link_tokens_expires_at ON magic_link_tokens (expires_at);
CREATE INDEX idx_magic_link_tokens_email ON magic_link_tokens (email);
