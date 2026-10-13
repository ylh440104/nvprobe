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

    static void call(IBinder b, int code, int a1, int a2) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(DESC);
            data.writeInt(a1);
            if (code != 1) {
                data.writeInt(a2);
            }
            log("transact code=" + code + " a1=" + a1 + " a2=" + a2);
            boolean ok = b.transact(code, data, reply, 0);
            log("    ok=" + ok);
            int ex = reply.readInt();
            log("    exception=" + ex);
            if (ex == 0) {
                if (code == 3) {
                    log("    return bool=" + reply.readInt());
                } else {
                    String s = reply.readString();
                    log("    return len=" + (s == null ? -1 : s.length())
                            + " : " + (s == null ? "null" : s));
                }
            }
            data.recycle();
            reply.recycle();
        } catch (Throwable t) {
            log("    EX: " + t);
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        log("onCreate");
        try {
            log("self pkg=" + getPackageName() + " uid=" + android.os.Process.myUid());
            log("service uid check: " + getPackageManager().getApplicationInfo(PKG, 0).uid);

            Intent it = new Intent();
            it.setComponent(new ComponentName(PKG, CLS));
            it.setAction("com.huaqin.action.factoryNv");

            ServiceConnection conn = new ServiceConnection() {
                public void onServiceConnected(ComponentName n, IBinder bnd) {
                    log("connected: " + n + " binder=" + bnd);
                    svc = bnd;
                    try {
                        log("desc=" + bnd.getInterfaceDescriptor());
                    } catch (Throwable t) {}
                    new Thread() {
                        public void run() {
                            call(svc, 1, 0, 0);
                            call(svc, 2, 0, 0);
                            call(svc, 3, 0, 0);
                            call(svc, 1, 1, 0);
                            call(svc, 2, 0, 32);
                        }
                    }.start();
                }
                public void onServiceDisconnected(ComponentName n) {
                    log("disconnected: " + n);
                }
            };
            boolean r = bindService(it, conn, Context.BIND_AUTO_CREATE);
            log("bindService=" + r);
        } catch (Throwable t) {
            log("EX: " + t);
        }
    }
}