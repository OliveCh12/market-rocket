# Audit et évolution Android

## Révision examinée

La branche `audit/arcade-android` part de la refonte locale `7628d6d`, dont le parent `9db7218` est la révision observée sur `main` chez GitHub. Cette base conserve le menu et le graphe bord à bord.

Le build debug et Android Lint ont été exécutés sur la base. Les neuf tests préexistants passaient. Trois tests écrits avant les corrections ont reproduit les écarts suivants.

| Observation sur la base | Reproduction | Correction |
| --- | --- | --- |
| Encaissement immédiat | Démarrer puis appeler `cashOut()` crédite 250 €, dès le premier appel. | L'encaissement est débloqué par un anneau collecté dans la manche en cours. Le moteur contrôle la règle et le HUD désactive le bouton. |
| Statut diminué par un achat | À 5 100 € en banque, acheter le premier niveau de capital débite 750 € et fait repasser de Freelance à Stagiaire. | Le classement local des paliers dépend des gains cumulés. Le solde disponible reste la seule source de paiement des achats. |
| Pas variable à 90 Hz | À entrées et durée identiques, le scénario finit à `(0.76992005, 0.3786206)` à 90 Hz et `(0.7699201, 0.378621)` à 120 Hz. L'écart observé est numérique. | Accumulateur et pas de 1/120 s, avec interpolation du dessin sur une période de simulation au maximum. |

## Vérifications par composant

| Composant | Observation et décision |
| --- | --- |
| `GameEngine` | L'ancien découpage utilisait le delta de chaque image. Le parcours rapprochait les portes avec la difficulté sans vérifier le déplacement entre deux anneaux. Le générateur isolé conserve une voie médiane libre et espace les portes selon le déplacement vertical et la vitesse maximale. Les contacts avec les anneaux utilisent le segment parcouru dans chaque pas. Les pics conservent leur collision géométrique au pas fixe. |
| `GameModels` | Le même montant servait à la fois de solde et de mesure de carrière. Le profil contient maintenant le solde, les gains cumulés et les records issus des manches terminées. |
| `GameViewModel` | Un instantané et un montant de HUD étaient recalculés à chaque image. Le prix affiché est rafraîchi environ 15 fois par seconde, avec mise à jour immédiate aux événements et changements de phase. Le moteur règle l'encaissement au montant exact. |
| `GameScreen` | Un retour au premier plan relançait le vol après avoir relâché la commande. Une interruption met le vol en pause, puis la reprise est explicite. La géométrie tient compte de la hauteur mesurée du HUD et des boutons. Le bouton peut afficher l'action et le montant sur deux lignes avec une grande police. |
| `MarketRocketCanvas` | Les chemins sont déjà réutilisés. Le code crée des dégradés d'anneaux, de pics et de limite, des traits et un motif de tirets pendant le dessin. Le coût GPU et les allocations propres aux fonctions de dessin restent à isoler avec une trace matérielle. Les objets de particule ne sont plus recopiés avant le dessin. |
| `StorageManager` | La lecture des préférences avait lieu lors de la création du ViewModel. DataStore charge le profil de façon asynchrone et écrit le profil et les réglages ensemble. |
| Gradle | SDK 35, build release de base et absence de CI. La branche utilise SDK de compilation et cible 36, AGP 8.10.1, Gradle 8.11.1, Kotlin 2.1.20 et JVM cible 17. R8 et la réduction des ressources sont activés en release. La CI définit un job de build et un job instrumenté sur Android 36. |

## Mesure d’allocations

Mesure sur JVM Temurin 21.0.13.1 sous Linux, avec le compteur d’octets alloués du thread courant. Médiane de cinq passages après trois échauffements. Le scénario de vol comporte 24 000 images simulées à 120 Hz avec la même séquence d’entrées. Le scénario d’impact mesure les 30 premières images après chacun de 120 krachs. Le protocole s’arrête à la production de l’instantané Kotlin.

| Scénario | Base `7628d6d` | Simulation et instantanés `9ef9296` |
| --- | ---: | ---: |
| Simulation seule | 236 o/image | 39 o/image |
| Simulation et instantané | 1 162 o/image | 682 o/image |
| Effets de krach et instantané | 3 001 o/image | 73 o/image |

Les listes immuables de géométrie sont réutilisées entre les changements. Chaque particule conserve son point de départ, sa vitesse et son heure de création. Le dessin en déduit l’âge et la position. Ces résultats décrivent les allocations de la JVM dans le protocole ci-dessus. La cadence de rendu, le temps GPU et l’empreinte totale du processus restent à mesurer sur téléphone.

Pour relancer le scénario de la version courante avec le même protocole, exécuter `MARKET_ROCKET_PROFILE=1 ./gradlew :app:testDebugUnitTest --tests '*EngineAllocationProbe' --rerun-tasks`. Les valeurs sont dans le champ `system-out` du rapport XML correspondant. Les parcours ont évolué après la mesure isolée au commit `9ef9296`.

## Règles de jeu et sauvegarde

- Un anneau débloque l’encaissement pour toute la manche. Une série perd son multiplicateur après un anneau manqué ou un contact, même absorbé par le bouclier. Chaque anneau consécutif ajoute 10 % à la valeur de son bonus, jusqu’à 50 %. La croissance passive et la durée de vie du bouclier suivent les règles précédentes.
- Le tutoriel du premier encaissement réagit à la montée, à la descente et à la collecte. Le bouton Relancer démarre immédiatement une nouvelle manche. Les sons et vibrations disposent d’interrupteurs séparés. L’option de réduction des effets agit sur les secousses et les animations secondaires du rendu.
- Les records et objectifs apparaissent dans les paramètres. Seules les manches terminées par un krach ou un encaissement contribuent aux compteurs et aux records.
- Le premier chargement de DataStore copie le solde, les achats et les réglages typés depuis `shared_prefs/market_rocket.xml` puis marque la migration. Le fichier XML reste intact. Les gains anciens sont reconstitués à partir du solde et des achats historiques au barème d’origine. Les records antérieurs inconnus démarrent à zéro. Un DataStore existant a priorité sur le XML. En cas d’erreur de lecture, l’application propose de réessayer. Les écritures sont sérialisées et la sauvegarde en erreur reste disponible en mémoire pour une nouvelle tentative.
- `GameEngine.startRound(seed = ...)` permet de rejouer un parcours. La source d’aléa des effets est distincte de la génération des portes. Un futur défi quotidien pourra fournir une seed et une version de règles. Les résultats de manche restent des données Kotlin utilisables par un adaptateur de classement. Le branchement à Google Play Games Services v2 pourra se faire après enregistrement local.

## Builds vérifiés

Les tâches `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleRelease`, `bundleRelease` et `assembleDebugAndroidTest` ont abouti. Android Lint produit 0 erreur et 10 avertissements, principalement sur les versions de dépendances disponibles. Le manifeste empaqueté déclare une cible SDK 36 et un minimum SDK 24. Le build R8 produit son fichier de correspondance.

Pour les mêmes sources applicatives, l’APK release fait 7 712 699 octets avec les deux options de réduction désactivées dans une copie temporaire. L’APK release optimisé fait 1 170 159 octets et le bundle fait 2 536 360 octets dans ce build. Les artefacts de publication nécessitent la signature de l’éditeur.

L’APK optimisé, signé avec la clé de debug pour ce contrôle, a affiché le menu sur Android 15 à 320 dp de large avec la police à 180 %. Des alertes système de l’émulateur ont limité ce contrôle au démarrage. Le parcours Compose complet a été testé en debug.

## Tests exécutés

- 25 tests JVM réussis. La sonde d’allocations s’exécute sur demande avec le protocole ci-dessus. Les tests couvrent l’encaissement, les paliers, la migration, les combinaisons de cadences et de formats, les changements de taille, les grands deltas et les collisions. Un pilote de référence enchaîne 40 anneaux sur 12 seeds et trois formats. Le générateur vérifie aussi l’espace entre les portes sur 64 seeds.
- Quatre scénarios Compose réussis sur l’émulateur Android 15 au fil des exécutions. Ils couvrent les réglages et les statistiques après recréation, la reprise après interruption, la rotation et l’appui-relâchement jusqu’au bonus, à l’encaissement et à la relance. Le test de collecte a été rejoué séparément après correction de son horloge de test.
- Sur un profil fictif installé avant le premier lancement, la migration a créé le fichier `market_rocket.preferences_pb` et le XML d’origine est resté identique à l’octet près.

Le job GitHub Actions instrumenté cible Android 36. Les mesures de fluidité et de latence sur matériel Android 16 font partie de la suite proposée.

## Suite proposée

| Priorité | Valeur | Effort | Tâche |
| --- | --- | --- | --- |
| 1 | Élevée | Moyen | Capturer temps par image, GC et traces CPU/GPU sur téléphones à 60 et 120 Hz. Isoler la grille, les effets et les dégradés Canvas avant de mettre en cache les postes mesurés. |
| 2 | Élevée | Faible | Vérifier TalkBack, contraste, taille de police 200 %, petits écrans et reprise après une interruption sur un appareil Android 16. Tester la variante signée sur la piste interne Play. |
| 3 | Moyenne | Moyen | Mesurer le démarrage et les premières manches sur appareil. Créer un Baseline Profile si les mesures montrent un coût de démarrage ou de compilation à chaud significatif. |
| 4 | Moyenne | Moyen | Versionner les seeds et les règles, puis mesurer l’intérêt des défis quotidiens hors connexion. Ajouter un adaptateur de classement Google Play Games Services v2 après validation du format de score. |

## Références

- [Architecture Android](https://developer.android.com/topic/architecture) et [compatibilité AGP 8.10](https://developer.android.com/build/releases/past-releases/agp-8-10-0-release-notes)
- [Performances Compose](https://developer.android.com/develop/ui/compose/performance) et [dessin Compose](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)
- [Preferences DataStore](https://developer.android.com/topic/libraries/architecture/datastore) et [tests Compose](https://developer.android.com/develop/ui/compose/testing)
- [Optimisation R8](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization) et [exigences Google Play](https://developer.android.com/google/play/requirements/target-sdk)
- [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview) et [Google Play Games Services](https://developer.android.com/games/pgs/overview)
