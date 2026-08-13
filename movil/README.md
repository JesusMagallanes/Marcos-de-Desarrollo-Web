# SmartZone — aplicación móvil

App en Flutter de la misma tienda. **No tiene backend propio**: consume la misma
API que la web, así que lo que el administrador publica desde el panel aparece
aquí sin tocar nada, y la cuenta es la misma en los dos sitios.

```
movil/lib/
  nucleo/
    config/     entorno.dart (a qué backend apunta) y tema.dart (la paleta)
    api/        cliente HTTP, almacén de tokens y traducción de errores
    modelos/    lo que devuelve el backend, en clases de Dart
    servicios/  sesión, catálogo y carrito
  paginas/      tienda, producto, login, carrito y perfil
  widgets/      piezas compartidas (tarjeta de producto, formato de precios)
```

## Levantarla

Primero el backend, que es de donde salen los datos:

```bash
docker compose --profile neon up -d      # desde la raíz del repositorio
```

Y después la app:

```bash
cd movil
flutter run
```

### A qué backend apunta

Es **lo que más falla al empezar**: `localhost` dentro del móvil no es tu
ordenador, es el propio teléfono. Por eso hay un valor distinto según dónde
corra la app, y lo resuelve solo:

| Dónde corre           | URL que usa            |
| --------------------- | ---------------------- |
| Emulador de Android   | `http://10.0.2.2:8080` |
| Simulador de iOS      | `http://localhost:8080`|

En un **teléfono real** ninguna de las dos vale: hay que darle la IP de tu
ordenador en la wifi, y ambos tienen que estar en la misma red.

```bash
flutter run --dart-define=API_URL=http://192.168.1.40:8080
```

Para saber tu IP: `ipconfig` en Windows, `ifconfig` o `ip a` en Linux y macOS.

Si la app no carga nada, mira el pie de "Mi cuenta": en desarrollo enseña
contra qué backend está hablando.

Siempre se apunta al **gateway (:8080)**, nunca a un servicio suelto: es la
única puerta de entrada y la que enruta a catálogo, usuarios y compras.

## Pruebas

```bash
flutter test                              # todas
flutter test --exclude-tags integracion   # solo las que no necesitan backend
```

Entran **una sola vez** y reutilizan la sesión: el backend limita los intentos
de login a 10 cada 15 minutos por IP, y una prueba que entrara en cada caso
agotaba el cupo a la segunda pasada y empezaba a fallar con 429 fingiendo ser un
error de la app. Si aun así te quedas fuera, reinicia los servicios que llevan
el contador en memoria:

```bash
docker compose restart usuarios web-gateway
```

Las de integración usan el mismo `ClienteApi` y los mismos servicios que la
app, contra el backend de verdad: comprueban que el catálogo llega, que se
entra con una cuenta existente, que la sesión se recupera al reabrir y que el
carrito es el mismo que el de la web. Si la pila no está levantada, se saltan
solas en vez de fallar.

## Decisiones que conviene conocer

**Los tokens van en almacenamiento seguro** (Keystore en Android, Keychain en
iOS), no en SharedPreferences. La web usa `localStorage` porque en un navegador
no hay mucho más; en móvil sí lo hay, y SharedPreferences es un XML en claro
dentro del sandbox.

**El refresco de sesión tiene candado.** Si varias peticiones caducan a la vez,
solo una canjea el token de refresco y las demás esperan a ese intento. Sin eso
se pisan entre ellas y el backend acaba revocando el token bueno.

**Un solo `ClienteApi` para toda la app**, compartido por los servicios. Con uno
por servicio, ese candado no serviría de nada.

**Los nombres de los campos siguen al backend** (`name`, `imageUrl`) aunque el
resto del código esté en español. Renombrarlos obligaría a mantener un
diccionario de equivalencias y a acordarse de él en cada cambio del contrato.

**El carrito vive en el servidor**, atado al `uid` del token, igual que en la
web. Por eso hace falta sesión para verlo, y por eso lo que añadas aquí aparece
allí.

## Qué falta

El **pago** desde la app. El checkout va por la saga de MercadoPago, con
preferencia, redirección y retorno; merece su propia pantalla y su manejo del
regreso a la app. Ahora mismo el botón avisa de que llega en la siguiente
entrega, en lugar de estar ahí sin hacer nada.

También quedan fuera **pedidos**, **valoraciones** y **guías** en pantalla: la
capa de datos ya las alcanza (la prueba de integración lee las guías), pero no
tienen interfaz todavía.
