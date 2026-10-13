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

    static String read(int code, int a1, int a2) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(DESC);
            data.writeInt(a1);
            data.writeInt(a2);
            boolean ok = svc.transact(code, data, reply, 0);
            int ex = reply.readInt();
            String r = (ex == 0) ? reply.readString() : null;
            data.recycle();
            reply.recycle();
            log("R code=" + code + " (" + a1 + "," + a2 + ") ex=" + ex + " len=" + (r == null ? -1 : r.length()));
            return r;
        } catch (Throwable t) {
            log("R EX: " + t);
            return null;
        }
    }

    static int write(int code, int a1, int a2) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(DESC);
            data.writeInt(a1);
            data.writeInt(a2);
            boolean ok = svc.transact(code, data, reply, 0);
            int ex = reply.readInt();
            int r = (ex == 0) ? reply.readInt() : -999;
            data.recycle();
            reply.recycle();
            log("W code=" + code + " (idx=" + a1 + ", val=" + a2 + ") ok=" + ok + " ex=" + ex + " ret=" + r);
            return r;
        } catch (Throwable t) {
            log("W EX: " + t);
            return -999;
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
                                String before = read(2, 0, 64);
                                log("before_len=" + (before == null ? -1 : before.length()));

                                log("--- idempotent write-back, byte values unchanged ---");
                                write(3, 0, 0);    // idx0  = '0' (0x30) -> write '0'
                                write(3, 1, 0);    // idx1  = '0' -> write '0'
                                write(3, 19, 0);   // idx19 = '0' -> write '0'
                                write(3, 14, 1);   // idx14 = '1' -> write '1'
                                write(3, 18, 1);   // idx18 = '1' -> write '1'
                                write(3, 20, 1);   // idx20 = '1' -> write '1'

                                log("--- read after ---");
                                String after = read(2, 0, 64);
                                log("after_len=" + (after == null ? -1 : after.length()));
                                boolean same = before != null && before.equals(after);
                                log("RESULT identical=" + same);
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