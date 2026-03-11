**Gryzor Food Tracker: The Behavioral Engine**

Most nutrition trackers assume humans are perfectly rational calculators. Gryzor assumes we are complex biological systems subject to ego depletion, central nervous system fatigue, and metabolic variance.

Built entirely in modern Jetpack Compose, Gryzor is not just a food log—it is a closed-loop behavioral economics engine designed to quantify the friction between intent and execution.
🧠 The Thesis: Bandwidth over Arithmetic

Weight management is a problem of cognitive bandwidth, not just mathematics. When tracking fails, it isn't because the user forgot the caloric value of an apple; it is because systemic fatigue broke their adherence. Gryzor focuses on the "Capture and Context" layer—measuring exactly how sleep quality and daily stress correlate with caloric success.
⚡ Feature Highlights

🌅 The Morning Intent Dashboard
Establishing a baseline before the day begins. When a day is empty, the app presents a centralized dashboard requiring the user to log:
* Cognitive Load (1-5): Anticipated daily stress and mental friction.
*Sleep Quality (1-5): Subjective recovery status.
*Why: This forces the user to acknowledge their willpower reserves before making nutritional decisions.

📈 The Signal vs. Noise Analytics
Daily weight and caloric data are inherently noisy (water retention, glycogen shifts). Gryzor filters the noise to find the trend:
*31-Day Horizon: Monthly canvases for Intake and Body Composition trends.
*The Signal Toggle: Overlays a smooth 7-Day Trailing Average line on top of raw daily inputs to reveal the true metabolic trajectory.
*Explicit Data Bounds: Dynamic Y-axis labels that auto-scale to the month's absolute minimum and maximum values.

🏗️ The Behavioral Engine
Advanced metrics derived from interdisciplinary fields (Psychology, Economics, and Physiology):
* Predictive Degradation (Burnout Meter): Calculates "binge risk" based on deficit streaks, intake volatility, and sleep debt.
* The Willpower Tax: Empirically proves how much adherence drops on "Poor Sleep" days vs. "Good Sleep" days.
* Recovery Debt Ratio: Monitors CNS fatigue by weighting "Grind" sessions against "Rest" tags, with a 1.5x penalty for workouts performed under recovery stress.
* Weekly P&L: A financial-style ledger comparing Theoretical Fat Loss against Actual Scale Delta to audit TDEE accuracy.

🎙️ High-Fidelity Capture
* Voice Engine: Natural language processing for ultra-low friction entries (e.g., "Snack, a handful of almonds at 3 PM").
* Gesture UI: Swipe-to-duplicate or swipe-to-delete logic.
* Tactile Feedback: Deep integration with the hardware haptic engine for a satisfying, physical interaction.

🛠️ Tech Stack

* UI: 100% Jetpack Compose (Declarative UI).
* Persistence: Room SQL Database (local-only, offline-first).
* Async: Kotlin Coroutines & Flow for reactive data streams.
* Deployment: Integrated OTA (Over-The-Air) updater via the GitHub Releases API.

🤖 AI-Assisted Development

This application was entirely conceptualized, architected, and coded through a continuous human-AI pair programming workflow.

Acting as the Product Manager and Domain Expert, the human developer defined the behavioral economics frameworks, physiological requirements, and strict UX constraints. The AI (Google's Gemini) acted as the primary engineering partner—translating product requirements into Room SQL persistence, executing vector mathematics for the custom analytics canvases, and writing the underlying Kotlin logic.
📝 A Note from the Developer

**In essence, I wanted a very simple food intake tracker and I couldn't find one to fill my needs—and then the feature creep came in, like usual.**

