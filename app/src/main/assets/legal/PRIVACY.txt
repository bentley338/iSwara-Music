# Privasi iSwara

Aplikasi tidak menyertakan SDK iklan atau analitik.

Pencarian musik, sampul, dan pemutaran terhubung langsung ke YouTube/YouTube Music serta server medianya. Layanan tersebut menerima alamat jaringan perangkat dan metadata permintaan. Permintaan lirik mengirim judul lagu, artis, dan durasi yang tersedia ke LRCLIB dan, bila perlu atau pada pencarian manual, KuGou.

Favorit, playlist, riwayat dengar, riwayat pencarian, dan pengaturan pemutaran disimpan di perangkat. Riwayat pencarian, antrean pemutaran, dan pengaturan tampilan tidak diunggah oleh sinkronisasi cloud. Ketika pengguna masuk, metadata playlist, favorit, dan riwayat dengar disinkronkan ke proyek Supabase yang dikonfigurasi. Berkas audio tidak diunggah.

Izin musik lokal memungkinkan aplikasi menampilkan audio di perangkat. Pemilih berkas sistem memberikan akses ke berkas yang dipilih pengguna. Izin notifikasi mendukung kontrol pemutaran. Layanan media mendukung pemutaran di latar belakang yang dimulai pengguna.

Sesi akun dienkripsi dengan AES-GCM menggunakan kunci Android Keystore. Aplikasi tidak menyimpan password akun. Token sesi dan URL stream mentah tidak sengaja ditulis ke log aplikasi. Backup Android untuk data pribadi aplikasi dinonaktifkan.

Data tamu dan masing-masing akun memiliki ruang penyimpanan lokal terpisah. Keluar dari akun mempertahankan cache akun tersebut untuk login berikutnya pada instalasi yang sama, tanpa menampilkannya di mode tamu. Penghapusan akun menghapus metadata cloud dan cache koleksi lokalnya. Menghapus aplikasi menghapus penyimpanan privat aplikasi serta izin berkasnya.

Ekspor JSON memuat metadata koleksi dan referensi berkas lokal dalam bentuk yang dapat dibaca. Simpan dan bagikan backup dengan sengaja. Playlist hasil impor menggunakan ID baru agar tidak menimpa playlist yang sudah ada.

URL audio disimpan sementara di memori. Persiapan lagu berikutnya juga dapat mengambil paling banyak 128 KB awal audio ke RAM sebelum lagu diputar; dua awalan dapat tersimpan, dengan masa berlaku dua menit. Cache ini dipisahkan dari unduhan offline dan dibersihkan saat akun atau kualitas berubah. Pilihan ini dapat dimatikan di Setelan dan otomatis dinonaktifkan pada mode hemat data.

Sebelum distribusi publik dengan cloud aktif, pengelola perlu menerbitkan kontak pengelola, wilayah layanan yang digunakan, dan kebijakan penyimpanan data yang berlaku. Dokumen ini tidak mencantumkan alamat pengelola fiktif.

Kebijakan layanan eksternal:

- [Ketentuan YouTube](https://www.youtube.com/t/terms)
- [Privasi Google](https://policies.google.com/privacy)
- [Privasi Supabase](https://supabase.com/privacy)
- [LRCLIB](https://lrclib.net)
- [KuGou](https://www.kugou.com)

Unduhan audio disimpan terenkripsi AES-GCM di penyimpanan privat aplikasi dan dipisahkan per akun. Kuncinya berada di Android Keystore. Berkas ini tidak termasuk backup JSON atau sinkronisasi cloud. Unduhan yang selesai dapat diputar tanpa koneksi; penghapusan akun juga membersihkan unduhan milik akun tersebut.

Rekap dengar menghitung waktu pemutaran pada perangkat dan menyimpan ringkasan harian di Room secara lokal per akun, dengan retensi hingga dua tahun. Rekap dapat dihapus dari layar Rekap dengar dan tidak disinkronkan ke cloud. Waktu sekitar beberapa detik terakhir dapat hilang bila proses aplikasi dihentikan mendadak.

Host media yang dipercaya Android, termasuk host mobil yang diberi akses, dapat membaca koleksi akun aktif melalui layanan media aplikasi dan mengontrol pemutaran. Pergantian akun memperbarui katalog layanan tersebut. Perangkat mobil atau aplikasi host dapat memiliki cache tampilannya sendiri.

Tombol Salin detail error menyalin versi aplikasi, versi Android, merek/model perangkat, kode error, serta nama kelas dan lokasi kode ke clipboard. Laporan ini tidak menyertakan pesan exception, URL stream, judul lagu, email, atau token akun. Tidak ada pengiriman otomatis laporan error.

Kartu lirik dibuat pada perangkat. Pilihan bagikan memberikan akses sementara ke satu berkas PNG melalui FileProvider kepada aplikasi yang dipilih pengguna. Tombol terjemahan membuka Google Translate di browser dan mengirim teks lirik yang dipilih ke layanan tersebut. Lirik melayang memakai izin tampil di atas aplikasi lain yang diaktifkan pengguna melalui pengaturan Android.

Antrean tersimpan, cache lirik, pilihan lirik manual, dan LRC impor disimpan lokal per akun. Cache otomatis lirik dibatasi 100 lagu dengan masa berlaku 30 hari; pilihan manual dipertahankan sampai direset atau akun dihapus. Sampul diproses pada perangkat untuk mengambil palet warna, tanpa dikirim ke layanan analisis gambar.

Jika layanan admin diaktifkan pengelola, konten Beranda yang diterbitkan dibaca melalui Supabase. Admin yang diberi peran server dapat melihat email dan status akun pengguna, ringkasan jumlah dokumen, serta menangguhkan, memulihkan, atau menghapus akun. Tindakan dicatat dalam audit server. Password dan token sesi pengguna tidak ditampilkan di panel admin.


Denger bareng menyimpan metadata antrean streaming, posisi, status putar, ID host, dan keanggotaan room di Supabase. Anggota room dapat membaca data room; kode adalah undangan. Audio diambil setiap perangkat, bukan diunggah host. Room berlaku 6 jam. Data yang sudah kedaluwarsa lebih dari 24 jam dibersihkan ketika room baru dibuat. Keluar/pindah akun menghentikan sesi room perangkat.

Ekspor audit admin menghasilkan CSV melalui lokasi yang dipilih pengguna dan dapat memuat UID serta rincian tindakan. Berkas kartu lirik berada di cache privat; kartu lebih lama dari 24 jam dibersihkan saat kartu berikutnya dibuat. Menyimpan atau membagikan PNG membuat salinan di lokasi/aplikasi pilihan pengguna. Posisi dan preferensi overlay tersimpan lokal.


Studio suara menerapkan perubahan tempo, pitch, hall, dan bass pada perangkat. Pemisahan AI memakai model Spleeter yang dibundel dalam APK, tanpa mengirim audio ke layanan AI. Jika audio sumber belum tersedia, aplikasi mengambilnya dari sumber musik. Hasil vokal/instrumen disimpan terenkripsi AES-GCM per blok, dipisahkan per akun, dan tidak disinkronkan ke cloud. Penghapusan akun membersihkan hasilnya. Berkas kerja plaintext berada pada cache privat dan dibersihkan setelah proses; folder tertinggal akibat proses mati dibersihkan setelah berusia lebih dari 24 jam saat aplikasi dibuka kembali.

Ekspor gambar/audio/video lirik dibuat pada perangkat. Potongan audio M4A dan video MP4 memakai audio asli sumber. Hasil ekspor berada di cache privat sampai pengguna menyimpan atau membagikannya melalui pemilih Android; salinan yang disimpan/dikirim pengguna berada di luar pengelolaan cache aplikasi. Pindah akun membatalkan pekerjaan ekspor; penghapusan akun membersihkan cache ekspor yang terikat akun. Cache ekspor lebih dari 24 jam dibersihkan saat aplikasi berikutnya dibuka. Izin foreground dataSync mendukung notifikasi progres dan pembatalan proses yang dimulai pengguna.


Radio: nama siaran, nama tampilan host, suasana, jumlah anggota, dan metadata lagu siaran publik dapat dibaca tanpa login. Pesan, reaksi, keanggotaan, dan identitas pengirim hanya dapat dibaca anggota yang berhak serta moderator server. Nama tampilan dan komentar disimpan di Supabase. Host/admin dapat menghapus pesan dan memblokir anggota siaran. Riwayat tampilan dibatasi; bukti operasi idempoten tetap disimpan sampai room dibersihkan. Room aktif maksimal 6 jam. Jangan membagikan informasi pribadi di lounge.

Lirik pintar: romanisasi Jepang/Korea/Mandarin dihitung lokal menggunakan Kuromoji/ICU. Mengaktifkan terjemahan meminta persetujuan untuk mengirim teks lirik, bahasa asal, bahasa tujuan, dan alamat IP jaringan ke Google Translate, termasuk lagu berikutnya selama fitur aktif. Arti kata hanya diminta melalui tombol pengguna. Pilihan Wi-Fi membatasi pengiriman. Mematikan terjemahan membatalkan permintaan lirik yang sedang berjalan; data yang sudah dikirim tetap mengikuti kebijakan Google. Terjemahan mesin dan cara baca dapat keliru sesuai konteks. Tidak mengirim email akun, token Supabase, atau berkas audio kepada layanan terjemahan.

Sumber cadangan mencari metadata judul/artis dari penyedia katalog dan mengambil audio yang tersedia. Pilihan manual disimpan lokal per akun/perangkat, tanpa mengganti identitas lagu di playlist atau backup. Penggunaan sumber eksternal mengikuti kebijakan privasi masing-masing penyedia. Efek analog/vinyl/ruang headphone diproses lokal.


Pembaruan aplikasi: pemeriksaan versi mengirim version code aplikasi, SDK Android dan kanal ke endpoint Supabase publik. APK diunduh dari GitHub Releases setelah tindakan pengguna; GitHub menerima alamat IP dan informasi permintaan jaringan. Aplikasi memeriksa hash, identitas dan sertifikat APK sebelum membuka installer Android. Berkas sementara tersimpan privat, dapat dibatalkan dan tidak menjadi bagian backup koleksi. Notifikasi mengikuti izin Android. Tidak ada instalasi diam-diam atau pengunggahan koleksi untuk pengecekan versi.

Impor playlist membaca metadata tautan publik Spotify, SoundCloud, Deezer atau sumber musik, atau berkas CSV/JSON/M3U/TXT pilihan pengguna. Judul dan artis dikirim ke sumber pencarian untuk pencocokan. Audio lokal tidak diunggah. Pratinjau berada di memori per akun; hanya hasil yang dipilih disimpan ke koleksi dan mengikuti sinkronisasi akun. Login/cookie platform lain tidak diambil. Rekomendasi antrean memakai metadata terkait, riwayat dan favorit; tidak mengunggah audio ke model AI.

Lirik tambahan dapat diminta dari music.163.com (NetEase), dengan judul/artis dan pengenal lagu untuk pencarian serta pengambilan teks. Terjemahan mendukung bahasa tujuan pilihan pengguna. Cache hasil terjemahan berada di memori, dibatasi24 konteks lagu/bahasa, dan dibersihkan saat akun berubah. Teks asli tetap tersedia untuk membandingkan hasil mesin.
