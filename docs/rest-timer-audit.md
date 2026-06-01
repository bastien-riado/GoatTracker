# Audit & plan de nettoyage — Timer de repos

Branche : `feature/rest-timer`
Périmètre : `RestTimerManager.kt`, `RestTimerService.kt`, `RestTimerReceiver.kt`,
`LiveWorkoutViewModel.kt`, `LiveWorkoutScreen.kt`, `MainActivity.kt`, `AndroidManifest.xml`.

> **Statut implémentation (itération 1).** Décision produit : les boutons **+30s / -30s sont
> supprimés** (le temps de repos est défini par exercice), ce qui supprime aussi le bug B4.
> Corrigés : **B1** (plus de flicker — `resetEffects` sans `Idle`), **B3** (arrondi au plafond),
> **B4** (boutons retirés), **B5** (boucle `notify` du service supprimée, chronomètre natif).
>
> **Statut implémentation (itération 2).** Retours terrain (test « Repos 15 sec ») :
> - **Décalage ~10-12 s** : la fin n'était pilotée que par l'`AlarmManager` **inexact**.
>   Correctif : la fin est désormais pilotée **en process par le service foreground**
>   (`delay` coroutine précis → `RestTimerManager.reachZero()`, idempotent), l'`AlarmManager`
>   devenant un **filet de secours**. `USE_EXACT_ALARM` **réintégré** pour que ce secours soit
>   exact et réveille le CPU en Doze (revient sur B8 — justifié pour un minuteur).
> - **Vibration incohérente (3× en app / 1× hors app)** : la vibration **du canal** écrasait la
>   nôtre hors app. Correctif : canal d'alerte **sans vibration** (nouvel id `…_v2`) + vibration
>   manuelle **répétitive** jusqu'à action utilisateur (souhaité), tous les chemins d'arrêt
>   (`Passer`, swipe via `deleteIntent`, série suivante) appellent `stopVibration()`. Couvre B2.
> - **B6** (deux horloges) atténué : une seule source de fin (service), affichage UI dérivé.
>
> Restant : **B7** (BootReceiver), **B9–B13**, refactor P6 (injection `Clock`/`TimerEffects`).

---

## 1. Vue d'ensemble de l'architecture actuelle

```
LiveWorkoutScreen (Compose)
   └─ LiveWorkoutViewModel ──observe──► RestTimerManager.state (StateFlow)
                                              │  (objet global / singleton)
        ┌─────────────────────────────────────┼────────────────────────────┐
        ▼                                      ▼                            ▼
 RestTimerService                      AlarmManager  ───fire──► RestTimerReceiver
 (foreground, notif compte à rebours)  SharedPreferences        (ACTION_ALARM_FIRED / ACTION_CANCEL)
```

États : `Idle` / `Counting(targetMillis, durationSeconds)` / `Finished`.

Le problème de fond : **la logique de transition d'état est éclatée** entre 5 fichiers
(Manager, Service, Receiver, ViewModel, Activity) sans propriétaire unique. Chaque acteur
mute l'état ou déclenche des effets de bord, d'où les incohérences observées.

---

## 2. Bugs identifiés

### 🔴 Critiques (cassent l'usage en séance)

**B1 — Émission parasite de `Idle` à chaque (re)démarrage du timer.**
`startTimer()` appelle `cancelAll()` en première ligne, qui pousse `state = Idle` *avant* de
passer à `Counting`. Conséquence à chaque série validée alors qu'un timer tourne déjà :
`Counting → Idle → Counting`. Le `RestTimerService` (qui observe) voit `Idle` → `stopSelf()`,
puis le ViewModel relance le service → la notif disparaît/réapparaît, et la barre rouge de repos
clignote dans l'UI. *Fix : un `resetEffects(silent=true)` interne qui annule alarme/notif/vibration
SANS émettre `Idle`, puis transition directe vers le nouveau `Counting`.*

**B2 — La vibration ne s'arrête pas si on balaie la notification « Repos terminé ».**
`startVibration()` lance un `createWaveform(..., repeat = 0)` = vibration **infinie**. Elle n'est
stoppée que par `acknowledge()` / `addTime()` / `cancelAll()`. La notif d'alerte n'est pas
`ongoing` et a `setAutoCancel(true)` (s'annule uniquement **au tap**). Si l'utilisateur **balaie**
la notif, rien n'appelle le Receiver → **le téléphone vibre indéfiniment**.
*Fix : `setDeleteIntent(ACTION_CANCEL)` sur la notif d'alerte + idéalement une vibration à durée
bornée (ex. 3 cycles) plutôt qu'infinie.*

**B3 — Off-by-one systématique sur le temps restant.**
`getRemainingSeconds() = (target - now) / 1000` (division entière → **troncature**). Un repos de
90 s affiche « 89 » immédiatement et n'atteint jamais la valeur de départ. Rend aussi le test
`assertEquals(90, timerRemainingSeconds)` **flaky** (dépend du nb de ms écoulées).
*Fix : arrondi au plafond → `ceil((target-now)/1000.0)` ou `(target - now + 999) / 1000`.*

**B4 — `-30s` peut déclencher l'alarme complète « Repos terminé ».**
Dans `addTime()`, si `newRemaining <= 0`, on déclenche `onAlarmFired()` + vibration + notif.
Donc appuyer sur **-30s** pour raccourcir/passer le repos peut **lancer la sonnerie + vibration**,
ce qui est contre-intuitif. *Fix : décider du contrat — soit `-30s` plafonne à un minimum (ex. 5 s),
soit il termine le repos silencieusement (équivalent « Passer »).*

### 🟠 Importants

**B5 — Double mécanisme de compte à rebours dans la notification.**
`RestTimerService` utilise à la fois `setUsesChronometer(true)+setChronometerCountDown(true)`
(rendu natif par le système) **et** une boucle manuelle `nm.notify(...)` toutes les 1000 ms.
Redondant : churn de notification + conso batterie inutile, et re-enregistre l'action « Passer »
chaque seconde. *Fix : garder le chronomètre natif, supprimer la boucle (ou notifier au plus
toutes les ~15 s en repli).*

**B6 — Deux horloges concurrentes pour l'affichage.**
Le Service rafraîchit la notif toutes les 1 s, le ViewModel poll `getRemainingSeconds()` toutes
les 500 ms. Combiné à la troncature (B3), l'affichage « saute » (89,89,88,88…).
*Fix : une seule source dérivée du `targetMillis` absolu, arrondie au plafond.*

**B7 — Pas de re-planification de l'alarme après reboot.**
Les alarmes `AlarmManager` sont effacées au redémarrage du téléphone. La restauration ne se fait
qu'à l'ouverture de l'app (`MainActivity.onCreate` → `initialize`). Un repos en cours pendant un
reboot ne sonnera pas. *Fix (optionnel) : `BootReceiver` + `RECEIVE_BOOT_COMPLETED`, ou accepter
la limite et la documenter.*

**B8 — `USE_EXACT_ALARM` dans le manifest = risque de rejet Play Store.**
Cette permission est réservée aux apps de type réveil/agenda. `SCHEDULE_EXACT_ALARM` (+ fallback
inexact déjà présent) suffit pour un timer de repos. *Fix : retirer `USE_EXACT_ALARM`.*

### 🟡 Mineurs / robustesse

- **B9** — Churn de service : `RestTimerService.start()` est appelé depuis `startRestTimer`,
  `addTimerTime` et `MainActivity`. Avec B1, on enchaîne start/stop/start. À fiabiliser une fois B1 corrigé.
- **B10** — `addTime` recalcule `newTarget = now + newRemaining*1000` à partir d'un `newRemaining`
  déjà tronqué → dérive cumulative à chaque ±30 s.
- **B11** — `addTimerTime(seconds)` en chemin test (`context == null`) appelle
  `startTimerForTesting(seconds)`, sémantique fausse pour `-30`.
- **B12** — Ordre de déclaration : `countdownDisplayJob` (l.78) est déclaré *après* le bloc `init`
  (l.46) qui l'utilise indirectement. Sûr aujourd'hui (coroutine lancée après construction) mais fragile.
- **B13** — `elapsedSeconds` (chrono de séance) est incrémenté par tics `delay(1000)` au lieu d'être
  calculé sur l'horloge murale (`startTime`) → dérive si le process est suspendu. Hors timer de repos
  strict, mais même classe de bug.

---

## 3. Améliorations d'architecture proposées

1. **Source de vérité unique = `targetMillis`.** Tout l'affichage (UI + notif) se dérive de
   `targetMillis` via une fonction pure `remaining(now)` arrondie au plafond. Supprimer les boucles
   de polling concurrentes.

2. **Centraliser les transitions dans `RestTimerManager`** et n'exposer que des intentions :
   `start(duration)`, `addTime(delta)`, `skip()`, `onAlarmFired()`. Les effets de bord
   (alarme, notif, vibration, persistance, service) sont orchestrés **au même endroit**, jamais
   par le ViewModel/Service. Supprimer l'aller-retour `Idle` (B1).

3. **Le Service ne fait qu'afficher**, il ne décide rien : il observe `Counting` et publie une notif
   chronomètre ; sur `Finished`/`Idle` il s'arrête. Pas de boucle de notify manuelle.

4. **Vibration bornée + `deleteIntent`** pour garantir l'arrêt quel que soit le geste utilisateur.

5. **Découpler le testable du framework.** Injecter une `Clock`/`TimeProvider` et une abstraction
   `TimerEffects` (alarme/notif/vibration) pour tester la logique sans `Context`, et supprimer les
   méthodes `…ForTesting` qui polluent l'API de prod.

6. **Chrono de séance sur horloge murale** (dériver de `session.startTime`) pour éliminer la dérive (B13).

---

## 4. Plan de nettoyage & débogage (par étapes, testables)

| Étape | Contenu | Risque |
|------|---------|--------|
| **P0** | Filet de sécurité : tests unitaires sur la logique d'état (start / addTime±/ skip / finish) avec `Clock` injectée. | faible |
| **P1** | B3 + B6 : arrondi au plafond + source unique `targetMillis`. Corrige l'affichage. | faible |
| **P2** | B1 : `resetEffects(silent)` interne, transition directe `Counting→Counting`, supprime le flicker UI/notif. | moyen |
| **P3** | B2 + B4 : `deleteIntent` + vibration bornée ; définir le contrat `-30s`/skip. | faible |
| **P4** | B5 : retirer la boucle `notify` du Service, garder le chronomètre natif. | faible |
| **P5** | B8 + B11 + B12 : nettoyage manifest, chemins test, ordre de déclaration. | faible |
| **P6** | Refactor architecture (sections 3.2/3.5) : centralisation des transitions + injection. | moyen |
| **P7** | (Optionnel) B7 BootReceiver, B13 chrono horloge murale. | moyen |

> Recommandation : commencer par **P0 → P1 → P2 → P3**, qui éliminent les symptômes les plus visibles
> en séance (clignotement, vibration infinie, affichage faux), avant le refactor P6.
