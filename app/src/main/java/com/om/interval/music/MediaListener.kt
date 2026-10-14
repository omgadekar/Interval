package com.om.interval.music

import android.service.notification.NotificationListenerService

/**
 * Only exists so the app can be granted notification access, which Android requires before it
 * will tell an app what is playing (song title, artist) and hand over the media session.
 */
class MediaListener : NotificationListenerService()