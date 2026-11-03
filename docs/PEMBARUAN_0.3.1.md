# iSwara 0.3.1

## Memasang pembaruan

Gunakan **dist/iSwara-0.3.1.apk**. Pasang sebagai pembaruan di atas release 0.3.0; tidak perlu menghapus aplikasi atau koleksi. APK memakai application ID dan kunci release yang sama. Jangan menimpa instalasi release dengan APK debug karena kunci penandatanganannya berbeda.

## Pemutar yang diperbaiki

Error **ERROR_CODE_FAILED_RUNTIME_CHECK** pada 0.3.0 berhasil direproduksi di pengujian Android 14 dan Android 16. Pemroses volume menerima buffer kosong saat Media3 menguras pipeline, lalu mencoba menyalin buffer tersebut ke dirinya sendiri. Keadaan ini menghentikan pemutaran meski Volume seimbang dimatikan. Versi ini menangani buffer kosong sebelum menyalin data.

Pengambilan ulang URL sekarang dikhususkan untuk akses stream kedaluwarsa/dibatasi. Gangguan koneksi mencoba sambung ulang secara terbatas; error audio mencoba inisialisasi ulang satu kali dengan posisi dan antrean dipertahankan. Error internal yang berulang tidak membuat aplikasi melewati banyak lagu. Efek perangkat baru dibuat ketika equalizer diaktifkan.

Jika pemutaran masih gagal, tekan **Salin detail error** di layar pemutar. Laporan memuat kode error dan lokasi kegagalan tanpa URL stream, judul lagu, email, password, atau token. Sertakan laporan itu saat melaporkan masalah.

## Mengurangi jeda

- **Setelan → Siapkan lagu berikutnya** menyiapkan URL dan 128 KB awal audio berikutnya di RAM. Awalan audio dapat dibaca tanpa menunggu koneksi CDN baru. Cache dibatasi dua lagu, berlaku dua menit, dan dibersihkan saat akun atau kualitas berubah.
- Mode hemat data mematikan persiapan ini. Lagu berikutnya yang sudah diunduh tidak dipersiapkan ulang dari jaringan.
- Daftar antrean tidak lagi diurai ulang dari JSON setiap setengah detik. Widget diperbarui ketika lagu/status putar berubah.

Kecepatan awal lagu tetap dipengaruhi koneksi, ketersediaan YouTube, dan kemampuan decoder HP. Angka benchmark komputer dalam laporan pengujian mengukur byte audio, bukan waktu keluaran suara pada HP. Tidak ada janji pemutaran tanpa delay di semua jaringan.

## Antrean dan timer

Antrean menampilkan urutan yang benar-benar diikuti pemutar, termasuk saat **Acak** aktif. **Putar berikutnya**, geser atas/bawah, dan **Hapus berikutnya** mengikuti urutan ini. Tambahan **Hapus yang lewat** membersihkan lagu sebelum lagu aktif. **Simpan playlist** menyimpan urutan yang sedang ditampilkan, dengan satu salinan per lagu.

**Setelan → Izinkan lagu berulang** mengatur apakah lagu yang sama boleh ditambahkan beberapa kali. Jika dimatikan, menambahkan lagu yang sudah ada memindahkannya; lagu aktif tidak diduplikasi.

Timer tidur mempunyai pilihan **Sampai akhir lagu ini**, selain 15/30/60/120 menit. Pemutar berhenti pada batas lagu melalui fitur Media3. Memilih lagu lain secara manual membatalkan timer akhir lagu. Antrean juga menampilkan perkiraan sisa durasi dari metadata yang tersedia.

## Rekap dengar

Pilih **Hari ini**, **7 hari**, **Bulan ini**, atau **Tahun ini**. Rekap lama dari 0.3.0 dipindahkan ke Room tanpa menghapus playlist/favorit. Pembaruan hanya menyentuh data lagu/hari yang berubah, dengan retensi hingga dua tahun. **Hapus rekap perangkat ini** hanya membersihkan statistik akun aktif pada perangkat ini; statistik dapat mulai terkumpul lagi saat mendengarkan.

Rekap tetap lokal dan tidak disinkronkan. Penghapusan akun membersihkan statistik pemilik dan menolak penulisan statistik tertunda untuk akun yang telah dihapus.

## Katalog untuk Android Auto

Layanan media kini menggunakan MediaLibraryService. Host tepercaya dapat menelusuri **Favorit**, **Playlist**, dan **Baru diputar**, kemudian meminta lagu berdasarkan ID koleksi akun aktif. Perintah MEDIA_PLAY_FROM_SEARCH dan searchQuery dari media session mencari lagu dari sumber. Berkas lokal milik instalasi lain tidak ditawarkan. Katalog diperbarui saat koleksi/akun berubah; controller yang tidak dipercaya Android ditolak.

Koneksi MediaBrowser, pembacaan katalog, resolusi lagu, urutan acak, dan pergantian akun diperiksa otomatis. Tampilan dan kontrol pada dashboard mobil belum diuji dengan head unit fisik atau Desktop Head Unit. APK sideload juga belum melalui proses distribusi/review Android Auto. Untuk pengujian lanjutan, ikuti [panduan Desktop Head Unit resmi](https://developer.android.com/training/cars/testing/dhu). Versi ini bukan paket terpisah untuk Android Automotive OS.

## Cloud dan pekerjaan berikutnya

Supabase Auth, RPC sinkronisasi, dan fungsi delete-account masih terjangkau; akses anonim ke data dan penghapusan akun ditolak. Setup yang sudah berhasil tidak perlu diulang. Pengiriman email SMTP, login sungguhan, hapus akun uji, dan sinkronisasi dua HP memerlukan pemeriksaan akun/perangkat yang belum tersedia di sesi ini. Panduan lengkap ada di **CLOUD_SETUP.md**.

Crossfade dua pemutar, Listen Together, terjemahan dalam aplikasi, timing lirik per kata, Spotify OAuth, pengenal lagu, Last.fm/Discord, dan animasi widget masih tercatat di **LANJUTKAN.md**. Integrasi yang membutuhkan layanan/token belum ditampilkan sebagai fitur aktif. Tidak ada pembelian atau publikasi otomatis.
