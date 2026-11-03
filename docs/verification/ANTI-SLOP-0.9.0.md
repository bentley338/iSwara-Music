# Audit desain iSwara 0.9.0

Mode during mengikuti pilihan sesi. Charcoal, putih hangat, jingga dan Plus Jakarta Sans dipertahankan. ENERGY2/RHYTHM2/MOTION2. Tidak membuat logo/figur/katalog ilustrasi baru; pilihan palet lirik diminta pengguna dan memakai token tema yang telah diuji.

## Fungsi dan hierarki

- Updater memiliki halaman sendiri dengan catatan versi, progres, batal, cek manual dan kontrol kanal/jaringan. Detail checksum ada di panduan dan formulir Admin; halaman pengguna memakai penjelasan singkat. Tidak menjanjikan pemasangan diam-diam atau menyembunyikan konfirmasi Android.
- Impor memakai tahap input, pencocokan, review dan simpan. Hasil kurang yakin tidak dipilih otomatis. Tidak membuat lagu contoh sebagai hasil impor. Batas halaman publik dan jumlah lagu ditampilkan; CSV/JSON/M3U/TXT menjadi alternatif untuk platform/daftar yang tidak dapat dibaca.
- Kurasi antrean mempunyai penjelasan data yang dipakai, tiga arah rekomendasi dan pratinjau yang dapat dipilih. Aksi tambah tidak menyusun ulang antrean lama. Tidak menyebut algoritme metadata sebagai AI generatif atau analisis BPM yang tidak dilakukan.
- Preset baru menyatakan perubahan tempo, pitch atau hall yang benar-benar diterapkan. Tidak memberi nama akustik/karaoke untuk efek yang tidak memisahkan instrumen/vokal.
- Pilihan bahasa memakai pencarian dan daftar berlabel, bukan ratusan chip. Teks asli tetap ditampilkan bersama terjemahan; atribusi resmi dan keterangan terjemahan mesin dipertahankan.
- Tujuh pilihan palet lirik memberi konteks membaca. Tujuh gaya fokus aktif dibatasi pada perpindahan baris. Pengaturan gerakan aplikasi/sistem dan lirik tetap dihormati. Lintas tema terang/gelap tidak memudarkan latar di bawah warna teks yang sudah berpindah.

## Pemeriksaan

Tes mencakup kontras token tema/aksen dan palet lirik, kontrol utama pemutar tanpa scroll pada layar kecil dengan teks besar, navigasi tablet, aturan kode/label bahasa, menu updater/impor/kurasi, serta regresi animasi saat warna sampul diaktifkan. Interaksi dialog selector bahasa belum diuji tersendiri. Dialog berisi TextField memakai konfigurasi runner tanpa lebar eksplisit untuk menghindari Robolectric8460. Kontrol tetap diuji; fitur tidak dihapus demi runner.

Snapshot final updater, impor dan kurasi telah diambil ulang melalui decorView dialog aktif dan dibuka untuk pemeriksaan visual. Judul, pilihan, tombol serta teks input terlihat tanpa tumpang tindih pada konfigurasi HP yang diuji. Teks Artis - Lagu dalam gambar impor adalah input fixture tes, bukan katalog. Snapshot Activity di belakang dialog yang sebelumnya menampilkan Beranda/blank telah diganti. Bukti final dan batas cakupan ada di ../VERIFICATION.md.

Komponen Material, target tombol48dp, label aksesibel dan kondisi disabled digunakan. Halaman daftar memakai LazyColumn, inset aman dan ruang footer. Transaksi berisiko seperti terbitkan rilis meminta tinjauan metadata; tidak ada publikasi otomatis. Hak Admin tetap berasal dari server.

## Batas

Robolectric dan tes intent bukan pengujian installer, notifikasi, TalkBack atau hasil audio fisik Android. Penerbitan GitHub dan pemasangan upgrade perangkat nyata tetap memerlukan langkah pemilik sesuai panduan. Tidak menyatakan seluruh penerimaan produksi atau semua bahasa/platform bebas kesalahan.
