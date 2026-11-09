package com.android.server.pm;
public final class PackageManagerService {
    private boolean isVerificationEnabled(int flags) { throw new AssertionError("Verification must be skipped"); }
    private void checkDowngrade() { throw new AssertionError("Must not hook downgrade"); }
}
