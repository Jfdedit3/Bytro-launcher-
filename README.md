# Ma Galerie — Galerie photo et vidéo Android

Application Android Kotlin native dédiée aux **photos et vidéos présentes sur le téléphone**. Elle remplace complètement l'ancien projet X Local API / FxTwitter.

## Fonctionnalités

- Galerie locale par miniatures en **grille de 3 colonnes**.
- Lecture de toutes les photos et vidéos accessibles via **MediaStore** (sans envoyer de médias à un serveur).
- Filtres **Tout**, **Photos**, **Vidéos**.
- Tri par date d'ajout, des plus récents aux plus anciens.
- Chargement des miniatures en arrière-plan, avec cache mémoire.
- Aperçu des photos en plein écran et lecture vidéo intégrée.
- Actualisation manuelle de la galerie.
- Gestion des autorisations Android 7–16, dont l'accès limité aux photos choisies (Android 14+).
- Interface en plein écran immersif.

## Sécurité et confidentialité

L'application ne nécessite **aucune connexion Internet**. Les médias restent sur ton appareil. Android peut proposer de n'autoriser que certaines photos ou vidéos ; dans ce cas l'application n'affichera que la sélection autorisée. Elle ne supprime ni ne modifie de médias.

## Compiler

Ouvre [GitHub Actions](.github/workflows/android.yml) puis télécharge l'artifact `ma-galerie-debug`. Le workflow crée `ma-galerie-signed-update` si les secrets GitHub `X_LOCAL_KEYSTORE_BASE64` et `X_LOCAL_KEYSTORE_PASSWORD` sont définis avec une clé permanente.

## Mises à jour Android

L'identifiant de package reste `com.jfdedit3.bytrolauncher` pour préserver la possibilité d'installer la nouvelle app au-dessus de l'ancienne **à condition que la signature soit identique**. Les builds debug sur des runners GitHub différents sont généralement signés avec des clés différentes : Android refusera alors la mise à jour. Si l'APK installé est signé différemment, il faut malheureusement désinstaller l'ancienne version une fois. Pour les mises à jour suivantes, conserve toujours la même clé de signature release.

```bash
gradle assembleDebug
```
