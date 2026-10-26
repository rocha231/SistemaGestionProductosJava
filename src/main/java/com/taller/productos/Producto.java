package com.taller.productos;

import java.math.BigDecimal;

public class Producto {
    private final String codigo;
    private final String nombre;
    private final String descripcion;
    private final BigDecimal precio;
    private final int cantidad;

    public Producto(String codigo, String nombre, String descripcion, BigDecimal precio, int cantidad) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.cantidad = cantidad;
    }

    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getPrecio() { return precio; }
    public int getCantidad() { return cantidad; }

    @Override
    public String toString() {
        return String.format("Código: %s | Nombre: %s | Descripción: %s | Precio: %s | Cantidad: %d",
                codigo, nombre, descripcion, precio.toPlainString(), cantidad);
    }
}
