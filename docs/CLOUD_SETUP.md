# Panduan mengaktifkan akun dan sinkronisasi cloud

Untuk proyek yang sudah dikonfigurasi, lihat [catatan pembaruan 0.3.1](PEMBARUAN_0.3.1.md#cloud-dan-pekerjaan-berikutnya). Tidak perlu mengulang migrasi atau pemasangan fungsi yang sudah berhasil.

Panduan ini menjelaskan cara mengaktifkan pendaftaran akun, login, dan sinkronisasi koleksi iSwara menggunakan Supabase.

Saat aplikasi diserahkan, proyek Supabase dan layanan pengirim email SMTP belum disiapkan. Kode backend sudah tersedia, tetapi layanan cloud produksi belum dibuat. Setelah konfigurasi selesai, APK harus dibangun ulang dengan URL dan kunci publik proyek Supabase milikmu.

## 1. Membuat proyek dan menyiapkan basis data

1. Buat proyek Supabase di akun milikmu. Gunakan paket gratis jika kapasitasnya mencukupi; peningkatan ke paket berbayar tidak dilakukan otomatis.
2. Pasang Supabase CLI dengan mengikuti [petunjuk instalasi resminya](https://supabase.com/docs/guides/cli/getting-started).
3. Buka PowerShell di folder proyek `C:\project\iSwaraMusic`.
4. Jalankan perintah berikut. Ganti `YOUR_PROJECT_REFERENCE` dengan referensi proyek Supabase milikmu:

```powershell
supabase login
supabase link --project-ref YOUR_PROJECT_REFERENCE
supabase db push
supabase functions deploy delete-account
```

Migrasi membuat tabel `documents`, tabel `sync_receipts`, urutan nomor revisi, serta fungsi `sync_document`. Setiap baris dimiliki oleh pengguna sesuai `auth.uid()`. Aplikasi hanya dapat membaca dokumen milik akun yang sedang masuk; perubahan data harus melalui fungsi sinkronisasi yang memerlukan autentikasi.

Fungsi penghapusan akun memverifikasi pengguna melalui Supabase Auth. Kunci service-role hanya digunakan di server untuk menghapus akun pengguna yang sudah diverifikasi tersebut.

## 2. Mengatur email dan autentikasi

- Aktifkan pendaftaran menggunakan email/password dan konfirmasi email.
- Atur panjang minimum password menjadi 8 karakter atau lebih.
- Tambahkan tepat `iswara://auth` ke daftar URL pengalihan yang diizinkan. Untuk tahap awal yang hanya menggunakan APK, isi alamat situs atau *site URL* dengan `iswara://auth`.
- Pertahankan template email konfirmasi dan pemulihan yang menggunakan `{{ .ConfirmationURL }}`. Aplikasi memakai PKCE/S256 dan menyimpan kode verifikasi internal secara terenkripsi melalui Android Keystore. Buka tautan email dalam waktu 30 menit pada instalasi aplikasi yang meminta email tersebut. Tautan pemulihan akan membuka formulir password baru; tautan berisi token yang tidak diminta aplikasi akan ditolak.
- Konfigurasikan SMTP sendiri dengan domain pengirim yang sudah diverifikasi beserta pengaturan DNS yang sesuai. SMTP bawaan Supabase membatasi penerima publik; baca [panduan SMTP Supabase](https://supabase.com/docs/guides/auth/auth-smtp).
- Simpan kredensial SMTP hanya di Supabase. Periksa batas pendaftaran dan pengiriman email sebelum mengundang pengguna publik.
- Gunakan URL proyek dan kunci publik *publishable/anon* pada aplikasi. Tetap aktifkan RLS, yaitu pembatasan akses per baris berdasarkan pemilik data. Kunci publik memang akan disertakan dalam APK; kunci tersebut tidak memiliki wewenang administrasi.

## 3. Mengisi konfigurasi aplikasi Android

Salin `secrets.properties.example` menjadi `secrets.properties`. Isi URL proyek dan kunci publik milikmu:

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY=YOUR_PUBLISHABLE_OR_ANON_KEY
```

Siapkan konfigurasi penandatanganan release sesuai [petunjuk di README](../README.md#menandatangani-apk-release), lalu bangun ulang APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

APK release yang dihasilkan berada di `app/build/outputs/apk/release/app-release.apk`. Pasang APK hasil pembangunan ulang tersebut untuk menggunakan konfigurasi cloud yang baru.

## 4. Menguji basis data dan alur lengkap

Setelah Docker dan Supabase CLI terpasang, jalankan:

```powershell
supabase start
supabase test db
```

Pengujian pgTAP memeriksa pembuatan data oleh pengguna yang sudah masuk, pemisahan kepemilikan data, pengiriman ulang tanpa duplikasi, konflik perubahan, serta penolakan penulisan langsung ke tabel. Aktifkan ekstensi pgTAP jika belum tersedia di lingkungan pengujian lokal.

Selanjutnya, uji pada dua instalasi Android:

1. Daftar, konfirmasikan email, lalu masuk. Buat playlist dan simpan satu lagu ke favorit.
2. Masuk ke akun yang sama di perangkat kedua, lalu tekan **Sinkronkan sekarang**. Periksa nama playlist, urutan lagu, dan favorit.
3. Putuskan koneksi kedua perangkat. Ubah playlist yang sama secara berbeda, sambungkan kembali, lalu sinkronkan keduanya. Pastikan salinan konflik mempertahankan perubahan yang bersamaan.
4. Hapus favorit dan playlist saat offline. Setelah tersambung kembali, pastikan penghapusan diterapkan di perangkat lainnya.
5. Keluar, lalu masuk dengan akun lain. Pastikan koleksi tidak tercampur antar-akun maupun dengan koleksi tamu.
6. Minta pemulihan password, buka tautan email, buat password baru, lalu coba masuk kembali.
7. Ekspor backup, kemudian hapus akun pengujian. Pastikan akun tidak dapat digunakan untuk masuk lagi dan baris data miliknya ikut terhapus melalui aturan cascade.

## Cara kerja sinkronisasi

- Antarmuka membaca data lokal dari Room. Perubahan lokal tetap tersimpan ketika koneksi gagal.
- Server memberikan nomor revisi. Penentuan konflik tidak bergantung pada ketepatan jam perangkat.
- Setiap operasi memiliki tanda terima UUID. Mengirim ulang operasi yang sama tidak menggandakan perubahan.
- Jika playlist diedit dari revisi lama, versi server dipertahankan dan perubahan lokal disimpan sebagai salinan konflik. Penghapusan atau perubahan favorit/riwayat dari revisi lama disesuaikan dengan revisi server agar dapat dicoba kembali.
- Sinkronisasi berjalan setelah perubahan lokal, setelah login, dan secara berkala ketika ada koneksi. Perubahan diterapkan bertahap; aplikasi belum menyediakan penyuntingan kolaboratif secara langsung.
- Berkas audio lokal tidak diunggah. Playlist dapat menyinkronkan metadata berkas tersebut, tetapi pemutarannya pada instalasi berbeda akan ditolak. Pilih berkas lokal pada perangkat tujuan jika ingin memutarnya di sana.
- Metadata sinkronisasi bersifat pribadi per pengguna. Versi ini belum menerbitkan playlist kolaboratif atau profil publik.

Sebelum memperluas distribusi publik, pantau pemakaian penyimpanan, pertumbuhan tanda terima operasi, keberhasilan pengiriman email, dan kuota proyek. Pengarsipan tanda terima operasi perlu mengikuti kebijakan penyimpanan yang mempertimbangkan batas waktu pengiriman ulang. Aplikasi tidak menghapus tanda terima tersebut secara otomatis.
