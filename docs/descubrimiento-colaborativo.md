# Descubrimiento colaborativo (fase 2)

La fase 1 solo sabe mirar hacia dentro: de tu historial saca tus categorías, tus
marcas y tus atributos, y te ofrece más de lo mismo. Eso acierta y aburre, y hay
una clase entera de recomendación que no puede dar por construcción — que quien
compra *ese* monitor acaba necesitando *ese* brazo articulado. Esa relación no
está en la ficha de ninguno de los dos: está en la conducta de la gente.

La fase 2 añade dos capas que sí la ven. Ninguna usa aprendizaje automático:
son dos agregaciones deterministas que se pueden leer, discutir y reproducir a
mano.

## Las dos capas

### Ítem ↔ ítem, por co-interacción

Para cada par de productos se cuenta cuántos **sujetos distintos** interactuaron
con los dos dentro de la ventana, y se normaliza:

```
score(A,B) = |S(A) ∩ S(B)| / √(|S(A)| · |S(B)|)
```

El denominador es lo importante. Con el recuento crudo, el superventas de la
tienda sería «parecido» a todo el catálogo, porque coincide con todo el mundo en
algo; y al recomendarse más, se vería más, y subiría más. Dividir por la raíz de
las dos popularidades pregunta otra cosa: no cuánta gente los vio juntos, sino
qué **proporción** de quienes vieron uno vieron también el otro. Un accesorio de
nicho que mira el 80 % de quienes miran cierta impresora gana a un superventas
que comparte público con todo.

### Sujeto ↔ sujeto, por parecido de perfil

Coseno entre los vectores de `perfil_faceta` que la fase 1 ya calculaba. No hay
representación nueva: el perfil **ya era** un vector ponderado de categorías,
marcas y atributos.

El coseno mide el ángulo y no la magnitud, que es exactamente la propiedad que
interesa: alguien que lleva dos años en la tienda y alguien que llegó el martes
pueden tener el mismo gusto con escalas muy distintas.

## Por qué esto no es O(N²)

Es la pregunta que hay que hacerle a cualquier sistema colaborativo, y tiene tres
respuestas, una por cada sitio donde podría explotar.

| dónde | qué lo acota |
|---|---|
| co-visita | el autojoin es cuadrático en los ítems de **cada** sujeto, no en el total. `colaborativo-tope-items-por-sujeto` pone el techo: con 200, el peor sujeto imaginable aporta 40 000 pares y no cuatro millones. |
| parecido entre perfiles | el cruce va **por faceta compartida**: dos sujetos que no coinciden en nada no llegan a encontrarse. El peligro es la faceta que tiene todo el mundo, y para eso está `similitud-tope-sujetos-por-faceta`. |
| el Home | no calcula nada. Lee las tablas derivadas por índice: unas pocas semillas × un recorrido de `idx_item_relacion_desde`. |

Descartar las facetas demasiado comunes es la misma idea que hay detrás de IDF, y
además es correcta desde el punto de vista de la recomendación: saber que a dos
personas les interesa la tecnología, en una tienda de tecnología, no distingue a
nadie.

## El proceso por lotes

`ColaborativoService.programado()`, cada hora por defecto. Reconstruye las dos
tablas y borra lo que no volvió a escribir.

Es **idempotente**: las dos consultas terminan en `ON CONFLICT DO UPDATE`, así
que reescriben en vez de duplicar. Y la purga posterior es lo que hace que una
relación pueda **morir**: sin ella, dos productos que se vieron juntos hace un
año seguirían recomendándose para siempre y el sistema sería un archivo en vez
de un reflejo de lo que pasa ahora.

## Qué eventos cuentan

Los que cuestan algo: `ITEM_VIEW`, `ITEM_VIEW_DEEP`, `ADD_TO_CART`, `FAVORITE`,
`PURCHASE`.

Las **impresiones no**. Una impresión dice que el sistema enseñó algo, no que a
nadie le interesara; darles peso colaborativo cerraría el círculo por el que el
recomendador se recomienda a sí mismo. Tampoco las búsquedas ni los descartes.
Los negativos se usan para excluir, nunca como evidencia positiva.

Y un mismo sujeto cuenta **una vez** por producto y ventana. Sin eso, alguien
que refresca una ficha veinte veces pesaría como veinte personas distintas, que
es el fraude más barato que existe contra un recomendador y también el accidente
más común.

## Privacidad

Tres reglas, y las tres son estructurales, no promesas.

1. **`sujeto_similitud` no sale del backend.** No hay endpoint que la exponga ni
   lo habrá: «gente parecida a ti» es un dato sobre terceros que ellos no han
   ofrecido. Lo que llega al navegador son unos productos, jamás de quién
   salieron.
2. **Piso de aportantes.** Un producto solo se ofrece en el módulo de perfiles
   parecidos si lo tocaron al menos `similitud-min-aportantes` vecinos
   distintos. Con uno solo, «otras personas descubrieron» significaría «una
   persona concreta miró». Con la tienda recién abierta esto devuelve vacío, y
   está bien que así sea.
3. **Borrado en cascada.** Las claves ajenas van con `ON DELETE CASCADE` en los
   dos lados: al borrar un sujeto desaparecen las aristas que lo mencionan sin
   que nadie tenga que acordarse.

El mínimo de 50 sujetos de las tendencias geográficas sigue donde estaba y
protege lo que protegía: un dato de zona. Es un problema distinto y no se mezcla
— la capa colaborativa no usa geografía.

## Los textos

Los títulos son deliberadamente sobrios: «Suele mirarse junto con esto», «Otras
personas descubrieron». La fórmula habitual —«usuarios como tú compraron»— es
mala por dos motivos: promete una precisión que la evidencia no sostiene, y le
dice a alguien que el sistema lo ha agrupado con otras personas, que es
exactamente la sensación que hace que una tienda parezca que vigila.

De dónde salió cada recomendación se guarda dentro, en `RazonRecomendacion`, y
sirve para depurar y para medir qué generador acierta.

## El ranker híbrido

No sustituye al ranking existente: cada carrusel sigue ordenándose por su
criterio. `RankerHibrido` entra cuando hay que **mezclar**, que es lo que la fase
1 no necesitaba hacer nunca.

Su trabajo empieza por normalizar cada grupo por su propio máximo, y el orden
importa. Cada generador puntúa en unidades suyas —el perfil suma scores que
llegan a 40, el colaborativo suma cosenos que rara vez pasan de 3—; sumarlos tal
cual no sería combinar señales, sería dejar que gane el de las unidades más
grandes. Después aplica el peso del origen, después castiga la sobreexposición, y
al final ordena.

Un producto que llega por varios caminos se queda con **el mejor** de sus scores,
no con la suma: sumar premiaría estar en todas partes, que es justo el sesgo que
este ranker existe para frenar.

## Configuración

Todo bajo el prefijo `descubrimiento.` en `application.properties` o por variable
de entorno. Los valores por defecto son un punto de partida razonado, no una
verdad.

| propiedad | por defecto | qué hace |
|---|---|---|
| `colaborativo-ventana-dias` | 30 | ventana de conducta que alimenta el cálculo |
| `colaborativo-min-soporte` | 3 | sujetos distintos que sostienen una relación |
| `colaborativo-tope-items-por-sujeto` | 200 | techo de lo que aporta un sujeto |
| `similitud-min-compartidas` | 3 | facetas que hay que compartir para parecerse |
| `similitud-tope-sujetos-por-faceta` | 500 | cuándo una faceta deja de distinguir |
| `similitud-minima` | 0,15 | parecido por debajo del cual un vecino no aporta |
| `similitud-vecinos-consultados` | 25 | vecinos que se miran al generar candidatos |
| `similitud-min-aportantes` | 3 | piso de privacidad del módulo de vecinos |
| `peso-origen.*` | ver abajo | cuánto se cree a cada fuente |
| `penalizacion-popularidad` | 0,35 | cuánto se castiga la sobreexposición |
| `maximo-modulos-colaborativos` | 1 | tope de carruseles colaborativos en el Home |
| `colaborativo.intervalo-ms` | 3 600 000 | cada cuánto corre el proceso |

Pesos de origen por defecto: `PERSONAL 1,0` · `COHORTE 0,8` · `GEO 0,5` ·
`TENDENCIA 0,4` · `EXPLORACION 0,3`. Lo que importa es la proporción, porque el
score se normaliza antes de aplicarlos. El orden dice qué se cree más: lo que
esta persona ya demostró que le interesa, después lo que hace gente con su mismo
gusto, y al final lo que solo es popular.

## Lo que sigue mandando

El colaborativo no puede saltarse nada de la fase 1. Un `NOT_INTERESTED` gana
siempre, por abrumadora que sea la evidencia: volver a enseñar lo que alguien
descartó convierte el botón en un adorno. La fatiga sigue apagando lo que ya se
enseñó sin éxito. Y un producto aparece **una vez** en todo el Home.

Está probado, no supuesto: ver `RecomendacionColaborativaIT`.

## Métricas

En `/actuator/prometheus`, sin un solo identificador:

- `smartzone_descubrimiento_relaciones_item` — relaciones vivas tras el lote
- `smartzone_descubrimiento_relaciones_sujeto` — aristas de parecido vivas
- `smartzone_descubrimiento_candidatos_total{razon}` — candidatos por generador
- `smartzone_descubrimiento_home_total{perfil,colaborativo}` — qué evidencia
  había al servir cada Home; la proporción sin perfil es la tasa de arranque en
  frío

Etiquetas de conjunto cerrado y pequeño a propósito. Una métrica etiquetada por
producto, además de filtrar conducta, revienta la cardinalidad de Prometheus.
