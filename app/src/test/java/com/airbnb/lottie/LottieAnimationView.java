package com.airbnb.lottie;
import android.content.Context;
import android.view.View;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
public class LottieAnimationView extends View {
    public final LottieDrawable lottieDrawable = new LottieDrawable();
    public final Map<String, String> cache = new HashMap<>();
    public String shown, lastKey;
    public boolean fail;
    private boolean clipped = true;
    public LottieAnimationView(Context context) { super(context); }
    public void setAnimation(String path) { shown = cache.computeIfAbsent("asset_" + path, ignored -> "stock"); }
    public void setAnimation(InputStream input, String key) throws IOException {
        if (fail) throw new IOException("simulated incompatible Lottie stream loader");
        lastKey = key;
        String data = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        shown = cache.computeIfAbsent(key, ignored -> data);
    }
    public boolean getClipToCompositionBounds() { return clipped; }
    public void setClipToCompositionBounds(boolean value) { clipped = value; }
}
