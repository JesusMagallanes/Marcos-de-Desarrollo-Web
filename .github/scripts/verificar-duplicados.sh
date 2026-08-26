#!/usr/bin/env bash
#
# Vigila la infraestructura que está copiada en varios servicios.
#
# Los cuatro servicios repiten diecisiete clases transversales —rate limit,
# contexto de RLS, cabeceras, saneado de entrada…— porque no hay un módulo común
# y cada uno se compila y despliega por su cuenta. Eso funciona hasta que alguien
# arregla algo en una copia y no en las otras, y entonces el fallo sigue vivo
# donde nadie mira. Ha pasado dos veces:
#
#   · El cupo propio para el webhook de la pasarela se puso en el gateway y no en
#     `compras`, así que el límite efectivo seguía siendo el viejo.
#   · El tope de claves del limitador estaba en tres servicios y faltaba justo en
#     `usuarios`, que es el del login y el que más recibe.
#
# Este script no pretende sustituir a un módulo común: pretende que la próxima
# vez se vea en el CI y no en producción.
#
# Dos comprobaciones:
#   1. Las clases que HOY son idénticas tienen que seguir siéndolo.
#   2. Ciertas invariantes de seguridad tienen que estar en TODAS las copias,
#      aunque el resto del fichero difiera.

set -uo pipefail

SERVICIOS=(usuarios catalogo compras web-gateway)
fallos=0

# El `package` y los `import` difieren siempre —cada servicio tiene el suyo— así
# que se comparan sin ellos.
sin_cabecera() {
    grep -v '^package\|^import' "$1"
}

localizar() {
    find "${SERVICIOS[@]}" -name "$1.java" -path '*/main/*' 2>/dev/null | sort
}

echo "── Clases que deben permanecer idénticas ──"
for clase in IpCliente CorsConfig; do
    mapfile -t copias < <(localizar "$clase")
    if [ "${#copias[@]}" -lt 2 ]; then
        echo "  [?] $clase: solo ${#copias[@]} copia(s); nada que comparar"
        continue
    fi

    base="${copias[0]}"
    diferentes=""
    for copia in "${copias[@]}"; do
        if ! diff -q <(sin_cabecera "$base") <(sin_cabecera "$copia") >/dev/null; then
            diferentes="$diferentes\n        $copia"
        fi
    done

    if [ -n "$diferentes" ]; then
        echo "  [X] $clase ha divergido respecto a $base:"
        printf "%b\n" "$diferentes"
        echo "      Si la diferencia es deliberada, sácala de esta lista y explica por qué."
        fallos=$((fallos + 1))
    else
        echo "  [OK] $clase — ${#copias[@]} copias idénticas"
    fi
done

echo
echo "── Invariantes que deben estar en todas las copias ──"

# clase|texto que debe aparecer|por qué importa
INVARIANTES=(
    "LimitadorPeticiones|MAXIMO_CLAVES|sin tope de claves, el mapa crece sin freno y el servicio se queda sin memoria"
)

for entrada in "${INVARIANTES[@]}"; do
    IFS='|' read -r clase texto motivo <<< "$entrada"
    mapfile -t copias < <(localizar "$clase")

    faltan=""
    for copia in "${copias[@]}"; do
        grep -q "$texto" "$copia" || faltan="$faltan\n        $copia"
    done

    if [ -n "$faltan" ]; then
        echo "  [X] $clase: falta '$texto' en"
        printf "%b\n" "$faltan"
        echo "      Motivo: $motivo"
        fallos=$((fallos + 1))
    else
        echo "  [OK] $clase — '$texto' presente en las ${#copias[@]} copias"
    fi
done

echo
if [ "$fallos" -gt 0 ]; then
    echo "$fallos comprobación(es) fallida(s): hay una copia que se quedó atrás."
    exit 1
fi

echo "Las copias están al día."
