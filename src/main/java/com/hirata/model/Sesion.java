/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.model;

/**
 *
 * @author wadev
 */
public class Sesion {
    public static int idPersonal;
    public static String rut;
    public static String nombre;
    public static String rol;
    
    // Método para resetear todo al cerrar sesión
    public static void limpiarSesion() {
        idPersonal = 0;
        rut = null;
        nombre = null;
        rol = null;
    }
}
