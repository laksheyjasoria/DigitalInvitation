ALTER TABLE customer_order ADD COLUMN order_type VARCHAR(30) NOT NULL DEFAULT 'INVITATION';
ALTER TABLE customer_order ADD CONSTRAINT ck_customer_order_type CHECK(order_type IN ('INVITATION','EXTENSION'));
CREATE TABLE invitation_extension (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 invitation_id UUID NOT NULL REFERENCES invitation(id),
 order_id UUID NOT NULL UNIQUE REFERENCES customer_order(id),
 publication_id UUID NOT NULL REFERENCES invitation_publication(id),
 extension_days INTEGER NOT NULL CHECK(extension_days>0),
 previous_expiry TIMESTAMPTZ NOT NULL,
 new_expiry TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
