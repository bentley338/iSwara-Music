# iSwara Music

Pembaruan terbaru: [panduan iSwara Music 0.9.0](docs/PEMBARUAN_0.9.0.md), dengan pembaruan aplikasi, impor playlist, kurasi antrean, perluasan terjemahan dan pilihan remix/lirik. [Panduan GitHub Releases](docs/PEMBARUAN_APLIKASI.md) menjelaskan penerbitan yang dilakukan pemilik.

Aplikasi musik Android native yang dibangun dari proyek kosong menggunakan Kotlin, Compose, Media3, Room, Hilt, DataStore, dan WorkManager. Mendukung Android 8.0 ke atas, dengan compile SDK dan target SDK 36.

## Membangun APK

Pasang JDK 17 untuk build Android, JDK 21 untuk pengujian Robolectric Android 16, Android SDK Platform 36, dan Build Tools 36.0.0. Gradle memakai toolchain yang sesuai; bytecode aplikasi tetap menargetkan Java 17. Atur variabel lingkungan `ANDROID_HOME`, atau buat berkas `local.properties` berisi `sdk.dir=C\:/lokasi/Android/Sdk`. Sesuaikan lokasi SDK dengan komputer yang digunakan.

Jalankan perintah berikut dari folder proyek:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

Pada komputer Windows ini, PATH berisi tanda kutip pada entri Cloudflare yang pernah mengganggu proses uji Java. Jika membangun dari lingkungan yang sama, bersihkan tanda kutip hanya untuk proses PowerShell saat ini dan gunakan daemon baru:

```powershell
$env:Path=$env:Path.Replace('"','')
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease --no-daemon
```

APK debug akan tersimpan di `app/build/outputs/apk/debug/app-debug.apk`. Untuk menjalankan pengujian pencarian dan audio YouTube dengan koneksi sungguhan, gunakan:

```powershell
$env:ISWARA_LIVE_TEST='1'
.\gradlew.bat :app:testDebugUnitTest --tests id.iswara.music.SourceLiveTest --rerun-tasks
```

Untuk menguji antarmuka pada perangkat, sambungkan HP Android atau jalankan emulator, lalu jalankan `:app:connectedDebugAndroidTest`. Pengujian jaringan bergantung pada koneksi, ketersediaan lagu di wilayah pengguna, dan format respons YouTube saat pengujian. Pengujian tersebut hanya dijalankan jika diaktifkan secara khusus.

Pengujian audio saat layar mati dapat dijalankan dengan `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.live=true`. Pengujian ini memutar audio sungguhan. Pengujian antarmuka di JVM menggunakan grafis native Robolectric, mencakup navigasi HP dan pemutar tablet dengan ukuran teks 150%. Pada Robolectric, pengujian dialog masukan teks perlu menghindari pengaturan lebar layar secara eksplisit karena [masalah pada Robolectric](https://github.com/robolectric/robolectric/issues/8460).

Untuk memeriksa basis data dan kontras tema:

```powershell
cd tools
npm.cmd ci
npm.cmd test
cd ..
python tools/check-contrast.py
```

Versi dependensi langsung sudah ditentukan dan berkas penguncian dependensi Gradle disertakan. Gunakan `--write-locks` saat memang ingin memperbarui versi yang dikunci. Distribusi Gradle diverifikasi melalui checksum SHA-256.

Setelah build release, tes, dan lint selesai, jalankan `python tools/verify-apk.py` untuk memeriksa APK, sertifikat update, model, empat ABI/ELF16KB, serta hasil tes/lint. Alat memakai Android Build Tools36.0.0. Gunakan hasil tes yang sesuai kode saat ini; jangan menyalin laporan versi lama sebagai bukti baru.

## Fitur

- Lima menu utama dengan navigasi bawah pada HP dan panel samping pada jendela lebar: Beranda, Cari, Jelajah, Koleksi, dan Ruang. Akun/Admin tetap tersedia melalui Ruang. Pencarian fitur, kategori, serta enam pintasan yang dapat diubah memudahkan akses alat musik.
- Jelajah genre/suasana/dekade memakai pencarian katalog nyata. Pembuat mix menyediakan pratinjau 10/15/20 lagu, filter durasi, penghapusan hasil, putar/acak, dan penyimpanan playlist sebelum mengganti pemutaran. Ini pencarian berbasis suasana, bukan AI generatif.
- Pusat offline menampilkan ukuran audio sebenarnya, pencarian, pengurutan, progres/batal unduhan aktif, putar/acak hasil, serta pemilihan dan hapus massal dengan konfirmasi.
- Pencarian lagu, album, dan playlist YouTube Music, serta pencarian kanal artis. Koleksi dapat dimuat bertahap; adapter InnerTube digunakan untuk membuka halaman yang membutuhkannya.
- Pemutaran di latar belakang, notifikasi MediaSession, kontrol headset, audio focus, penyuntingan antrean, radio otomatis, ulang, acak, timer menit/akhir lagu, dan pemulihan sesi. Preload awal lagu berikutnya memakai RAM dan dapat dimatikan.
- Favorit, playlist yang dapat disunting, riwayat dengar, penemuan musik dari pencarian nyata, serta lagu terkait dari sumber.
- Lirik per baris dari LRCLIB dan KuGou, pencarian/pilihan manual, impor LRC, animasi yang dapat dimatikan, kartu lirik PNG, serta overlay opsional. Equalizer, bass boost, dan reverb mengikuti kemampuan perangkat. Volume seimbang memakai pengolahan PCM, bukan metadata ReplayGain.
- Unduhan audio terenkripsi per akun dan rekap lokal hari/7 hari/bulan/tahun. Migrasi rekap lama ke Room mempertahankan data koleksi.
- Katalog Favorit/Playlist/Baru diputar melalui MediaLibraryService untuk host mobil tepercaya. Uji head unit Android Auto masih diperlukan; ini bukan distribusi Android Automotive OS.
- Pemindaian musik lokal dan pemilih berkas Android. Referensi berkas lokal menyertakan ID instalasi agar hasil sinkronisasi tidak memutar berkas lain yang kebetulan memiliki alamat serupa di perangkat berbeda.
- Tema gelap, terang, dan mengikuti sistem; antarmuka Indonesia/Inggris; pilihan kualitas streaming; serta mode hemat data.
- Ekspor/impor backup JSON berversi, penyimpanan terpisah per akun, Supabase Auth, serta sinkronisasi cloud berdasarkan revisi data.

## Mengaktifkan akun dan sinkronisasi cloud

Konfigurasi cloud bersifat opsional saat membangun APK. Jika belum dikonfigurasi, aplikasi menampilkan keterangan bahwa layanan akun belum tersedia. Fitur musik untuk tamu tetap dapat digunakan.

Ikuti [panduan Supabase dan SMTP](docs/CLOUD_SETUP.md). Salin `secrets.properties.example` menjadi `secrets.properties`, lalu isi `SUPABASE_URL` dan `SUPABASE_ANON_KEY`. Kunci service-role dan password SMTP hanya dikonfigurasi di server, bukan di aplikasi Android.

Data tamu dan data akun disimpan terpisah. Untuk memindahkan koleksi tamu ke akun, ekspor backup sebelum masuk, lalu impor backup setelah masuk. Penghapusan akun menghapus metadata di server dan koleksi lokal akun tersebut. Menghapus aplikasi dari perangkat juga menghapus pengaturan lokal dan izin akses berkasnya.

## Menandatangani APK release

Isi `ISWARA_KEYSTORE`, `ISWARA_STORE_PASSWORD`, `ISWARA_KEY_ALIAS`, dan `ISWARA_KEY_PASSWORD` melalui variabel lingkungan atau berkas `secrets.properties` yang dikecualikan dari Git. Setelah itu, jalankan `:app:assembleRelease`. Simpan cadangan keystore dan password secara terpisah. Pembaruan APK berikutnya harus memakai application ID dan kunci penandatanganan yang sama. Pembuatan kunci release lokal tidak mendaftarkan aplikasi ke Play Store.

Pada komputer pengembangan awal, kunci release dan konfigurasi penandatanganannya tersimpan di `%USERPROFILE%/.iswara/`. Keduanya dikecualikan dari arsip kode sumber. Password tidak disertakan dalam paket distribusi.

## Lisensi dan kode sumber

Lisensi aplikasi dan NewPipeExtractor adalah GPL-3.0-or-later. Aplikasi ini dikembangkan sebagai proyek orisinal. Saat membagikan APK, sertakan kode sumber yang sesuai dan petunjuk membangunnya. Rincian tersedia di LICENSE dan THIRD_PARTY_NOTICES.md.

Proyek ini memakai ekstraksi YouTube nonresmi. Ketersediaan stream dan kualitas audio tertinggi mengikuti sumber; unduhan tersedia untuk audio yang dapat diakses. Aplikasi tidak melewati pembatasan akun/usia dan tidak menjanjikan audio lossless. Rencana rilis Play Store memerlukan peninjauan distribusi serta kebijakan sumber secara terpisah.

Penanganan data dijelaskan dalam [panduan privasi](docs/PRIVACY.md). Hasil pemeriksaan yang sudah dijalankan tersedia di [laporan pengujian](docs/VERIFICATION.md).

## Tambahan 0.4.0

Beberapa antrean lokal menyimpan posisi dan urutan sendiri. Hapus lagu atau kosongkan antrean dapat diurungkan selama 30 detik. Palet pemutar diambil dari warna dominan sampul dan dijaga kontrasnya pada tema gelap/terang.

Panel admin memakai peran UID yang diverifikasi server, pengumuman/pilihan musik Beranda, statistik cloud, dan pengelolaan pengguna melalui Edge Function. Aktifkan melalui [panduan admin bahasa Indonesia](docs/ADMIN_SUPABASE.md). Migrasi admin dan fungsi `admin-users` belum dipasang di produksi dari sesi ini. Jangan memasukkan service-role key ke APK.

Pengujian backend admin lokal: `node tools/test-admin.mjs`. Pemeriksaan tipe Edge: `npx.cmd --yes deno@2.9.6 check supabase/functions/admin-users/index.ts supabase/functions/delete-account/index.ts`.

## Verifikasi Radio 0.8.0

Jalankan `node tools/test-radio.mjs` untuk menguji migrasi lokal, RLS, pesan idempoten, moderasi, dan kompatibilitas room privat lama. `python tools/check-radio.py` memeriksa endpoint produksi secara baca-saja memakai konfigurasi lokal; tidak membuat room atau pesan. Migrasi Radio sudah berhasil dijalankan pemilik pada sesi ini.
