# Verifikasi iSwara 0.5.0

Tanggal: 2 November 2026. versionCode 7, minSdk 26, compile/target 36, Media3 1.10.1, Room schema 3. Bytecode Android JDK17; Robolectric Android16 JDK21.

## Build dan APK

Build `assembleRelease`, `assembleDebug`, `lintDebug`, dan `compileDebugAndroidTestKotlin` berhasil dalam 5 menit 52 detik. APK release dioptimasi R8. Lint: 0 error, 0 fatal, 112 warning. Warning mencakup teks XML yang belum dipindah ke resource, saran KTX/versi dependensi, RTL, layout widget, dan accessibility touch listener; tidak disembunyikan dengan suppression. Saran target Android terbaru tidak diterapkan karena target SDK36 mengikuti spesifikasi proyek. Layanan media yang diekspor tetap memeriksa controller tepercaya/UID pada onGetSession dan onConnect; warning exported service bukan berarti controller arbitrer diterima.

Signature APK v2 lulus, satu penanda tangan RSA-3072, SHA-256 sertifikat sama dengan rilis sebelumnya: `6903592161d5a611c0748f4d9b38802b879053927e8c4b84ca7bd64f5c901dbe`. Zipalign dengan halaman 16 KB berhasil. Bukti: verification/signature-0.5.0.txt, alignment-0.5.0.txt, apk-0.5.0.txt, dan lint-0.5.0.xml/html/txt/json.

APK tidak dipasang pada HP pada sesi ini. Pengguna meminta memakai emulator atau pengujian lain. AVD Android16/API36 sudah tersedia, tetapi mode default gagal karena driver akselerasi tidak terpasang; dua variasi software tetap ADB offline dan tidak menyelesaikan boot. Tidak mengubah fitur Windows, driver, atau pengaturan keamanan. Bukti percobaan: verification/emulator-0.5.0.json. Panduan: EMULATOR.md. Hasil perangkat 0.4.0 diarsipkan terpisah dan bukan bukti eksekusi APK 0.5.0.

## Pengujian Android/JVM

85 pengujian unik lulus, 0 gagal, 0 error, 0 dilewati. Metode: suite penuh 84 tes dengan `ISWARA_LIVE_TEST=1`, lalu enam tes visual/overlay setelah perbaikan terakhir. XML terakhir per kelas digabung ke verification/unit-0.5.0; ringkasan verification/tests-0.5.0.json dan hasil suite penuh tests-0.5.0-full.json. Tes yang diulang tidak dihitung dua kali.

Cakupan meliputi parsing, backup, RLS/sinkronisasi lokal, antrean/undo/shuffle/radio, pemulihan sesi, isolasi akun, cache audio, byte-range/expiry/seek, serta pipeline PCM pada Android API26/34/36. Tes jaringan nyata mengambil sumber musik dan membaca audio kontinu; ini tidak mengukur speaker atau keseluruhan APK di perangkat.

Tes room memeriksa kode/tautan, validasi lagu remote, proyeksi jam server dan akhir lagu, serta pemulihan antrean pribadi termasuk posisi 42 detik, urutan, shuffle, repeat, dan kondisi jeda saat keluar pada API26/36. Source room menetapkan 1×/tanpa skip silence selama bersama, memisahkan niat putar dari keadaan buffering, dan melarang kontrol seek/next/ubah antrean anggota melalui callback session. Jalur kontrol notifikasi anggota masih memerlukan uji APK nyata.

## Perbaikan animasi dan lirik

Kegagalan animasi masuk direproduksi sebelum perubahan; bukti lama verification/lyrics-motion-before-0.4.1.xml. AnimatedLyricLine kini memakai Animatable yang dimulai dari awal, bukan langsung target akhir ketika komposisi pertama aktif. Penanda baris dan warna teks tidak bergantung pada aksen sampul. Pergantian palet tidak mereset animasi. Lima tes memeriksa palet sampul abu-abu, warna sampul mati, tema terang, gerakan mati, dan pergantian palet selama gerakan. Snapshot piksel memakai clock Compose terkontrol.

ListeningToolsTest memeriksa timestamp intro/duplikat, offset ±15 detik, seek, pembatasan angka, label, pencarian antrean, dan penyimpanan koreksi per pemilik. TabletPlayerUiTest pada teks 150% memeriksa pemilih lirik manual, koreksi 0,5 detik/reset, buka/tutup studio, pemilihan dua baris, perubahan gaya/format, dan pengosongan lirik berulang tanpa crash. LRCLIB + KuGou tetap tersedia; tidak menjanjikan setiap lagu memiliki lirik/timing kata.

## Kartu, widget, dan overlay

Renderer PNG diuji enam kombinasi gaya/format, termasuk empat baris panjang. Dimensi: 1080×1920 dan 1080×1350. Baris kosong/kurang dari dua ditolak. PNG dan snapshot studio dibuka untuk inspeksi visual; metadata, lirik, dan footer tidak saling menimpa pada fixture uji. Bukti card-*.png dan docs/screenshots/lyrics-card-studio.png. Fixture pengujian tidak dimasukkan sebagai katalog produksi.

Tiga RemoteViews dirender dengan judul dan kontrol. PNG widget-0/1/2.png diperiksa pada ukuran representatif. Tes memeriksa layout vinyl bergerak hanya saat playing, pilihan animasi mati, dan skala animasi Android nol. Rotasi menggunakan ViewFlipper pada launcher tanpa loop pembaruan bitmap per detik. Kelancaran dan dukungan launcher belum dibuktikan melalui launcher nyata.

FloatingLyricsTest memeriksa izin overlay ditolak/diberikan/dicabut, baris aktif dari cache manual, pause/resume, dan tombol tutup. Implementasi mengikuti koreksi waktu, membatasi posisi saat ukuran layar berubah, serta menyembunyikan overlay saat iSwara terbuka, terkunci, atau layar mati. Uji menyerupai komponen WindowManager Android melalui Robolectric; bukan bukti overlay di atas game/aplikasi lain pada HP.

Android Sharesheet, FileProvider pada sistem Android nyata, CreateDocument untuk PNG/CSV, dan permintaan pemasangan widget belum diuji penuh pada rilis ini. Tidak melonggarkan FileProvider atau menambahkan izin penyimpanan luas untuk melewati keterbatasan host Windows.

## Supabase, admin, dan Denger Bareng

Pengguna menyatakan panel admin telah berhasil aktif dan migrasi Listen Together berhasil dijalankan; screenshot memperlihatkan Success. Preflight terbaru membuktikan Auth terjangkau, email confirmation aktif, dokumen/RPC library menolak anonim, delete-account tersedia dan menolak anonim. Konten terbit admin dapat dibaca, sedangkan membership/audit/summary dan admin-users menolak anonim. Tabel/RPC room tersedia serta menolak anonim. Bukti cloud-preflight.json, admin-live-preflight.json, dan together-server-preflight.json.

Tiga pengujian PostgreSQL lokal/PGlite lulus. Library: ownership/RLS, mutasi idempoten, revisi, konflik/tombstone, cascade. Admin: UID terverifikasi, larangan self-promotion, isolasi draf/audit, pencarian pengguna khusus server, penangguhan dengan JWT yang masih berlaku, dan pemulihan. Room: migrasi berulang, join kode, RLS anggota/orang luar, wewenang host, URL kanonis, penolakan lokal, enqueue idempoten, konflik revisi, pencabutan akses setelah keluar, dan tutup. Bukti backend-library/admin/together-0.5.0.txt.

Tes AdminContentUiTest memeriksa callback pratinjau tanpa publikasi dan kontrol disabled; AdminToolsTest memeriksa duplikasi draf/filter serta CSV quote/penetralan awalan rumus. Tidak membuat/mengubah/menghapus pengguna produksi selama pengujian.

SMTP/pemulihan sungguhan, sinkron koleksi dua perangkat, dua sesi Listen Together terautentikasi, dan CRUD admin produksi belum diuji oleh agen. Socket Realtime Android beserta ketepatan sinkron audio dua perangkat belum diverifikasi langsung. Tidak menyebutnya sinkron sempurna atau siap rilis publik tanpa pengujian penerimaan. LISTEN_TOGETHER.md memuat langkah uji dua perangkat.

## Distribusi dan batas

Dist memuat APK release/debug, mapping, source GPL beserta migrasi/backend/aset dan panduan Indonesia, serta SHA256SUMS.txt. Rahasia, keystore, local.properties, .temp, log mentah, dan cache tooling dikecualikan. Pemeriksaan isi arsip dicatat pada verification/source-0.5.0.json setelah pengemasan. Tidak ada publikasi atau pembelian layanan.

Pemeriksaan antislop berada pada verification/ANTI-SLOP-0.5.0.md. Desain/renderer lokal diperiksa; gate integrasi menyeluruh R-35 belum lulus karena keterbatasan eksekusi emulator/perangkat. Paket disediakan sebagai build pengujian bertanda tangan sesuai permintaan lanjut build, bukan klaim penerimaan publik selesai. Pekerjaan selanjutnya: LANJUTKAN.md. Crossfade dua player, terjemahan dalam aplikasi, timing per kata, Spotify OAuth, ShazamKit, AI playlist, dan rich presence belum dinyatakan aktif.
