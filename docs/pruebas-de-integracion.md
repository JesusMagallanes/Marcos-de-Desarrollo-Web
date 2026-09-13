# Pruebas de integración

## Cómo se ejecutan

```
mvn test      # solo las unitarias (*Test). No necesita Docker.
mvn verify    # añade las de integración (*IT).
```

Cada servicio arranca **un** PostgreSQL en contenedor para toda la ejecución,
le aplica sus migraciones de Flyway y prueba contra él. El gateway es la
excepción: no tiene base de datos, así que sus `*IT` levantan la aplicación
entera en un puerto y le hablan por HTTP.

| servicio | unitarias | integración | qué cubre la integración |
|---|---|---|---|
| catalogo | 178 | 270 | descubrimiento (aislamiento de sujetos, cooldown, sesión, purga, procesos programados), consultas del panel de descuentos, galería con JOIN FETCH, y **búsqueda/categoría con filtros, orden, paginación y facetas** (`BusquedaFiltradaIT`) |
| usuarios | 106 | 4 | la cadena Flyway → esquema → entidades, con `ddl-auto=validate` |
| compras | 100 | 16 | saga de checkout, métodos de pago, envíos |
| web-gateway | 52 | 2 | la cadena de filtros montada de verdad: cupos y cabeceras |

## Sin Docker

En un portátil sin Docker las `*IT` **se saltan** y el build pasa: no todo el
mundo del equipo lo tiene instalado y un build roto por eso solo enseña a
ignorarlo.

En CI es al revés. Cuando la variable `CI` está puesta —GitHub Actions y los
demás la ponen solos— el salto no está permitido: si Docker no responde,
`Docker#disponible` lanza una excepción y la ejecución se cae diciendo por qué.
Además, el flujo de trabajo cuenta las pruebas de los informes de Failsafe y
falla si hay cero ejecutadas o alguna saltada. Un verde sin pruebas es peor que
un rojo, porque nadie mira un verde.

## Docker Engine 29 y Testcontainers 1.21.3

**Síntoma.** Con Docker Desktop 29.5 (Engine 29.5.2) las `*IT` se saltaban
todas, en verde. En el log, Testcontainers decía:

```
Could not find a valid Docker environment
  NpipeSocketClientProviderStrategy: failed with exception
  BadRequestException (Status 400: {"ID":"","Containers":0, ...})
```

**Causa.** No era la dirección del socket ni el pipe, que responden bien. Es la
versión de la API:

| ruta | respuesta del motor |
|---|---|
| `/v1.32/info` | **400 Bad Request** ← la que pide docker-java |
| `/v1.41/info`, `/v1.44/info` | 200 OK |
| `/v1.55/info` | 400 (aún no existe) |

Docker Engine 29 retiró el soporte de las APIs anteriores a la **1.40**
(`docker version` lo dice: `MinAPIVersion 1.40`). docker-java pide la 1.32 y el
motor la rechaza; Testcontainers interpreta ese 400 como «aquí no hay Docker».

**Por qué no se arregla subiendo docker-java.** Testcontainers 1.21.3 lleva su
propia copia SOMBREADA de `docker-java-core`
(`org.testcontainers.shaded.com.github.dockerjava.core`), así que sustituir la
dependencia externa no cambia nada. Comprobado con la 3.7.1: mismo 400.

**Solución.** Fijar la versión de la API que hablan las pruebas, en el `pom` de
cada servicio con Testcontainers:

```xml
<docker.api.version>1.41</docker.api.version>
```

y pasarla al JVM de Surefire y Failsafe como propiedad del sistema
`api.version`, que es de donde la lee esa copia sombreada.

**Por qué 1.41.** La copia sombreada solo conoce hasta la 1.44, y el motor exige
1.40 como mínimo: la ventana es 1.40–1.44. La 1.41 cae dentro y además sigue
valiendo en motores tan antiguos como Docker 20.10, que es lo que puede haber
en cualquier máquina del equipo o en un runner.

**Alternativas descartadas.**

- *Testcontainers 1.21.4*: lleva la misma copia sombreada de docker-java 3.4.2.
  No cambia nada.
- *Testcontainers 2.x*: sí lo corrige (docker-java 3.7.1), pero reorganizó los
  módulos —`org.testcontainers:postgresql` y `junit-jupiter` ya no están en el
  BOM con esas coordenadas— y es un cambio de otro tamaño. Cuando toque
  hacerlo, esta propiedad se puede quitar.
