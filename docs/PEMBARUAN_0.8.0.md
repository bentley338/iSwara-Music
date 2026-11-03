# iSwara Music 0.8.0

Panduan dalam Bahasa Indonesia. Pasang APK sebagai pembaruan, tanpa menghapus aplikasi lama. Kunci penandatanganan tetap sama. Backup koleksi melalui Ruang → Ekspor backup sebelum mengganti perangkat.

## Radio dan lounge

Radio sekarang ada di navigasi utama, bersama Beranda, Cari, Koleksi, dan Ruang. Jelajah musik tetap tersedia melalui Beranda dan Ruang. Akun serta Admin tetap melalui Ruang; hak Admin diperiksa server.

Di Radio, tekan **Go Live**, masuk dengan akun yang emailnya terverifikasi, lalu isi nama tampilan, judul, deskripsi, suasana, dan visibilitas. Siaran publik tampil di direktori; siaran privat dibuka melalui kode atau tautan `iswara://radio/KODE`. Kode merupakan undangan, jadi bagikan kepada orang yang ingin diundang.

Antrean streaming aktif menjadi antrean siaran. Host mengendalikan play, pause, next, dan posisi; anggota bisa menambahkan lagu melalui tombol Tambah lagu. Setiap perangkat mengambil audionya sendiri. Ini berbagi pemutaran, bukan siaran mikrofon atau unggah audio host. Koneksi dan sumber dapat menyebabkan selisih waktu; sinkronisasi bukan jaminan tanpa keterlambatan.

Lounge menyediakan komentar, lima reaksi, ikuti percakapan otomatis yang bisa dimatikan, riwayat lebih lama, coba kirim ulang, serta batal pesan tertunda. Host/admin dapat menghapus pesan dan memblokir anggota; pengirim dapat menghapus pesannya sendiri. Batasnya 32 akun, 6 jam, komentar 280 karakter, dan jeda pengiriman 2 detik. Server menyimpan bukti operasi agar percobaan ulang tidak menggandakan pesan.

Meninggalkan siaran memulihkan antrean pribadi dalam keadaan jeda. Radio otomatis pribadi tetap berada di Pengaturan → Pemutaran. Antrean siaran diatur host dan anggota, terpisah dari radio otomatis pribadi.

Migrasi `supabase/migrations/202611030004_radio_lounge.sql` sudah dijalankan pemilik proyek dengan hasil Success. Pemeriksaan baca-saja sesudahnya memastikan direktori tersedia dan lounge menolak anonim. Pengujian dua akun/perangkat tetap perlu dilakukan sebelum peluncuran publik.

## Sumber audio cadangan

Pengaturan → Pemutaran → **Cari sumber cadangan** mengizinkan pencarian alternatif saat sumber asli gagal. Pencocokan membandingkan judul, artis, versi, dan durasi; versi live, cover, remix, atau karaoke tidak dianggap otomatis setara dengan rekaman asli. Identitas lagu dalam playlist tetap dipertahankan.

Untuk memilih sendiri, buka menu lagu → **Sumber audio & cadangan**. Cari berdasarkan judul/artis atau tempel tautan sumber yang didukung, periksa kandidat, lalu pilih. Sumber diuji sebelum disimpan. Pilihan berlaku per akun pada perangkat ini dan dapat dikembalikan ke asli. Pemutar menandai penggunaan cadangan.

Adapter mencakup pencarian video dan SoundCloud melalui extractor. SoundCloud hanya memakai audio publik dengan format HTTP progresif yang tersedia; format HLS, cuplikan premium, autentikasi, atau pembatasan wilayah tidak diterobos. Tidak semua lagu memiliki cadangan yang cocok. Jika pencarian gagal, koleksi tetap tersimpan dan aplikasi memberi pilihan mencoba kembali atau memilih manual. Tidak ada janji lagu selalu tersedia.

## Lirik pintar

Buka tab Lirik pada pemutar → **Lirik pintar**, atau Pengaturan → **Cara baca & arti lirik**. Aktifkan cara baca untuk romaji Jepang, romanisasi Korea, dan pinyin Mandarin. Pemrosesan cara baca berjalan lokal. Pilih bahasa secara manual jika deteksi aksara kurang tepat, misalnya teks Jepang yang hanya berisi kanji. Nama orang, bacaan khusus, dan kata ambigu bisa memerlukan koreksi konteks.

Aktifkan arti Bahasa Indonesia untuk terjemahan berdampingan. Aplikasi meminta persetujuan sebelum mengirim teks lirik ke Google Translate; selama aktif, pilihan juga berlaku pada lagu berikutnya. Pilihan hanya Wi-Fi aktif secara bawaan. Lirik dan koneksi internet diperlukan. Ketuk kata untuk membuka cara baca dan tombol **Translate with Google**; arti kata dikirim hanya saat tombol ditekan. Terjemahan mesin dapat keliru; tidak ada cap waktu per kata yang dibuat-buat.

Integrasi terjemahan menggunakan endpoint web tanpa API key dan membutuhkan pemeliharaan bila respons berubah. Pembatasan layanan ditangani dengan pesan dan jeda; tidak memakai proksi untuk menghindarinya. Atribusi resmi Google Translate ditampilkan di dekat hasil. Cara baca tetap dapat digunakan saat internet terputus setelah data lirik tersedia. Mematikan animasi antarmuka juga menghentikan gerakan fokus lirik baru.

## Studio analog

Di Studio suara tersedia **Kehangatan analog**, **Tekstur vinyl**, dan **Ruang headphone**, masing-masing dengan intensitas mati/25/50/75/100 persen serta reset bersama. Efek bekerja pada PCM di perangkat, termasuk saat musik berjalan. Keadaan mati melewatkan PCM tanpa perubahan; pengaktifan dipudarkan untuk mengurangi perubahan mendadak.

Kehangatan menggunakan saturasi lembut; tekstur vinyl menambahkan noise kecil mengikuti energi audio; ruang headphone menggunakan crossfeed stereo dan pergeseran panorama halus. Ini bukan Dolby, HRTF, atau pelacakan kepala. Efek tidak mengubah waktu bersama di room. Mulai dari intensitas rendah dan nilai kenyamanannya dengan headphone/speaker sendiri.

## Tema dan Beranda

Pengaturan menyediakan tema Sistem, Terang, Gelap, Tengah malam, Hutan, dan Fajar, dengan enam aksen. Animasi antarmuka bisa dimatikan; pengaturan animasi sistem juga dihormati. Warna sampul dan animasi lirik tetap bisa aktif bersamaan.

Beranda menggunakan identitas bagian yang stabil, tinggi judul kartu tetap, dan mempertahankan hasil yang sudah tampil saat penyegaran. Pembaruan posisi mini-player dipisahkan agar tidak memicu pembaruan seluruh halaman pada setiap tick pemutaran. Perubahan ini menargetkan pergeseran tampilan; mutu playback dan perilaku perangkat fisik masih perlu diuji.

## Yang perlu dicoba sebelum rilis publik

1. Pemutaran sumber asli/cadangan, seek, next, layar mati, Bluetooth, panggilan dan aplikasi lain pada beberapa Android. Fokus audio tetap dihormati; aplikasi tidak memaksa musik berjalan saat sistem mencabut fokus.
2. Dua akun/perangkat: buat/join publik dan privat, kendali host, antrean anggota, putus-sambung jaringan, komentar/reaksi, moderasi, keluar, dan kedaluwarsa.
3. Cara baca tiga bahasa, ganti akun/lagu saat proses, izin terjemahan, Wi-Fi mati, pembatasan layanan, dan ukuran teks besar.
4. Efek analog dengan audio mono/stereo, speaker/headphone, volume nyaman, dan perpindahan lagu.
5. Regresi karaoke AI, kartu banyak baris, encoder M4A/MP4, overlay dan widget pada Android sungguhan. Tes komponen JVM tidak menggantikan pengujian perangkat tersebut.

Bukti build, lint, pengujian, signature dan batas pengujian ada di `docs/VERIFICATION.md`. APK, source, mapping dan checksum berada di `dist` setelah proses pengemasan berhasil.
