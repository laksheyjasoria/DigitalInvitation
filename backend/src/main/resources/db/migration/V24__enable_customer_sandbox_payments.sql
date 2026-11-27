-- Require verified payment for customer invitations; administrator publishing is exempt in application logic.
UPDATE system_setting
SET setting_value = 'true'::jsonb,
    value_type = 'BOOLEAN',
    description = 'Require a verified payment gateway transaction for customer publication; admins are exempt',
    updated_at = now(),
    version = version + 1
WHERE setting_key = 'invitation.publication.payment-required';

UPDATE system_setting
SET setting_value = '"RAZORPAY"'::jsonb,
    value_type = 'STRING',
    description = 'Customer payment provider; use Razorpay test credentials until live launch',
    updated_at = now(),
    version = version + 1
WHERE setting_key = 'payment.provider';

INSERT INTO system_setting_version(setting_id, setting_key, setting_value, change_reason)
SELECT id, setting_key, setting_value, 'Enable customer payment checkout in Razorpay sandbox; admin publishing remains free'
FROM system_setting
WHERE setting_key IN ('invitation.publication.payment-required', 'payment.provider');
