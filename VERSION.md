# Versions

## 0.12.0

- Nom visible simplifié en « Endoscope » sur le bureau Android et dans l’application.
- Identifiant interne `com.alyx.endoscope` conservé pour assurer la mise à jour de l’installation existante.
- Nouvelle charte : bleu `#194196`, turquoise `#0096AA`, blanc et fond clair `#FAFAFA`.
- Icône de l’application recolorée en turquoise à la place de l’orange.
- Nouvelles captures nommées `Endoscope_…`, avec compatibilité maintenue pour les anciens clichés `Alyx_…`.
- Indication de fréquence centrée au-dessus de l’aperçu.
- Libellés Workshop uniformisés à la même taille, avec un compromis qui maintient « Commentaire » dans son bouton.
- Positionnement vertical des libellés Workshop à mi-distance entre le bas de l’icône et le bas du bouton.
- Remplacement des faibles résolutions par quatre formats d’image : 16:9, 3:2, 4:3 et 1:1 carré.
- Conservation du flux haute définition 1280 × 720 et recadrage centré, sans étirement, dans l’aperçu, les photos et les vidéos.
- Agrandissement de la fenêtre de caméra à toute la largeur disponible pour chaque format.
- Conservation du format d’image choisi après fermeture, redémarrage ou mise à jour.

## 0.11.0

- Ajout d’une réglette verticale de correction anti-fisheye dans Atelier et Workshop.
- Réglette très fine placée directement en superposition sur l’image de l’endoscope.
- Réglage en temps réel du coefficient de distorsion radiale de 0,00 à −0,40.
- Application de la correction à l’aperçu, aux photos et aux vidéos.
- Correction de l’écran noir provoqué par l’initialisation trop précoce du traitement vidéo.
- Conservation systématique du coefficient, du mode Photo/Vidéo, de la résolution et de la caméra choisie.
- Suppression de l’ancienne réglette horizontale de luminosité.
- Luminosité de l’écran à 100 % pendant l’utilisation, puis retour automatique au réglage normal.

## 0.10.1

- Remplacement du déclencheur à l’écran par l’accès aux clichés Endoscope dans les deux thèmes.
- Affichage jaune du commentaire au-dessus de la date et de l’heure.
- Réorganisation des boutons Workshop : Dossier, Caméra, Résolution, Commentaire.
- Ajustement des libellés Workshop pour qu’ils restent contenus dans leurs boutons.

## 0.10.0

- Le bouton physique de l’endoscope suit désormais le mode Photo ou Vidéo sélectionné.
- Incrustation jaune de la date, de l’heure et du commentaire dans les nouvelles captures.
- Ajout d’un commentaire actif depuis la barre d’outils, adapté aux interfaces Atelier et Workshop.
- Ajout d’une galerie Endoscope dédiée avec aperçu, lecture vidéo et modification des commentaires.
- Ajout d’un bouton de galerie à droite du déclencheur et suppression de l’accès général à gauche.
- Correction de l’enregistrement vidéo sur Android 10 et versions ultérieures.

## 0.9.2

- Verrouillage de l’application en orientation portrait.
- Correction de l’enregistrement des photos sur Android 10 et versions ultérieures.

## 0.9.1

- Suppression du thème **Visée**.
- Conservation du thème choisi après fermeture, redémarrage ou mise à jour de l’application.
- Prise en charge du bouton photo physique des endoscopes UVC compatibles.
- Correction du plantage lors de la fermeture ou de la reconnexion de la caméra USB.

## 0.9

- Ajout des thèmes **Atelier**, **Visée** et **Workshop**.
- Adaptation automatique au thème clair ou sombre du système.
- Suppression du mode audio.
- Sélection de la caméra centralisée dans la barre supérieure.
- Suppression des écrans Multi-caméra et Contact.
- Suppression de l'ancien écran de chargement.
- Refonte générale de l'interface pour l'utilisation avec un endoscope USB.

Le projet est dérivé d'[AndroidUSBCamera](https://github.com/jiangdongguo/AndroidUSBCamera) de [jiangdongguo](https://github.com/jiangdongguo).
