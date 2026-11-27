package io.github.aureliomo.prismlock;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;

final class UpdateApkVerifier {
    private UpdateApkVerifier() {}

    static void verify(Context context, File apk) throws Exception {
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? Api28.SIGNING_FLAG
                : PackageManager.GET_SIGNATURES;
        PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null) throw new IOException("The downloaded file is not a valid APK.");
        if (!context.getPackageName().equals(archive.packageName)) {
            throw new IOException("The downloaded APK belongs to another package.");
        }
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
        String expectedSigner = signer(installed);
        String actualSigner = signer(archive);
        if (expectedSigner == null || !expectedSigner.equals(actualSigner)) {
            throw new IOException("The downloaded APK is not signed by the PrismLock release key.");
        }
        if (versionCode(archive) <= versionCode(installed)) {
            throw new IOException("The downloaded release is not newer than the installed version.");
        }
    }

    private static String signer(PackageInfo info) throws Exception {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28
                ? Api28.signatures(info)
                : info.signatures;
        if (signatures == null || signatures.length == 0) return null;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return UpdateDownload.hex(digest.digest(signatures[0].toByteArray()));
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? Api28.versionCode(info) : info.versionCode;
    }

    /** Isolates API 28+ types so older devices never load them. */
    @android.annotation.TargetApi(28)
    private static final class Api28 {
        static final int SIGNING_FLAG = PackageManager.GET_SIGNING_CERTIFICATES;

        static Signature[] signatures(PackageInfo info) {
            SigningInfo signing = info.signingInfo;
            if (signing == null) return null;
            return signing.hasMultipleSigners()
                    ? signing.getApkContentsSigners()
                    : signing.getSigningCertificateHistory();
        }

        static long versionCode(PackageInfo info) {
            return info.getLongVersionCode();
        }
    }

}
