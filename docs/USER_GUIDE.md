# User guide

Install the release APK from the official GitHub Releases page. Android 8.0 or newer is required. Install updates without uninstalling to retain local data. The full native Admin panel is available only to authorized server administrators.

## Playback and navigation

Home shows published selections and listening-based recommendations. Search finds tracks and supported catalog pages. Radio provides public stations/private room entry. Library contains favorites, playlists, history, downloads and local music. Space groups audio, creative, social and personal tools; search features or pin shortcuts.

The mini-player remains available while browsing. Open it for artwork, controls, lyrics and the queue. Core player controls adapt to available screen space; tools open separate panels. Pause/resume, seek, previous/next, repeat, shuffle, sleep timer, editable/saved queues and supported undo actions are available.

Queue curator previews related tracks before adding them. Balanced/familiar/discovery modes use metadata, history and favorites; they do not analyze BPM or use a generative audio model. Existing manual queue order is retained.

Local music uses Android's media permission or file picker. If permission is denied, use the picker or grant access through Android Settings. A moved/deleted file may need to be selected again.

## Library, imports and cloud

Library changes save locally first. Signing in enables configured cloud sync for playlist/favorite/history metadata. Guest and account scopes remain separate. Music files are not uploaded by library sync. Use JSON backup export/import to move a collection deliberately.

Import supports public YouTube playlists, Spotify playlist metadata, SoundCloud and Deezer, subject to provider limits. Other platforms or private lists you own can use exported CSV/JSON/M3U/TXT or pasted Artist - Title lines. File input is limited to 2 MB, pasted text to 20,000 characters and imports to 500 tracks. Choose a smaller limit when needed.

Review matches before saving. Uncertain results are not automatically selected; compare artist/title/version/duration, edit the query or choose another candidate. Only selected tracks are saved. Import does not start playback. Account changes cancel and clear the preview.

## Lyrics and exports

Lyrics use LRCLIB, KuGou and NetEase when available. Search/select manually or import LRC if an automatic result is wrong. Timing can be adjusted per song. Original text remains visible; local readings support Japanese/Korean/Chinese where dictionaries/rules apply.

Optional translation sends lyric text to Google Translate after consent. Search source/target languages or use automatic source detection, and optionally restrict requests to Wi-Fi. More than 100 choices and supported custom codes are available; accuracy and availability depend on the provider. Translation is not offline.

Settings/Lyrics tools offer seven palettes and seven motion styles. Cover colors and animation are independent options; motion can be disabled and system motion preferences are respected.

Lyric cards accept multiple/all selected lines with automatic page splitting. Export PNG images, original-audio M4A segments or MP4 video. Video can follow timed lines or show static text. Saved/shared copies leave private cache management. Export content only when you have the necessary rights.

Floating lyrics require user-enabled overlay permission. Widgets offer Vinyl, Retro Classic and transparent/minimal styles; behavior depends on the Android launcher/system.

## Audio and rooms

Studio offers original playback, Slowed + Reverb, Nightcore, Speed Up Natural, Vocal Practice, Deep Voice, Dreamscape and Concert Hall. Separate pitch/EQ/bass and analog controls adjust supported processing. These presets do not turn a recording into an acoustic performance.

Local AI stem separation generates vocal/instrumental results using processing time, memory and storage. Complete vocal removal is not guaranteed. Results are encrypted per account. Quality settings select available source formats; they cannot create a higher bitrate/lossless stream absent from the source.

Listen Together/Radio require the configured backend and verified accounts for participation. Each device streams its own audio. Hosts control shared playback; eligible members can add tracks. Rooms use the original timeline rather than independent remix speeds. Network latency and source availability can differ; exact simultaneity is not guaranteed.

## Updates and troubleshooting

Settings -> App updates offers checks, channels, notifications, download/cancel and installation. Android asks for installer confirmation. Versions through 0.8 need one manual update; versions with the updater discover higher compatible releases.

For unavailable sources, retry or use manual source/lyric selection. For unexpected pauses, check audio focus, sleep timer and Android background restrictions. Copied error details intentionally exclude tokens/raw stream URLs; redact personal data before reporting.

Read VERIFICATION.md for actual coverage. Automated tests do not prove long background playback, native exports, overlays, installer behavior or two-device sessions on physical Android hardware.
