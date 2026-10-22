package com.scout.control;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.LinkProperties;
import android.net.LinkAddress;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONObject;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.io.IOException;
import android.util.Log;

/** One serialized worker; the only drive sample retained is SessionState's latest pair. */
final class ScoutClient {
    interface Listener { void changed(); void calibration(boolean left, boolean right); }
    final SessionState state = new SessionState();
    final FaceSelection faces = new FaceSelection();
    private final ConnectivityManager connectivity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final Thread worker;
    private volatile Network wifi;
    private ConnectivityManager.NetworkCallback wifiRequest;
    private boolean closed, inspectPending, configPending, busy;
    private boolean configLeft, configRight;
    private long boot;
    private long nextDrive;
    private DatagramSocket udp; // Owned only by the serialized worker.
    private long lastAck, lastAckSequence;

    ScoutClient(Context context, Listener listener) {
        this.listener = listener;
        connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        worker = new Thread(this::loop, "Scout local Wi-Fi"); worker.start();
        new Thread(this::faceLoop, "Scout face controls").start();
    }
    void connect() {
        prepareWifi(false);
    }
    private void prepareWifi(boolean calibration) {
        long epoch;
        synchronized (state) {
            if (closed || busy || state.mode == SessionState.ACTIVE || state.mode == SessionState.CONNECTING || state.mode == SessionState.WAITING_WIFI) return;
            busy = calibration;
            epoch = state.awaitWifi(SystemClock.elapsedRealtime());
        }
        changed();
        if (wifi != null && connectivity.getNetworkCapabilities(wifi) != null) {
            wifiAvailable(epoch, wifi, calibration); return;
        }
        releaseWifi(epoch);
        if (Build.VERSION.SDK_INT < 29) {
            try { wifiAvailable(epoch, findWifi(), calibration); }
            catch (Exception e) { wifiUnavailable(epoch, "Join C3-Scout-Test in Wi-Fi settings"); }
            return;
        }
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                long availableEpoch; boolean begin;
                synchronized (state) {
                    if (wifiRequest != this) return;
                    availableEpoch = state.epoch; begin = state.mode == SessionState.WAITING_WIFI;
                    if (!begin) wifi = network; // Restore routing only; never resume an ended drive.
                }
                if (begin) wifiAvailable(availableEpoch, network, calibration);
            }
            @Override public void onUnavailable() {
                synchronized (state) { if (wifiRequest != this) return; }
                wifiUnavailable(epoch, "Scout Wi-Fi connection was cancelled or unavailable"); releaseWifi(this);
            }
            @Override public void onLost(Network network) {
                synchronized (state) {
                    if (wifiRequest != this || !network.equals(wifi)) return;
                    wifi = null;
                    if (state.mode == SessionState.CONNECTING) {
                        // Android 10 may replace a manual connection with an app-owned
                        // network during pairing. Retry only this still-neutral setup.
                        state.end("Scout Wi-Fi is changing"); state.awaitWifi(SystemClock.elapsedRealtime());
                    } else state.fail(state.epoch, "Scout Wi-Fi lost · reconnect when ready");
                    busy = calibration && state.mode == SessionState.WAITING_WIFI;
                }
                // Keep the app's network request alive while foreground. Recovery
                // restores only routing; an active drive that lost Wi-Fi stays ended.
                changed();
            }
        };
        synchronized (state) { if (state.epoch != epoch) return; wifiRequest = callback; }
        try {
            NetworkRequest request = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(new WifiNetworkSpecifier.Builder().setSsid("C3-Scout-Test").setWpa2Passphrase("ScoutTest42").build()).build();
            // Android's approval dialog may take as long as the user needs.
            // WAITING_WIFI is neutral; Stop or leaving the app cancels it.
            connectivity.requestNetwork(request, callback);
        } catch (Exception e) {
            wifiUnavailable(epoch, "Allow Wi-Fi access and enable Location to connect to Scout"); releaseWifi(callback);
        }
    }
    private void wifiAvailable(long epoch, Network network, boolean calibration) {
        synchronized (state) {
            if (!state.wifiReady(epoch, SystemClock.elapsedRealtime())) return;
            wifi = network;
            if (calibration) { state.connectPending = false; state.mode = SessionState.OFF; inspectPending = true; state.message = "Reading motor directions…"; state.notifyAll(); }
        }
        changed();
    }
    private void wifiUnavailable(long epoch, String message) {
        synchronized (state) { if (state.epoch != epoch) return; state.fail(epoch, message); busy = false; }
        changed();
    }
    private void releaseWifi(long expectedEpoch) {
        ConnectivityManager.NetworkCallback callback;
        synchronized (state) {
            if (state.epoch != expectedEpoch) return;
            callback = wifiRequest; wifiRequest = null; wifi = null;
        }
        unregisterWifi(callback);
    }
    private void releaseWifi(ConnectivityManager.NetworkCallback expectedCallback) {
        synchronized (state) {
            if (wifiRequest != expectedCallback) return;
            wifiRequest = null; wifi = null;
        }
        unregisterWifi(expectedCallback);
    }
    private void unregisterWifi(ConnectivityManager.NetworkCallback callback) {
        if (callback != null) { try { connectivity.unregisterNetworkCallback(callback); } catch (IllegalArgumentException ignored) { } }
    }
    void leave(String reason) {
        synchronized (state) { state.leave(reason); faces.clear(); inspectPending = configPending = false; busy = false; }
        changed();
    }
    void stop(String reason) {
        ConnectivityManager.NetworkCallback cancelled = null;
        synchronized (state) {
            boolean pending = state.mode == SessionState.WAITING_WIFI; state.end(reason);
            faces.clear();
            if (pending) { busy = false; cancelled = wifiRequest; wifiRequest = null; wifi = null; }
        }
        unregisterWifi(cancelled); changed();
    }
    void input(float forward, float turn, boolean touching) { state.input(forward, turn, touching, SystemClock.elapsedRealtime()); changed(); }
    void speed(int cap) { state.speed(cap); changed(); }
    boolean busy() { synchronized (state) { return busy; } }
    void inspectCalibration() {
        prepareWifi(true);
    }
    void configure(boolean left, boolean right) {
        synchronized (state) {
            if (closed || busy || state.mode == SessionState.ACTIVE || state.mode == SessionState.CONNECTING) return;
            configLeft = left; configRight = right; busy = true; configPending = true;
            state.message = "Saving motor directions…"; state.notifyAll();
        }
        changed();
    }
    void close() { synchronized (state) { closed = true; faces.clear(); state.leave("Controller closed"); state.notifyAll(); } }
    void selectFace(int id) {
        synchronized (state) {
            if (closed || !state.active() || !faces.queue(id)) return;
            state.notifyAll();
        }
        changed();
    }
    // Face HTTP can take hundreds of milliseconds. Never do it on the UDP
    // worker, and never let a face reply update drive acknowledgments or leases.
    private void faceLoop() {
        while (true) {
            FaceSelection.Ticket ticket; Network network;
            synchronized (state) {
                if (closed) return;
                if (!state.active()) faces.pending = false;
                ticket = state.active() ? faces.take(state.epoch, state.token) : null;
                network = wifi;
                if (ticket == null) {
                    try { state.wait(100); } catch (InterruptedException ignored) { }
                    continue;
                }
            }
            try {
                if (network == null) throw new IOException("Scout Wi-Fi unavailable");
                JSONObject reply;
                try (Socket socket = network.getSocketFactory().createSocket()) {
                    socket.connect(new InetSocketAddress("192.168.4.1", 80), 600);
                    RobotHttp.Reply result = RobotHttp.exchange(socket, "/face",
                        "token=" + ticket.token + "&id=" + ticket.id);
                    if (result.code != 200) throw new IOException(result.code == 503 ?
                        "OLED unavailable · check wiring and restart Scout" : "Face not confirmed · tap to retry");
                    reply = new JSONObject(result.body);
                    validate(reply);
                    if (reply.getLong("boot") != boot || !reply.optBoolean("faceControl") ||
                        !reply.optBoolean("oledReady") || reply.getInt("face") != ticket.id)
                        throw new IOException("Face not confirmed · tap to retry");
                }
                synchronized (state) {
                    if (faces.current(ticket, state.epoch, state.active()) && ticket.token.equals(state.token))
                        faces.message = FaceSelection.label(ticket.id);
                }
            } catch (Exception failure) {
                synchronized (state) {
                    if (faces.current(ticket, state.epoch, state.active()) && ticket.token.equals(state.token))
                        faces.message = failure.getMessage() == null ? "Face not confirmed · tap to retry" : failure.getMessage();
                }
            }
            changed();
        }
    }
    private void changed() { main.post(listener::changed); }
    private void loop() {
        while (true) {
            pollDriveAck();
            int action; long epoch; String token = null; long seq = 0; int left = 0, right = 0;
            synchronized (state) {
                long now = SystemClock.elapsedRealtime(); state.tick(now); epoch = state.epoch;
                if (state.stopPending) { state.stopPending = false; action = 1; }
                    else if (closed) { closeUdp(); return; }
                else if (state.connectPending) { state.connectPending = false; action = 2; }
                else if (inspectPending) { inspectPending = false; action = 3; }
                else if (configPending) { configPending = false; action = 4; }
                else if (state.active() && (state.urgent || now >= nextDrive)) {
                    if (state.sequence >= 0xffffffffL) { state.end("Session complete · reconnect to drive"); continue; }
                    action = 5; token = state.token; seq = ++state.sequence;
                    int[] pair = state.takeTargetPair(); left = pair[0]; right = pair[1]; state.urgent = false; nextDrive = now + 50;
                } else {
                    try { state.wait(25); } catch (InterruptedException ignored) { }
                    continue;
                }
            }
            changed();
            try {
                if (action == 1) {
                    if (udp != null && !udp.isClosed()) {
                        byte[] stop=DriveDatagram.encode(true,DriveDatagram.ZERO_TOKEN,0,0,0);
                        try {
                            for(int repeat=0;repeat<3;++repeat)udp.send(new DatagramPacket(stop,stop.length));
                        } catch (IOException error) {
                            Log.w("ScoutLink", "Packet STOP failed; trying HTTP STOP", error);
                        }
                    }
                    JSONObject result = request("/stop", ""); validate(result);
                } else if (action == 2) {
                    closeUdp();
                    if (wifi == null) wifi = findWifi();
                    JSONObject status = request("/status", null); validate(status);
                    if (!status.getBoolean("ready")) throw new Exception("Scout is not ready");
                    synchronized (state) { if (state.epoch != epoch || state.mode != SessionState.CONNECTING) continue; }
                    JSONObject session = request("/session", ""); validate(session);
                    if (!session.getBoolean("ready") || !session.getBoolean("connected") || session.getBoolean("enabled") || session.getInt("left") != 0 || session.getInt("right") != 0)
                        throw new Exception("Scout did not create a neutral session");
                    DatagramSocket socket = new DatagramSocket();
                    try { wifi.bindSocket(socket); socket.connect(InetAddress.getByName("192.168.4.1"),DriveDatagram.PORT); socket.setSoTimeout(1); }
                    catch(Exception e){socket.close();throw e;}
                    synchronized (state) {
                        state.tick(SystemClock.elapsedRealtime());
                        if (state.acceptSession(epoch, session.getString("token"))) {
                            boot = session.getLong("boot"); udp=socket;lastAck=SystemClock.elapsedRealtime();lastAckSequence=0;
                            faces.initialize(session.optBoolean("faceControl"), session.optBoolean("oledReady"), session.optInt("face", -1));
                        } else { socket.close(); if (state.epoch == epoch) throw new Exception("Invalid Scout session token"); }
                    }
                } else if (action == 3) {
                    if (wifi == null) wifi = findWifi(); JSONObject status = request("/status", null); validate(status);
                    if (status.getBoolean("connected")) throw new Exception("Scout has a live session · press STOP first");
                    boolean l = status.getBoolean("leftReversed"), r = status.getBoolean("rightReversed");
                    synchronized (state) {
                        if (state.epoch == epoch) {
                            state.message = "Motor directions ready";
                            main.post(() -> { synchronized (state) { if (state.epoch != epoch || state.active() || state.mode == SessionState.CONNECTING) return; } listener.calibration(l, r); });
                        }
                    }
                } else if (action == 4) {
                    if (wifi == null) wifi = findWifi();
                    JSONObject status = request("/config", "leftReversed=" + (configLeft ? 1 : 0) + "&rightReversed=" + (configRight ? 1 : 0)); validate(status);
                    if (status.getBoolean("connected") || status.getBoolean("enabled")) throw new Exception("Scout did not confirm stopped calibration");
                    synchronized (state) { if (state.epoch == epoch) state.message = "Motor directions saved"; }
                } else {
                    if(udp==null)throw new IOException("Drive channel unavailable");
                    byte[] command=DriveDatagram.encode(false,token,seq,left,right);
                    udp.send(new DatagramPacket(command,command.length));
                }
            } catch (Exception error) {
                String detail = error.getMessage();
                Log.w("ScoutLink", "Request " + action + " failed: " + error.getClass().getSimpleName() + ": " + detail);
                if (error instanceof IOException) detail = "Scout link interrupted · check the robot's power and Wi-Fi";
                if (detail == null || detail.isEmpty()) detail = "Wi-Fi request failed";
                synchronized (state) {
                    if (action != 1) state.fail(epoch, detail + " · reconnect when ready");
                    else if (state.epoch == epoch && state.mode == SessionState.OFF)
                        state.message = "Stop unconfirmed · check Scout's Wi-Fi connection";
                }
            } finally {
                if(action==1)closeUdp();
                ConnectivityManager.NetworkCallback released = null;
                synchronized (state) {
                    // Consume cleanup and detach its exact request atomically. A later
                    // Stop or Connect cannot consume cleanup or replace this callback.
                    if (action == 1 && state.takeWifiCleanup(epoch)) {
                        released = wifiRequest; wifiRequest = null; wifi = null;
                    }
                    if (action == 3 || action == 4) busy = false;
                }
                unregisterWifi(released);
                changed();
            }
        }
    }
    private void closeUdp(){if(udp!=null){udp.close();udp=null;}}
    private void pollDriveAck(){
        if(udp==null)return;
        synchronized(state){if(!state.active())return;}
        long epoch;String token;
        synchronized(state){epoch=state.epoch;token=state.token;}
        try {
            for(int i=0;i<8;++i){
                byte[] bytes=new byte[128];DatagramPacket packet=new DatagramPacket(bytes,bytes.length);udp.receive(packet);
                DriveDatagram.Ack ack=DriveDatagram.decode(bytes,packet.getLength());
                if(ack==null||!ack.token.equals(token))continue;
                synchronized(state){
                    if(!state.acceptDrive(epoch))return;
                    if(ack.boot!=boot||(ack.flags&3)!=3||ack.code==2||ack.code==3){state.fail(epoch,"Scout session ended · reconnect");break;}
                    if(ack.code==0&&ack.sequence>lastAckSequence&&ack.sequence<=state.sequence){lastAckSequence=ack.sequence;lastAck=SystemClock.elapsedRealtime();}
                }
            }
        }catch(SocketTimeoutException normal){ }
        catch(IOException failure){synchronized(state){state.fail(epoch,"Scout drive link lost · reconnect");}}
        synchronized(state){if(state.acceptDrive(epoch)&&SystemClock.elapsedRealtime()-lastAck>=400)state.fail(epoch,"Scout replies lost · reconnect when ready");}
    }
    private Network findWifi() throws Exception {
        for (Network network : connectivity.getAllNetworks()) {
            NetworkCapabilities c = connectivity.getNetworkCapabilities(network);
            if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                LinkProperties properties = connectivity.getLinkProperties(network);
                if (properties != null) for (LinkAddress address : properties.getLinkAddresses())
                    if (address.getAddress().getHostAddress().startsWith("192.168.4.")) return network;
            }
        }
        throw new Exception("Join C3-Scout-Test in Wi-Fi settings");
    }
    private JSONObject request(String path, String body) throws Exception {
        long started = SystemClock.elapsedRealtime();
        if (wifi == null) wifi = findWifi();
        try (Socket socket = wifi.getSocketFactory().createSocket()) {
            socket.connect(new InetSocketAddress("192.168.4.1", 80), 600);
            RobotHttp.Reply reply = RobotHttp.exchange(socket, path, body);
            if (reply.code != 200) throw new Exception(reply.code == 409 ? "Scout session conflict or expired lease" : "Scout request rejected (" + reply.code + ")");
            return new JSONObject(reply.body);
        } finally {
            long elapsed = SystemClock.elapsedRealtime() - started;
            if (elapsed > 150) Log.i("ScoutLink", path + " reply took " + elapsed + " ms");
        }
    }
    private static void validate(JSONObject j) throws Exception {
        if (j.optInt("protocol", -1) != 2 || !"SCOUT 2".equals(j.optString("version")) || j.optInt("timeoutMs", 0) != 400 || j.optInt("drivePort",0)!=DriveDatagram.PORT)
            throw new Exception("Firmware update needed · install SCOUT 2");
        long boot = j.getLong("boot");
        if (boot < 0 || boot > 0xffffffffL || Math.abs((long) j.getInt("left")) > 100 || Math.abs((long) j.getInt("right")) > 100)
            throw new Exception("Invalid Scout response");
        j.getBoolean("ready"); j.getBoolean("connected"); j.getBoolean("enabled");
        j.getBoolean("leftReversed"); j.getBoolean("rightReversed"); j.getString("message");
    }
}
