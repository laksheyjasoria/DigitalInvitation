# Administrator access and invitations

## Roles

- **SUPER_ADMIN**: platform-wide authority, including creating administrator/support invitations, assigning roles, and changing privileged configuration.
- **ADMIN**: manages templates, publishes validated packages, and performs normal platform administration.
- **SUPPORT**: read-only access to the administration dashboard and Template Studio. All write requests are denied by the backend security filter, not only hidden in the UI.
- **CUSTOMER**: uses the customer invitation workspace only.

## Initial Super Admin account

On the first deployment, configure APP_BOOTSTRAP_ADMIN_EMAIL and APP_BOOTSTRAP_ADMIN_PASSWORD as server-side environment variables. The password must be 8-72 characters and include at least one letter, one number and one symbol. The bootstrap runner creates or repairs that account as ACTIVE SUPER_ADMIN and increments its auth version when it updates an existing account. Do not commit these values to the repository or put them in frontend configuration.

## Invite-only administrator onboarding

1. A Super Admin opens Administration and creates an invitation for an email address with role ADMIN or SUPPORT.
2. The admin-invite table stores a SHA-256 hash of a cryptographically random, single-use token. The invitation expires after 24 hours. When email delivery is enabled, the outbox temporarily holds the raw token for delivery and clears the payload after success or terminal failure.
3. If mail is enabled, an ADMIN_INVITE message is queued through the email outbox. The dashboard also displays the one-time link so it can be shared manually.
4. The invitee opens the link, chooses their own display name and password, and accepts the invitation.
5. The backend locks and validates the invitation, creates an ACTIVE account with the invited role, and consumes the token in the same database transaction.
6. Reuse, expiry, and email collisions are rejected.

The invite endpoint is Super Admin only. Invites cannot create another SUPER_ADMIN; elevated access must be assigned by an existing Super Admin through the protected role-management operation.

## Configuration

The email delivery path uses the existing SMTP outbox. Set APP_MAIL_ENABLED=true and configure the SMTP environment variables to deliver invitation emails. If email is disabled, copy the invitation URL from the Super Admin dashboard and share it privately. Never place invitation tokens in tickets, logs, or source control.
