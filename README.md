# NotasApp 📚

App Android nativa para gestionar notas académicas con cálculo automático de promedios ponderados.

---

## Stack Tecnológico

| Capa | Tecnología |
|------|-----------|
| **Lenguaje** | Kotlin |
| **UI** | Jetpack Compose + Material Design 3 |
| **Arquitectura** | MVVM + Clean Architecture |
| **Base de datos** | Room (SQLite local) |
| **Inyección de dependencias** | Hilt |
| **Autenticación** | Google Sign-In (Credential Manager API) |
| **Sincronización** | Google Sheets API v4 + WorkManager |
| **Exportación** | Apache POI (.xlsx) |
| **Navegación** | Navigation Compose |
| **Drag & Drop** | sh.calvin.reorderable |
| **Logging** | Timber |

---

## Arquitectura del Proyecto

```
app/
  src/main/java/com/notasapp/
    ├── data/
    │   ├── local/
    │   │   ├── AppDatabase.kt          # Base de datos Room
    │   │   ├── dao/                    # DAOs: UsuarioDao, MateriaDao, ComponenteDao, SubNotaDao
    │   │   ├── entities/               # Entidades Room
    │   │   └── relations/              # Relaciones de Room (@Embedded + @Relation)
    │   ├── mapper/
    │   │   └── EntityMapper.kt         # Conversión Entity ↔ Domain Model
    │   └── repository/
    │       └── MateriaRepositoryImpl.kt
    ├── domain/
    │   ├── model/                      # Modelos puros de dominio (sin Room)
    │   ├── repository/
    │   │   └── MateriaRepository.kt    # Interfaz del repositorio
    │   └── usecase/                    # Casos de uso (lógica de negocio)
    ├── ui/
    │   ├── auth/                       # Login con Google
    │   ├── home/                       # Lista de materias
    │   ├── materia/
    │   │   ├── create/                 # Wizard de creación (3 pasos)
    │   │   ├── detail/                 # Detalle + ingreso de notas
    │   │   └── edit/                   # Editar porcentajes (drag & drop)
    │   ├── export/                     # Exportar Excel / Sync Sheets
    │   └── theme/                      # Tema Material You
    ├── di/                             # Módulos Hilt
    ├── navigation/                     # NavGraph y Screen sealed class
    └── utils/
        └── ExcelExporter.kt            # Exportación Apache POI
```

---

## Configuración Inicial

### 1. Requisitos

- **Android Studio** Ladybug o superior
- **JDK 17**
- **Android SDK 35** (compileSdk)
- **Min SDK 26** (Android 8.0)

### 2. Google Sign-In

1. Ve a [Google Cloud Console](https://console.cloud.google.com)
2. Crea un proyecto o usa uno existente
3. Habilita la API de Google Sign-In
4. Crea credenciales OAuth 2.0 de tipo **Android**
5. Copia el `client_id` en `app/src/main/res/values/strings.xml`:
   ```xml
   <string name="default_web_client_id">TU_CLIENT_ID.apps.googleusercontent.com</string>
   ```

### 3. Google Sheets API (Fase 3)

1. En Google Cloud Console, habilita la **Google Sheets API v4**
2. Habilita la **Google Drive API**
3. Agrega los scopes de OAuth necesarios

### 4. Compilar y ejecutar

```bash
./gradlew assembleDebug
```

---

## Fases de Desarrollo

### ✅ Fase 1 - MVP
- [x] Estructura del proyecto (MVVM + Clean Architecture)
- [x] Entidades Room + DAOs
- [x] Modelos de dominio
- [x] Repositorio de materias
- [x] Inyección de dependencias (Hilt)
- [x] Navegación (Navigation Compose)
- [x] Login con Google (pantalla)
- [x] Home - Lista de materias
- [x] Wizard de creación de materia (3 pasos)
- [x] Vista detalle con ingreso de notas
- [x] Cálculo de promedio ponderado
- [x] UI con Material Design 3

### ✅ Fase 2 - UX Pulida
- [x] Animaciones Compose (entrada escalonada, animateContentSize, AnimatedText)
- [x] Drag & drop funcional (reorderable con handle, elevación y feedback háptico)
- [x] Calculadora "¿Qué nota necesito?" (BottomSheet integrado en detalle)
- [x] Modo oscuro (soportado vía sistema — Dynamic Color Android 12+)
- [x] Pantalla de estadísticas del semestre
- [x] Componentes reutilizables (ShimmerLoading, PromedioGauge, SwipeToDeleteWrapper, GradeBadges)
- [x] Shimmer skeleton en carga de Home y Detalle

### 📅 Fase 3 - Cloud & Export
- [ ] Exportar a .xlsx (Apache POI - parcialmente implementado)
- [ ] Integración Google Sheets API
- [ ] WorkManager para sync automático
- [ ] Manejo de errores offline-first

### 📅 Fase 4 - Extras
- [ ] Notificaciones recordatorio
- [ ] Widget en pantalla principal
- [ ] Backup/Restore desde Google Drive

---

## Seguridad

- El `client_id` de Google OAuth va en `strings.xml`, **nunca hardcodeado**
- Los tokens de acceso se guardan en `EncryptedSharedPreferences`
- La base de datos Room puede encriptarse con SQLCipher si se necesita
- En releases, Timber no registra logs de debug (ProGuard lo elimina)

---

## Licencia

MIT License - Ver [LICENSE](LICENSE) para más detalles.
