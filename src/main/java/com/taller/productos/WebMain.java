package com.taller.productos;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** Interfaz web del proyecto original. Usa el mismo ProductoDAO y la misma tabla MySQL. */
public final class WebMain {
    private static final ProductoDAO DAO = new ProductoDAO();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile boolean tablaPreparada;

    private WebMain() {}

    public static void main(String[] args) throws IOException {
        int port;
        try {
            port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
            if (port < 1 || port > 65535) throw new NumberFormatException("invalid port");
        } catch (NumberFormatException e) {
            System.err.println("La variable PORT debe ser un puerto válido.");
            return;
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", WebMain::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Sistema web iniciado. Abra http://localhost:" + port);
    }

    private static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            ex.getResponseHeaders().set("X-Frame-Options", "DENY");
            ex.getResponseHeaders().set("Referrer-Policy", "same-origin");
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            if ("/health".equals(path)) {
                if (!"GET".equals(method)) { respond(ex, 405, "text/plain", "Método no permitido"); return; }
                health(ex);
                return;
            }
            if ("/".equals(path) && "GET".equals(method)) {
                view(ex, query(ex.getRequestURI().getRawQuery()), 200, null);
                return;
            }
            if (("/crear".equals(path) || "/actualizar".equals(path) || "/eliminar".equals(path))
                    && "POST".equals(method)) {
                Map<String, String> data = parseForm(ex);
                if (!validCsrf(ex, data.getOrDefault("csrf", ""))) {
                    respond(ex, 403, "text/plain", "Formulario inválido. Actualice la página e inténtelo otra vez.");
                    return;
                }
                try {
                    initTabla();
                    String ok;
                    switch (path) {
                        case "/crear" -> {
                            Producto p = new Producto(
                                    required(data, "codigo", 30),
                                    required(data, "nombre", 100),
                                    required(data, "descripcion", 255),
                                    precio(data),
                                    cantidad(data));
                            DAO.crear(p);
                            ok = "creado";
                        }
                        case "/actualizar" -> {
                            String codigo = required(data, "codigo", 30);
                            boolean updated = DAO.actualizar(codigo,
                                    required(data, "descripcion", 255), precio(data), cantidad(data));
                            ok = updated ? "actualizado" : "noexiste";
                        }
                        case "/eliminar" -> {
                            boolean deleted = DAO.eliminar(required(data, "codigo", 30));
                            ok = deleted ? "eliminado" : "noexiste";
                        }
                        default -> throw new IllegalArgumentException("Acción no reconocida");
                    }
                    redirect(ex, "/?estado=" + ok);
                } catch (IllegalArgumentException e) {
                    view(ex, Map.of(), 400, e.getMessage());
                } catch (SQLException e) {
                    System.err.println("Error MySQL al guardar: " + e.getMessage());
                    String error = "No fue posible guardar el cambio. Compruebe la conexión MySQL.";
                    if ("23000".equals(e.getSQLState())) {
                        error = "El código de producto ya existe, o hay una restricción de datos.";
                    }
                    view(ex, Map.of(), 503, error);
                }
                return;
            }
            respond(ex, 404, "text/plain", "Página no encontrada");
        } catch (IllegalArgumentException e) {
            respond(ex, 400, "text/plain", "Solicitud inválida: " + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("Error web: " + e.getMessage());
            respond(ex, 500, "text/plain", "Error inesperado.");
        } finally {
            ex.close();
        }
    }

    private static synchronized void initTabla() throws SQLException {
        if (tablaPreparada) return;
        String sql = "CREATE TABLE IF NOT EXISTS productos ("
                + "codigo VARCHAR(30) PRIMARY KEY, "
                + "nombre VARCHAR(100) NOT NULL, "
                + "descripcion VARCHAR(255) NOT NULL, "
                + "precio DECIMAL(12,2) NOT NULL, "
                + "cantidad INT NOT NULL, "
                + "CONSTRAINT chk_producto_precio CHECK (precio >= 0), "
                + "CONSTRAINT chk_producto_cantidad CHECK (cantidad >= 0))";
        try (var con = ConexionBD.obtenerConexion(); var statement = con.createStatement()) {
            statement.executeUpdate(sql);
            tablaPreparada = true;
        }
    }

    private static void health(HttpExchange ex) throws IOException {
        try {
            initTabla();
            try (var connection = ConexionBD.obtenerConexion(); var statement = connection.createStatement()) {
                statement.executeQuery("SELECT 1");
            }
            respond(ex, 200, "application/json", "{\"status\":\"ok\",\"mysql\":\"connected\"}");
        } catch (SQLException e) {
            respond(ex, 503, "application/json", "{\"status\":\"error\",\"mysql\":\"unavailable\"}");
        }
    }

    private static void view(HttpExchange ex, Map<String, String> query, int status, String forcedError)
            throws IOException {
        String csrf = getOrCreateCsrf(ex);
        String term = query.getOrDefault("q", "").trim();
        if (term.length() > 100) term = term.substring(0, 100);
        String estado = query.getOrDefault("estado", "");
        List<Producto> productos = new ArrayList<>();
        String error = forcedError;
        try {
            initTabla();
            productos = term.isEmpty() ? DAO.listarTodos() : DAO.buscar(term);
        } catch (SQLException e) {
            System.err.println("Error MySQL al listar: " + e.getMessage());
            error = "No se puede acceder a MySQL. Revise DB_URL, DB_USER, DB_PASSWORD y que el servidor esté disponible.";
            status = 503;
        }
        String page = pageHtml(productos, term, estado, error, csrf);
        respond(ex, status, "text/html", page);
    }

    private static Map<String, String> parseForm(HttpExchange ex) throws IOException {
        String type = ex.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
            throw new IllegalArgumentException("Formato de formulario no admitido");
        }
        byte[] bytes = ex.getRequestBody().readNBytes(16_385);
        if (bytes.length > 16_384) throw new IllegalArgumentException("Formulario demasiado largo");
        return query(new String(bytes, StandardCharsets.UTF_8));
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> fields = new HashMap<>();
        if (raw == null || raw.isBlank()) return fields;
        for (String part : raw.split("&", -1)) {
            int eq = part.indexOf('=');
            String key = eq >= 0 ? part.substring(0, eq) : part;
            String val = eq >= 0 ? part.substring(eq + 1) : "";
            fields.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(val, StandardCharsets.UTF_8));
        }
        return fields;
    }

    private static String required(Map<String, String> data, String key, int length) {
        String value = data.getOrDefault(key, "").trim();
        if (value.isBlank()) throw new IllegalArgumentException("El campo " + key + " no puede estar vacío.");
        if (value.length() > length) throw new IllegalArgumentException("El campo " + key + " supera " + length + " caracteres.");
        return value;
    }

    private static BigDecimal precio(Map<String, String> data) {
        String raw = required(data, "precio", 20);
        try {
            BigDecimal bd = new BigDecimal(raw);
            if (bd.signum() < 0 || bd.compareTo(new BigDecimal("9999999999.99")) > 0)
                throw new IllegalArgumentException("El precio debe estar entre 0 y 9999999999.99.");
            return bd.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("El precio debe ser un número con máximo dos decimales, por ejemplo 95000.00.");
        }
    }

    private static int cantidad(Map<String, String> data) {
        String raw = required(data, "cantidad", 11);
        try {
            int n = Integer.parseInt(raw);
            if (n < 0) throw new IllegalArgumentException("La cantidad no puede ser negativa.");
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("La cantidad debe ser un número entero no negativo.");
        }
    }

    private static boolean validCsrf(HttpExchange ex, String field) {
        String cookie = getCookie(ex, "webcsrf");
        return cookie != null && cookie.matches("[0-9a-f]{64}")
                && MessageDigest.isEqual(cookie.getBytes(StandardCharsets.UTF_8),
                        field.getBytes(StandardCharsets.UTF_8));
    }

    private static String getOrCreateCsrf(HttpExchange ex) {
        String existing = getCookie(ex, "webcsrf");
        if (existing != null && existing.matches("[0-9a-f]{64}")) return existing;
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        StringBuilder b = new StringBuilder(64);
        for (byte item : token) b.append(String.format("%02x", item & 0xff));
        String secure = "https".equalsIgnoreCase(ex.getRequestHeaders().getFirst("X-Forwarded-Proto")) ? "; Secure" : "";
        ex.getResponseHeaders().add("Set-Cookie", "webcsrf=" + b + "; Path=/; HttpOnly; SameSite=Lax" + secure);
        return b.toString();
    }

    private static String getCookie(HttpExchange ex, String name) {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return null;
        for (String part : cookie.split(";")) {
            String t = part.trim();
            if (t.startsWith(name + "=")) return t.substring(name.length() + 1);
        }
        return null;
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String pageHtml(List<Producto> products, String q, String status, String error, String csrf) {
        StringBuilder body = new StringBuilder();
        body.append("""
                <!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Sistema de Gestión de Productos</title><style>
                :root{font-family:Inter,system-ui,Arial,sans-serif;color:#172b46;background:#f3f6fa}*{box-sizing:border-box}
                body{margin:0}.wrap{max-width:1120px;margin:auto;padding:30px 22px 65px}
                header{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:28px}
                .eyebrow{letter-spacing:.13em;text-transform:uppercase;font-size:11px;font-weight:800;color:#3e76b4}
                h1{font-size:clamp(25px,4vw,36px);letter-spacing:-.04em;margin:7px 0 5px}
                .sub{color:#64738a;margin:0;font-size:14px}.badge{background:#e8f6f0;color:#147a55;border:1px solid #c7e5d7;
                  border-radius:40px;padding:9px 14px;font-size:12px;font-weight:700}
                .panel{background:white;border:1px solid #e0e6ef;border-radius:17px;padding:23px;box-shadow:0 6px 24px #22415c0a}
                .stats{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:15px;margin-bottom:20px}
                .stat{padding:22px}.stat span{display:block;color:#7a8699;font-size:13px}.stat strong{display:block;font-size:28px;margin-top:8px}
                .formtitle{font-size:18px;margin:0 0 18px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}
                label{display:block;font-weight:650;font-size:12px;color:#4e5f75;margin:0 0 7px}
                input{width:100%;border:1px solid #d5ddeb;border-radius:9px;padding:11px 12px;color:#172b46;background:#fff;font:inherit;font-size:14px}
                input:focus{outline:2px solid #8bbef7;border-color:#236dbd}.full{grid-column:1/-1}
                button,.btn{border:0;background:#246bb5;color:white;padding:11px 17px;border-radius:9px;font-weight:750;cursor:pointer;font-size:13px}
                button:hover,.btn:hover{background:#125597}.row{display:flex;align-items:center;justify-content:space-between;gap:14px;flex-wrap:wrap}
                .primary{margin-top:18px}.table-panel{margin-top:20px} .search{display:flex;gap:8px;flex-wrap:wrap}
                .search input{min-width:240px;flex:1}.search button{background:#233d60}
                table{width:100%;border-collapse:collapse;margin-top:13px;text-align:left;font-size:13px}
                th{color:#6a7a90;font-size:11px;letter-spacing:.05em;text-transform:uppercase;border-bottom:1px solid #e4e9f1;padding:15px 8px}
                td{padding:14px 8px;border-bottom:1px solid #ecf0f6;vertical-align:top}td code{background:#f0f5fb;border-radius:5px;padding:5px 7px}
                .money{white-space:nowrap;font-weight:750}.qty{font-weight:750}.action{min-width:190px}
                .inline{display:inline}.danger{background:#fff0f0;color:#ab2e37;border:1px solid #f5d1d2}.danger:hover{background:#fbe3e5}
                details{margin-bottom:9px}summary{cursor:pointer;color:#2369b0;font-size:12px;font-weight:750}
                .edit{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px;background:#f6f8fb;border-radius:9px;padding:11px;margin-top:10px}
                .edit input{font-size:12px;padding:8px}.edit button{grid-column:1/-1}.notice{padding:13px 16px;margin-bottom:20px;border-radius:10px;font-size:13px}
                .success{background:#e7f7ef;color:#12613f;border:1px solid #bde4cc}.error{background:#fff1ed;color:#8d3527;border:1px solid #f2cbc2}
                .empty{padding:25px 0;color:#66758a;text-align:center}.small{font-size:12px;color:#7b879a;margin-top:17px}
                @media(max-width:700px){.grid,.stats{grid-template-columns:1fr}.wrap{padding:20px 12px}
                  .panel{padding:16px}.scroll{overflow-x:auto}table{min-width:740px}header{align-items:start}.badge{white-space:nowrap}}
                </style></head><body><main class="wrap">
                <header><div><div class="eyebrow">Inventario / Proyecto Java</div><h1>Gestión de productos</h1>
                <p class="sub">Registrar, consultar y administrar productos en MySQL.</p></div><span class="badge">● Persistencia MySQL</span></header>
                """);
        if (error != null) body.append("<div role='alert' class='notice error'>").append(safe(error)).append("</div>");
        String ok = switch (status) {
            case "creado" -> "Producto registrado correctamente.";
            case "actualizado" -> "Producto actualizado correctamente.";
            case "eliminado" -> "Producto eliminado correctamente.";
            case "noexiste" -> "No se encontró un producto con ese código.";
            default -> null;
        };
        if (ok != null) body.append("<div role='status' class='notice ").append("noexiste".equals(status) ? "error" : "success")
                .append("'>").append(ok).append("</div>");

        int total = 0;
        BigDecimal inventory = BigDecimal.ZERO;
        for (Producto p : products) {
            total += p.getCantidad();
            inventory = inventory.add(p.getPrecio().multiply(BigDecimal.valueOf(p.getCantidad())));
        }
        body.append("<div class='stats'>")
                .append("<section class='panel stat'><span>Productos mostrados</span><strong>").append(products.size()).append("</strong></section>")
                .append("<section class='panel stat'><span>Unidades mostradas</span><strong>").append(total).append("</strong></section></div>");

        body.append("<section class='panel'><h2 class='formtitle'>Registrar nuevo producto</h2><form method='post' action='/crear'>")
                .append(hiddenCsrf(csrf)).append("<div class='grid'>")
                .append(field("Código", "codigo", "P001", 30, "text", ""))
                .append(field("Nombre", "nombre", "Teclado Logitech", 100, "text", ""))
                .append(field("Descripción", "descripcion", "Teclado USB para computador", 255, "text", "full"))
                .append(field("Precio", "precio", "85000.00", 20, "number", ""))
                .append(field("Cantidad disponible", "cantidad", "10", 11, "number", ""))
                .append("</div><button class='primary' type='submit'>+ Registrar producto</button></form></section>");
        body.append("<section class='panel table-panel'><div class='row'><h2 class='formtitle' style='margin:0'>Productos registrados</h2>")
                .append("<form method='get' action='/' class='search'><input name='q' aria-label='Buscar productos' placeholder='Código exacto o parte del nombre' value='")
                .append(safe(q)).append("'><button type='submit'>Buscar</button></form></div>");
        if (products.isEmpty()) {
            body.append("<div class='empty'>No hay productos para mostrar. Puedes registrar uno arriba.</div>");
        } else {
            body.append("<div class='scroll'><table><thead><tr><th>Código</th><th>Nombre y descripción</th><th>Precio</th><th>Cantidad</th><th>Acciones</th></tr></thead><tbody>");
            for (Producto p : products) {
                body.append("<tr><td><code>").append(safe(p.getCodigo())).append("</code></td><td><strong>")
                        .append(safe(p.getNombre())).append("</strong><p class='sub'>").append(safe(p.getDescripcion()))
                        .append("</p></td><td class='money'>$").append(p.getPrecio().toPlainString())
                        .append("</td><td class='qty'>").append(p.getCantidad())
                        .append("</td><td class='action'><details><summary>Editar producto</summary>")
                        .append("<form class='edit' method='post' action='/actualizar'>").append(hiddenCsrf(csrf))
                        .append("<input type='hidden' name='codigo' value='").append(safe(p.getCodigo())).append("'>")
                        .append("<div class='full'><label>Descripción</label><input name='descripcion' maxlength='255' required value='")
                        .append(safe(p.getDescripcion())).append("'></div>")
                        .append("<div><label>Precio</label><input name='precio' type='number' min='0' step='0.01' required value='")
                        .append(p.getPrecio().toPlainString()).append("'></div>")
                        .append("<div><label>Cantidad</label><input name='cantidad' type='number' min='0' step='1' required value='")
                        .append(p.getCantidad()).append("'></div>")
                        .append("<button type='submit'>Guardar cambios</button></form></details>")
                        .append("<form method='post' action='/eliminar' onsubmit=\"return confirm('¿Eliminar este producto?')\">")
                        .append(hiddenCsrf(csrf)).append("<input type='hidden' name='codigo' value='")
                        .append(safe(p.getCodigo())).append("'><button type='submit' class='danger'>Eliminar</button></form></td></tr>");
            }
            body.append("</tbody></table></div>");
        }
        body.append("<div class='small'>Los datos se guardan en MySQL y siguen disponibles después de cerrar el navegador. ")
                .append("Esta versión es una demostración académica sin usuarios ni roles.</div></section>")
                .append("</main></body></html>");
        return body.toString();
    }

    private static String hiddenCsrf(String csrf) {
        return "<input type='hidden' name='csrf' value='" + csrf + "'>";
    }

    private static String field(String label, String name, String placeholder, int max,
                                String type, String className) {
        String numeric = "number".equals(type)
                ? ("precio".equals(name) ? " min='0' step='0.01'" : " min='0' step='1'") : "";
        return "<div class='" + className + "'><label for='f_" + name + "'>" + label + "</label>"
                + "<input id='f_" + name + "' name='" + name + "' type='" + type
                + "' maxlength='" + max + "' placeholder='" + placeholder + "' required" + numeric + "></div>";
    }

    private static void redirect(HttpExchange ex, String where) throws IOException {
        ex.getResponseHeaders().set("Location", where);
        ex.sendResponseHeaders(303, -1);
    }

    private static void respond(HttpExchange ex, int code, String mime, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", mime + "; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (var out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
