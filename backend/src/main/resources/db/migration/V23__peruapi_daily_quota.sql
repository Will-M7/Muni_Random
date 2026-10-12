CREATE TABLE peruapi_dni_daily_quota (
    quota_date DATE NOT NULL PRIMARY KEY,
    request_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_peruapi_dni_quota_nonnegative CHECK (request_count >= 0)
);
