# iSwara Music 0.9.0

Panduan Bahasa Indonesia. Pasang APK release sebagai update tanpa uninstall, agar koleksi dan akun tetap tersimpan. VersionCode11, Android8 ke atas. Pemeriksaan perangkat fisik dan dua perangkat masih diperlukan; bukti otomatis ada di VERIFICATION.md.

## Pembaruan dari dalam aplikasi

Buka Pengaturan -> Pembaruan aplikasi, atau cari fitur Pembaruan aplikasi di Ruang. Tersedia cek manual, pemeriksaan harian, notifikasi, pilihan Stabil/Beta, progres unduhan, pembatalan, serta pemasangan APK yang diperiksa ukuran, SHA-256 dan sertifikatnya.

Tombol Unduh & pasang memulai alur. Android tetap meminta konfirmasi pemasangan dan dapat meminta izin pemasangan dari iSwara sekali pada awalnya. Bila aplikasi berada di background, gunakan notifikasi selesai untuk melanjutkan. Pemeriksaan otomatis tidak mengunduh APK tanpa tindakan pengguna.

SQL pembaruan sudah berhasil dijalankan pemilik dan endpoint publik sudah lolos pemeriksaan baca-saja. APK belum diunggah ke GitHub oleh agen, sesuai pilihan pemilik untuk menyiapkan panduan dahulu. Penerbitan di GitHub dan pendaftaran rilis tetap dilakukan pemilik. Lihat **PEMBARUAN_APLIKASI.md** untuk langkah lengkap. Metadata build disiapkan sebagai `dist/iSwara-0.9.0-release.json`; URL awal kosong dan published=false agar tidak menerbitkan tautan contoh.

Versi sampai0.8 belum mempunyai pengecek update, jadi perlu memasang APK baru satu kali secara manual. Pengumuman Beranda melalui Admin tetap dapat menjangkau versi lama saat Beranda dimuat. Setelah updater terpasang, versi berikutnya dapat ditemukan dari aplikasi. Versi0.9 tidak menawarkan dirinya sendiri sebagai pembaruan; rilis berikutnya harus memiliki versionCode lebih tinggi dan APK sungguhan dengan keystore yang sama.

## Pindahkan playlist

Buka Ruang -> Impor playlist, atau chip Impor playlist di Beranda.

Tautan publik yang didukung: playlist sumber musik iSwara/YouTube Music, Spotify, SoundCloud dan Deezer. Pembacaan halaman publik dapat dibatasi penyedia; iSwara menunjukkan batas tersebut. Daftar privat tidak dibuka tanpa izin atau melalui pengambilan cookie. Untuk platform lain, daftar privat milik sendiri, atau jumlah lagu yang belum lengkap, gunakan ekspor CSV/JSON/M3U/TXT atau tempel teks.

Format teks paling sederhana:

```
Nama artis - Judul lagu
Nama artis lain - Judul berikutnya
```

CSV menerima kolom title/track name dan artist/artist name(s), termasuk kutipan, koma dalam judul dan durasi milidetik. JSON menerima daftar lagu atau objek tracks/items/data dengan metadata judul-artis. M3U memakai metadata EXTINF; berkas audio lokal tidak diunggah. Backup JSON iSwara lengkap dipulihkan melalui Impor backup, bukan alat pencocokan playlist ini.

Batas berkas2MB, tempelan teks20.000karakter, dan maksimal500 lagu per impor. Pilih50/100/250/500 sesuai kebutuhan. Pencocokan berjalan dengan progres dan dapat dihentikan. Lagu yang belum meyakinkan tidak dipilih otomatis. Ketuk Periksa kecocokan untuk membandingkan kandidat atau mencari ulang dengan judul/artis yang diperbaiki. Pilih hasil yang benar sebelum menyimpan; versi cover/live/remix dan durasi yang berbeda tidak diterima otomatis sebagai rekaman asli.

Nama playlist dapat diubah sebelum Simpan pilihan. Hanya lagu terpilih yang disimpan sebagai satu playlist, tanpa duplikasi ID. Tidak otomatis memutar musik. Sinkronisasi mengikuti akun aktif dan layanan sinkronisasi yang sudah ada. Perubahan akun membatalkan proses dan menutup pratinjau lama. Playlist yang sudah tersimpan dapat dikelola dari Koleksi.

## Kurasi antrean

Buka Kurasi antrean dari tab Antrean pemutar, Ruang, atau Beranda. Putar satu lagu sebagai titik awal, pilih Seimbang/Lebih akrab/Temukan baru, lalu Susun rekomendasi.

Peringkat memakai hasil lagu terkait, riwayat dan favorit lokal. Pemilihan menghindari lagu yang sudah ada, rekaman duplikat, lagu terlalu panjang, serta pengulangan artis ketika alternatif tersedia. Ini alat rekomendasi berbasis aturan dan metadata, bukan klaim model AI yang mendengarkan atau mengetahui BPM/genre yang tidak diberikan sumber.

Periksa daftar, pilih beberapa atau semua hasil, lalu Tambahkan di akhir atau Putar berikutnya. Urutan manual yang sudah ada tetap dipertahankan. Antrean otomatis menggunakan logika yang sama untuk rekomendasi berikutnya; pilihan rekomendasi tidak menyusun ulang lagu yang sudah masuk. Aksi personal dibatasi saat berada di room supaya tidak mengubah antrean bersama tanpa konteks.

Beranda memanfaatkan kurasi berbasis riwayat untuk bagian rekomendasi personal dan menyediakan akses cepat ke alat antrean serta impor. Tidak menampilkan katalog atau statistik promosi palsu.

## Terjemahan dan lirik

Lirik pintar sekarang menyediakan pencarian bahasa asal dan tujuan, deteksi otomatis, lebih dari100 pilihan bahasa, serta kode bahasa khusus untuk bahasa yang didukung layanan. Pilih Terjemahkan ke untuk mengganti bahasa tujuan. Teks asli tetap tampil; cara baca Jepang/Korea/Mandarin tetap berjalan lokal.

Terjemahan memerlukan persetujuan pengiriman teks ke Google Translate dan mengikuti pilihan Wi-Fi. Baris dikirim berkelompok untuk menjaga konteks; hasil yang sudah diproses dapat digunakan kembali dalam cache memori per akun dan bahasa selama sesi aplikasi. Cache dibatasi dan dibersihkan saat akun berubah. Mengubah bahasa/jaringan atau mematikan fitur membatalkan permintaan yang sudah tidak sesuai. Ketuk kata untuk melihat cara baca dan menerjemahkannya secara eksplisit.

Terjemahan mesin dan bacaan nama tetap dapat salah menurut konteks; tidak ada janji seluruh bahasa atau lagu selalu tersedia. Jika deteksi bahasa keliru, pilih bahasa asal secara manual. Bahasa/kode yang ditolak layanan menghasilkan pesan, bukan hasil buatan.

Pencarian lirik memakai LRCLIB, KuGou, dan tambahan NetEase untuk memperluas katalog. Judul, artis, versi dan durasi dibandingkan; lirik bertimestamp lebih diutamakan dibanding teks biasa. Ketiadaan hasil diberi jeda cache agar sumber tidak diminta terus-menerus. Hasil manual dan LRC impor tetap tersedia jika hasil otomatis kurang cocok. Waktu lirik dapat dikoreksi per lagu melalui Atur waktu.

Tema lirik: Ikuti sampul, Beda tiap lagu, Panggung malam, Samudra, Hutan, Kertas, dan Monokrom. Gerak aktif: Fokus, Geser, Sorot, Mengambang, Panggung, Tenang dan Spotlight. Pilihan ada pada Lirik pintar dan Pengaturan. Animasi antarmuka/lirik dapat dimatikan, dan perubahan lintas tema terang-gelap menjaga warna teks.

## Pilihan remix tambahan

Selain Slowed + Reverb dan Nightcore, Studio suara dan deretan preset pemutar menyediakan:

- Speed Up Natural:1,10x dengan nada tetap.
- Latihan vokal:0,80x dengan nada tetap.
- Deep Voice:tempo tetap dan nada turun3semitone.
- Dreamscape:0,92x, nada sedikit lebih rendah dan hall.
- Concert Hall:tempo/nada asli dengan hall.

Pitch tambahan dan efek analog tetap terpisah. Tanpa remix kembali ke pengaturan tempo pengguna. Room tetap memakai timeline asli agar perangkat tidak berbeda kecepatan. Efek ini pemrosesan audio lokal; tidak mengubah rekaman menjadi instrumen akustik atau menggunakan model suara penyanyi.

Sebelum publikasi, uji APK pada beberapa Android: pemutaran lama, layar mati, Bluetooth, fokus audio, impor sungguhan, bahasa yang sering dipakai, izin/notifikasi pembaruan, berkas update rusak, pemasangan upgrade dengan data tetap ada, dan dua perangkat Radio/Listen Together. Integrasi sumber nonresmi memerlukan pemeliharaan ketika respons penyedia berubah.
