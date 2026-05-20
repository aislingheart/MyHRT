package com.example.data

import kotlin.math.exp

data class PKProfile(val ka: Double, val ke: Double, val scalingFactor: Double)

object Pharmacokinetics {
    // Reference parameters strictly derived from the provided clinical PDF
    // Table 1 (Estradiol Esters Normalized to 5 mg Dose) & Table 2
    // C(t) = (Scaling / (ka - ke)) * (exp(-ke * t) - exp(-ka * t))
    
    val profiles = mapOf(
        "Estradiol Valerate" to PKProfile(ka = 1.0, ke = 0.231, scalingFactor = 460.0), // tmax=2.1, t1/2=3.0, Cmax=295
        "Estradiol Cypionate" to PKProfile(ka = 0.4, ke = 0.103, scalingFactor = 99.0), // tmax=4.3, t1/2=6.7, Cmax=155
        "Estradiol Enanthate" to PKProfile(ka = 0.18, ke = 0.150, scalingFactor = 71.0), // tmax=6.5, t1/2=4.6, Cmax=160
        "Estradiol Benzoate" to PKProfile(ka = 3.5, ke = 0.577, scalingFactor = 4858.0), // tmax=0.65, t1/2=1.2, Cmax=971
        
        // Testosterone estimates to achieve 500-1000 ng/dL ranges on standard doses
        "Testosterone Cypionate" to PKProfile(ka = 0.3, ke = 0.1, scalingFactor = 600.0), 
        "Testosterone Enanthate" to PKProfile(ka = 0.08, ke = 0.088, scalingFactor = 300.0) // IM clearance estimation from Table 2
    )

    fun calculateConcentration(meds: List<Medication>, timeOffsetDays: Double, bloodTests: List<BloodTestResult> = emptyList(), profileStartMillis: Long? = null): Double {
        var baseTotalC = 0.0
        
        // Calibration Factor logic via exponentially-weighted moving average
        var calibrationScaler = 1.0
        if (bloodTests.isNotEmpty() && meds.isNotEmpty()) {
            val isTestosterone = meds.firstOrNull()?.name?.contains("Testosterone", true) == true
            
            // Just average the ratio of actual / expected for the past tests to scale the curve.
            var sumWeights = 0.0
            var weightedRatioSum = 0.0
            val now = System.currentTimeMillis()
            
            bloodTests.forEach { test ->
                val actual = if (isTestosterone) test.tLevel else test.e2Level
                if (actual != null && actual > 0) {
                    val daysAgo = (now - test.dateMillis) / (1000.0 * 60 * 60 * 24)
                    val expectedTrough = calculateBaseConcentration(meds, -daysAgo, profileStartMillis)
                    if (expectedTrough > 1.0) {
                        val ratio = actual / expectedTrough
                        val weight = exp(-0.05 * daysAgo)
                        sumWeights += weight
                        weightedRatioSum += ratio * weight
                    }
                }
            }
            if (sumWeights > 0) {
                calibrationScaler = (weightedRatioSum / sumWeights).coerceIn(0.2, 5.0)
            }
        }
        
        baseTotalC = calculateBaseConcentration(meds, timeOffsetDays, profileStartMillis)
        val finalC = baseTotalC * calibrationScaler
        return if (finalC > 0) finalC else 0.0
    }

    private fun calculateBaseConcentration(meds: List<Medication>, timeOffsetDays: Double, profileStartMillis: Long? = null): Double {
        var totalC = 0.0
        val nowMillis = System.currentTimeMillis()
        
        meds.forEach { med ->
            val profileKeys = profiles.keys.filter { med.name.contains(it, ignoreCase = true) }
            val profile = if (profileKeys.isNotEmpty()) profiles[profileKeys.first()] else null
            
            val doseStr = med.dose.replace(Regex("[^0-9.]"), "")
            
            val intervalDays = when {
                med.frequency.contains("Daily", ignoreCase = true) -> 1.0
                med.frequency.contains("Twice", ignoreCase = true) -> 0.5
                med.frequency.contains("Weekly", ignoreCase = true) -> 7.0
                med.frequency.contains("Bi-weekly", ignoreCase = true) -> 14.0
                med.frequency.contains("Month", ignoreCase = true) -> 30.0
                else -> 7.0 
            }

            // Estimate exact dose timestamps. Favor profileStartMillis if older, to fix historical graphs correctly.
            val medStart = med.startDateMillis
            val startMillis = if (profileStartMillis != null && profileStartMillis < medStart) profileStartMillis else medStart
            val startDaysAgo = (nowMillis - startMillis) / (1000.0 * 60 * 60 * 24)
            // Align to specific time of day set by user
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = nowMillis
            val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(java.util.Calendar.MINUTE)
            val tzOffsetDays = (currentHour + currentMinute / 60.0) / 24.0
            val doseTimeDays = (med.reminderHour + med.reminderMinute / 60.0) / 24.0
            val timeDoseOffset = doseTimeDays - tzOffsetDays
            
            val numHistoricDoses = (startDaysAgo / intervalDays).toInt() + 2
            // We simulate doses from start date up to some days into the future
            
            if (profile != null) {
                val doseAmt = doseStr.toDoubleOrNull() ?: 5.0
                var adjustedKa = profile.ka
                if (med.method.contains("SubQ", ignoreCase = true) || med.method.contains("Subcutaneous", ignoreCase = true)) {
                    adjustedKa *= 0.75
                }
                
                for (i in -numHistoricDoses..15) {
                    val doseTakenOffset = - (i * intervalDays) + timeDoseOffset // shift by exact time
                    val t = timeOffsetDays - doseTakenOffset
                    // Ensure the dose wasn't taken BEFORE start date
                    if (t >= 0.0 && doseTakenOffset >= -startDaysAgo) {
                        val factor = (doseAmt / 5.0) * profile.scalingFactor
                        totalC += (factor / (adjustedKa - profile.ke)) * (exp(-profile.ke * t) - exp(-adjustedKa * t))
                    }
                }
            } else if (med.method.contains("Oral", ignoreCase = true) || med.method.contains("Sublingual", ignoreCase = true)) {
                val isSublingual = med.method.contains("Sublingual", ignoreCase = true)
                val ka = if (isSublingual) 12.0 else 2.0
                val ke = if (isSublingual) 3.0 else 1.5
                val doseAmt = doseStr.toDoubleOrNull() ?: 2.0
                
                for (i in -numHistoricDoses..30) {
                    val doseTakenOffset = - (i * intervalDays) + timeDoseOffset
                    val t = timeOffsetDays - doseTakenOffset
                    if (t >= 0.0 && doseTakenOffset >= -startDaysAgo) {
                        val factor = (doseAmt / 2.0) * (if (isSublingual) 1500.0 else 400.0)
                        totalC += (factor / (ka - ke)) * (exp(-ke * t) - exp(-ka * t))
                    }
                }
            }
        }
        return totalC
    }
}
