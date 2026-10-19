package com.fastlauncher.tv;

import android.service.notification.NotificationListenerService;

/**
 * Exists so the launcher can read active media sessions (now-playing).
 * Enabled over ADB: cmd notification allow_listener com.fastlauncher.tv/.SessionListener
 */
public class SessionListener extends NotificationListenerService {
}
