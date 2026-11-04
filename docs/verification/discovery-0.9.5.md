# iSwara Music 0.9.5 — discovery checks

The targeted Android JVM suite covers recommendation ordering and recording duplicates, artist rotation in shuffle, recording-aware lyric matching and timing, and recognition response handling. The APK verification reports in this folder record the exact test, lint and signing results for the built artifact.

The public LRCLIB search endpoint returned results with synchronized lyrics matching both title and artist for Tulus — Hati-Hati di Jalan, Hindia — Evaluasi, Queen — Bohemian Rhapsody, and The Weeknd — Blinding Lights. This probes the provider catalog, not playback or synchronization in the Android UI.

The Android 16 emulator did not complete boot on this host: hardware acceleration was unavailable and the software boot remained offline. Live microphone and audio-file recognition still need an Android runtime test with a working AudD API token.
