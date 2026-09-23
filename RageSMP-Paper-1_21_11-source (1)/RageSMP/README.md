# RageSMP — Paper 1.21.11

Plugin de progression PvP basé sur 5 niveaux de Rage.

## Niveaux actuels
- Rage 1 : Blood Combo — après 3 coups d'épée consécutifs, les coups suivants sont traités comme des critiques jusqu'à rater.
- Rage 2 : Speed I.
- Rage 3 : +2 cœurs.
- Rage 4 : Armor Break après 5 coups consécutifs.
- Rage 5 : les coups d'épée désactivent les boucliers.

## Rage
- Kill : +1 Rage
- Mort : -1 Rage
- Maximum : 5
- Si le tueur est déjà à Rage max, il ne gagne rien : un **éclat de Rage** tombe
  au sol à la place. N'importe quel joueur peut le ramasser (il va dans l'inventaire)
  puis faire **clic droit dessus** pour convertir l'éclat en Rage.
- `/rage withdraw <montant>` permet de reconvertir ta propre Rage en éclats
  physiques (utile pour la stocker, la donner ou la vendre à quelqu'un).
- Chaque gain ou perte de Rage s'affiche en **gras violet dans le chat**
  (en plus de l'action bar).

Tout est configurable dans `config.yml`.

## Compiler
Utilise Java 21 et Gradle :
`./gradlew build`

Le JAR sera dans `build/libs/`.

## Installer
Place le JAR dans le dossier `plugins/` de ton serveur Paper 1.21.11 puis redémarre le serveur.

Commandes :
- `/rage`
- `/rage <joueur>`
- `/rage withdraw <montant>`
- `/setrage <joueur> <0-5>` (admin)
