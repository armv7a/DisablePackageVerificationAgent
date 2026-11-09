package com.android.server.pm;
public final class VerifyingSession {
    private int mInstallFlags;
    public int observedFlags;
    public int starts;
    public RuntimeException failure;
    public VerifyingSession(int flags) { mInstallFlags = flags; }
    public int flags() { return mInstallFlags; }
    private void handleStartVerify() {
        starts++;
        observedFlags = mInstallFlags;
        if (failure != null) throw failure;
    }
    private boolean isAdbVerificationEnabled() { throw new AssertionError("ADB verification must be skipped"); }
}
