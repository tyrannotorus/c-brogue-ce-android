package org.broguece.game;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Launches external URLs in the system browser. Swallows failures so a
 *  missing browser intent handler never crashes the app. */
final class Links {

    private Links() {}

    static void open(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) { }
    }
}
