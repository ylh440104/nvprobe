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

    static IBinder svc;

    static void log(String s) {
        Log.i(TAG, s);
        System.out.println("[NVP] " + s);
    }

    static String read(IBinder b, int code, int a1, int a2) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(DESC);
            data.writeInt(a1);
            data.writeInt(a2);
            boolean ok = b.transact(code, data, reply, 0);
            int ex = reply.readInt();
            String r = (ex == 0) ? reply.readString() : null;
            data.recycle();
            reply.recycle();
            log("R code=" + code + " (" + a1 + "," + a2 + ") ok=" + ok + " ex=" + ex
                    + " len=" + (r == null ? -1 : r.length()));
            if (r != null && r.length() > 40) {
                for (int k = 0; k < r.length(); k += 40) {
                    log("  HEX[" + k + "] " + r.substring(k, Math.min(k+40, r.length())));
                }
            } else {
                log("  V " + r);
            }
            return r;
        } catch (Throwable t) {
            log("R EX: " + t);
            return null;
        }
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
                            log("desc=" + safeDesc());
                            log("--- read full raw (read only) ---");
                            read(svc, 2, 0, 0);
                            read(svc, 2, 0, 32);
                            read(svc, 2, 0, 64);
                            read(svc, 2, 0, 96);
                            read(svc, 2, 0, 160);
                            read(svc, 2, 0, 256);
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

    String safeDesc() {
        try { return svc.getInterfaceDescriptor(); } catch (Throwable t) { return "?"; }
    }
}