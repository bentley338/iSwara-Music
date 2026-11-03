# Verifikasi iSwara 0.7.0

Tanggal: 3 November 2026. versionCode9; Android8/min26, compile/target36, Media3 1.10.1, ONNX Runtime Android 1.24.3, Room schema3. Tidak ada migrasi backend baru.

## Build dan APK

assembleRelease, assembleDebug, lintDebug, lintVitalRelease dan compileDebugAndroidTestKotlin berhasil. Build akhir: BUILD SUCCESSFUL in4m. Build sebelumnya menyelesaikan R8 dan suite penuh dalam11m6s tetapi gagal pada satu tes dialog dengan konfigurasi lebar eksplisit; penyesuaian hanya pada konfigurasi/selektor tes, tanpa mengubah kode produk setelah suite penuh.

Lint: 0 error/fatal, 121 warning, tanpa menonaktifkan pemeriksaan. Laporan lint-0.7.0.json/xml/txt/html disimpan di verification. Kompilasi instrumentation test berhasil; instrumentation tidak dijalankan di perangkat.

APK release universal: **147565842 byte** (sekitar148MB desimal), mencakup model AI dan empat ABI. SHA-256: **6f05e962e86be2b95d3ed83b7e59aa6870a066e05a45a3f80c38850f790e21e6**.

Signature v2, satu signer, RSA3072. Sertifikat cocok dengan APK sebelumnya: 6903592161d5a611c0748f4d9b38802b879053927e8c4b84ca7bd64f5c901dbe. APK bisa dipasang sebagai update tanpa uninstall. Zipalign halaman16KB lulus; semua16 library ELF di arm64-v8a/armeabi-v7a/x86/x86_64 memenuhi alignment minimal16384. Kedua model yang dibundel sesuai hash dan ukuran manifest. Bukti signature/alignment/apk/apk-integrity/native-elf-0.7.0 dihasilkan oleh tools/verify-apk.py.

## Tes otomatis

**105 tes unik lulus, 0 gagal/error/dilewati pada hasil akhir.** ISWARA_LIVE_TEST=1. Suite penuh104 tes menghasilkan103 lulus dan satu kendala runner pada dialog TextField. Setelah memisahkan uji dialog tanpa qualifier lebar eksplisit, kelas NavigationSpaceUiTest dengan4 tes lulus. XML terakhir per kelas digabung di verification/unit-0.7.0 dan dihitung tanpa duplikasi oleh verifier. Ringkasan tests-0.7.0.json. Ini bukan klaim bahwa105 tes dijalankan sekaligus dalam satu invocation.

Kendala dialog sesuai laporan [Robolectric #8460](https://github.com/robolectric/robolectric/issues/8460). Uji dialog tetap dijalankan; tidak dihapus, ditandai skip, atau diatasi dengan mengubah UI produk. Hasil tanpa qualifier lebar eksplisit lulus.

Tambahan cakupan:

- Navigasi lima tab di HP, rail di tablet, tema terang/gelap dan teks130%; hasil genre menuju pencarian yang benar, kategori dan pencarian fitur, keadaan kosong, pin lepas/pasang, serta jalur Akun/Setelan/Lokal.
- Buka/tutup Studio audio, EQ, antrean tersimpan, rekap, mode berkendara, widget, room dan offline. Studio lirik/antrean membuka tab pemutar yang diminta.
- Dialog tautan menolak masukan tidak valid tanpa crash. Ekspor/impor backup meluncurkan ACTION_CREATE_DOCUMENT/ACTION_OPEN_DOCUMENT; ini pemeriksaan kontrak intent, bukan uji seluruh pemilih berkas sistem.
- Pratinjau mix, keluarkan lagu, simpan playlist, serta pencarian mix sungguhan tanpa autoplay dan pembersihan hasil saat akun berubah. Penyimpanan playlist baru berupa satu dokumen, menolak owner yang berubah dan membuang duplikasi.
- Pin tidak dapat mengungkap menu Admin kepada pengguna tanpa hak, menangani ID usang/duplikat dan batas enam. Pencarian/urutan unduhan menggunakan metadata asli tanpa mengubah daftar sumber.

Regresi tetap lulus: parsing, backup, akun/konflik lokal, antrean/shuffle/undo/radio, cache/range/seek/expiry, audio background/library session pada komponen, lirik/warna sampul, overlay, widget, admin dan room. AudioPipelineTest menjalankan ExoPlayer/sink PCM pada API26/34/36, termasuk seek, tempo/pitch, skip silence, hall dan perpindahan lagu. StemModelTest menjalankan model ONNX melalui JNI Windows dengan contoh musik resmi. Ini tidak mengukur mutu suara HP atau menjamin seluruh pipeline native Android.

## Pemeriksaan visual

Snapshot aktual yang dibuka dan diperiksa: space-light-phone.png, space-dark-phone.png, space-light-tablet.png, space-dark-tablet.png, mix-preview-phone.png dan offline-dark-phone.png. Snapshot Jelajah tablet serta regresi pemutar/kartu juga tersedia di docs/screenshots. Fixture diberi judul pengujian; tidak dijadikan katalog aplikasi.

check-contrast.py lulus pada38 pasangan tema aktual; rasio minimum4,85:1. Tidak menambah warna teks hardcoded pada halaman baru. Kontrol inti pemutar360×640dp teks150% tetap lulus tanpa scroll. Catatan antislop: verification/ANTI-SLOP-0.7.0.md.

## Batas yang masih perlu diuji

APK0.7 belum dipasang/dijalankan pada HP atau emulator penuh oleh agen. Host emulator sebelumnya tidak bisa boot dengan akselerasi yang tersedia; tidak mengubah keamanan/driver Windows. Pengguna memilih menguji APK sendiri. Bukti perangkat0.4 tidak dipakai sebagai bukti0.7.

Validasi MediaCodec→ONNX→Keystore→worker→playback di Android penuh, mutu hasil vokal/instrumen, panas/RAM, keluaran AAC/H264/MP4 yang dapat diputar, ShareSheet/aplikasi penerima, pemilih berkas, dan lifecycle fisik tetap terbuka. AI tidak dijanjikan instan/100% bersih. Batas0.6 seperti8menit proses AI,60detik ekspor, serta audio ekspor asli tetap berlaku.

Cloud produksi tidak diubah. Pengguna sebelumnya mengonfirmasi Admin aktif dan migrasi room berhasil; sinkronisasi dua sesi nyata, email/SMTP, CRUD admin produksi dan penghapusan akun uji belum diverifikasi ulang pada0.7. Tidak ada akun sungguhan dihapus/dinonaktifkan untuk QA.

## Distribusi dan reproduksi

dist berisi APK release/debug, mapping R8, source ZIP dan SHA256SUMS.txt. Source dikemas melalui tools/package-release.py, tanpa secrets.properties/local.properties/keystore/build/cache. Audit input dan ZIP memeriksa nilai konfigurasi aktual tidak ikut, CRC, serta kesesuaian setiap berkas. Bukti source-0.7.0.json. Lisensi GPL aplikasi/extractor serta lisensi model/runtime tetap disertakan.

Panduan pengguna: PEMBARUAN_0.7.0.md. Untuk mengulang verifikasi setelah build/test/lint, gunakan `python tools/verify-apk.py --test-results docs/verification/unit-0.7.0` hanya jika direktori bukti tersebut sesuai sumber yang dibangun. Untuk tes baru lengkap, gunakan default direktori hasil Gradle. Tidak ada publikasi otomatis atau pembelian layanan.
