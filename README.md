# nvprobe

probe for huaqin NvService exported AIDL service

target: com.huaqin.NvService / FactoryNvramService (uid 1000)
interface: aidl.NvAidlService
methods: getTestFlagFromNvram, getTestValueFromNvram, setTestFlagFromNvram

build via GitHub Actions, output probe.dex, run with app_process