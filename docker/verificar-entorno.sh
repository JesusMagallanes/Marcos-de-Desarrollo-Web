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
