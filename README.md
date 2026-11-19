# Market Rocket

Jeu arcade Android en Kotlin, Jetpack Compose et Material 3. Ouvrez ce dossier dans Android Studio avec le SDK Android 36 et un JDK 17 ou 21. La configuration `app` fonctionne sur Android 7.0 et versions ultérieures.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease :app:bundleRelease
./gradlew :app:connectedDebugAndroidTest
```

La dernière commande utilise un appareil ou un émulateur Android connecté. Le build release applique R8 et nécessite une signature de publication avant envoi sur Google Play.

## Installer la version 1.2

L’APK de test signé avec le même certificat que les APK précédents peut être installé directement par-dessus les versions 1.0 et 1.1. Au premier lancement, Market Rocket reprend le solde, les améliorations et les réglages existants.

## Jouer

Depuis le menu, choisissez **Jouer**. Maintenez le doigt sur le graphe pour monter et relâchez pour redescendre. Traversez un anneau doré pour débloquer **Encaisser**. À mesure que les passages se resserrent, choisissez entre sécuriser la cagnotte et prolonger votre série. Les anneaux ambre plus petits valent davantage et demandent de se rapprocher d’un pic. Le bouton en haut à droite met la manche en pause et une interruption demande une reprise explicite.

Sans amélioration, une manche commence avec 150 € et rapporte 12 €/s en vol. Chaque anneau ajoute un montant en euros, majoré par la série jusqu’à 48 %. Entre les manches, **Améliorations** permet d’augmenter le capital ou le revenu par seconde et d’acheter un bouclier qui absorbe un pic rouge par manche. Les paliers suivent les gains cumulés et les achats débitent le solde disponible.

Les **Paramètres** donnent accès aux sons, aux vibrations, à la réduction des effets, ainsi qu’aux statistiques et objectifs locaux.

## Architecture

- `GameEngine.kt` fait avancer la simulation à pas fixe de 1/120 s. `CourseGenerator.kt` définit les portes d’un parcours à partir d’une seed.
- `GameScreen.kt` suit l’horloge de l’écran avec `withFrameNanos`. `MarketRocketCanvas.kt` dessine les instantanés de rendu interpolés.
- `StorageManager.kt` persiste le profil et les réglages dans Preferences DataStore. Le premier chargement importe la sauvegarde XML existante et conserve le fichier d’origine.

[Équilibrage 1.2 et mesures](docs/equilibrage-1.2.md) · [Audit Android de la version 1.1](docs/audit-android.md)
