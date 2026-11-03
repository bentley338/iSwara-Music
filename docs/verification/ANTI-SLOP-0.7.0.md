# Audit desain iSwara 0.7.0

Cakupan: perubahan navigasi, Jelajah, direktori Ruang, pratinjau mix, dan Pusat offline. Arah DESIGN.md dipertahankan: charcoal, putih hangat, jingga, Plus Jakarta Sans, ENERGY2/RHYTHM2/MOTION2. Navigasi tambahan diminta pengguna; tidak ada aset orang/logo atau statistik promosi baru.

## Gate UI lokal

- R-02/R-17/R-18/R-36/R-38 PASS: teks baru langsung menjelaskan tindakan, tanpa testimoni, angka promosi, chart palsu, atau klaim AI generatif. Hitungan unduhan/mix memakai data sebenarnya. Fixture diberi nama pengujian dan hanya berada di tes.
- R-03/R-39 PASS pada konfigurasi yang diuji: HP360×800dp dan tablet1024×768dp pada teks130%, serta regresi pemutar360×640dp teks150%. Label navigasi tampil, target sentuh Material48dp, chip bisa bergulir dan daftar memiliki ruang dari Scaffold. Screenshot baru dibuka langsung. Ini bukan sertifikasi semua ukuran perangkat.
- R-23/R-24/R-26 PASS: lima tab punya halaman nyata; entri Ruang menuju handler/dialog/kontrak Android. Akun tetap bisa dibuka; Admin mengikuti hak server. Tidak membuat tombol untuk paket yang belum dibangun.
- R-25/R-34 PASS untuk token dan renderer yang diperiksa: check-contrast.py menguji38 pasangan aktual dengan rasio minimal4,85:1, termasuk primaryContainer dan permukaan. Tema terang/gelap HP dan tablet digambar dan diperiksa; teks dan ikon baru memakai token semantik.
- R-27 PASS: pencarian fitur/mix/offline memiliki kondisi kosong; mix mempunyai loading, timeout, error, retry dan cancel. Aksi putar dinonaktifkan bila tidak ada hasil atau pengguna masih dalam room. Unduhan dan penghapusan punya progres; hapus massal meminta konfirmasi.
- R-28 PASS: tidak menambah FAQ pengisi.
- R-32 PASS pada komponen yang diperiksa: kontrol Material/Compose native, bidang berlabel, peran Button/selected/toggle, nama aksesibel pin/tutup, serta status disabled. TalkBack dan keyboard fisik penuh masih perlu uji Android.
- R-33 PASS: perubahan fitur ditulis langsung dalam Kotlin; alat Python hanya memverifikasi APK dan menulis bukti, tidak menyuntikkan UI.
- R-35 PASS pada pengujian UI lokal: navigasi HP/tablet, tema, pemilihan genre ke Cari, mix tanpa autoplay, buang/simpan hasil, pin lepas/pasang, pencarian kosong, buka/tutup Studio/EQ/antrean tersimpan/rekap/mode mobil/widget/room/offline, masuk Lokal/Akun/Setelan, validasi tautan, kontrak backup, dan tab lirik/antrean diuji. Rincian NavigationSpaceUiTest serta XML unit-0.7.0. Dialog input memakai konfigurasi tanpa lebar eksplisit karena [Robolectric #8460](https://github.com/robolectric/robolectric/issues/8460); uji dialog tetap dijalankan.
- R-37/R-40 PASS: identitas dan hierarki aplikasi dipertahankan, artwork berdimensi tetap, tidak menambah benchmark yang belum diukur atau animasi dekoratif tanpa tujuan.

## Purpose gate dan karakter

- R-01/R-07/R-10/R-12/R-13 PASS: permukaan solid dan satu penekanan warna untuk pembuat mix; tidak menambah pola, glass, shadow/glow bertumpuk.
- R-04/R-06/R-08/R-09 PASS: ikon menggambarkan fungsi (EQ, unduhan, grup, folder), font aplikasi konsisten, pin mempunyai keadaan aktif, chip dipakai untuk memilih kategori/filter. Chevron bukan hiasan massal.
- R-14/R-19 PASS: promosi mix, lagu aktif, pintasan dan daftar alat memakai hierarki berbeda. Tidak menambah animasi halaman yang mengabaikan pilihan pengurangan gerakan.
- C-1–C-5 serta R-05/11/15/16/20/21/29/30/31 PASS pada cakupan perubahan: direktori memberi pencarian untuk banyak alat, bottom navigation tetap lima tujuan, rail memanfaatkan ruang jendela, keadaan halaman disimpan dan label konsisten. Nomor lagu, ukuran audio dan koleksi bukan konten rekaan.

## Batas penerimaan

Build, lint, signature serta105 tes unik dicatat terpisah di VERIFICATION.md. Menjalankan UI di JVM dan memeriksa intent pemilih berkas tidak membuktikan UI sistem/hasil share atau codec Android penuh. Validasi HP fisik, M4A/MP4, pipeline karaoke dengan MediaCodec/Keystore/worker, Bluetooth, dan dua perangkat room tetap belum selesai. APK diserahkan sebagai release untuk pengujian pengguna, sesuai pilihan pengguna pada sesi sebelumnya. Tidak menyatakan seluruh penerimaan produksi telah lulus.
