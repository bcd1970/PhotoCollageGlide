```
"/mnt/c/Windows/System32/cmd.exe" /C "cd C:\Apps\PhotoCollageGlide\PhotoCollageGlideTest && gradlew.bat :DrawToolsSandbox:installDebug -x lint -x test"
"/mnt/c/Users/cozmita/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n com.photocollage.drawsandbox/com.photocollage.glide.drawsandbox.MainActivity
"/mnt/c/Users/cozmita/AppData/Local/Android/Sdk/platform-tools/adb.exe" logcat -s DrawSmooth:D *:S

# Take a screenshot and pull it locally
"/mnt/c/Users/cozmita/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell screencap -p /sdcard/drawsandbox_debug.png
"/mnt/c/Users/cozmita/AppData/Local/Android/Sdk/platform-tools/adb.exe" pull /sdcard/drawsandbox_debug.png PhotoCollageGlideTest/DrawToolsSandbox/build/drawsandbox_debug.png
```
