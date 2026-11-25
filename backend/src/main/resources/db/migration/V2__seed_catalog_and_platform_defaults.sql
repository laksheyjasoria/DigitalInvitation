INSERT INTO system_setting(setting_key,setting_value,value_type,description) VALUES
 ('invitation.expiry.grace-days','7'::jsonb,'INTEGER','Calendar days after the last scheduled function date; applies to new publications'),
 ('invitation.expiry.extension-enabled','false'::jsonb,'BOOLEAN','Allow customers to purchase an invitation extension'),
 ('invitation.preview.enabled','true'::jsonb,'BOOLEAN','Allow authenticated customers to preview drafts'),
 ('invitation.publication.payment-required','true'::jsonb,'BOOLEAN','Require verified payment before publication'),
 ('invitation.media.max-file-bytes','10485760'::jsonb,'INTEGER','Maximum individual media upload size in bytes'),
 ('invitation.media.max-files','30'::jsonb,'INTEGER','Maximum media files per invitation'),
 ('pricing.base-price-minor','49900'::jsonb,'INTEGER','Default invitation price in minor currency units'),
 ('pricing.currency','"INR"'::jsonb,'STRING','Default checkout currency'),
 ('consent.required.accuracy','true'::jsonb,'BOOLEAN','Customer confirms invitation details are accurate'),
 ('consent.required.preview','true'::jsonb,'BOOLEAN','Customer confirms preview was reviewed'),
 ('consent.required.rights','true'::jsonb,'BOOLEAN','Customer confirms rights and permissions for submitted content'),
 ('consent.required.expiry','true'::jsonb,'BOOLEAN','Customer acknowledges the validity and expiry policy'),
 ('consent.required.publication','true'::jsonb,'BOOLEAN','Customer acknowledges that published content is public by link'),
 ('consent.required.terms','true'::jsonb,'BOOLEAN','Customer accepts the applicable Terms and Privacy Policy')
ON CONFLICT(setting_key) DO NOTHING;

INSERT INTO event_category(category_key,display_name,description,sort_order) VALUES
 ('WEDDING','Wedding','Engagements, ceremonies, receptions and wedding celebrations',10),
 ('BIRTHDAY','Birthday','Birthday celebrations for every age',20),
 ('ANNIVERSARY','Anniversary','Celebrate milestones and years together',30),
 ('FAMILY','Family celebration','Gatherings, reunions and family occasions',40),
 ('MILESTONE','Milestone','Graduations, achievements and personal milestones',50),
 ('OTHER','Other occasion','A flexible starting point for any event',99)
ON CONFLICT(category_key) DO NOTHING;

INSERT INTO invitation_template(category_id,template_key,display_name,description,status,current_version)
SELECT id, lower(category_key)||'-classic','Classic '||display_name,'A flexible, section-based design that can be adapted to your celebration','PUBLISHED',1
FROM event_category WHERE category_key IN ('WEDDING','BIRTHDAY','ANNIVERSARY','FAMILY','MILESTONE','OTHER')
ON CONFLICT(template_key) DO NOTHING;

INSERT INTO invitation_template_version(template_id,version_number,definition)
SELECT t.id,1,
 jsonb_build_object(
  'schemaVersion',1,
  'layout','editorial-classic',
  'sections',jsonb_build_array(
   jsonb_build_object('key','hero','label','Invitation','required',true,'enabled',true,'fields',jsonb_build_array('headline','hostNames','intro')),
   jsonb_build_object('key','events','label','Event schedule','required',false,'enabled',true,'repeatable',true,'fields',jsonb_build_array('eventName','date','time','venue','description')),
   jsonb_build_object('key','story','label','Our story','required',false,'enabled',false,'fields',jsonb_build_array('heading','body')),
   jsonb_build_object('key','gallery','label','Gallery','required',false,'enabled',false,'fields',jsonb_build_array('media')),
   jsonb_build_object('key','rsvp','label','RSVP','required',false,'enabled',false,'fields',jsonb_build_array('deadline','contact','message')),
   jsonb_build_object('key','footer','label','Additional information','required',false,'enabled',true,'fields',jsonb_build_array('note','contact'))
  ),
  'customSectionsAllowed',true,
  'locales',jsonb_build_array('en','hi')
 )
FROM invitation_template t WHERE t.current_version=1
ON CONFLICT(template_id,version_number) DO NOTHING;

CREATE TABLE IF NOT EXISTS email_outbox (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 recipient VARCHAR(320) NOT NULL,
 template_key VARCHAR(100) NOT NULL,
 payload JSONB NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','SENT','FAILED')),
 attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 sent_at TIMESTAMPTZ
);
CREATE INDEX ix_email_outbox_pending ON email_outbox(status,next_attempt_at);
CREATE TABLE IF NOT EXISTS auth_token (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id UUID NOT NULL REFERENCES app_user(id),
 token_hash VARCHAR(128) NOT NULL UNIQUE,
 token_type VARCHAR(30) NOT NULL CHECK(token_type IN ('EMAIL_VERIFY','PASSWORD_RESET','REFRESH')),
 expires_at TIMESTAMPTZ NOT NULL,
 consumed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_auth_token_user_type ON auth_token(user_id,token_type,expires_at);
