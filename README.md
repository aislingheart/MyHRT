# MyHRT — HRT Tracking & Pharmacokinetics Companion

MyHRT is an Android application designed for tracking gender-affirming hormone replacement therapy (HRT). It features a real-time mathematical pharmacokinetics (PK) simulation engine, custom medication routines, automated reminders, and intelligent cycle/symptom tracking.

---

## Key Features

- 📈 **Pharmacokinetics Simulation Engine**  
  Simulates serum Estradiol (E2) and Testosterone (T) levels over time based on actual dose histories, route of administration (Oral, Sublingual, Gel, Patch, IM/SubQ Injections), and ester types (EV, EC, EN, EB, TC, TE).

- 🔔 **Intelligent Reminders & Boot Recovery**  
  Frequency-aware medication alarms (spaced intervals for twice-daily doses, weekly schedules) that survive device reboots.

- 💡 **Clinical Timing Recommendations**  
  Real-time guidance on optimal dose spacing, sublingual hold times, gel application advice, and injection site rotation.

- 📱 **On-Device & Offline Intelligence**  
  Features an offline clinical reference engine to provide guidance without requiring cloud API keys.

---

## Downloads & Releases

You can download the pre-compiled APK directly from the [GitHub Releases](https://github.com/aislingheart/MyHRT/releases) section.

---

## Building from Source

1. Clone this repository:
   ```bash
   git clone https://github.com/aislingheart/MyHRT.git
   ```
2. Open the project in **Android Studio**.
3. Build and run using standard Gradle commands:
   ```bash
   ./gradlew assembleDebug
   ```

---

## License

Distributed under the MIT License. See `LICENSE` for details.
