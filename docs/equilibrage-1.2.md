# Équilibrage 1.2

La croissance composée et les anciens anneaux multiplicateurs amplifiaient mutuellement la cagnotte. La 1.2 donne 150 € au départ et 12 €/s en vol. Un niveau de capital ajoute 55 € au départ. Un niveau de levier ajoute 4 €/s. Les anneaux versent une somme fixe qui augmente par section. Chaque anneau consécutif augmente le bonus de l’anneau suivant de 8 %, jusqu’à 48 %. Un anneau manqué ou un choc met fin à la série.

Le parcours alterne des passages larges, des changements de côté et des passages à deux pics qui se resserrent avec la distance. L’anneau ambre est plus petit et décalé vers un pic. Le centre de sa porte permet de poursuivre le vol en laissant cette prime. La trajectoire d’introduction apprend l’appui puis le relâchement avant ces choix. Le bilan montre les anneaux collectés et la meilleure série de la manche. Les statistiques donnent deux objectifs de vol, huit anneaux d’affilée et une minute de vol.

Les anciens profils sont repris au chargement. Les niveaux achetés utilisent le nouveau barème de capital et de levier. La reconstitution des gains historiques à partir d’un ancien fichier SharedPreferences utilise les prix de l’époque.

## Scénario mesuré

Le pilote JVM de `CourseTest` vise chaque anneau à 120 Hz sur un terrain de 320 × 620 et une seed de 0. Les valeurs ci-dessous sont les cagnottes à la collecte, avec un profil sans amélioration. Les deux versions génèrent des parcours différents. Les temps proviennent de la simulation.

| Anneaux | Cagnotte 1.1 | Temps 1.1 | Cagnotte 1.2 | Temps 1.2 |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 618 € | 1,3 s | 221 € | 1,3 s |
| 4 | 11 382 € | 6,6 s | 573 € | 7,1 s |
| 8 | 249 900 € | 12,1 s | 1 143 € | 13,0 s |
| 24 | 20 671 720 015 € | 30,8 s | 4 247 € | 33,7 s |
| 40 | 803 360 913 521 101 € | 46,9 s | 8 654 € | 56,4 s |

Avec le capital et le levier au niveau maximal, ce pilote arrive à 10 898 € au 40ᵉ anneau dans la 1.2. Il atteignait le plafond technique de 9 000 000 000 000 000 € dès le 16ᵉ anneau dans la 1.1.

Les tests vérifient la somme des primes et du revenu au terme du vol, les plafonds de référence à tous les niveaux, le passage sans prise de risque à côté d’un anneau ambre et le krach d’un vol qui reste au milieu. Un second scénario franchit 40 anneaux de suite sur 12 seeds et trois formats d’écran.
