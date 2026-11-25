# API Reference (v1)

Authenticated endpoints use Authorization: Bearer <access-token>. Responses use the common success/data/error/timestamp envelope.

## Authentication
- POST /api/v1/auth/register — create account and queue email verification.
- POST /api/v1/auth/verify-email — consume verification token.
- POST /api/v1/auth/login — issue short-lived access JWT.
- POST /api/v1/auth/forgot-password — request a reset email without account enumeration.
- POST /api/v1/auth/reset-password — consume one-time reset token.
- GET /api/v1/auth/me — current user.

## Public catalogue and policies
- GET /api/v1/catalog/categories
- GET /api/v1/catalog/categories/{categoryId}/templates
- GET /api/v1/catalog/templates/{templateId}
- GET /api/v1/public/config
- GET /api/v1/public/policies/current
- GET /api/v1/public/invitations/{slug}
- GET /api/v1/public/invitations/{slug}/qr
- GET /api/v1/public/media/{mediaId} (only public media associated with a live publication)

## Customer invitations
- POST /api/v1/invitations — create private draft.
- GET /api/v1/invitations — list only the authenticated owner's invitations.
- GET /api/v1/invitations/{id} — owner-scoped detail.
- PUT /api/v1/invitations/{id} — owner-scoped draft update.
- POST /api/v1/invitations/{id}/consents — record required declarations and policy versions.
- POST /api/v1/invitations/{id}/publish — publish after payment and consent checks.
- POST /api/v1/invitations/{id}/media — upload private image.
- GET /api/v1/invitations/{id}/media — list owner's media.
- GET /api/v1/invitations/{id}/media/{mediaId}/content — authenticated private stream.
- PATCH /api/v1/invitations/{id}/media/{mediaId}/visibility — set PRIVATE or PUBLIC.

## Payments
- POST /api/v1/invitations/{id}/orders — create order from configured price.
- POST /api/v1/payments/{orderId}/checkout — create provider checkout order.
- POST /api/v1/payments/{orderId}/verify — verify checkout signature and captured payment with provider.
- POST /api/v1/webhooks/razorpay — verify signed webhook events.

## Administration (ADMIN or SUPER_ADMIN)
- GET /api/v1/admin/settings
- PUT /api/v1/admin/settings/{key}
- POST /api/v1/admin/categories
- POST /api/v1/admin/templates
- POST /api/v1/admin/templates/{id}/versions
- PATCH /api/v1/admin/templates/{id}/status
- POST /api/v1/admin/policies/{TERMS|PRIVACY}

## Important behavior
- Public URLs use random 192-bit slugs, not database IDs.
- Public invitation access checks revocation and expiry on every request.
- QR images remain retrievable after expiry; the encoded URL then displays the expired page.
- Draft updates increment a content version. Consent must match the version being published.
- Expiry derives from the last function date plus configured calendar-day grace and timezone.
- Browser redirects are not payment evidence; the backend verifies provider signatures and captured payment details.
