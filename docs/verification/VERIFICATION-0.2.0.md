# Laporan pengujian iSwara 0.2.0

Diperbarui pada 2 November 2026. Hasil dari komputer pengembangan dipisahkan dari pemeriksaan yang memerlukan HP dan akun cloud sungguhan.

## Build dan paket

LULUS: `lintDebug`, `testDebugUnitTest`, `assembleRelease`, `assembleDebug`, dan `compileDebugAndroidTestKotlin`. Build akhir selesai tanpa kegagalan. Lint: 0 error dan 43 peringatan, terutama pembaruan versi dependensi yang sengaja dikunci serta saran atribut/style Android. Tidak ada baseline untuk menyembunyikan error. APK release berukuran 3,812,074 byte.

APK release menggunakan application ID `id.iswara.music`, version code 2, min SDK 26, dan target SDK 36. Dependensi dikunci melalui Gradle lockfile. Kunci penandatanganan dan `secrets.properties` tidak disertakan dalam paket source.

LULUS: APK Signature Scheme v2 dengan satu kunci RSA-3072. SHA-256 sertifikat: `6903592161d5a611c0748f4d9b38802b879053927e8c4b84ca7bd64f5c901dbe`, sama dengan release 0.1.0. Pemeriksaan `zipalign -c -P 16 4` berhasil. Konfigurasi Supabase yang benar dan dokumen privasi Indonesia juga diperiksa langsung dalam APK. Bukti ada di `verification/signature-0.2.0.txt`, `verification/alignment-0.2.0.txt`, dan `verification/apk-0.2.0.txt`.

## Uji otomatis

29 uji lokal berhasil. Tiga uji jaringan dipilih secara khusus dalam proses terpisah dan semuanya berhasil; dalam suite lokal normal ketiga uji tersebut dilewati. Bukti suite lokal ada di `verification/unit-0.2.0/`, sedangkan hasil jaringan ada di dua XML live yang disebut di bawah.

Uji repository mencakup urutan playlist, objek playlist lama, ID baru saat impor, ekspor backup, penghapusan favorit bertanda, serta pemisahan koleksi tamu dan akun. Uji model mencakup backup tidak valid, batas ukuran UTF-8, URL berkas, antrean, dan parsing lirik.

Uji baru mencakup permintaan ekstraksi bersamaan, cache per kualitas, URL kedaluwarsa, fallback setelah penolakan stream, pemulihan setelah gagal, pembatalan persiapan lagu berikutnya, serta perhitungan kedaluwarsa sesi REST Supabase.

Migrasi SQL yang sebenarnya berhasil diuji di PostgreSQL PGlite melalui `node tools/test-database.mjs`: pembatasan pemilik data dengan RLS, penolakan akses anonim, penolakan penulisan langsung, revisi, konflik perubahan, operasi idempoten, penghapusan bertanda, dan cascade penghapusan pengguna. Ini pengujian database terisolasi, bukan bukti sinkronisasi dua HP pada server produksi.

## Uji sumber musik nyata

Tiga uji jaringan terpisah berhasil: pencarian lagu YouTube Music, ekstraksi URL audio, pembacaan data audio, pengambilan sampul, pembukaan album menjadi daftar lagu, dan pencarian kanal artis. Bukti: `verification/source-live-0.2.0.xml` dan `verification/transport-live-0.2.0.xml`.

Pengukuran satu lagu pada jaringan pengembangan:

- Ekstraksi URL: 1.544 milidetik.
- Pengambilan URL yang sudah tersimpan di cache: di bawah 1 milidetik.
- Byte pertama audio, termasuk ekstraksi URL: 1.696 milidetik.
- Data diterima: 524.288 byte dalam 268 milidetik setelah permintaan audio dimulai, dengan bitrate sumber 128 kbps.

Permintaan audio tanpa batas byte sebelumnya mengalami timeout saat pembacaan berlanjut; buktinya disimpan di `verification/source-unbounded-failure.xml`. Transport sekarang mengirim rentang 512 KB dan menyambungkan hasilnya secara berkelanjutan. Uji transport yang sama dengan pemutar berhasil menerima byte pertama dalam 1.650 milidetik, membaca 1.105.920 byte melewati tiga permintaan dalam total 2.115 milidetik, lalu membaca 32.768 byte setelah seek. Ukuran berkas sumber 3.918.515 byte.

Lima uji transport lokal memeriksa kontinuitas seluruh byte lintas potongan, posisi seek dan batas panjang, panjang stream dari Content-Range, perilaku host lain yang tidak diubah, serta penolakan EOF prematur agar lagu tidak terpotong diam-diam.

Pengukuran ini tidak mencakup decoder dan keluaran suara HP. Angka bukan janji waktu mulai untuk semua lagu, jaringan, negara, atau perubahan YouTube berikutnya. Buffer awal 750 milidetik adalah ambang pemutar, bukan total waktu dari menekan tombol sampai audio terdengar.

## Antarmuka dan aksesibilitas

Uji HP berhasil melewati navigasi, buat/ubah nama playlist, pemisahan Akun dan Setelan, tema terang/gelap, bahasa Indonesia/Inggris, toggle hening, pilihan kecepatan, dan dialog pembesaran sampul. Uji tablet 800 x 1024 dp dengan teks 150% berhasil memeriksa controller Media3, pengubahan urutan antrean, favorit, timer, dan pengosongan antrean.

Snapshot berada di `screenshots/`. Uji halaman album menggunakan metadata khusus pengujian tanpa lagu. Uji pemutar tablet menggunakan referensi berkas yang sengaja tidak tersedia untuk memeriksa kondisi gagal. Fixture tidak disertakan sebagai katalog di APK. Sampul serta metadata album nyata diverifikasi oleh uji jaringan sumber musik.

Saat uji ulang ditemukan area toggle yang terlalu terbatas dan pilihan chip yang belum terlihat saat simulasi sentuhan. Seluruh baris toggle sekarang dapat ditekan, pilihan kualitas/kecepatan membungkus ke baris berikutnya, dan uji perubahan nilai yang tersimpan berhasil.

Tema memakai token yang sama dengan versi sebelumnya. Seluruh 14 pasangan warna teks yang diperiksa memenuhi AA, antara 6,39:1 dan 16,35:1, menggunakan `python tools/check-contrast.py`. Kontrol memakai ikon Material, semantik Compose, dan area sentuh minimal 48 dp. Pemeriksaan TalkBack dan keyboard pada perangkat fisik masih diperlukan.

Pemeriksaan antislop pada kode dan layar yang diuji: identitas S dan palet iSwara dipertahankan; sampul memimpin halaman album; Setelan dipisahkan dari Akun; konten musik berasal dari sumber nyata; status kosong, memuat, dan gagal memiliki tindakan yang sesuai. Tidak ada lagu contoh yang disajikan sebagai katalog nyata. Seluruh baris toggle dapat ditekan dan menyampaikan keadaan aktif/nonaktif. Dials tetap ENERGY 2 / RHYTHM 2 / MOTION 2.

## Supabase

Konfigurasi URL salah ketik sudah diperbaiki agar cocok dengan proyek pada anon key. Pemeriksaan endpoint server berhasil untuk Auth, email/password, konfirmasi email, penolakan pembacaan library anonim, dan penolakan pemanggilan RPC sinkronisasi anonim. Bukti: `verification/cloud-preflight.json`.

Perhitungan kedaluwarsa sesi memakai `expires_in` dari respons REST bila `expires_at` tidak tersedia. Sinkronisasi memeriksa akun setelah respons server diterima dan menyaring pembacaan berdasarkan pemilik selain perlindungan RLS server.

Sesuai pilihan pengguna, fungsi `delete-account` disiapkan sebagai kode dan panduan deploy. Fungsi belum dipasang di server. Pendaftaran, pengiriman email SMTP, pemulihan password pada HP, penghapusan akun, serta sinkronisasi dua perangkat yang sudah login belum diuji. Anon key tidak dapat memasang fungsi server atau menggantikan akun pengguna yang sudah login.

## Batas pemeriksaan

Tidak ada perangkat Android tersambung. Emulator Android belum dapat dijalankan di lingkungan ini karena driver akselerasi Windows tidak tersedia; percobaan fallback sebelumnya tidak berhasil. Pengujian UI memakai Robolectric native graphics, bukan emulator Android penuh.

Keluaran suara dan kelancaran jangka panjang pada HP, layar mati, notifikasi lock screen, Bluetooth/headset, perpindahan Wi-Fi/data seluler, equalizer sistem, serta izin/pemilih berkas perlu diuji pada perangkat. Uji byte audio tidak membuktikan seluruh integrasi sistem tersebut.

Tidak dilakukan pembelian layanan, upload Play Store, atau publikasi otomatis. Panduan penggunaan, setup cloud, dan laporan ini memakai bahasa Indonesia. Lisensi asli dan keluaran alat pengujian tetap dipertahankan dalam bahasa aslinya.
