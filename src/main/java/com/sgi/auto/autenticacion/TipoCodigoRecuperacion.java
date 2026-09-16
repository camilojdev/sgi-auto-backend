package com.sgi.auto.autenticacion;

public enum TipoCodigoRecuperacion {
    RECUPERACION_CONTRASENA, // "olvidé mi contraseña" desde el login (sin sesión)
    CAMBIO_CONTRASENA,       // cambio de contraseña desde el perfil (con sesión)
    CAMBIO_CORREO            // confirmación del correo nuevo al cambiarlo desde el perfil
}