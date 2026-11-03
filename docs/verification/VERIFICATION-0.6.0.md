# Verifikasi iSwara 0.6.0

Tanggal: 2 November 2026. versionCode 8; minSdk26, compile/target36, Media3 1.10.1, ONNX Runtime Android 1.24.3, Room schema3. Bytecode JDK17, pengujian Robolectric Android16 memakai JDK21.

## Hasil build

assembleRelease, assembleDebug, lintDebug, dan compileDebugAndroidTestKotlin berhasil dalam 7 menit 51 detik. Lint: 0 error/fatal, 121 warning; laporan lengkap verification/lint-0.6.0.*. Warning mencakup saran API/dependensi/KTX, teks resource, layout/RTL dan aksesibilitas listener; tidak disembunyikan. Instrumentation test hanya dikompilasi, tidak dijalankan di perangkat.

APK release universal berukuran 147533074 byte (sekitar 148 MB desimal) karena model dan empat ABI native dibundel. SHA-256 APK: 4abf19ffa49a1a8d80f8c8ec8b0c5d9406a21f3b88fa43e0b73767034749ad93. Model dalam APK sesuai checksum manifest. Signature v2, RSA3072, satu signer dan sertifikat sama dengan versi sebelumnya: 6903592161d5a611c0748f4d9b38802b879053927e8c4b84ca7bd64f5c901dbe. Zipalign halaman16KB lulus. ELF PT_LOAD seluruh library arm64/ARMv7/x86/x86_64 memiliki alignment16384. Bukti signature/alignment/apk/apk-integrity/native-elf-0.6.0.

Tidak memasang APK pada HP/emulator pada sesi ini; pengguna menyatakan akan menguji. Emulator host sebelumnya gagal boot karena akselerasi tidak tersedia dan fallback software tetap offline. Tidak menggunakan hasil perangkat0.4 sebagai bukti0.6. Panduan emulator tetap di EMULATOR.md.

## Pengujian otomatis

97 tes unik lulus, 0 gagal/error/dilewati. Suite penuh berisi96 tes dengan ISWARA_LIVE_TEST=1, kemudian tes UI/stream terarah setelah polesan pemutar; satu tes pemilihan lirik baru ditambahkan. Hasil terakhir per kelas digabung ke verification/unit-0.6.0. Ringkasan tests-0.6.0.json dan tests-0.6.0-full.json. Pengulangan tes tidak dihitung sebagai tes baru.

Suite tetap mencakup parsing, antrean/shuffle/undo/radio, backup, konflik/isolasi akun lokal, cache audio/range/seek/expiry, penyimpanan dan warna sampul, lirik, admin UI, serta aturan room/pemulihan antrean. Pipeline ExoPlayer PCM diuji API26/34/36 dengan hall, perubahan tempo/pitch, volume balancer, seek, skip silence, dan perpindahan lagu. Tidak teramati error pada pengujian tersebut; ini bukan uji kualitas speaker/Bluetooth.

RemixTest memeriksa parameter preset, pitch terpisah, pembatasan, timing asli saat room, bypass bit-exact saat mati, drain buffer kosong, tail reverb, batas sampel dan flush antar-audio. StemDspTest memeriksa round-trip FFT, rekonstruksi mask, resampling durasi/stereo. StemCipherTest memeriksa seek lintas blok, autentikasi pemilik, korupsi, dan pertukaran blok.

StemModelTest menjalankan kedua model ONNX asli melalui JNI Windows 1.24.3: bentuk stereo, nilai finite, silence, serta vokal/instrumen bukan nol pada contoh musik resmi yang diambil dari rilis sumber. Model/codec Android belum dijalankan penuh. Provenance memeriksa60 initializer per model tetap identik dengan bobot asal, hanya deklarasi jendela/intermediate shape dan urutan graph diadaptasi. SHA serta bukti real-music berada pada stem-provenance-0.6.0.json dan stem-model-0.6.0.json. Angka desktop di JSON bukan benchmark HP atau ukuran akurasi pemisahan. Cuplikan musik uji tidak disertakan source/APK.

## UI dan media

CompactPhonePlayerUiTest memakai layar360×640dp dan teks150%, membuka pemutar lalu memeriksa transport/timer/EQ/Studio/Mobil terlihat tanpa scroll. Dialog Studio dan timer dibuka/ditutup. TabletPlayerUiTest memeriksa tema, antrean/undo, timer/favorit, preset slowed/nightcore, perubahan/reset nada, lirik/koreksi waktu, kartu, pilihan format Audio/Video, serta toggle mengikuti audio. Header tutup Studio dipasang tetap agar tidak hilang ketika isi digulir.

LyricsSelectionUiTest memilih semua12 baris dan membuktikan urutan dikembalikan tanpa batas4. SharingWidgetTest memeriksa100 baris dibagi halaman tanpa teks hilang, PNG panjang/dua format, dan widget. LyricVideoPainterTest membandingkan piksel area lirik pada dua timestamp berbeda, terpisah dari area progressbar. Gambar pemutar kecil, studio kartu, halaman PNG, dan video-frame dibuka langsung untuk inspeksi.

Ekspor PNG, M4A/AAC, dan MP4/H264+AAC memiliki implementasi nyata: decoder/resampling, crop PCM, encoder, muxer, PTS/EOS, progres/batal, FileProvider dan pemilih berkas. Video dapat mengikuti timestamp per baris atau memakai teks diam. Potongan maksimal60detik; sumber mengikuti batas8menit. Audio ekspor adalah versi asli. Tidak ada unggahan atau pengiriman otomatis.

Batas penting: encoder/muxer Android penuh belum diuji pada perangkat, MP4/M4A native belum diputar/divalidasi end-to-end, dan penerimaan WhatsApp/Instagram/pemilih berkas belum dibuktikan. Uji renderer bukan pengganti verifikasi MP4 sah. Perangkat dapat menolak codec/YUV tertentu; aplikasi menampilkan error dan menghapus output parsial. Pengguna diminta menguji paket sesuai konteks permintaannya untuk menguji sendiri.

## Pemrosesan, data dan batas

Pemisahan memakai Spleeter2-stems fp16 lokal dengan jendela128frame, STFT dan output bertumpang tindih. Ini bukan center-channel cancellation dan tidak menjamin vokal100% hilang. Proses tidak instan pada semua perangkat. Batas:8menit, masukan64MB,350MB ruang kosong, penolakan low-RAM/RAM kosong<384MB. Model JNI desktop lulus; penyiapan MediaCodec, Android Keystore, worker background, beban RAM/CPU dan kualitas sambungan audio perlu uji HP lengkap.

Hasil stem memakai AES-GCM per blok, AAD pemilik/lagu/mode/generation dan header, commit manifest sesudah kedua berkas siap, seek bounded serta binding pemilik pada DataSource. Account switch membatalkan kerja dan melepaskan pilihan varian; penghapusan akun membersihkan data lokal terkait. Ekspor cache juga diikat akun dan diperiksa saat bagikan/simpan. Source/work plaintext dibersihkan selesai/gagal; sisa proses mati dibersihkan setelah24jam pada pembukaan berikutnya. Uji account switch/keystore penuh di Android masih diperlukan.

Fitur Supabase/admin/Listen Together dipertahankan tanpa migrasi baru. Bukti server0.5 ada dalam VERIFICATION-0.5.0.md. SMTP, CRUD admin produksi, sync dua HP, serta dua sesi room terautentikasi tetap belum diverifikasi oleh agen. Tidak melakukan tindakan admin produksi pada sesi ini.

## Distribusi

Dist memuat APK release/debug, mapping, source GPL/migrasi/aset/model/alat reproduksi/lisensi/panduan Indonesia dan SHA256SUMS. Source tidak menyertakan secrets.properties, local.properties, keystore, .temp, log mentah atau cache tooling. Pemeriksaan isi/fingerprint source setelah kemas dicatat pada source-0.6.0.json. Tidak ada pembelian layanan/publikasi.

Audit antislop: verification/ANTI-SLOP-0.6.0.md. Build ini diserahkan sebagai APK bertanda tangan untuk diuji pengguna; penerimaan native penuh belum selesai. Panduan PEMBARUAN_0.6.0.md dan catatan LANJUTKAN.md memuat langkah berikutnya.
