# HA Car Widget

Tiny Android app with a 3×1 home-screen widget for a car in Home Assistant:

- lock status (green locked / red unlocked / orange when a door, window, trunk or hood is open)
- open doors/windows listed only when something is open
- fuel % and range
- lock and unlock buttons (unlock asks for confirmation by default)
- tap the status or fuel area to refresh; refreshes every 15 min otherwise

## Install
Every push builds an APK via GitHub Actions and attaches it to a release:
**Releases → latest → `ha-car-widget.apk`**. Install it (allow unknown sources), open
**HA Car Widget**, enter your HA URL and a long-lived access token, tap **Test**, then **Save**.
Add the widget from the launcher's widget picker.

Entity IDs default to the Toyota integration's `speedy_gonyaris` entities and can be changed in the app.
