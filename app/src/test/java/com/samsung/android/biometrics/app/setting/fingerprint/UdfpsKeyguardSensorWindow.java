package com.samsung.android.biometrics.app.setting.fingerprint;
public class UdfpsKeyguardSensorWindow {
    public String mCurrentAnimationType;
    public int loads;
    public void setAnimationTypeIfNeeded() {
        if (mCurrentAnimationType == null) { loads++; mCurrentAnimationType = "same_asset"; }
    }
}
