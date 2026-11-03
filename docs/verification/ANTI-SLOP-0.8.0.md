# Audit desain iSwara 0.8.0

Mode during mengikuti pilihan sesi. Arah dasar DESIGN.md tetap charcoal, putih hangat, jingga dan Plus Jakarta Sans; pengguna meminta perluasan palet dan animasi. ENERGY2/RHYTHM2/MOTION2 dipertahankan. Tema Tengah malam, Hutan dan Fajar memberi pilihan suasana tanpa mengganti hierarki aplikasi.

## Tujuan visual dan interaksi

- Radio memiliki tujuan navigasi sendiri; Jelajah tetap dapat dibuka dari Beranda/Ruang. Lima tujuan utama tetap berlabel. Ikon radio, antrean, komentar, reaksi dan sumber menggambarkan tindakannya. Akun/Admin tidak disisipkan ke daftar kontrol pemutar.
- Bidang utama Radio menjelaskan tindakan Go Live dan gabung kode. Direktori menggunakan data server dan kondisi kosong yang jujur. Tidak mengisi siaran, anggota, statistik atau komentar dengan konten rekaan.
- Lirik memakai tiga tingkat: cara baca, teks asli, lalu terjemahan. Kata interaktif mengikuti warna teks tema; garis bawah hanya untuk fokus, agar seluruh baris tidak menyerupai kumpulan tautan web. Atribusi Google Translate memakai badge resmi tanpa perubahan, disertai penjelasan terjemahan mesin.
- Pemilih sumber menunjukkan lagu asli, metadata kandidat, perbandingan versi dan konfirmasi. Identitas koleksi tidak diganti diam-diam. Tidak menjanjikan lagu abadi atau ketersediaan sumber yang tidak terbukti.
- Studio analog memberi intensitas dan reset yang bekerja. Label ruang headphone menjelaskan efek stereo, tanpa klaim Dolby/HRTF. Tidak menambahkan kontrol kosong untuk crossfade, OAuth atau pengenalan lagu.
- Beranda memakai kunci bagian stabil dan tinggi judul tetap. Hasil lama tetap terlihat saat refresh. Progres mini-player dipisahkan dari root halaman untuk mengurangi pembaruan yang tidak perlu.

## Pemeriksaan keterbacaan dan gerak

RadioUpgradeTest memeriksa 240 pasangan warna dari fungsi tema aktual: lima tema kali enam aksen kali delapan pasangan foreground/background. Kontras minimal 4,5:1. Pemeriksaan baseline juga dijalankan melalui tools/check-contrast.py. Tidak menonaktifkan lint atau menambahkan suppression untuk menutupi kegagalan.

Fade halaman, crossfade sampul, fokus lirik dan reaksi singkat mempunyai tujuan perubahan keadaan. LocalAppMotion menghormati pengaturan aplikasi serta ValueAnimator.areAnimatorsEnabled(). Tidak menambahkan animasi dekoratif yang berjalan tanpa batas di halaman baru. Pengujian regresi animasi dengan warna sampul aktif/nonaktif tetap dipertahankan.

Snapshot Radio pada tema gelap/terang dan palet baru, serta smart-lyrics-midnight/forest/dawn merupakan hasil render komponen aplikasi. Snapshot lirik diberi label teks pengujian. Pengujian UI mencakup HP dengan teks130%, rail tablet, serta pemutar kecil dengan teks150%. Target kontrol menggunakan komponen Material dengan area sentuh48dp; kata di lirik memakai semantik link native. TalkBack, keyboard fisik dan UI sistem berbagi/izin masih memerlukan Android sungguhan.

## Batas penerimaan

Build, pengujian, lint, signature dan metode penggabungan hasil dijelaskan di ../VERIFICATION.md. Robolectric/JVM, intent dan pembacaan resource APK bukan bukti semua pipeline native berjalan di HP. Sinkronisasi dua akun/perangkat, fokus audio fisik, Bluetooth, mutu DSP, codec ekspor serta pemisahan AI Android penuh masih memerlukan pengujian perangkat. Tidak mengklaim bebas error atau seluruh penerimaan produksi selesai.
