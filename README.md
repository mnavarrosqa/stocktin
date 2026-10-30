# Stocktin

App de Android para seguir acciones, ETFs, índices, monedas y cripto, con un widget para la
pantalla de inicio pensado para ser mejor que el de Yahoo Finance.

**Descargar:** [stocktin.apk (última versión)](https://github.com/mnavarrosqa/stocktin/releases/latest/download/stocktin.apk).
Abrilo desde el teléfono para instalar. Requiere Android 9 o superior.

**Actualizar:** la app busca versiones nuevas al abrirse (como mucho cada 6 horas) y avisa con las
novedades. **Actualizar** la baja (el avance se ve en Ajustes) y Android pregunta "¿Actualizar
Stocktin?": se instala encima sin perder nada. La primera vez Android pide permitir que Stocktin
instale apps. Ese permiso puede hacer que Play Protect marque la app como desconocida o dañina al
instalarla: en ese aviso, "Más detalles → Instalar de todas formas". Después de actualizar muestra
las novedades, y en Ajustes está el historial de versiones.

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
- Aspecto **Material You** (el que viene por defecto): toma los colores del fondo de pantalla, como
  los widgets de Google. En el encabezado, ▲ cuántos suben y ▼ cuántos bajan.
- **Un símbolo:** desde 1×1 (símbolo y cambio) hasta precio grande con gráfico del día; ancho y
  bajo, todo en una fila.

Se actualizan solos cada 15 min, 30 min o 1 h (o solo a mano). Con la bolsa de EE.UU. cerrada
(noches y fines de semana) se puede ahorrar batería actualizando una vez por hora, salvo que la
lista tenga cripto.

## La app

Inspirada en la app Bolsa de Apple y en Robinhood: títulos grandes, números que no "bailen"
(cifras de ancho fijo) y una barra de pestañas flotante abajo (Listas, Mercados, Ajustes) con la
búsqueda al lado.

- **Foldables y tablets:** desplegada muestra la lista y el detalle lado a lado, separados justo en
  la bisagra; en un foldable las pestañas quedan en la barra flotante y en tablets grandes pasan a
  una barra lateral. En paneles angostos las filas sacan el minigráfico para que entre el nombre. Medio plegada en modo mesa, el gráfico queda arriba y los
  datos abajo.
- **Mercados:** si Wall Street está abierto y cuánto falta para que cierre o abra (en tu hora), lo
  que más sube, baja y se opera hoy, y secciones de índices, Argentina (Merval, ADRs y dólar),
  sectores de EE.UU. como mapa de calor, cripto, materias primas y monedas y tasas, con chips para
  saltar a cada una.
- **Ideas para armar listas**: abajo de tu lista, listas armadas para agregar con un toque (las 7
  magníficas, ADRs argentinos, panel líder, IA, cripto, ETFs, dividendos, materias primas y dólar
  y tasas). Tocando una se ve qué trae y cómo viene hoy.
- **Varias listas** (por ejemplo "USD", "Argentina", "Cripto"), con pestañas para pasar de una a otra.
- **Búsqueda** por símbolo o nombre, con sugerencias (S&P 500, Nasdaq, Merval, Bitcoin, oro,
  dólar/peso…).
- **Detalle** con gráfico de 1D, 5D, 1M, 6M, YTD, 1A, 5A y Máx: deslizá el dedo sobre el gráfico
  para ver el precio de cada momento (con vibración suave). Debajo, dónde está el precio en el
  rango de 52 semanas, apertura, máximo y mínimo, volumen y capitalización.
- Arriba de las listas, una tira con S&P 500, Nasdaq, Dow y Bitcoin; debajo, cuántos símbolos
  suben y cuántos bajan, con una barra de amplitud.
- **Arrastrar y soltar** para ordenar: mantené presionado un símbolo y movelo (o usá la manija ≡
  en "Ordenar y quitar símbolos").
- **Ordenar** por mayor suba, mayor baja o símbolo, y **deslizar una fila** para quitarla (con
  Deshacer). Los precios destellan en verde o rojo cuando cambian.
- La búsqueda muestra tus búsquedas recientes y lo más buscado del día en Yahoo Finance.
- **Alertas:** en el detalle, la campana → "Llegue a un precio" (o ±5 % / ±10 %; suena una vez y
  se borra) o "Se mueva mucho" (más de 3, 5 o 10 % en el día; suena una vez por día). Avisan con
  una notificación y se revisan cada vez que se actualizan los precios. En Ajustes están todas.
- **Cartera:** en el detalle, "Tu posición" → cantidad y precio promedio de compra. Arriba de las
  listas aparece cuánto vale todo, cuánto cambió hoy y cuánto ganaste (separado por moneda). El
  Para acciones y ETFs de EE.UU. se pueden cargar también **CEDEARs** (o la acción en pesos de un
  ADR argentino): se valúan con su cotización en Buenos Aires (`AAPL.BA`) y van al total en pesos. El
  ojito oculta los montos y las cantidades (quedan los porcentajes), como en las apps de los bancos.
- **Comparar:** en el gráfico del detalle, "vs S&P 500" (o "vs Merval" para acciones argentinas)
  dibuja el índice en la misma escala y muestra cuánto rindió cada uno en el rango.
- **Agenda** (en Mercados): decisiones de tasas de la Fed, inflación (CPI), informe de empleo y PBI
  de EE.UU., y los balances de las acciones de tus listas y tu cartera. El día de cada evento llega
  un aviso (se apaga en Ajustes → Alertas). Las fechas de EE.UU. están en
  [`calendar.json`](app/src/main/assets/calendar.json): la app lo baja de GitHub, así que para sumar
  fechas alcanza con editar ese archivo en `main`, sin sacar una versión nueva. Las de los balances
  vienen de Yahoo Finance.
- **Bloqueo con huella:** Ajustes → Seguridad → "Bloquear con huella o PIN". Al abrir la app (o si
  estuvo más de un minuto cerrada) pide la huella, la cara o el bloqueo del teléfono, y la vista
  previa en "Recientes" queda tapada.
- **Compartir la app:** Ajustes → Acerca de → Compartir Stocktin manda el link de descarga.
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
listas, mercados, detalle, búsqueda, ajustes, modo oscuro, foldable con bisagra y tablet, y los
widgets en varios tamaños y aspectos (`WidgetScreenshotTest`).

## Tests

```sh
./gradlew testReleaseUnitTest
```

Cubren lo que no depende de Android: lectura de las respuestas de Yahoo, pre/post, formatos de
números, horario de la bolsa y guardado de listas y cotizaciones.
