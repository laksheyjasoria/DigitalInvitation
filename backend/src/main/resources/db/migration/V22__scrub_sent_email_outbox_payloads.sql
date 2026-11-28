UPDATE email_outbox SET payload='{}'::jsonb WHERE status='SENT';
