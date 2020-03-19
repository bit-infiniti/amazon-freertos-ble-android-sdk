package wificonfig.bitinfiniti.com;

import android.support.v4.app.Fragment;

public class DeviceScanActivity extends SingleFragmentActivity {
    @Override
    protected Fragment createFragment() {
        return new DeviceScanFragment();
    }
}
