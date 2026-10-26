# Sistema de Gestión de Productos

Este proyecto lo desarrollé para el taller de persistencia de datos en Java. La idea era crear un programa que permitiera llevar un registro de productos y guardar la información en una base de datos, de manera que no se perdiera al cerrar la aplicación.

Primero hice una versión que se manejaba desde la consola. Después la adapté a una página web para que se pudiera utilizar desde cualquier computador, sin necesidad de instalar Java o MySQL.

## Probar el sistema

**Enlace a la aplicación:** https://gestion-productos-java.onrender.com

Desde ese enlace se puede entrar al sistema y probar sus funciones directamente en el navegador. Como está alojado en un servicio gratuito, es posible que tarde un poco en abrir cuando lleva tiempo sin utilizarse.

## ¿Qué permite hacer?

- **Registrar productos** con código, nombre, descripción, precio y cantidad.
- **Consultar** los productos que están guardados.
- **Buscar** por código o por una parte del nombre.
- **Actualizar** la descripción, el precio y la cantidad de un producto.
- **Eliminar** productos que ya no se necesitan.
- **Conservar los datos** al recargar la página o cerrar el navegador, porque se guardan en MySQL.

El sistema también valida algunos datos de los formularios, por ejemplo, para evitar campos vacíos y precios o cantidades negativos.

## Tecnologías utilizadas

- **Java:** lógica principal de la aplicación.
- **JDBC y MySQL Connector/J:** conexión entre Java y la base de datos.
- **MySQL:** almacenamiento de los productos.
- **Maven:** compilación y gestión de dependencias.
- **HTML y CSS:** presentación de la interfaz web.
- **Servidor HTTP de Java:** permite utilizar el sistema desde un navegador.
- **GitHub:** repositorio del código.
- **Render:** alojamiento de la aplicación web.
- **Aiven:** alojamiento de MySQL en Internet.

## ¿Cómo funciona la persistencia?

Cuando se registra, modifica o elimina un producto, Java realiza la operación correspondiente en MySQL. Por eso la información no depende de que la página permanezca abierta: al volver a entrar, el sistema consulta los datos guardados en la base de datos.

Durante el desarrollo probé el registro y la consulta tanto desde la aplicación como desde MySQL Workbench, primero con una base de datos local y después con la base de datos de Aiven.

## Archivos principales

- `Main.java`: versión original por consola.
- `WebMain.java`: servidor e interfaz web.
- `ConexionBD.java`: conexión con MySQL.
- `Producto.java`: datos de cada producto.
- `ProductoDAO.java`: operaciones sobre la base de datos.
- `sql/crear_base_datos.sql`: creación de la base de datos y la tabla.
- `pom.xml`: configuración de Maven.
- `Dockerfile`: despliegue de la aplicación.

## Ejecutarlo en un computador

Para trabajar con el código de forma local se necesita Java 17 o superior, Maven y una base de datos MySQL.

1. Crear la base de datos y la tabla ejecutando `sql/crear_base_datos.sql` en MySQL.
2. Configurar las variables de entorno `DB_URL`, `DB_USER` y `DB_PASSWORD` según la instalación de MySQL.
3. Desde la carpeta que contiene `pom.xml`, ejecutar:

    `mvn clean package`

    `java -jar target/SistemaGestionProductos-1.1.0.jar`

4. Abrir `http://localhost:8080` en el navegador.

La contraseña de la base de datos no está incluida en el repositorio. En la versión publicada, la conexión se configura de manera privada desde el servicio de alojamiento.

## Nota sobre la versión en línea

El enlace web es una demostración académica. No tiene cuentas de usuario, así que cualquier persona que entre puede agregar, modificar o eliminar los productos de prueba. Se recomienda no utilizar información personal ni datos importantes.

La aplicación y la base de datos usan servicios gratuitos, por lo que su disponibilidad puede depender de los límites y períodos de inactividad de esos servicios.