# Mengaktifkan panel admin iSwara Music

Panel admin mengelola pengumuman Beranda, pilihan musik, ringkasan data cloud, dan pengguna. APK tidak menyimpan service-role key. Tombol muncul setelah server menyatakan akun yang sedang masuk sebagai admin.

Status proyek pengguna pada 2 November 2026: panel admin sudah tampil dan berhasil digunakan menurut konfirmasi pengguna. Preflight terbaru dapat membaca konten terbit dan memastikan akses anonim ke membership/audit/summary serta fungsi admin-users ditolak. Langkah pemasangan di bawah tetap disediakan untuk proyek baru; tidak perlu mengulang instalasi yang sudah berhasil. Pengujian CRUD produksi oleh agen belum dilakukan.

## 1. Pasang migrasi

Buka proyek Supabase milikmu > SQL Editor. Salin seluruh isi `supabase/migrations/202611020002_admin.sql`, lalu Run. Migrasi library `202611010001_library.sql` harus sudah terpasang. Migrasi admin menggunakan transaksi dan aman dijalankan ulang.

## 2. Berikan peran pada akunmu

Pastikan email akun sudah terverifikasi di Authentication > Users. Jalankan SQL berikut setelah mengganti alamat contoh dengan email akunmu:

```sql
do $$
declare target uuid;
begin
  select id into strict target from auth.users
  where lower(email) = lower('email-admin@example.com')
    and email_confirmed_at is not null;
  insert into public.admin_members(user_id) values(target)
    on conflict(user_id) do nothing;
end $$;
```

Jika pengguna belum ditemukan atau belum terverifikasi, SQL gagal tanpa memberikan peran ke akun lain. Peran terikat UID akun. Mengubah email di APK atau profil tidak memberikan akses admin. Untuk mencabut akses, hapus baris UID yang bersangkutan dari `admin_members` melalui SQL Editor.

## 3. Pasang fungsi admin-users

Dashboard > Edge Functions > Deploy a new function > Via Editor. Nama fungsi harus `admin-users`. Ganti isi berkas dengan `supabase/functions/admin-users/index.ts`, lalu deploy.

Di Settings fungsi ini, nonaktifkan **Verify JWT with legacy secret** dan simpan. Kode fungsi tetap memeriksa token pengguna melalui Auth server dan memeriksa `is_admin()` sebelum menggunakan hak server. Jangan menghapus pemeriksaan tersebut. `SUPABASE_URL`, `SUPABASE_ANON_KEY`, dan `SUPABASE_SERVICE_ROLE_KEY` disediakan lingkungan Edge Supabase; jangan masukkan service-role key ke aplikasi atau chat.

Alternatif melalui Supabase CLI yang sudah login:

```powershell
supabase link --project-ref dpewwdhwjhacpvvggiwn
supabase functions deploy admin-users --no-verify-jwt
```

## 4. Buka aplikasi

Masuk memakai akun admin, buka Akun. Buka ulang halaman bila peran baru saja diberikan. Tombol **Panel admin** muncul setelah pemeriksaan server berhasil. Jika tombol belum muncul, periksa email sudah terverifikasi, UID di `admin_members`, dan migrasi tidak gagal.

- Ringkasan: jumlah akun, email terverifikasi, akun ditangguhkan, playlist, favorit, dan riwayat cloud. Angka ini bukan total menit mendengarkan.
- Beranda: buat/edit pengumuman dan pilihan musik. Simpan sebagai draf atau terbitkan; urutan kecil muncul lebih dahulu. Pilihan musik memakai tautan katalog yang didukung. Sampul HTTPS opsional.
- Pengguna: cari email, lihat halaman berikutnya, tangguhkan 30 hari, aktifkan kembali, atau hapus. Akun admin dan akun sendiri dilindungi. Penghapusan memerlukan pengetikan email target dan bersifat permanen.
- Aktivitas: tindakan admin tercatat di server. Jangan gunakan data pengguna untuk pengujian penghapusan; buat akun uji sendiri.

Setelah menerbitkan konten, segarkan Beranda pada perangkat lain. Beranda hanya membaca konten terbit dengan peran publik, termasuk saat dibuka oleh admin.

## Pengujian dan batas

`node tools/test-admin.mjs` menguji migrasi dan RLS di PostgreSQL lokal melalui PGlite, termasuk akun biasa, pengguna belum terverifikasi, pencabutan akses, larangan menaikkan peran sendiri, isolasi draf, audit, dan akun ditangguhkan dengan JWT yang masih berlaku. Ini tidak menggantikan pengujian Edge Functions dan dua akun di proyek produksi.

Migrasi dan penetapan peran belum dijalankan dari sesi ini: kontrol dashboard dihentikan lewat Escape. Kode siap dipasang; belum ada klaim bahwa peran produksi sudah aktif. Setelah deployment, uji akun admin dan akun biasa; jangan menghapus pengguna sungguhan sebagai tes.
