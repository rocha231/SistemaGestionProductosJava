package com.taller.productos;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** DAO: centraliza el acceso a la tabla productos. */
public class ProductoDAO {

    public void crear(Producto p) throws SQLException {
        String sql = "INSERT INTO productos (codigo, nombre, descripcion, precio, cantidad) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = ConexionBD.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, p.getCodigo());
            ps.setString(2, p.getNombre());
            ps.setString(3, p.getDescripcion());
            ps.setBigDecimal(4, p.getPrecio());
            ps.setInt(5, p.getCantidad());
            ps.executeUpdate();
        }
    }

    public List<Producto> listarTodos() throws SQLException {
        String sql = "SELECT codigo, nombre, descripcion, precio, cantidad FROM productos ORDER BY nombre";
        List<Producto> productos = new ArrayList<>();
        try (Connection con = ConexionBD.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) productos.add(mapear(rs));
        }
        return productos;
    }

    public List<Producto> buscar(String termino) throws SQLException {
        String sql = "SELECT codigo, nombre, descripcion, precio, cantidad FROM productos " +
                     "WHERE codigo = ? OR nombre LIKE ? ORDER BY nombre";
        List<Producto> productos = new ArrayList<>();
        try (Connection con = ConexionBD.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, termino);
            ps.setString(2, "%" + termino + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) productos.add(mapear(rs));
            }
        }
        return productos;
    }

    public boolean actualizar(String codigo, String descripcion, BigDecimal precio, int cantidad) throws SQLException {
        String sql = "UPDATE productos SET descripcion = ?, precio = ?, cantidad = ? WHERE codigo = ?";
        try (Connection con = ConexionBD.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, descripcion);
            ps.setBigDecimal(2, precio);
            ps.setInt(3, cantidad);
            ps.setString(4, codigo);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean eliminar(String codigo) throws SQLException {
        String sql = "DELETE FROM productos WHERE codigo = ?";
        try (Connection con = ConexionBD.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, codigo);
            return ps.executeUpdate() > 0;
        }
    }

    private Producto mapear(ResultSet rs) throws SQLException {
        return new Producto(rs.getString("codigo"), rs.getString("nombre"),
                rs.getString("descripcion"), rs.getBigDecimal("precio"), rs.getInt("cantidad"));
    }
}
