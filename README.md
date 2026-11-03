# iSwara Music

An open-source Android music player with a local library, synchronized lyrics, audio tools, and optional cloud sync.

[Download the latest release](https://github.com/bentley338/iSwara-Music/releases/latest) · [User guide](docs/USER_GUIDE.md) · [Build guide](docs/BUILD.md) · [Security policy](SECURITY.md)

## Requirements

Android 8.0 or newer. The application targets Android 16 / API 36. Install the release APK from this repository's Releases page. To update an existing installation, use the same signing identity and install without uninstalling.

## Features

- Background playback, headset controls, editable queues, shuffle/repeat, sleep timer, saved queues, favorites, playlists, local audio, and encrypted offline files.
- Public radio rooms and private Listen Together rooms, with collaborative queues, comments, and reactions where supported by the configured backend.
- Timed lyrics, manual lyric selection and LRC import, local Japanese/Korean/Chinese readings, optional machine translation, and lyric image/audio/video exports.
- Audio presets, pitch controls, equalizer support, local stem separation, and optional analog effects. Processing time and output quality depend on the recording and device.
- Public playlist imports from supported providers, CSV/JSON/M3U/TXT imports, and reviewed track matching.
- Queue recommendations based on related tracks, listening history, and favorites; light/dark themes, cover colors, lyric palettes, and animation controls.
- Optional Supabase accounts and library sync. Direct APK updates use GitHub Releases and verify the downloaded package's hash, identity, version, and signing certificate.

Release and developer builds retain the full native administration interface. Only an active, verified account assigned an administrator role by the server is authorized to use it. Seeing or modifying client source does not grant backend permissions.

## Documentation

- [Documentation index](docs/README.md)
- [Using the application](docs/USER_GUIDE.md)
- [Building and signing](docs/BUILD.md)
- [Cloud deployment](docs/CLOUD_SETUP.md)
- [Server administration](docs/ADMIN_SUPABASE.md)
- [Publishing updates](docs/PEMBARUAN_APLIKASI.md)
- [Listen Together and Radio](docs/LISTEN_TOGETHER.md)
- [Privacy](docs/PRIVACY.md)
- [Verification and known limits](docs/VERIFICATION.md)

Maintained documentation is in English. Earlier Indonesian release notes and session records are retained as historical records; use the current guides for setup.

## Security and official builds

Never commit signing keystores, passwords, personal access tokens, database passwords, SMTP credentials, or Supabase server keys. A mobile application's public Supabase key can be extracted from its APK; RLS and authenticated server checks protect data. Forks must use their own backend and signing keys.

Download official builds from this repository's Releases page. A repackaged APK cannot update an official installation without the original private signing key. This does not prevent someone from publishing a separate fork under another identity. See [SECURITY.md](SECURITY.md) for reporting and operational safeguards.

## License

The project is distributed under [GNU GPL version 3](LICENSE), with dependency notices in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the application's license screen. Corresponding source is provided with each release. Copying, modification, and redistribution are permitted under the applicable license terms; open source is not a copy-prevention mechanism. Do not misrepresent a modified build as an official release.

## Limitations

Music and lyric availability depend on third-party services. Unofficial integrations can change or stop working; no permanent availability, universal provider support, lossless quality, or perfect translation is promised. Use content and exports in accordance with the rights and terms that apply to them.

Automated component tests do not replace Android device testing. Installer permissions, long-running playback, Bluetooth/audio focus, native media exports, and two-device cloud/radio behavior require device validation. The latest verification report records what was actually tested.
