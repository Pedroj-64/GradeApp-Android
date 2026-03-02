# Gradify Backend

Proxy ligero que oculta la API key de Gemini del APK.

## Configuración

1. Crear variable de entorno o agregar en `application.yml`:
   ```
   GEMINI_API_KEY=tu_clave_de_gemini
   ```

2. Construir:
   ```bash
   ./gradlew bootJar
   ```

3. Ejecutar:
   ```bash
   java -jar build/libs/gradify-backend-1.0.0.jar
   ```

4. Docker:
   ```bash
   docker build -t gradify-backend .
   docker run -p 8080:8080 -e GEMINI_API_KEY=tu_clave gradify-backend
   ```

## Endpoint

```
POST /api/v1/recomendaciones
Content-Type: application/json

{
  "nombreMateria": "Cálculo Diferencial",
  "periodo": "2025-1",
  "promedio": 3.5,
  "notaAprobacion": 3.0,
  "aprobado": true
}
```

## Configuración Android

En `local.properties` del proyecto Android:
```
BACKEND_URL=https://tu-servidor.com
```

La app intentará el backend primero. Si falla, usa el SDK directo como fallback.
