# Kelanjutan iSwara Music 0.9.0

Status penyerahan 3 November 2026: source dan build 0.9.0/versionCode 11 selesai. APK release 173592244 byte, SHA-256 f93812219660055a8153103f455877cffb4b405f3becf832471daf4d7dbee868. Sertifikat sama dengan release sebelumnya. Assemble release/debug, lint dan kompilasi instrumentation berhasil; hasil XML final gabungan 126 tes unik lulus, lint 0 error/fatal dan 128 warning. Metode penggabungan, batas dan bukti ada di VERIFICATION.md.

Tambahan 0.9: updater WorkManager dengan verifikasi APK dan panel rilis Admin; impor playlist publik serta CSV/JSON/M3U/TXT dengan review pencocokan; kurasi antrean dan Beranda berbasis riwayat/favorit; lebih dari 100 pilihan bahasa/kode; NetEase sebagai tambahan sumber lirik; delapan preset remix; tujuh palet dan tujuh gaya gerak lirik. Panduan PEMBARUAN_0.9.0.md.

Pemilik sudah menjalankan 202611030005_app_updates.sql dengan Success. Preflight produksi baca-saja membuktikan RPC tersedia dan tabel draft ditolak untuk anonim; pengujian SQL lokal lulus. Jangan meminta pemasangan migrasi ini lagi. Migrasi Radio 004 dan Listen Together sebelumnya juga telah berhasil menurut pemilik.

Hosting dipilih GitHub Releases, tetapi pemilik meminta panduan dahulu. Tidak ada APK yang diunggah atau rilis yang diterbitkan oleh agen. PEMBARUAN_APLIKASI.md berisi langkah lengkap. dist/iSwara-0.9.0-release.json adalah draft dengan URL kosong dan published=false, hash/ukuran sebenarnya. Versi <=0.8 perlu pemasangan manual sekali; 0.9 menemukan rilis berikutnya dengan versionCode lebih tinggi setelah pemilik menerbitkan metadata yang benar. Android tetap meminta konfirmasi pemasangan. Pertahankan keystore saat membuat release berikutnya.

Prioritas sesi berikutnya: uji unduhan/izin/notifikasi/update GitHub nyata pada Android, dengan data koleksi tetap tersimpan; playback lama dan latar/Bluetooth/audio focus; codec/karaoke dan M4A/MP4 native; dua akun/perangkat cloud, Radio dan Listen Together. ADB terakhir kosong dan emulator hypervisor belum terpasang (kode 6), jadi jangan mengklaim APK sudah dijalankan di Android. Jangan mengubah keamanan Windows untuk mempercepat emulator.

Tes dialog memakai ShadowDialog.getLatestDialog().window.decorView untuk snapshot jendela aktif, bukan decorView Activity di belakangnya. Snapshot updater/impor/kurasi telah dibuka dan ditinjau. Jangan memakai runBlocking untuk DataStore pada main thread Compose atau menambahkan lebar eksplisit pada runner Dialog TextField yang terkena Robolectric8460. Tes UI terakhir hanya mengubah helper/asersi, tidak mengubah APK setelah build release.

Pemulihan unduhan yang menunggu offline setelah proses aplikasi mati, instalasi aktual, bahasa langka dan playlist sangat besar masih menjadi prioritas QA. Kurasi bukan model AI audio, terjemahan bukan jaminan akurat untuk semua bahasa, dan endpoint nonresmi dapat berubah. Hindari janji lagu abadi atau bebas error.

Bagian di bawah adalah riwayat. Status dan keterbatasan versi lama dapat sudah digantikan oleh 0.9 atau laporan sebelumnya; gunakan VERIFICATION.md sebagai sumber status terbaru.

---
# Kelanjutan iSwara Music 0.8.0

Hasil final: assembleRelease/Debug, lintDebug, lintVitalRelease dan compileDebugAndroidTestKotlin berhasil. APK release 173525828 byte; SHA-256 8eb9f433778e6cb3d9e9b78a6d64fab75e177615e394cd71d446213c303db9a9. Sertifikat update sama. XML final gabungan berisi 114 tes unik lulus, 0 gagal/error/dilewati; lint 0 error dan 124 warning. Bukti final VERIFICATION.md.

RadioUiTest dipisah ke kelas/worker sendiri agar DataStore tidak menahan scheduler Compose dari tes sebelumnya. Jangan memakai runBlocking untuk menulis DataStore di thread utama runner Compose; hentikan worker tes yang tertahan secara terarah, jangan mematikan proses Java lain. Tes Radio tetap memeriksa guest, deep link tanpa autojoin, dan kelima tema. SmartLyricsUiTest memeriksa render lokal serta seek; AudioPipelineTest kini memasukkan AnalogAudioProcessor pada API26/34/36. Jalankan lint dan tes sebelum build release mahal pada pekerjaan berikutnya.

Paket 3-6 telah ditambahkan dalam source: ResilientMusicSource dan SourcePicker; RadioRepository/RadioScreen dan migrasi radio_lounge; SmartLyrics/SmartLyricsUi; AnalogAudioProcessor dan Studio. Navigasi utama Beranda, Cari, Radio, Koleksi, Ruang. Tema midnight/forest/dawn, enam aksen dan kontrol animasi global. Beranda memakai stable keys/tinggi judul tetap dan pembaruan progres mini-player dipisahkan. Panduan PEMBARUAN_0.8.0.md dan VERIFICATION.md memuat status bukti final.

Pemilik telah menjalankan 202611030004_radio_lounge.sql dengan Success. Preflight baca-saja memastikan discover_radio tersedia dan radio_state menolak anonim. Jangan meminta migrasi ini diulang. Tidak membuat room/pesan atau memodifikasi akun produksi sebagai QA. Uji lokal tools/test-radio.mjs mencakup RLS, verifikasi email, publik/privat, host/moderasi, anti-bypass ban, idempotensi dan rate limit.

Romanisasi lokal memakai Kuromoji/IPADIC dan ICU4J. Terjemahan memakai endpoint web Google Translate dengan persetujuan, Wi-Fi-only, cancel dan atribusi resmi; bukan MLKit dan bukan terjemahan offline. Pustaka MLKit hanya pernah diperiksa di .temp dan tidak dipakai aplikasi. Source cadangan mempertahankan identitas playlist, pencocokan ketat, retry terbatas dan pilihan manual per akun/perangkat. SoundCloud HTTP progresif saja; jangan menjanjikan semua format atau lagu abadi. Efek ruang headphone adalah crossfeed/pan, bukan HRTF/Dolby.

Yang masih memerlukan perangkat nyata: fokus audio/layar mati/Bluetooth; mutu suara analog; pemisahan/codec Android penuh; dua akun dan perangkat Radio/ListenTogether, reconnect dan moderasi. Host Windows belum punya driver hypervisor berfungsi, ADB kosong; jangan mengubah keamanan Windows atau mengklaim emulator berhasil. Build/JVM bukan bukti APK dijalankan fisik.

Crossfade dua player, timing lirik per kata, Spotify OAuth, pengenalan musik dan rich presence tetap belum aktif. Tidak ada tombol pura-pura untuk fitur itu. Akun admin milik pengguna sudah aktif; jangan suspend/delete akun nyata sebagai pengujian.

Bagian berikut merupakan catatan historis 0.7 dan sebelumnya, bukan status terbaru.

# Kelanjutan iSwara Music setelah 0.7.0

## Status 0.7.0

Permintaan terbaru: perluas menu dan fitur dengan penempatan rapi, lalu selesaikan APK. Navigasi sekarang Beranda, Cari, Jelajah, Koleksi, Ruang. Akun tetap tersedia dari Ruang/rail. Rail digunakan saat jendela minimal 600×480dp; mini-player tetap tampil dan halaman utama memakai SaveableStateHolder per pemilik.

SpaceScreen mempunyai direktori 18 entri (Admin hanya untuk akun berhak), kategori/pencarian dua bahasa, serta enam pintasan per akun/perangkat melalui DataStore. Jelajah berisi 19 pilihan genre/suasana/dekade menuju pencarian sungguhan. Home mendapat akses Jelajah dan Ruang kreatif.

Pembuat mix memberi pratinjau sebelum play, jumlah 10/15/20, filter durasi, batal/retry/timeout, keluarkan lagu, putar/acak, dan simpan playlist bernama. MixPreview terikat owner dan dibersihkan saat perubahan scope. LibraryRepository.createPlaylistWithTracks menyimpan satu dokumen owner eksplisit dengan deduplikasi, tanpa dua operasi create/update yang bisa terpencar antar-akun. Tidak ada migrasi baru.

OfflineScreen memberi hitungan dan ukuran sebenarnya, urutkan/cari, progres/batal unduhan aktif, play/shuffle hasil, pilih beberapa/semua hasil dan hapus massal berkonfirmasi. Musik perangkat langsung membuka Koleksi kategori Lokal. Studio lirik/antrean membuka tab PlayerSheet melalui initialTab. Pencarian artis menutup detail/section agar hasilnya terlihat.

Berkas UI baru: AppNavigation.kt, SpaceScreen.kt, ExploreScreen.kt, OfflineScreen.kt. Tambahan tes: SpaceRulesTest, NavigationSpaceUiTest dan satu LibraryRepositoryTest. Jumlah/status akhir ada di VERIFICATION.md dan verification/unit-0.7.0; pengulangan tidak dihitung sebagai tes baru. Dialog TextField diuji terpisah tanpa lebar eksplisit karena Robolectric #8460. Jangan mengubah UI produk atau menghapus uji dialog untuk menutupi kendala runner.

tools/verify-apk.py memeriksa versi/SDK, signature v2 dan sertifikat update, zipalign/ELF16KB, empat ABI, hash model, XML tes dan lint. Argumen --test-results dapat menunjuk direktori bukti yang digabung per kelas. Jalankan setelah build sebelum package-release.py. Panduan pengguna: PEMBARUAN_0.7.0.md.

## Prioritas setelah penyerahan

1. Pasang release 0.7.0 sebagai update tanpa uninstall. Uji menu dengan data nyata; Akun/Admin tetap dari Ruang. Tidak ada SQL baru.
2. Selesaikan uji Android penuh untuk codec/Keystore/worker karaoke AI, M4A/MP4, share/file picker, overlay, Bluetooth, fokus audio dan layar mati. Renderer/JVM serta kontrak intent bukan bukti integrasi native lengkap.
3. Uji dua akun/perangkat untuk cloud dan Listen Together. Jangan memakai akun sungguhan untuk aksi hapus/suspend sebagai QA.
4. Sumber cadangan, radio publik, romanisasi/terjemahan, analog/spatial, crossfade dua player, Spotify OAuth, ShazamKit dan rich presence belum dibangun. Rinci kebutuhan penyedia dan cakupan sebelum menambah kontrol; hindari tombol kosong atau janji musik abadi/AI sempurna.

Keystore, SDK, emulator dan batas pipeline 0.6.0 tetap berlaku. Bagian di bawah adalah catatan historis sesi sebelumnya, bukan status versi terbaru.

## Catatan teknis sesi 0.6.0

## Status terbaru

Pengguna meminta paket 1 dan 2 dahulu, lalu menambahkan kartu banyak baris, semua format ekspor (gambar/audio/video), serta pemutar utama tanpa scroll. Paket 3–6 menunggu instruksi sesi berikutnya. Tidak ada publikasi otomatis atau layanan berbayar.

0.6.0 mengimplementasikan Instant Remix (Slowed + Reverb, Nightcore), pitch terpisah ±12 semitone, hall/bass PCM, pemisahan AI Spleeter lokal, hasil vokal/instrumen terenkripsi dan seekable, serta notifikasi/progres/pembatalan. Sumber lokal/offline/streaming didukung melalui penyiapan audio, MediaExtractor/MediaCodec, resampling, STFT, dua model ONNX, rekonstruksi dan sambungan potongan. Model dibundel, bobot dipin dan identik dengan bobot asal; jendela diperkecil menjadi 128 frame. Tidak memakai stereo center cancellation sebagai AI.

Pemutar memakai susunan tetap dengan sampul yang menyesuaikan ruang, transport, timer/EQ/Studio/Mobil yang terlihat tanpa scroll. Kartu dapat memilih satu/banyak/semua baris, dibagi halaman otomatis. Studio ekspor mempunyai PNG, M4A/AAC, dan MP4/H264+AAC, pilihan awal dan 15/30/60 detik, serta lirik mengikuti timestamp atau teks diam. Koreksi waktu lirik ikut dipakai. Ekspor menggunakan audio asli, bukan efek/remix/hasil AI yang sedang diputar. Hasil lama tidak dianggap cocok setelah rentang/pilihan berubah; tombol hasil sebelumnya dapat dipakai untuk mengambil ekspor yang sudah selesai.

97 tes unik lulus: suite penuh 96 tes dengan jaringan nyata, kemudian tes UI/stream terarah setelah polesan pemutar dan pemilih lirik. Rincian metode dan batas final di VERIFICATION.md. Jangan menyebut tes komponen JVM sebagai uji APK pada perangkat. Encoder MP4/M4A penuh, pemisahan dengan MediaCodec+Android Keystore lengkap, dan mutu suara pada keluaran fisik belum diverifikasi. Pengguna akan menguji APK.

## Uji pertama yang perlu dilakukan pengguna/agen berikutnya

1. Pasang APK release 0.6.0 sebagai update tanpa uninstall. Cek VERIFICATION.md dan SHA256SUMS di dist. Kunci sama dengan versi sebelumnya.
2. Dengarkan sumber remote dan lokal dengan preset off/slowed/nightcore, nada -2/0/+2 semitone, seek/skip, headphone/Bluetooth, layar mati, fokus audio, dan beberapa durasi. Pastikan tidak terjadi FAILED_RUNTIME_CHECK, crackle, clip, pause tak diinginkan, atau pembekuan UI. Pipeline sintetis sudah diuji API26/34/36, tetapi belum speaker fisik.
3. Karaoke AI: lagu stereo dan mono, lokal dan streaming; proses/batal/background, memori rendah, ruang habis, pindah akun, hapus akun uji, proses ulang, ganti Asli/Vokal/Tanpa vokal saat jalan/jeda dan posisi di tengah. Periksa jumlah frame, sambungan chunk, hasil akhir, owner binding, fallback ke asli, serta penggunaan CPU/RAM nyata. Jangan menjanjikan instan atau 100% bersih.
4. Ekspor: pilih >4 baris dan Pilih semua, halaman PNG, simpan/bagikan satu dan semua halaman; potongan M4A/MP4 pada awal/tengah/akhir lagu; video bertimestamp/teks diam; koreksi lirik; proses/batal/background/pindah akun; encoder hardware/software dan penolakan izin notifikasi; cek file dengan pemutar nyata dan aplikasi penerima. Belum ada bukti MP4/M4A native diputar karena emulator tidak boot pada sesi sebelumnya.
5. Jika ekspor gagal, ambil pesan UI serta log terbatas proses sendiri. Jangan tampilkan token atau URL CDN. Jika encoder tidak menerima YUV flexible, periksa getInputImage/stride/pixelStride, ukuran buffer, color format, output-format change, packet sebelum muxer start, PTS/EOS, dan AAC priming. Jangan menandai output dari shadow codec sebagai bukti MP4 valid.
6. Emulator: AVD iswara_api36 tersedia, tetapi host Windows belum memiliki akselerasi yang berjalan. Default gagal karena driver hypervisor tidak terpasang; mode software tetap offline. Panduan EMULATOR.md. Tidak mengaktifkan fitur Windows atau mematikan pengamanan tanpa tindakan pemilik/restart yang sesuai.

## Berkas penting

- playback/RemixAudioProcessor.kt: aturan tempo/pitch dan hall PCM. Jangan menyalin EMPTY_BUFFER ke dirinya sendiri; guard drain kosong harus tetap ada.
- playback/StemDsp.kt, StemModel.kt, StemDecoder.kt: FFT/STFT, inference, resampling/decoder. CORE/CONTEXT/OVERLAP harus tetap selaras dengan FRAMES/HOP/FFT.
- data/StemStore.kt, StemCipher.kt, StemWorker.kt: satu mutex proses berat, owner/generation, AES-GCM blok, commit manifest, notifikasi, pembatalan/bersih temp. Model loading bukan di thread pemutar/UI.
- playback/StemDataSource.kt dan Playback.kt: pemutaran seekable serta pergantian varian dengan posisi/antrean dipertahankan. Room selalu timeline asli. Jangan mengirim file lokal/stem ke anggota room.
- data/LyricMediaStore.kt, playback/LyricMediaEncoder.kt dan LyricVideoPainter.kt: ekspor lokal AAC/H264, owner-bound cache, muxer, progres dan gambar. FileProvider hanya mencakup folder cache tertentu.
- ui/CompactPlayer.kt, SoundStudio.kt, LyricsCardStudio.kt: kontrol tanpa scroll pada halaman utama, setelan rinci dalam dialog, pemilihan/paginasi/preview/ekspor.
- tools/prepare-stem-models.py dan assets/stem-models/manifest.json: regenerasi model dengan onnx 1.23.2, SHA asal/hasil dan provenance. Model terlatih tidak berubah. Lisensi ada dalam aset legal.

## Fitur berikutnya yang belum diimplementasikan

Paket 3 multi-source fallback memerlukan adapter sumber dan pencocokan metadata nyata, tanpa klaim playlist abadi/lagu pasti tersedia. Paket 4 radio publik/komentar membutuhkan desain room/publikasi/moderasi dan migrasi server; Listen Together privat 0.5.0 tetap terpisah. Paket 5 romanisasi/terjemahan memerlukan penyedia/lisensi/config dan timing yang benar. Paket 6 efek analog/spatial memerlukan DSP serta uji audio keluaran. Jangan menambahkan tombol kosong atau menyebut efek tidak teruji sebagai audiophile/spatial sungguhan.

Crossfade dua player, timing per kata, Spotify OAuth, ShazamKit, rich presence, dan AI playlist berbasis layanan AI juga belum aktif. Pengguna belum meminta implementasi paket 3–6 pada sesi ini.

## Cloud dan fitur 0.5.0

Admin sudah aktif menurut pengguna; preflight menolak anonim pada membership/audit/summary/admin-users. Pengguna telah menjalankan migrasi Listen Together 202611020003 dengan Success. Tidak perlu mengulang deployment yang sudah berhasil. Dua sesi room, SMTP/pemulihan, sync dua HP, CRUD admin produksi dan penghapusan akun uji masih memerlukan uji sah. Jangan menangguhkan/menghapus akun sungguhan untuk QA atau mengekstrak cookie browser sebagai pengganti sesi yang tersedia.

## Build dan distribusi

C:/project/iSwaraMusic, Kotlin/Compose, Media3 1.10.1, SDK36/min26, JDK17 bytecode dan JDK21 Robolectric, Gradle9.4.1, ONNX Runtime Android 1.24.3. Semua ABI Android disertakan; ELF native sudah diperiksa alignment 16 KB. Tidak ada kondisi merek/model perangkat. Antislop during berdasarkan pilihan sesi; DESIGN.md dipertahankan. Tidak memakai subagent tanpa instruksi eksplisit.

Windows: bersihkan tanda kutip PATH hanya dalam proses, `$env:Path=$env:Path.Replace('"','')`. Gunakan `--no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8 -Djava.library.path=.'`. Suite jaringan memakai ISWARA_LIVE_TEST=1. Bila menambah konfigurasi dependency baru, --write-locks hanya tersimpan lengkap setelah tugas berhasil; verifikasi semua konfigurasi final terkunci. Jangan edit source saat build/lint/tes berjalan.

Keystore eksternal C:/Users/bentl/.iswara/iswara-release.jks, password di secrets.properties; jangan cetak atau sertakan dalam ZIP. SHA-256 sertifikat 6903592161d5a611c0748f4d9b38802b879053927e8c4b84ca7bd64f5c901dbe. Kemas dengan tools/package-release.py setelah laporan final sesuai versi, kemudian scan nilai kredensial aktual pada source ZIP. Bukti lama 0.4/0.5 disimpan terpisah dan tidak boleh disebut hasil perangkat 0.6.

