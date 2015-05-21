package org.fairytail.guessthesong.fragments;

import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.widget.Toast;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import org.fairytail.guessthesong.broadcasts.WiFiDirectBroadcastReceiver;
import org.fairytail.guessthesong.dagger.Injector;
import org.fairytail.guessthesong.events.p2p.P2PBroadcastReceivedEvent;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import ru.noties.debug.Debug;

public class DevicesDiscoveryFragment extends Fragment {

    @Inject
    Bus bus;

    @Inject
    WifiP2pManager manager;

    private final IntentFilter intentFilter = new IntentFilter();

    private WifiP2pManager.Channel channel;
    private WiFiDirectBroadcastReceiver receiver;
    private List<WifiP2pDevice> peers = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Injector.inject(this);
        bus.register(this);

        initIntentFilter();

        channel = manager.initialize(getActivity(), getActivity().getMainLooper(), null);
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Debug.d("discoverPeers onSuccess");
            }

            @Override
            public void onFailure(int reason) {
                Debug.d("discoverPeers onFailure");
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver();
        getActivity().registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(receiver);
    }

    @Override
    public void onDestroy() {
        bus.unregister(this);
        super.onDestroy();
    }

    @Subscribe
    public void onP2PBroadcastReceived(P2PBroadcastReceivedEvent event) {
        Debug.d("onP2PBroadcastReceived");
        switch (event.intent.getAction()) {
            case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION: break;
            case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                if (manager != null) {
                    manager.requestPeers(channel, peerList -> {
                        // Out with the old, in with the new.
                        peers.clear();
                        peers.addAll(peerList.getDeviceList());

                        Debug.d("Peers:");
                        for (WifiP2pDevice p : peers) {
                            Debug.d("deviceAddress: "+p.deviceAddress+"; deviceName: "+p.deviceName);
                        }

                        if (!peers.isEmpty()) {
                            connect(0);
                        }
                    });
                }
                Debug.d("P2P peers changed");
                break;
            case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                NetworkInfo networkInfo = event.intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

                if (networkInfo.isConnected()) {

                    // We are connected with the other device, request connection
                    // info to find group owner IP

                    manager.requestConnectionInfo(channel, info -> {

                        // InetAddress from WifiP2pInfo struct.
                        InetAddress groupOwnerAddress = info.groupOwnerAddress;

                        // After the group negotiation, we can determine the group owner.
                        if (info.groupFormed && info.isGroupOwner) {
                            // Do whatever tasks are specific to the group owner.
                            // One common case is creating a server thread and accepting
                            // incoming connections.
                        } else if (info.groupFormed) {
                            // The other device acts as the client. In this case,
                            // you'll want to create a client thread that connects to the group
                            // owner.
                        }
                    });
                }

                break;
            case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION: break;
        }
    }

    private void connect(int index) {
        // Picking the first device found on the network.
        WifiP2pDevice device = peers.get(index);

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(getActivity(), "Connect failed. Retry.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initIntentFilter() {
        //  Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }
}