package io.github.aureliomo.prismlock;

import android.graphics.BitmapFactory;
import android.util.Base64;

final class Icons {
    static final int DEF_SIZE = 50;
    static final int DEF_STROKE10 = 26;

    static String imageLottie(byte[] png, int size) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(png, 0, png.length, options);
        int width = Math.max(1, options.outWidth);
        int height = Math.max(1, options.outHeight);
        float scale = size * 4f / Math.max(width, height) * 100f;
        String encoded = Base64.encodeToString(png, Base64.NO_WRAP);
        return "{\"v\":\"5.9.1\",\"fr\":120,\"ip\":0,\"op\":180,\"w\":288,\"h\":288,\"nm\":\"custom\",\"ddd\":0,"
                + "\"assets\":[{\"id\":\"img\",\"w\":" + width + ",\"h\":" + height + ",\"u\":\"\",\"p\":\"data:image/png;base64," + encoded + "\",\"e\":1}],"
                + "\"layers\":[{\"ddd\":0,\"ind\":1,\"ty\":2,\"nm\":\"icon\",\"refId\":\"img\",\"sr\":1,"
                + "\"ks\":{\"o\":{\"a\":0,\"k\":100},\"r\":{\"a\":0,\"k\":0},\"p\":{\"a\":0,\"k\":[144,144,0]},"
                + "\"a\":{\"a\":0,\"k\":[" + (width / 2f) + "," + (height / 2f) + ",0]},"
                + "\"s\":{\"a\":0,\"k\":[" + scale + "," + scale + ",100]}},"
                + "\"ao\":0,\"ip\":0,\"op\":180,\"st\":0,\"bm\":0}],\"markers\":[]}";
    }
}
