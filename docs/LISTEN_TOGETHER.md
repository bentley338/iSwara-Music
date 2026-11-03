# Listen Together and Radio

Listen Together uses private invitation rooms. Radio adds discoverable public stations, a lounge, reactions, and moderation. Each device resolves and plays its own audio; the host does not upload a continuous audio broadcast.

## Deployment

Apply the library/admin migrations first, then listen_together and radio_lounge in order. Existing successful migrations do not need to be repeated. Keep database roles, function grants, RLS, and Realtime publication setup from the migrations intact. See [Cloud setup](CLOUD_SETUP.md).

## Rooms and synchronization

Sign in with a verified account to create or join. Share a room code or deep link intentionally; codes are invitations. Opening a link presents the room flow rather than silently joining. Room lifetime is six hours; expired room data is cleaned up under the migration's retention rules.

Host playback/queue state is stored with timestamps and revisions. Clients use Realtime events plus state recovery/reconciliation. Network latency, device buffering, clock differences, and source availability mean exact sample-level simultaneity is not guaranteed. Rooms use the original playback timeline rather than individual remix speeds.

Members can add eligible streaming tracks to the shared queue. Host-authorized controls update shared playback. Local files are not uploaded to other devices. Account switching or leaving stops the device's room session.

## Public Radio

Public station discovery can expose station/host display names, mood, member count and current-track metadata. Lounge messages, reactions and membership details require room access. Hosts and authorized server moderators can remove messages or block members. Rate limits, tombstones and idempotent receipts protect state consistency; display history is bounded.

Platform Admin tools require the verified server-assigned role. Room-host controls remain available to legitimate hosts; normal room ownership does not confer platform administration privileges.

## Testing and privacy

Use two disposable accounts/devices to test create/join, play/pause/next/seek, concurrent additions, network loss, resuming, host departure, closure, moderation and expiration. SQL tests validate authorization/state behavior locally; they do not prove two physical Android devices stayed synchronized.

Do not post private information in a public lounge. External music services receive each listener's normal playback requests. See [Privacy](PRIVACY.md).
