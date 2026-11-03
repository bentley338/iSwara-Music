# Third-party software and assets

- NewPipeExtractor v0.26.5: GPL-3.0-or-later. https://github.com/TeamNewPipe/NewPipeExtractor
  Matching upstream source archive is included at `third_party/NewPipeExtractor-v0.26.5-source.zip` in the source distribution.
- AndroidX, Compose and Media3: Apache-2.0. https://android.googlesource.com/platform/frameworks/support/
- Dagger/Hilt: Apache-2.0. https://github.com/google/dagger
- Kotlin, kotlinx.coroutines and kotlinx.serialization: Apache-2.0. https://github.com/JetBrains/kotlin
- OkHttp and Okio: Apache-2.0. https://github.com/square/okhttp
- Coil: Apache-2.0. https://github.com/coil-kt/coil
- Jsoup: MIT. https://github.com/jhy/jsoup
- Rhino: MPL-2.0. https://github.com/mozilla/rhino
- NanoJSON: MIT. https://github.com/TeamNewPipe/nanojson
- Protocol Buffers: BSD-3-Clause. https://github.com/protocolbuffers/protobuf
- Plus Jakarta Sans: SIL Open Font License 1.1. Font source: https://github.com/google/fonts/tree/main/ofl/plusjakartasans ; license included in branding/FONT-LICENSE.txt.
- Gradle Wrapper: Apache-2.0. https://github.com/gradle/gradle

The iSwara logo and application UI are original project assets. Album/artist artwork is retrieved from source metadata and is not represented as original iSwara artwork. LRCLIB and KuGou supply available lyric text; lyrics are not fabricated or packaged as a local music catalog.

Exact resolved dependencies can be inspected with `gradlew.bat :app:dependencies`. Full application license is in LICENSE. Source distributions include build files and database migrations.

Lyric endpoints: https://lrclib.net and https://lyrics.kugou.com. These are external services; API availability does not confer ownership of song lyrics. User-imported LRC remains on the device.


- ONNX Runtime Android 1.24.3: MIT. https://github.com/microsoft/onnxruntime/tree/v1.24.3
- Spleeter 2-stems: MIT, Deezer. https://github.com/deezer/spleeter
  FP16 ONNX conversion by k2-fsa/sherpa-onnx (Apache-2.0); mobile windows preserve all learned parameters. Original SHA-256 and derived hashes are in assets/stem-models/manifest.json. Reproduction: tools/prepare-stem-models.py, onnx 1.23.2.
  Model origin mirror pinned to csukuangfj/sherpa-onnx-spleeter-2stems-fp16 commit 93ba771920ade509f8cbd6825b1a90856c797e08.
  Full licenses: legal/SPLEETER_LICENSE.txt, SHERPA_ONNX_LICENSE.txt, ONNXRUNTIME_LICENSE.txt, ONNXRUNTIME_THIRD_PARTY.txt.

## Additions in 0.8.0

Kuromoji 0.9.0 uses Apache-2.0; IPADIC includes its original notices. ICU4J 76.1 uses Unicode License V3 and related notices. Full copies are bundled in app/src/main/assets/legal/KUROMOJI_LICENSE.txt, KUROMOJI_IPADIC_NOTICE.txt and ICU_LICENSE.txt, and are available through the application's license screen. Shrinker rules retain the dictionary classes/resources needed for reflective access.

Google Translate is a separate online service, not a bundled SDK/model. Its official attribution badge is included unchanged. Translation remains subject to original content rights and provider terms. See https://docs.cloud.google.com/translate/attribution and https://policies.google.com/privacy. The web integration can change and is not a guarantee of official API service availability.

SoundCloud alternative sources use NewPipeExtractor GPL-3.0. Premium/preview restrictions, regional access and authentication are not bypassed. https://soundcloud.com/terms-of-use

## Additional services in 0.9

NetEase supplies available public lyric metadata/text through an unofficial integration. Imports read public Spotify embed, SoundCloud and Deezer metadata or user-selected exports without collecting account login cookies. Audio/lyric rights remain with their respective holders. GitHub Releases hosts owner-published APKs; GitHub credentials are not included in the application. Full notices are also bundled in the application's legal assets.
