package com.focuslock.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

/** A DNS-only local VPN. Normal app traffic never enters this tunnel. */
public class FamilyDnsVpnService extends VpnService {
    public static final String ACTION_STOP = "com.focuslock.app.STOP_FAMILY_DNS";
    private static final String CHANNEL = "adult_protection";
    private static final byte[] VPN_DNS = new byte[]{10, 77, 0, 2};
    private volatile boolean running;
    private static volatile boolean active;
    private ParcelFileDescriptor tunnel;
    private Thread worker;

    public static boolean isActive() { return active; }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_STICKY;
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("FocusLock Adult Protection")
                .setContentText("DNS-only protection is active")
                .setContentIntent(pending).setOngoing(true).build();
        startForeground(8, notification);
        try {
            tunnel = new Builder()
                    .setSession("FocusLock Adult Protection")
                    .setMtu(1500)
                    .addAddress("10.77.0.1", 32)
                    .addDnsServer("10.77.0.2")
                    .addRoute("10.77.0.2", 32)
                    .establish();
            if (tunnel == null) { stopSelf(); return START_NOT_STICKY; }
            running = true;
            active = true;
            worker = new Thread(this::dnsLoop, "FocusLock-DNS");
            worker.start();
            return START_STICKY;
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private void dnsLoop() {
        try (FileInputStream input = new FileInputStream(tunnel.getFileDescriptor());
             FileOutputStream output = new FileOutputStream(tunnel.getFileDescriptor())) {
            byte[] packet = new byte[32767];
            while (running) {
                int length = input.read(packet);
                if (length < 28 || (packet[0] >> 4) != 4) continue;
                int ipHeader = (packet[0] & 0x0F) * 4;
                if (ipHeader < 20 || length < ipHeader + 8 || (packet[9] & 0xFF) != 17) continue;
                int udpLength = unsignedShort(packet, ipHeader + 4);
                int dnsLength = Math.min(length - ipHeader - 8, udpLength - 8);
                if (dnsLength < 12) continue;
                byte[] query = Arrays.copyOfRange(packet, ipHeader + 8, ipHeader + 8 + dnsLength);
                byte[] answer = resolve(query);
                if (answer == null) continue;
                byte[] response = makeResponse(packet, ipHeader, answer);
                output.write(response);
            }
        } catch (Exception ignored) {
        } finally {
            stopSelf();
        }
    }

    private byte[] resolve(byte[] query) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            protect(socket);
            socket.setSoTimeout(4500);
            DatagramPacket outbound = new DatagramPacket(query, query.length, InetAddress.getByName("1.1.1.3"), 53);
            socket.send(outbound);
            byte[] response = new byte[4096];
            DatagramPacket inbound = new DatagramPacket(response, response.length);
            socket.receive(inbound);
            return Arrays.copyOf(response, inbound.getLength());
        } catch (Exception e) {
            return null;
        } finally {
            if (socket != null) socket.close();
        }
    }

    private byte[] makeResponse(byte[] request, int oldIpHeader, byte[] dns) {
        int total = 20 + 8 + dns.length;
        byte[] response = new byte[total];
        response[0] = 0x45;
        response[1] = 0;
        putShort(response, 2, total);
        response[4] = request[4]; response[5] = request[5];
        response[6] = 0x40; response[7] = 0;
        response[8] = 64; response[9] = 17;
        System.arraycopy(VPN_DNS, 0, response, 12, 4);
        System.arraycopy(request, 12, response, 16, 4);
        int originalSourcePort = unsignedShort(request, oldIpHeader);
        putShort(response, 20, 53);
        putShort(response, 22, originalSourcePort);
        putShort(response, 24, 8 + dns.length);
        putShort(response, 26, 0); // Valid for UDP over IPv4.
        System.arraycopy(dns, 0, response, 28, dns.length);
        putShort(response, 10, checksum(response, 0, 20));
        return response;
    }

    private int checksum(byte[] data, int offset, int length) {
        long sum = 0;
        for (int i = offset; i < offset + length; i += 2) {
            int value = (data[i] & 0xFF) << 8;
            if (i + 1 < offset + length) value |= data[i + 1] & 0xFF;
            sum += value;
            while ((sum & 0xFFFF0000L) != 0) sum = (sum & 0xFFFF) + (sum >>> 16);
        }
        return (int) (~sum) & 0xFFFF;
    }

    private int unsignedShort(byte[] bytes, int offset) { return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF); }
    private void putShort(byte[] bytes, int offset, int value) { bytes[offset] = (byte) (value >> 8); bytes[offset + 1] = (byte) value; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Adult protection", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Required while FocusLock DNS protection is active");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() {
        running = false;
        active = false;
        if (worker != null) worker.interrupt();
        try { if (tunnel != null) tunnel.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return super.onBind(intent); }
}
