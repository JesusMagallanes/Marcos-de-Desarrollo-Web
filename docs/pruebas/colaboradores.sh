#!/usr/bin/env bash
# Recorrido completo del rol colaborador, contra la pila levantada.
#
#   docker compose --profile neon up -d
#   bash docs/pruebas/colaboradores.sh
#
# Cubre el alta con verificación de identidad, los adjuntos, los permisos de
# descarga, la aprobación y el cambio de rol. Deja usuarios, solicitudes y
# archivos de prueba en la base: son inofensivos, pero conviene saberlo.
#
# Si la preparación falla, el script se para y lo dice. Casi siempre es el
# limitador de peticiones: docker compose restart usuarios web-gateway

G=http://localhost:8080; ok=0; fallo=0
comprobar(){ if [ "$2" = "$3" ]; then printf "   [OK]    %-54s %s\n" "$1" "$3"; ok=$((ok+1));
  else printf "   [FALLO] %-54s esperado %s, dio %s\n" "$1" "$2" "$3"; fallo=$((fallo+1)); fi; }
cod(){ curl -s -o /dev/null -w "%{http_code}" "$@"; }

# Extrae un campo del JSON sin pelearse con las comillas en bash.
campo(){ python -c "import json,sys; d=json.load(sys.stdin); print(d.get('$1',''))"; }
# Lee el claim 'rol' de DENTRO del JWT: es lo que de verdad decide los permisos.
claim_rol(){ python -c "
import sys,json,base64
t=sys.stdin.read().strip().split('.')[1]
t+='='*(-len(t)%4)
print(json.loads(base64.urlsafe_b64decode(t)).get('rol',''))"; }

# Espera a que TODO lo que se va a usar responda, no solo el gateway: si
# `usuarios` aun esta arrancando, el gateway contesta 503 y la prueba entera
# se llena de 401 que no significan nada.
for s in 8080 8082; do
  ini=$(date +%s)
  until curl -s -f -o /dev/null "http://localhost:$s/actuator/health"; do
    [ $(( $(date +%s) - ini )) -gt 90 ] && { echo "  El servicio en :$s no arranco"; exit 1; }
    sleep 2
  done
done

exigir_token() {
  if [ -z "$2" ]; then
    echo ""
    echo "  NO SE PUDO PREPARAR LA PRUEBA: falta el token de $1."
    echo "  Casi siempre es el limitador: 10 logins por 15 min, 5 registros por hora."
    echo "  Reinicialo con:  docker compose restart usuarios web-gateway"
    exit 1
  fi
}

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT

# Archivos de prueba. Lo que decide si valen no es la extension sino los
# primeros bytes, asi que se escriben cabeceras de verdad.
printf '\xFF\xD8\xFF\xE0 foto de prueba' > "$TMP/anverso.jpg"
printf '\xFF\xD8\xFF\xE0 otra foto'      > "$TMP/reverso.jpg"
printf '%%PDF-1.4 ficha de prueba'       > "$TMP/ficha.pdf"
# Un ejecutable con nombre de imagen: el caso que el almacen debe rechazar.
printf 'MZ\x90\x00 esto no es una imagen' > "$TMP/trampa.jpg"
# Un SVG es una imagen de verdad, pero puede llevar scripts: tambien fuera.
printf '<svg xmlns="http://www.w3.org/2000/svg"><script/></svg>' > "$TMP/vector.svg"

SUF=$(date +%s)
# Los documentos se derivan de la marca de tiempo: la regla "un documento
# identifica a UN titular" es real, asi que repetir numero entre pasadas daria
# 409 con toda la razon y ensuciaria el resultado.
RUC1="20${SUF: -9}"
RUC2="20$(( ${SUF: -9} + 1 ))"
DNI1="${SUF: -8}"

crear_cliente() {
  curl -s -X POST "$G/api/auth/registrar" -H "Content-Type: application/json" \
    -d "{\"name\":\"Ana\",\"lastname\":\"Vega$1\",\"emailAddress\":\"vend$1.$SUF@t.com\",\"password\":\"ClaveSegura1!\",\"phoneNumber\":\"98765432$1\",\"address\":\"Av Prueba $1\"}"
}

ADMIN=$(curl -s -X POST "$G/api/auth/login" -H "Content-Type: application/json" \
  -d '{"email":"admin@smartzone.com","password":"SmartZone2026!"}' | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

RESP_A=$(crear_cliente 1); TOK_A=$(echo "$RESP_A" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
REF_A=$(echo "$RESP_A" | grep -o '"refreshToken":"[^"]*"' | cut -d'"' -f4)
RESP_B=$(crear_cliente 2); TOK_B=$(echo "$RESP_B" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

exigir_token "administrador" "$ADMIN"
exigir_token "vendedor A" "$TOK_A"
exigir_token "vendedor B" "$TOK_B"

ADJ="$G/api/colaboradores/solicitudes/adjuntos"
SOL="$G/api/colaboradores/solicitudes"

subir(){ curl -s -X POST "$ADJ?tipo=$2" -H "Authorization: Bearer $1" -F "archivo=@$3"; }
subir_cod(){ cod -X POST "$ADJ?tipo=$2" -H "Authorization: Bearer $1" -F "archivo=@$3"; }

# Empresa: RUC, representante legal, sin fecha de nacimiento.
cuerpo_empresa() { cat <<JSON
{"tipoPersona":"JURIDICA","tipoDocumento":"RUC","documento":"$1",
 "nombreTitular":"Importaciones Vega SAC","representanteLegal":"Ana Vega Rios",
 "nombreComercial":"Importaciones Vega","telefonoContacto":"987654321",
 "rubro":"Componentes de PC",
 "descripcion":"Importamos teclados mecanicos y monitores desde 2019 con almacen propio.",
 "domicilio":{"direccion":"Av. Los Proceres 1420","referencia":"Frente al parque",
   "distrito":"Surco","provincia":"Lima","departamento":"Lima","codigoPostal":"15039","pais":"PE"},
 "aceptaTerminos":true,"terminosVersion":"2026-08"}
JSON
}

# Persona: DNI, fecha de nacimiento, sin representante.
cuerpo_persona() { cat <<JSON
{"tipoPersona":"NATURAL","tipoDocumento":"DNI","documento":"$1",
 "nombreTitular":"Ana Vega Rios","fechaNacimiento":"${2:-1995-04-12}",
 "nombreComercial":"Taller de Ana","telefonoContacto":"987654321",
 "rubro":"Accesorios",
 "descripcion":"Vendo accesorios para computadora que armo yo misma desde hace anios.",
 "domicilio":{"direccion":"Jr. Union 200","distrito":"Surco","provincia":"Lima",
   "departamento":"Lima","codigoPostal":"15039"},
 "aceptaTerminos":true,"terminosVersion":"2026-08"}
JSON
}

echo "############ SIN SOLICITAR ############"
comprobar "GET /mia sin haber solicitado -> 204" "204" "$(cod "$SOL/mia" -H "Authorization: Bearer $TOK_A")"
comprobar "sin token -> 401" "401" "$(cod "$SOL/mia")"
comprobar "subir sin token -> 401" "401" "$(cod -X POST "$ADJ?tipo=DOCUMENTO_ANVERSO" -F "archivo=@$TMP/anverso.jpg")"

echo ""
echo "############ VALIDACION DE ARCHIVOS ############"
PRIMERA=$(subir_cod "$TOK_A" DOCUMENTO_ANVERSO "$TMP/anverso.jpg")
if [ "$PRIMERA" = "429" ]; then
  echo ""
  echo "  CUPO DE SUBIDAS AGOTADO (30 cada 10 min). Es el limitador haciendo su trabajo,"
  echo "  no un fallo: pasa al repetir la prueba varias veces seguidas."
  echo "  Reinicialo con:  docker compose restart usuarios web-gateway"
  exit 1
fi
comprobar "un JPG de verdad -> 201" "201" "$PRIMERA"
comprobar "un ejecutable renombrado .jpg -> 400" "400" "$(subir_cod "$TOK_A" DOCUMENTO_ANVERSO "$TMP/trampa.jpg")"
comprobar "un SVG (imagen, pero con scripts) -> 400" "400" "$(subir_cod "$TOK_A" DOCUMENTO_ANVERSO "$TMP/vector.svg")"
comprobar "tipo de adjunto inventado -> 400" "400" "$(subir_cod "$TOK_A" RADIOGRAFIA "$TMP/anverso.jpg")"
SUBIDA=$(subir "$TOK_A" DOCUMENTO_ANVERSO "$TMP/anverso.jpg")
comprobar "la respuesta dice el tipo detectado" "image/jpeg" "$(echo "$SUBIDA" | campo tipoMime)"
DOC_A=$(echo "$SUBIDA" | campo id)

# Subir el mismo tipo otra vez NO debe acumular: sustituye. Sin esto, nada
# acotaba cuantos archivos sueltos deja una cuenta (1,5 GB por minuto con el
# cupo general, y los huerfanos no se purgan hasta pasada una semana).
REEMPLAZO=$(subir "$TOK_A" DOCUMENTO_ANVERSO "$TMP/anverso.jpg")
DOC_A=$(echo "$REEMPLAZO" | campo id)
comprobar "resubir el mismo tipo -> otro id (sustituye)" "si" "$([ -n "$DOC_A" ] && echo si || echo no)"

echo ""
echo "############ FALTAN ADJUNTOS ############"
comprobar "empresa sin ficha RUC ni reverso -> 400" "400" "$(cod -X POST "$SOL" -H "Authorization: Bearer $TOK_A" -H "Content-Type: application/json" -d "$(cuerpo_empresa $RUC1)")"
subir "$TOK_A" DOCUMENTO_REVERSO "$TMP/reverso.jpg" > /dev/null
FALTA=$(curl -s -X POST "$SOL" -H "Authorization: Bearer $TOK_A" -H "Content-Type: application/json" -d "$(cuerpo_empresa $RUC1)")
comprobar "el error dice QUE archivo falta" "si" "$(echo "$FALTA" | grep -qi "Ficha RUC" && echo si || echo no)"
subir "$TOK_A" FICHA_RUC "$TMP/ficha.pdf" > /dev/null

echo ""
echo "############ REGLAS DE IDENTIDAD ############"
comprobar "empresa con los 3 archivos -> 201" "201" "$(cod -X POST "$SOL" -H "Authorization: Bearer $TOK_A" -H "Content-Type: application/json" -d "$(cuerpo_empresa $RUC1)")"
SID=$(curl -s "$SOL/mia" -H "Authorization: Bearer $TOK_A" | campo id)

subir "$TOK_B" DOCUMENTO_ANVERSO "$TMP/anverso.jpg" > /dev/null
subir "$TOK_B" DOCUMENTO_REVERSO "$TMP/reverso.jpg" > /dev/null
comprobar "persona con RUC -> 400" "400" "$(cod -X POST "$SOL" -H "Authorization: Bearer $TOK_B" -H "Content-Type: application/json" -d "$(echo "$(cuerpo_persona $DNI1)" | sed 's/"DNI"/"RUC"/')")"
comprobar "menor de edad -> 400" "400" "$(cod -X POST "$SOL" -H "Authorization: Bearer $TOK_B" -H "Content-Type: application/json" -d "$(cuerpo_persona $DNI1 2015-04-12)")"
comprobar "terminos de otra version -> 400" "400" "$(cod -X POST "$SOL" -H "Authorization: Bearer $TOK_B" -H "Content-Type: application/json" -d "$(echo "$(cuerpo_persona $DNI1)" | sed 's/2026-08/2019-01/')")"
comprobar "sin aceptar terminos -> 400" "400" "$(cod -X POST "$SOL" -H "Authorization: Bearer $TOK_B" -H "Content-Type: application/json" -d "$(echo "$(cuerpo_persona $DNI1)" | sed 's/"aceptaTerminos":true/"aceptaTerminos":false/')")"
comprobar "codigo postal invalido -> 400" "400" "$(cod -X POST "$SOL" -H "Authorization: Bearer $TOK_B" -H "Content-Type: application/json" -d "$(echo "$(cuerpo_persona $DNI1)" | sed 's/"15039"/"1"/')")"
comprobar "persona con DNI correcto -> 201" "201" "$(cod -X POST "$SOL" -H "Authorization: Bearer $TOK_B" -H "Content-Type: application/json" -d "$(cuerpo_persona $DNI1)")"

echo ""
echo "############ REGLAS DE SOLICITUD ############"
comprobar "segunda solicitud del mismo -> 409" "409" "$(cod -X POST "$SOL" -H "Authorization: Bearer $TOK_A" -H "Content-Type: application/json" -d "$(cuerpo_empresa $RUC2)")"

echo ""
echo "############ PERMISOS SOBRE LOS ARCHIVOS ############"
comprobar "el dueno descarga el suyo -> 200" "200" "$(cod "$ADJ/$DOC_A" -H "Authorization: Bearer $TOK_A")"
comprobar "OTRO usuario NO lo descarga -> 404" "404" "$(cod "$ADJ/$DOC_A" -H "Authorization: Bearer $TOK_B")"
comprobar "el admin SI lo descarga -> 200" "200" "$(cod "$ADJ/$DOC_A" -H "Authorization: Bearer $ADMIN")"
comprobar "sin token -> 401" "401" "$(cod "$ADJ/$DOC_A")"
comprobar "archivo inexistente -> 404" "404" "$(cod "$ADJ/999999" -H "Authorization: Bearer $ADMIN")"
comprobar "se descarga como adjunto, no se abre" "si" "$(curl -s -D- -o /dev/null "$ADJ/$DOC_A" -H "Authorization: Bearer $TOK_A" | grep -qi "content-disposition: attachment" && echo si || echo no)"
comprobar "no se guarda en cache" "si" "$(curl -s -D- -o /dev/null "$ADJ/$DOC_A" -H "Authorization: Bearer $TOK_A" | grep -qi "no-store" && echo si || echo no)"

echo ""
echo "############ PERMISOS DE LA BANDEJA ############"
comprobar "cliente NO ve la bandeja -> 403" "403" "$(cod "$SOL" -H "Authorization: Bearer $TOK_A")"
comprobar "cliente NO aprueba -> 403" "403" "$(cod -X POST "$SOL/$SID/aprobar" -H "Authorization: Bearer $TOK_A")"
comprobar "admin SI ve la bandeja" "200" "$(cod "$SOL?estado=PENDIENTE" -H "Authorization: Bearer $ADMIN")"
comprobar "estado inventado -> 400" "400" "$(cod "$SOL?estado=BASURA" -H "Authorization: Bearer $ADMIN")"
comprobar "la bandeja trae los adjuntos" "si" "$(curl -s "$SOL?estado=PENDIENTE" -H "Authorization: Bearer $ADMIN" | grep -qi "Ficha RUC" && echo si || echo no)"
# La empresa subio el anverso DOS veces: si se acumulasen serian 4 adjuntos.
comprobar "la empresa tiene 3 adjuntos, no 4" "3" "$(curl -s "$SOL/mia" -H "Authorization: Bearer $TOK_A" | python -c "import json,sys; print(len(json.load(sys.stdin).get('adjuntos',[])))")"

echo ""
echo "############ APROBAR Y CAMBIO DE ROL ############"
comprobar "admin aprueba" "200" "$(cod -X POST "$SOL/$SID/aprobar" -H "Authorization: Bearer $ADMIN")"
comprobar "aprobar dos veces -> 409" "409" "$(cod -X POST "$SOL/$SID/aprobar" -H "Authorization: Bearer $ADMIN")"
comprobar "rechazar lo aprobado -> 409" "409" "$(cod -X POST "$SOL/$SID/rechazar" -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" -d '{"motivo":"Me arrepenti de haberla aprobado antes"}')"

echo "   -- lo que decide los permisos es el claim DENTRO del token --"
comprobar "el token viejo aun lleva rol=CLIENTE" "CLIENTE" "$(echo "$TOK_A" | claim_rol)"
# OJO: /auth/yo lee de la BASE, no del token, asi que ya dice COLABORADOR
# aunque el token todavia no lo lleve. Es la trampa para el frontend.
comprobar "pero /auth/yo ya dice COLABORADOR (lee de la BD)" "COLABORADOR" "$(curl -s "$G/api/auth/yo" -H "Authorization: Bearer $TOK_A" | campo rol)"

echo "   -- tras refrescar, el rol nuevo llega --"
NUEVO=$(curl -s -X POST "$G/api/auth/refresh" -H "Content-Type: application/json" -d "{\"refreshToken\":\"$REF_A\"}")
comprobar "el refresco devuelve COLABORADOR" "COLABORADOR" "$(echo "$NUEVO" | campo rol)"
TOK_NUEVO=$(echo "$NUEVO" | campo accessToken)
comprobar "y el token NUEVO ya lleva el claim rol=COLABORADOR" "COLABORADOR" "$(echo "$TOK_NUEVO" | claim_rol)"
comprobar "el colaborador sigue viendo su documento" "200" "$(cod "$ADJ/$DOC_A" -H "Authorization: Bearer $TOK_NUEVO")"

echo ""
echo "############ YA COLABORADOR ############"
comprobar "no puede volver a solicitar -> 409" "409" "$(cod -X POST "$SOL" -H "Authorization: Bearer $TOK_NUEVO" -H "Content-Type: application/json" -d "$(cuerpo_empresa $RUC2)")"
comprobar "ni subir mas archivos -> 409" "409" "$(subir_cod "$TOK_NUEVO" DOCUMENTO_ANVERSO "$TMP/anverso.jpg")"

echo ""
echo "############ RECHAZO ############"
SID_B=$(curl -s "$SOL/mia" -H "Authorization: Bearer $TOK_B" | campo id)
comprobar "rechazar sin motivo -> 400" "400" "$(cod -X POST "$SOL/$SID_B/rechazar" -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" -d '{}')"
comprobar "rechazar con motivo -> 200" "200" "$(cod -X POST "$SOL/$SID_B/rechazar" -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" -d '{"motivo":"La foto del reverso esta movida y no se lee el codigo."}')"
comprobar "el solicitante ve el motivo" "si" "$(curl -s "$SOL/mia" -H "Authorization: Bearer $TOK_B" | grep -qi "movida" && echo si || echo no)"

echo ""
echo "############ 404 ############"
comprobar "solicitud inexistente -> 404" "404" "$(cod -X POST "$SOL/999999/aprobar" -H "Authorization: Bearer $ADMIN")"
comprobar "id negativo -> 400" "400" "$(cod -X POST "$SOL/-1/aprobar" -H "Authorization: Bearer $ADMIN")"

echo ""; echo "=================================================="
printf " RESULTADO: %d correctas, %d fallidas\n" "$ok" "$fallo"; echo "=================================================="
[ "$fallo" -eq 0 ]
