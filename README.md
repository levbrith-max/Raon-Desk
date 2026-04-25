# GNOME Bazzite Launcher — Code source Android

Launcher Android inspiré de GNOME/Bazzite OS (paysage) et Ubuntu Touch (portrait).

---

## STRUCTURE DU PROJET

```
GnomeBazziteLauncher/
├── app/src/main/
│   ├── AndroidManifest.xml          ← Intent HOME = launcher principal
│   ├── assets/builtin_apps/         ← Apps intégrées (zips HTML/JS)
│   │   ├── catalog.json             ← Catalogue des apps
│   │   ├── terminal_emulator.zip
│   │   ├── system_monitor.zip
│   │   ├── text_editor.zip
│   │   ├── calculator.zip
│   │   ├── file_manager.zip
│   │   └── snake_game.zip
│   ├── kotlin/com/gnomebazzite/launcher/
│   │   ├── MainActivity.kt          ← Entry point, gestion orientation
│   │   ├── BaseFragment.kt
│   │   ├── WebAppActivity.kt        ← Runner WebView pour apps intégrées
│   │   ├── BootReceiver.kt
│   │   ├── data/Models.kt           ← AppInfo, BuiltinApp
│   │   ├── manager/
│   │   │   ├── LauncherViewModel.kt ← ViewModel partagé
│   │   │   └── BuiltinAppManager.kt ← Décompression des apps intégrées
│   │   ├── ui/landscape/
│   │   │   ├── GnomeDesktopFragment.kt   ← Bureau GNOME (paysage)
│   │   │   ├── DraggableWindowView.kt    ← Fenêtres déplaçables/redimension.
│   │   │   ├── DesktopWindowManager.kt   ← Gestion couche fenêtres
│   │   │   └── OverviewController.kt     ← Effet dézoom style Android Recents
│   │   ├── ui/portrait/
│   │   │   └── UbuntuTouchFragment.kt    ← Interface Ubuntu Touch (portrait)
│   │   ├── ui/common/
│   │   │   └── AppGridAdapter.kt         ← Adapters RecyclerView
│   │   └── ui/store/
│   │       ├── AppStoreActivity.kt       ← Bazaar App Store
│   │       └── SettingsActivity.kt
│   └── res/                              ← Layouts, drawables, couleurs
```

---

## COMMENT COMPILER

### Prérequis
- Android Studio Hedgehog ou plus récent
- JDK 17
- Android SDK API 28+

### Étapes

1. **Ouvrir le projet dans Android Studio**
   ```
   File → Open → sélectionner le dossier GnomeBazziteLauncher/
   ```

2. **Synchroniser Gradle**
   ```
   File → Sync Project with Gradle Files
   ```

3. **Compiler et installer sur l'appareil**
   ```
   Run → Run 'app'   (ou Shift+F10)
   ```

4. **Définir comme launcher principal**
   - Android affichera une popup "Choisir une application par défaut pour Accueil"
   - Sélectionner "GNOME Bazzite Launcher" → "Toujours"

### Build APK de release
```bash
cd GnomeBazziteLauncher
./gradlew assembleRelease
# APK → app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## FONCTIONNALITÉS IMPLÉMENTÉES

### Mode PAYSAGE — GNOME Bazzite
| Fonctionnalité | État |
|---|---|
| Barre supérieure GNOME (Activities, horloge, systray) | ✅ |
| Fenêtres flottantes déplaçables par drag | ✅ |
| Fenêtres redimensionnables (coin bas-droit) | ✅ |
| Boutons close/min/max avec animations | ✅ |
| Maximisation/restauration animée | ✅ |
| Drawer apps (Activities) avec stagger animation | ✅ |
| Fond semi-transparent du drawer (taskbar visible) | ✅ |
| Effet Overview dézoom (bouton ⠿) | ✅ |
| Preview app dans l'Overview → zoom-in ouverture | ✅ |
| Dash (dock) en bas avec icônes épinglées | ✅ |
| Menu Bazzite (point violet) | ✅ |
| Popup systray (volume, réseau, batterie) | ✅ |
| Horloge temps réel | ✅ |
| Recherche dans le drawer | ✅ |

### Mode PORTRAIT — Ubuntu Touch + GNOME
| Fonctionnalité | État |
|---|---|
| Lanceur latéral gauche (Ubuntu Touch) | ✅ |
| Icône Dossier 📂 → App Drawer style GNOME | ✅ |
| Animation stagger d'ouverture du drawer | ✅ |
| Barre supérieure GNOME (heure, indicateurs) | ✅ |
| Quick settings toggles | ✅ |
| Grille d'apps rapide (8 apps) | ✅ |
| Indicateur de pages (scope dots) | ✅ |
| Cartes de notifications | ✅ |
| Apps épinglées dynamiques dans le launcher | ✅ |
| Recherche dans le drawer | ✅ |

### App Store "Bazaar"
| Fonctionnalité | État |
|---|---|
| Catalogue d'apps intégrées dans l'APK | ✅ |
| Installation = décompression zip (progress bar) | ✅ |
| Désinstallation | ✅ |
| Lancement via WebView | ✅ |

### Apps intégrées
| App | Description |
|---|---|
| 🖥️ Terminal | Émulateur bash avec commandes simulées |
| 📊 Moniteur Système | CPU, RAM, processus en temps réel |
| 📝 Éditeur de texte | GNOME Text Editor minimaliste |
| 🧮 Calculatrice | Scientifique avec historique |
| 📁 Gestionnaire de fichiers | Navigation style GNOME Files |
| 🐍 Snake | Jeu classique thème Bazzite |

---

## AJOUTER UNE APP INTÉGRÉE

1. Créer un dossier `mon_app/` avec `index.html` (+ CSS/JS)
2. Compresser en zip : `zip -r mon_app.zip mon_app/`
3. Copier dans `assets/builtin_apps/mon_app.zip`
4. Ajouter l'entrée dans `catalog.json` :
```json
{
  "id": "mon_app",
  "name": "Mon App",
  "description": "Description de l'app.",
  "version": "1.0.0",
  "author": "Moi",
  "sizeKb": 20,
  "category": "Utilitaires",
  "assetPath": "builtin_apps/mon_app.zip",
  "iconEmoji": "🚀",
  "iconColor": "#1A2A50",
  "entryFile": "index.html",
  "isInstalled": false
}
```

---

## LICENCE
Open Source — libre d'utilisation et modification.
