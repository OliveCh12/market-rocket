# Market Rocket

Jeu arcade Android en Kotlin, Jetpack Compose et Material 3. Ouvrez ce dossier dans Android Studio avec le SDK Android 36 et un JDK 17 ou 21. La configuration `app` fonctionne sur Android 7.0 et versions ultérieures.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease :app:bundleRelease
./gradlew :app:connectedDebugAndroidTest
```

La dernière commande utilise un appareil ou un émulateur Android connecté. Le build release applique R8 et nécessite une signature de publication avant envoi sur Google Play.

## Jouer

Depuis le menu, choisissez **Jouer**. Maintenez le doigt sur le graphe pour monter et relâchez pour redescendre. Traversez un anneau doré pour débloquer **Encaisser**, puis choisissez le moment de mettre les gains en banque. Les anneaux successifs augmentent la valeur de leurs bonus. Le bouton en haut à droite met la manche en pause et une interruption demande une reprise explicite.

Chaque manche fournit un capital initial de 250 €. Entre les manches, **Améliorations** permet de dépenser le solde en banque pour augmenter ce capital, accélérer la croissance passive ou activer un bouclier qui absorbe un pic rouge par manche. Les paliers suivent les gains cumulés et les achats débitent le solde disponible.

Les **Paramètres** donnent accès aux sons, aux vibrations, à la réduction des effets, ainsi qu’aux statistiques et objectifs locaux.

## Architecture

- `GameEngine.kt` fait avancer la simulation à pas fixe de 1/120 s. `CourseGenerator.kt` définit les portes d’un parcours à partir d’une seed.
- `GameScreen.kt` suit l’horloge de l’écran avec `withFrameNanos`. `MarketRocketCanvas.kt` dessine les instantanés de rendu interpolés.
- `StorageManager.kt` persiste le profil et les réglages dans Preferences DataStore. Le premier chargement importe la sauvegarde XML existante et conserve le fichier d’origine.

[Audit, mesures et priorités](docs/audit-android.md)
