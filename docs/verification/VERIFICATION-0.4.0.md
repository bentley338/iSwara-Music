# Verifikasi iSwara 0.4.0

Tanggal: 2 November 2026. Kotlin/Compose/Media3 1.10.1, minSdk 26, compile/target SDK 36, Room schema 3. Bytecode Android memakai JDK 17; pengujian Android 16 memakai JDK 21.

## Hasil build dan pengujian

64 tes lulus, 0 gagal, 0 dilewati. Suite mencakup tiga tes jaringan sungguhan. Bukti XML: verification/unit-0.4.0/, ringkasan verification/tests-0.4.0.json. Build assembleRelease/assembleDebug, lintDebug, dan compileDebugAndroidTestKotlin berhasil. Lint: 0 error, 70 warning (termasuk konvensi RTL dan API/ikon deprecated); warning tidak disembunyikan. Laporan: verification/lint-0.4.0.txt dan HTML.

APK release diverifikasi v2, sertifikat RSA-3072 sama dengan versi sebelumnya, SHA-256 sertifikat 6903592161d5a611c0748f4d9b38802b879053927e8c4b84ca7bd64f5c901dbe. Zipalign 16 KB lolos. Bukti signature/alignment/apk-0.4.0. APK dipasang melalui adb install -r tanpa uninstall; hash APK terpasang sama dengan output build: verification/installed-apk-0.4.0.json.

## Perbaikan umum dan cakupan perangkat

Perubahan tidak memakai kondisi merek/model HP. Pipeline PCM ExoPlayer diuji pada Android 8/API26, Android 14/API34, dan Android 16/API36, termasuk buffer kosong, seek, perubahan kecepatan, skip silence, serta perpindahan item. Root FAILED_RUNTIME_CHECK pada 0.3.0 adalah EMPTY_BUFFER.put(EMPTY_BUFFER) ketika pipeline dikuras; guard buffer kosong diperbaiki pada 0.3.1 dan tetap ada. Bukti reproduksi lama: verification/audio-pipeline-before-0.3.1.xml.

Crash daftar lirik saat nilai state menjadi null ditangani dengan snapshot immutable per komposisi. Pola yang sama diterapkan ke layar Akun/Beranda/Koleksi/Setelan. TabletPlayerUiTest memunculkan dan mengosongkan lirik berulang. Snapshot stack sebelum perbaikan: verification/lyrics-crash-before-fix.txt. Kartu Lanjut Dengarkan membuka pemutar untuk lagu aktif; jeda memakai tombol tersendiri. Alasan audio focus/headset dapat ditampilkan saat audio dijeda oleh sistem.

## Uji release pada perangkat fisik

Perangkat uji Android 16/API36 tersambung lewat ADB. Cold start melalui pencarian musik, pemutaran native, pause/resume pada session aplikasi, layar mati 90 detik, next, dan jeda akhir berhasil. Posisi setelah layar mati mencapai 100981 ms. Tidak teramati buffering pada sampel lima detik selama uji layar mati. Proses aplikasi tetap sama, 0 fatal exception, 0 player error pada log yang dibatasi PID pengujian. Bukti: verification/device-0.4.0.json dan runtime-0.4.0.json. Musik ditinggalkan dalam keadaan jeda.

PLAYING pertama teramati 5294 ms setelah perintah mulai; next teramati 1203 ms. Ini satu pengamatan jaringan/perangkat dengan polling dan overhead perintah, bukan pengukuran suara speaker atau jaminan latensi untuk setiap lagu. Cache 128 KB awal lagu berikutnya berada di RAM, maksimal dua entri/dua menit. Mode hemat data mematikan preload. Tes byte memeriksa sambungan cache/CDN, seek, expiry, dan clear saat prefetch; retry dibedakan berdasarkan penyebab error.

Pengujian fisik merek lain, sesi berjam-jam, koneksi seluler tidak stabil, seluruh keluaran Bluetooth/headset, efek vendor, dan kondisi baterai ekstrem belum selesai. Status kompatibel Android 8 ke atas tidak berarti seluruh model sudah diuji fisik.

## Antrean dan penyimpanan

Tes ExoPlayer memeriksa urutan shuffle, play next, reorder, clear played/upcoming, duplikasi, radio, serta restore urutan. UI menguji simpan antrean bernama, hapus lagu lalu undo, kosongkan antrean lalu undo, serta urutan setelah pemulihan. ExperienceTest pada API26/36 membuktikan snapshot posisi/urutan bertahan saat repository dibuat ulang, akun terpisah, dan data tidak muncul kembali setelah penghapusan.

Migrasi Room 1 ke 3 mempertahankan documents dan rekap; schema 3 menambah saved_queues/lyrics_cache. Antrean dan lirik manual tetap lokal, belum masuk sinkronisasi/backup koleksi. Rekap lama tetap idempoten dan penambahan detik konkuren diuji.

## Lirik, tema, dan aksesibilitas

LRCLIB dan KuGou diperiksa melalui endpoint nyata. Probe menghasilkan lirik tersinkron 49 dan 44 baris tanpa menyimpan isi lirik dalam laporan: verification/lyrics-providers-0.4.0.json. Pemilihan otomatis menyaring artis, judul, versi, dan durasi. Tes memeriksa penolakan kecocokan salah, cache pilihan manual, impor timestamp LRC, dan isolasi akun. Pemilih lirik dapat dibuka melalui UI; impor melalui pemilih berkas Android masih memerlukan uji berkas nyata pada lebih banyak perangkat.

Warna sampul memakai kelompok warna dominan dengan pembatasan kontras. ExperienceTest memeriksa warna tombol pada berbagai hue dan dua tema. 38 pasangan token tema memenuhi kontras teks 4,5:1: verification/contrast-0.4.0.txt. Snapshot track-menu-dark.png dan track-menu-light.png diperiksa langsung; judul dan tindakan terbaca. UiFlowTest meliputi tema/bahasa dan layar kecil; tablet memakai font scale 150%. Animasi lirik finite, dapat dimatikan, dan memakai timestamp per baris, bukan kata.

## Cloud dan admin

Preflight cloud: Auth terjangkau, email confirmation aktif, library/RPC anonim ditolak, delete-account tersedia dan menolak anonim. PostgreSQL lokal/PGlite menguji RLS, ownership, konflik, operasi idempoten, tombstone, cascade, peran UID, pengguna belum terverifikasi, larangan menaikkan peran sendiri, isolasi draf, audit, server-only user search, penangguhan dengan JWT yang masih berlaku, larangan bypass sync, dan pemulihan akses. Deno 2.9.6 typecheck dua Edge Function lulus. Bukti: verification/backend-0.4.0.txt.

Panel Android dan kode SQL/Edge admin tersedia, tetapi tabel admin produksi masih HTTP 404. Migrasi baru, seed UID pemilik, dan deploy admin-users belum dilakukan setelah pengguna menghentikan kontrol dashboard lewat Escape. Tidak mengambil cookie atau token browser sebagai pengganti akses itu. Panduan pemasangan: ADMIN_SUPABASE.md. Tidak ada service-role key atau email allowlist admin di APK.

SMTP, login/pemulihan sungguhan, sinkronisasi dua perangkat, CRUD admin produksi, serta penangguhan/penghapusan akun uji belum diverifikasi. Layanan admin belum dinyatakan siap produksi publik. Akun pengguna sungguhan tidak dihapus atau ditangguhkan selama QA.

## Distribusi dan batas fitur

APK release/debug, mapping, source GPL, serta SHA256SUMS dikemas di dist. secrets.properties, local.properties, keystore, .temp, cache tooling, dan log mentah dikecualikan. Pemeriksaan desain tercatat di verification/ANTI-SLOP-0.4.0.md. Fitur lanjutan yang belum selesai ada di LANJUTKAN.md; tidak ada klaim bahwa crossfade dua player, Listen Together, Spotify OAuth, ShazamKit, atau rich presence sudah aktif.
