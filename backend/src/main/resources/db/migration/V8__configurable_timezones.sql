INSERT INTO system_setting(setting_key,setting_value,value_type,description)
VALUES (
    'invitation.supported-timezones',
    '["Asia/Kolkata","UTC","Europe/London","America/New_York"]'::jsonb,
    'JSON',
    'Timezones available in the invitation editor; valid IANA timezone identifiers'
)
ON CONFLICT(setting_key) DO NOTHING;
