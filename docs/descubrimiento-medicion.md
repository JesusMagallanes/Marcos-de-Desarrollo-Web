# Medición y evaluación del recomendador (fase 3)

La fase 3 no hace mejor al recomendador. Hace que se pueda saber si lo es.

Hasta aquí los pesos eran números razonados: defendibles, y sin una sola prueba
detrás. Cambiar uno era una corazonada — se movía, algo pasaba o no pasaba, y no
había forma de atribuirlo a nada.

## Lo que faltaba

La auditoría previa encontró tres huecos concretos y uno estructural.

| hueco | consecuencia |
|---|---|
| la impresión no guarda la **razón** | el módulo no sirve como sustituto: el carrusel colaborativo mezcla `CO_VIEWED` y `SIMILAR_SUBJECT` en uno solo |
| no guarda el **score** | no se puede ver si el orden que produjo el ranker se corresponde con lo que la gente eligió |
| no existe **versión de ranking** | dos configuraciones distintas quedaban registradas en la misma cuenta, y la comparación parecía válida sin serlo |
| `purgarAnterioresA` **no lo llamaba nadie** | las impresiones crecían sin límite desde la fase 1 |

Y el estructural, que condiciona todo lo demás: **`perfil_faceta` no tiene
historia**. Es un agregado que se actualiza en sitio, así que no se puede
rebobinar a una fecha pasada. Lo mismo vale para `item_relacion`,
`sujeto_similitud` y `tendencia_item`.

## Qué se registra

`recomendacion_servida` es lo que el backend **decidió** enseñar. Va aparte de
`impresion`, que es lo que el usuario **llegó a ver**, y la separación importa:
si se mezclaran, la fatiga empezaría a castigar productos hasta los que nadie se
desplazó, que es lo contrario de «ya te lo enseñé y lo ignoraste».

De la diferencia entre las dos sale una medida que antes no existía: cuánto de
lo que el sistema propone no llega a mirarse. En un Home largo es enorme.

Cada fila lleva sujeto, ítem, módulo, **razón**, posición, **score**, **versión
del ranker** y si había perfil. Nada personal: el sujeto es el identificador
opaco de siempre y desaparece en cascada.

## La versión del ranker

Etiqueta más huella: `v3.0-4f2a1c`.

La huella es un resumen de los pesos que afectan al orden — los de origen y el
castigo por popularidad. No es un adorno: una etiqueta a mano se olvida de
subir, y entonces dos configuraciones distintas comparten nombre. Eso es peor
que no versionar, porque la comparación posterior parece válida. Con la huella,
cambiar un peso cambia la versión se acuerde alguien o no.

Quedan fuera a propósito la vida media y el techo de permanencia: cambian el
perfil, no la forma de combinarlo, y meterlos haría bailar la versión por
motivos que no explican ninguna diferencia de ranking.

## El agregado diario

`metrica_descubrimiento`: una fila por día, módulo, razón, versión y banda de
posición. Unas decenas al día.

Cuenta servidas, vistas, clics, vistas profundas, carritos y compras. Las tres
últimas son las que convierten un panel de vanidad en una medida: **un clic no
es un éxito**.

La **banda** de posición en vez de la posición exacta porque la diferencia entre
la primera y la segunda tarjeta importa y entre la novena y la décima no.

El CTR se calcula sobre **lo visto**, no sobre lo servido. Dividir por lo
servido mezcla dos preguntas —si la recomendación era buena y si el usuario
llegó a desplazarse— y hunde por igual a un módulo acertado que quede al final
de la página.

### Atribución

Una acción cuenta para una recomendación si ocurrió **después** de servirla y
dentro de `descubrimiento.medicion.horas-atribucion` (24 por defecto).

No es perfecta: alguien pudo llegar al producto por el buscador. Lo que la hace
útil no es acertar, es ser **la misma para todos los módulos**, para que la
comparación entre ellos signifique algo aunque el número absoluto no lo sea.

Se recalculan **dos días**, el de ayer y el de hoy, porque la atribución
necesita tiempo para cerrarse: quien ve una recomendación a las 23:50 y compra a
las 00:30 pertenece al día anterior. Como la agregación es idempotente, repetir
un día no duplica: corrige.

## La evaluación offline

```
ventana de entrenamiento → CORTE → holdout
      construir                     comprobar
```

Se construye el recomendador con lo que se sabía hasta el corte, se le pide su
lista, y se compara contra lo que la gente hizo después.

### La trampa

Lo cómodo sería reutilizar las consultas de producción. Y sería inválido: esas
tablas son de estado actual y **ya contienen el futuro** respecto a cualquier
corte del pasado. Un evaluador que las usara construiría la recomendación con
información que en ese momento no existía y luego se felicitaría por acertar.

Una evaluación con fuga temporal no falla. Sale bien. Enseña unas cifras
estupendas y nadie sospecha, porque para verla hay que buscarla a propósito.

Por eso todo sale de `evento_interaccion`, la única tabla con fecha por fila, y
por eso la mitad de `EvaluacionOfflineIT` son pruebas de que la frontera existe
— en los dos bordes del entrenamiento y en el final del holdout.

### Las métricas

| métrica | qué dice |
|---|---|
| Recall@5, @10 | cuánto de lo que hizo después estaba en la lista |
| NDCG@5, @10 | lo mismo, premiando acertar **arriba**: casi nadie llega a la décima tarjeta |
| cobertura | qué porción del catálogo elegible llega a asomar |
| diversidad | categorías y marcas distintas por lista |
| novedad | cuánto se aleja de los superventas |
| repetición | cuánto de la lista ya había visto — debería ser 0, y se **mide** para que se note si alguien rompe la exclusión |
| arranque en frío | recall separado por cuánta historia tenía el sujeto |
| cobertura colaborativa | qué porción de sujetos llega a recibir evidencia colaborativa |

Se mantienen **separadas**. Un recomendador que maximiza aciertos y solo enseña
diez superventas puntúa muy bien en relevancia y está destruyendo la tienda:
el 95 % del catálogo se vuelve invisible. Meterlo todo en una cifra permite que
ese desastre se esconda detrás de una media.

Existe un `indiceGlobal()` para ordenar candidatas cuando hay veinte —60 %
NDCG@10, 20 % cobertura, 20 % novedad—, documentado aquí y no escondido. Los
porcentajes son una decisión de producto: dicen que se prefiere acertar, pero no
a cualquier precio. Nunca sustituye a mirar las métricas por separado.

### Comparar configuraciones

`EvaluacionOfflineService.comparar` evalúa varias sobre **el mismo corte**. No
es un detalle: sobre ventanas distintas la diferencia podría ser de los datos y
no de los pesos, que es el error que convierte una comparación en una anécdota.

## Rendimiento

Nada de esto corre dentro de `GET /api/descubrimiento/home`. No hay endpoint que
dispare la evaluación. El Home sigue leyendo tablas ya calculadas.

El registro de lo servido sí ocurre en la petición, y por eso **no puede
romperla**: va en transacción propia con la captura fuera del límite
transaccional. Ese detalle costó una vuelta — con el `try` dentro, el fallo se
lanza al confirmar, desde fuera del `try`, y el Home se caía igual.

## Retención

| tabla | política |
|---|---|
| `recomendacion_servida` | `descubrimiento.retencion-dias`, 90 por defecto |
| `impresion` | igual — antes no se purgaba nunca |
| `metrica_descubrimiento` | **no se purga**: unas decenas de filas al día, y es lo que permite comparar con el año pasado |

## Configuración

| propiedad | por defecto | qué hace |
|---|---|---|
| `ranker-etiqueta` | `v3.0` | mitad legible de la versión |
| `retencion-dias` | 90 | cuánto vive el detalle |
| `medicion.horas-atribucion` | 24 | ventana de atribución |
| `medicion.intervalo-ms` | 3 600 000 | cada cuánto se agrega |

## Privacidad

Todo agregado. `metrica_descubrimiento` no contiene ningún identificador, y su
columna `sujetos` está para lo contrario de identificar: permite descartar filas
sostenidas por una sola persona, que no describen un patrón sino a esa persona.
`resumenPorRazon` exige un mínimo de sujetos por ese motivo — con cuatro
personas detrás, un CTR del 50 % significa que dos hicieron clic, y decidir con
eso es peor que no medir.

No hay ningún endpoint que exponga actividad individual, ni lo habrá.

## Métricas técnicas

En `/actuator/prometheus`, etiquetadas solo por conjuntos cerrados y pequeños:

- `smartzone_descubrimiento_servidas_total{razon}`
- `smartzone_descubrimiento_metricas_agregadas`
- más las de la fase 2 (`relaciones_item`, `relaciones_sujeto`,
  `candidatos_total{razon}`, `home_total{perfil,colaborativo}`)

Una métrica etiquetada por producto filtraría conducta y reventaría la
cardinalidad de Prometheus, que es como se tumba un sistema de monitorización
sin querer.
