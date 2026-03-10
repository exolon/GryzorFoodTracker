# Gryzor Food Tracker: The Behavioral Engine

Most nutrition trackers assume humans are perfectly rational calculators. Gryzor Food Tracker assumes we are biological systems subject to ego depletion, central nervous system fatigue, and metabolic variance. 

Built entirely in modern **Jetpack Compose** with a local **Room SQL Database**, this is not just a calorie counter—it is a closed-loop behavioral economics engine designed to predict failure states before they happen.

### 🧠 The Philosophy
Weight management is a problem of cognitive bandwidth, not just mathematics. When tracking fails, it isn't because you forgot the caloric value of an apple; it is because systemic fatigue broke your adherence. This application actively tracks the *friction* and *context* surrounding your meals, quantifying exactly how sleep, stress, and momentum impact your willpower over time.

### ⚡ Core Architecture

* **The Capture Engine:** Ultra-low friction inputs. Use natural voice commands ("Snack, an apple at 4 pm") or gesture-based meal duplication.
* **Morning Intent Dashboard:** An empty-state UI that forces you to establish your daily Cognitive Load (1-5) and Subjective Sleep Quality (1-5) *before* you log food. 
* **The Willpower Tax:** The Analytics engine mathematically cross-references your deficit success rate on days with Good Sleep versus Poor Sleep, providing empirical proof of how compromised recovery degrades discipline.
* **Caloric VIX & Fuel ROI:** Tracks standard deviation (variance) of caloric intake to prevent erratic eating patterns, and calculates whether your surplus calories are efficiently fueling high-strain "Grind" days.
* **Predictive Degradation (Burnout Meter):** A live meter that calculates your "rubber-band binge risk" by multiplying your current deficit streak against your sleep penalties and metabolic volatility.
* **Recovery Debt Ratio:** Actively monitors CNS fatigue. If you log a high-strain workout on a day with a poor Sleep Score, the engine applies a 1.5x penalty to the ratio, accelerating the "Critical Debt" warning to force a deload.

### 🏗️ Tech Stack
* **UI:** 100% Jetpack Compose with responsive hardware haptic engine integration (`HapticFeedbackType.LongPress`).
* **Persistence:** Room Database (SQL) mapped seamlessly through Flow to the UI layer, with Datastore for isolated preference states.
* **Data Export:** Automated `.db` exports and `.pdf` Executive Summary generation.
