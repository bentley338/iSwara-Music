# Pemeriksaan desain iSwara Music 0.5.0

Arah: DESIGN.md, ENERGY 2 / RHYTHM 2 / MOTION 2. Cakupan yang diperiksa: source Android, pengujian UI/renderer lokal, dan pemeriksaan build. Status ini tidak menyatakan seluruh integrasi native atau layanan publik sudah lolos uji penerimaan. Pengguna meminta APK dibangun dahulu, kemudian mengizinkan emulator atau pengujian lain sebagai pengganti HP.

## Hard gate

- R-02 PASS: teks tindakan baru memakai kalimat langsung; tidak menambah em dash pada teks UI.
- R-03 PASS untuk layar yang diuji: TabletPlayerUiTest pada teks 150% membuka antrean, lirik, pemilih baris, dan studio; UiFlowTest memeriksa navigasi HP. Kolom dan dialog dapat digulir. Snapshot lyrics-card-studio.png diperiksa langsung.
- R-17 PASS: tidak ada angka pengguna, rating, performa, atau statistik promosi rekaan; hitungan room berasal dari server, rekap dari data lokal.
- R-18 PASS: tidak ada testimoni atau profil rekaan pada produk. Teks fixture hanya berada di pengujian.
- R-23 PASS: pengguna secara eksplisit meminta kartu dan tiga gaya widget. Logo vektor yang sudah ada dipertahankan; sampul katalog dipakai sebagai konten, bukan aset buatan aplikasi.
- R-24 PASS: Beranda membuka Denger Bareng; Setelan membuka galeri widget; Pemutar membuka pemilih baris dan studio. Jalur masuk/keluar dialog diuji.
- R-25 PASS untuk token/renderer yang diperiksa: contrast-0.5.0.txt memeriksa 38 pasangan tema; ExperienceTest memeriksa kontras palet sampul. Kartu Kertas/Charcoal serta menu lagu gelap/terang diperiksa melalui PNG.
- R-26 PASS pada implementasi: tombol mempunyai handler nyata untuk room/RPC, simpan/bagikan PNG, pemasangan widget, overlay, dan admin. Hasil filter antrean menonaktifkan reorder; aksi room mengikuti peran. Keterbatasan pengujian platform dicatat pada R-35.
- R-27 PASS: studio mempunyai loading/error dan tombol yang dinonaktifkan selama rendering; room mempunyai keadaan belum login, sibuk, koneksi putus, host hilang, dan room berakhir; overlay menangani izin ditolak/dicabut dalam tes.
- R-28 PASS: tidak menambah FAQ dekoratif.
- R-32 PASS pada komponen Android yang diperiksa: kontrol Material/native, label bidang, nama aksesibilitas, keadaan disabled, dan jalur tutup dialog tersedia. Audit TalkBack fisik belum dilakukan.
- R-33 PASS: implementasi berada pada Kotlin/XML proyek, bukan injeksi UI runtime.
- R-34 PASS pada pengujian lokal: warna teks baris aktif berasal dari pasangan tema; lima tes animasi mencakup dua tema/palet serta motion mati. Menu gelap/terang tetap diperiksa dalam TabletPlayerUiTest.
- R-35 FAIL untuk klaim seluruh integrasi selesai: APK belum dijalankan pada emulator/perangkat versi ini. Emulator gagal boot; Sharesheet, launcher, dua sesi room, dan CRUD admin produksi belum diuji menyeluruh. Build dan 85 tes unik lokal tersedia; batas ini dicatat eksplisit. Paket disediakan sebagai APK pengujian bertanda tangan atas permintaan pengguna untuk lanjut build, bukan pernyataan lolos penerimaan publik.
- R-36 PASS: tidak menjanjikan tanpa delay/crash atau sinkron milidetik. Hasil perangkat 0.4.0 tidak dipakai sebagai bukti eksekusi 0.5.0.
- R-37 PASS: identitas charcoal/putih hangat/jingga, Plus Jakarta Sans, dan sampul sebagai isi dipertahankan sesuai DESIGN.md.
- R-38 PASS: metadata musik berasal dari sumber/antrean pengguna; fixture pengujian ditandai dan tidak mengisi katalog produksi.
- R-39 PASS pada implementasi yang diperiksa: tombol native/Material, target utama 48 dp, text wrapping, dan pengaturan gerakan. Vinyl mempunyai opsi mati serta mengikuti skala animasi sistem; overlay dapat ditutup. Uji aksesibilitas fisik lengkap belum dilakukan.
- R-40 PASS pada renderer yang diperiksa: kartu memiliki dimensi output tetap; sampul dan preview punya batas ukuran; bitmap widget dibatasi. Tidak menerbitkan angka performa baru yang tidak diukur.

## Purpose gate

- R-01/R-13 PASS: palet/gradasi pemutar menghubungkan sampul dengan lagu; intensitas dibatasi kontras. Latar kartu Sampul memakai scrim untuk keterbacaan; Kertas tidak memakai glow.
- R-04 PASS: ikon Material Rounded menunjukkan lirik, grup, widget, dan kontrol pemutaran yang sesuai fungsi.
- R-06 PASS: Plus Jakarta Sans dipakai agar sesuai tipografi aplikasi dan tetap terbaca pada PNG yang dibagikan.
- R-07 PASS: tidak ada pola latar dekoratif. Alur melingkar Vinyl menjelaskan piringan, bukan pola generik.
- R-08 PASS: tidak menambah panah dekoratif pada setiap tombol.
- R-09 PASS: chip memilih gaya/format/gerakan dan menyatakan pilihan; tidak ada badge promosi.
- R-10/R-12 PASS: transparansi hanya pada widget/overlay yang perlu menampilkan layar di belakangnya; dialog tetap permukaan solid.
- R-14 PASS: widget memakai tiga struktur yang berbeda sesuai pilihan pengguna; daftar lagu tetap baris efisien.
- R-19 PASS: animasi finite untuk baris lirik; Vinyl hanya berjalan saat musik berjalan dan dapat dimatikan. Tidak menggerakkan seluruh UI sekaligus.

## Liveliness dan craftsmanship

- PASS: sampul, baris pilihan pengguna, kode/nama room, dan keadaan playback merupakan isi yang mengubah tampilan; tidak memakai konten pemasaran pengisi.
- C-1/C-2 PASS: hierarki pratinjau, pilihan gaya, dan tombol utama jelas pada snapshot; judul/artist dipisahkan dari kutipan lirik.
- C-3 PASS: jalur kosong/loading/error dan pembatalan tidak mengirim atau memublikasikan konten otomatis.
- C-4/C-5 PASS pada renderer lokal: PNG enam kombinasi gaya/format dan tiga widget dibuka untuk inspeksi; teks tidak menimpa metadata atau footer pada fixture panjang yang diuji.
- R-05/R-11/R-15/R-16/R-20/R-21/R-29/R-30/R-31 PASS pada perubahan ini: token tema, satu keluarga ikon, skala 4/8 dp, kontrol Material, bentuk kartu, dan penamaan tombol konsisten dengan aplikasi yang ada.

Kesimpulan: implementasi/desain lokal diperiksa. Gate penerimaan integrasi penuh masih gagal pada R-35 dan tidak disamarkan menjadi PASS. Bukti, batas, serta langkah uji berikutnya dicatat di VERIFICATION.md dan LANJUTKAN.md.
