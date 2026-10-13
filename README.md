# nvprobe

PoC for the vulnerability report on TB330FU (Lenovo tablet / MTK mt8786 / Android 13):
unauthenticated system(1000) factory services exposing NVRAM access and enabling ADB.

## What this is

An Android app that talks to two exported, permission-less services running as
`android.uid.system` (uid 1000):

- `com.huaqin.NvService/.FactoryNvramService` (AIDL `aidl.NvAidlService`)
  proxies calls to the root-run MTK NVRAM HAL (`vendor.mediatek.hardware.nvram@1.1::INvram`).
- `com.huaqin.factoryservice/.FactorySetService` on start runs `openAdb()` when a
  NVRAM gate byte is set.

## Modes

Launch with an intent extra `mode`:

- `read`  (default) - VULN A: read NVRAM `PRODUCT_INFO` unauthenticated
- `write` - VULN A: prove write persists (change one byte, read back, restore exactly)
- `chain` - VULN A+B: set the openAdb gate via NVRAM write, then start the system service

```
adb shell am start -n com.probe.nvaidl/.MainActivity --es mode read
adb shell am start -n com.probe.nvaidl/.MainActivity --es mode write
adb shell am start -n com.probe.nvaidl/.MainActivity --es mode chain
adb logcat -s nvprobe
```

## Build

GitHub Actions, see .github/workflows/build.yml. Produces nvprobe.apk + sha256.

```
pm install -r -t nvprobe.apk
```

## Notes

- `write` mode is non-destructive: it changes index 55 to a different value,
  reads it back to prove persistence, then restores the original value exactly.
- `chain` mode triggers `openAdb()` which sets persistent properties
  (`persist.sys.firstboot`, `persist.sys.usb.config`) that cannot be reverted
  from a normal shell.
- Only `PRODUCT_INFO` is reachable; no wifi/display/battery NVRAM items are touched.
