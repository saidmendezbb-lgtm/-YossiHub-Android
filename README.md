# YOSSI HUB Android v1.0.0

Aplicación Android nativa contenedora de la plataforma oficial **https://yossihub.com**.

## Qué incluye
- Nombre: YOSSI HUB
- Package ID: `com.yossihub.app`
- Android mínimo: 7.0 (API 24)
- Target/Compile SDK: 35
- WebView nativo con JavaScript y almacenamiento web habilitados
- Geolocalización con permiso Android
- Selector de archivos para documentos/fotos
- Enlaces externos (WhatsApp, Google Maps, teléfono, correo) abiertos en sus apps correspondientes
- Navegación Atrás integrada
- Pantalla inicial e icono de YOSSI HUB
- Enlaces `yossihub.com` preparados como App Links

## Cómo generar el APK
1. Abrir esta carpeta en Android Studio.
2. Esperar a que Gradle termine de sincronizar.
3. Seleccionar **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
4. El APK de prueba aparecerá en `app/build/outputs/apk/debug/app-debug.apk`.

## Para Google Play
Antes de publicación hay que:
- crear y guardar una clave de firma (keystore),
- generar un archivo `.aab` de release firmado,
- agregar política de privacidad/términos y condiciones,
- completar ficha de Play Console y declaraciones de datos/permisos,
- configurar `/.well-known/assetlinks.json` si se quiere verificación completa de App Links.

## Arquitectura
La app carga `https://yossihub.com` como fuente principal. Esto permite que las actualizaciones de la plataforma web se reflejen en Android sin rehacer el APK en cada ajuste de contenido/lógica web. Las capacidades nativas pueden ampliarse gradualmente.
