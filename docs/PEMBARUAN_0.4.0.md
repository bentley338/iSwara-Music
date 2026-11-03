# iSwara Music 0.4.0

## Pemutar dan tampilan

Perbaikan buffer audio dari 0.3.1 dipertahankan. Crash ketika berganti lagu saat membuka lirik diperbaiki dengan menangkap state layar yang konsisten; pola yang sama diterapkan ke layar lain. Persiapan audio lagu berikutnya memakai awalan kecil di RAM. Hasil nyata dan batas pengujian tercatat di VERIFICATION.md.

Warna pemutar mengikuti warna dominan sampul, dengan warna pendamping untuk latar. Area hitam/putih tepi sampul dan titik warna kecil tidak mendominasi pilihan warna. Tombol dijaga kontrasnya di kedua tema. Nonaktifkan **Warna dari sampul** di Setelan jika ingin warna aplikasi tetap.

Tema gelap memakai permukaan bertingkat dan teks hangat yang lebih terang. Menu lagu menampilkan sampul, judul yang jelas, serta tindakan Putar. Nama layanan di layar umum menggunakan iSwara Music; sumber sebenarnya tetap tercantum di Privasi dan Lisensi.

Menekan kartu Lanjut Dengarkan untuk lagu aktif membuka pemutar. Tombol play/pause tersendiri mengendalikan audio, sehingga menekan judul tidak menjeda tanpa sengaja. Bila audio berhenti karena headset terlepas atau dipakai aplikasi lain, pemutar dapat menunjukkan alasannya.

## Beberapa antrean dan urungkan

Buka **Antreanku** di Beranda, atau **Antrean tersimpan** di pemutar. Simpan antrean yang sedang aktif, beri nama misalnya Kerja atau Perjalanan, lalu lanjutkan antrean lain. Urutan, shuffle, repeat, lagu aktif, dan posisi disimpan masing-masing. Batas 20 antrean, masing-masing sampai 1.000 lagu.

Antrean tersimpan berada di perangkat dan terpisah per akun; belum disinkronkan ke cloud atau dimasukkan ke backup koleksi. Posisi diperbarui berkala, sehingga penghentian proses mendadak dapat menghilangkan beberapa detik terakhir.

Menghapus lagu, lagu yang sudah lewat, lagu berikutnya, atau seluruh antrean memunculkan **Urungkan** selama 30 detik. Memulai antrean baru atau menyunting urutannya membatalkan pembatalan sebelumnya agar susunan baru tidak tertimpa.

## Lirik

Pencarian otomatis mencoba LRCLIB serta KuGou, menyaring kecocokan judul, artis, versi lagu, dan durasi. Tidak semua lagu memiliki hasil yang cocok. Cache lirik otomatis disimpan hingga 30 hari; pilihan manual dan LRC milik pengguna tetap tersedia di perangkat.

Di tab Lirik pilih **Cari / impor lirik** untuk mencari judul dan artis, memilih hasil lain, atau membuka berkas `.lrc` dari pemilih berkas Android. Berkas harus berisi timestamp seperti `[00:12.50]`. Pilih **Kembali otomatis** untuk membuang pilihan manual pada lagu itu. Lirik tidak disinkronkan ke cloud.

Di Setelan > Lirik & gerakan, aktifkan/nonaktifkan animasi, pilih fokus/geser/sorot, atau biarkan variasi per lagu. Ukuran teks lirik bisa diperbesar. Animasi mengikuti timestamp per baris yang disediakan sumber, bukan timing per kata yang dibuat-buat.

## Admin dan Beranda

Panel admin mendukung pengumuman, pilihan lagu/playlist untuk Beranda, ringkasan data cloud, pencarian pengguna, penangguhan 30 hari, pemulihan akses, serta penghapusan dengan konfirmasi email. Akun admin dilindungi. Hak diperiksa server dan tidak diberikan lewat pengecekan alamat email di APK.

Untuk mengaktifkannya, ikuti [panduan admin Supabase](ADMIN_SUPABASE.md). SQL, fungsi Edge, dan aplikasi sudah disiapkan. Pemasangan produksi dan pemberian peran belum dikerjakan setelah kontrol dashboard dihentikan lewat Escape. Beranda tetap bekerja saat layanan admin belum dipasang.

## Memasang update

Pasang `dist/iSwara-0.4.0.apk` sebagai pembaruan. Jangan uninstall jika ingin mempertahankan koleksi lokal. APK memakai sertifikat release yang sama. Jika Android menolak pembaruan karena signature, pastikan versi lama adalah release iSwara dari proyek ini, bukan APK debug.

Daftar fitur yang masih memerlukan pekerjaan atau konfigurasi ada di LANJUTKAN.md. Crossfade dua player, Listen Together, ShazamKit, integrasi Spotify OAuth, Discord/Last.fm, dan terjemahan di dalam aplikasi belum diklaim selesai.
