# Cómo contribuir

Gracias por echar una mano con Dezzy. Antes de abrir un cambio, lee estas normas: son obligatorias.

## 1. Código limpio de licencias

Dezzy se publica bajo la licencia PolyForm Strict 1.0.0. Para que esa licencia sea válida, el código tiene que ser propio de principio a fin.

- No se admite ni una línea derivada de código GPL.
- Cuando se quiera igualar el comportamiento de otra app, se observa cómo funciona y se escribe desde cero.
- Las dependencias tienen que ser Apache-2.0, MIT, BSD, ISC o LGPL enlazada dinámicamente.

El detalle está en [`docs/sala-limpia.md`](docs/sala-limpia.md).

## 2. Commits

- Escribe los mensajes en español, cortos y en presente.
- La última línea indica de dónde sale el código:
  - `Origen: nuevo`: escrito desde cero.
  - `Origen: propio (…)`: código anterior del propio autor de Dezzy.
  - `Origen: dezzy-desktop/…`: adaptado desde la app de escritorio.
- Un cambio por commit. No mezcles arreglos con funciones nuevas.

## 3. Cambios visuales

Cualquier cambio de diseño empieza con bocetos: tres variantes (A, B y C). Se elige una antes de implementarla en Compose.

## 4. Seguridad y privacidad

- Ninguna clave ni secreto del proyecto puede ir dentro de la app. Las claves de servicios externos las pone cada usuario.
- No se usan Google Play Services, ML Kit ni SDK de rastreo.

## 5. Publicación

Las releases y las subidas a tiendas las hace únicamente Dezzy. Consulta [`TRADEMARKS.md`](TRADEMARKS.md) sobre el uso del nombre y los iconos.
