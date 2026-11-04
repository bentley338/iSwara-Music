# Menyiapkan pengenalan lagu iSwara

1. Jalankan `supabase/migrations/202611040006_recognition_quota.sql` di Supabase SQL Editor.
2. Simpan token operator AudD sebagai `AUDD_API_TOKEN` di **Edge Functions → Secrets**. Jangan simpan di APK, repo, atau `secrets.properties` Android.
3. Deploy `supabase/functions/recognize-song/index.ts` dengan nama `recognize-song`. Aplikasi memanggil fungsi dengan kunci publik proyek, tanpa akun iSwara. Fungsi meneruskan cuplikan ke AudD dari server. Fungsi kuota hanya boleh dipanggil service role.
4. `HEAD /functions/v1/recognize-song` dengan kunci publik proyek harus memberi `204` jika konfigurasi tersedia. Jalankan `node tools/test-recognition.mjs` dari root untuk menguji validasi audio dan pembatasan kuota tanpa memakai token atau kuota produksi.

Batas awal: 20 permintaan per hari dan 200 per bulan untuk seluruh proyek. Slot jaringan berbagi batas dua permintaan per menit dan delapan per hari. Sesuaikan batas global dengan paket dan kuota operator sebelum memperbesar distribusi.

Untuk menguji di HP: buka **Cari → Kenali lagu**. Coba satu file audio, mikrofon dengan musik dari perangkat lain, dan **Audio perangkat** sambil memutar video di aplikasi lain. Untuk TikTok, pilih **Bagikan → iSwara Music → Kenali dari TikTok**. Audio perangkat bergantung pada izin Android dan kebijakan aplikasi sumber. Jika hasilnya kosong, ulangi dari bagian lagu yang jelas atau gunakan sumber audio lain.
