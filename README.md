# Manhunt (Paper 26.1.2, Java 25)

## Compilation automatique (GitHub)
1. Mets TOUT le contenu de ce dossier a la racine de ton repo (pom.xml, src/, .github/).
2. Push -> onglet Actions -> "Build Manhunt".
3. Le jar est dans Releases ("Manhunt (dernier build)") ou dans Actions > run > Artifacts.
4. Mets Manhunt.jar dans plugins/ du serveur Paper 26.1.2.

Compilation locale : `mvn package` -> `target/Manhunt.jar`.

## Commandes (`/manhunt` ou `/mh`, TAB partout)
- `coureur <joueur>` / `chasseur ajouter|retirer|liste|vider|tous [joueur]`
- `demarrer` / `arreter` / `boussole [joueur]` / `statut` / `options` / `recharger`
- `regler <option> <valeur>` : delai-chasseurs, temps-coureur, kit-coureur, quantite-planches,
  nourriture (aucune|coureur|chasseurs|tous), quantite-nourriture, intervalle-boussole, tp-depart

Permissions : `manhunt.admin` (op par defaut), `manhunt.use` (aide/options/statut).
