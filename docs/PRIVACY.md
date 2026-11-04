# Privacy

The application includes no advertising or analytics SDKs. An operator must publish an appropriate operator contact and deployment-specific service/retention details before offering a public cloud service; no fictitious operator address is provided here.

## External music and lyrics

Search, artwork and playback connect to YouTube/YouTube Music and its media hosts; alternative-source lookup can use SoundCloud. Providers receive the network address and normal request metadata. Title/artist/duration and provider song identifiers can be sent to LRCLIB, KuGou and NetEase for lyric search/retrieval. Availability and each provider's policies apply.

Playlist import reads public metadata from supported Spotify, SoundCloud, Deezer or music-source pages, or user-selected CSV/JSON/M3U/TXT. Titles/artists are sent to music search for matching. The app does not collect platform login cookies. Previews are account-scoped in memory; selected results become library metadata. Local audio is not uploaded by import. Queue recommendations use related metadata/history/favorites rather than sending audio to an AI model.

## Library, accounts and storage

Favorites, playlists, listening/search history and preferences are local. Signed-in library sync sends playlist/favorite/listening-history metadata to the configured Supabase project. Search history, queue state, display settings and audio files are not uploaded by that sync.

Guest and account scopes are separate. Signing out keeps that account's local cache for a later login on the same installation, without exposing it in guest mode. Account deletion removes its cloud metadata and account-bound local collection/download/cache data. Uninstalling removes private storage and file grants.

Sessions use AES-GCM encryption backed by Android Keystore; account passwords are not stored by the application. Tokens/raw stream URLs are not intentionally logged. Android backups of private data are disabled. Exported JSON backups contain readable metadata and local-file references; save/share them deliberately. Imported playlists receive new IDs.

Saved queues, lyric cache, manual choices and imported LRC remain local per account. Automatic lyric cache is limited to 100 songs with a 30-day lifetime; manual choices persist until reset/account deletion. Listening recap stores local per-account daily summaries in Room for up to two years, can be cleared, and is not cloud-synced. Abrupt process termination can lose a few recent seconds.

Offline audio is AES-GCM encrypted per account in private storage, excluded from JSON/cloud sync, with keys in Android Keystore. Resolved stream URLs are temporarily cached in memory. Optional next-track preparation can fetch up to 128 KB into RAM for at most two prefixes with a two-minute lifetime. It clears on account/quality changes and is disabled in data-saving mode.

## Processing, translation and exports

Song recognition is optional. When you request it, the app sends a short sample from the microphone or the audio file you select, along with your AudD API token, directly to AudD for identification. The app does not save the token and deletes its temporary microphone recording after the request. AudD handles the request under its own policies.

Tempo/pitch/hall/bass, analog effects and cover-palette extraction run locally. Stem separation uses bundled Spleeter models without sending audio to an AI service. Unavailable source audio is fetched from the music provider. Stem results use encrypted account-scoped blocks. Plaintext working files stay in private cache and are cleaned after processing; leftovers older than 24 hours are cleaned on a later start.

Japanese/Korean/Chinese readings run locally with Kuromoji/ICU. Enabling integrated translation asks consent to send lyric text and language choices to Google Translate, including subsequent songs while active. The provider sees the network address. Wi-Fi restrictions/cancellation are available; cancellation cannot recall data already sent. Word meanings require an explicit action. Email, Supabase tokens and audio are not sent for translation. Cached results are in memory, limited to 24 song/language contexts and cleared on account changes. Browser translation actions can separately send selected text to Google Translate. Machine translation/readings can be wrong.

Lyric PNG/M4A/MP4 exports are produced locally; audio/video use original source audio. Private export cache older than 24 hours is cleaned on a later start. Account changes cancel work and deletion cleans account-bound exports. Android file/share selection grants temporary access through FileProvider. Copies saved/shared by the user are outside private-cache management.

## Rooms and administration

Listen Together stores queue metadata, position/play state, host ID and membership in Supabase. Authorized members can read room state; a code is an invitation. Each device fetches its own audio. Rooms last six hours, with expired-data cleanup according to migration retention rules. Leaving/changing accounts ends the device session.

Public Radio discovery exposes station/host display names, mood, member count and track metadata. Messages/reactions/membership details are restricted to authorized members/moderators. Host/admin moderation can remove messages or block members. Display history is bounded, while idempotent receipts remain until room cleanup. Avoid posting private information in a lounge.

Authorized server administrators can view account email/status, document counts and audits, and suspend/restore/delete accounts. User passwords/session tokens are not displayed in the Admin UI. The native Admin UI is restricted to server-authorized administrators; operators retain their privacy responsibilities. Exported audit CSV can contain user IDs and action details.

## Android integrations and updates

Local-media permission enables discovery; the system picker grants chosen-file access. Notifications/foreground media support playback; foreground data-sync work provides progress/cancellation for user-initiated processing/downloads. Floating lyrics require user-enabled overlay permission; position/preferences are local.

Trusted Android media hosts, including authorized car hosts, can read the current account's media catalog/control playback. Account changes refresh it, although hosts may keep their own display cache. Copy error details places app/Android version, device brand/model, error code and code locations on the clipboard; it excludes exception messages, stream URLs, track titles, email and tokens. No automatic error-report upload occurs.

Version checks send app version code, Android SDK and channel to public Supabase discovery. User-initiated APK downloads go to GitHub Releases, which receives normal network/request information. The app checks hash/package/signer before Android installation. Download files are private and excluded from collection backups. Notifications follow Android permissions. There is no silent installation or library upload for update checks.

External services operate independently: Google/YouTube/Translate, Supabase, GitHub, SoundCloud, Spotify, Deezer, LRCLIB, KuGou and NetEase. Consult their published privacy policies. These flows are also described in the application's existing Indonesian privacy notice; changing repository language does not change provider processing.
