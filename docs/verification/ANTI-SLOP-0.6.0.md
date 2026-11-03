# Pemeriksaan desain iSwara 0.6.0

Arah DESIGN.md dipertahankan: ENERGY2/RHYTHM2/MOTION2, charcoal/putih hangat/jingga, Plus Jakarta Sans, sampul sebagai isi. Pengguna meminta APK untuk diuji sendiri. Cakupan audit lokal bukan sertifikasi seluruh integrasi native.

- R-02 PASS: teks baru menggunakan tindakan langsung; tidak menambah em dash pada teks UI.
- R-03 PASS pada cakupan uji: layar360×640dp dan teks150% menampilkan kontrol utama tanpa scroll; sampul menyesuaikan ruang. Dialog dan daftar tetap bergulir bila perlu. Snapshot player-compact-phone diperiksa.
- R-17/R-18 PASS: tidak ada statistik promosi/testimoni rekaan. Progres berasal dari pekerjaan dan hitungan96/97 berasal dari XML tes.
- R-23 PASS: pengguna meminta preset, pemisahan, semua format ekspor dan pemutar ringkas. Tidak membuat logo/foto baru. Sampul berasal dari metadata; fixture hanya untuk tes.
- R-24 PASS: tombol Studio, kartu, pilihan format dan timer menuju handler/dialog nyata, dengan jalur tutup yang diuji.
- R-25 PASS untuk token yang diuji: warna dasar tidak diubah; screenshot terang/gelap, kartu dan video-frame diperiksa. Tes palet dan animasi lama tetap lulus.
- R-26 PASS pada implementasi: preset mengubah audio, pitch terpisah, pemisahan memakai model ONNX, ekspor mempunyai pipeline codec/muxer nyata; tombol simpan/bagikan memakai API Android. Integrasi perangkat belum dinyatakan lulus.
- R-27 PASS: proses memiliki progres/batal/error; blank lyrics diberi keadaan kosong, hasil belum siap menonaktifkan bagikan/simpan, unsupported/RAM/storage ditangani.
- R-28 PASS: tidak menambah FAQ pengisi.
- R-32 PASS pada UI lokal: komponen native/Material, bidang berlabel dan fokus/disabled, header tutup Studio dipasang tetap. TalkBack fisik menyeluruh belum diuji.
- R-33 PASS: Kotlin/XML dan aset model direproduksi dari sumber yang dipin, tanpa injeksi UI runtime.
- R-34 PASS pada tes: dua tema lama tetap lulus, warna sampul tidak mematikan animasi; teks video/kartu memiliki latar solid/scrim.
- R-35 FAIL untuk penerimaan lengkap: APK0.6 belum dieksekusi di perangkat; native codec, ShareSheet, worker/keystore penuh dan mutu suara masih perlu uji.97 tes unik, build dan renderer tersedia. Pengguna meminta melanjutkan build sambil ia menguji; paket diberi status build pengujian, bukan klaim seluruh penerimaan selesai.
- R-36/R-38 PASS: tidak menjanjikan AI instan, vokal100% bersih, semua perangkat teruji, nol delay/crash, atau ketepatan sinkron sempurna. Keluaran NN nyata dibedakan dari mutu pemisahan.
- R-37 PASS: arah desain tertulis dan dials di atas diikuti.
- R-39 PASS pada komponen yang diperiksa: tombol48dp, label, keadaan aktif/disabled, ukuran teks besar, motion dapat dimatikan. Video dipilih/diekspor pengguna, bukan autoplay saat membuka editor. Audit aksesibilitas perangkat tetap terbuka.
- R-40 PASS: dimensi PNG/video jelas, preview dibatasi, sampul berukuran eksplisit, tidak menerbitkan benchmark Android yang belum diukur.

Purpose gate: R-01/R-13 PASS: warna sampul/scrim mengikat tampilan ke lagu dan menjaga teks. R-04 PASS: ikon timer/EQ/studio/mobil menggambarkan fungsi. R-06 PASS: font aplikasi dipakai pada kartu/video agar konsisten. R-07/R-08 PASS: tidak ada pola atau panah dekoratif massal. R-09 PASS: chip memilih mode/format/durasi, bukan promosi. R-10/R-12 PASS: permukaan solid; tidak menambah glass/shadow berlapis. R-14/R-19 PASS: pemutar mempunyai hierarki berbeda dari editor; animasi baris finite dan mengikuti pilihan gerakan.

Liveliness PASS pada renderer lokal: sampul, pilihan baris, preset dan posisi lagu mengubah isi, bukan teks promosi pengisi. C-1–C-5 serta konsistensi R-05/11/15/16/20/21/29/30/31 PASS pada perubahan yang diperiksa: hierarki, empat aksi bawah berlabel, preview/halaman, ukuran tombol, tema dan tipografi konsisten; snapshot dibuka langsung. Gate R-35 tetap dicatat gagal untuk integrasi penuh dan tidak disamarkan.
