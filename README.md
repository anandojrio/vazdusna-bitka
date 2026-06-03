***

## README

### 1. PREGLED SISTEMA

Projekat implementira simulaciju vazdušne borbe između **BLUE** i **RED** strane sa sledećim komponentama:[^1]

- Komandni centri: `BlueCommandCenterApplication`, `RedCommandCenterApplication`
- Skuadron aplikacije: `BlueSquadroonApplication`, `RedSquadroonApplication`
- Avioni: `AircraftWorker`
- Rakete: `MissileWorker`
- Centralni radar servis: `RadarServiceImpl` + `AirObjectRegistry`
- Zajednički DTO/eum/model sloj: `common.dto`, `common.enums`, `common.model`[^3]

Komunikacija ide preko TCP soketa (CC ↔ squadron) i JSON poruka (Jackson), a radarski servis je izložen preko RMI.[^4][^3]

***

### 2. INICIJALIZACIJA I TOPOLOGIJA

- Tabla je 8x8, koordinate u opsegu $[0.0, 7.9]$, ćelije A1–H8 dobijaju se `floor(x), floor(y)`.[^3]
- Baza BLUE strane je uvek A1 $(0.0, 0.0)$.
- Baza RED strane je uvek H8 $(7.0, 7.0)$.[^3]

Svaka squadron aplikacija pokreće po 5 niti tipa `AircraftWorker`, sa odgovarajućim `id`, `AircraftType` i `Side`, i svi avioni startuju **u bazi svog tima**.[^3]

***

### 3. STANJE AVIONA I KRETANJE

`AircraftWorker` drži:[^3]

- `state`: `IN_BASE`, `PATROLLING`, `RETURNING`, `DESTROYED`
- `aircraftType`: definiše `patrolStep`, `returnStep`, `maxPauseMs`, `radarRange`
- `basePosition`: A1 ili H8, zavisno od strane

Logika:

- `IN_BASE`: avion stoji u bazi, šalje samo report.
- `PATROLLING`: avion se kreće unutar zadate patrol zone koristeći `patrolStep`, odbija se od granica zone, zaokružuje koordinate na jednu decimalu i clamp-uje na tablu.[^3]
- `RETURNING`: avion se kreće ka `basePosition` korišćenjem `returnStep` dok ne stigne dovoljno blizu, zatim prelazi u `IN_BASE`.
- `DESTROYED`: nit se gasi.[^3]

Patrol zona:

- Postavlja se komandama `P? PATROL A2 B3` ili numerički.
- Koordinate se normalizuju (min/max), clamp-uju na tablu i osigurava se da zona ima površinu (ako su granice jednake, širi se za jedan `patrolStep`).[^3]

***

### 4. RADAR AVIONA

Svaki avion na svakom tick-u poziva:


RadarUpdateRequest(
    aircraftType,
    id,
    FlyingObjectType.AIRCRAFT,
    position,
    radarRange,
    side
)
→ RadarService.updateAndScan()


`RadarServiceImpl`:

- Upisuje/azurira sopstveno stanje u `AirObjectRegistry` (svi objekti u vazduhu).[^3]
- Iterira kroz sve `AirObjectState` objekte i filtrira:
    - ignoriše samog sebe,
    - ignoriše neaktivne objekte,
    - za aircraft poziv ne filtrira po tipu, vidi sve u dometu.[^3]

Vidljivi kontakti vraćaju se kao `RadarContact` (id, type, side, position).[^3]

Komandni centar:

- Drži mapu `enemyPositions` i `lastKnownEnemyCellByAircraft`.
- Pri svakom `AircraftReportMessage` ažurira poslednje poznate pozicije neprijateljskih aviona i koristi ih za `ATTACK` komandu.[^3]

***

### 5. KOMANDNI CENTRI I KOMANDE

Komande sa konzole:[^3]

- `SHOW`: iscrtava matricu 8x8 sa svim prijateljskim i neprijateljskim letelicama.
- `P? RETURN` / `C? RETURN`: šalje `CommandMessage.RETURN_TO_BASE` ka konkretnom avionu.
- `P? PATROL ...` / `C? PATROL ...`: šalje `CommandMessage.PATROL` sa koordinatama zone.
- `P? ATTACK C?` / `C? ATTACK P?`: šalje `CommandMessage.ATTACK` sa `targetId` i poslednjom poznatom `targetX`, `targetY` iz `enemyPositions`.[^3]

Kill link:

- Svaki CC sluša na svom `KILL_LISTEN_PORT` i prima `KillNotification` sa ID žrtve.
- Po prijemu:
    - briše taj ID iz svojih mapa,
    - šalje `DESTROY` komandu u svoj squadron,
    - prosleđuje kill na protivnički CC preko stalne veze.[^3]

***

### 6. RAKETE I NAVODJENJE

`MissileWorker` implementira logiku rakete:[^5][^3]

- Inicijalno dobija:
    - `initialPosition` (A1/H8 ili baza),
    - `initialTargetPosition` (poslednja poznata pozicija cilja),
    - `RadarService`,
    - `missileRadarRange` (mali domet, npr. 0.6).[^3]

Petlja:

1. `moveTowardsTarget()` – raketa se pomera u pravcu poslednje poznate pozicije cilja, korakom `MissileConfig.STEP`, uz zaokruživanje na jednu decimalu.[^3]
2. `canConfirmHit()`:
    - šalje `RadarUpdateRequest(null, id, FlyingObjectType.MISSILE, position, missileRadarRange, side)`
    - radar upisuje raketu u registry i vraća sve objekte u dometu,
    - logika filtra u radaru: ako je `request.objectType == MISSILE`, u scan ulaze **samo** `AIRCRAFT` objekti.[^3]
    - `canConfirmHit` vraća true samo ako u vidljivim objektima postoji `contact` sa:
        - `contact.objectType == AIRCRAFT` i
        - `contact.id == targetId`.[^3]
3. Ako `canConfirmHit()` vrati true:
    - raketa šalje `MissileStatus.HIT`, loguje HIT i završava rad.
4. Ako nije videla cilj, proverava `reachedLastKnownPosition()`:
    - ako je raketa dovoljno blizu `lastKnownTarget`, a cilj nije viđen u dometu malog radara, šalje `MissileStatus.SELF_DESTRUCTED` i gasi se.[^3]
5. Ako nije ni hit ni self-destruct, šalje `MissileStatus.TRACKING` i nastavlja.[^3]

Kada nit završi, pokušava `radarService.unregister(id)` i poziva `onFinished` callback (oslobađanje base launchera ako je korišćen).[^3]

Ovo tačno implementira pravilo:

> Raketa ide na poslednju poznatu lokaciju cilja. Ako ga u malom dometu oko sebe ne vidi kad stigne u taj region, samouništava se. Ako ga vidi, potvrđuje hit i sledi logika uništenja cilja.[^2][^1]

***

### 7. KILL LOGIKA I UNISTENJE AVIONA

- Kad CC primi `MissileReportMessage` sa `status == HIT`:
    - loguje HIT i uklanja neprijateljski avion iz `enemyPositions`,
    - šalje `KillNotification` preko kill linka protivničkom CC, koji onda šalje `DESTROY` u svoj squadron.[^3]
- `AircraftWorker.destroyFromCommandCenter()`:
    - postavlja state u `DESTROYED`,
    - loguje razlog,
    - glavna petlja izlazi jer `running = false`.[^3]

Avion se time efektivno uklanja iz simulacije (ne kreće se više, prestaje da šalje reportove).[^3]

***

### 8. LOGOVANJE

Logovi su svedeni na ono što je smisleno za praćenje:[^3]

- Squadron:
    - spawn aviona u bazi sa inicijalnim `state=IN_BASE`,
    - promene stanja (IN_BASE → PATROLLING, PATROLLING → RETURNING, ...),
    - promene ćelije (`moved X -> Y`),
    - kreiranje i životni ciklus raketa (launched, HIT, SELF_DESTRUCTED, finished).[^3]
- Komandni centar:
    - registracija aviona,
    - detekcije i pomeranja neprijateljskih aviona,
    - slanje komandi PATROL / RETURN / ATTACK,
    - Missile report sa HIT/SELF_DESTRUCTED,
    - kill notifikacije i prosleđivanje DESTROY komandi.[^3]

***

### 9. FUNKCIONALNA POKRICE ZADATKA

Na osnovu tvog opisa i koda:[^2][^1]

- Inicijalne pozicije, baze i strane: odrađeno.
- Kretanje aviona (patrola, povratak, baza): odrađeno, sa tip-specifičnim parametrima.
- Radar aviona sa dometom po tipu i poslednjom poznatom pozicijom ciljeva: odrađeno.
- Komandni centri, dvosmerna komunikacija sa squadronima: odrađeno.
- Prikaz table (SHOW) sa prijateljskim i neprijateljskim avionima: odrađeno.
- ATTACK komanda ka poslednjoj poznatoj poziciji cilja: odrađeno.
- Rakete koje:
    - idu ka lastKnownTarget,
    - koriste mali radar da potvrde da je cilj još tu,
    - ako ga nema, self-destruct,
    - ako ga ima, HIT i ubijaju avion, uz sinhronizovan kill-link: odrađeno.[^3]
- Logika uništenja aviona i sinkronizacija oba komandna centra: odrađeno.
