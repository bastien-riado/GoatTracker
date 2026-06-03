# Plan d'implémentation — Audit GoatTracker

**Objectif :** traiter l'ensemble des findings de [AUDIT.md](AUDIT.md) (**hors i18n, reporté**) **sans introduire de régression**.

**Principe directeur :** chaque lot est livrable indépendamment, dans un ordre qui minimise le risque et le retravail. On stabilise le filet de tests *avant* de toucher au code sensible (le timer), et on garde le changement de configuration release **pour la fin**, sur un code déjà stable et validé par un vrai build release. Pour chaque lot : objectif → fichiers → **effets de bord à surveiller** (la discipline « ne pas créer de nouveau problème ») → **porte de vérification**.

---

## Décisions à valider (3 bifurcations)

Je recommande un défaut pour chacune ; dis-moi si tu veux dévier.

1. **Persistance — garder JSON (recommandé) ou migrer vers Room ?**
   **Reco : garder le JSON** pour cette campagne de corrections, en ajoutant le versioning (P1-3) et le brouillon de séance (P0-1). Migrer vers Room est un chantier à part entière qui réécrit la source de vérité — exactement le genre de gros changement qui *crée* des bugs pendant qu'on en corrige. Room reste la **cible long terme** (il fait disparaître P1-3 et P2-8), mais dans un lot dédié et délibéré, pas ici.

2. **Alarme — garder `USE_EXACT_ALARM` (risque Play) ou passer en inexact ?**
   **Reco : passer en inexact** (`setAndAllowWhileIdle`, sans gate Play). Le service au premier plan conserve la précision tant que le process vit ; on ne perd quelques secondes que dans le cas « process tué **et** Doze ». À garder en exact seulement si cette dérive écran-éteint est inacceptable côté produit (et alors il faut assumer la justification Play).

3. **1RM — exposer le choix de formule à l'UI, ou retirer Brzycki/Lander ?**
   `BRZYCKI`/`LANDER` sont codés et testés mais jamais branchés. **Reco : soit** un sélecteur de formule dans Profil/Détail (valorise le code existant), **soit** suppression du code mort. Décision produit.

---

## Ordre & dépendances

```
1. Quick wins isolés       (P0-2, P2-12)        ← valeur immédiate, risque quasi nul
2. Filet de tests          (P2-6)               ← rend la suite VM exécutable
3. Extraction RestTimer    (P2-5)               ← supprime les hacks de test, dé-risque la suite
4. Persistance séance      (P0-1)               ← sur un VM testé et propre
5. Versioning + récup      (P1-3)               ← même couche que 4 (adjacent)
6. Threading + debounce    (P2-7, P2-8)         ← le debounce sert la persistance de 4
7. Cohérence VMs           (P2-9, P2-10, P2-11) ← en touchant chaque VM
8. Release & permissions   (P1-4)               ← EN DERNIER, code stable + build release réel
9. Polish                  (P3)                 ← au fil de l'eau
```

| Lot | Findings | Sévérité | Effort |
|-----|----------|----------|--------|
| 1 | P0-2, P2-12 | Critique / Moyen | S |
| 2 | P2-6 | Moyen | S |
| 3 | P2-5 | Moyen | M |
| 4 | P0-1 | Critique | M |
| 5 | P1-3 | Élevé | M |
| 6 | P2-7, P2-8 | Moyen | S |
| 7 | P2-9, P2-10, P2-11 | Moyen | M |
| 8 | P1-4 | Élevé | M |
| 9 | P3 + UX/A11y | Mineur | M |

---

## Lot 1 — Quick wins isolés (P0-2, P2-12)

**Objectif.** Corriger la saisie Temps/Distance non enregistrée, et passer la collecte des flux en lifecycle-aware.

**Changements.**
- `LiveWorkoutScreen.kt` : élargir `onUpdateSetValues` à `(setId, weight, reps, durationSeconds, distanceKm)` ; câbler les branches `TIME` et `DISTANCE` de `SetRowItem` vers `viewModel.updateSetValues(...)` ; **masquer le champ reps** pour TIME/DISTANCE (il n'a pas de sens).
- Remplacer `collectAsState()` par `collectAsStateWithLifecycle()` dans tous les écrans (`MainScreen`, `LiveWorkoutScreen`, `ProfileScreen`, `ExerciseDetailScreen`, `SessionsListScreen`, et le `pendingNav` de `Navigation.kt`).

**Effets de bord à surveiller.**
- Le champ TIME affiche des **minutes** (`set.durationSeconds / 60`) → la saisie perd la précision sous la minute. Décider : saisie `mm:ss` ou accepter le pas d'une minute. Convertir minutes → `durationSeconds` au moment d'écrire (ne pas stocker des minutes dans un champ « secondes »).
- `collectAsStateWithLifecycle` arrête la collecte en arrière-plan : vérifier que le retour sur l'écran live ré-affiche bien le timer de repos (il est piloté par le `RestTimerManager` via la boucle du VM, pas par un flux d'écran — donc OK, mais à confirmer).

**Vérification.** Test VM par type de suivi (saisie TIME/DISTANCE persistée). Manuel : créer un exercice TIME, logger une durée, terminer, rouvrir → la valeur est là.

---

## Lot 2 — Filet de tests (P2-6)

**Objectif.** Rendre `LiveWorkoutViewModelTest` exécutable (fin du hang) **avant** de toucher au timer.

**Changements.**
- `MainDispatcherRule.kt` : exposer le `scheduler` de la règle et le partager avec `runTest` (ou construire la règle depuis le scheduler du test) → un seul horloge virtuelle.
- Garder temporairement la boucle `startElapsedTimer` bornée/annulable en test (le correctif propre vient au Lot 3 via l'injection de ticker).

**Effets de bord.** Changer le scheduler peut faire bouger des tests existants (certains passent « par accident »). Relancer toute la suite et lire les diffs.

**Vérification.** `gradlew testDebugUnitTest` se termine sans hang ; suite verte = filet en place.

---

## Lot 3 — Extraction de l'interface RestTimer (P2-5)

**Objectif.** Sortir l'orchestration timer (objet global + service) derrière une interface injectée → supprime `applicationContext` du VM, supprime `startTimerForTesting`/`acknowledgeForTesting` et le `if (applicationContext == null)`, et rend le timer testable.

**Changements.**
- Définir `interface RestTimer { val state: StateFlow<…>; fun start(durationSeconds); fun acknowledge(); fun cancelAll(); fun remainingSeconds(): Int }`.
- Implémentation Android `AndroidRestTimer` = le `RestTimerManager` + `RestTimerService` **inchangés derrière l'interface**. Fake en test.
- `LiveWorkoutViewModel` reçoit `RestTimer` (et une source de temps/ticker injectable pour la boucle elapsed).

**Effets de bord à surveiller (zone délicate — B1–B5).**
- Le timer est finement réglé : extraire **sans changer le comportement**. Ne pas réécrire la logique d'alarme/vibration/notif — juste la déplacer derrière l'interface.
- Ajouter des tests de caractérisation sur les parties pures **avant** de bouger (`getRemainingSeconds` arrondi au plafond, transitions Counting→Finished→Idle idempotentes) pour figer le comportement actuel.

**Vérification.** Tests de caractérisation verts avant/après. Manuel : valider une série → barre de repos, « Passer », fin de repos + vibration, enchaînement de séries (pas de flicker — B1).

---

## Lot 4 — Persistance de la séance en cours (P0-1)

**Objectif.** La séance active survit à la mort du process.

**Changements.**
- Persister un **brouillon de séance** à chaque mutation : champ `activeDraft: WorkoutSession?` dans `WorkoutState` (sur disque) — plus robuste qu'un `SavedStateHandle` face à un kill piloté par le service.
- Au démarrage de `LiveWorkoutScreen`, **reprendre** le brouillon s'il existe au lieu de `startNewSession()`.
- Vider le brouillon sur `confirmSaveSession`, `discardSession`, et au démarrage explicite d'une nouvelle séance.

**Effets de bord à surveiller.**
- **Ne pas ressusciter une séance explicitement abandonnée** (discard doit effacer le brouillon).
- Brouillon référençant un exercice supprimé depuis → filtrer à la reprise.
- Interaction avec le cold-start par notification (`MainActivity`) : aujourd'hui ça démarre une séance vide ; ça doit désormais **restaurer le brouillon**.
- Écrire à chaque mutation = beaucoup d'écritures → **dépend du Lot 6** (debounce). Faire le Lot 6 juste après (ou ensemble).

**Vérification.** Logger 5 séries → « Ne pas conserver les activités » + `adb shell am kill <pkg>` → rouvrir : la séance est intacte. Discard → kill → rouvrir : pas de résurrection.

---

## Lot 5 — Versioning + récupération de données (P1-3)

**Objectif.** Plus de perte totale silencieuse sur dérive de schéma.

**Changements.**
- Ajouter `schemaVersion: Int = 0` à `WorkoutStateDto` (**défaut obligatoire** pour que les fichiers existants, sans ce champ, parsent encore).
- Sur parse échoué (`loadFromDisk`), **renommer** `workouts.json` → `workouts.corrupt-<ts>.json` avant de réinitialiser (récupération possible) plutôt que d'écraser.
- Fonction de migration `vN → vN+1` (vide aujourd'hui, mais le squelette + le test existent).

**Effets de bord à surveiller.**
- `ignoreUnknownKeys = true` **ne couvre pas** un champ requis manquant → tout nouveau champ persisté doit avoir un défaut.
- Tester la lecture d'un **fichier d'ancienne version** (fixture) pour garantir la non-régression des installs existantes.
- Le renommage peut échouer (I/O) → gérer le cas.

**Vérification.** Test : fichier corrompu → réinit + fichier `.corrupt-*` créé, pas de crash. Test : fichier « v0 » (sans `schemaVersion`) → chargé correctement.

---

## Lot 6 — Threading & debounce (P2-7, P2-8)

**Objectif.** Sortir les calculs du thread principal et arrêter de réécrire tout le fichier à chaque frappe.

**Changements.**
- `.flowOn(Dispatchers.Default)` sur les transformations `combine` lourdes (MainScreen, et Profile/ExerciseDetail une fois convertis).
- `debounce(~250 ms)` sur la recherche et sur l'écriture disque (écrire au plus une fois / ~500 ms, **avec écriture finale garantie** à la sortie pour ne pas perdre la dernière modif).
- `updateNotes` : debounce l'upsert (aujourd'hui une réécriture complète par caractère).

**Effets de bord à surveiller.**
- `flowOn(Default)` : s'assurer qu'aucun état Compose n'est touché dans ces transformations (elles sont pures — OK).
- Debounce d'écriture : garantir le **flush final** (sortie d'écran, process qui part) sinon perte de la dernière saisie.
- Debounce de recherche : le rendre neutralisable en test (sinon flakiness).

**Vérification.** Profiler la frappe dans la recherche (pas de calcul sur Main). Vérifier qu'une note saisie puis sortie immédiate est bien persistée.

---

## Lot 7 — Cohérence des ViewModels (P2-9, P2-10, P2-11)

**Objectif.** Un seul pattern, réutilisation de l'abstraction 1RM, événements one-shot propres.

**Changements.**
- Convertir `ProfileViewModel`, `ExerciseDetailViewModel` (et la partie réactive de `LiveWorkoutViewModel`) au pattern `combine + stateIn(WhileSubscribed) + sealed UiState(Loading/Error/Success)`.
- Router tous les calculs 1RM via `OneRepMaxFormula.EPLEY.strategy` (supprimer la formule Epley dupliquée dans `ExerciseDetailViewModel`).
- Remplacer `isSaved`/`isDeleted` (état) par des **événements one-shot** (`Channel`/`SharedFlow(replay=0)`) consommés une fois ; adapter les `LaunchedEffect` des écrans.

**Effets de bord à surveiller.**
- Passer Profile/Détail en `sealed UiState` change le `when` des écrans → adapter le rendu, vérifier l'absence de régression (notamment le contournement `isDeleted` → doit toujours naviguer en arrière correctement).
- Les one-shot events ne doivent pas se reperdre à la rotation (les collecter avec le bon scope).

**Vérification.** Tests VM par état. Manuel : créer/supprimer un exercice → navigation correcte, pas de double-déclenchement à la rotation.

---

## Lot 8 — Release & permissions (P1-4) — EN DERNIER

**Objectif.** Build release publiable et durci.

**Changements.**
- `app/build.gradle.kts` : `isMinifyEnabled = true`, `isShrinkResources = true` en release ; règles `-keep` (kotlinx.serialization, NavKey `@Serializable`, enums utilisés par `valueOf`).
- Ajouter un `signingConfig` release (clés hors VCS, via `local.properties`/env).
- Selon la **décision #2** : passer l'alarme en inexact et retirer `USE_EXACT_ALARM` (+ réévaluer `FOREGROUND_SERVICE_SPECIAL_USE`).

**Effets de bord à surveiller (haut risque — ne casse qu'en release).**
- R8 casse typiquement la **désérialisation** (sérialisation, NavKeys, `enum.valueOf` par réflexion) → ça ne plante **qu'en build minifié**, pas en debug. D'où la position en dernier et la vérification renforcée.
- Ne pas committer keystore/mots de passe.
- Alarme inexacte : accepter la dérive process-tué + Doze (cf. décision #2).

**Vérification (obligatoire).** Build **release** réel, puis parcours complet : cold start → créer un exercice → logger et **enregistrer** une séance → tuer/rouvrir → la séance se **désérialise** sans crash. C'est le scénario qui attrape les casses R8.

---

## Lot 9 — Polish (P3 + UX/A11y)

**Objectif.** Finitions sans risque.

**Changements.** `applicationId` en domaine réel ; kotlinx-serialization-json dans `libs.versions.toml` ; hisser les `Paint` hors des boucles de dessin des charts ; réduire/supprimer le splash forcé 1,4 s ; `getLatestState()` non-`suspend` ; **A11y** : `contentDescription`/sémantique sur les charts Canvas, agrandir les cibles tactiles < 48 dp (case de validation, icônes suppression), éclaircir `Meta` pour le contraste ; assumer ou configurer `allowBackup` ; renommer `MainScreenViewModelTest.uiState_initiallyLoading`.

**Vérification.** Scanner d'accessibilité + test TalkBack rapide sur un écran avec chart. Build debug + release OK.

---

## Note de méthode

i18n est volontairement reporté ; les nouveaux libellés (ex. feedback « aucune série validée ») restent en français en dur, cohérents avec l'existant, jusqu'au lot i18n dédié. Chaque lot doit finir **vert** (tests) et être commité séparément pour faciliter la revue et le rollback.
