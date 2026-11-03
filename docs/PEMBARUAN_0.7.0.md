# iSwara Music 0.7.0

Pembaruan ini memusatkan penemuan musik dan alat mendengarkan dalam navigasi yang lebih mudah dicari. Pasang APK sebagai update agar akun, koleksi, dan data di perangkat tetap tersimpan.

## Menemukan menu

Di HP, ada lima tujuan utama:

- **Beranda**: lanjut dengarkan, rekomendasi, playlist, dan akses Jelajah serta Ruang kreatif.
- **Cari**: pencarian lagu, album, playlist, dan artis.
- **Jelajah**: genre, suasana, dekade, serta pintu masuk katalog. Pilihan seperti Pop Indonesia, Dangdut, Jazz, K-Pop, fokus, olahraga, dan perjalanan menjalankan pencarian sungguhan. Ini bukan tangga lagu atau rekomendasi editorial palsu.
- **Koleksi**: playlist, favorit, riwayat, musik lokal, dan unduhan.
- **Ruang**: direktori alat musik dengan pencarian dan kategori Dengarkan, Kreasikan, Bersama, dan Pribadi.

Pada jendela minimal 600 dp lebar dan 480 dp tinggi, navigasi menjadi panel samping. Akun dan Setelan juga dapat dibuka di panel itu. Mini-player tetap ada ketika berganti halaman. Halaman utama menyimpan keadaan antarmukanya saat berpindah tab.

Navigasi memakai komponen Material bawaan dengan susunan yang menyesuaikan ruang jendela, mengikuti [panduan navigasi adaptif Android](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation).

**Akun tidak dihapus.** Di HP, buka Ruang lalu ikon akun di kanan atas, atau pilih Akun & sinkronisasi. Panel admin hanya muncul jika sesi akun sekarang diizinkan oleh server; tidak ditentukan dengan mencocokkan email di aplikasi.

## Ruang dan pintasan

Ruang memuat Studio audio, Studio lirik, Equalizer, antrean sekarang/tersimpan, Pusat offline, musik perangkat, Denger Bareng, widget, rekap, mode berkendara, tautan, akun, pengaturan, ekspor/impor backup, serta Admin untuk akun yang berhak.

Gunakan **Cari fitur** untuk mencari nama maupun fungsi. Tombol tambah/semat di kanan baris mengatur pintasan di bagian atas. Maksimal enam pintasan per akun di perangkat ini; saat menambahkan yang ketujuh, pintasan paling awal digantikan. Tekan pin lagi untuk melepasnya. Pintasan ini tidak disinkronkan ke cloud.

Studio lirik dan antrean membuka tab pemutar yang sesuai. Bila belum ada lagu, aplikasi meminta memutar lagu dahulu. Akses fitur juga tetap tersedia dari pemutar dan Pengaturan.

## Pembuat mix dengan pratinjau

1. Buka Jelajah atau Ruang → Mix sesuai suasana.
2. Tulis suasana/genre, atau pilih saran.
3. Pilih 10, 15, atau 20 lagu dan durasi per lagu: bebas, kurang dari 4 menit, atau 4–10 menit.
4. Tekan **Susun mix**. Hasil belum langsung mengganti pemutaran.
5. Buang lagu yang kurang cocok, lalu putar berurutan, acak, atau simpan sebagai playlist bernama.

Jumlah hasil mengikuti lagu yang ditemukan dan lolos filter; bisa lebih sedikit dari jumlah yang dipilih. Mix memakai pencarian katalog, bukan model AI generatif. Pencarian memiliki batas waktu, pembatalan, kondisi kosong, dan retry. Hasil pratinjau dihapus saat akun berubah. Saat mengikuti room, pemutaran mix pribadi dinonaktifkan agar tidak mengganggu sesi bersama.

## Pusat offline

Buka Ruang → Pusat offline. Lihat jumlah lagu selesai unduh, ukuran audio sebenarnya, serta progres unduhan aktif. Pencarian dapat diurutkan berdasarkan terbaru, judul, atau ukuran terbesar. Putar semua hasil, acak, atau pilih beberapa lagu untuk diputar/dihapus.

**Hapus pilihan** meminta konfirmasi. Tindakan ini menghapus berkas offline, dengan playlist dan favorit tetap tersimpan. Unduhan aktif dapat dibatalkan. Musik lokal punya pintu masuk sendiri ke Koleksi → Lokal; gunakan Pindai musik atau Pilih file jika izin pustaka ditolak.

## Fitur dari 0.6.0

Instant Remix, pitch ±12 semitone, pemisahan vokal/instrumen lokal, kartu lirik banyak baris, PNG beberapa halaman, potongan M4A dan MP4 tetap disertakan. Panduan dan batasnya ada di [pembaruan 0.6.0](PEMBARUAN_0.6.0.md). Model AI dibundel sehingga APK universal sekitar 148 MB. Tidak ada migrasi Supabase baru untuk pembaruan navigasi ini.

Paket lanjutan seperti radio publik, pencocokan sumber cadangan, romanisasi, dan simulasi analog/spatial belum menjadi bagian versi ini. Tidak ada tombol untuk layanan yang belum tersedia.

## Pengujian

Hasil build, pengujian, signature, dan batas pengujian dicatat di [VERIFICATION.md](VERIFICATION.md). Uji komponen/UI di JVM tidak menggantikan pengujian seluruh codec, suara, share, dan pekerjaan latar belakang pada HP. Gunakan perangkat uji untuk mencoba ekspor M4A/MP4 dan karaoke AI penuh sebelum publikasi.
