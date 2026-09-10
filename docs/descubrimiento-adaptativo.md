# Descubrimiento adaptativo (fase 4)

La fase 3 dejó el sistema medible. Lo que no resolvió es que **había un solo
juego de pesos para todo**: la misma mezcla en el Home y en la ficha, y la misma
para quien lleva meses usando la tienda y para quien acaba de llegar.

Eso es una simplificación que se nota. Quien abre la ficha de un monitor está
haciendo una pregunta concreta —«¿y con esto qué va?»— y quien abre el Home no
está preguntando nada. A quien no se conoce, la señal personal solo le reparte
ceros.

## Diseño

```
candidatos (ya filtrados)
        ↓
características explícitas
        ↓
pesos del contexto  ·  variante del sujeto
        ↓
freno por exposición
        ↓
exploración con presupuesto
        ↓
diversificación
        ↓
lista final
```

**Los filtros duros no están en este dibujo porque van antes.** Los descartes y
la fatiga se aplican dentro del SQL que trae los candidatos, así que cuando la
lista llega aquí ya no contiene nada excluido. El ranker adaptativo **solo
reordena lo que recibe** y no añade nada de ninguna parte — ni al explorar. Esa
es la propiedad que hace imposible que una mejora estadística resucite un
`NOT_INTERESTED`.

## Características

Ninguna necesita una consulta por candidato. Las de origen las trae el propio
generador; la exposición sale del recuento por lotes que ya existía.

| característica | de dónde sale |
|---|---|
| `scorePersonal` | generador de intereses, normalizado por su máximo |
| `scoreColaborativo` | co-visita y perfiles parecidos, normalizado |
| `scoreTendencia` | tendencia y zona, normalizado |
| `exposicionReciente` | impresiones de los últimos 7 días, en una consulta por lote |
| `novedad` | 1 − exposición relativa dentro de la tanda |
| `posicionOriginal` | el orden antes de aplicar nada |
| `origen` · `razon` | qué familia y qué generador concreto lo propuso |

La normalización por familia es lo que hace que los pesos signifiquen lo que
parece: sin ella gana el generador con las unidades más grandes, que no tiene
por qué ser el mejor.

## El ranker

```
señal = Σ pesoDe(contexto, origen) × scoreDe(origen)
final = señal × 1/(1 + k·ln(1 + exposición))
```

Lineal a propósito. Una suma se puede leer, explicar y reproducir a mano cuando
algo sale raro; un modelo que acertara un poco más y no se pudiera depurar sería
mal negocio en un sistema del que hay que responder.

El freno por exposición va **fuera** de la suma porque no es una señal más: es
un freno. Sumándolo, subir un peso positivo podría anularlo sin que nadie lo
viera.

### Los cuatro contextos

| contexto | qué manda | por qué |
|---|---|---|
| `HOME_CON_PERFIL` | PERSONAL 1,0 · COHORTE 0,9 | es donde el descubrimiento tiene sitio |
| `HOME_SIN_PERFIL` | TENDENCIA 1,0 · GEO 0,8 · PERSONAL 0,2 | lo personal reparte ceros |
| `FICHA_CON_PERFIL` | COHORTE 1,0 · PERSONAL 0,8 | la pregunta la hace el producto |
| `FICHA_SIN_PERFIL` | COHORTE 1,0 · PERSONAL 0,7 | igual, y con menos que saber |

**En la ficha la conducta ajena vale más que el gusto propio.** Es la diferencia
de fondo con el Home: quien mira una impresora no quiere que le ofrezcan
monitores porque los mire mucho; quiere el tóner. Y eso solo lo sabe la
co-visita. Zona y tendencia se hunden ahí por el mismo motivo: que algo se lleve
en Ica no responde nada sobre la impresora que hay en pantalla.

Cuatro contextos y no más. Multiplicarlos lleva a un sistema que nadie puede
calibrar porque nunca hay datos suficientes para ninguna casilla.

## Versionado

`v4.0-1f26e7`. Etiqueta más huella, igual que en la fase 3 y por lo mismo: una
etiqueta a mano se olvida de subir y dos configuraciones acaban compartiendo
nombre.

Entra en la huella todo lo que cambia el resultado: los pesos de cada contexto,
los de la variante, el freno de exposición y los presupuestos de exploración. Se
ordenan antes de resumir para que la huella no dependa del orden de iteración de
un mapa — si dependiera, la versión cambiaría al reiniciar y la comparación se
rompería sin que nadie entendiera por qué.

Lo que se graba en `recomendacion_servida.score` sigue siendo el score **final**,
el que produjo el orden. Con el adaptativo encendido se graba su versión, no la
de la fase 3: atribuir meses de datos a una configuración que no los produjo es
exactamente el fallo que el versionado existe para evitar.

## Experimentación

Dos brazos: `CONTROL` y `VARIANTE`. Dos, y no veinte, porque con veinte no hay
muestra para ninguno.

**La asignación se calcula, no se guarda.** Es una función determinista del
identificador del sujeto y del nombre del experimento. Eso da la estabilidad que
un experimento necesita —la misma persona ve siempre la misma variante— sin una
tabla que mantener, sin una consulta por petición, sin un dato más que borrar
cuando alguien ejerce su derecho al olvido, y sin una fila que relacione a una
persona con un tratamiento.

Y trae de regalo el comportamiento correcto al cerrar sesión: el navegador
suelta su identificador y estrena otro, así que el siguiente visitante recibe la
variante que le toque a él. No hay que acordarse de limpiar nada porque no hay
nada que limpiar.

El nombre del experimento entra en el hash para que el siguiente reparta
distinto. Sin eso, los mismos sujetos caerían siempre del mismo lado y un sesgo
de ese grupo contaminaría todos los experimentos seguidos.

Sin sujeto se sirve el control: no hay a quién atribuir el resultado, así que
meterlo en el experimento solo ensuciaría la medida.

## Exploración

| perfil | presupuesto |
|---|---|
| sin historial | 40 % |
| poco historial | 25 % |
| historial fuerte | 10 % |

Varía con lo que el sistema sabe: a quien no conoce, explorar es lo único que
puede hacer; a quien conoce bien, explorar cuesta y se hace con medida. Nunca
llega al 100 %: un carrusel entero de apuestas no es descubrimiento, es ruido.

Los candidatos de exploración salen de la **misma lista**, elegidos por novedad
entre los que no entraron por score. No son aleatorios: siguen siendo candidatos
legítimos que pasaron todos los filtros. La mitad de arriba no se toca — la
primera tarjeta es la que más se mira y no es sitio para una apuesta.

## Guardarraíles

Una variante no se promueve por ganar en una métrica, sino por **no perder en
ninguna que importe**.

| límite | por defecto |
|---|---|
| muestra mínima por brazo | 200 sujetos |
| mejora mínima en NDCG@10 | 0,01 |
| caída máxima en cobertura | 10 % |
| caída máxima en diversidad | 10 % |
| caída máxima en novedad | 10 % |
| repetición máxima | 0,05 |

Optimizar clics es la forma más rápida de estropear una tienda, y no es una
opinión: la lista que más clics recibe es la de los diez superventas, porque son
lo que la gente ya conocía y habría encontrado sola. Un sistema que persiga esa
cifra acabará enseñando el 5 % del catálogo mientras el panel muestra una curva
que sube.

Y por encima de todo está la muestra. **`INCONCLUSIVO` no es `NO_PROMOVER`**: el
primero dice que hay que seguir midiendo, el segundo que la variante es peor.
Confundirlos hace que se descarten buenas ideas por falta de datos.

## Búsqueda offline de calibración

```
ENTRENAMIENTO → VALIDACIÓN → PRUEBA
  construir       elegir       medir
```

Tres ventanas y no dos. Si se prueban cuatro configuraciones sobre una ventana y
se elige la mejor, ese resultado ya no es una medida: es el máximo de cuatro
intentos sobre los mismos datos, y saldrá optimista aunque las cuatro fueran
igual de buenas. Se elige mirando validación y se informa mirando prueba, que no
ha intervenido en la elección.

Cuatro candidatas, cada una con un argumento detrás: la base, más peso
colaborativo, más exploración, y más castigo a lo popular. Con búsqueda masiva
sobre el tráfico que hay hoy se encontraría el ruido de la ventana, no una
calibración mejor.

## Rollback

`descubrimiento.adaptativo.activo=false`. Reiniciar, no desplegar.

Con eso el sistema vuelve al ranker determinista de la fase 3 exactamente como
estaba: `PesosDescubrimiento` sigue intacto y sigue sirviendo. Por eso la
configuración de esta fase vive en una clase aparte y no dentro de la anterior.

**Y ante cualquier fallo cae solo.** Una calibración mal escrita, un contexto
inesperado o cualquier otra sorpresa dejan la lista en manos del ranker estable;
el visitante ve su carrusel y queda constancia en el log y en el contador
`smartzone_descubrimiento_caida_estable_total`. Ese contador es el que hay que
vigilar: si sube, la tienda funciona y las métricas de la variante no significan
nada.

## Privacidad

No se almacena nada nuevo. La variante no se guarda —se calcula—, así que no
existe ninguna fila que relacione a una persona con un tratamiento. Las métricas
llevan etiquetas de conjunto cerrado y pequeño: variante y contexto. Ningún
identificador.

No hay endpoint que exponga pesos, scores internos, variantes ni actividad de
sujetos, y no lo habrá.

## Rendimiento

En la petición solo ocurre: características → ordenar → diversificar →
responder. Una consulta por lote para la exposición, sobre los identificadores ya
recortados. Ni entrenamiento, ni evaluación, ni búsqueda de configuraciones, ni
N+1.

## Límites actuales

- La atribución sigue siendo por ventana temporal: sirve para comparar módulos y
  variantes entre sí, no como cifra absoluta de causalidad.
- No hay significancia estadística. Los guardarraíles usan umbrales y muestra
  mínima, que es honesto y sencillo; presentarlo como una prueba estadística
  sería falsa precisión.
- La calibración de partida es una hipótesis razonada. Lo que esta fase aporta no
  es que sea la correcta, sino que por primera vez se puede comprobar si lo es.
