# Verifikasi iSwara 0.9.0

Tanggal: 3 November 2026. VersionCode 11, minimum Android 8/API 26, compile/target SDK 36, Media3 1.10.1. Keystore dan dependensi terkunci dipertahankan. Panduan pengguna: PEMBARUAN_0.9.0.md. Panduan penerbitan: PEMBARUAN_APLIKASI.md.

## Build dan integritas

Build release/debug, lintDebug, lintVitalRelease dan compileDebugAndroidTestKotlin berhasil. Build pengemasan terakhir selesai dalam 6 menit 51 detik. Kompilasi instrumentation bukan pelaksanaan tes di Android. Perubahan terakhir sesudah build hanya memperbaiki pengambilan bukti pada tes UI dan dokumentasi, tanpa perubahan aplikasi.

APK universal: **173592244 byte**. SHA-256: **f93812219660055a8153103f455877cffb4b405f3becf832471daf4d7dbee868**.

Sertifikat SHA-256: 6903592161d5a611c0748f4d9b38802b879053927e8c4b84ca7bd64f5c901dbe. Signature v2 valid dan sertifikat cocok dengan release sebelumnya. Verifier memeriksa identitas/versi/SDK, zipalign 16 KB, seluruh 16 ELF pada empat ABI, hash/ukuran model ONNX, resource kamus/notice, izin installer, serta nama worker update yang dipertahankan pada mapping R8. Bukti: apk-integrity, signature, alignment, native-elf dan apk-0.9.0 dalam verification.

Lint: **0 error/fatal, 128 warning**. Warning tersisa dicatat dalam laporan xml/html/txt dan lint-0.9.0.json; bukan dinyatakan selesai.

## Tes otomatis

**126 tes unik lulus, 0 gagal/error/dilewati pada XML terakhir setiap kelas**, disimpan pada verification/unit-0.9.0. Suite penuh versi ini menjalankan 126 tes: 124 lulus dan dua gagal. Pengujian terarah setelah koreksi menggantikan XML kelas yang terkait. Pengulangan tidak dihitung sebagai tes baru; verifier menolak ID duplikat. Tidak menggunakan XML versi lama sebagai bukti versi ini.

Dua kegagalan awal berasal dari fixture lirik publik yang tidak tersedia sebagai rekaman artis yang tepat, serta kontrol preset yang berada di luar area terlihat setelah jumlah preset bertambah. Fixture diganti ke lagu publik yang benar-benar cocok, tanpa melonggarkan gerbang judul/artis/versi. Tes tablet menggulir daftar Studio dan memakai label Nightcore yang berlaku. Pengujian terarah beserta lint berhasil; pengambilan snapshot dialog diperbaiki secara terpisah agar menangkap jendela dialog aktif.

Tambahan cakupan versi ini:

- Validasi metadata rilis, URL GitHub/redirect yang diizinkan, checksum, batas ukuran dan penolakan APK tidak valid. Payload worker tetap di bawah batas WorkManager. Verifikasi APK release membandingkan sertifikat yang dipakai updater.
- CSV dengan kutipan, JSON, M3U dan teks; pencocokan judul/artis/versi/durasi; pembacaan playlist publik nyata, peninjauan dan penyimpanan playlist terpilih, pencegahan simpan ganda serta pembersihan saat akun berubah.
- Kurasi menghindari duplikasi/rekaman berbeda, memakai riwayat/favorit dan memberi variasi artis tanpa mengubah daftar masukan. Antrean otomatis membaca snapshot koleksi yang telah dimuat.
- Delapan preset remix dan kesesuaian timeline; kode/label bahasa, palet lirik dan kontras; terjemahan nyata ke bahasa berbeda dan beberapa batch dengan bahasa campuran; pencarian lirik NetEase dengan timestamp nyata.
- Entry point dan kondisi kosong updater/impor/kurasi, navigasi HP/tablet, serta regresi pemutar, Radio dan lirik pintar. Selector bahasa dicakup pengujian aturan bahasa; interaksi dialog selector belum diuji tersendiri.

Regresi parsing, antrean/shuffle/undo, backup, isolasi akun/konflik lokal, playlist, cache/range/seek/expiry, audio PCM pada API 26/34/36, model ONNX, overlay/widget, admin dan renderer ekspor tetap tercakup. Robolectric, sink audio sintetis dan JNI model Windows bukan bukti mutu audio fisik atau seluruh pipeline codec Android.

## Backend dan jaringan

Pemilik menyatakan migrasi 202611030005_app_updates.sql berhasil. Pemeriksaan produksi baca-saja membuktikan check_app_update tersedia dan tabel draft tidak dapat dibaca anonim. Tidak membuat atau menerbitkan rilis produksi. Bukti update-preflight-0.9.0.json.

Pengujian PGlite tools/test-updates.mjs lulus: migrasi berulang, admin aktif/terverifikasi, pembatasan RLS, metadata tidak aman, pembacaan publik hanya rilis terbit, filter versi/SDK/kanal, penarikan rilis dan penolakan admin yang ditangguhkan. Bukti updates-sql-0.9.0.txt. Radio/Listen Together mengikuti migrasi yang telah berhasil sebelumnya; pengujian SQL lokal bukan dua sesi Realtime di Android.

Pemutaran jaringan host menghasilkan resolve 1743 ms, resolve dari cache 0 ms, byte pertama termasuk resolve 1937 ms, dan 524288 byte terbaca dalam 321 ms sejak permintaan unduhan dimulai pada sumber 128 kbps. Bukti playback-network-0.9.0.json. Angka ini bukan startup suara APK, ukuran stutter atau perbandingan terkontrol dengan versi lama.

Endpoint publik untuk musik, playlist, lirik dan terjemahan dapat berubah/dibatasi. Impor tidak menjanjikan seluruh platform/playlist privat atau semua halaman selalu tersedia. Kurasi memakai aturan metadata, bukan klaim AI yang menganalisis audio. Terjemahan mesin dapat salah; teks asli, pemilihan bahasa dan perbaikan manual tetap tersedia.

## Visual, privasi dan distribusi

Bukti visual dialog updater, impor dan kurasi berada dalam docs/screenshots. Snapshot harus menangkap decorView dialog aktif; Activity di belakang dialog tidak dianggap bukti. Audit desain: verification/ANTI-SLOP-0.9.0.md. Privasi/lisensi dalam aset telah diperbarui untuk metadata update, GitHub, impor, NetEase dan cache terjemahan dalam memori per akun.

GitHub Releases dipilih pemilik; tahap ini hanya menyiapkan panduan dan metadata draft. Tidak mengunggah APK, membuat repository, menerbitkan rilis atau membeli layanan. dist/iSwara-0.9.0-release.json memuat hash/ukuran sebenarnya, URL kosong dan published=false. Pemilik mengisi URL APK nyata serta metadata Admin setelah publikasi.

Versi sampai 0.8 memerlukan pemasangan manual satu kali. Mulai 0.9, pembaruan berikutnya dapat ditemukan melalui aplikasi setelah rilis dengan versionCode lebih tinggi diterbitkan. Android tetap meminta konfirmasi pemasangan. Pengecekan/unduhan worker dapat ditunda sistem saat hemat daya.

## Batas pengujian dan tindak lanjut

ADB tidak melihat perangkat; emulator melaporkan kode 6 karena driver hypervisor belum terpasang. Tidak mengubah keamanan Windows. APK 0.9 belum dijalankan pada perangkat/emulator Android dalam sesi ini. Bukti android-device-0.9.0.json dan panduan EMULATOR.md.

Belum teruji langsung: notifikasi/perizinan installer, unduhan APK GitHub nyata dan upgrade dengan data tetap tersimpan; playback lama/layar mati/Bluetooth/fokus audio; mutu analog/remix; codec-Keystore-worker karaoke dan M4A/MP4 penuh; aplikasi penerima/overlay; dua perangkat cloud/Radio/Listen Together dengan reconnect dan moderasi. SMTP, pemulihan akun dan hapus akun uji belum dibuktikan end-to-end. Tidak ada klaim seluruh penerimaan produksi lulus atau bebas error.

Hasil akhir: APK release/debug, mapping, source ZIP, metadata draft dan SHA256SUMS dalam dist. Source mengecualikan konfigurasi privat, keystore, build/cache dan node_modules; audit nilai konfigurasi aktual serta CRC/isi arsip dijalankan sebelum penyerahan. Riwayat laporan sebelumnya: verification/VERIFICATION-0.8.0.md.
