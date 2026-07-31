package com.example.data

import kotlin.math.exp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

// ─────────────────────────────────────────────────────────────────────────────
//  PHARMACOKINETICS ENGINE  –  v2.0
//  Inspired by Mahiro's estrogen PK model (mahiro.uk) and the Oyama HRT Tracker.
//
//  Architecture:
//  • Historical doses are COMPUTED MATHEMATICALLY from Medication.startDateMillis
//    and frequency – nothing is written to the database.
//  • New dose events are only created when explicitly logged in the app.
//  • The engine is a pure, stateless computation layer.
//
//  Supported routes:  Injection (IM / SubQ), Oral, Sublingual, Gel, Patch
//  Supported E2 esters: Estradiol (E2), Valerate (EV), Cypionate (EC),
//                       Enanthate (EN), Benzoate (EB)
//  Supported T esters:  Testosterone (T), Cypionate (TC), Enanthate (TE)
// ─────────────────────────────────────────────────────────────────────────────

// ─── Core PK constants ───────────────────────────────────────────────────────

/** Distribution volume per kg body weight for Estradiol (L/kg). */
private const val VD_PER_KG_E2  = 2.0
/** Distribution volume per kg for Cyproterone Acetate (L/kg). */
private const val VD_PER_KG_CPA = 14.0
/** Distribution volume per kg for Testosterone (L/kg). */
private const val VD_PER_KG_T   = 1.0

/** Clearance rate constant k₃ for free E2 – non-injection routes (h⁻¹).
 *  Corresponds to t½ ≈ 1.69 h; anchored to transdermal patch post-removal decay. */
private const val K_CLEAR_E2            = 0.41
/** Clearance rate constant k₃ for free E2 – injection routes (h⁻¹).
 *  Kept small so the flip-flop depot shape is preserved for oil depots. */
private const val K_CLEAR_E2_INJECTION  = 0.041
/** Clearance rate constant k₃ for free T – non-injection. */
private const val K_CLEAR_T             = 0.50
/** Clearance rate constant k₃ for free T – injection. */
private const val K_CLEAR_T_INJECTION   = 0.035

// ─── Molecular weights (g/mol) ───────────────────────────────────────────────

private val MW = mapOf(
    "E2" to 272.38,
    "EB" to 376.50,
    "EV" to 356.50,
    "EC" to 396.58,
    "EN" to 384.56,
    "T"  to 288.42,
    "TC" to 412.60,
    "TE" to 400.59,
    "TU" to 456.70,
    "CPA" to 416.94
)

/** Returns the fraction of ester weight that is free E2 (or T). */
private fun toE2Factor(ester: String): Double = MW["E2"]!! / (MW[ester] ?: MW["E2"]!!)
private fun toTFactor(ester: String): Double = MW["T"]!! / (MW[ester] ?: MW["T"]!!)

// ─── Injection depot PK (E2) ─────────────────────────────────────────────────
// Two-part depot (fast + slow compartments) sourced from Oyama's TwoPartDepotPK.

private data class DepotPK(val fracFast: Double, val k1Fast: Double, val k1Slow: Double)

private val E2_DEPOT = mapOf(
    "EB" to DepotPK(0.90,   0.144,       0.114),
    "EV" to DepotPK(0.40,   0.0216,      0.0138),
    "EC" to DepotPK(0.2292, 0.005035,    0.004511),
    "EN" to DepotPK(0.05,   0.0010,      0.0050),
    "E2" to DepotPK(1.00,   0.50,        0.0)
)

/** k₂ – hydrolysis rate (ester → free E2) in h⁻¹. */
private val E2_K2 = mapOf(
    "EB"  to 0.090,
    "EV"  to 0.070,
    "EC"  to 0.045,
    "EN"  to 0.015,
    "E2"  to 0.0
)

/** Empirical formation fraction – net fraction of ester dose delivered as E2. */
private val E2_FORMATION = mapOf(
    "EB"  to 0.1092,
    "EV"  to 0.0623,
    "EC"  to 0.1173,
    "EN"  to 0.12,
    "E2"  to 1.0
)

// ─── Injection depot PK (T) ──────────────────────────────────────────────────

private val T_DEPOT = mapOf(
    "TC" to DepotPK(0.35, 0.025, 0.005),
    "TE" to DepotPK(0.40, 0.035, 0.008),
    "TU" to DepotPK(0.10, 0.008, 0.0009)
)

private val T_K2 = mapOf("TC" to 0.20, "TE" to 0.20, "TU" to 0.20)

private val T_FORMATION = mapOf("TC" to 0.025, "TE" to 0.025, "TU" to 0.025)

// ─── Oral / Sublingual PK ────────────────────────────────────────────────────

private const val K_ABS_E2      = 0.32   // h⁻¹ – oral E2 absorption (Tmax ≈ 2–3 h)
private const val K_ABS_EV_ORAL = 0.05   // h⁻¹ – oral EV absorption (Tmax ≈ 6–7 h)
private const val K_ABS_SL      = 1.8    // h⁻¹ – mucosal absorption (Tmax ≈ 1 h)
private const val F_ORAL        = 0.03   // Oral first-pass bioavailability fraction

// Sublingual θ (mucosal fraction) by hold-duration tier
private val SL_THETA = mapOf(
    "quick"    to 0.01,   // ~2 min hold
    "casual"   to 0.04,   // ~5 min hold
    "standard" to 0.11,   // ~10 min hold (default)
    "strict"   to 0.18    // ~15 min hold
)

// ─── Gel PK ──────────────────────────────────────────────────────────────────

private const val K_ABS_GEL_E2 = 0.022   // h⁻¹ (t½ ≈ 31.5 h)
private const val F_GEL_E2     = 0.05    // 5% transdermal bioavailability (arm/thigh)
private const val F_GEL_SCROTAL = 0.40   // ~40% scrotal bioavailability

private const val K_ABS_GEL_T  = 0.05
private const val F_GEL_T      = 0.10

// ─── CPA (Cyproterone Acetate) ───────────────────────────────────────────────

private const val K_ABS_CPA = 1.0    // h⁻¹
private const val K_CLEAR_CPA = 0.017 // h⁻¹ (t½ ≈ 41 h)
private const val F_CPA       = 0.70  // ~70% oral bioavailability

// ─────────────────────────────────────────────────────────────────────────────
//  ANALYTICAL SOLVERS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Three-compartment analytical solution (depot → ester → free hormone → clearance).
 * Returns the amount of free hormone (mg) in the central compartment at time τ hours
 * after dosing.
 *
 *   A(τ) = D·F·k1·k2 · [ e^{-k1·τ}/((k1-k2)(k1-k3))
 *                         + e^{-k2·τ}/((-k1+k2)(k2-k3))
 *                         + e^{-k3·τ}/((k1-k3)(k2-k3)) ]
 *
 * Returns 0 when τ < 0 or any pair of rate constants is numerically identical
 * (singularity guard).
 */
private fun analytic3C(
    tau: Double,
    doseMG: Double,
    F: Double,
    k1: Double,
    k2: Double,
    k3: Double
): Double {
    if (tau < 0.0 || doseMG <= 0.0 || k1 <= 0.0) return 0.0

    val d12 = k1 - k2
    val d13 = k1 - k3
    val d23 = k2 - k3

    // Singularity protection: if any two rate constants are too close, skip.
    if (abs(d12) < 1e-9 || abs(d13) < 1e-9 || abs(d23) < 1e-9) return 0.0

    val term1 = exp(-k1 * tau) / (d12 * d13)
    val term2 = exp(-k2 * tau) / (-d12 * d23)
    val term3 = exp(-k3 * tau) / (d13 * d23)

    return doseMG * F * k1 * k2 * (term1 + term2 + term3)
}

/**
 * Single-compartment Bateman equation (first-order absorption + first-order elimination).
 * Used for oral, gel, and the swallowed fraction of sublingual doses.
 *
 *   A(τ) = D·F·ka/(ka−ke) · (e^{-ke·τ} − e^{-ka·τ})
 *
 * Limit form when ka ≈ ke:
 *   A(τ) = D·F·ka·τ·e^{-ke·τ}
 */
private fun oneCompAmount(
    tau: Double,
    doseMG: Double,
    F: Double,
    ka: Double,
    ke: Double
): Double {
    if (tau < 0.0 || doseMG <= 0.0) return 0.0
    if (abs(ka - ke) < 1e-9) {
        return doseMG * F * ka * tau * exp(-ke * tau)
    }
    return doseMG * F * ka / (ka - ke) * (exp(-ke * tau) - exp(-ka * tau))
}

// ─────────────────────────────────────────────────────────────────────────────
//  ESTER & ROUTE DETECTION
// ─────────────────────────────────────────────────────────────────────────────

private fun detectE2Ester(name: String): String {
    val n = name.lowercase()
    return when {
        n.contains("valerate")    || n.contains(" ev") -> "EV"
        n.contains("cypionate")   || n.contains(" ec") -> "EC"
        n.contains("enanthate")   || n.contains(" en") -> "EN"
        n.contains("benzoate")    || n.contains(" eb") -> "EB"
        else -> "E2"  // Unesterified / patch / gel / oral
    }
}

private fun detectTEster(name: String): String {
    val n = name.lowercase()
    return when {
        n.contains("cypionate") || n.contains(" tc") -> "TC"
        n.contains("enanthate") || n.contains(" te") -> "TE"
        n.contains("undecanoate") || n.contains(" tu") -> "TU"
        else -> "T"
    }
}

private fun isTestosteroneMed(med: Medication): Boolean {
    val n = med.name.lowercase()
    return n.contains("testosterone") || n.contains("androgel") ||
           n.contains("sustanon") || n.contains("nebido") ||
           n.contains("t-gel") || n.contains("testogel")
}

private fun isCPAMed(med: Medication): Boolean {
    val n = med.name.lowercase()
    return n.contains("cyproterone") || n.contains("androcur") || n.contains("cpa")
}

private fun isAntiAndrogen(med: Medication): Boolean {
    val n = med.name.lowercase()
    return n.contains("spiro") || n.contains("aldactone") ||
           n.contains("bica") || n.contains("casodex") ||
           n.contains("lupron") || n.contains("decapeptyl") ||
           n.contains("finasteride") || n.contains("dutasteride") ||
           isCPAMed(med)
}

private fun isInjection(med: Medication): Boolean {
    val m = med.method.lowercase()
    return m.contains("inject") || m.contains("im") || m.contains("subq") ||
           m.contains("subcutaneous") || m.contains("intramuscular")
}

private fun isSublingual(med: Medication) = med.method.lowercase().contains("sublingual")
private fun isGel(med: Medication): Boolean {
    val m = med.method.lowercase()
    return m.contains("gel") || m.contains("cream") || m.contains("lotion")
}
private fun isPatch(med: Medication) = med.method.lowercase().contains("patch")
private fun isOral(med: Medication): Boolean {
    val m = med.method.lowercase()
    return m.contains("oral") || m.contains("pill") || m.contains("tablet")
}
private fun isSubQ(med: Medication): Boolean {
    val m = med.method.lowercase()
    return m.contains("subq") || m.contains("subcutaneous")
}

// ─────────────────────────────────────────────────────────────────────────────
//  DOSE AMOUNT PARSER
// ─────────────────────────────────────────────────────────────────────────────

private fun parseDoseMG(dose: String): Double {
    val raw = dose.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 1.0
    // Convert µg → mg if specified
    return if (dose.lowercase().contains("µg") || dose.lowercase().contains("mcg"))
        raw / 1000.0
    else
        raw
}

// ─────────────────────────────────────────────────────────────────────────────
//  INTERVAL & HISTORICAL DOSE GENERATOR
// ─────────────────────────────────────────────────────────────────────────────

private fun intervalHours(frequency: String): Double {
    val f = frequency.lowercase()
    return when {
        f.contains("twice daily") || f.contains("twice-daily") || f.contains("bid") -> 12.0
        f.contains("three") && f.contains("day")  -> 8.0
        f.contains("daily") || f.contains("every day") -> 24.0
        f.contains("every 2") || f.contains("every other") -> 48.0
        f.contains("every 3") -> 72.0
        f.contains("every 4") -> 96.0
        f.contains("every 5") -> 120.0
        f.contains("twice weekly") || f.contains("bi-weekly") && !f.contains("month") -> 84.0 // 3.5 days
        f.contains("weekly") || f.contains("every week") -> 168.0
        f.contains("every 10") -> 240.0
        f.contains("every 2 week") || f.contains("fortnightly") -> 336.0
        f.contains("every 3 week") -> 504.0
        f.contains("monthly") || f.contains("every month") -> 720.0
        else -> 168.0   // default: weekly
    }
}

/**
 * Generates the list of (offsetHours from now) at which this medication was dosed,
 * based on its startDate and frequency.
 *
 * These are PURE CALCULATIONS — nothing is written to the database.
 * Historical doses are reconstructed mathematically at simulation time.
 *
 * @param nowMillis   Current epoch time in milliseconds.
 * @param lookaheadH  How many hours into the future to project (for graph tails).
 */
private fun historicalDoseOffsets(
    med: Medication,
    nowMillis: Long,
    lookaheadH: Double = 14.0 * 24.0
): List<Double> {
    val intervalH = intervalHours(med.frequency)
    val startMillis = med.startDateMillis
    if (startMillis > nowMillis) return emptyList()

    val nowH = nowMillis / (1000.0 * 3600.0)
    val startH = startMillis / (1000.0 * 3600.0)

    // Add small time-of-day offset from reminder time
    val reminderOffsetH = med.reminderHour + med.reminderMinute / 60.0

    // Number of past doses
    val elapsed = nowH - startH
    val pastCount = (elapsed / intervalH).toInt() + 1

    val offsets = mutableListOf<Double>()

    // Past and present doses
    for (i in 0..pastCount) {
        val doseH = startH + reminderOffsetH / 24.0 + i * intervalH
        // Store as hours-before-now (negative = past, used by the solver as τ from now)
        offsets.add(doseH - nowH)
    }

    // Future doses for lookahead graph
    var futureH = (pastCount + 1) * intervalH
    while (futureH <= lookaheadH) {
        offsets.add(futureH)
        futureH += intervalH
    }

    return offsets
}

// ─────────────────────────────────────────────────────────────────────────────
//  AMOUNT CALCULATORS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Computes the total amount of E2 (mg) contributed by one medication at time
 * [timeOffsetDays] days from now, summing contributions from all historical doses.
 *
 * Positive timeOffsetDays = future, negative = past.
 */
private fun computeE2AmountMG(
    med: Medication,
    timeOffsetDays: Double,
    nowMillis: Long
): Double {
    val doseMG = parseDoseMG(med.dose)
    val timeH  = timeOffsetDays * 24.0   // convert query time to hours

    val doseOffsets = historicalDoseOffsets(med, nowMillis)

    var totalAmountMG = 0.0

    when {
        isInjection(med) -> {
            val ester  = detectE2Ester(med.name)
            val depot  = E2_DEPOT[ester] ?: E2_DEPOT["EV"]!!
            val k2     = E2_K2[ester] ?: 0.07
            val F      = E2_FORMATION[ester] ?: 0.07
            val k3     = K_CLEAR_E2_INJECTION
            // SubQ slows absorption ~25%
            val k1FastAdj = if (isSubQ(med)) depot.k1Fast * 0.75 else depot.k1Fast
            val k1SlowAdj = if (isSubQ(med)) depot.k1Slow * 0.75 else depot.k1Slow

            for (doseOffH in doseOffsets) {
                val tau = timeH - doseOffH  // time since this dose (hours)
                if (tau < 0.0) continue
                val doseFast = doseMG * depot.fracFast
                val doseSlow = doseMG * (1.0 - depot.fracFast)
                totalAmountMG += analytic3C(tau, doseFast, F, k1FastAdj, k2, k3)
                totalAmountMG += analytic3C(tau, doseSlow, F, k1SlowAdj, k2, k3)
            }
        }

        isSublingual(med) -> {
            val ester = detectE2Ester(med.name)
            val theta = SL_THETA["standard"]!!   // default; could be per-med in future
            val k3    = K_CLEAR_E2

            for (doseOffH in doseOffsets) {
                val tau = timeH - doseOffH
                if (tau < 0.0) continue

                // Fast branch: mucosal absorption (bypasses first-pass)
                val doseFast = doseMG * theta
                val doseSlow = doseMG * (1.0 - theta)

                if (ester == "EV") {
                    // EV sublingual still needs hydrolysis → 3-compartment
                    val k2 = E2_K2["EV"]!!
                    totalAmountMG += analytic3C(tau, doseFast, 1.0, K_ABS_SL, k2, k3)
                    totalAmountMG += analytic3C(tau, doseSlow, F_ORAL, K_ABS_EV_ORAL, k2, k3)
                } else {
                    // Plain E2 sublingual → 1-compartment dual-branch
                    totalAmountMG += oneCompAmount(tau, doseFast, 1.0, K_ABS_SL, k3)
                    totalAmountMG += oneCompAmount(tau, doseSlow, F_ORAL, K_ABS_E2, k3)
                }
            }
        }

        isGel(med) -> {
            val isScrotally = med.method.lowercase().contains("scrotal")
            val F  = if (isScrotally) F_GEL_SCROTAL else F_GEL_E2
            val k3 = K_CLEAR_E2
            for (doseOffH in doseOffsets) {
                val tau = timeH - doseOffH
                if (tau < 0.0) continue
                totalAmountMG += oneCompAmount(tau, doseMG, F, K_ABS_GEL_E2, k3)
            }
        }

        isPatch(med) -> {
            // Patches: zero-order release during wear + exponential decay after removal
            val intervalH = intervalHours(med.frequency)
            val wearH     = intervalH   // assume wear = full interval
            val k3        = K_CLEAR_E2
            // Nominal release rate in mg/h. If dose is in µg/day, convert.
            val rateMGh   = doseMG / 24.0   // doseMG is already in mg/day or equivalent

            for (doseOffH in doseOffsets) {
                val tau = timeH - doseOffH
                if (tau < 0.0) continue
                val amount = if (tau <= wearH) {
                    rateMGh / k3 * (1.0 - exp(-k3 * tau))
                } else {
                    val amtAtRemoval = rateMGh / k3 * (1.0 - exp(-k3 * wearH))
                    amtAtRemoval * exp(-k3 * (tau - wearH))
                }
                totalAmountMG += amount
            }
        }

        isOral(med) -> {
            val ester = detectE2Ester(med.name)
            val ka    = if (ester == "EV") K_ABS_EV_ORAL else K_ABS_E2
            val k3    = K_CLEAR_E2
            for (doseOffH in doseOffsets) {
                val tau = timeH - doseOffH
                if (tau < 0.0) continue
                totalAmountMG += oneCompAmount(tau, doseMG, F_ORAL, ka, k3)
            }
        }

        else -> {
            // Fallback: treat as oral
            val k3 = K_CLEAR_E2
            for (doseOffH in doseOffsets) {
                val tau = timeH - doseOffH
                if (tau < 0.0) continue
                totalAmountMG += oneCompAmount(tau, doseMG, F_ORAL, K_ABS_E2, k3)
            }
        }
    }

    return max(0.0, totalAmountMG)
}

/**
 * Computes the total amount of Testosterone (mg) in central compartment
 * from a testosterone medication at [timeOffsetDays] from now.
 */
private fun computeTAmountMG(
    med: Medication,
    timeOffsetDays: Double,
    nowMillis: Long
): Double {
    val doseMG     = parseDoseMG(med.dose)
    val timeH      = timeOffsetDays * 24.0
    val doseOffsets = historicalDoseOffsets(med, nowMillis)
    var totalMG    = 0.0

    when {
        isInjection(med) -> {
            val ester  = detectTEster(med.name)
            val depot  = T_DEPOT[ester] ?: T_DEPOT["TC"]!!
            val k2     = T_K2[ester] ?: 0.20
            val F      = T_FORMATION[ester] ?: 0.025
            val k3     = K_CLEAR_T_INJECTION
            val k1FastAdj = if (isSubQ(med)) depot.k1Fast * 0.75 else depot.k1Fast
            val k1SlowAdj = if (isSubQ(med)) depot.k1Slow * 0.75 else depot.k1Slow

            for (doseOffH in doseOffsets) {
                val tau = timeH - doseOffH
                if (tau < 0.0) continue
                totalMG += analytic3C(tau, doseMG * depot.fracFast, F, k1FastAdj, k2, k3)
                totalMG += analytic3C(tau, doseMG * (1.0 - depot.fracFast), F, k1SlowAdj, k2, k3)
            }
        }

        isGel(med) -> {
            val k3 = K_CLEAR_T
            for (doseOffH in doseOffsets) {
                val tau = timeH - doseOffH
                if (tau < 0.0) continue
                totalMG += oneCompAmount(tau, doseMG, F_GEL_T, K_ABS_GEL_T, k3)
            }
        }

        isPatch(med) -> {
            val intervalH = intervalHours(med.frequency)
            val wearH     = intervalH
            val k3        = K_CLEAR_T
            val rateMGh   = doseMG / 24.0
            for (doseOffH in doseOffsets) {
                val tau = timeH - doseOffH
                if (tau < 0.0) continue
                val amount = if (tau <= wearH) {
                    rateMGh / k3 * (1.0 - exp(-k3 * tau))
                } else {
                    val amtAtRemoval = rateMGh / k3 * (1.0 - exp(-k3 * wearH))
                    amtAtRemoval * exp(-k3 * (tau - wearH))
                }
                totalMG += amount
            }
        }

        else -> {
            // Oral/sublingual T (uncommon but possible)
            val k3 = K_CLEAR_T
            for (doseOffH in doseOffsets) {
                val tau = timeH - doseOffH
                if (tau < 0.0) continue
                totalMG += oneCompAmount(tau, doseMG, 0.07, K_ABS_E2, k3)
            }
        }
    }

    return max(0.0, totalMG)
}

/**
 * Computes the amount (in arbitrary mg-equivalent units) for CPA and other
 * anti-androgens at [timeOffsetDays] from now.  Used for the secondary
 * compound overlay on the graph.
 */
private fun computeCPAAmountMG(
    med: Medication,
    timeOffsetDays: Double,
    nowMillis: Long
): Double {
    val doseMG      = parseDoseMG(med.dose)
    val timeH       = timeOffsetDays * 24.0
    val doseOffsets = historicalDoseOffsets(med, nowMillis)
    var totalMG     = 0.0

    for (doseOffH in doseOffsets) {
        val tau = timeH - doseOffH
        if (tau < 0.0) continue
        totalMG += oneCompAmount(tau, doseMG, F_CPA, K_ABS_CPA, K_CLEAR_CPA)
    }
    return max(0.0, totalMG)
}

// ─────────────────────────────────────────────────────────────────────────────
//  AMOUNT → CONCENTRATION CONVERSION
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Converts a central-compartment amount (mg) to blood plasma concentration.
 *
 * @param amountMG       Total drug amount in central compartment (mg).
 * @param vdPerKG        Distribution volume per kg body weight (L/kg).
 * @param bodyWeightKG   User body weight in kg (default 70 kg if unknown).
 * @param unitFactor     1e9 for pg/mL (E2), 1e8 for ng/dL (T), 1e6 for ng/mL (CPA).
 */
private fun amountToConc(
    amountMG: Double,
    vdPerKG: Double,
    bodyWeightKG: Double = 70.0,
    unitFactor: Double = 1e9
): Double {
    val vdML = vdPerKG * bodyWeightKG * 1000.0
    return (amountMG * unitFactor) / vdML
}

// ─────────────────────────────────────────────────────────────────────────────
//  PUBLIC API
// ─────────────────────────────────────────────────────────────────────────────

object Pharmacokinetics {

    /**
     * Calculates serum Estradiol concentration in pg/mL at [timeOffsetDays]
     * from now.  Sums over all E2 medications provided.
     *
     * @param meds            All active medications.
     * @param timeOffsetDays  Days from now (0 = now, negative = past, positive = future).
     * @param bloodTests      Past blood test results used for individual calibration.
     * @param profileStartMillis  Start of HRT profile (for baseline reference).
     * @param isFeminizing    True for feminizing regimens (E2 as primary hormone).
     */
    fun calculateEstradiol(
        meds: List<Medication>,
        timeOffsetDays: Double,
        bloodTests: List<BloodTestResult> = emptyList(),
        profileStartMillis: Long? = null,
        isFeminizing: Boolean = true
    ): Double {
        val nowMillis = System.currentTimeMillis()

        // Physiological baseline E2 (before any exogenous HRT)
        val baselineE2 = if (isFeminizing) 20.0 else 25.0

        val e2Meds = meds.filter { med ->
            val n = med.name.lowercase()
            !isTestosteroneMed(med) && !isCPAMed(med) && (
                n.contains("estradiol") || n.contains("estrogen") ||
                n.contains("oestradiol") || n.contains("progynova") ||
                n.contains("estrofem") || n.contains("divigel") ||
                n.contains("sandrena") || n.contains("lenzetto") ||
                n.contains("climara") || n.contains("vivelle") ||
                n.contains("e2")
            )
        }

        if (e2Meds.isEmpty()) return baselineE2

        // Sum amounts from all E2 medications
        var totalAmountMG = 0.0
        for (med in e2Meds) {
            totalAmountMG += computeE2AmountMG(med, timeOffsetDays, nowMillis)
        }

        // Convert mg → pg/mL using default 70 kg body weight
        val rawConc = amountToConc(totalAmountMG, VD_PER_KG_E2, 70.0, 1e9)

        // Apply blood-test calibration (exponentially-weighted, most-recent tests count more)
        val calibScale = computeCalibrationScale(
            e2Meds, bloodTests, nowMillis, profileStartMillis, isFeminizing, isE2 = true
        )

        return baselineE2 + rawConc * calibScale
    }

    /**
     * Calculates serum Testosterone in ng/dL at [timeOffsetDays] from now.
     *
     * For feminizing regimens: returns the suppressed endogenous T level based
     *   on E2 feedback and any anti-androgen medications present.
     * For masculinizing regimens: returns T from exogenous medications.
     */
    fun calculateTestosterone(
        meds: List<Medication>,
        timeOffsetDays: Double,
        bloodTests: List<BloodTestResult> = emptyList(),
        profileStartMillis: Long? = null,
        isFeminizing: Boolean = true,
        simulatedE2: Double = 0.0
    ): Double {
        val nowMillis = System.currentTimeMillis()

        if (isFeminizing) {
            return calculateSuppressedT(meds, simulatedE2, bloodTests, profileStartMillis, nowMillis, timeOffsetDays)
        }

        // Masculinizing: exogenous testosterone
        val tMeds = meds.filter { isTestosteroneMed(it) }
        val baselineT = 30.0 // AFAB baseline ng/dL

        if (tMeds.isEmpty()) return baselineT

        var totalAmountMG = 0.0
        for (med in tMeds) {
            totalAmountMG += computeTAmountMG(med, timeOffsetDays, nowMillis)
        }

        // Convert mg → ng/dL
        val rawConc = amountToConc(totalAmountMG, VD_PER_KG_T, 70.0, 1e8)

        val calibScale = computeCalibrationScale(
            tMeds, bloodTests, nowMillis, profileStartMillis, isFeminizing, isE2 = false
        )

        return baselineT + rawConc * calibScale
    }

    /**
     * Calculates the secondary compound (CPA, Spironolactone, etc.) equivalent
     * dose level at [timeOffsetDays] for overlay on the graph.
     * Returns in mg-equivalent units (not pg/mL – this is for graph overlay only).
     */
    fun calculateOtherDrug(
        med: Medication,
        timeOffsetDays: Double,
        profileStartMillis: Long? = null
    ): Double {
        val nowMillis = System.currentTimeMillis()

        return if (isCPAMed(med)) {
            // CPA: compute actual concentration in ng/mL for display
            val amtMG = computeCPAAmountMG(med, timeOffsetDays, nowMillis)
            amountToConc(amtMG, VD_PER_KG_CPA, 70.0, 1e6)
        } else {
            // Other anti-androgens: simple 1-comp model, show dose units
            val doseMG = parseDoseMG(med.dose)
            val timeH = timeOffsetDays * 24.0
            val intervalH = intervalHours(med.frequency)
            val startH = med.startDateMillis / (1000.0 * 3600.0)
            val nowH = nowMillis / (1000.0 * 3600.0)
            val elapsed = nowH - startH
            val pastCount = (elapsed / intervalH).toInt() + 2
            var total = 0.0
            for (i in -pastCount..15) {
                val doseOffH = -(i * intervalH)
                val tau = timeH - doseOffH
                if (tau >= 0.0) {
                    total += oneCompAmount(tau, doseMG, 0.7, 1.0, 0.03)
                }
            }
            max(0.0, total)
        }
    }

    /**
     * Legacy compatibility wrapper — delegates to calculateEstradiol or calculateTestosterone
     * depending on regimen type.
     */
    fun calculateConcentration(
        meds: List<Medication>,
        timeOffsetDays: Double,
        bloodTests: List<BloodTestResult> = emptyList(),
        profileStartMillis: Long? = null
    ): Double {
        val hasTestosterone = meds.any { isTestosteroneMed(it) }
        return if (hasTestosterone) {
            calculateTestosterone(meds, timeOffsetDays, bloodTests, profileStartMillis,
                isFeminizing = false, simulatedE2 = 0.0)
        } else {
            calculateEstradiol(meds, timeOffsetDays, bloodTests, profileStartMillis, isFeminizing = true)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Computes suppressed endogenous T for feminizing HRT.
     * E2 at high levels inhibits LH/FSH → reduces gonadal T production.
     * Anti-androgens apply an additional suppression / receptor blockade.
     */
    private fun calculateSuppressedT(
        meds: List<Medication>,
        simulatedE2: Double,
        bloodTests: List<BloodTestResult>,
        profileStartMillis: Long?,
        nowMillis: Long,
        timeOffsetDays: Double
    ): Double {
        val baselineT = 550.0 // Typical AMAB pre-HRT T (ng/dL)
        val hasBlocker = meds.any { isAntiAndrogen(it) }

        // E2 feedback: exponential suppression of LH/FSH axis.
        // At E2=100 pg/mL → ~22% remaining; at E2=200 → ~5% remaining.
        val lhFeedback = exp(-0.015 * simulatedE2)

        // Anti-androgen additional suppression factor
        val blockerFactor = when {
            meds.any { isCPAMed(it) }                          -> 0.10  // CPA: strong suppressor
            meds.any { it.name.lowercase().contains("spiro") } -> 0.20  // Spiro: moderate
            meds.any { it.name.lowercase().contains("lupron") ||
                       it.name.lowercase().contains("decapeptyl") } -> 0.05 // GnRH agonists: near-complete
            hasBlocker                                          -> 0.15  // Other blockers
            else                                                -> 1.0
        }

        val rawT = (baselineT * lhFeedback * blockerFactor).coerceIn(8.0, 550.0)

        // Apply blood-test calibration for T
        val tTests = bloodTests.filter { it.tLevel != null && it.tLevel > 0 }
        if (tTests.isEmpty()) return rawT

        var sumWeights = 0.0
        var sumRatios  = 0.0
        for (test in tTests) {
            val actual   = test.tLevel?.toDouble() ?: continue
            val daysAgo  = (nowMillis - test.dateMillis) / (1000.0 * 86400.0)
            val weight   = exp(-0.03 * daysAgo)
            if (rawT > 0.1) {
                sumRatios  += (actual / rawT) * weight
                sumWeights += weight
            }
        }
        val scale = if (sumWeights > 0) (sumRatios / sumWeights).coerceIn(0.1, 5.0) else 1.0
        return (rawT * scale).coerceIn(8.0, 550.0)
    }

    /**
     * Computes an exponentially-weighted blood-test calibration scale factor.
     * Recent test results have more influence; older tests decay in weight.
     * Output is clamped to [0.2, 5.0] to avoid runaway corrections.
     */
    private fun computeCalibrationScale(
        meds: List<Medication>,
        bloodTests: List<BloodTestResult>,
        nowMillis: Long,
        profileStartMillis: Long?,
        isFeminizing: Boolean,
        isE2: Boolean
    ): Double {
        val relevantTests = if (isE2)
            bloodTests.filter { it.e2Level != null && it.e2Level > 0 }
        else
            bloodTests.filter { it.tLevel != null && it.tLevel > 0 }

        if (relevantTests.isEmpty()) return 1.0

        var sumWeights = 0.0
        var sumRatios  = 0.0

        for (test in relevantTests) {
            val actual  = if (isE2) test.e2Level?.toDouble() else test.tLevel?.toDouble()
            actual ?: continue

            val daysAgo = (nowMillis - test.dateMillis) / (1000.0 * 86400.0)
            val weight  = exp(-0.03 * daysAgo)

            // Compute what the model predicted at this past date
            val offsetDays = -daysAgo
            var predicted = 0.0
            for (med in meds) {
                val amtMG = if (isE2)
                    computeE2AmountMG(med, offsetDays, nowMillis)
                else
                    computeTAmountMG(med, offsetDays, nowMillis)

                val unitFactor = if (isE2) 1e9 else 1e8
                val vdPerKG    = if (isE2) VD_PER_KG_E2 else VD_PER_KG_T
                predicted += amountToConc(amtMG, vdPerKG, 70.0, unitFactor)
            }

            if (predicted > 1.0) {
                sumRatios  += (actual / predicted) * weight
                sumWeights += weight
            }
        }

        return if (sumWeights > 0)
            (sumRatios / sumWeights).coerceIn(0.2, 5.0)
        else
            1.0
    }
}
