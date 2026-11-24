package app.nightfall

/** Alle Texte der App auf Deutsch und Englisch. */
class Text(lang: String) {
    private val de = lang == "de"
    private fun t(de: String, en: String) = if (this.de) de else en

    val on = t("AN", "ON")
    val off = t("AUS", "OFF")
    val statusOff = t("Ausgeschaltet.", "Off.")
    fun statusOn(time: String) = t("Wird jeden Tag um $time aktiviert.", "Activates every day at $time.")

    val pickTitle = t("Uhrzeit", "Time")
    val ok = t("OK", "OK")
    val cancel = t("Abbrechen", "Cancel")

    val setupTitle = t("Einrichtung", "Setup")
    val setupSub = t("Einmalig. Danach nie wieder.", "Once. Never again.")
    val done = t("ERLEDIGT", "DONE")
    val open = t("ÖFFNEN", "OPEN")
    val allow = t("ERLAUBEN", "ALLOW")
    val download = t("LADEN", "GET")

    val stepInstallShizuku = t("Shizuku installieren", "Install Shizuku")
    val stepStartShizuku = t("Shizuku starten", "Start Shizuku")
    val stepShizukuPermission = t("Zugriff erlauben", "Allow access")
    val stepExact = t("Exakte Uhrzeit", "Exact time")
    val stepBattery = t("Akku nicht einschränken", "Unrestricted battery")
    val stepNotifications = t("Benachrichtigungen", "Notifications")
    val stepAutostart = t("Autostart", "Autostart")

    val shizukuDenied = t(
        "Zugriff wurde abgelehnt. In Shizuku unter „Apps“ Nightfall erlauben.",
        "Access was denied. Allow Nightfall in Shizuku under “Apps”."
    )
    val shizukuOld = t("Shizuku ist zu alt. Bitte aktualisieren.", "Shizuku is too old. Please update.")

    val channel = t("Probleme", "Problems")
    val failNoShizuku = t(
        "Flugmodus wurde nicht aktiviert: Shizuku lief nicht.",
        "Airplane mode was not turned on: Shizuku was not running."
    )
    val failNoPermission = t(
        "Flugmodus wurde nicht aktiviert: Zugriff in Shizuku fehlt.",
        "Airplane mode was not turned on: Shizuku access missing."
    )
    val failAirplane = t("Flugmodus konnte nicht aktiviert werden.", "Airplane mode could not be turned on.")
    val failBluetooth = t(
        "Flugmodus ist an, aber Bluetooth konnte nicht ausgeschaltet werden.",
        "Airplane mode is on, but Bluetooth could not be turned off."
    )
}
