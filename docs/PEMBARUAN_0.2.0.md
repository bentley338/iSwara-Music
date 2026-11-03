# iSwara 0.2.0: playback, cloud, dan setelan

Pasang APK release 0.2.0 di atas versi release sebelumnya. Application ID dan kunci penandatanganan tetap sama, sehingga data aplikasi tetap tersedia saat pembaruan. APK debug memakai kunci berbeda.

## Perbaikan playback

- Audio dicari melalui helper Android NewPipe tanpa menunggu metadata video, subtitle, dan rekomendasi lengkap. Jika format respons berubah atau URL ditolak, aplikasi dapat mencoba extractor lengkap.
- URL audio disimpan sementara di memori. Permintaan yang bersamaan berbagi proses ekstraksi, sehingga persiapan lagu berikutnya dan tombol putar tidak menggandakan permintaan. URL kedaluwarsa diperbarui.
- Kualitas audio dikunci selama satu stream aktif agar perubahan pengaturan tidak mencampur format ketika melakukan seek. Pengaturan baru berlaku saat stream berikutnya dimuat.
- Permintaan CDN YouTube memakai rentang 512 KB yang disambung menjadi satu aliran berkelanjutan. Ini menangani timeout yang ditemukan ketika permintaan audio dibiarkan tanpa batas byte. Pemutar tetap menerima seluruh lagu, bukan berhenti setelah satu potongan.
- Pemutar memakai koneksi OkHttp yang dapat digunakan ulang. Buffer ditargetkan 30 sampai 120 detik; ambang awal 750 milidetik dan ambang setelah macet 2,5 detik. Angka ini adalah konfigurasi buffer, bukan jaminan waktu mulai lagu.
- URL lagu berikutnya disiapkan setelah pemutaran dimulai. Fitur ini tidak mengunduh audio offline dan otomatis dimatikan ketika mode hemat data aktif.
- Wi-Fi wake lock membantu menjaga koneksi selama pemutaran latar belakang. Riwayat dan rekomendasi baru dipicu setelah audio mulai berjalan.
- Tombol putar dapat menjeda saat buffering. Percobaan ulang tidak memaksa lagu yang sudah dijeda kembali berjalan.

Jika jaringan sering terputus, pilih **Setelan → Kualitas streaming → Sedang** atau aktifkan **Hemat data**. Perubahan kualitas berlaku saat stream berikutnya dibuka. Kecepatan server YouTube, jaringan perangkat, dan pembatasan wilayah tetap memengaruhi pemutaran.

## Setelan dan halaman album

Buka ikon roda gigi di Beranda, atau pilih **Akun → Buka setelan**. Halaman Akun berfokus pada login, sinkronisasi, keluar, dan penghapusan akun. Tema, bahasa, audio, backup, tautan YouTube, dan lisensi berada di Setelan.

Fitur tambahan:

- Kecepatan 0,75x sampai 2x dan pilihan melewati bagian hening.
- Tombol mundur/maju 10 detik di pemutar.
- Sampul besar pada halaman album/playlist; tekan sampul untuk memperbesar.
- Putar, acak, simpan lagu yang sudah dimuat sebagai playlist pribadi, dan bagikan tautan koleksi.
- Pilihan menyiapkan URL lagu berikutnya.

## Konfigurasi Supabase proyek ini

URL proyek harus cocok dengan referensi pada anon key. Koreksi DNS pada sesi versi ini sudah diterapkan di konfigurasi lokal; gunakan secrets.properties milik proyek sendiri. URL konfigurasi tidak disertakan dalam distribusi source.

Pemeriksaan endpoint berhasil mengonfirmasi layanan Auth, autentikasi email, kebutuhan konfirmasi email, tabel library yang menolak pembacaan anonim, dan fungsi `sync_document` yang menolak operasi tanpa login. Hasil disimpan di `verification/cloud-preflight.json`. Pemeriksaan ini tidak membuat akun atau mengubah koleksi.

Fungsi `delete-account` masih perlu dipasang jika endpoint mengembalikan 404. Anon key tidak mempunyai hak untuk memasang fungsi server. Cara melalui Dashboard Supabase:

1. Pilih proyek yang ID-nya `dpewwdhwjhacpvvggiwn`.
2. Buka **Edge Functions**, pilih pembuatan fungsi melalui editor, dan beri nama `delete-account`.
3. Salin seluruh isi `supabase/functions/delete-account/index.ts` ke editor fungsi, lalu deploy. Fungsi memvalidasi token pengguna sebelum menghapus akun milik pengguna tersebut. Service-role key hanya diambil dari lingkungan server bawaan Supabase.
4. Jangan menaruh service-role key atau password SMTP di APK.
5. Jalankan `python tools/check-cloud.py` untuk memeriksa kembali keberadaan endpoint. Hasil `delete_function_present` harus berubah menjadi `true`.

Jika memakai CLI yang sudah login, cukup jalankan:

```powershell
supabase functions deploy delete-account --project-ref dpewwdhwjhacpvvggiwn
```

Migrasi database yang sudah berhasil tidak perlu dijalankan ulang dari nol. Petunjuk SMTP, konfirmasi email, dan pengujian dua perangkat ada di [panduan cloud](CLOUD_SETUP.md).

Setelah memasang APK baru, daftar atau masuk melalui Akun, lalu tekan **Sinkronkan sekarang**. Uji playlist dan favorit dengan akun yang sama pada perangkat kedua. Data tamu tetap terpisah: ekspor backup sebelum login dan impor setelah login bila ingin memindahkannya.

Pengiriman email SMTP, tautan pemulihan pada HP, penghapusan akun, dan sinkronisasi dua perangkat harus dibuktikan dengan akun pengujian. Kunci publik saja tidak cukup untuk menguji alur pengguna yang sudah login.
