# Verifikasi iSwara 0.3.1

Tanggal: 2 November 2026. Kotlin/Compose/Media3 1.10.1, SDK 36, minSdk 26, Room schema 2. JDK 17 untuk build Android dan JDK 21 untuk tes Robolectric SDK 36.

## Hasil

54 tes lokal dan 3 tes jaringan berhasil (57 unik). Tiga tes jaringan sengaja dilewati dalam suite lokal dan dijalankan terpisah. Bukti: verification/unit-0.3.1/ dan verification/live-0.3.1/. Lint: 0 error, 70 warning. Build debug/release serta kompilasi AndroidTest berhasil. APK release v2 ditandatangani sertifikat RSA-3072 yang sama dengan versi 0.3.0; zipalign 16 KB lolos. Keystore, password, dan secrets.properties tidak masuk ZIP source.

## Penyebab playback gagal

VolumeBalancer menerima AudioProcessor.EMPTY_BUFFER ketika AudioProcessingPipeline menguras output sebelum PCM pertama. Saat fitur volume mati, kode lama memanggil EMPTY_BUFFER.put(EMPTY_BUFFER), sehingga IllegalArgumentException dipetakan Media3 menjadi FAILED_RUNTIME_CHECK. Reproduksi gagal pada SDK 34 dan 36 sebelum perbaikan: verification/audio-pipeline-before-0.3.1.xml. Sesudah menolak input kosong sebelum alokasi, kedua pengujian pipeline lulus, termasuk seek, kecepatan, skip silence, dan perpindahan item.

Log HP menemukan crash tambahan pada PlayerSheet saat daftar lirik lama membaca lyrics yang baru menjadi null. Stack hasil retrace: verification/lyrics-crash-before-fix.txt. Layar kini menangkap nilai state per komposisi, memakai daftar lirik nonnull, serta mereset pilihan lirik. Tes tablet mencakup pemunculan dan pengosongan lirik berulang.

## Perangkat Android nyata

Update release berhasil dipasang melalui adb install -r pada POCO X8 Pro Max, model 2602BPC18G, Android 16 / API 36. Tanpa uninstall atau penghapusan data. Backend USB baru Platform Tools 37.0.1 menyebabkan sambungan offline; ADB_USB_LEGACY=1 membuat sambungan native bekerja. Tidak mengubah driver, kunci ADB, atau setelan keamanan Windows.

Setelah build perbaikan lirik dipasang, uji cold-start melalui pencarian, audio native, kontrol play/pause pada session aplikasi, layar mati selama 12 detik, resume, dan next berhasil. Posisi pause pertama 10.576 ms; sesudah layar mati 23.125 ms; lagu berikutnya mencapai 5.887 ms. Pengamatan pertama PLAYING 5.580 ms sejak perintah mulai; pengamatan PLAYING pada next 1.177 ms. Ini pengukuran satu jaringan/perangkat dengan polling, bukan jaminan latensi semua lagu. Bukti: verification/device-0.3.1.json. Tes meninggalkan musik dijeda.

Percobaan QA sebelumnya menggunakan media key global dan dapat mengenai session lain; script kemudian memakai monitor dengan tag session iSwara yang unik. Jeda yang dikirim selama QA bukan bukti aplikasi menjeda sendiri. Pengujian Bluetooth/headset, semua efek perangkat, launcher widget, penghapusan proses di berbagai kondisi baterai, dan head unit Android Auto belum selesai.

## Performa dan antrean

Awalan audio berikutnya 128 KB di RAM, maksimal dua entri dan masa berlaku dua menit; mati pada mode hemat data. Tes byte membuktikan sambungan prefix/CDN tanpa kehilangan atau pengulangan, seek, panjang eksplisit, kedaluwarsa, dan clear saat prefetch. Uji jaringan membaca 1.105.920 byte dan seek 32.768 byte. Pembacaan awal cache 194 mikrodetik pada komputer; tidak mengukur suara speaker.

Tes antrean memakai ExoPlayer sungguhan dalam Robolectric: play next saat shuffle untuk lagu baru/lama, reorder, clear upcoming/played, duplikasi, posisi tetap, radio di ujung urutan, dan serialize/restore urutan shuffle. Pemrosesan ulang JSON antrean tidak lagi berjalan tiap 500 ms.

## Penyimpanan dan cloud

Migrasi Room 1?2 mempertahankan documents. Migrasi rekap lama idempoten, penambahan detik konkuren tidak hilang, pemilik tetap terpisah, dan statistik tertunda tidak muncul lagi sesudah akun dihapus. Pengujian PostgreSQL lokal memeriksa RLS, akses lintas akun, konflik, idempotensi, tombstone, dan cascade.

Preflight Supabase: Auth terjangkau, konfirmasi email aktif, pembacaan/RPC anonim ditolak, delete-account terpasang dan menolak anonim. Login sungguhan, SMTP, penghapusan akun uji terautentikasi, dan sinkronisasi dua HP belum diverifikasi. Tidak ada service-role key di Android.

## Desain dan batas

Arah DESIGN.md, ENERGY 2 / RHYTHM 2 / MOTION 2 dipertahankan. Snapshot HP/tablet serta kedua tema ada di screenshots/. Pemeriksaan kontras token lulus. FileProvider berbagi PNG perlu uji tambahan pada HP; uji Windows memeriksa PNG langsung karena separator path Robolectric berbeda. MediaLibraryService diuji dengan MediaBrowser, bukan head unit fisik.

Pekerjaan berikutnya mengikuti permintaan terbaru pengguna: branding iSwara Music, perbaikan kontras kedua tema, lirik beberapa sumber/animasi, antrean terpisah, pilihan/impor LRC, undo, serta panel admin dengan otorisasi server. Fitur tersebut bukan klaim selesai dalam APK 0.3.1. Lihat LANJUTKAN.md.
