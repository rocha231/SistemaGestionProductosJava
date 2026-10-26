# Sistema de Gestión de Productos — Java web + JDBC + MySQL

**Versión web de demostración:** el usuario puede registrar, listar, buscar, actualizar y eliminar productos desde el navegador. Los datos se conservan en MySQL.

> El enlace público solo existirá después de desplegar la aplicación en un alojamiento. GitHub por sí solo no ejecuta Java ni MySQL.

## Tecnología
- Java 17+ (proyecto compilado para Java 17)
- Servidor HTTP incluido en el JDK (`HttpServer`), sin añadir Spring Boot
- JDBC + MySQL Connector/J (gestión de dependencias con Maven)
- HTML/CSS renderizado desde Java (sin cliente de compilación separado)
- Dockerfile multi-etapa para despliegue en Railway u otro proveedor compatible

## Clases
- `Producto.java`: modelo de producto original.
- `ProductoDAO.java`: las operaciones SQL CRUD originales (consultas parametrizadas).
- `ConexionBD.java`: datos de conexión mediante variables de entorno.
- `Main.java`: se conserva la versión de consola original.
- `WebMain.java`: nueva interfaz web y servidor HTTP para las operaciones CRUD.

## 1. Crear base de datos local
En la conexión local de MySQL Workbench, ejecutar `sql/crear_base_datos.sql`.

## 2. Configurar conexión en PowerShell
En la terminal donde vas a ejecutar Maven:

```powershell
$env:DB_URL = "jdbc:mysql://127.0.0.1:3307/gestion_productos"
$env:DB_USER = "root"
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", (Read-Host -AsSecureString "Contraseña MySQL")).Password
```

3307 era tu puerto local; otras personas deben usar los datos de **su propio MySQL**. No subas contraseñas a GitHub.

## 3. Probar en tu computador

```powershell
mvn clean package
java -jar target/SistemaGestionProductos-1.1.0.jar
```

Abrir **http://localhost:8080** en un navegador. Para detener el servidor, `Ctrl+C`.

También puedes iniciar la versión web con `mvn exec:java` y conservar la versión de consola con `mvn exec:java "-Dexec.mainClass=com.taller.productos.Main"`.

La aplicación escucha en `0.0.0.0` y usa la variable de entorno `PORT`, definida automáticamente por muchas plataformas de alojamiento. La tabla `productos` se crea si no existe dentro de la base ya seleccionada.

## 4. Publicar con GitHub + Railway
1. Subir el contenido de esta carpeta a un repositorio GitHub, **sin** archivos `.env` ni credenciales.
2. Crear un proyecto en Railway, agregar un servicio web desde ese repositorio. Railway detectará el `Dockerfile`.
3. Crear un servicio MySQL en Railway.
4. En **Variables** del servicio web, configurar `DB_URL`, `DB_USER`, `DB_PASSWORD` utilizando los valores y referencias del servicio MySQL de Railway. La URL JDBC debe incluir el host, puerto y nombre de la base MySQL de Railway, no `localhost`.
5. Habilitar un dominio público para el servicio web.
6. Abrir la URL, registrar un producto, cerrar el navegador, volver a entrar y consultar los datos.
7. Pegar la URL pública en la primera sección del `README.md` del repositorio.

> Se requieren cuentas propias, activación del despliegue y un plan/cuota suficiente para mantener la aplicación y MySQL en línea. Este ZIP no publica automáticamente el proyecto.

## Importante — seguridad
- Las contraseñas solo se leen de variables de entorno; no se entregan dentro del código.
- Las sentencias JDBC siguen utilizando `PreparedStatement`.
- Los campos se validan en el servidor, y el HTML escapa texto proporcionado por el usuario.
- Los formularios POST usan un token antifalsificación asociado a una cookie SameSite.
- **Esta es una demo educativa sin inicio de sesión.** Cualquier persona que conozca la URL puede crear, modificar y eliminar los productos de demostración. No la uses con información real o sensible; para producción, agregar autenticación y roles.
