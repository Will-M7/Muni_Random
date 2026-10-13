-- V27 belongs exclusively to db/production; it is intentionally not part of this location.
-- V20 enforces one account per identity. This FK also prevents orphan identities.
-- No cascade: operational records and historical actors must survive account lifecycle changes.
ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_operational_identity
    FOREIGN KEY (fiscalizador_id) REFERENCES fiscalizador(id);
