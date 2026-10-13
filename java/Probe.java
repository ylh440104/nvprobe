import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.Method;

public class Probe {
    static final String PKG = "com.huaqin.NvService";
    static final String CLS = "com.huaqin.NvService.FactoryNvramService";
    static final String DESC = "aidl.NvAidlService";

    static IBinder svc;
    static final Object lock = new Object();

    static void call(IBinder b, int code, int a1, int a2) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(DESC);
            data.writeInt(a1);
            if (code != 1) {
                data.writeInt(a2);
            }
            System.out.println("[*] transact code=" + code + " a1=" + a1 + " a2=" + a2);
            boolean ok = b.transact(code, data, reply, 0);
            System.out.println("    ok=" + ok);
            int ex = reply.readInt();
            System.out.println("    exception=" + ex);
            if (ex == 0) {
                if (code == 3) {
                    System.out.println("    return bool=" + reply.readInt());
                } else {
                    String s = reply.readString();
                    System.out.println("    return string len=" + (s == null ? -1 : s.length())
                            + " : " + (s == null ? "null" : s));
                }
            }
            data.recycle();
            reply.recycle();
        } catch (Throwable t) {
            System.out.println("    EX: " + t);
        }
    }

    static Context getSystemContext() throws Exception {
        Class<?> looper = Class.forName("android.os.Looper");
        Method prepare = looper.getMethod("prepare");
        prepare.invoke(null);
        Class<?> at = Class.forName("android.app.ActivityThread");
        Method systemMain = at.getMethod("systemMain");
        Object thread = systemMain.invoke(null);
        Method getCtx = at.getMethod("getSystemContext");
        return (Context) getCtx.invoke(thread);
    }

    public static void main(String[] args) {
        try {
            Context ctx = getSystemContext();
            System.out.println("[*] ctx=" + ctx);

            Intent it = new Intent();
            it.setComponent(new ComponentName(PKG, CLS));
            it.setAction("com.huaqin.action.factoryNv");

            ServiceConnection conn = new ServiceConnection() {
                public void onServiceConnected(ComponentName n, IBinder b) {
                    System.out.println("[+] connected: " + n + " binder=" + b);
                    svc = b;
                    synchronized (lock) { lock.notifyAll(); }
                }
                public void onServiceDisconnected(ComponentName n) {
                    System.out.println("[-] disconnected: " + n);
                }
            };

            boolean r = ctx.bindService(it, conn, Context.BIND_AUTO_CREATE);
            System.out.println("[*] bindService=" + r);

            Class<?> looper = Class.forName("android.os.Looper");
            Object mainLooper = looper.getMethod("myLooper").invoke(null);
            Method loop = looper.getMethod("loop");
            final Method quit = looper.getMethod("quit");
            final Object fl = mainLooper;

            new Thread() {
                public void run() {
                    try {
                        long d = System.currentTimeMillis() + 8000;
                        while (svc == null && System.currentTimeMillis() < d) {
                            Thread.sleep(200);
                        }
                        Thread.sleep(500);
                        quit.invoke(fl);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }.start();

            System.out.println("[*] entering looper");
            loop.invoke(mainLooper);
            System.out.println("[*] looper done");
            if (svc == null) {
                System.out.println("[-] binder not obtained (timeout)");
                return;
            }

            String desc = svc.getInterfaceDescriptor();
            System.out.println("[*] desc=" + desc);
            call(svc, 1, 0, 0);
            call(svc, 2, 0, 0);
            call(svc, 3, 0, 0);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}