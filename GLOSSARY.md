# immo-scrabber

Native Android-App zu immo-scrabber: Inserate von Häusern, Wohnungen und Grundstücken, die zu den Suchprofilen des Nutzers passen, werden gesichtet und bewertet.

## Inserate

**Inserat**:
Ein Angebot eines Anbieters (Haus, Wohnung oder Grundstück), das der Scraper gefunden hat und das zu mindestens einem Suchprofil des Nutzers passt.
_Avoid_: Listing, Objekt, Angebot, Ergebnis

**Immobilientyp**:
Die Art eines Inserats: Haus, Wohnung oder Grundstück. Jeder Immobilientyp hat einen eigenen Tab.
_Avoid_: Kategorie, Property Type

**Suchprofil**:
Eine gespeicherte Suche des Nutzers für genau einen Immobilientyp (Ort, Radius, Preisgrenze usw.). Sie bestimmt, welche Inserate der Nutzer sieht.
_Avoid_: Suchparameter, Suche, Preferences

**Quelle**:
Das Portal, auf dem ein Inserat veröffentlicht ist.
_Avoid_: Provider, Portal

## Bewertung

**Bewertung**:
Das Urteil des Nutzers über ein Inserat, ausgedrückt als eines von drei Labels. Jeder Nutzer bewertet für sich.
_Avoid_: Rating, Markierung

**Label**:
Der Wert einer Bewertung: `unbewertet`, `interessant` oder `uninteressant`.
_Avoid_: Status, Tag

**Neu**:
Filter für alle unbewerteten Inserate eines Immobilientyps.
_Avoid_: Ungelesen, Inbox

**Favorit**:
Ein Inserat mit dem Label `interessant`. Der Filter heißt „Favoriten“.
_Avoid_: Merkliste, Like

**Archiv**:
Filter für die Inserate mit dem Label `uninteressant`.
_Avoid_: Papierkorb, Abgelehnt, Nö

**Alle**:
Filter für alle Inserate eines Immobilientyps, gleich welches Label sie tragen.

**Überspringen**:
Ein Inserat im Kartenstapel für jetzt zurückstellen, ohne es zu bewerten. Es bleibt unbewertet und kommt später wieder.
_Avoid_: Später, Snooze

## Ansichten

**Kartenstapel**:
Die Ansicht des Filters „Neu“: ein Inserat nach dem anderen; nach rechts gewischt wird es zum Favoriten, nach links wandert es ins Archiv.
_Avoid_: Stack, Tinder-Ansicht

**Wischliste**:
Die Ansicht der Filter Favoriten, Alle und Archiv: eine Liste, deren Zeilen man zum Umbewerten zur Seite wischt.
_Avoid_: Swipe-Liste
