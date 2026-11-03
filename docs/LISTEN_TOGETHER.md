# Denger bareng: penggunaan dan pemasangan

## Memakai room

1. Masuk dengan akun yang emailnya telah diverifikasi. Buka Denger bareng dari Beranda.
2. Siapkan antrean streaming, beri nama room, lalu buat. Lagu lokal dilewati saat antrean awal dibagikan.
3. Bagikan kode 12 karakter atau tautan `iswara://room/KODE`. Membuka tautan hanya mengisi undangan, tidak otomatis bergabung.
4. Teman masuk ke akun sendiri lalu bergabung. Cari lagu melalui pencarian room dan tambahkan ke antrean bersama.
5. Host dapat putar/jeda, berpindah lagu, mengubah posisi, menghapus, dan mengurutkan antrean. Anggota mengikuti host, tetapi dapat jeda lokal. Kembali mengikuti host melalui tombol putar di room.
6. Keluar room memulihkan antrean pribadi dalam keadaan jeda. Jika host keluar, room ditutup untuk semua anggota.

Room berlaku 6 jam, maksimal 32 anggota dan 200 lagu. Jika host tidak mengirim kabar selama sekitar 30 detik, anggota dijeda dan melihat status menunggu host. Gangguan jaringan menghasilkan percobaan sambung ulang. Penambahan lagu yang belum pasti dapat dicoba ulang dengan ID operasi yang sama agar tidak menggandakan lagu.

Jika Android menghentikan proses aplikasi, sesi room perangkat tersebut berhenti; aplikasi tidak otomatis masuk kembali setelah restart. Buka Denger bareng dan pilih kode room terakhir bila masih berlaku. Pemutaran background mengikuti layanan media Android dan kebijakan baterai perangkat.

Audio diputar masing-masing perangkat melalui sumber musik. Tidak ada pengiriman audio dari HP host. Lagu yang dibatasi wilayah, tidak tersedia, atau koneksi lambat dapat berbeda hasilnya antarperangkat. Koreksi waktu memakai estimasi jam server dan ambang selisih 1,8 detik; fitur ini tidak menjanjikan speaker berbunyi pada milidetik yang sama. Kecepatan putar room ditetapkan 1× dan skip silence dimatikan agar durasi tetap sejalan. Pengaturan pribadi dipulihkan setelah keluar.

## Status proyek ini

Pengguna telah menjalankan `supabase/migrations/202611020003_listen_together.sql` dengan hasil Success di dashboard. Pemeriksaan anonim memastikan tabel dan RPC terpasang serta menolak akses tanpa login. Tidak perlu menjalankan ulang untuk menggunakan APK ini.

Untuk proyek Supabase baru, jalankan migrasi library dan admin lebih dahulu, kemudian migrasi Listen Together tersebut melalui SQL Editor. Migrasi mengaktifkan RLS, fungsi terautentikasi, dan memasukkan `listening_rooms` ke publikasi `supabase_realtime` jika publikasi tersedia. Periksa Database → Replication bila status aplikasi terus menunjukkan sambungan berkala. Jangan menaruh service-role key dalam APK.

Tabel `listening_rooms`, `listening_members`, dan `listening_receipts` tidak dapat ditulis langsung oleh aplikasi. Perubahan melalui RPC yang memeriksa akun aktif, email terverifikasi, keanggotaan, dan wewenang host. Antrean memakai ID video yang divalidasi dan URL kanonis; URL arbitrer serta file lokal ditolak. Revisi melindungi perubahan bersamaan, sedangkan receipt membuat penambahan idempoten.

## Data dan pengujian

Anggota room dapat melihat nama room, ID host, jumlah anggota, serta metadata antrean/posisi pemutaran. Kode adalah undangan; bagikan hanya kepada orang yang dituju. Logout/pindah akun menghentikan sesi room di perangkat. Data room kedaluwarsa lebih dari 24 jam dibersihkan ketika fungsi pembuatan room dijalankan; ini bukan cron otomatis tepat pada jam kedaluwarsa.

Uji PostgreSQL lokal: `node tools/test-together.mjs`. Pengujian tersebut meliputi migrasi berulang, RLS, host/anggota/orang luar, antrean idempoten, konflik revisi, keluar room, dan penutupan. Tes Android memeriksa aturan waktu/kode serta pemulihan antrean pribadi. Detail verifikasi rilis ada di VERIFICATION.md.

Uji penerimaan dua perangkat: gunakan dua akun terverifikasi, buat/gabung room, pastikan status Realtime, play/pause/next/seek oleh host, tambah lagu bersamaan, putuskan jaringan salah satu perangkat, sambungkan kembali, jeda lokal anggota, pindah akun, dan akhiri room. Catat selisih posisi dan kasus lagu yang tidak tersedia. Uji ini memerlukan dua sesi akun; kelulusan tes lokal tidak menggantikannya.
