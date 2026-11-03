# iSwara Music 0.6.0: Studio suara dan ekspor lirik

Pasang sebagai pembaruan tanpa uninstall. Koleksi dan akun tetap memakai penyimpanan sebelumnya. Model AI serta runtime dibundel agar pemisahan tidak membutuhkan server atau pemasangan model terpisah; ukuran APK lebih besar dari 0.5.0.

## Pemutar tanpa scroll untuk kontrol utama

Layar Pemutar memakai susunan tetap: informasi lagu, seek, shuffle, sebelumnya, putar/jeda, berikutnya, repeat, timer, equalizer, Studio suara, mode berkendara, dan opsi lagu. Ukuran sampul mengikuti ruang tersisa. Pada layar pendek dengan teks besar, sampul/preset ringkas dikurangi agar kontrol utama tetap bisa dijangkau. Setelan lengkap berada di dialog masing-masing; daftar antrean dan lirik tetap dapat digulir.

## Instant Remix

Tekan Slowed + Reverb atau Nightcore langsung dari pemutar, atau buka ikon Studio suara. Menekan preset aktif kembali mematikannya.

- **Slowed + Reverb:** tempo 0,85×, pitch dasar 0,9×, efek hall dan tambahan bass melalui pemrosesan PCM aplikasi. Efek ini tidak memerlukan dukungan reverb vendor.
- **Nightcore / Speed Up:** tempo 1,25× dan pitch dasar naik dua semitone.
- **Nada dasar:** turunkan/naikkan sampai 12 semitone tanpa mengubah tempo. Nilai ini ditambahkan ke pitch preset. Satu semitone adalah setengah nada.
- **Reset studio:** mematikan remix, mengembalikan pengatur nada dan tempo, serta memilih audio asli. Pengaturan equalizer manual tetap diatur melalui equalizer.

Preset diterapkan saat pemutaran, bukan mengunduh versi remix. Efek yang sedang terdengar tidak diekspor oleh fitur potongan audio/video lirik; ekspor menggunakan audio asli sumber. Di room Denger Bareng, remix/pengatur nada dan pemutaran hasil AI dinonaktifkan agar timeline mengikuti host. Pengaturan remix pribadi dipulihkan setelah keluar room.

## Karaoke AI: vokal dan instrumen

1. Putar lagu lalu buka Studio suara → Karaoke AI.
2. Tekan **Pisahkan vokal & musik** dan **Mulai proses**. Audio asli tetap berjalan.
3. Pantau progres di Studio/notifikasi. Proses dapat dibatalkan. Jika belum diunduh, audio sumber akan diambil terlebih dahulu.
4. Setelah selesai, pilih **Tanpa vokal**, **Vokal saja**, atau **Asli**. Posisi putar dipertahankan ketika mengganti versi.
5. **Hapus hasil AI lagu ini** membersihkan kedua versi dari perangkat; **Proses ulang** membuat hasil pengganti.

Model Spleeter 2-stems berjalan lokal melalui ONNX Runtime, tidak mengunggah audio. Pemrosesan memakai potongan bertumpang tindih agar tidak memuat seluruh lagu ke RAM. Ini pemisahan AI sungguhan, bukan pembatalan stereo tengah. Hasil dapat memiliki suara tersisa/artefak; tidak dijanjikan vokal hilang sempurna atau proses seketika.

Batas awal: lagu maksimal 8 menit, masukan audio maksimal 64 MB, ruang kosong sedikitnya 350 MB. Perangkat low-RAM atau dengan RAM kosong kurang dari 384 MB ditolak dengan pesan yang jelas. Lama proses bergantung perangkat, format, durasi, dan aktivitas lain. Data tamu/akun dipisahkan. Hasil AI terenkripsi dan disimpan lokal, tidak masuk cloud/backup koleksi. Audio sementara dibersihkan setelah proses; folder kerja yang tertinggal akibat proses aplikasi mati dibersihkan setelah berusia lebih dari 24 jam pada pembukaan berikutnya.

Kecepatan/pitch dan hasil pemisahan perlu didengarkan pada perangkat/keluaran audio yang dipakai. Bila hasil AI tidak dapat diputar, aplikasi berusaha kembali ke audio asli. Pilih Asli atau Reset studio bila ingin memulihkan pilihan sendiri.

## Kartu lirik: pilih banyak baris

Pemutar → Lirik → **Kartu lirik**. Pilih satu atau banyak baris, atau **Pilih semua**. Tidak ada lagi batas empat baris. Teks panjang dibagi ke beberapa kartu sesuai ruang gambar; satu baris yang terlalu panjang dilanjutkan ke halaman berikutnya. Gunakan nomor halaman untuk memeriksa dan membagikan/menyimpan kartu tertentu, atau **Bagikan semua gambar** untuk membuka pemilih aplikasi dengan seluruh halaman.

Format PNG: Story 1080×1920 dan Post 1080×1350; pilihan Charcoal, Kertas, dan Sampul. PNG berisi gambar/teks, tidak memiliki suara. Jumlah gambar yang bisa diterima sekaligus bergantung aplikasi penerima dan kapasitas penyimpanan perangkat.

## Audio dan video lirik bersuara

Di Studio kartu lirik, pilih **Audio** atau **Video + audio**:

1. Geser **Awal potongan**, lalu pilih 15, 30, atau 60 detik. Durasi dipotong bila mencapai akhir lagu.
2. Audio menghasilkan M4A/AAC stereo; video menghasilkan MP4/H.264 vertikal 720×1280, 24 fps dengan audio AAC.
3. Pada video, **Lirik mengikuti audio** memakai timestamp per baris yang tersedia. Matikan untuk teks diam. Lirik tanpa timestamp memakai teks diam; aplikasi tidak membuat timing kata palsu.
4. Tekan **Buat potongan audio** atau **Buat video lirik**. Tunggu progres selesai atau batalkan melalui dialog/notifikasi.
5. Tekan **Bagikan** untuk memilih aplikasi penerima, atau **Simpan** untuk memilih lokasi berkas Android.

Ekspor memakai audio asli, termasuk bila pemutar sedang memakai remix atau hasil AI. Potongan maksimal 60 detik per ekspor; audio sumber dibaca dengan pipeline yang sama dan saat ini mengikuti batas lagu 8 menit. Koreksi waktu lirik yang dipilih di pemutar ikut diterapkan. Pada video bertimestamp, baris yang masuk dalam rentang potongan mengikuti posisinya di audio. Untuk teks diam, gunakan pilihan halaman yang ingin ditampilkan.

Pemrosesan audio/video dikerjakan di perangkat, dapat mengambil audio dari jaringan, dan memerlukan ruang sementara. Pemisahan AI serta ekspor memakai antrean pemrosesan yang sama agar tidak menjalankan dua proses audio berat bersamaan. Tidak ada pengunggahan atau publikasi otomatis. Penggantian bagian/durasi membuat hasil lama tidak langsung dianggap sesuai pilihan baru.

Encoder audio/video bergantung dukungan perangkat. Pengujian rilis ini memeriksa renderer, logika, model, dan kompilasi; penerimaan MP4/M4A serta proses codec penuh tetap memerlukan pengujian Android nyata. Bila perangkat menolak encoder, dialog menampilkan kegagalan dan tidak membagikan berkas parsial.

Rincian hasil dan keterbatasan: [VERIFICATION.md](VERIFICATION.md). Daftar pekerjaan berikutnya: [LANJUTKAN.md](LANJUTKAN.md).

## Sumber model dan lisensi

[Deezer Spleeter](https://github.com/deezer/spleeter) menyediakan pemisahan dua stem berlisensi MIT. Model ONNX berasal dari konversi [sherpa-onnx](https://k2-fsa.github.io/sherpa/onnx/source-separation/models.html). Bobot dipin dengan SHA-256, dipakai pada jendela 128 frame dengan bobot terlatih yang tetap sama. Alat reproduksi: `tools/prepare-stem-models.py` (Python + onnx 1.23.2). Manifest model dan lisensi disertakan dalam aset/sumber. Runtime: [ONNX Runtime Java/Android](https://onnxruntime.ai/docs/get-started/with-java.html) 1.24.3, MIT.
