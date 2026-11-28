CREATE TABLE invitation_rsvp (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 invitation_id UUID NOT NULL REFERENCES invitation(id),
 publication_id UUID NOT NULL REFERENCES invitation_publication(id),
 guest_name VARCHAR(160) NOT NULL,
 guest_email VARCHAR(320),
 attendance VARCHAR(20) NOT NULL CHECK(attendance IN ('ATTENDING','NOT_ATTENDING','MAYBE')),
 guest_count INTEGER NOT NULL DEFAULT 1 CHECK(guest_count BETWEEN 0 AND 20),
 message VARCHAR(1000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_invitation_rsvp_publication ON invitation_rsvp(publication_id,created_at DESC);
