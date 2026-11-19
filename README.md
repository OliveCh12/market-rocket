# Market Rocket

Prototype arcade Android en Kotlin, Jetpack Compose et Material 3. Ouvrez ce dossier dans Android Studio, installez le SDK Android 35 et sélectionnez un JDK 17 ou 21 pour Gradle. Lancez la configuration `app` sur Android 7.0 ou supérieur.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## Jouer

Depuis le menu, choisissez **Jouer**. Maintenez le doigt sur le graphe pour monter et relâchez pour redescendre. Passez par les anneaux dorés, évitez les pics rouges et les deux lignes qui bornent la zone de vol, puis touchez **Encaisser** pour transférer la cagnotte dans la banque. Le bouton en haut à droite met la manche en pause.

Chaque manche fournit un capital initial de 250 € dès le départ. Entre les manches, **Améliorations** permet de dépenser le solde en banque pour augmenter ce capital, accélérer la croissance passive ou activer un bouclier qui absorbe un pic rouge par manche. Les paliers suivent le solde disponible en banque.

Les **Paramètres** permettent de désactiver les vibrations et de réduire les animations du décor et les secousses.

## Architecture

- `GameEngine.kt` et `GameModels.kt` gèrent la simulation Kotlin et ses instantanés. Le moteur découpe le temps écoulé en pas de 1/120 s au maximum.
- `GameScreen.kt` cadence la simulation avec `withFrameNanos` et la suspend quand l’activité quitte le premier plan. `MarketRocketCanvas.kt` lit uniquement les instantanés à l’affichage.
- `StorageManager.kt` sauvegarde le solde, les niveaux d’amélioration et les paramètres dans des préférences locales.
