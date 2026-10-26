package com.taller.productos;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static final Scanner SC = new Scanner(System.in);
    private static final ProductoDAO DAO = new ProductoDAO();

    public static void main(String[] args) {
        System.out.println("====================================");
        System.out.println("   SISTEMA DE GESTIÓN DE PRODUCTOS");
        System.out.println("====================================");
        try {
            // Verifica conexión y crea la tabla si no existe.
            inicializarTabla();
            boolean salir = false;
            while (!salir) {
                mostrarMenu();
                String opcion = leerTexto("Seleccione una opción: ");
                try {
                    switch (opcion) {
                        case "1" -> registrar();
                        case "2" -> listar();
                        case "3" -> buscar();
                        case "4" -> actualizar();
                        case "5" -> eliminar();
                        case "6" -> { salir = true; System.out.println("Hasta luego."); }
                        default -> System.out.println("Opción no válida. Elija entre 1 y 6.");
                    }
                } catch (SQLException e) {
                    System.out.println("Error de base de datos: " + e.getMessage());
                    System.out.println("Verifique que MySQL esté activo y que las credenciales sean correctas.");
                } catch (IllegalArgumentException e) {
                    System.out.println("Dato inválido: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.out.println("No fue posible conectar con MySQL: " + e.getMessage());
            System.out.println("Revise la base de datos, el usuario y las variables DB_URL, DB_USER y DB_PASSWORD.");
        } finally {
            SC.close();
        }
    }

    private static void inicializarTabla() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS productos (" +
                "codigo VARCHAR(30) PRIMARY KEY, nombre VARCHAR(100) NOT NULL, " +
                "descripcion VARCHAR(255) NOT NULL, precio DECIMAL(12,2) NOT NULL, cantidad INT NOT NULL, " +
                "CONSTRAINT chk_producto_precio CHECK (precio >= 0), " +
                "CONSTRAINT chk_producto_cantidad CHECK (cantidad >= 0))";
        try (var con = ConexionBD.obtenerConexion(); var st = con.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static void mostrarMenu() {
        System.out.println("\n1. Registrar producto");
        System.out.println("2. Consultar todos los productos");
        System.out.println("3. Buscar por código o nombre");
        System.out.println("4. Actualizar descripción, precio y cantidad");
        System.out.println("5. Eliminar producto");
        System.out.println("6. Salir");
    }

    private static void registrar() throws SQLException {
        String codigo = requerido("Código: ");
        String nombre = requerido("Nombre: ");
        String descripcion = requerido("Descripción: ");
        BigDecimal precio = leerPrecio("Precio (use punto decimal, por ejemplo 12500.50): ");
        int cantidad = leerCantidad("Cantidad disponible: ");
        DAO.crear(new Producto(codigo, nombre, descripcion, precio, cantidad));
        System.out.println("Producto registrado correctamente.");
    }

    private static void listar() throws SQLException {
        mostrarProductos(DAO.listarTodos());
    }

    private static void buscar() throws SQLException {
        String termino = requerido("Ingrese el código exacto o parte del nombre: ");
        mostrarProductos(DAO.buscar(termino));
    }

    private static void actualizar() throws SQLException {
        String codigo = requerido("Código del producto que desea actualizar: ");
        String descripcion = requerido("Nueva descripción: ");
        BigDecimal precio = leerPrecio("Nuevo precio: ");
        int cantidad = leerCantidad("Nueva cantidad: ");
        if (DAO.actualizar(codigo, descripcion, precio, cantidad))
            System.out.println("Producto actualizado correctamente.");
        else
            System.out.println("No existe un producto con ese código.");
    }

    private static void eliminar() throws SQLException {
        String codigo = requerido("Código del producto que desea eliminar: ");
        System.out.print("¿Confirma la eliminación? Escriba SI para continuar: ");
        String confirmacion = SC.nextLine().trim();
        if (!confirmacion.equalsIgnoreCase("SI")) {
            System.out.println("Eliminación cancelada.");
            return;
        }
        if (DAO.eliminar(codigo)) System.out.println("Producto eliminado correctamente.");
        else System.out.println("No existe un producto con ese código.");
    }

    private static void mostrarProductos(List<Producto> productos) {
        if (productos.isEmpty()) {
            System.out.println("No se encontraron productos.");
            return;
        }
        System.out.println("\n--- Productos encontrados: " + productos.size() + " ---");
        productos.forEach(System.out::println);
    }

    private static String leerTexto(String mensaje) {
        System.out.print(mensaje);
        return SC.nextLine().trim();
    }

    private static String requerido(String mensaje) {
        String valor = leerTexto(mensaje);
        if (valor.isBlank()) throw new IllegalArgumentException("Este campo no puede quedar vacío.");
        return valor;
    }

    private static BigDecimal leerPrecio(String mensaje) {
        String entrada = requerido(mensaje);
        try {
            BigDecimal valor = new BigDecimal(entrada);
            if (valor.compareTo(BigDecimal.ZERO) < 0)
                throw new IllegalArgumentException("El precio no puede ser negativo.");
            return valor;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ingrese un precio numérico válido, por ejemplo 12500.50.");
        }
    }

    private static int leerCantidad(String mensaje) {
        String entrada = requerido(mensaje);
        try {
            int valor = Integer.parseInt(entrada);
            if (valor < 0) throw new IllegalArgumentException("La cantidad no puede ser negativa.");
            return valor;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("La cantidad debe ser un número entero no negativo.");
        }
    }
}
