CREATE TABLE app_user (
 id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, nickname VARCHAR(80) NOT NULL,
 role VARCHAR(10) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','ADMIN')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE auth_session (
 token_hash CHAR(64) PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX auth_session_user_idx ON auth_session(user_id);
CREATE TABLE ledger (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id),
 name VARCHAR(80) NOT NULL, currency CHAR(3) NOT NULL,
 budget NUMERIC(16,2) CHECK(budget >= 0), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE place (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), name VARCHAR(120) NOT NULL,
 latitude DOUBLE PRECISION NOT NULL CHECK(latitude BETWEEN -90 AND 90),
 longitude DOUBLE PRECISION NOT NULL CHECK(longitude BETWEEN -180 AND 180),
 address VARCHAR(300) NOT NULL DEFAULT '', created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE bill (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), ledger_id UUID NOT NULL REFERENCES ledger(id),
 place_id UUID NOT NULL REFERENCES place(id), amount NUMERIC(16,2) NOT NULL CHECK(amount > 0),
 kind VARCHAR(10) NOT NULL CHECK(kind IN ('EXPENSE','INCOME','TRANSFER','REFUND')),
 category VARCHAR(80) NOT NULL, note VARCHAR(2000) NOT NULL DEFAULT '',
 occurred_at TIMESTAMPTZ NOT NULL, original_bill_id UUID REFERENCES bill(id),
 request_key UUID NOT NULL, request_hash CHAR(64) NOT NULL,
 deleted_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(user_id, request_key),
 CHECK ((kind = 'REFUND') = (original_bill_id IS NOT NULL))
);
CREATE INDEX bill_user_time_idx ON bill(user_id, occurred_at DESC) WHERE deleted_at IS NULL;
CREATE TABLE media (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), bill_id UUID NOT NULL REFERENCES bill(id),
 kind VARCHAR(5) NOT NULL CHECK(kind IN ('IMAGE','VIDEO')), mime VARCHAR(50) NOT NULL,
 sha256 CHAR(64) NOT NULL, size_bytes BIGINT NOT NULL CHECK(size_bytes > 0),
 deleted_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(user_id, sha256)
);
CREATE TABLE checkin (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), bill_id UUID NOT NULL UNIQUE REFERENCES bill(id),
 latitude DOUBLE PRECISION NOT NULL CHECK(latitude BETWEEN -90 AND 90),
 longitude DOUBLE PRECISION NOT NULL CHECK(longitude BETWEEN -180 AND 180),
 accuracy_m DOUBLE PRECISION NOT NULL CHECK(accuracy_m > 0 AND accuracy_m <= 100),
 captured_at TIMESTAMPTZ NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','VERIFIED','REJECTED','REVOKED')),
 canonical_place_id VARCHAR(160), city_id VARCHAR(160), province_id VARCHAR(160), country_id VARCHAR(160),
 reviewed_by UUID REFERENCES app_user(id), review_note VARCHAR(500), reviewed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE point_event (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), source_key VARCHAR(250) NOT NULL,
 type VARCHAR(30) NOT NULL, amount INT NOT NULL CHECK(amount > 0),
 bill_id UUID REFERENCES bill(id), media_id UUID REFERENCES media(id), region_id VARCHAR(160),
 status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','REVOKED')),
 reward_day DATE NOT NULL, rule_version INT NOT NULL DEFAULT 1,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), revoked_at TIMESTAMPTZ,
 UNIQUE(user_id, source_key)
);
CREATE INDEX point_event_user_day_idx ON point_event(user_id, reward_day, type);
CREATE TABLE audit_log (
 id BIGSERIAL PRIMARY KEY, actor_id UUID NOT NULL REFERENCES app_user(id),
 action VARCHAR(50) NOT NULL, target_id UUID NOT NULL, reason VARCHAR(500) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
