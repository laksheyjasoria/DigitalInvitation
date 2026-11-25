INSERT INTO system_setting(setting_key,setting_value,value_type,description) VALUES
 ('invitation.default-timezone','Asia/Kolkata'::jsonb,'STRING','Default event timezone for new invitations'),
 ('invitation.custom-sections.enabled','true'::jsonb,'BOOLEAN','Allow customers to add custom sections'),
 ('invitation.rsvp.enabled','false'::jsonb,'BOOLEAN','Enable RSVP functionality'),
 ('invitation.supported-locales','["en","hi"]'::jsonb,'JSON','Supported invitation locales'),
 ('invitation.expiry.extension-days','7'::jsonb,'INTEGER','Calendar days added by an extension purchase'),
 ('invitation.expiry.extension-price-minor','19900'::jsonb,'INTEGER','Price for the configured extension duration'),
 ('pricing.tax-percent','0'::jsonb,'DECIMAL','Tax percentage applied at checkout'),
 ('pricing.coupons.enabled','false'::jsonb,'BOOLEAN','Enable coupon code redemption'),
 ('payment.provider','RAZORPAY'::jsonb,'STRING','Selected payment provider key')
ON CONFLICT(setting_key) DO NOTHING;
