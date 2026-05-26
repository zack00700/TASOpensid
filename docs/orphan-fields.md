# Champs persistés mais non lus dans le backend

> **Analyse statique** des modèles `@MongoEntity` de `FactBack-main/` à la date du
> 2026-05-22. Un champ est "orphelin" quand il est stocké en base mais aucun code
> métier ne le lit (au-delà d'un mapping trivial vers un DTO de sortie).
>
> Méthode : `grep` du nom de champ sur tout le tree Java, exclusion des
> tests et des mappers triviaux (EventMapper, ItemResponseDTO, etc.).
> Les champs hérités d'`EntityBase` (`id`, `version`, `createdAt`, `createdBy`,
> `updatedAt`, `updatedBy`, `deleted`, `deletedAt`) ne sont pas inclus.

## Synthèse

| Modèle | Fichier | Champs analysés | Orphelins | % |
|---|---|---:|---:|---:|
| **ThirdParty** | `parties/model/ThirdParty.java` | 30 | **16** | 53 % |
| **Invoice** | `billing/model/Invoice.java` | 25 | **11** | 44 % |
| **BillOfLading** | `bol/model/BillOfLading.java` | 28 | **11** | 39 % |
| **Payment** | `billing/model/Payment.java` | 13 | 2 | 15 % |
| **Visit** | `berth/model/Visit.java` | 16 | 2 | 13 % |
| CustomsDeclaration | `customs/model/CustomsDeclaration.java` | 12 | 0 | 0 % |
| Item | `yard/model/Item.java` | 45 | 0 | 0 % |
| Operator | `equipment/model/Operator.java` | 7 | 0 | 0 % |
| DdRule | `dd/model/DdRule.java` | 10 | 0 | 0 % |
| FeatureRequest | `ai/featurerequest/model/FeatureRequest.java` | 18 | 0 | 0 % |
| **TOTAL** | | **204** | **42** | **20,6 %** |

71 % des champs orphelins se concentrent sur **trois modèles** (ThirdParty,
Invoice, BillOfLading). Les bounded contexts logistiques opérationnels (Yard,
Berth, Customs, D&D, Equipment) sont quasi propres.

---

## Détail par bounded context

### `parties/` — ThirdParty (16 orphelins)

`fr.alb.parties.model.ThirdParty`

#### Volet "Access / RBAC client" (déjà mentionné côté UI sous "Access Information")
| Champ | Type | Intention déduite |
|---|---|---|
| `accessType` | `String` | Niveau d'accès logique (View Only / Data Entry / Full Access) — pas de RBAC qui filtre dessus |
| `modulesRequired` | `List<RequiredModules>` | Modules à provisionner (Vessel Scheduling, Yard Management, Billing…) — aucun feature gating qui le consomme |
| `accessStartDate` | `Date` | Fenêtre temporelle d'accès — pas de check à la connexion |
| `accessEndDate` | `Date` | Idem |

#### Volet "Crédit / Compliance commerciale"
| Champ | Type | Intention déduite |
|---|---|---|
| `creditLimit` | `BigDecimal` | Plafond de crédit — pas de validation au moment de la facturation |
| `creditRating` | `String` (A/B/C/D/BLOCKED) | Rating interne — jamais consulté |
| `paymentTermsDefault` | `String` (ex: NET30) | Délais par défaut — facturation utilise les termes du contrat, pas ceux du tiers |
| `hazmatApproved` | `boolean` | Habilitation IMDG — pas de filtre côté BOL/Item |
| `reeferApproved` | `boolean` | Habilitation reefer — idem |

#### Volet "EDI / livraison facture"
| Champ | Type | Intention déduite |
|---|---|---|
| `invoiceDeliveryMethod` | `String` (EMAIL/EDI/PORTAL/PAPER) | Mode de livraison — aucun service ne route la facture en fonction |
| `invoiceElectronicAddress` | `String` | Adresse d'envoi (email / endpoint EDI) — jamais lu |
| `ediPartnerCode` | `String` | Code partenaire EDI — référencé en query DAO mais jamais utilisé pour résoudre un routing |

#### Volet "Coordonnées bancaires"
| Champ | Type | Intention déduite |
|---|---|---|
| `iban` | `String` | Pas d'intégration de paiement |
| `swiftCode` | `String` | Idem |
| `bankAccountNumber` | `String` | Alternative IBAN, idem |
| `bankName` | `String` | Descriptif, idem |

#### Volet "Hiérarchie corporate"
| Champ | Type | Intention déduite |
|---|---|---|
| `parentCompanyId` | `String` | Référence parent pour groupe / filiales — pas de logique de hiérarchie |

> **Champs bien utilisés** (à ne pas confondre) : `customerCode`, `customerType`,
> `taxIdentificationNumber`, `defaultCurrency` sont consommés ailleurs.

---

### `billing/` — Invoice (11 orphelins)

`fr.alb.billing.model.Invoice`

#### Volet "Période de facturation"
| Champ | Type | Intention déduite |
|---|---|---|
| `invoicePeriodStart` | `LocalDate` | Borne basse de la période couverte — aucun calcul ne s'appuie dessus |
| `invoicePeriodEnd` | `LocalDate` | Borne haute — idem |

#### Volet "Cycle de vie / approbation"
| Champ | Type | Intention déduite |
|---|---|---|
| `approvalDate` | `Instant` | Pendant de `approvedBy` (qui est utilisé) — jamais lu |
| `sentDate` | `Instant` | Suivi d'envoi — jamais mis à jour ni consulté |
| `deliveryMethod` | `String` | Pas de routing EDI/email |

#### Volet "Annulations"
| Champ | Type | Intention déduite |
|---|---|---|
| `cancelledBy` | `String` | Audit du responsable d'annulation — jamais écrit ni lu |
| `cancelledDate` | `Instant` | Idem |
| `cancellationReason` | `String` | Idem |

#### Volet "Notes"
| Champ | Type | Intention déduite |
|---|---|---|
| `internalNotes` | `String` | Pas d'affichage / d'enregistrement |
| `customerNotes` | `String` | Idem |

#### Volet "Solde"
| Champ | Type | Intention déduite |
|---|---|---|
| `balanceDue` | `BigDecimal` | Reste à payer — pas de suivi calculé |

> **Champs bien utilisés** : `invoiceDate`, `dueDate`, `approvedBy`, `paymentTerms`
> (entrée seulement, schema-only).

---

### `bol/` — BillOfLading (11 orphelins)

`fr.alb.bol.model.BillOfLading`

#### Volet "Compliance COPRAR / CUSCAR"
| Champ | Type | Intention déduite |
|---|---|---|
| `bolDate` | `LocalDate` | Date d'émission BOL (≠ createdAt) — jamais lu |
| `bolStatus` | `String` (ISSUED/SURRENDERED/CANCELLED/AMENDED) | Cycle de vie BOL — non suivi (le `status` public à part est utilisé) |
| `originalBolId` | `String` | Référence amendement — pas de chaîne d'amendements implémentée |
| `houseBolNumber` | `String` | House BOL du freight forwarder — jamais consulté |
| `masterBolNumber` | `String` | Master BOL du carrier — idem |

#### Volet "Commercial / fret"
| Champ | Type | Intention déduite |
|---|---|---|
| `incoterms` | `String` | INCOTERMS 2020 (FOB, CIF, EXW, DAP…) — aucun calcul de fret ne s'y réfère |
| `freightPayableAt` | `String` (PREPAID/COLLECT/BOTH) | Lieu de paiement fret — jamais lu |
| `freightCharges` | `BigDecimal` | Montant fret déclaré — jamais consommé |
| `freightCurrency` | `String` | Devise fret — idem |

#### Volet "Voyage maritime"
| Champ | Type | Intention déduite |
|---|---|---|
| `onBoardDate` | `Instant` | Date d'embarquement (on-board) — jamais consulté |
| `transshipmentPort` | `String` | Port de transbordement — pas de gestion transshipment |
| `documentationComplete` | `boolean` | Flag complétude doc — jamais consulté |

> **Champs bien utilisés** : `blNumber`, `shipper`, `consignee`, `itemIds`,
> `bookingNumber`, `transport`, `status` (public).

---

### `billing/` — Payment (2 orphelins)

`fr.alb.billing.model.Payment`

| Champ | Type | Intention déduite |
|---|---|---|
| `checkNumber` | `String` | Numéro de chèque — pas de support du mode chèque |
| `reversedPaymentId` | `String` | Référence du paiement annulé — pas de logique de reversal (alors que `reversalReason` est utilisé) |

---

### `berth/` — Visit (2 orphelins)

`fr.alb.berth.model.Visit`

| Champ | Type | Intention déduite |
|---|---|---|
| `inboundCaptain` | `String` | Nom du capitaine à l'arrivée — descriptif jamais consulté |
| `outboundCaptain` | `String` | Idem au départ |

---

### Modèles **propres** (aucun champ orphelin détecté)

- `customs/model/CustomsDeclaration` — tous les timestamps (submittedAt, heldAt, clearedAt, rejectedAt), raisons (holdReason, rejectionReason) et notes sont consommés
- `yard/model/Item` — y compris les champs documentaires (hsCode, countryOfOrigin) lus par EdiProcessor ; lastInspectionDate et nextInspectionDate sont mappés en DTO de sortie
- `equipment/model/Operator` — champs opérationnels (certifications, userId) utilisés
- `dd/model/DdRule` — toutes les règles de calcul D&D sont consultées par les services D&D (ddType, carrierId, freeDays, clockAnchor, containerTypeCode, includeHolidays, includeWeekends)
- `ai/featurerequest/model/FeatureRequest` — tickets utilisent ticketNumber, category, assignedTo, dueDate, milestone

---

## Hot zones (où l'écart intention ↔ implémentation est le plus marqué)

### 1. Gestion client avancée (ThirdParty — 16 champs)

**Intention** : modèle client "complet" avec RBAC par modules, validation de
crédit, paiements directs et routing EDI.

**Réalité** : le RBAC est au niveau **utilisateur** (User.roles via Azure AD
groups), pas au niveau **tiers**. La facturation utilise les termes du
**contrat**, pas du tiers. Pas d'intégration bancaire.

**Conséquence** : les opérateurs remplissent un formulaire dont la moitié des
champs n'a aucun effet runtime — confusion garantie ("j'ai coché Billing,
pourquoi l'utilisateur n'y a pas accès ?").

### 2. Cycle de vie complet des factures (Invoice — 11 champs)

**Intention** : workflow d'approbation avec audit (approvalDate, sentDate,
cancelledBy/Date/Reason), gestion fine du solde, notes client.

**Réalité** : facture **générée et c'est tout** — pas de workflow d'approbation,
pas de cancellation, pas de tracking d'envoi.

### 3. Conformité internationale BOL (BillOfLading — 11 champs)

**Intention** : compliance COPRAR/CUSCAR (Master vs House BOL, INCOTERMS,
transbordement, charges de fret déclarées).

**Réalité** : le BOL est un **conteneur de références à des items**, rien de
plus. Les charges sont calculées par le contrat sur les items, pas par le BOL.

---

## Recommandations de nettoyage

### Priorité P1 — Documenter dans le code (≤ 1 j)

Pour les 42 champs orphelins, annoter avec un commentaire JavaDoc explicite :

```java
/**
 * Reserved for future RBAC integration (v2+). Currently stored on create/update
 * but ignored by all business logic — do not rely on this field for security
 * decisions. See docs/orphan-fields.md.
 */
public String accessType;
```

Évite que d'autres devs (ou agents IA) tirent des conclusions fausses du modèle.

### Priorité P2 — Décider du destin de chaque hot zone (équipe)

| Hot zone | Option A : implémenter | Option B : retirer |
|---|---|---|
| ThirdParty RBAC (4 champs Access*) | Brancher un filtre menu front basé sur `modulesRequired` | Supprimer la section "Access Information" du formulaire (TC discuté) |
| Invoice cycle de vie (11 champs) | Implémenter workflow d'approbation + tracking envoi | Garder en réserve, marquer `@Deprecated(forRemoval = false)` |
| BOL compliance (11 champs) | Activer la génération INVOIC + COPRAR | Idem, garder en réserve |
| Coordonnées bancaires ThirdParty (4 champs) | Brancher SEPA/SWIFT au paiement | Retirer si pas de plan paiement direct |

### Priorité P3 — Nettoyage UI sans toucher au schéma

Quoi qu'il advienne du back, le front peut déjà :
- masquer les champs sans usage (ex : section "Access Information" → suppression ou note "à provisionner manuellement dans Entra")
- afficher en gris/disabled les champs "future" avec un tooltip explicatif

Ça aligne l'UX sur la réalité fonctionnelle sans risquer une migration de
données.

---

## Annexe — Comment reproduire l'analyse

```bash
cd FactBack-main
# Pour chaque champ d'un modèle, compter les occurrences hors fichier d'origine et tests :
for field in accessType modulesRequired creditLimit ediPartnerCode invoiceDeliveryMethod; do
  count=$(grep -rE "\\b$field\\b" src/main/java | grep -v "ThirdParty.java" | wc -l)
  echo "$field → $count refs"
done
```

Un champ avec 0 référence hors source et hors getter/setter est candidat à
l'orphelinat.
