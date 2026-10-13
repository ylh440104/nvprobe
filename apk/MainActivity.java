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

    static String read(int a1, int a2) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(DESC);
            data.writeInt(a1);
            data.writeInt(a2);
            boolean ok = svc.transact(2, data, reply, 0);
            int ex = reply.readInt();
            String r = (ex == 0) ? reply.readString() : null;
            data.recycle();
            reply.recycle();
            log("R (" + a1 + "," + a2 + ") ex=" + ex + " len=" + (r == null ? -1 : r.length()));
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
                            try {
                                log("desc=" + svc.getInterfaceDescriptor());
                                // read full 1024-byte item: raw hex is 2048 chars
                                for (int s = 0; s < 1024; s += 128) {
                                    String r = read(s, s + 128);
                                    if (r == null) continue;
                                    for (int k = 0; k < r.length(); k += 64) {
                                        log("H[" + s + "+" + k + "] " + r.substring(k, Math.min(k + 64, r.length())));
                                    }
                                }
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