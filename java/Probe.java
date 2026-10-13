import android.app.ActivityThread;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;

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
            if (code == 1) {
                data.writeInt(a1);
            } else {
                data.writeInt(a1);
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
                            + " : " + (s == null ? "null" : (s.length() > 120 ? s.substring(0, 120) : s)));
                }
            }
            data.recycle();
            reply.recycle();
        } catch (Throwable t) {
            System.out.println("    EX: " + t);
        }
    }

    public static void main(String[] args) {
        try {
            ActivityThread at = ActivityThread.systemMain();
            Context ctx = at.getSystemContext();
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

            long deadline = System.currentTimeMillis() + 8000;
            synchronized (lock) {
                while (svc == null && System.currentTimeMillis() < deadline) {
                    lock.wait(500);
                }
            }
            if (svc == null) {
                System.out.println("[-] binder not obtained (timeout)");
                return;
            }

            System.out.println("[*] desc=" + svc.getInterfaceDescriptor());
            call(svc, 1, 0, 0);
            call(svc, 2, 0, 0);
            call(svc, 3, 0, 0);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}