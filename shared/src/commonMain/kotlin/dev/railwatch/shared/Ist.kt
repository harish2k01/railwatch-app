package dev.railwatch.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Indian Standard Time: every booking date and reminder in RailWatch is reckoned in IST, wherever the phone is. */
val IST: TimeZone = TimeZone.of("Asia/Kolkata")

/** Today's date in IST. */
fun todayIst(clock: Clock = Clock.System): LocalDate = clock.todayIn(IST)
