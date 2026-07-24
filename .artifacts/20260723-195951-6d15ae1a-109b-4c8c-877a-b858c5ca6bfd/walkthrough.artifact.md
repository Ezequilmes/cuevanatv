# Walkthrough: Sistema de Usuarios y Pagos Optimizado

Se han realizado las correcciones solicitadas para estabilizar la autenticación y la integración con MercadoPago en CuevanaTV.

## Cambios Realizados

### 1. Trigger de Base de Datos (Supabase)
Se creó el archivo [setup_auth_trigger.sql](file:///D:/magik/Mi%20app/Cuevanatv/supabase/setup_auth_trigger.sql).
- **Función `on_auth_user_created`**: Inserta automáticamente un registro en `app_users` cada vez que alguien se registra.
- **Trial de 3 días**: Configura por defecto `active = true` y calcula la `fecha_vencimiento`.
- **Seguridad (RLS)**: Habilita políticas de seguridad para que los usuarios solo puedan leer su propia información, protegiendo la tabla `app_users`.

### 2. Refactorización del Frontend
Se modificó [index.html](file:///D:/magik/Mi%20app/Cuevanatv/docs/index.html).
- **Autenticación Nativa**: Las funciones `login` y `register` ahora usan `supabase.auth` directamente.
- **Sincronización de Datos**: `syncUserData` ahora consulta la tabla `app_users` usando el `id` del usuario autenticado (UUID), lo cual es más seguro y eficiente.
- **Dashboard en Tiempo Real**: El modal de perfil refleja el estado real (Activo/Vencido) basándose en la respuesta de la base de datos.

### 3. Integración con MercadoPago (Checkout Pro)
Se implementó la Edge Function en [index.ts](file:///D:/magik/Mi%20app/Cuevanatv/supabase/functions/create-preference/index.ts).
- **Seguridad**: El `ACCESS_TOKEN` de MercadoPago ya no se expone en el cliente; ahora se maneja en el servidor.
- **Flujo de Pago**: El botón "Pagar Abono" invoca esta función para obtener un `preferenceId` y abre el Checkout Pro de MercadoPago de forma fluida.

---

## Instrucciones para el Despliegue Final

Para que todo funcione correctamente, debes seguir estos pasos en tu consola de Supabase:

1.  **Ejecutar el SQL**: Copia el contenido de [setup_auth_trigger.sql](file:///D:/magik/Mi%20app/Cuevanatv/supabase/setup_auth_trigger.sql) y ejecútalo en el **SQL Editor** de Supabase.
2.  **Configurar Secretos**: En tu terminal (donde tengas el CLI de Supabase), ejecuta:
    ```bash
    supabase secrets set MP_ACCESS_TOKEN=tu_access_token_de_mercadopago
    ```
3.  **Desplegar la Función**:
    ```bash
    supabase functions deploy create-preference
    ```

## Verificación
- [x] El registro crea automáticamente el perfil en `app_users`.
- [x] El login recupera correctamente los días restantes.
- [x] El botón de pago abre el checkout de MercadoPago por $3000.
