# Verifikasi iSwara 0.8.0

Tanggal: 3 November 2026. versionCode10, Android8/min26, compile/target36, Media3 1.10.1, Room schema3. Tambahan terkunci: Kuromoji/IPADIC0.9.0 dan ICU4J76.1. Tidak memakai MLKit. Panduan: PEMBARUAN_0.8.0.md.

## Build dan APK

Build akhir berhasil dalam12m50s: assembleRelease, assembleDebug, lintDebug, lintVitalRelease dan compileDebugAndroidTestKotlin. Instrumentation tidak dijalankan pada perangkat. Duplikat CONTRIBUTORS.md sebelumnya diselesaikan dengan pickFirst setelah isinya dibandingkan dan identik; notice kamus dibundel lengkap. Dua pembacaan StateFlow di komposisi dipindahkan ke proyeksi ViewModel, tanpa suppression lint.

Lint: **0 error/fatal, 124 warning**. Peringatan tersisa tidak dianggap selesai. Laporan xml/html/txt dan ringkasan lint-0.8.0.json ada di verification.

APK universal: **173525828 byte**, sekitar174MB desimal. SHA-256: **8eb9f433778e6cb3d9e9b78a6d64fab75e177615e394cd71d446213c303db9a9**.

Signature v2, RSA3072, sertifikat cocok dengan release sebelumnya: 6903592161d5a611c0748f4d9b38802b879053927e8c4b84ca7bd64f5c901dbe. Dapat dipasang sebagai update atas release dengan kunci tersebut. tools/verify-apk.py berhasil memeriksa versi/SDK, signature, zipalign16KB, seluruh16 ELF pada empat ABI, hash/ukuran kedua model, serta resource kamus/transliterasi dan notice. Bukti signature/alignment/apk-integrity/native-elf-0.8.0 ada di verification.

## Tes dan cara menghitung

**114 tes unik lulus, 0 gagal/error/dilewati pada XML terakhir per kelas**, dalam verification/unit-0.8.0. ISWARA_LIVE_TEST=1. Ini hasil gabungan, bukan klaim114 tes lulus sekaligus dalam satu invocation.

Suite penuh114 menghasilkan113 lulus dan satu kegagalan menunggu DataStore ketika pemeriksaan tema Radio ditambahkan ke kelas Compose lain. Percobaan penulisan blocking juga menahan runner; worker uji dihentikan terarah. Radio dipisahkan ke kelas/worker sendiri agar penyimpanan singleton tidak menahan scheduler tes sebelumnya. Alur dan kelima tema tetap diuji, tidak dihapus atau dijadikan skip. Setelah perbaikan lint, tujuh tes navigasi, Radio, pemutar HP kecil dan tablet diulang dan lulus. XML terakhir menggantikan hasil lama per kelas; verifier menolak test ID duplikat. Tidak memakai hasil0.7 sebagai bukti0.8.

Tambahan cakupan:

- Sumber cadangan menolak perbedaan artis/judul/versi/durasi, mempertahankan dakuten Jepang, serta menolak host tiruan dan URL dengan userinfo.
- DSP analog off bit-exact, drain kosong aman, crossfeed menghasilkan energi kanal lawan, flush membuang audio sebelumnya, serta PCM mono/stereo/6/8 kanal terbatas. AudioPipelineTest menjalankan rantai remix-analog-normalisasi pada API26/34/36 dengan seek, tempo/pitch, bagian hening dan pergantian lagu.
- Kamus Jepang dan transliterasi Korea/Mandarin berjalan dengan data sebenarnya; offset token cocok dengan substring asli. Terjemahan nyata menguji deteksi bahasa, pemetaan beberapa baris, dan Jepang ke Indonesia.
- Radio guest menuju Akun, formulir gabung, deep link tanpa autojoin, lima tema dan navigasi HP/tablet. SmartLyricsUiTest merender cara baca/arti, menyembunyikan cara baca tanpa menghilangkan teks asli, serta memisahkan tombol seek.
- Penggabungan pesan tanpa duplikasi, tombstone, batas riwayat dan panjang input. Tiga puluh kombinasi tema/aksen mencakup240 pasangan teks/permukaan dengan kontras minimal4,5:1.

Regresi parsing, backup, akun/konflik/isolasi lokal, playlist, antrean/shuffle/undo, cache/range/seek/expiry, overlay, widget, admin, private Listen Together, kartu/renderer ekspor dan model ONNX tetap tercakup. JNI model Windows dan sink PCM Robolectric bukan bukti seluruh pipeline native Android atau mutu suara perangkat fisik.

## Backend dan jaringan

Pemilik menyatakan migrasi202611030004_radio_lounge.sql berhasil. Preflight baca-saja setelahnya memastikan discover_radio tersedia dan radio_state menolak anonim. Tidak membuat akun, room, pesan, reaksi atau tindakan moderasi pada produksi melalui alat pengujian. Bukti radio-preflight-0.8.0.json.

tools/test-radio.mjs lulus di PGlite: migrasi berulang, publik/privat, verifikasi email, RLS, host, idempotensi setelah riwayat dipangkas, rate limit, reaksi, soft-delete, ban dan penolakan bypass melalui join lama, host usang, serta kompatibilitas Listen Together privat lama. Bukti radio-sql-0.8.0.txt. Ini SQL lokal, bukan dua sesi Supabase Realtime produksi.

Tes audio jaringan host terakhir: resolve1741ms, cache0ms, byte pertama termasuk resolve1885ms, 524288 byte terbaca dalam262ms setelah permintaan download dimulai, sumber128kbps. Bukti playback-network-0.8.0.json. Angka ini bukan startup suara/stutter APK di HP atau perbandingan terkontrol dengan versi lama.

Pencarian/metadata/audio sumber utama diuji nyata. Pencocokan dan penjagaan sumber cadangan diuji, tetapi matriks pemutaran SoundCloud lintas lagu/perangkat belum diverifikasi. Adapter membatasi format ke HTTP progresif dan menolak sumber tidak tersedia. Tidak menjanjikan musik abadi atau semua format sumber.

## Visual, lisensi, privasi

Snapshot yang dibuka: radio-dark-phone, radio-dawn-phone, smart-lyrics-midnight, smart-lyrics-dawn serta snapshot navigasi/pemutar regresi. Forest juga dirender. Teks Jepang berlabel fixture pengujian. Baseline38 pasangan warna dan tes240 pasangan palet baru lulus. Catatan desain: verification/ANTI-SLOP-0.8.0.md.

Video referensi29,87detik ditinjau melalui cuplikan frame. Terlihat navigasi lama dan alasan jeda karena fokus audio dipakai aplikasi lain; tidak ada stack trace force close. Beranda kini memakai stable keys/tinggi kartu dan proyeksi state tanpa tick posisi. Belum membuktikan seluruh glitch video atau jeda fisik selesai.

Kuromoji/IPADIC dan ICU disertai lisensi/notice lengkap di aset dan menu lisensi. Google Translate memakai badge resmi tanpa perubahan, persetujuan pengiriman teks, Wi-Fi-only, cancellation dan penjelasan terjemahan mesin. Endpoint web tanpa API key dapat berubah/dibatasi. Romanisasi lokal. Pilihan sumber dipisahkan per akun/perangkat dan tidak mengganti identitas playlist.

## Batas dan penyerahan

ADB tidak melihat perangkat; emulator melaporkan kode6 karena driver hypervisor belum terpasang. Tidak mengubah keamanan Windows. APK0.8 belum dijalankan pada Android fisik/emulator. Bukti android-device-0.8.0.json dan panduan EMULATOR.md.

Masih perlu uji Android: playback lama/layar mati/Bluetooth/audio focus, mutu analog, decode-ONNX-Keystore-worker karaoke penuh, encoder M4A/MP4/aplikasi penerima, overlay, dan dua akun/perangkat Radio/Listen Together dengan antrean bersama, reconnect dan moderasi. SMTP, pemulihan akun, sync dua HP dan hapus akun uji belum dibuktikan end-to-end pada sesi ini. Tidak ada klaim bebas error atau seluruh penerimaan produksi lulus.

dist berisi APK release/debug, mapping, source dan SHA256SUMS. Source mengecualikan konfigurasi privat, keystore, build/cache dan node_modules; audit nilai konfigurasi aktual serta CRC/isi arsip dilakukan sebelum penyerahan. Tidak memublikasikan aplikasi atau membeli layanan. Riwayat0.7 ada di verification/VERIFICATION-0.7.0.md.
