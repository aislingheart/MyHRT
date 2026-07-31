# HRT Tracker Application Index & Development Documentation

Welcome to the HRT (Hormone Replacement Therapy) Tracker codebase developer guide. This document serves as a comprehensive system architecture index, engineering blueprint, and mathematical engine specification to guide ongoing local or remote development.

---

## 1. System & Structural Architecture

The application is structured as a modern Android app utilizing **MVVM (Model-View-ViewModel)** architecture, built entirely using **Kotlin, Jetpack Compose**, and standard Clean Architecture layers.

```
/app/src/main/java/com/example
│
├── api/
│   └── GeminiApi.kt         # Network client & Local Offline Gemini Nano emulator 
│
├── data/
│   ├── Entities.kt          # Room entities & Data schemas
│   ├── AppDatabase.kt       # Persistent room database migration context
│   ├── HRTDao.kt            # SQL Data Access Object
│   ├── HRTRepository.kt     # Repository abstraction exposing Kotlin Flows
│   └── Pharmacokinetics.kt  # Math engine / PK curves & feedback algorithms
│
└── ui/
    ├── AppNavigation.kt    # Type-safe Jetpack Navigation Graph & routes
    ├── HRTViewModel.kt     # Unidirectional StateFlow holder & state manager
    ├── DashboardScreen.kt  # Quick check-ins, medication logs, short telemetry
    ├── ExpectedEffectsScreen.kt # Interactive canvas graph, touch scrubbing & timelines
    ├── MedicationScreen.kt # Addition, deletion, dose schedules & method configuration
    ├── OnboardingScreen.kt # Set up regimen type, startup milestones & key profiles
    └── ProfileScreen.kt    # Calibration settings, API key manager & offline modes
```

---

## 2. The Core Mathematical Engines (`Pharmacokinetics.kt`)

The brain of the app is the custom PK simulator in `Pharmacokinetics.kt`. It models hormone absorption and blood levels over time, calibrated by real blood tests.

### A. Pharmacokinetics (PK Profile Structures)
Compounds are simulated via continuous-time differential approximations ($K_a$, $K_e$, dosing intervals, and methods of administration). 

$$\text{Concentration}(t) = \text{Dose} \times \frac{\text{Scaling Factor}}{K_a - K_e} \times \left(e^{-K_e \cdot t} - e^{-K_a \cdot t}\right)$$

*   **Estradiol Valerate:** Half-life $\approx 3.0$ days ($K_e=0.231$, $K_a=1.0$).
*   **Estradiol Cypionate:** Half-life $\approx 6.7$ days ($K_e=0.103$, $K_a=0.4$).
*   **Estradiol Enanthate:** Half-life $\approx 4.6$ days ($K_e=0.150$, $K_a=0.18$).
*   **Estradiol Benzoate:** Half-life $\approx 1.2$ days ($K_e=0.577$, $K_a=3.5$).
*   **Testosterone Cypionate / Enanthate:** High scaling-factors supporting standard masculinizing physiological ranges of 300 to 1200 ng/dL.

### B. Absorption Mode Alterations
The PK calculation adjusts parameters based on the intake method:
*   **Subcutaneous (SubQ / Intramuscular):** Dampens $K_a$ to model slower depot release characteristics.
*   **Oral & Sublingual pills:** Faster absorption kinetics ($K_a$ up to 12.0) and rapid clearance.
*   **Transdermal (Gel & Patches):** Slower continuous absorption kinetics.

### C. Estradiol & Testosterone Suppression Feedback Loop
Under feminizing HRT, natural Testosterone production is suppressed via negative hypothalamic-pituitary-gonadal (HPG) feedback. The mathematical model calculates:
$$lhFeedbackFactor = e^{-0.015 \times \text{Simulated Estradiol}}$$
$$T_{\text{suppressed}} = \text{Baseline T} \times lhFeedbackFactor \times \text{Blocker Factor}$$
*   **Baseline Testosterone (AMAB):** 550.0 ng/dL.
*   **Blocker Factor:** If taking anti-androgens (Spiro, Cypro, Bica, etc.), T level is actively partitioned lower ($0.15$).

### D. Blood Test Calibration Algorithm
Blood tests calibrate the curves using weighted exponential time decay so that older tests have less impact on today's levels:
$$\text{Weight}_i = e^{-0.03 \times \text{Days Elapsed}}$$
$$\text{Calibration Scalar} = \frac{\sum (\text{Actual}_i / \text{Expected}_i) \times \text{Weight}_i}{\sum \text{Weight}_i}$$
Re-scaled within a safe bound $[0.2, 5.0]$ to dynamically correct theoretical assumptions based on empirical lab results.

---

## 3. High-Fidelity UI Features

### ExpectedEffectsScreen (`ExpectedEffectsScreen.kt`)
1.  **Unified Dashboard:** Displays calculated Day 0 (today's) live circulating hormones plus exact active milligram values of secondary drugs.
2.  **Interactive Canvas Chart:** Plots smooth curves for E2, T, and other active secondary drugs on a grid.
3.  **Mouse/Touch Pointer Scrubbing:** Dragging a finger or cursor across the canvas tracks a vertical line and highlights intersection values dynamically on a floating tooltip card.
4.  **Date/Time Ranges:** Dynamic chips change viewframes (`1 Week`, `1 Month`, `3 Months`, `6 Months`, `1 Year`), adjusting math steps to optimize processing speed and layout responsiveness layout-wide.
5.  **Multi-Series Filter & Toggles:** Filter out unrelated graphs with dynamic chip groups matching the user's active prescription.
6.  **Dynamic Milestone Milestones:** Forecasts physiological effects (breast development, voice changes, fat redistribution) which stretch algorithmically (`stretchRatio`) if average hormone blood levels drop below therapeutical requirements (e.g. E2 $< 100\text{ pg/mL}$ or T $> 50\text{ ng/dL}$).

---

## 4. Local Offline Gemini Nano Emulator (`GeminiApi.kt`)

To safeguard the app against cloud connection loss, API-key unavailability, or backend service changes, we have implemented a high-fidelity **Rule-Based Offline Companion** that matches Gemini Nano guidelines:
*   **Zero-Dependency Local Inference:** Prevents gRPC serialization crashes (e.g., HTTP 404/MissingField errors) when cloud access is skipped or disabled.
*   **Intelligent Keyword Router:** Scans user chat queries for critical HRT phrases (such as `breast`, `voice`, `hair`, `fat`, `mood`) and provides immediate, clinically accurate, empathetic responses offline.
*   **Seamless Integration:** Fully switches state flags based on the user's settings menu selection ("Use Local On-Device AI").

---

## 5. Persistence & Schema Config

### Database Room Contract (`Entities.kt`)
*   **`user_profiles`**: Tracks regimen types, startup time indices, system preferences (dynamic themes, UI styles), API keys, enabled models, and local/cloud AI mode toggles.
*   **`medications`**: Details prescribed compound name, dosage (mg/ml), intake method (pill, gel, patch, injection), application time, and frequency.
*   **`blood_test_results`**: Stores date indices, Estradiol (E2) pg/mL readings, and Testosterone (T) ng/dL readings for calibration.
*   **`milestone_checks`**: Maintains user achievements corresponding to physiological transitions.
