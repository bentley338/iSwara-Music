# iSwara Music 0.9.0

VersionCode 11 introduced in-app update checks, reviewed playlist imports, queue curation, expanded translation options and additional lyric/remix styles. See USER_GUIDE.md for the maintained English feature guide and PEMBARUAN_APLIKASI.md for release publishing.

Playlist import supports eligible public YouTube/Spotify/SoundCloud/Deezer lists and CSV/JSON/M3U/TXT input, with provider/page limits and manual match review. It does not collect private platform login cookies or promise every playlist is complete. Queue curation uses related metadata/history/favorites, avoids eligible duplicates and varies artists without rewriting a manual queue.

Translation offers more than 100 language choices and custom supported codes; Google Translate receives lyric text after consent. LRCLIB/KuGou/NetEase provide available lyrics. Original lyrics/manual selection remain available. Seven lyric palettes, seven motion styles and eight audio presets provide configurable appearance/processing without guaranteeing perfect translation or audio separation.

The release has 126 unique passing tests in the final per-class merged results, 0 lint errors/fatal and 128 warnings. The old verification report is preserved in verification/VERIFICATION-0.9.0.md. Physical Android installation/playback and two-device end-to-end behavior were not verified in that environment.

This APK contains the server-authorized native Admin UI, which is also retained in 0.9.1. Updating source documentation does not modify an installed 0.9.0 APK. Users through 0.8 need a manual update once; versions with the updater can discover higher compatible releases after publication/registration.
