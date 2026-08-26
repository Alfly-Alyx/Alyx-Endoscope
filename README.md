# Alyx Endoscope

Alyx Endoscope est une application Android destinée à l'utilisation d'endoscopes et de caméras USB compatibles UVC. La version **0.11.0** privilégie une interface simple, lisible et adaptée automatiquement au thème clair ou sombre du système.

## Fonctionnalités

- aperçu en direct d'une caméra USB ;
- prise de photos et enregistrement vidéo, sans mode audio ;
- choix de la caméra depuis l'icône dédiée de la barre supérieure ;
- sélection de la résolution et du dossier de destination ;
- correction anti-fisheye manuelle et en temps réel par coefficient de distorsion radiale ;
- deux interfaces sélectionnables : **Atelier** et **Workshop** ;
- prise de photo ou démarrage/arrêt d’une vidéo depuis le bouton physique des endoscopes UVC compatibles ;
- incrustation jaune de la date, de l’heure et d’un commentaire dans les captures ;
- galerie dédiée aux clichés Endoscope avec commentaires modifiables ;
- conservation des réglages utilisateur après fermeture ou mise à jour ;
- luminosité de l’écran automatiquement maximale pendant l’utilisation de l’application ;
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
