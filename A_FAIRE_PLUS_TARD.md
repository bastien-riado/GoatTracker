# À faire plus tard — éléments reportés

Ces 4 chantiers ont été **volontairement reportés** pendant la campagne de correctifs de l'audit
(voir `AUDIT.md`, `IMPLEMENTATION_PLAN.md`, `RECAP.md`). Chacun est décrit ici pour être repris dans
une session dédiée, sans avoir à tout re-déduire.

Ordre conseillé : **1 (applicationId)** avant toute publication Play ; **3 (cibles tactiles)** rapide
et utile ; **2 (i18n)** seulement si tu vises plusieurs langues ; **4 (Paint)** optionnel.

---

## 1. `applicationId` — préparer la publication Play 🔴 (bloquant pour Play)

**Contexte.** `app/build.gradle.kts` a `applicationId = "com.example.goattracker"` et
`namespace = "com.example.goattracker"`. Google Play **refuse** tout `applicationId` en `com.example.*`.

**À savoir — deux notions distinctes :**
- `applicationId` = l'**identité de l'app sur Play** (ne peut plus changer après publication). DOIT être
  un domaine réel inversé (ex. `com.bastienriado.goattracker`).
- `namespace` = le **package du code** (R, BuildConfig, classes). Play ne le regarde pas ; il peut rester
  `com.example.goattracker` sans bloquer la publication.

**À faire — option minimale (recommandée, faible risque) :**
1. Dans `app/build.gradle.kts` : `applicationId = "com.<ton-domaine>.goattracker"`. Laisser `namespace` tel quel.
2. Vérifier qu'aucun `Intent`/`PendingIntent`/provider ne code en dur l'ancien id (ici la nav notif passe
   par des classes, pas par l'applicationId → OK).

**À faire — option propre (renommer aussi le package du code, plus gros) :**
1. Android Studio → clic droit sur le package `com.example.goattracker` → **Refactor > Rename** (coche
   « rename package »). Met à jour imports, `namespace`, `AndroidManifest`, dossiers.
2. ⚠️ **Mettre à jour la règle R8** : `app/proguard-rules.pro` contient
   `-keepclassmembers enum com.example.goattracker.** { … }` → remplacer par le nouveau package.
3. Re-tester un **build release** (R8) + l'installation sur device (le package des classes `@Serializable`
   change).

**Vérification.** `:app:assembleRelease` OK, et le test « sauvegarde → réouverture » en release ne plante pas.
La config de signature est déjà en place (clés via `local.properties`, cf. `RECAP.md`).

---

## 2. Internationalisation (i18n) 🟡 (dette ; utile seulement si plusieurs langues)

**Contexte.** ~100 % des libellés sont des chaînes **françaises en dur** dans les Composables ; aucun
`strings.xml` n'est utilisé. Plusieurs `String.format("%.2f km", …)` n'ont pas de `Locale` (séparateur
décimal dépendant du téléphone). `android:supportsRtl="true"` est déclaré mais rien n'est traduisible.

**À faire :**
1. Extraire toutes les chaînes UI vers `app/src/main/res/values/strings.xml` (Android Studio :
   sélectionner la chaîne → ampoule → **Extract string resource**). Garder le français comme défaut
   (`values/`) ; ajouter `values-en/strings.xml` etc. pour d'autres langues.
2. Remplacer les littéraux par `stringResource(R.string.x)` dans les Composables (et `context.getString(...)`
   hors Compose, ex. notifications dans `RestTimerManager`).
3. Chaînes avec valeurs : ressources avec placeholders `%1$s` / `%1$d` + `stringResource(id, arg)`.
4. Pluriels (« 1 exercice » / « 2 exercices ») : utiliser des **plurals** (`pluralStringResource`).
5. Formats nombres/dates : `String.format(Locale.getDefault(), …)` ou `NumberFormat` / `DateTimeFormatter`.

**Risque.** Faible techniquement, **fastidieux** (beaucoup d'écrans). Bien vérifier l'affichage (longueurs
de texte variables, RTL via la pseudo-locale d'Android Studio).

**Vérification.** L'app reste identique en français ; tester une 2e langue + la pseudo-locale RTL.

---

## 3. Cibles tactiles ≥ 48 dp (accessibilité) 🟠 (rapide, à valider sur device)

**Contexte.** Dans `LiveWorkoutScreen.kt`, fonction `SetRowItem` (ligne de série) :
- la **case de validation** est un `Box` de **28 dp** cliquable,
- le **bouton suppression** est un `IconButton(modifier = Modifier.size(24.dp))` — ce `.size(24.dp)`
  **écrase** le minimum 48 dp par défaut d'`IconButton`.

Les deux sont **< 48 dp** (recommandation accessibilité + Play / Accessibility Scanner).

**À faire (agrandir la ZONE tactile sans forcément grossir le visuel) :**
- Case de validation : garder le carré visuel 28 dp, mais poser le `.clickable` sur un conteneur ≥ 48 dp
  (`Modifier.size(48.dp)` avec le visuel centré dedans), ou utiliser `Modifier.minimumInteractiveComponentSize()`.
- Bouton suppression : **retirer** le `Modifier.size(24.dp)` (laisser le défaut 48 dp ; l'icône reste 16 dp).

**⚠️ Pourquoi c'est reporté.** La colonne « CHECK » de la ligne est étroite (`weight(0.25f)`) ; passer ces
éléments à 48 dp peut **faire déborder ou serrer** la ligne dense. À **valider visuellement sur device**,
quitte à re-répartir les poids de colonnes (`SÉRIE / KG / REPS / CHECK`) ou revoir le layout de la ligne.

**Vérification.** Rendu sur device + **Accessibility Scanner** (Google Play Store) qui signale les cibles
< 48 dp.

---

## 4. Optimisation des `Paint` dans les graphiques 🟢 (optionnel, micro-optimisation)

**Contexte.** Les graphiques `Canvas` créent des `android.graphics.Paint()` **dans la boucle de dessin**
(un par point) :
- `app/src/main/java/com/example/goattracker/ui/profile/ProfileScreen.kt` : `OneRepMaxLineChart`,
  `MuscleRadarChart`, `SessionVolumesBarChart`,
- `app/src/main/java/com/example/goattracker/ui/exercise/ExerciseDetailScreen.kt` : `ExerciseProgressChart`.

**À faire.** Créer le(s) `Paint` **une seule fois** par dessin (hors du `forEachIndexed`) et le réutiliser ;
pour le radar (couleur/typeface qui changent par muscle), créer le Paint une fois et muter `.color`/`.typeface`
par itération.

**Priorité : basse.** Ces graphiques se redessinent au **changement de données**, pas à chaque frame →
l'impact réel est minime. À faire par propreté ou si un profilage montre un souci.

**Vérification.** Compile ; **aucun changement visuel** attendu.

---

*Rappel : pour l'analyse d'impact avant ces chantiers, le graphe graphify est dans `graphify-out/graph.json`
(reconstruction : voir la note mémoire graphify — le CLI nu exige une clé API, donc passer par le chemin AST Python).*
