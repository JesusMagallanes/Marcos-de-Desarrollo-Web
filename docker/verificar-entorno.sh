#!/bin/sh
# Preflight de la pila: revisa el .env antes de que arranque nada.
#
# Compose solo falla por JWT_SECRET. Las variables opcionales vacias pasan en
# silencio y la pila levanta sin admin, sin login social y con el checkout
# devolviendo 503, sin decir por que. Esto lo dice antes.
#
# Corre como servicio `verificador`; los demas esperan a que termine bien.

set -u

# Los codigos se evaluan aqui: busybox no interpreta \033 dentro de una
# variable al sustituirla en el formato de printf.
rojo=$(printf '\033[0;31m'); verde=$(printf '\033[0;32m')
gris=$(printf '\033[0;90m'); amarillo=$(printf '\033[0;33m'); fin=$(printf '\033[0m')
fallos=0

echo ""
echo "  Verificacion del entorno SmartZone"
echo "  ----------------------------------"
echo ""
echo "  Obligatorias"

obligatoria() {
  nombre=$1; valor=$2; nota=$3
  if [ -z "$valor" ]; then
    printf "    [X] %-20s %s\n" "$nombre" "$nota"
    fallos=$((fallos + 1))
  elif echo "$valor" | grep -qE 'xxxxx|tu_usuario|tu_contrase|cambia-esto|genera-el-tuyo'; then
    printf "    [X] %-20s sigue con el marcador de la plantilla\n" "$nombre"
    fallos=$((fallos + 1))
  else
    printf "    [OK] %-20s configurada\n" "$nombre"
  fi
}

obligatoria "DB_URL"      "${DB_URL:-}"      "URL JDBC de la base de datos"
obligatoria "DB_USER"     "${DB_USER:-}"     "Usuario de base de datos"
obligatoria "DB_PASSWORD" "${DB_PASSWORD:-}" "Contrasena de base de datos"

# El secreto se mide en bytes: por debajo de 32 el arranque de los servicios
# se detiene, porque HS256 no admite claves mas cortas.
secreto="${JWT_SECRET:-}"
bytes=$(printf '%s' "$secreto" | wc -c)
if [ -z "$secreto" ]; then
  printf "    [X] %-20s falta la clave de firma\n" "JWT_SECRET"
  fallos=$((fallos + 1))
elif [ "$bytes" -lt 32 ]; then
  printf "    [X] %-20s solo %s bytes, hacen falta 32\n" "JWT_SECRET" "$bytes"
  fallos=$((fallos + 1))
else
  printf "    [OK] %-20s %s bytes\n" "JWT_SECRET" "$bytes"
fi

echo ""
echo "  Seguridad"

# Row Level Security se desactiva EN SILENCIO si la aplicacion se conecta con el
# rol dueno de las tablas (que en Neon y en la mayoria de gestionados trae
# BYPASSRLS). Aqui no se puede consultar pg_roles, pero si detectar el sintoma
# mas comun: que el rol de la app y el de las migraciones sean el mismo.
migracion="${DB_MIGRACION_USER:-}"
if [ -z "$migracion" ]; then
  printf "    [!] %-20s sin definir: la app migrara y correra con el mismo rol,\n" "DB_MIGRACION_USER"
  printf "        %-20s y si ese rol tiene BYPASSRLS las politicas no se aplican\n" ""
elif [ "$migracion" = "${DB_USER:-}" ]; then
  printf "    [!] %-20s es el mismo rol que DB_USER; revisa que no tenga BYPASSRLS\n" "DB_MIGRACION_USER"
else
  printf "    [OK] %-20s separado del rol de la aplicacion\n" "DB_MIGRACION_USER"
fi

# CORS con comodin deja la API abierta a cualquier web. Los servicios ya fallan
# al arrancar si lo detectan, pero es mejor decirlo antes de construir nada.
origenes="${CORS_ORIGENES:-}"
if [ -z "$origenes" ]; then
  printf "    [ ]  %-20s sin definir, se usara localhost (solo desarrollo)\n" "CORS_ORIGENES"
elif echo "$origenes" | grep -q '\*'; then
  printf "    [X] %-20s contiene un comodin; enumera los origenes exactos\n" "CORS_ORIGENES"
  fallos=$((fallos + 1))
else
  printf "    [OK] %-20s %s\n" "CORS_ORIGENES" "$origenes"
fi

echo ""
echo "  Opcionales"

opcional() {
  nombre=$1; valor=$2; funcion=$3
  if [ -z "$valor" ]; then
    printf "    [ ]  %-20s %s (desactivado)\n" "$nombre" "$funcion"
  else
    printf "    [OK] %-20s %s\n" "$nombre" "$funcion"
  fi
}

opcional "ADMIN_PASSWORD"    "${ADMIN_PASSWORD:-}"    "Administrador inicial"
opcional "GOOGLE_CLIENT_ID"  "${GOOGLE_CLIENT_ID:-}"  "Login con Google"
opcional "FACEBOOK_CLIENT_ID" "${FACEBOOK_CLIENT_ID:-}" "Login con Facebook"
opcional "MP_ACCESS_TOKEN"   "${MP_ACCESS_TOKEN:-}"   "Cobros con MercadoPago"
opcional "MP_WEBHOOK_SECRET" "${MP_WEBHOOK_SECRET:-}" "Verificacion de webhooks"
opcional "MP_NOTIFICACION_URL" "${MP_NOTIFICACION_URL:-}" "Aviso de cobro (webhook)"

# El cobro se hace y la tienda no se entera, sin un solo error por ninguna
# parte. Se avisa aqui porque es lo unico que se lee antes de arrancar, y
# porque este fallo ya costo semanas de "el pago no llega".
publica() {
  case "$1" in
    ""|*localhost*|*127.0.0.1*|*://192.168.*|*://10.*|*://0.0.0.0*) return 1 ;;
    *) return 0 ;;
  esac
}

if [ -n "${MP_ACCESS_TOKEN:-}" ]; then
  if ! publica "${MP_RETORNO_BASE:-}"; then
    echo ""
    printf "    [!]  MP_RETORNO_BASE no es publica (%s).
" "${MP_RETORNO_BASE:-vacia}"
    echo "         MercadoPago DESCARTA en silencio las back_urls que apuntan ahi:"
    echo "         responde 201, las guarda vacias, y el comprador paga y se queda"
    echo "         sin boton para volver. La venta nunca se confirma."
  fi
  if ! publica "${MP_NOTIFICACION_URL:-}"; then
    echo ""
    echo "    [!]  MP_NOTIFICACION_URL sin URL publica: MercadoPago no avisara del"
    echo "         cobro y el pedido seguira PENDIENTE hasta que concilie el"
    echo "         barrendero. En desarrollo:  ngrok http 8080"
  fi
fi

echo ""

if [ "$fallos" -gt 0 ]; then
  printf "  Faltan %s valores obligatorios. Edita el .env de la raiz.\n" "$fallos"
  printf "  Genera un JWT_SECRET con:\n"
  echo "    openssl rand -base64 48"
  echo ""
  exit 1
fi

printf "  Entorno correcto. Arrancando servicios...\n"
echo ""
exit 0
