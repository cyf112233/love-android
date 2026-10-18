/*
 * Copyright (c) 2006-2024 LOVE Development Team
 *
 * This software is provided 'as-is', without any express or implied
 * warranty.  In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package org.love2d.android;

import java.lang.ref.WeakReference;

/** Small bridge between the game Activity and the Compose game menu. */
public final class GameSession {
    private static WeakReference<GameActivity> current = new WeakReference<>(null);

    private GameSession() {
    }

    public static void attach(GameActivity activity) {
        current = new WeakReference<>(activity);
    }

    public static void detach(GameActivity activity) {
        if (current.get() == activity) {
            current.clear();
        }
    }

    public static boolean isActive() {
        return current.get() != null;
    }

    public static void requestExit() {
        final GameActivity activity = current.get();
        if (activity != null) {
            activity.runOnUiThread(activity::finish);
        }
    }
}
