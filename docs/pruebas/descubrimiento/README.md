# Recorridos de descubrimiento con cuatro visitantes

La validación final del recomendador: cuatro visitantes distintos navegan la
tienda de verdad —Chromium, gateway, servicios y PostgreSQL— y se comprueban
las garantías que cerraron las fases A–I.

```
python docs/pruebas/descubrimiento/ejecutar.py          una pasada
python docs/pruebas/descubrimiento/ejecutar.py --dos    dos pasadas desde cero
```

## Recorridos de búsqueda y categoría

El mismo aislamiento (proyecto Compose `smartzone-e2e`, base vacía, `down -v` al
terminar), otro flujo: comprobar que la **búsqueda y la navegación por categoría
filtran, ordenan y paginan en el SERVIDOR**, no en el navegador.

```
python docs/pruebas/descubrimiento/ejecutar_busqueda.py
```

Dos recorridos con Chromium propio:

1. Inicio → buscar → filtrar por marca → ordenar por precio → paginar → ficha → carrito.
2. Categoría → filtrar por atributo → ordenar → paginar → ficha.

Se comprueban invariantes, no listas: el total es el del conjunto entero (no el
de la página), el filtro y el orden viajan en la petición (`marcaId`,
`atributo`, `orden`, `page`), lo ordenado llega ya ordenado de la base, y los
filtros quedan en la URL para poder compartirla. El paso de carrito inicia
sesión con el administrador que ya siembra la pila; no hace falta pago real.

Cada pasada levanta la pila con el proyecto Compose `smartzone-e2e`, volúmenes
propios y una base **vacía**, recorre, y la destruye con `down -v`. Por eso una
pasada no puede depender de restos de la anterior; `--dos` lo demuestra.

## Requisitos

Docker, Python 3 y Google Chrome en su ruta habitual de Windows. **No se
instala nada más.** El navegador se conduce por el protocolo DevTools con un
cliente de WebSocket escrito sobre la librería estándar (`cdp.py`); ni
Playwright, ni Puppeteer, ni una dependencia nueva en el repositorio.

## Aislamiento

`.env.e2e` apunta a `jdbc:postgresql://postgres:5432/smartzone` —el contenedor—
y usa puertos propios para no chocar con una pila de desarrollo. Antes de
arrancar, `ejecutar.py` lee `docker compose config` y **aborta** si el
datasource no es el aislado. Nunca Neon. Todos los valores del fichero son de
usar y tirar y solo valen dentro de esa pila.

## Chrome

Cada visitante abre **su propio** Chromium, con perfil de datos y puerto de
depuración propios, y lo cierra al terminar pidiéndoselo por el protocolo
(`Browser.close`). Si no responde, se termina el proceso que él mismo lanzó,
por su PID. Nunca se mata Chrome por nombre de imagen: el navegador personal
de quien ejecuta esto no se toca.

## Los visitantes

| | quién | qué comprueba |
|---|---|---|
| V1 | anónimo sin rastro | arranque en frío (B), elegibilidad (A), impresiones con evidencia (H), firma (H) |
| V2 | anónimo con sesión activa | intención de sesión (C), «no me interesa» desde la tarjeta, descarte como señal negativa |
| V3 | con perfil e historial preparado | módulo personal, razones, cooldown hasta el corte (D), exclusión dura, elegibilidad |
| V4 | se registra, entra, fusiona y sale | fusión legítima, el anónimo fusionado no es credencial, sujeto nuevo al salir (H) |

Y dos comprobaciones transversales: los sujetos de los cuatro son distintos y
no se cruzan, y Prometheus —con rol de administrador— no expone ningún
identificador.

Se comprueban **invariantes**, no listas exactas de productos: el algoritmo
es determinista pero depende del catálogo, y fijar un orden rompería la prueba
con cualquier alta de producto sin que nada se hubiera roto de verdad.

## Datos de apoyo

`visitantes.py` crea dos categorías propias (`e2e-monitores`, `e2e-impresoras`)
con productos elegibles y uno agotado, y prepara el historial de V3 y el
perfil de contraste de V2 **para el sujeto que el servidor firmó**, dentro de
la base aislada. No se fabrica ninguna firma ni se toca otro sujeto.

## Qué hacer si falla

La salida marca cada comprobación con `OK` o `FALLA` y el visitante al que
pertenece. Los logs de la pila se leen con `docker compose -p smartzone-e2e
logs catalogo` **antes** de que `ejecutar.py` la derribe; para mirarlos con
calma, lanza `visitantes.py` a mano con la pila ya levantada.
