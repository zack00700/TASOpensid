#!/usr/bin/env bash
# Seed end-to-end MVP data through the REST API.
#
# Scenario (chronological maritime workflow A → Z):
#   1. A textile shipper in Casablanca exports 3 FCL containers (one being a reefer
#      with fresh produce) to a consignee in Marseille on the MSC ATHENS feeder.
#   2. We register all parties (shipper, consignee, shipping line, trucking, customs).
#   3. The vessel and its visit at Casablanca terminal are created.
#   4. A storage tariff + a tier-1 storage contract for the consignee are set up.
#   5. A Bill of Lading is issued with three Items (20FT FCL textile, 40HC FCL
#      electronics, 40HC reefer fruits).
#   6. Each item is "Gate-In"-ed through the configured event, opening a lifecycle.
#   7. A draft invoice is generated for the consignee from the BL.
#
# Usage:
#   ./scripts/seed-mvp.sh                       # default credentials devuser/devpass123 on localhost
#   ./scripts/seed-mvp.sh -u admin -p secret    # override login
#   ./scripts/seed-mvp.sh -b http://host:8080   # override base URL
#   ./scripts/seed-mvp.sh --reset               # remove previously-seeded docs first

set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
USERNAME="devuser"
PASSWORD="devpass123"
RESET=0

while [ $# -gt 0 ]; do
  case "$1" in
    -u|--user)     USERNAME="$2"; shift 2 ;;
    -p|--password) PASSWORD="$2"; shift 2 ;;
    -b|--base)     BASE="$2"; shift 2 ;;
    --reset)       RESET=1; shift ;;
    -h|--help)     sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

API="$BASE/api"

color() { printf "\033[1;%sm%s\033[0m\n" "$1" "$2" >&2; }
info() { color 36 "[seed] $*"; }
ok()   { color 32 "[seed] $*"; }
warn() { color 33 "[seed] $*"; }
err()  { color 31 "[seed] $*"; }

require() { command -v "$1" >/dev/null || { err "$1 is required"; exit 1; }; }
require curl
require jq

# Idempotent helpers -----------------------------------------------------------
#
# All entities are tagged with a deterministic identifier (companyName, blNumber,
# eventName, etc.) so re-running the script does not duplicate rows. We probe the
# list endpoint first and reuse existing IDs.

post()  { local path="$1" body="$2"; curl -sS -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST  -d "$body" "$API$path"; }
put()   { local path="$1" body="$2"; curl -sS -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X PUT   -d "$body" "$API$path"; }
get()   { local path="$1";           curl -sS -H "Authorization: Bearer $TOKEN" "$API$path"; }
delete(){ local path="$1";           curl -sS -H "Authorization: Bearer $TOKEN" -X DELETE "$API$path"; }

# --- 1. Login ----------------------------------------------------------------
info "Login as $USERNAME …"
LOGIN_BODY=$(printf '{"username":"%s","password":"%s"}' "$USERNAME" "$PASSWORD")
LOGIN_RES=$(curl -sS -H "Content-Type: application/json" -d "$LOGIN_BODY" "$API/auth/login")
TOKEN=$(echo "$LOGIN_RES" | jq -r '.token // empty')
if [ -z "$TOKEN" ]; then
  err "Login failed: $LOGIN_RES"
  exit 1
fi
ok "Token acquired"

# --- 2. Third parties --------------------------------------------------------
info "Creating third parties …"

upsert_third_party() {
  local company="$1" type="$2" body="$3"
  local existing
  existing=$(get "/third-party" | jq -r --arg c "$company" '.[] | select(.companyName==$c) | .id' | head -1)
  if [ -n "$existing" ] && [ "$existing" != "null" ]; then
    info "  · ($type) $company → already present ($existing)"
    echo "$existing"
    return
  fi
  local res; res=$(post "/third-party" "$body")
  local id; id=$(echo "$res" | jq -r '.id // empty')
  if [ -z "$id" ]; then
    err "  ✗ Failed to create $company: $res"; exit 1
  fi
  ok "  ✓ ($type) $company → $id"
  echo "$id"
}

SHIPPER_ID=$(upsert_third_party "GreenLine Textiles SARL" "Shipper" '{
  "fullName":"Karim El Idrissi","jobTitle":"Export Manager","email":"k.elidrissi@greenline-textiles.ma","contactNumber":"+212522123456",
  "companyName":"GreenLine Textiles SARL","companyAddress":"Zone Industrielle Aïn Sebaa, Casablanca, Maroc",
  "industryType":"Other","companyContactPerson":"Karim El Idrissi","companyContactEmail":"export@greenline-textiles.ma",
  "customerCode":"GLT-001","customerType":"SHIPPER","taxIdentificationNumber":"MA40123456",
  "paymentTermsDefault":"NET30","creditLimit":50000,"defaultCurrency":"EUR",
  "invoiceDeliveryMethod":"EMAIL","invoiceElectronicAddress":"invoices@greenline-textiles.ma",
  "creditRating":"A","hazmatApproved":false,"reeferApproved":true,
  "identificationType":"National ID","identificationNumber":"BJ123456",
  "accessType":"Data Entry","modulesRequired":["Billing"]
}')

CONSIGNEE_ID=$(upsert_third_party "Marseille Fresh Imports" "Consignee" '{
  "fullName":"Sophie Lambert","jobTitle":"Import Coordinator","email":"s.lambert@marseille-fresh.fr","contactNumber":"+33491775533",
  "companyName":"Marseille Fresh Imports","companyAddress":"12 Quai du Port, 13002 Marseille, France",
  "industryType":"Other","companyContactPerson":"Sophie Lambert","companyContactEmail":"ops@marseille-fresh.fr",
  "customerCode":"MFI-002","customerType":"CONSIGNEE","taxIdentificationNumber":"FR45123456789",
  "paymentTermsDefault":"NET45","creditLimit":120000,"defaultCurrency":"EUR",
  "invoiceDeliveryMethod":"EMAIL","invoiceElectronicAddress":"ap@marseille-fresh.fr",
  "creditRating":"A","hazmatApproved":false,"reeferApproved":true,
  "iban":"FR7630006000011234567890189","swiftCode":"AGRIFRPP","bankName":"Crédit Agricole",
  "identificationType":"Passport","identificationNumber":"FR9988776",
  "accessType":"Full Access","modulesRequired":["Billing"]
}')

CARRIER_ID=$(upsert_third_party "MSC Mediterranean Shipping" "Carrier" '{
  "fullName":"Giovanni Rossi","jobTitle":"Liner Operations","email":"g.rossi@msc.com","contactNumber":"+41227033333",
  "companyName":"MSC Mediterranean Shipping","companyAddress":"Chemin Rieu 12, 1208 Genève, Suisse",
  "industryType":"Shipping Line","companyContactPerson":"Giovanni Rossi","companyContactEmail":"booking.casa@msc.com",
  "customerCode":"MSC-CAR","customerType":"CARRIER","taxIdentificationNumber":"CHE-105.987.654",
  "paymentTermsDefault":"NET15","defaultCurrency":"USD",
  "invoiceDeliveryMethod":"EDI","ediPartnerCode":"MSCGVA",
  "creditRating":"A","hazmatApproved":true,"reeferApproved":true,
  "identificationType":"Passport","identificationNumber":"CH4455667",
  "accessType":"View Only","modulesRequired":[]
}')

TRUCKER_ID=$(upsert_third_party "Atlas Transit SARL" "Trucker" '{
  "fullName":"Mehdi Bensalem","jobTitle":"Dispatch","email":"dispatch@atlas-transit.ma","contactNumber":"+212522998877",
  "companyName":"Atlas Transit SARL","companyAddress":"Route de Rabat, Casablanca, Maroc",
  "industryType":"Trucking Company","companyContactPerson":"Mehdi Bensalem","companyContactEmail":"ops@atlas-transit.ma",
  "customerCode":"ATL-TR","customerType":"TRUCK_COMPANY","taxIdentificationNumber":"MA40999888",
  "paymentTermsDefault":"NET30","defaultCurrency":"MAD",
  "creditRating":"B","identificationType":"National ID","identificationNumber":"BK998877",
  "accessType":"Data Entry","modulesRequired":["Gate Operations"]
}')

CUSTOMS_BROKER_ID=$(upsert_third_party "Maghreb Customs Services" "Customs Broker" '{
  "fullName":"Fatima Zahra Alaoui","jobTitle":"Senior Broker","email":"f.alaoui@maghreb-customs.ma","contactNumber":"+212522445566",
  "companyName":"Maghreb Customs Services","companyAddress":"Place de l Aéroport, Casablanca, Maroc",
  "industryType":"Customs Broker","companyContactPerson":"Fatima Zahra Alaoui","companyContactEmail":"clearance@maghreb-customs.ma",
  "customerCode":"MCS-CB","customerType":"AGENT","taxIdentificationNumber":"MA40555666",
  "paymentTermsDefault":"NET30","defaultCurrency":"MAD",
  "creditRating":"A","identificationType":"National ID","identificationNumber":"BL112233",
  "accessType":"Data Entry","modulesRequired":["Customs Interface"]
}')

# --- 3. Event Config ---------------------------------------------------------
info "Creating event configs …"
upsert_event() {
  local name="$1" type="$2" billed="$3" scope="${4:-ITEM}"
  local existing
  existing=$(get "/event" | jq -r --arg n "$name" '.[] | select(.eventName==$n) | .id' | head -1)
  if [ -n "$existing" ] && [ "$existing" != "null" ]; then
    info "  · $name ($type) → already present ($existing)"
    echo "$existing"; return
  fi
  local body
  body=$(jq -n --arg n "$name" --arg t "$type" --arg s "$scope" --argjson b "$billed" \
    '{eventName:$n, eventType:$t, billedEvent:$b, scope:$s}')
  local res; res=$(post "/event" "$body")
  # /event returns "Event created <id>" as plain text; recover via GET
  existing=$(get "/event" | jq -r --arg n "$name" '.[] | select(.eventName==$n) | .id' | head -1)
  if [ -z "$existing" ] || [ "$existing" = "null" ]; then
    err "  ✗ Failed to create event $name: $res"; exit 1
  fi
  ok "  ✓ $name ($type) → $existing"
  echo "$existing"
}

EVT_GATE_IN_ID=$(upsert_event "Gate In"                "IN"          false)
EVT_DISCHARGED_ID=$(upsert_event "Discharged from vessel" "INTERMEDIATE" true)
EVT_GATE_OUT_ID=$(upsert_event "Gate Out"              "OUT"         true)

# --- 4. Vessel + Visit -------------------------------------------------------
info "Creating vessel & visit …"
existing_vessel=$(get "/vessel" | jq -r '.[] | select(.name=="MSC ATHENS") | .id' | head -1)
if [ -n "$existing_vessel" ] && [ "$existing_vessel" != "null" ]; then
  VESSEL_ID="$existing_vessel"; info "  · MSC ATHENS → already present ($VESSEL_ID)"
else
  res=$(post "/vessel" '{
    "name":"MSC ATHENS","imoNumber":"9778237","mmsi":"255805991","callSign":"CQDX9",
    "flag":"Portugal","owner":"MSC Shipmanagement Ltd","operator":"MSC Mediterranean Shipping",
    "vesselType":"Container Ship","status":"Active"
  }')
  VESSEL_ID=$(echo "$res" | jq -r '.id // empty')
  if [ -z "$VESSEL_ID" ]; then err "  ✗ Vessel creation failed: $res"; exit 1; fi
  ok "  ✓ MSC ATHENS → $VESSEL_ID"
fi

# Visit MV-CASA-2026-018
existing_visit=$(get "/visit" | jq -r '.[] | select(.visitReference=="MV-CASA-2026-018") | .id' | head -1)
if [ -n "$existing_visit" ] && [ "$existing_visit" != "null" ]; then
  VISIT_ID="$existing_visit"; info "  · MV-CASA-2026-018 → already present ($VISIT_ID)"
else
  visit_body=$(jq -n --arg vId "$VESSEL_ID" '{
    vesselName:"MSC ATHENS", vesselId:$vId, visitReference:"MV-CASA-2026-018",
    phase:"PRE_ARRIVAL", service:"WCCA", serviceName:"West Coast Central America",
    facility:"Terminal A", pol:"MAGES", pod:"FRMRS", finalDestination:"FRMRS",
    eta:"2026-05-25T08:00:00", etd:"2026-05-26T22:00:00",
    inboundVoyage:"018N", outboundVoyage:"019S",
    inboundCaptain:"Capt. Vasco Pereira", outboundCaptain:"Capt. Vasco Pereira",
    lineOperator:"MSC Mediterranean Shipping",
    beginReceive:"2026-05-20T08:00:00", dryCutoff:"2026-05-24T16:00:00",
    reeferCutoff:"2026-05-24T20:00:00", hazCutoff:"2026-05-24T12:00:00",
    emptyPickup:"2026-05-18T08:00:00",
    notes:"Regular weekly feeder, expect crane productivity ~28 mph."
  }')
  res=$(post "/visit" "$visit_body")
  VISIT_ID=$(echo "$res" | jq -r '.id // ._id // empty')
  if [ -z "$VISIT_ID" ]; then err "  ✗ Visit creation failed: $res"; exit 1; fi
  ok "  ✓ Visit MV-CASA-2026-018 → $VISIT_ID"
fi

# --- 5. Tariff + Contract ----------------------------------------------------
info "Creating storage tariff and contract …"

existing_tariff=$(get "/tariffs" | jq -r '.[] | select(.name=="Storage – Standard FCL 2026") | .id' | head -1)
if [ -n "$existing_tariff" ] && [ "$existing_tariff" != "null" ]; then
  TARIFF_ID="$existing_tariff"; info "  · Tariff already present ($TARIFF_ID)"
else
  tariff_body='{
    "name":"Storage – Standard FCL 2026","description":"Banded storage charges per day for full containers, applied after the contractual free days.",
    "serviceType":"STORAGE","status":"ACTIVE",
    "startDate":"2026-01-01","endDate":"2026-12-31",
    "calculationMode":{"type":"Banded","subType":"banded"},
    "rates":[
      {"rateId":"band-1","startQuantity":1,"endQuantity":5,"unitOfMeasurement":"Day","amount":12,"flatCost":0,"currency":"EUR","applicableCategory":"Import","applicableFreightKind":"FCL","priority":1},
      {"rateId":"band-2","startQuantity":6,"endQuantity":15,"unitOfMeasurement":"Day","amount":25,"flatCost":0,"currency":"EUR","applicableCategory":"Import","applicableFreightKind":"FCL","priority":2},
      {"rateId":"band-3","startQuantity":16,"endQuantity":9999,"unitOfMeasurement":"Day","amount":48,"flatCost":0,"currency":"EUR","applicableCategory":"Import","applicableFreightKind":"FCL","priority":3}
    ],
    "notes":"Reefer monitoring billed separately under contract REEFER-MON."
  }'
  res=$(post "/tariffs" "$tariff_body")
  TARIFF_ID=$(echo "$res" | jq -r '.id // empty')
  if [ -z "$TARIFF_ID" ]; then err "  ✗ Tariff creation failed: $res"; exit 1; fi
  ok "  ✓ Tariff Storage Standard 2026 → $TARIFF_ID"
fi

existing_contract=$(get "/contract" | jq -r '.[] | select(.name=="MFI-Storage-2026") | .id' | head -1)
if [ -n "$existing_contract" ] && [ "$existing_contract" != "null" ]; then
  CONTRACT_ID="$existing_contract"; info "  · Contract already present ($CONTRACT_ID)"
else
  contract_body=$(jq -n --arg cId "$CONSIGNEE_ID" --arg tId "$TARIFF_ID" --arg evt "$EVT_GATE_IN_ID" '{
    name:"MFI-Storage-2026", description:"Marseille Fresh Imports — storage agreement for import FCL containers, 5 free days from gate-in.",
    calculationMode:{
      type:"Banded", subType:"banded",
      eventConfig:{id:$evt, eventName:"Gate In", eventType:"IN", billedEvent:false, scope:"ITEM"},
      filters:[]
    },
    status:"Active", startDate:"2026-01-01", endDate:"2026-12-31",
    rates:[
      {rateId:"band-1", startQuantity:1, endQuantity:5,    unitOfMeasurement:"Day", amount:12, flatCost:0, currency:"EUR", applicableCategory:"Import", applicableFreightKind:"FCL", priority:1},
      {rateId:"band-2", startQuantity:6, endQuantity:15,   unitOfMeasurement:"Day", amount:25, flatCost:0, currency:"EUR", applicableCategory:"Import", applicableFreightKind:"FCL", priority:2},
      {rateId:"band-3", startQuantity:16, endQuantity:9999,unitOfMeasurement:"Day", amount:48, flatCost:0, currency:"EUR", applicableCategory:"Import", applicableFreightKind:"FCL", priority:3}
    ],
    tariffId:$tId, customerId:$cId, customerName:"Marseille Fresh Imports", priority:10
  }')
  res=$(post "/contract" "$contract_body")
  CONTRACT_ID=$(echo "$res" | jq -r '.id // empty')
  if [ -z "$CONTRACT_ID" ]; then err "  ✗ Contract creation failed: $res"; exit 1; fi
  ok "  ✓ Contract MFI-Storage-2026 → $CONTRACT_ID"
fi

# --- 6. Bill of Lading with 3 items -----------------------------------------
info "Creating Bill of Lading + 3 items …"

BL_NUMBER="MSCUBL2026CASA0042"
existing_bl=$(get "/billoflading?search=$BL_NUMBER&size=1" \
  | jq -r --arg n "$BL_NUMBER" '(.items // .)[] | select(.blNumber==$n) | .id' \
  | head -1)
if [ -n "$existing_bl" ] && [ "$existing_bl" != "null" ]; then
  BL_ID="$existing_bl"; info "  · BL $BL_NUMBER → already present ($BL_ID)"
else
  bl_body=$(jq -n --arg vId "$VISIT_ID" '{
    blNumber:"MSCUBL2026CASA0042", blType:"Original", bolDate:"2026-05-24", bolStatus:"ISSUED",
    shipper:"GreenLine Textiles SARL",
    consignee:"Marseille Fresh Imports",
    notifyParty:"Marseille Fresh Imports",
    vessel:"MSC ATHENS", voyage:"018N",
    portOfLoading:"MAGES (Casablanca)", portOfDischarge:"FRMRS (Marseille)", placeOfDelivery:"FRMRS",
    transportType:"VESSEL",
    commodity:{name:"Textiles & fresh produce", weight:38000, weightUnit:"kg"},
    bookingNumber:"MSC-BK-2026-998", shippingLine:"MSC", incoterms:"CIF", freightPayableAt:"PREPAID",
    onBoardDate:"2026-05-26T15:30:00Z", countryOfOrigin:"MA",
    hsCodes:["520852","620530","081060"],
    transport:{type:"VESSEL", vesselVisitId:$vId, carrier:"MSC Mediterranean Shipping", modeReference:"MV-CASA-2026-018"},
    status:"Issued",
    items:[
      {
        itemNumber:"MSCU2010101", containerNumber:"MSCU2010101", containerType:"20FT", itemType:"CONTAINER", type:"container",
        position:"YARD-A12", status:"Preadvised", weight:18500, volume:33,
        emptyStatus:"FULL", category:"Import", freightKind:"FCL",
        consigneeName:"Marseille Fresh Imports", shipperName:"GreenLine Textiles SARL",
        bookingNumber:"MSC-BK-2026-998", inboundVoyage:"018N", outboundVoyage:"019S",
        hsCode:"520852", countryOfOrigin:"MA",
        sealNumbers:["SEAL445566"], hazmatFlag:false, reeferFlag:false,
        condition:"GOOD", weightVerified:true, verifiedWeight:18500,
        notes:"Cotton fabric — 20-foot dry container."
      },
      {
        itemNumber:"MSCU4087654", containerNumber:"MSCU4087654", containerType:"40HC", itemType:"CONTAINER", type:"container",
        position:"YARD-B07", status:"Preadvised", weight:24500, volume:76,
        emptyStatus:"FULL", category:"Import", freightKind:"FCL",
        consigneeName:"Marseille Fresh Imports", shipperName:"GreenLine Textiles SARL",
        bookingNumber:"MSC-BK-2026-998", inboundVoyage:"018N", outboundVoyage:"019S",
        hsCode:"620530", countryOfOrigin:"MA",
        sealNumbers:["SEAL778899"], hazmatFlag:false, reeferFlag:false,
        condition:"GOOD", weightVerified:true, verifiedWeight:24500,
        notes:"Apparel — 40HC dry container."
      },
      {
        itemNumber:"MSCU9112233", containerNumber:"MSCU9112233", containerType:"REEFER_40", itemType:"CONTAINER", type:"container",
        position:"REEFER-R02", status:"Preadvised", weight:22000, volume:67,
        emptyStatus:"FULL", category:"Import", freightKind:"FCL",
        consigneeName:"Marseille Fresh Imports", shipperName:"GreenLine Textiles SARL",
        bookingNumber:"MSC-BK-2026-998", inboundVoyage:"018N", outboundVoyage:"019S",
        hsCode:"081060", countryOfOrigin:"MA",
        sealNumbers:["SEAL112299"], hazmatFlag:false, reeferFlag:true, reeferTemperature:2.0,
        condition:"GOOD", weightVerified:true, verifiedWeight:22000,
        notes:"Fresh berries — reefer at +2 °C, monitor every 4 h."
      }
    ]
  }')
  res=$(post "/billoflading" "$bl_body")
  BL_ID=$(echo "$res" | jq -r '.id // empty')
  if [ -z "$BL_ID" ]; then err "  ✗ BL creation failed: $res"; exit 1; fi
  ok "  ✓ BL $BL_NUMBER → $BL_ID"
fi

# --- 7. Gate-in events on each item -----------------------------------------
info "Recording Gate-In events on each container …"
ITEM_IDS=$(get "/billoflading/$BL_ID" | jq -r '.itemIds[]?' || true)
if [ -z "$ITEM_IDS" ]; then
  warn "  · No items resolved on BL; events skipped."
else
  i=0
  for itemId in $ITEM_IDS; do
    # Stagger gate-in by a few hours so the lifecycle has a chronology
    eventDate=$(printf '2026-05-26T%02d:30:00Z' $((10 + i)))
    body=$(jq -n --arg eId "$EVT_GATE_IN_ID" --arg d "$eventDate" '{eventId:$eId, eventDate:$d}')
    res=$(post "/items/$itemId/event" "$body" || echo "{}")
    # /items/{id}/event is the JSON-Patch resource — try the workflow endpoint instead
    if ! echo "$res" | grep -q '"id"\|"lifecycleId"\|CREATED'; then
      res=$(post "/item/$itemId/event" "$body" || echo "{}")
    fi
    if echo "$res" | grep -q 'id'; then
      ok "  ✓ Gate-In on $itemId @ $eventDate"
    else
      warn "  · Gate-In on $itemId failed/ignored: $(echo "$res" | head -c 200)"
    fi
    i=$((i+1))
  done
fi

# --- 8. Draft invoice from the BL --------------------------------------------
info "Generating draft invoice for the consignee …"
CUSTOMER_ENC=$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))' "Marseille Fresh Imports")
draft=$(post "/invoice/bl/$BL_ID/draft/customer/$CUSTOMER_ENC" "{}" || echo "{}")
INVOICE_ID=$(echo "$draft" | jq -r '.invoiceId // .id // empty')
if [ -n "$INVOICE_ID" ]; then
  ok "  ✓ Draft invoice → $INVOICE_ID"
else
  warn "  · Draft invoice could not be generated (status may already be Final). Response: $(echo "$draft" | head -c 200)"
fi

# --- Recap -------------------------------------------------------------------
echo
ok "Seed complete. URLs (front):"
echo "  Tiers              : http://localhost:5173/third-parties"
echo "  Vessels / Visites  : http://localhost:5173/vessel-registry  /  /vessels"
echo "  Bill of Lading     : http://localhost:5173/bills"
echo "  Items              : http://localhost:5173/items"
echo "  Contrats / Tariffs : http://localhost:5173/contracts  /  /tariffs"
echo "  Factures           : http://localhost:5173/invoices"
echo
echo "Identifiants créés :"
echo "  Shipper            : $SHIPPER_ID  (GreenLine Textiles)"
echo "  Consignee          : $CONSIGNEE_ID  (Marseille Fresh Imports)"
echo "  Carrier            : $CARRIER_ID  (MSC)"
echo "  Trucker            : $TRUCKER_ID  (Atlas Transit)"
echo "  Customs Broker     : $CUSTOMS_BROKER_ID  (Maghreb Customs)"
echo "  Vessel             : $VESSEL_ID  (MSC ATHENS)"
echo "  Visit              : $VISIT_ID  (MV-CASA-2026-018)"
echo "  Tariff             : $TARIFF_ID"
echo "  Contract           : $CONTRACT_ID  (MFI-Storage-2026)"
echo "  Bill of Lading     : $BL_ID  ($BL_NUMBER)"
[ -n "${INVOICE_ID:-}" ] && echo "  Invoice (draft)    : $INVOICE_ID"
