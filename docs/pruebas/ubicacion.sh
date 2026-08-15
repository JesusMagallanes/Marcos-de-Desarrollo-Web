#!/usr/bin/env bash
# El punto del mapa, de punta a punta: se manda al enviar la solicitud, se guarda
# y vuelve en la respuesta y en la bandeja del administrador.
#
#   docker compose --profile neon up -d
#   bash docs/pruebas/ubicacion.sh
#
# Se prueba contra la pila levantada y no solo con unitarias porque lo que puede
# romperse aqui esta en las costuras: la columna nueva, el CHECK y el JSON.
#
# Deja tres usuarios y tres solicitudes de prueba en la base. Son inofensivos,
# pero conviene saberlo.
G=http://localhost:8080; ok=0; fallo=0
comprobar(){ if [ "$2" = "$3" ]; then printf "   [OK]    %-54s %s\n" "$1" "$3"; ok=$((ok+1));
  else printf "   [FALLO] %-54s esperado %s, dio %s\n" "$1" "$2" "$3"; fallo=$((fallo+1)); fi; }
ruta(){ python -c "
import json,sys
d=json.load(sys.stdin)
for p in '$1'.split('.'):
    d = d.get(p) if isinstance(d, dict) else None
    if d is None: break
print('' if d is None else d)"; }

SUF=$(date +%s)
RUC1="20${SUF: -9}"; RUC2="10${SUF: -9}"; RUC3="20${SUF: -8}1"

crear_cliente(){ curl -s -X POST "$G/api/auth/registrar" -H "Content-Type: application/json" \
  -d "{\"name\":\"Ana\",\"lastname\":\"Vega$1\",\"emailAddress\":\"ubi$1.$SUF@t.com\",\"password\":\"ClaveSegura1!\",\"phoneNumber\":\"98765432$1\",\"address\":\"Av Prueba $1\"}"; }

cuerpo(){ cat <<JSON
{"tipoPersona":"JURIDICA","tipoDocumento":"RUC","documento":"$1",
 "nombreTitular":"Importaciones Vega SAC","representanteLegal":"Ana Vega Rios",
 "nombreComercial":"Importaciones Vega","telefonoContacto":"987654321",
 "rubro":"Componentes de PC",
 "descripcion":"Importamos teclados mecanicos y monitores desde 2019 con almacen propio.",
 "domicilio":{"direccion":"Av. Los Proceres 1420","referencia":"Frente al parque",
   "distrito":"Surco","provincia":"Lima","departamento":"Lima","codigoPostal":"15039",
   "pais":"PE"$2},
 "aceptaTerminos":true,"terminosVersion":"2026-08"}
JSON
}

ADMIN=$(curl -s -X POST "$G/api/auth/login" -H "Content-Type: application/json" \
  -d '{"email":"admin@smartzone.com","password":"SmartZone2026!"}' | ruta accessToken)
TOK_A=$(crear_cliente 1 | ruta accessToken)
TOK_B=$(crear_cliente 2 | ruta accessToken)
TOK_C=$(crear_cliente 3 | ruta accessToken)
for tk in "$TOK_A" "$TOK_B" "$TOK_C" "$ADMIN"; do
  [ -z "$tk" ] && { echo "  NO SE PUDO PREPARAR: falta algun token."
    echo "  Casi siempre es el limitador: 5 registros por hora, 10 logins por 15 min."
    echo "  Reinicialo con:  docker compose restart usuarios web-gateway"; exit 1; }
done

# Sin los tres documentos la solicitud se rechaza antes de mirar el domicilio,
# asi que hay que subirlos o esta prueba nunca llega a lo que quiere probar.
#
# Se generan aqui en vez de guardarlos en el repositorio: el backend mira los
# bytes magicos, no la extension, asi que tienen que ser un PNG y un PDF de
# verdad, y dos binarios versionados para esto no valen la pena.
#
# La carpeta se crea junto al script y no en /tmp: en Git Bash sobre Windows el
# curl del sistema no entiende las rutas de MSYS y el `-F archivo=@/tmp/...` se
# queda sin subir nada, sin decirlo.
DIR=$(mktemp -d "$(dirname "$0")/tmp.XXXXXX")
trap 'rm -rf "$DIR"' EXIT
python - "$DIR" <<'PY'
import struct, sys, zlib
from pathlib import Path

destino = Path(sys.argv[1])

def trozo(tipo, datos):
    c = tipo + datos
    return struct.pack('>I', len(datos)) + c + struct.pack('>I', zlib.crc32(c))

(destino / 'doc.png').write_bytes(
    b'\x89PNG\r\n\x1a\n'
    + trozo(b'IHDR', struct.pack('>IIBBBBB', 1, 1, 8, 2, 0, 0, 0))
    + trozo(b'IDAT', zlib.compress(b'\x00\xff\xff\xff'))
    + trozo(b'IEND', b''))

(destino / 'ficha.pdf').write_bytes(
    b'%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n'
    b'2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n'
    b'3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n'
    b'trailer<</Root 1 0 R>>\n%%EOF\n')
PY

adjuntar(){ # $1 token
  for par in "DOCUMENTO_ANVERSO:$DIR/doc.png:image/png" \
             "DOCUMENTO_REVERSO:$DIR/doc.png:image/png" \
             "FICHA_RUC:$DIR/ficha.pdf:application/pdf"; do
    tipo="${par%%:*}"; resto="${par#*:}"; f="${resto%%:*}"; mime="${resto##*:}"
    curl -s -o /dev/null -X POST "$G/api/colaboradores/solicitudes/adjuntos?tipo=$tipo" \
      -H "Authorization: Bearer $1" -F "archivo=@$f;type=$mime"
  done
}
adjuntar "$TOK_A"; adjuntar "$TOK_B"; adjuntar "$TOK_C"

echo "############ CON PUNTO EN EL MAPA ############"
CREADA=$(curl -s -X POST "$G/api/colaboradores/solicitudes" -H "Authorization: Bearer $TOK_A" \
  -H "Content-Type: application/json" -d "$(cuerpo $RUC1 ',"latitud":-12.046374,"longitud":-77.042793')")
SID=$(echo "$CREADA" | ruta id)
comprobar "se crea la solicitud" "si" "$([ -n "$SID" ] && echo si || echo no)"
comprobar "la latitud vuelve en la respuesta" "-12.046374" "$(echo "$CREADA" | ruta domicilio.latitud)"
comprobar "la longitud vuelve en la respuesta" "-77.042793" "$(echo "$CREADA" | ruta domicilio.longitud)"

echo "   -- y sigue ahi al releerla, o sea que se guardo de verdad --"
MIA=$(curl -s "$G/api/colaboradores/solicitudes/mia" -H "Authorization: Bearer $TOK_A")
comprobar "GET /mia trae la latitud" "-12.046374" "$(echo "$MIA" | ruta domicilio.latitud)"

echo "   -- quien revisa la ve: es para lo que sirve --"
BANDEJA=$(curl -s "$G/api/colaboradores/solicitudes?estado=PENDIENTE" -H "Authorization: Bearer $ADMIN")
comprobar "en la bandeja del admin" "-77.042793" "$(python -c "
import json,sys
d=json.load(sys.stdin)
s=[x for x in d if x['id']==$SID]
print(s[0]['domicilio']['longitud'] if s else 'no-esta')" <<<"$BANDEJA")"

echo ""
echo "############ SIN PUNTO (SIGUE SIENDO OPCIONAL) ############"
SIN=$(curl -s -X POST "$G/api/colaboradores/solicitudes" -H "Authorization: Bearer $TOK_B" \
  -H "Content-Type: application/json" -d "$(cuerpo $RUC2 '')")
comprobar "se crea igual sin coordenadas" "si" "$([ -n "$(echo "$SIN" | ruta id)" ] && echo si || echo no)"
comprobar "y vuelven nulas, no en cero" "" "$(echo "$SIN" | ruta domicilio.latitud)"

echo ""
echo "############ LO QUE NO SE ACEPTA ############"
# Se mira el MOTIVO y no solo el 400: esta solicitud tiene mas de diez campos
# que pueden fallar, y un 400 a secas pasaria igual el dia que se rompa otro.
# El `detail` de una validacion de campos es siempre el mismo texto generico;
# el motivo de verdad viene en `errores`, que es lo que pinta el formulario.
rechazo(){ curl -s -X POST "$G/api/colaboradores/solicitudes" -H "Authorization: Bearer $TOK_C" \
  -H "Content-Type: application/json" -d "$(cuerpo $RUC3 "$1")" \
  | python -c "
import json,sys
print(' | '.join(sorted(json.load(sys.stdin).get('errores', {}).values())))"; }
comprobar "latitud fuera de rango" "La latitud está fuera de rango" \
  "$(rechazo ',"latitud":999.5,"longitud":-77.0')"
comprobar "longitud fuera de rango" "La longitud está fuera de rango" \
  "$(rechazo ',"latitud":-12.0,"longitud":500.5')"

echo "   -- media coordenada no da error: se ignora entera --"
MEDIA=$(curl -s -X POST "$G/api/colaboradores/solicitudes" -H "Authorization: Bearer $TOK_C" \
  -H "Content-Type: application/json" -d "$(cuerpo $RUC3 ',"latitud":-12.046374')")
comprobar "se acepta la solicitud" "si" "$([ -n "$(echo "$MEDIA" | ruta id)" ] && echo si || echo no)"
comprobar "pero sin guardar media ubicacion" "" "$(echo "$MEDIA" | ruta domicilio.latitud)"

echo ""
echo "   $ok correctas, $fallo fallidas"
[ $fallo -eq 0 ] || exit 1
