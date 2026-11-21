package id.bulwark

import android.app.Application

/**
 * Application entry point.
 *
 * Later tasks hang the single Room database and the overlay/notification channel off here so there
 * is exactly one instance per process. Kept empty for task 1 (skeleton).
 */
class BulwarkApp : Application()
