# Alyx Endoscope

Alyx Endoscope est une application Android destinée à l'utilisation d'endoscopes et de caméras USB compatibles UVC. La version **0.9** privilégie une interface simple, lisible et adaptée automatiquement au thème clair ou sombre du système.

## Fonctionnalités

- aperçu en direct d'une caméra USB ;
- prise de photos et enregistrement vidéo, sans mode audio ;
- choix de la caméra depuis l'icône dédiée de la barre supérieure ;
- sélection de la résolution et du dossier de destination ;
- trois interfaces sélectionnables : **Atelier**, **Visée** et **Workshop** ;
- adaptation automatique au thème clair ou sombre du système ;
- prise en charge des appareils `armeabi-v7a` et `arm64-v8a`.

## Prérequis

- Android 9 ou version ultérieure ;
- téléphone ou tablette compatible USB OTG ;
- caméra ou endoscope USB compatible UVC.

## Compilation

Le projet nécessite Android Studio avec un JDK 17 et le SDK Android 36.

```powershell
.\gradlew.bat :app:assembleDebug
```

Pour produire l'APK de distribution locale :

```powershell
.\gradlew.bat :app:assembleRelease
```

## Origine et remerciements

Alyx Endoscope est inspirée de l'application libre [AndroidUSBCamera](https://github.com/jiangdongguo/AndroidUSBCamera), créée par [jiangdongguo](https://github.com/jiangdongguo). Ce projet conserve et adapte une partie de sa base technique de gestion des caméras USB/UVC.

Merci à son auteur et aux contributeurs du projet d'origine pour leur travail.

## Licence

Ce projet est distribué sous licence Apache 2.0. Consultez le fichier [LICENSE](LICENSE) pour les conditions complètes.
