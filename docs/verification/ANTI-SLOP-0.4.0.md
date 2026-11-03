# Pemeriksaan desain iSwara Music 0.4.0

Cakupan: APK pengujian dan perubahan UI lokal. Pengguna meminta build dilanjutkan sebelum pengujian cloud produksi selesai. Aktivasi admin, SMTP, dan alur dua akun produksi tetap dicatat sebagai pekerjaan terpisah di ADMIN_SUPABASE.md dan VERIFICATION.md; laporan ini bukan persetujuan rilis cloud publik.

## Hard gate

- R-02 PASS: teks UI baru memakai kalimat langsung tanpa em dash; judul katalog merupakan data sumber.
- R-03 PASS: navigasi layar HP dan tablet pada teks 150% diperiksa UiFlowTest/TabletPlayerUiTest. Menu lagu dapat digulir, judul dibatasi baris, dialog lirik memakai daftar bergulir.
- R-17/R-18 PASS: tidak ada statistik pemasaran atau testimoni rekaan. Rekap memakai data Room; ringkasan admin memakai query server.
- R-23 PASS: logo vektor yang sudah ada dipertahankan. Sampul berasal dari metadata katalog, bukan aset yang diklaim buatan aplikasi.
- R-24 PASS: empat tab menuju layar nyata; Setelan dan dialog antrean/lirik memiliki jalur buka/tutup yang diuji.
- R-25 PASS: 38 pasangan warna tema memenuhi 4,5:1 dalam contrast-0.4.0.txt. ExperienceTest memeriksa warna tombol/sampul pada kedua tema dan API 26/36.
- R-26 PASS: antrean, undo, pencarian/pilihan/impor lirik, gerakan, tema, dan menu memakai handler aplikasi nyata. Tombol admin hanya tampil untuk peran yang dikonfirmasi server; tidak ada tombol promosi fitur yang belum dibuat.
- R-27 PASS: katalog, lirik, antrean, dan admin menyediakan kondisi kosong/loading/error. Data contoh berada dalam fixture pengujian, tidak menjadi katalog aplikasi.
- R-28 PASS: tidak ada FAQ dekoratif.
- R-32 PASS: kontrol memakai komponen Compose/Material dengan fokus dan nama aksesibilitas; input memiliki label, dialog dapat ditutup. Jalur dialog diuji UI.
- R-33 PASS: fitur berada di source Kotlin/XML dan dikompilasi; tidak ada injeksi UI saat runtime.
- R-34 PASS: menu lagu gelap/terang diperiksa pada track-menu-dark.png dan track-menu-light.png, termasuk judul lagu dan tindakan.
- R-35 PASS untuk cakupan lokal: 64 tes lulus, termasuk alur klik tema, menu, antrean, undo, timer, favorit, pemilih lirik, dan teks besar. Uji audio jaringan ikut dijalankan. Jalur admin produksi menunggu deployment dan tidak dinyatakan lolos di laporan ini.
- R-36/R-38 PASS: jumlah tes dan keterbatasan berasal dari hasil alat. Tidak ada janji tanpa delay, bebas crash di semua perangkat, kualitas audio yang tidak tersedia, atau cloud produksi selesai.
- R-37 PASS: arah DESIGN.md dipertahankan, ENERGY 2 / RHYTHM 2 / MOTION 2; charcoal, putih hangat, jingga, Plus Jakarta Sans, dan sampul sebagai pusat tampilan.
- R-39 PASS: animasi lirik dibatasi 320-350 ms per perubahan baris dan dapat dimatikan; gaya per lagu tidak membuat timing kata palsu. Target sentuh utama mengikuti kontrol Material 48 dp.
- R-40 PASS: sampul memakai ukuran/aspect ratio eksplisit. Ukuran sampul kosong tetap tersedia. Angka waktu playback hanya ditulis sebagai pengamatan perangkat/jaringan yang diuji.

## Purpose gate

PASS: gradasi pemutar berasal dari dua kelompok warna sampul untuk menghubungkan lagu dengan tampilan. Intensitas dibatasi agar teks tetap jelas; warna dapat dimatikan. Warna jingga aplikasi menandai tindakan dan lagu aktif. Permukaan bertingkat membedakan menu dan isi tanpa menambah panel dekoratif.

## Liveliness

PASS: sampul nyata, judul lagu, riwayat, antrean bernama, dan pilihan suasana menjadi isi utama. Tidak ada pola statistik rekaan atau kartu promosi kosong. Pergantian fokus lirik mengikuti baris aktif dan pilihan gerakan pengguna.

## Craftsmanship dan konsistensi

PASS: font Jakarta, jarak utama 20 dp, tombol Material, perilaku loading/error, dan warna teks mengikuti token tema. Menekan kartu lagu aktif membuka pemutar; tombol jeda tersendiri mencegah perubahan audio saat sekadar membuka detail. Tes pipeline tidak memakai pengecualian model perangkat.
