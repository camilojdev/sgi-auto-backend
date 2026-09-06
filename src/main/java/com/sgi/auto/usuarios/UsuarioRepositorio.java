package com.sgi.auto.usuarios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {

    // Buscar por nombre de usuario solo entre los activos (soft delete)
    @Query("SELECT u FROM Usuario u WHERE u.nombreUsuario = :nombre AND u.eliminadoEn IS NULL")
    Optional<Usuario> buscarPorNombreUsuario(@Param("nombre") String nombreUsuario);

    // Verificar si ya existe un nombre de usuario (para validación al crear)
    @Query("SELECT COUNT(u) > 0 FROM Usuario u WHERE u.nombreUsuario = :nombre AND u.eliminadoEn IS NULL")
    boolean existePorNombreUsuario(@Param("nombre") String nombreUsuario);

    // Buscar por correo
    @Query("SELECT u FROM Usuario u WHERE u.correo = :correo AND u.eliminadoEn IS NULL")
    Optional<Usuario> buscarPorCorreo(@Param("correo") String correo);

    // Buscar por nombre de usuario O correo (para login flexible)
    @Query("SELECT u FROM Usuario u WHERE (u.nombreUsuario = :identificador OR u.correo = :identificador) AND u.eliminadoEn IS NULL")
    Optional<Usuario> buscarPorUsuarioOCorreo(@Param("identificador") String identificador);
}