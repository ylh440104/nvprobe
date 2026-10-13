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
            log("R EX: " + t);
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

    // read raw byte at index i
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
                                int T = 55;   // target index (2nd digit of product code "11")
                                log("desc=" + svc.getInterfaceDescriptor());

                                // STEP0 backup
                                String fullBefore = readHex(0, 1024);
                                log("BACKUP len=" + (fullBefore == null ? -1 : fullBefore.length()));
                                log("BACKUP head=" + (fullBefore == null ? "null"
                                        : fullBefore.substring(0, 128)));
                                int v0 = byteAt(T);
                                log("TARGET idx=" + T + " orig=0x" + Integer.toHexString(v0)
                                        + " ('" + (char) v0 + "')");

                                // STEP1 write NEW value (orig '1' -> '2')
                                int newSel = (v0 == '1') ? 2 : (v0 == '2' ? 1 : 0);
                                log("STEP1 write idx=" + T + " -> selector " + newSel);
                                write(T, newSel);

                                int v1 = byteAt(T);
                                log("VERIFY1 idx=" + T + " = 0x" + Integer.toHexString(v1)
                                        + " ('" + (char) v1 + "')  changed=" + (v1 != v0));

                                // also confirm neighbors + serial intact
                                log("NEIGHBOR idx54=0x" + Integer.toHexString(byteAt(54))
                                        + " idx56=0x" + Integer.toHexString(byteAt(56)));
                                log("SERIAL head=" + readHex(1, 15));

                                // STEP2 restore original value
                                int backSel = (v0 == '1') ? 1 : (v0 == '2' ? 2 : 0);
                                log("STEP2 restore idx=" + T + " -> selector " + backSel);
                                write(T, backSel);

                                int v2 = byteAt(T);
                                log("VERIFY2 idx=" + T + " = 0x" + Integer.toHexString(v2)
                                        + " ('" + (char) v2 + "')  restored=" + (v2 == v0));

                                String fullAfter = readHex(0, 1024);
                                boolean identical = fullBefore != null && fullBefore.equals(fullAfter);
                                log("FINAL full-image identical=" + identical);
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