package wificonfig.bitinfiniti.com;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Switch;
import android.widget.TextView;

import com.amazonaws.auth.AWSCredentialsProvider;

import java.util.ArrayList;
import java.util.List;

import lombok.NonNull;
import software.amazon.freertos.amazonfreertossdk.AmazonFreeRTOSConstants;
import software.amazon.freertos.amazonfreertossdk.AmazonFreeRTOSDevice;
import software.amazon.freertos.amazonfreertossdk.AmazonFreeRTOSManager;
import software.amazon.freertos.amazonfreertossdk.BleConnectionStatusCallback;
import software.amazon.freertos.amazonfreertossdk.BleScanResultCallback;

public class DeviceScanFragment extends Fragment {
    private static final String TAG = "DeviceScanFragment";
    private boolean mqttOn = false;
    private RecyclerView mBleDeviceRecyclerView;
    private BleDeviceAdapter mBleDeviceAdapter;
    List<BleDevice> mBleDevices = new ArrayList<>();
    private SwipeRefreshLayout mDevicePullToRefresh;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;

    private Button scanButton;

    private AmazonFreeRTOSManager mAmazonFreeRTOSManager;

    private enum ConnectionStatus {
        CONNECTED,
        CONNECTING,
        DISCONNECTED
    }
    private ConnectionStatus mConnStatus = ConnectionStatus.DISCONNECTED;

    private class BleDeviceHolder extends RecyclerView.ViewHolder {
        private TextView mBleDeviceNameTextView;
        private TextView mBleDeviceMacTextView;
        private Switch mBleDeviceSwitch;
        private TextView mMenuTextView;
        private BleDevice mBleDevice;
        private LinearLayout mDeviceInfo;
        private TextView mConnectionStatus;

        private boolean userDisconnect = true;
        private boolean connecting = false;
        private AmazonFreeRTOSDevice aDevice = null;

        private BleDeviceHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.list_device, parent, false));
            mBleDeviceNameTextView = itemView.findViewById(R.id.device_name);
            mBleDeviceMacTextView = itemView.findViewById(R.id.device_mac);
            mMenuTextView = itemView.findViewById(R.id.menu_option);
            mDeviceInfo = itemView.findViewById(R.id.device_info);
            mConnectionStatus = itemView.findViewById(R.id.connection_status);
        }

        private void bind(BleDevice bleDevice) {
            mBleDevice = bleDevice;
            mBleDeviceNameTextView.setText(mBleDevice.getName());
            mBleDeviceMacTextView.setText(mBleDevice.getMacAddr());


            mDeviceInfo.setOnClickListener((View v) -> {

                boolean autoReconnect = false;

                if (mConnStatus == ConnectionStatus.DISCONNECTED ) {
                    aDevice = mAmazonFreeRTOSManager.connectToDevice(mBleDevice.getBluetoothDevice(),
                            connectionStatusCallback, (AWSCredentialsProvider) null, autoReconnect);
                    if (aDevice != null) {
                        mConnStatus = ConnectionStatus.CONNECTING;
                        getActivity().runOnUiThread(() -> {
                            mConnectionStatus.setText("Connecting");
                        });
                    }
                }
            });

            mMenuTextView.setOnClickListener((View view) -> {
                    Log.i(TAG, "Click menu.");
                    PopupMenu popup = new PopupMenu(getContext(), mMenuTextView);
                    popup.inflate(R.menu.options_menu);
                    popup.setOnMenuItemClickListener((MenuItem item) -> {
                        switch (item.getItemId()) {
                            case R.id.wifi_provisioning_menu_id:
                                Intent intentToStartWifiProvision
                                        = WifiProvisionActivity.newIntent(getActivity(), mBleDevice.getMacAddr());
                                startActivity(intentToStartWifiProvision);
                                return true;
                            case R.id.disconnect_menu_id:
                                Log.i(TAG, "Should disconnect here");
                                //userDisconnect = true;
                                mAmazonFreeRTOSManager.disconnectFromDevice(aDevice);
                                //mBleDeviceSwitch.setChecked(false);
                                return true;
                        }
                        return false;

                    });
                    popup.show();
                });
            resetUI();
        }

        final BleConnectionStatusCallback connectionStatusCallback = new BleConnectionStatusCallback() {
            @Override
            public void onBleConnectionStatusChanged(AmazonFreeRTOSConstants.BleConnectionState connectionStatus) {
                Log.i(TAG, "BLE connection status changed to: " + connectionStatus);
                if (connectionStatus == AmazonFreeRTOSConstants.BleConnectionState.BLE_INITIALIZED) {
                    mConnStatus = ConnectionStatus.CONNECTED;
                    try {
                        getActivity().runOnUiThread(() -> {
                            mMenuTextView.setEnabled(true);
                            mConnectionStatus.setText("Connected");
                            mBleDeviceNameTextView.setTextColor(getResources().getColor(R.color.colorAccent, null));
                            mMenuTextView.setTextColor(getResources().getColor(R.color.colorAccent, null));
                        });
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                    }
                } else if (connectionStatus == AmazonFreeRTOSConstants.BleConnectionState.BLE_DISCONNECTED) {
                    mConnStatus = ConnectionStatus.DISCONNECTED;
                    try {
                        getActivity().runOnUiThread(() -> {
                            if (aDevice != null) {
                                aDevice = null;
                            }
                            resetUI();
                        });
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        private void resetUI() {
            try {
                getActivity().runOnUiThread(() -> {
                    mMenuTextView.setEnabled(false);
                    mMenuTextView.setTextColor(Color.GRAY);
                    mBleDeviceNameTextView.setTextColor(Color.GRAY);
                    //mBleDeviceSwitch.setChecked(false);
                    mConnectionStatus.setText("Disconnected");
                });
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

    }

    private class BleDeviceAdapter extends RecyclerView.Adapter<BleDeviceHolder> {
        private List<BleDevice> mDeviceList;
        private BleDeviceAdapter(List<BleDevice> devices) {
            mDeviceList = devices;
        }

        @Override
        public BleDeviceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            return new BleDeviceHolder(layoutInflater, parent);
        }

        @Override
        public void onBindViewHolder(BleDeviceHolder holder, int position) {
            BleDevice device = mDeviceList.get(position);
            holder.bind(device);
        }

        @Override
        public int getItemCount() {
            return mDeviceList.size();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_device_scan, container, false);

        mDevicePullToRefresh = view.findViewById(R.id.device_pull_to_refresh);
        mDevicePullToRefresh.setOnRefreshListener(() -> {
            mBleDevices.clear();
            mBleDeviceAdapter.notifyDataSetChanged();
            scanBLEDevices(); // your code
            mDevicePullToRefresh.setRefreshing(false);
        });

        //Enabling Bluetooth
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

        // requesting user to grant permission.
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);

        //Getting AmazonFreeRTOSManager
        mAmazonFreeRTOSManager = AmazonFreeRTOSAgent.getAmazonFreeRTOSManager(getActivity());

        //RecyclerView for displaying list of BLE devices.
        mBleDeviceRecyclerView = view.findViewById(R.id.device_recycler_view);
        mBleDeviceRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        mBleDeviceAdapter = new BleDeviceAdapter(mBleDevices);
        mBleDeviceRecyclerView.setAdapter(mBleDeviceAdapter);


        if (mqttOn) {
            Intent intentToStartAuthenticator
                    = AuthenticatorActivity.newIntent(getActivity());
            startActivity(intentToStartAuthenticator);
        }

        //scanBLEDevices();
        //getBondedDevices();

        return view;
    }

    private void getBondedDevices() {
        mAmazonFreeRTOSManager.getBondedDevices().forEach(bd -> {
            BleDevice thisDevice = new BleDevice(bd.getName(),
                    bd.getAddress(), bd);
            if (!mBleDevices.contains(thisDevice)) {
                Log.d(TAG, "new ble device found. Mac: " + thisDevice.getMacAddr());
                mBleDevices.add(thisDevice);
                mBleDeviceAdapter.notifyDataSetChanged();
            }
        });
    }

    private void scanBLEDevices() {
        Log.i(TAG, "scan started.");
        mDevicePullToRefresh.setEnabled(false);
        mAmazonFreeRTOSManager.startScanDevices(new BleScanResultCallback() {
            @Override
            public void onBleScanResult(ScanResult result) {

                BleDevice thisDevice = new BleDevice(result.getDevice().getName(),
                        result.getDevice().getAddress(), result.getDevice());
                if (!mBleDevices.contains(thisDevice)) {
                    Log.d(TAG, "new ble device found. Mac: " + thisDevice.getMacAddr());
                    mBleDevices.add(thisDevice);
                    mBleDeviceAdapter.notifyDataSetChanged();
                }
            }
            @Override
            public void onBleScanFailed(int error) {
                Log.d(TAG, String.format("scan failed %d", error));
                mDevicePullToRefresh.setEnabled(true);
            }

            @Override
            public void onBleScanStop() {
                mDevicePullToRefresh.setEnabled(true);
            }
        }, 10000);

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            getActivity();
            if (resultCode == Activity.RESULT_OK) {
                Log.i(TAG, "Successfully enabled bluetooth");
            } else {
                Log.w(TAG, "Failed to enable bluetooth");
            }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "ACCESS_FINE_LOCATION granted.");
            } else {
                Log.w(TAG, "ACCESS_FINE_LOCATION denied");
                scanButton.setEnabled(false);
            }
        }

    }

}
