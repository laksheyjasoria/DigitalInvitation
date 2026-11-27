-- Keep a single launch template in the customer catalogue.
-- Older sample designs are archived, not deleted, so historical invitations
-- that reference their immutable template versions continue to render.
UPDATE invitation_template
SET status = CASE
    WHEN template_key = 'wedding-floral-garden' THEN 'PUBLISHED'
    WHEN template_key IN (
        'wedding-royal-heritage',
        'wedding-minimal-modern',
        'wedding-sunset-romance',
        'birthday-confetti-pop',
        'birthday-golden-hour',
        'anniversary-ever-after',
        'anniversary-modern-milestone',
        'family-gathering-bloom',
        'milestone-achievement',
        'other-festival-lights',
        'other-clean-canvas'
    ) THEN 'ARCHIVED'
    ELSE status
END,
updated_at = now()
WHERE template_key IN (
    'wedding-floral-garden',
    'wedding-royal-heritage',
    'wedding-minimal-modern',
    'wedding-sunset-romance',
    'birthday-confetti-pop',
    'birthday-golden-hour',
    'anniversary-ever-after',
    'anniversary-modern-milestone',
    'family-gathering-bloom',
    'milestone-achievement',
    'other-festival-lights',
    'other-clean-canvas'
);
