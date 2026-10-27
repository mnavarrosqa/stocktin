# Stocktin

App de Android para seguir acciones, ETFs, índices, monedas y cripto, con un widget para la
pantalla de inicio pensado para ser mejor que el de Yahoo Finance.

**Descargar:** [stocktin.apk (última versión)](https://github.com/mnavarrosqa/stocktin/releases/latest/download/stocktin.apk).
Abrilo desde el teléfono para instalar. Requiere Android 9 o superior.

**Actualizar:** la app busca versiones nuevas al abrirse (como mucho cada 6 horas) y muestra un
aviso con las novedades y el botón **Actualizar**, que descarga el APK con el navegador; al
terminar, se toca la descarga para instalarla encima. La app no pide permiso para instalar apps
por su cuenta: es lo que hace que Play Protect la confunda con apps dañinas.
También se puede buscar a mano en Ajustes → Actualizaciones de la app.

## El widget

Dos widgets, los dos redimensionables y configurables (mantenerlo presionado → Configurar):

- **Lista de cotizaciones:** como el de Yahoo (símbolo, nombre, precio, cambio del día y
  pre-apertura / post-cierre), y además:
  - **Minigráfico del día** en cada fila, con el cierre anterior punteado.
  - **Estado del mercado** (Abierto, Pre-apertura, Post-cierre, Cerrado) y hora de actualización.
  - **5 aspectos:** Automático (sigue el modo oscuro), Claro, Oscuro, Negro (para pantallas OLED)
    y Transparente.
  - Cambio como **texto de color** o **etiqueta de color**, en **porcentaje** o en **dinero**.
  - Cada widget puede mostrar **una lista distinta**.
  - Tocar una fila abre el gráfico de ese símbolo; el botón ↻ actualiza al instante.
  - Si no hay conexión muestra los últimos precios y lo avisa ("Sin conexión · 9:42").
  - **Diseño Mosaico:** una baldosa por símbolo, más verde o más roja cuanto más se movió (como un
    mapa de calor).
  - Se adapta al tamaño: desde una columna con lo justo hasta dos columnas en tablets y foldables
    desplegados; el encabezado se achica a una línea en widgets bajos.
- **Un símbolo:** desde 1×1 (símbolo y cambio) hasta precio grande con gráfico del día; ancho y
  bajo, todo en una fila.

Se actualizan solos cada 15 min, 30 min o 1 h (o solo a mano). Con la bolsa de EE.UU. cerrada
(noches y fines de semana) se puede ahorrar batería actualizando una vez por hora, salvo que la
lista tenga cripto.

## La app

Inspirada en la app Bolsa de Apple y en Robinhood: títulos grandes, números que no "bailen"
(cifras de ancho fijo) y una barra de pestañas flotante abajo (Listas, Mercados, Ajustes) con la
búsqueda al lado.

- **Foldables y tablets:** desplegada usa una barra lateral y muestra la lista y el detalle lado a
  lado, separados justo en la bisagra. Medio plegada en modo mesa, el gráfico queda arriba y los
  datos abajo.
- **Mercados:** índices (S&P 500, Nasdaq, Merval…), cripto, materias primas y monedas en tarjetas
  con su gráfico del día.
- **Varias listas** (por ejemplo "USD", "Argentina", "Cripto"), con pestañas para pasar de una a otra.
- **Búsqueda** por símbolo o nombre, con sugerencias (S&P 500, Nasdaq, Merval, Bitcoin, oro,
  dólar/peso…).
- **Detalle** con gráfico de 1D, 5D, 1M, 6M, YTD, 1A, 5A y Máx: deslizá el dedo sobre el gráfico
  para ver el precio de cada momento (con vibración suave). Debajo, dónde está el precio en el
  rango de 52 semanas, apertura, máximo y mínimo, volumen y capitalización.
- Arriba de las listas, una tira con S&P 500, Nasdaq, Dow y Bitcoin; debajo, cuántos símbolos
  suben y cuántos bajan, con una barra de amplitud.
- **Ordenar** por mayor suba, mayor baja o símbolo, y **deslizar una fila** para quitarla (con
  Deshacer). Los precios destellan en verde o rojo cuando cambian.
- La búsqueda muestra tus búsquedas recientes y lo más buscado del día en Yahoo Finance.
- El detalle trae las últimas **noticias** del símbolo y marca el máximo y el mínimo en el gráfico.
- Con la app abierta los precios se actualizan cada 30 segundos.
- Tocá la etiqueta de color para alternar entre porcentaje y dinero.

## Cómo funciona

- Datos de **Yahoo Finance** (la misma fuente que su app), sin clave ni cuenta. La cotización
  completa (con pre/post) sale de `v7/finance/quote`; si falla, se arma con el gráfico del día
  (`v8/finance/chart`), que también da el minigráfico. Algunas bolsas tienen demora.
- Las listas y las últimas cotizaciones se guardan en archivos de la app (`watchlists.json` y
  `quotes.json`), así los widgets muestran algo aunque no haya conexión.
- Widgets con [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance);
  actualización en segundo plano con WorkManager.

## Versiones

Cada push a `main` compila, corre los tests y publica una release `v0.1.N` con `stocktin.apk`.
Las novedades de la release son los títulos de los PR (o de los commits) desde la anterior, y la
app las muestra en el aviso de actualización.

Todas las versiones se firman con la misma clave para que se instalen encima. La clave no está en
el repo: va en los secrets de GitHub Actions `STOCKTIN_KEYSTORE_BASE64` (el `.p12` en base64) y
`STOCKTIN_KEYSTORE_PASSWORD`. Sin ellos, `main` no publica nada (las demás ramas compilan con la
clave de debug).

## Capturas de pantalla

Cada push a una rama que no sea `main` genera capturas de la app con datos armados (Robolectric +
Roborazzi, `ScreenshotTest`) y las deja en la rama
[`screenshots`](https://github.com/mnavarrosqa/stocktin/tree/screenshots/app/screenshots):
listas, mercados, detalle, búsqueda, ajustes, modo oscuro y foldable desplegado.

## Tests

```sh
./gradlew testReleaseUnitTest
```

Cubren lo que no depende de Android: lectura de las respuestas de Yahoo, pre/post, formatos de
números, horario de la bolsa y guardado de listas y cotizaciones.
