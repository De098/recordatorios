# Recordatorios

Calendario con tareas, citas, avisos y notas con candado que funciona **entero en el móvil**: sin cuenta, sin servidor y
sin permiso de internet. Android 13 o superior. Licencia GPL-3.0.

- **Calendario**: tareas que avisan hasta «Hecho», citas con avisos previos, repeticiones, días salteados, colores,
  cumpleaños, buscador, importantes en MAYÚSCULAS.
- **Avisos**: alarmas exactas locales, con botón «✔ Hecho»; se reprograman al encender el móvil.
- **Notas** con colores, fijadas y candado (cifrado en el móvil: PBKDF2-HMAC-SHA512 + TweetNaCl secretbox).
- **Widgets**: Próximos, Mes y Apuntar/Nota.

## Cómo está hecha

Kotlin sin dependencias (solo la plataforma Android). La interfaz son dos páginas web empaquetadas en
`app/src/main/assets/web` (`cal.html`, `notas.html`) que se enseñan en un `WebView` sin acceso a la red; sus peticiones
(`/cal/…`, `/notas/…`) las contesta la propia app (`Rutas.kt`).

| Archivo | Qué hace |
|---|---|
| `Nucleo.kt` | datos (un `.json` por cosa en la carpeta privada), repeticiones, ocurrencias, avisos previos |
| `Calendario.kt` | lo que pide el calendario (datos, buscar, guardar, borrar, hecho) y los avisos de las próximas 48 h |
| `Notas.kt` | notas y candado (el cifrado lo hace la página, aquí nunca se descifra) |
| `Rutas.kt` | puente entre las páginas y las clases anteriores |
| `Programador.kt`, `Avisos.kt` | alarmas locales, notificaciones y botón «Hecho» |
| `Widgets.kt` | los tres widgets |

Los datos están en la carpeta privada de la app; no hay copia de seguridad en la nube (`allowBackup=false`).

## Compilar

```
./gradlew assembleDebug      # APK de pruebas
./gradlew assembleRelease    # sin firmar (F-Droid firma con su clave)
```

Hace falta JDK 17 o superior y el SDK de Android 36.

## Lugar del sol

«Cuando se ponga el sol» y «al amanecer» usan por defecto las coordenadas de Madrid (`Frases.lugar`).

## Licencias

Código: GPL-3.0 (`LICENSE`). Tipografía Nunito: SIL OFL 1.1 (`app/src/main/assets/web/nunito-LICENCIA.txt`).
TweetNaCl-js (`nacl-fast.min.js`): dominio público.
