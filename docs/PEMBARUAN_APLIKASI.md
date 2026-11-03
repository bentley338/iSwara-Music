# Menerbitkan pembaruan iSwara melalui GitHub Releases

Panduan ini memakai GitHub Releases gratis untuk APK dan Supabase untuk informasi versi. Tidak perlu menyimpan password GitHub atau service-role key di aplikasi. Tidak ada rilis yang dipublikasikan otomatis oleh alat build.

## Sekali pada awal pemasangan

1. Jalankan `supabase/migrations/202611030005_app_updates.sql` di Supabase SQL Editor, lalu Run. Migrasi menambah tabel/rute versi dan membatasi penyuntingan kepada admin server yang aktif serta terverifikasi. Jika sudah Success, tidak perlu mengulang.
2. Buat repository publik GitHub milik sendiri, misalnya `iswara-music`. Gunakan menu **Releases**, bukan mengunggah APK melalui menu tambah berkas repository.
3. Pilih **Draft a new release**, tag `v0.9.0`, lalu lampirkan APK release, source ZIP dan SHA256SUMS dari dist. Berikan catatan perubahan dalam Bahasa Indonesia. Distribusi source dan pemberitahuan lisensi harus menyertai APK GPL.
4. Setelah memeriksa semua berkas, pemilik dapat menekan **Publish release**. Salin tautan unduh lampiran APK. Bentuknya `https://github.com/PEMILIK/REPO/releases/download/v0.9.0/iSwara-0.9.0.apk`. Tautan halaman `/blob/`, `/tag/`, ZIP source, dan URL pemendek bukan tautan APK yang diterima updater.
5. Di iSwara versi baru, buka **Ruang -> Panel admin -> ikon Rilis aplikasi**. Isi version code, nama versi, tautan APK, SHA-256, ukuran byte, kanal, dan catatan perubahan. Ambil nilai hash/ukuran dari hasil build, bukan perkiraan ukuran MB. Tinjau sebelum menyimpan. Draft tidak muncul kepada pengguna; aktifkan Terbitkan hanya setelah APK dapat diunduh.

GitHub mengizinkan berkas lampiran rilis hingga 2 GiB, cukup untuk APK universal ini. Storage gratis Supabase membatasi berkas hingga 50 MB, sehingga Supabase hanya menyimpan metadata versi yang kecil. Referensi: [GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases), [batas Supabase Storage](https://supabase.com/docs/guides/storage/uploads/file-limits).

## Pengalaman pengguna

- Pengaturan -> Pembaruan aplikasi menyediakan cek manual, pemeriksaan harian, kanal Stabil/Beta, dan pilihan unduhan melalui jaringan tanpa batas kuota.
- Saat ada rilis yang lebih tinggi dan cocok dengan Android perangkat, aplikasi menampilkan banner. Pemeriksaan background dapat memberi notifikasi bila izin notifikasi diaktifkan. Android dapat menunda pekerjaan background saat hemat daya.
- Tombol **Unduh & pasang** memulai unduhan yang terlihat progresnya dan dapat dibatalkan. Jika halaman masih terbuka, pemasangan dilanjutkan setelah unduhan tervalidasi. Saat aplikasi berada di background, ketuk notifikasi siap dipasang untuk melanjutkan.
- Pertama kali, Android dapat meminta izin pemasangan dari iSwara. Konfirmasi pemasangan sistem tetap wajib. Ini bukan pemasangan diam-diam.
- APK diperiksa terhadap ukuran, SHA-256, package id, version code, minimum Android dan sertifikat release yang sama. Berkas berbeda atau terpotong ditolak. Hanya HTTPS GitHub Releases beserta host aset GitHub yang diterima.

Alur izin resmi: [PackageManager.canRequestPackageInstalls](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()). Fitur ini ditujukan untuk distribusi APK langsung. Distribusi Play Store dan persyaratan izinnya perlu dievaluasi terpisah.

## Pengguna yang masih memakai versi lama

Versi sampai 0.8 belum mempunyai mesin pengecek update. Mereka perlu memasang APK baru satu kali sebagai update, tanpa uninstall. Pengumuman Beranda dari panel Admin masih bisa dipakai untuk memberitahukan rilis kepada pengguna versi lama ketika Beranda dimuat, tetapi tidak menambahkan notifikasi background ke APK lama.

Mulai versi yang sudah membawa updater, pengguna dapat mengikuti pembaruan berikutnya dari aplikasi. Versi0.9 menggunakan versionCode11. Versi yang sudah terpasang tidak menawarkan dirinya sendiri sebagai pembaruan. Untuk rilis berikutnya, naikkan versionCode menjadi angka lebih besar, bangun APK sungguhan dengan keystore yang sama, lalu daftarkan metadata APK itu. Jangan mengubah angka di server agar terlihat baru jika versionCode di dalam APK belum berubah; verifikasi pemasangan akan menolaknya.

Kanal Beta hanya membaca rilis beta. Kanal Stabil hanya membaca stabil. Untuk menarik rilis, simpan kembali version code yang sama dengan status Terbitkan dimatikan; jangan mengganti berkas APK pada tautan lama tanpa memperbarui checksum dan ukuran yang sesuai.

Pengujian SQL lokal: `node tools/test-updates.mjs`. Uji unduhan lengkap, penolakan APK rusak, izin sumber pemasangan dan pemasangan upgrade pada Android tetap perlu dilakukan setelah pemilik menyediakan rilis GitHub nyata.
