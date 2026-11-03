# iSwara Music 0.5.0

Pasang APK sebagai pembaruan versi sebelumnya. Jangan uninstall agar koleksi, unduhan, dan sesi akun tetap tersimpan. Android minimum 8.0.

## Lirik dan kartu untuk Story

Animasi baris aktif diperbaiki agar tetap terlihat ketika Warna dari sampul aktif, termasuk sampul berwarna abu-abu. Animasi masuk dimulai saat baris muncul; perubahan palet tidak mengulang animasi. Mematikan Animasi lirik atau animasi sistem tetap dihormati.

Di Pemutar → Lirik, gunakan **Atur waktu** untuk memajukan atau memundurkan lirik per 0,5 detik, sampai 15 detik. Koreksi disimpan per lagu dan akun. Geser lirik untuk menghentikan mengikuti otomatis sementara, lalu tekan **Ikuti baris aktif** untuk kembali.

Tekan **Kartu lirik**, pilih 2–4 baris, lalu **Lanjut**. Lirik tersinkron dan teks biasa sama-sama bisa dipilih. Studio menyediakan Charcoal, Kertas, dan Sampul, dengan format Story 1080×1920 atau Post 1080×1350. Periksa pratinjau, kemudian **Bagikan** untuk membuka pemilih aplikasi Android, atau **Simpan** untuk memilih lokasi PNG. Jika tidak ada sampul, kartu memakai tanda S. Baris terlalu panjang ditolak agar teks tidak terpotong diam-diam. Tidak ada pengiriman otomatis ke media sosial.

## Widget dan lirik melayang

Setelan → **Koleksi widget** menampilkan tiga gaya: Minimal transparan, Retro Classic dengan layar inset dan kontrol retro, serta Vinyl dengan sampul di tengah piringan. Tekan Tambahkan dan konfirmasi melalui launcher. Jika launcher tidak mendukung permintaan pemasangan, tekan lama layar utama → Widget → iSwara.

Ketiganya mendukung sebelumnya, putar/jeda, berikutnya, dan membuka aplikasi melalui judul. Vinyl berputar ketika musik berjalan; Setelan → **Animasi vinyl** dapat mematikannya. Animasi dirender oleh launcher memakai ViewFlipper, tanpa mengirim pembaruan gambar setiap detik. Dukungan dan kelancaran animasi bergantung launcher. Animasi sistem yang dimatikan juga menghasilkan piringan diam.

Aktifkan **Lirik melayang** di Setelan dan berikan izin tampil di atas aplikasi lain. Kembali ke iSwara, lalu buka aplikasi lain saat musik berjalan. Geser area judul/lirik untuk memindahkan; tombol × menutup dan mematikan fitur. Ukuran dan kepekatan latar tersedia di Setelan. Posisi disimpan dan dibatasi tepi layar. Overlay mengikuti koreksi waktu lirik, tersembunyi saat musik dijeda, layar terkunci/mati, dan saat iSwara sedang terbuka. Lirik tanpa timestamp tidak dipaksakan menjadi karaoke.

## Denger bareng

Buka **Denger bareng** dari Beranda, masuk dengan akun terverifikasi, lalu buat room atau masukkan kode/tautan teman. Host mengendalikan lagu, posisi, jeda, dan antrean. Anggota dapat menambah lagu serta menjeda pemutaran di perangkatnya sendiri. Antrean pribadi disimpan dan dipulihkan dalam keadaan jeda ketika keluar.

Perubahan room dikirim melalui Supabase Realtime; pembacaan berkala dipakai saat koneksi realtime belum tersedia. Setiap perangkat mengambil audio sendiri. Sinkronisasi memperkirakan waktu server dan memperbaiki selisih posisi; jaringan dan penyiapan stream tetap memengaruhi keterlambatan. Room maksimal 32 orang, 200 lagu streaming, berlaku 6 jam. Berkas lokal tidak dibagikan. Panduan lengkap: [LISTEN_TOGETHER.md](LISTEN_TOGETHER.md).

## Antrean dan admin

Antrean sekarang bisa dicari berdasarkan judul dan artis. Mengurutkan ulang dinonaktifkan saat hasil pencarian tersaring agar posisi tidak tertukar. Penghapusan dan undo tetap tersedia sesuai peran room.

Panel admin menambah pencarian/filter konten, pratinjau sebelum terbit, duplikasi menjadi draf, konfirmasi terbit/hapus, dan perlindungan ketika konten sudah diubah pihak lain. Audit dapat difilter, dilihat detailnya, dan diekspor CSV melalui pemilih berkas Android. CSV menetralkan awalan rumus spreadsheet. Detail pengguna menyertakan UID dan status waktu akun. Izin admin tetap diperiksa server.

Hasil pengujian dan batas yang belum diverifikasi ada di [VERIFICATION.md](VERIFICATION.md).
