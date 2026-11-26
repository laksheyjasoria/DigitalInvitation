-- Payment-provider configuration is intentionally deferred for the initial rollout.
-- Customers can publish without payment until an administrator enables a configured provider.
UPDATE system_setting
SET setting_value = 'false'::jsonb,
    value_type = 'BOOLEAN',
    description = 'Require a verified provider payment before publication; enable only after payment provider setup and sandbox verification',
    updated_at = now(),
    version = version + 1
WHERE setting_key = 'invitation.publication.payment-required';

INSERT INTO system_setting(setting_key, setting_value, value_type, description)
VALUES ('payment.provider', '"NONE"'::jsonb, 'STRING',
        'Selected payment provider. NONE disables checkout until a provider is configured.')
ON CONFLICT (setting_key) DO UPDATE
SET setting_value = EXCLUDED.setting_value,
    value_type = EXCLUDED.value_type,
    description = EXCLUDED.description,
    updated_at = now(),
    version = system_setting.version + 1;

INSERT INTO system_setting_version(setting_id, setting_key, setting_value, change_reason)
SELECT id, setting_key, setting_value, 'Initial rollout: payment provider setup deferred'
FROM system_setting
WHERE setting_key IN ('invitation.publication.payment-required', 'payment.provider');
