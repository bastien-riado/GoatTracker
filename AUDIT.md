# Audit technique — GoatTracker

**Date :** 2026-06-03 · **Périmètre :** intégralité du module `:app` (architecture, Compose, Coroutines/Flow, couche données, cycle de vie, service/alarme, tests) + retours fonctionnels et UX/UI.
**Méthode :** revue du code source (lecture intégrale), pas d'exécution. Chaque point indique **le problème → la conséquence concrète → le correctif**, avec la référence `fichier:ligne`.

---

## 1. Résumé exécutif

GoatTracker n'est **pas** du code de junior. La séparation DTO/domaine est propre, l'écriture disque est atomique, le timer de repos est une vraie pièce d'ingénierie (transition `reachZero` idempotente, `PendingIntent` d'alarme construit à l'identique pour que `cancel()` matche, vibration bornée, migration de canal de notif), et le bug de scoping de ViewModel nav3 a été correctement diagnostiqué et corrigé. Les commentaires « audit B1–B5 » montrent qu'un travail sérieux a déjà eu lieu sur la partie la plus dure.

Le profil de risque n'est donc **pas** la qualité d'implémentation locale — c'est l'**asymétrie de robustesse** et la **publiabilité** :

- On a investi énormément pour que le *timer* survive à la mort du process… mais **la séance en cours, elle, ne survit pas** (perte de données silencieuse).
- Des **types de suivi entiers (Temps / Distance) ne sont pas enregistrables** dans une séance live — bug fonctionnel direct.
- La **persistance n'a ni version ni migration** : un simple renommage d'enum efface tout l'historique de l'utilisateur, en silence.
- Le **build release n'est pas publiable en l'état** (pas de R8, pas de signing config) et deux permissions (`USE_EXACT_ALARM`, `FOREGROUND_SERVICE_SPECIAL_USE`) exposent à un **refus Google Play**.

Aucun de ces points n'est visible « en faisant tourner l'app sur son téléphone » — ils apparaissent à la rotation, à la mort du process, sur un exercice cardio, en build minifié, ou au moment de publier. C'est exactement le périmètre d'un audit senior.

**Verdict :** base solide, mais 4 points P0/P1 à traiter avant toute mise en production, et une dette d'architecture moyenne (incohérence des patterns ViewModel, calculs sur le thread principal) à éponger avant que l'app grossisse.

---

## 2. Points forts (à préserver)

- **Couche données** : `interface DataRepository` + impl injectable (dispatcher/scope en paramètres) → testable hors Android. Écriture **atomique** temp-file + `renameTo` ([DataRepository.kt:114](app/src/main/java/com/example/goattracker/data/DataRepository.kt)), `Mutex`, dispatcher IO, récupération sur parse échoué.
- **DTO ↔ domaine** séparés avec mappers explicites ([WorkoutDtos.kt](app/src/main/java/com/example/goattracker/data/dto/WorkoutDtos.kt)) — exactement le bon découpage.
- **`MainScreenViewModel`** : `combine + stateIn(WhileSubscribed(5s)) + sealed UiState(Loading/Success/Error)` ([MainScreenViewModel.kt:31](app/src/main/java/com/example/goattracker/ui/main/MainScreenViewModel.kt)) — le pattern UDF de référence.
- **nav3** : scoping ViewModel par entrée via `rememberViewModelStoreNavEntryDecorator()` ([Navigation.kt:55](app/src/main/java/com/example/goattracker/Navigation.kt)) — correctif juste, bien commenté.
- **Timer de repos** : architecture Service (précis, premier plan) + AlarmManager (secours mort-du-process) + `RestTimerManager` (machine à états), `reachZero` idempotent ([RestTimerManager.kt:157](app/src/main/java/com/example/goattracker/ui/live/RestTimerManager.kt)). Sophistiqué et correct.
- **UX de saisie** : pré-remplissage des séries depuis la dernière séance, `BringIntoViewRequester` dans `AppTextField` pour remonter le champ au-dessus du clavier.

---

## 3. Findings P0 / P1 (à traiter avant prod)

### P0-1 — La séance en cours est perdue à la mort du process
**Problème.** `LiveWorkoutUiState.activeSession` vit uniquement dans le ViewModel ([LiveWorkoutViewModel.kt:38](app/src/main/java/com/example/goattracker/ui/live/LiveWorkoutViewModel.kt)). Elle n'est écrite dans le repository qu'au moment de « Enregistrer » (`confirmSaveSession`). Aucun `SavedStateHandle`, aucune persistance intermédiaire.
**Conséquence.** Scénario réel : l'utilisateur logue 6 séries, met le tél en poche pendant le repos (écran éteint), l'OS tue le process pour récupérer de la RAM. Au retour — y compris **via la notif « Repos terminé » qui le ramène justement sur l'écran live** — `startNewSession()` démarre une séance **vide** ([LiveWorkoutScreen.kt:71](app/src/main/java/com/example/goattracker/ui/live/LiveWorkoutScreen.kt)). **Toute la séance est perdue, en silence.** C'est l'ironie de l'app : le *timer* survit à la mort du process (SharedPreferences), mais la séance qu'il chronomètre, non.
**Correctif.** Persister la séance active dès qu'elle change : soit dans `SavedStateHandle` (sérialiser `WorkoutSession` en JSON), soit comme « brouillon de séance » dans le repository (un champ `activeDraft` dans `WorkoutState`). Au démarrage de l'écran, reprendre le brouillon s'il existe au lieu de `startNewSession()`. Tester avec « Ne pas conserver les activités » + `adb shell am kill`.

### P0-2 — Les exercices Temps et Distance ne sont pas enregistrables en séance
**Problème.** Dans `SetRowItem`, seuls `WEIGHT_REPS` (poids) et le champ reps appellent `onUpdateValues`. Les branches `TIME` (`minutesText`) et `DISTANCE` (`distText`) ne mettent à jour que l'état local `remember` et **n'appellent jamais le ViewModel** ([LiveWorkoutScreen.kt:941](app/src/main/java/com/example/goattracker/ui/live/LiveWorkoutScreen.kt) et :950). De plus le lambda `onUpdateSetValues` ne transporte que `(weight, reps)` — `durationSeconds`/`distanceKm` ne remontent jamais, alors que `updateSetValues` les supporte.
**Conséquence.** Pour un exercice cardio/gainage, ce que l'utilisateur tape dans le champ valeur est **silencieusement jeté** ; pire, un champ « REPS » s'affiche alors qu'il n'a pas de sens. Le volume Temps utilise donc toujours la valeur par défaut (60 s). Un type de suivi entier est cosmétique.
**Correctif.** Élargir `onUpdateSetValues` à `(weight, reps, durationSeconds, distanceKm)` et câbler les branches TIME/DISTANCE vers `viewModel.updateSetValues(...)`. Masquer le champ reps pour TIME/DISTANCE. Ajouter un test live VM par type de suivi.

### P1-3 — Persistance sans version ni migration → perte totale silencieuse
**Problème.** La désérialisation reconstruit les enums par nom : `ExerciseCategory.valueOf(category)` / `TrackingType.valueOf(...)` ([WorkoutDtos.kt:26](app/src/main/java/com/example/goattracker/data/dto/WorkoutDtos.kt)). En cas de parse échoué, `loadFromDisk` retombe sur `WorkoutState(exercises = defaultExercises())` ([DataRepository.kt:96](app/src/main/java/com/example/goattracker/data/DataRepository.kt)). Aucun numéro de schéma, aucune migration, aucune sauvegarde du fichier corrompu.
**Conséquence.** Le jour où une constante d'enum est renommée/supprimée, où un champ requis est renommé, ou où le fichier est partiellement corrompu, **tout l'historique de l'utilisateur est remplacé par les exercices par défaut, sans le moindre signal.** C'est le risque d'intégrité de données le plus grave puisque ce fichier est la source de vérité unique.
**Correctif.** (1) Ajouter un champ `schemaVersion` dans `WorkoutStateDto` et une fonction de migration. (2) Sur parse échoué, **renommer** `workouts.json` en `workouts.corrupt-<ts>.json` avant de réinitialiser (récupération possible). (3) Donner des valeurs par défaut aux listes (`exercises = emptyList()` déjà ; vérifier la robustesse aux clés manquantes). (4) Envisager `@SerialName` stable sur les champs pour découpler noms Kotlin et clés JSON.

### P1-4 — Build release non publiable + risque de refus Play
**Problème.**
- `isMinifyEnabled = false` en release ([app/build.gradle.kts:20](app/build.gradle.kts)) : pas de R8, pas de shrink, pas d'obfuscation ; `proguard-rules.pro` n'est jamais exercé (activer R8 plus tard cassera vraisemblablement kotlinx.serialization sans règles `-keep`). Aucun `signingConfig` release → `assembleRelease` signe avec la clé debug (non publiable).
- `USE_EXACT_ALARM` ([AndroidManifest.xml:11](app/src/main/AndroidManifest.xml)) est **réservé par Google Play** aux apps d'alarme/horloge/agenda ; une app de fitness risque le refus. `FOREGROUND_SERVICE_SPECIAL_USE` exige une justification revue manuellement par Google.
**Conséquence.** L'app ne peut pas être publiée telle quelle, et même corrigé côté signing, le couple de permissions peut bloquer la validation Play.
**Correctif.** Activer R8 + `shrinkResources` en release, ajouter les règles `-keep` kotlinx.serialization et **tester un build release** (cf. la classe de pièges « ça marche en debug, ça crashe en prod »). Ajouter un `signingConfig`. Réévaluer le besoin d'`USE_EXACT_ALARM` : pour un timer de repos, `setAndAllowWhileIdle` (inexact, autorisé sans gate) + le service précis au premier plan suffisent souvent ; ne garder l'exact que si la dérive de quelques secondes écran éteint est inacceptable, et documenter la justification.

---

## 4. Findings P2 (dette d'architecture / qualité)

### P2-5 — ViewModel qui tient un `Context` + chemins de code réservés aux tests
`LiveWorkoutViewModel(… applicationContext: Context? = null)` orchestre directement `RestTimerManager` (objet global) et `RestTimerService`. Pour rester testable, le code multiplie les hacks : `startTimerForTesting`, `acknowledgeForTesting`, et le `if (applicationContext == null) break` dans la boucle de comptage ([LiveWorkoutViewModel.kt:111](app/src/main/java/com/example/goattracker/ui/live/LiveWorkoutViewModel.kt)). **De la logique de test fuit dans le code de production.**
**Correctif.** Extraire une interface `RestTimer` (start/cancel/acknowledge/observeState/remainingSeconds) ; injecter l'implémentation Android réelle en prod et un fake en test. Le ViewModel n'a alors plus besoin de `Context` ni de méthodes `*ForTesting`.

### P2-6 — Le hang de test documenté : cause racine
`startElapsedTimer` lance `while (true) { … ; delay(1000) }` **sans garde** ([LiveWorkoutViewModel.kt:149](app/src/main/java/com/example/goattracker/ui/live/LiveWorkoutViewModel.kt)) — contrairement à la boucle de comptage qui, elle, a reçu le garde `applicationContext == null`. Sous `runTest`/temps virtuel, cette boucle ne devient jamais inactive. Aggravé par `MainDispatcherRule` qui crée **son propre** `UnconfinedTestDispatcher()` (scheduler distinct de celui de chaque `runTest` — [MainDispatcherRule.kt:14](app/src/test/java/com/example/goattracker/MainDispatcherRule.kt)), donc le contrôle du temps virtuel est éclaté entre deux schedulers.
**Conséquence.** La classe `LiveWorkoutViewModelTest` peut bloquer indéfiniment → CI inexploitable sur cette suite.
**Correctif.** Injecter une source de temps / un ticker (cf. P2-5) plutôt qu'un `delay` infini non gardé ; faire partager **un seul** scheduler entre la règle et `runTest` (passer `mainDispatcherRule.testDispatcher.scheduler` à `runTest`, ou construire la règle à partir du scheduler du test).

### P2-7 — Calculs lourds sur le thread principal, sans debounce
`MainScreenViewModel` recalcule **toutes** les stats par exercice à chaque frappe de recherche ([MainScreenViewModel.kt:43](app/src/main/java/com/example/goattracker/ui/main/MainScreenViewModel.kt)) ; `ProfileViewModel` et `ExerciseDetailViewModel` recalculent en O(séances × exercices × séries) à chaque émission. Tout cela tourne dans `viewModelScope` (dispatcher **Main** par défaut) car aucun `flowOn(Dispatchers.Default)` n'est posé avant `stateIn`/`collect`. Aucun `debounce` sur la recherche.
**Conséquence.** Imperceptible aujourd'hui ; saccades garanties quand l'historique grossit (la question « 10 ou 10 000 éléments ? »).
**Correctif.** `.flowOn(Dispatchers.Default)` sur les transformations `combine`, `debounce(~250ms)` sur la recherche, et mémoïser les stats par exercice.

### P2-8 — Réécriture de tout le fichier à chaque mutation
`saveToDisk()` resérialise l'intégralité de `WorkoutState` à chaque changement ([DataRepository.kt:106](app/src/main/java/com/example/goattracker/data/DataRepository.kt)). Or `ExerciseDetailViewModel.updateNotes` appelle `addExercise` (upsert → réécriture complète) **à chaque caractère** tapé dans les notes ([ExerciseDetailViewModel.kt:183](app/src/main/java/com/example/goattracker/ui/exercise/ExerciseDetailViewModel.kt)).
**Conséquence.** Amplification d'écriture O(historique total) par frappe. Dégrade avec la taille des données et l'usure flash.
**Correctif.** Debouncer les écritures (collecter `_workoutState` et écrire au plus une fois / ~500 ms) ; à terme, migrer vers **Room** (source de vérité réactive, écritures incrémentales, migrations versionnées) — ce qui résout aussi P1-3.

### P2-9 — Patterns ViewModel incohérents
Deux écoles cohabitent : `combine + stateIn + sealed UiState` (MainScreen, SessionsList) vs `MutableStateFlow + init{collect}` avec data class à valeurs par défaut, **sans Loading/Error** (Profile, LiveWorkout, ExerciseDetail). 
**Correctif.** Choisir un seul pattern (le premier) et l'appliquer partout — cohérence = maintenabilité.

### P2-10 — Formule d'Epley dupliquée au lieu de réutiliser la stratégie testée
`ExerciseDetailViewModel` réécrit `set.weight * (1.0 + set.reps / 30.0)` en dur ([ExerciseDetailViewModel.kt:80](app/src/main/java/com/example/goattracker/ui/exercise/ExerciseDetailViewModel.kt)) alors que `ProfileViewModel` utilise `OneRepMaxFormula.EPLEY.strategy` ([OneRepMaxStrategy.kt](app/src/main/java/com/example/goattracker/domain/OneRepMaxStrategy.kt)), abstraction propre **et testée**. Par ailleurs `BRZYCKI`/`LANDER` sont testés mais jamais exposés à l'UI (fonctionnalités mortes).
**Correctif.** Router tous les calculs 1RM via `OneRepMaxFormula`. Décider : exposer le choix de formule à l'UI, ou retirer les stratégies inutilisées.

### P2-11 — Événements one-shot stockés dans l'état
`CreateExerciseUiState.isSaved` ([CreateExerciseViewModel.kt:22](app/src/main/java/com/example/goattracker/ui/create/CreateExerciseViewModel.kt)) et `ExerciseDetailUiState.isDeleted` pilotent la navigation via `LaunchedEffect(state)`. Ça marche ici grâce au scoping par entrée (réentrée = VM neuf), mais c'est l'anti-pattern classique : un changement de config alors que `isSaved == true` peut re-déclencher la navigation.
**Correctif.** Modéliser ces signaux comme événements one-shot (`Channel`/`SharedFlow(replay=0)`) consommés une fois.

### P2-12 — `collectAsState()` au lieu de `collectAsStateWithLifecycle()`
Tous les écrans collectent avec `collectAsState()` ([MainScreen.kt:62](app/src/main/java/com/example/goattracker/ui/main/MainScreen.kt), LiveWorkoutScreen, ProfileScreen, …) — donc la collecte **et les recalculs** continuent en arrière-plan. La dépendance `lifecycle-runtime-compose` est **déjà présente** et MainActivity utilise déjà `collectAsStateWithLifecycle`. Correctif trivial, gain batterie réel (surtout combiné à P2-7).

---

## 5. Findings P3 (mineurs / polish)

- **`applicationId = "com.example.goattracker"`** ([app/build.gradle.kts:11](app/build.gradle.kts)) : package placeholder `com.example` — non publiable sur Play, à renommer en domaine réel.
- **kotlinx-serialization-json en dur** (`"…:1.6.3"`, [app/build.gradle.kts:87](app/build.gradle.kts)) hors du catalogue de versions, et en retard par rapport à Kotlin 2.3.20 → l'aligner via `libs.versions.toml`.
- **`Paint` alloués dans la boucle de dessin** des charts ([ProfileScreen.kt:465](app/src/main/java/com/example/goattracker/ui/profile/ProfileScreen.kt), ExerciseDetailScreen) : un `android.graphics.Paint` créé par point/frame → hisser hors du `forEach`.
- **Splash artificiel de 1,4 s** imposé à chaque démarrage à froid ([MainActivity.kt:72](app/src/main/java/com/example/goattracker/MainActivity.kt)) — les utilisateurs détestent l'attente forcée ; réduire ou supprimer.
- **`getLatestState()` est `suspend`** sans raison (retourne `.value`).
- **`allowBackup="true"`** par défaut sans règles de sauvegarde — sauvegarde cloud du JOSN de séances ; au moins l'assumer explicitement.
- **`MainScreenViewModelTest.uiState_initiallyLoading`** teste en réalité l'état Success (nom trompeur).
- **`e.printStackTrace()`** comme seule journalisation (DataRepository, RestTimerManager) — passer par un logger, ou au moins être conscient que ces traces partent en stderr en prod.

---

## 6. Retours fonctionnels

- **(P0-1, P0-2 ci-dessus)** sont avant tout des bugs fonctionnels.
- **Séance sans série complétée non enregistrée silencieusement** : `confirmSaveSession` filtre les exercices sans série complétée ([LiveWorkoutViewModel.kt:374](app/src/main/java/com/example/goattracker/ui/live/LiveWorkoutViewModel.kt)) ; si rien n'est coché, « Enregistrer » ne sauvegarde rien **sans feedback**. Afficher un message (« aucune série validée »).
- **Modifier le `trackingType` d'un exercice après des séances** : les séries historiques (ex. WEIGHT_REPS) seront ré-affichées selon le nouveau type (ex. DISTANCE) → incohérences. Bloquer le changement de type si l'exercice a un historique, ou versionner le type par séance.
- **Pas de doublon d'exercice dans une séance** ([LiveWorkoutViewModel.kt:172](app/src/main/java/com/example/goattracker/ui/live/LiveWorkoutViewModel.kt)) : choix volontaire, mais empêche un même exercice répété (superset). À confirmer côté produit.
- **Détection de l'exercice auto-ajouté** par « premier id inconnu » ([LiveWorkoutViewModel.kt:61](app/src/main/java/com/example/goattracker/ui/live/LiveWorkoutViewModel.kt)) : heuristique fragile ; préférer un retour de navigation typé portant l'id créé.

---

## 7. UX/UI & Accessibilité

- **Charts non accessibles** : radar, courbes 1RM, barres de volume sont des `Canvas` **sans `contentDescription`/sémantique** → invisibles à TalkBack. Ajouter une description textuelle (ex. « 1RM en progression de X à Y kg »).
- **Cibles tactiles < 48 dp** : la case de validation de série fait 28 dp ([LiveWorkoutScreen.kt:994](app/src/main/java/com/example/goattracker/ui/live/LiveWorkoutScreen.kt)), les icônes supprimer 24 dp. Sous le minimum d'accessibilité ; agrandir la zone cliquable.
- **Contraste** : `Meta = #606060` sur `Bg #050505` ≈ 3,5:1, sous le seuil WCAG AA (4,5:1) pour du petit texte — et il est utilisé en 9–11 sp. Éclaircir `Meta` ou réserver aux gros textes.
- **Tailles 9–11 sp** très présentes : `sp` respecte le facteur d'échelle (bien), mais 9 sp reste minuscule. Remonter les minimas.
- **Thème sombre forcé**, `dynamicColor=false`, pas de thème clair ([Theme.kt:33](app/src/main/java/com/example/goattracker/theme/Theme.kt)) : choix de marque assumable, mais aucune option utilisateur ni Material You.
- **i18n** : 100 % des libellés sont des chaînes françaises **en dur** (aucun `stringResource`, aucun `strings.xml`) et plusieurs `String.format` sans `Locale` → séparateur décimal dépendant de la locale du téléphone. `supportsRtl="true"` est déclaré mais rien n'est traduisible. Pour une app FR-only c'est un choix, mais c'est un mur à la localisation ; extraire dans `strings.xml` (même un seul `values-fr`) dès maintenant coûte peu et évite un refactor douloureux plus tard.
- **`BackHandler` bloque le retour système** sur l'écran live ([LiveWorkoutScreen.kt:64](app/src/main/java/com/example/goattracker/ui/live/LiveWorkoutScreen.kt)) et le redirige vers la modale de fin — protège des pertes accidentelles (OK), mais vérifier le rendu avec le **retour prédictif** (Android 13+/14).
- **Bons points UX** : pré-remplissage des séries, repli des cartes terminées, modale de récap de fin, action « Passer » sur le repos, retour haptique sur validation.

---

## 8. Tests

- **Couverture correcte** du repository, `MainScreenViewModel`, `OneRepMaxStrategy`, `CreateExerciseViewModel`, `ProfileViewModel`, et la logique de `LiveWorkoutViewModel`. Le repo injectable rend tout ça testable hors émulateur — bon réflexe.
- **Bloquant : le hang `LiveWorkoutViewModelTest`** (cf. P2-6) rend la suite VM peu fiable en CI.
- **Machine à états du timer (`RestTimerManager`) non testée** — c'est pourtant la logique la plus complexe ; l'extraction d'interface (P2-5) la rendrait testable.
- **Un seul test UI Compose** (`MainScreenTest`) — léger mais acceptable à ce stade. Prioriser des tests de réducteur d'état (P0-2 par type de suivi) plutôt que viser une couverture chiffrée.

---

## 9. Plan d'action priorisé

| # | Sévérité | Action | Effort |
|---|----------|--------|--------|
| P0-1 | Critique | Persister la séance en cours (SavedStateHandle/brouillon repo) | M |
| P0-2 | Critique | Câbler la saisie Temps/Distance au ViewModel + masquer reps | S |
| P1-3 | Élevé | Version de schéma + sauvegarde du fichier corrompu (ou Room) | M |
| P1-4 | Élevé | Activer R8 + règles keep + signingConfig ; réévaluer permissions Play | M |
| P2-5/6 | Moyen | Interface `RestTimer` injectée → supprime les hacks de test + fixe le hang | M |
| P2-7/8 | Moyen | `flowOn(Default)` + `debounce` + écritures debouncées | S |
| P2-12 | Moyen | `collectAsStateWithLifecycle()` partout (dépendance déjà présente) | S |
| P2-9/10/11 | Moyen | Uniformiser les ViewModels, réutiliser `OneRepMaxFormula`, événements one-shot | M |
| P3 + UX | Mineur | applicationId, catalogue de versions, accessibilité charts/cibles, i18n, splash | M |

**Ordre conseillé :** P0-2 (rapide, bug visible) → P0-1 (perte de données) → P1-4 (publiabilité) → P1-3 (intégrité) → P2. Les P0/P1 sont les bloquants de mise en production ; les P2 sont la dette à éponger avant que l'app grossisse.
