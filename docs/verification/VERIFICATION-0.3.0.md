# Laporan pengujian iSwara 0.3.0

Diperiksa 2 November 2026. APK release berukuran 3,881,450 byte, application ID `id.iswara.music`, version code 3, min SDK 26, target SDK 36.

## Build dan tanda tangan

LULUS: lintDebug, testDebugUnitTest, assembleRelease, assembleDebug, dan compileDebugAndroidTestKotlin. Build terakhir berhasil. Lint 0 error dan 69 peringatan, terutama versi dependensi yang dikunci, atribut/style, serta label XML widget. Tidak ada baseline untuk menyembunyikan error.

LULUS: APK Signature Scheme v2, RSA-3072, satu sertifikat. SHA-256 sertifikat `6903592161d5a611c0748f4d9b38802b879053927e8c4b84ca7bd64f5c901dbe`, sama dengan 0.1.0 dan 0.2.0. Zipalign 16 KB berhasil. Bukti pada verification/signature-0.3.0.txt, alignment-0.3.0.txt, dan apk-0.3.0.txt.

Konfigurasi Supabase dan dokumen privasi Indonesia diperiksa langsung dari isi APK. Keystore dan secrets.properties tidak disertakan dalam source ZIP.

## Pengujian

41 pengujian unik berhasil: 38 uji lokal pada build terakhir dan 3 uji jaringan pada proses terpisah sebelumnya. Dalam suite lokal terakhir, tiga uji jaringan dilewati secara sengaja. Bukti lokal ada di verification/unit-0.3.0/ dan bukti jaringan ada di verification/live-0.3.0/.

Uji baru memeriksa penyaringan radio terhadap antrean/riwayat/duplikasi/berkas lokal/video panjang, pembatas jumlah rekomendasi, ringkasan dengar per periode, AES-GCM dan penolakan ciphertext yang diubah, pemisahan nama berkas akun, pemrosesan PCM saat mati/aktif, pembatasan puncak, serta sinyal senyap.

Kartu lirik diuji sebagai berkas PNG 1080 x 1350 pada direktori cache khusus. Tiga gaya widget berhasil dirender dengan judul dan kontrol. Jendela native equalizer, rekap, dan mode berkendara diambil dari window dialog yang sebenarnya, bukan gambar Activity di belakangnya.

Alur UI HP: navigasi, playlist baru/ubah nama, tema, bahasa, toggle hening, kecepatan, pembesaran sampul, rekap bulan, preset Rock, mode berkendara, dan tab Unduhan. Alur tablet 800 x 1024 dp dengan teks 150%: controller Media3, antrean/reorder, favorit, timer, dan clear. Fixture hanya dipakai pada pengujian dan tidak menjadi katalog di APK.

Robolectric di Windows tidak dapat menyelesaikan jalur FileProvider end-to-end karena pembandingan path memakai separator Android '/'. Renderer PNG diuji langsung; URI berbagi memakai FileProvider AndroidX dengan direktori terbatas dan grant baca sementara, tetapi intent berbagi perlu pengujian HP. Tidak ada perubahan untuk melonggarkan pembatasan FileProvider produksi.

## Jaringan musik

Pencarian, stream audio, sampul, album, dan kanal artis berhasil diuji. Transport audio yang sama dengan player membaca 1.105.920 byte melewati tiga potongan 512 KB dan berhasil seek 32.768 byte. Byte pertama termasuk resolusi URL: 2.056 ms; keseluruhan pembacaan tersebut 2.397 ms. Uji ekstraksi lain: resolve 1.851 ms, cache di bawah 1 ms, byte pertama 2.647 ms. Pengukuran ini berasal dari jaringan komputer pengembangan dan tidak membuktikan waktu keluaran suara pada HP.

Transport byte-range, cache, kedaluwarsa URL, permintaan bersamaan, dan retry dari versi 0.2.0 dipertahankan. Unduhan memakai transport byte-range yang sama.

## Cloud dan privasi

Endpoint Auth, email/password, konfirmasi email, pembatasan library anonim, serta RPC sinkronisasi anonim diperiksa. delete-account sudah terpasang dan menolak panggilan anonim. Bukti: verification/cloud-preflight.json. Login, SMTP, penghapusan akun terautentikasi, serta sinkronisasi dua HP belum diuji.

Unduhan disimpan AES-GCM dengan kunci Android Keystore, maksimal 32 MB per lagu. Scope pemilik ditangkap sebelum menunggu antrean mutex dan diperiksa saat unduhan berjalan agar pergantian akun tidak memindahkan data. Metadata unduhan dan rekap tidak disinkronkan ke cloud. Penghapusan akun membersihkan unduhan/rekap pemilik tersebut.

## Pemeriksaan desain

Identitas S, charcoal/putih hangat/jingga, Plus Jakarta Sans, dan dials ENERGY 2 / RHYTHM 2 / MOTION 2 dipertahankan. Beranda berbasis musik nyata dan riwayat pengguna; tidak ada klaim popularitas atau AI palsu. Setelan memakai kontrol singkat dengan privasi/lisensi dalam dialog terpisah. Warna pemutar dari sampul disesuaikan agar kontras tombol minimal 4,5:1. Kontrol utama minimal 48 dp, mode berkendara 80 sampai 100 dp. Snapshot yang diperiksa berada di screenshots/.

## Batas dan kelanjutan

Tidak ada HP tersambung dan emulator Windows belum tersedia. Keluaran suara nyata, kemampuan equalizer/bass/reverb perangkat, audio latar/screen-off/Bluetooth, Android Keystore untuk file unduhan, WorkManager saat proses mati, overlay di aplikasi lain, sharing intent, dan interaksi widget launcher masih perlu pemeriksaan perangkat. Uji kriptografi tidak menggantikan uji Android Keystore.

Crossfade dua player, ReplayGain/LUFS, karaoke per kata, terjemahan berdampingan, Listen Together, Spotify OAuth, pengenal lagu, Last.fm/Discord, animasi vinyl, dan Android Auto penuh belum selesai. Tidak ada tombol kosong untuk mengklaim integrasi tersebut. Panduan fitur dan batas ada di PEMBARUAN_0.3.0.md; urutan kelanjutan ada di LANJUTKAN.md.

Tidak dilakukan pembelian layanan, pembuatan akun pengembang, atau publikasi otomatis.
