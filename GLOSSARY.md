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

**Anbieter**:
Wer ein Inserat anbietet, etwa ein Makler, eine Bank oder ein Bauträger. Nicht zu verwechseln mit der Quelle, auf der das Inserat erscheint.
_Avoid_: Provider, Verkäufer

**Ausgeschlossene Anbieter**:
Anbieter, deren Häuser und Wohnungen dem Nutzer nicht angezeigt werden. Sie werden im Suchprofil gepflegt, gelten aber für alle Haus- und Wohnungsprofile des Nutzers; auf Grundstücke wirken sie nicht.
_Avoid_: Blacklist, Provider-Blacklist

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

## Benachrichtigungen

**Scraping-Runde**:
Ein Durchlauf, in dem der Scraper alle Quellen abfragt und die gefundenen Inserate anschließend anreichert.
_Avoid_: Scrape, Lauf, Job

**Neuzugang**:
Ein unbewertetes Inserat, das seit der letzten Benachrichtigung des Nutzers für seinen Immobilientyp hinzugekommen ist. Enger als der Filter „Neu“, der alle unbewerteten Inserate zeigt.
_Avoid_: neues Inserat, ungesehenes Inserat

**Benachrichtigung**:
Die Push-Meldung an einen Nutzer über die Neuzugänge eines Immobilientyps nach einer Scraping-Runde; höchstens eine pro Immobilientyp und Runde.
_Avoid_: Notification, Alert, Push
