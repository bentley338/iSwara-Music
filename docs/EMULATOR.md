# Menguji APK di emulator Windows

AVD `iswara_api36` dengan image Google APIs Android 16/x86_64 sudah tersedia pada komputer pengembangan. Percobaan rilis 0.5.0 tidak berhasil menjalankan Android: emulator melaporkan driver akselerasi tidak terpasang, sementara dua variasi mode software tetap berstatus ADB offline. Tidak ada fitur sistem atau pengaturan keamanan Windows yang diubah untuk memaksa emulator berjalan.

Untuk menyiapkan komputer:

1. Pastikan virtualisasi CPU aktif di BIOS/UEFI.
2. Ikuti petunjuk resmi Android untuk akselerasi emulator Windows. Pada komputer yang mendukungnya, aktifkan Windows Hypervisor Platform melalui fitur Windows, lalu restart bila diminta. Perubahan ini dilakukan pemilik komputer karena dapat memerlukan hak administrator dan restart.
3. Buka Android Studio → Device Manager → `iswara_api36` → Cold Boot. Tunggu Android selesai boot.
4. Jalankan `adb devices`; lanjutkan hanya jika emulator berstatus `device`, bukan `offline`.
5. Pasang `dist/iSwara-0.5.0.apk` melalui `adb -s emulator-5554 install -r dist/iSwara-0.5.0.apk`.

Untuk uji playback, dari root proyek jalankan:

```powershell
python tools/verify-device.py --serial emulator-5554 --version 0.5.0 --cold --screen-off-seconds 90
```

Perintah tersebut mencari dan memutar musik nyata, mengganti antrean aktif, memeriksa kontrol session serta layar mati, lalu meninggalkan musik dalam keadaan jeda. Gunakan akun uji atau mode tamu. Emulator tanpa keluaran audio tidak membuktikan mutu suara speaker maupun Bluetooth.

Uji kartu lirik dengan pemilih aplikasi dan pemilih berkas Android, widget melalui launcher, serta izin overlay ditolak/diberikan/dicabut. Dua emulator dengan akun berbeda diperlukan untuk uji Denger Bareng dua sesi. Pengujian Robolectric tersedia melalui `:app:testDebugUnitTest`; ini pengujian komponen Android di JVM, terpisah dari menjalankan APK pada emulator penuh.

Rujukan: [akselerasi emulator Android di Windows](https://developer.android.com/studio/run/emulator-acceleration#vm-windows).
