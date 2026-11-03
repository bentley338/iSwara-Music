# iSwara 0.3.0

## Beranda dan musik berikutnya

Beranda menyediakan enam rak musik nyata, filter Untukmu/Indonesia/Global, lanjut dengarkan, pintasan playlist, dan enam suasana musik. Pilih **Bikin mix versimu**, tulis suasana, lalu aplikasi mencari hingga 15 lagu yang cocok. Ini pencarian musik berdasarkan prompt, bukan layanan AI berbayar.

Radio otomatis menambahkan lagu terkait ketika antrean hampir habis. Lagu yang sudah ada di antrean, baru diputar, berkas lokal, serta video terlalu panjang disaring. Antrean manual tetap berada sebelum hasil radio. Jika sumber terkait tidak tersedia, radio mencari berdasarkan artis. Gangguan sumber ditampilkan di tab Antrean dengan tombol mencoba lagi.

Tab **Antrean** membedakan lagu aktif, lagu berikutnya, dan hasil radio. Pengguna bisa memindahkan, menghapus, memutar berikutnya, menyimpan antrean sebagai playlist, serta menghapus seluruh lagu berikutnya. Menghapus bagian berikutnya juga menghentikan permintaan radio untuk antrean tersebut. Menambahkan lagu yang sudah ada memindahkan lagu itu; tidak membuat salinan kedua. Shuffle dan repeat memakai urutan Media3. Radio tidak menambahkan lagu baru selama repeat aktif.

Pilihan **Lewati lagu tidak tersedia** mencoba ulang stream satu kali, lalu beralih jika memungkinkan. Jumlah kegagalan berturut-turut dibatasi. Koneksi putus tidak menyebabkan aplikasi menghapus antrean.

## Audio dan Setelan

Setelan berisi kontrol singkat. Keterangan teknis dan lisensi panjang ditempatkan di dokumen atau dialog terpisah.

- Equalizer internal: Flat, Rock, Pop, EDM, Bass Boost, Vocal, serta lima kontrol manual. Nilai dipetakan ke band yang tersedia pada perangkat.
- Bass Boost dan reverb Room/Hall mengikuti kemampuan efek audio perangkat. Perangkat yang tidak mendukung menampilkan keterangan yang sesuai.
- Volume seimbang memakai pengolahan PCM 16-bit, perataan RMS, perubahan gain bertahap, dan pembatas puncak. Ini bukan pembacaan metadata ReplayGain atau pengukuran LUFS seluruh lagu.
- Kualitas rendah, sedang, dan tertinggi yang tersedia; mode hemat data; persiapan lagu berikutnya; lewati hening; kecepatan 0,75x sampai 2x.
- Warna pemutar mengambil warna sampul dengan penyesuaian kontras tombol.

Kualitas sumber menentukan bitrate sebenarnya. Aplikasi tidak menjanjikan high-res, 256/320 kbps, atau kualitas yang tidak diberikan YouTube. Transisi tetap memakai antrean Media3 dan persiapan URL; crossfade tumpang tindih dua pemutar belum diterapkan.

## Unduhan terenkripsi

Buka menu lagu, pilih **Unduh untuk offline**, lalu lihat hasil di **Koleksi > Unduhan**. Pekerjaan unduhan dijadwalkan dengan WorkManager ketika koneksi tersedia dan dapat dilanjutkan oleh penjadwal Android setelah aplikasi berpindah ke latar belakang. Unduhan yang gagal dapat dicoba kembali dengan memilih menu unduh.

Audio yang selesai diunduh disimpan dengan AES-GCM di direktori privat aplikasi. Kunci berada di Android Keystore. Pengguna yang berbeda memiliki indeks dan nama berkas terpisah. Lagu yang tersedia di perangkat diprioritaskan saat diputar, sehingga tidak perlu meminta stream YouTube saat offline. Ukuran maksimal 32 MB per lagu.

Penghapusan unduhan tersedia lewat menu lagu. Berkas audio tidak diunggah ke cloud dan tidak masuk backup JSON. Ekspor backup hanya menyimpan metadata. Menghapus data aplikasi atau mencopot aplikasi menghilangkan unduhan dan kunci lokalnya.

Unduhan lengkap memakai memori untuk dekripsi saat dibuka. Dukungan Android Keystore, penjadwalan latar belakang, dan pemutaran file terenkripsi masih perlu dibuktikan pada HP fisik; uji otomatis memeriksa algoritme enkripsi, integritas, serta pemisahan nama berkas per akun.

## Lirik dan berbagi

- Lirik LRCLIB mengikuti baris aktif. Menekan baris mengubah posisi putar.
- Tekan **Pilih lirik**, pilih 2 sampai 4 baris, lalu **Buat kartu**. Hasil berupa PNG 1080 x 1350 berisi sampul, judul, artis, dan lirik pilihan.
- **Terjemahkan di browser** membuka Google Translate dengan teks lirik. Terjemahan berdampingan di dalam pemutar belum tersedia.
- **Lirik melayang** dapat diaktifkan di Setelan setelah memberi izin tampil di atas aplikasi lain melalui Android. Aktifkan kembali toggle setelah izin diberikan. Jendela lirik dapat digeser dan disembunyikan ketika pemutaran dijeda.
- Tautan `iswara://track/VIDEO_ID` membuka lagu di iSwara. Tautan YouTube disertakan saat berbagi sebagai pilihan untuk penerima tanpa aplikasi.
- Playlist YouTube dapat dibuka lalu disimpan ke koleksi. Penyimpanan memuat halaman tambahan hingga 500 lagu. Playlist pribadi tidak diterbitkan secara publik.

LRCLIB memberikan timestamp per baris ketika tersedia. Aplikasi tidak mengarang timestamp per kata atau lirik yang tidak ditemukan. Kartu dibuat lokal; pembagian PNG melalui FileProvider perlu pemeriksaan pada perangkat Android nyata.

## Rekap, widget, dan berkendara

Rekap menyimpan hingga 5.000 entri harian/lagu terbaru. Rekap dengar dapat dibuka lewat ikon grafik pada Beranda atau melalui Setelan. Pilih bulan atau tahun berjalan untuk melihat menit mendengarkan, artis, dan lagu teratas. Waktu dihitung dari pemutaran yang berjalan pada perangkat ini, bukan durasi penuh setiap lagu. Data lokal dipisahkan per akun; riwayat lama sebelum 0.3.0 tidak diubah menjadi menit dengar palsu. Statistik genre belum tersedia karena sumber tidak memberikan metadata genre yang konsisten.

Tambahkan widget melalui pemilih widget launcher Android: **iSwara Minimal**, **iSwara Classic**, atau **iSwara Vinyl**. Widget menampilkan lagu dan kontrol sebelumnya/putar-jeda/berikutnya. Gaya Vinyl memakai gambar piringan statis; animasi putar terus-menerus belum diterapkan. Pengujian otomatis sudah memeriksa rendering tiga gaya, tetapi interaksi pada launcher dan perilaku saat proses aplikasi mati perlu pengujian HP.

Mode berkendara menyediakan tombol besar, favorit, dan layar tetap menyala selama mode dibuka. Ini tampilan dalam aplikasi, bukan integrasi Android Auto pada dashboard mobil.

## Akun dan cloud

Proyek menggunakan URL yang cocok dengan anon key pada konfigurasi lokal. Endpoint Auth dan sinkronisasi terjangkau, akses anonim ke koleksi ditolak, dan fungsi `delete-account` sudah terpasang serta menolak permintaan tanpa login. Login, SMTP, penghapusan akun terautentikasi, dan sinkronisasi dua HP belum diverifikasi dalam sesi ini.

Data koleksi tetap terpisah antara tamu dan akun. Unduhan serta rekap lokal tidak disinkronkan ke perangkat lain. Penghapusan akun membersihkan koleksi, unduhan, dan rekap milik akun tersebut pada perangkat.

## Fitur yang masih memerlukan pengerjaan

Catatan lanjutan ada di [LANJUTKAN.md](LANJUTKAN.md), termasuk:

- Crossfade dua pemutar dengan handoff MediaSession dan uji keluaran audio nyata.
- Karaoke per kata, terjemahan berdampingan, analisis ReplayGain/LUFS, dan statistik genre berbasis sumber yang valid.
- Listen Together: migrasi room/anggota/perintah, RLS, autentikasi, Supabase Realtime, dan pengujian dua perangkat.
- Impor Spotify dengan OAuth resmi. [Spotify mewajibkan otorisasi API](https://developer.spotify.com/documentation/web-api/concepts/api-calls).
- Pengenal lagu: SDK dan token layanan. [ShazamKit Android memerlukan Apple Developer token](https://developer.apple.com/shazamkit/android/).
- Integrasi akun Last.fm/Discord, Android Auto MediaLibraryService, dan animasi widget.

Tidak ada modul kugou, shazamkit, atau proto ListenTogether pada repo awal. Tidak dilakukan pembelian layanan, pembuatan akun pengembang, atau publikasi otomatis.
