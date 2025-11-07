# 🌐 LAN File Sender (Java Swing)

Ein leichtgewichtiges Peer‑to‑Peer‑Tool zum **Dateien‑Freigeben im lokalen Netzwerk**.  
Erstellt einen kleinen **HTTP‑Server**, generiert **QR‑Codes** und erlaubt Downloads direkt über Browser oder Smartphone.  

---

## 🚀 Features

- ✅ **Dateien freigeben** via Drag & Drop oder Dateiauswahl
- 🌍 **LAN‑IP automatisch erkennen**
- 🔢 **Konfigurierbarer Port** (Standard: 8080)
- 📱 **QR‑Code‑Anzeige** für einfachen Zugriff per Handy
- 📦 **Mehrere Dateien gleichzeitig**
- 📊 **Fortschrittsanzeige** beim Download
- 🧠 Komplett offline, kein Internet nötig
- 💡 Läuft auf **Windows, Linux, macOS** (Java 17+)

---

## 🧩 Voraussetzungen

- **Java 17 oder neuer**
- **ZXing Core** für QR‑Code‑Erzeugung

📦 Lade `zxing-core-3.5.2.jar` von Maven Central oder GitHub:  
[https://github.com/zxing/zxing/releases](https://github.com/zxing/zxing/releases)

Lege die JAR in denselben Ordner wie `LanFileSenderSwing.java`.

---

## ⚙️ Installation & Start

### 🧱 Kompilieren

```powershell
javac -cp ".;core-3.5.2.jar" LanFileSenderSwing.java
```

### ▶️ Starten

```powershell
java -cp ".;core-3.5.2.jar" LanFileSenderSwing
```

> 💡 Auf Linux/macOS wird `;` durch `:` ersetzt:  
> `java -cp ".;core-3.5.2.jar" LanFileSenderSwing`

---

## 🖥️ Nutzung

1. **Dateien hinzufügen**
   - Entweder über den Button *„Dateien hinzufügen…“* oder einfach per **Drag & Drop**.
2. **LAN‑IP auswählen** (Dropdown) und ggf. **Port anpassen**.
3. **Server starten** → die URL erscheint rechts unten, inklusive QR‑Code.
4. Öffne die angezeigte **URL** oder scanne den **QR‑Code** mit dem Handy im selben WLAN.
5. Lade deine Dateien direkt über den Browser herunter.

### 🔁 Steuerung

| Taste / Button | Funktion |
|----------------|-----------|
| ⬆️ / ⬇️ | Reihenfolge in der Liste ändern |
| 🗑️ Entfernen | Markierte Datei löschen |
| 🧹 Leeren | Liste vollständig löschen |
| ▶️ Starten | Server aktivieren |
| ⏹️ Stop | Server stoppen |
| 🌐 Im Browser öffnen | Öffnet Index‑Seite |
| 📋 URL kopieren | Kopiert die LAN‑Adresse in die Zwischenablage |

---

## 📱 Beispiel

- **URL:** `http://192.168.178.25:8080/`
- **QR‑Code:** wird rechts angezeigt  
- **Index‑Seite:** listet alle freigegebenen Dateien inkl. Größe und Download‑Link  

---

## 🧭 Tipps

- Firewall‑Dialog beim ersten Start: **Zulassen**, sonst kein Zugriff im LAN.  
- Wenn keine IP angezeigt wird → WLAN oder LAN‑Adapter prüfen.  
- Port kann beliebig geändert werden (z. B. 8081).

---

## 🧾 Beispiel‑Dateistruktur

```
C:\Users\user\Desktop\java-programms\LanFileSenderSwing\
 ├── LanFileSenderSwing.java
 ├── zxing-core-3.5.2.jar
 └── (Deine freigegebenen Dateien)
```

---

## 🔒 Sicherheitshinweise

- Freigegebene Dateien sind für alle Geräte im LAN zugänglich, solange der Server läuft.  
- Keine Authentifizierung — nur für **Heimnetzwerke** empfohlen.  
- Nach Nutzung: **Server stoppen** (Button „Stop“).

---

## 📄 Lizenz

MIT License — frei nutzbar, modifizierbar, kommerziell erlaubt.  

---

© 2025 Robert Martin
