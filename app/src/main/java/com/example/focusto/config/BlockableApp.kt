package com.example.focusto.config

/** Apps distractoras conocidas que el usuario puede activar/desactivar en Ajustes. */
enum class BlockableApp(val packageName: String, val label: String) {
    TIKTOK("com.zhiliaoapp.musically", "TikTok"),
    INSTAGRAM("com.instagram.android", "Instagram"),
    FACEBOOK("com.facebook.katana", "Facebook"),
    WHATSAPP("com.whatsapp", "WhatsApp"),
    TWITTER("com.twitter.android", "Twitter / X"),
    SNAPCHAT("com.snapchat.android", "Snapchat"),
    YOUTUBE("com.google.android.youtube", "YouTube")
}
