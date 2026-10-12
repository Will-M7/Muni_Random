ALTER TABLE identity_integration
  ADD COLUMN encrypted_peruapi_key TEXT NULL,
  ADD COLUMN encrypted_factiliza_token TEXT NULL;

UPDATE identity_integration
SET encrypted_peruapi_key = encrypted_api_key,
    encrypted_api_key = NULL
WHERE provider = 'PERUAPI_COM';
