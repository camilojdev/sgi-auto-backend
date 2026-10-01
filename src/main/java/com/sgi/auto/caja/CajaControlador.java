package com.sgi.auto.caja;

import com.sgi.auto.caja.dto.*;
import com.sgi.auto.compartido.ApiRespuesta;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/caja")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DUENO','CAJERA')")
public class CajaControlador {

    private final CajaServicio cajaServicio;

    @PostMapping("/abrir")
    @PreAuthorize("hasRole('DUENO') or (hasRole('CAJERA') and authentication.principal.puedeCerrarCaja)")
    public ResponseEntity<ApiRespuesta<SesionCajaRespuestaDTO>> abrir(
            @Valid @RequestBody AperturaCajaDTO solicitud) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(
                cajaServicio.abrirSesion(solicitud),
                "Sesión de caja abierta correctamente"));
    }

    @PostMapping("/cerrar")
    @PreAuthorize("hasRole('DUENO') or (hasRole('CAJERA') and authentication.principal.puedeCerrarCaja)")
    public ResponseEntity<ApiRespuesta<SesionCajaRespuestaDTO>> cerrar(
            @Valid @RequestBody CierreCajaDTO solicitud) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(
                cajaServicio.cerrarSesion(solicitud),
                "Sesión de caja cerrada correctamente"));
    }

    @GetMapping("/sesiones-abiertas")
    @PreAuthorize("hasRole('DUENO')")
    public ResponseEntity<ApiRespuesta<List<SesionCajaRespuestaDTO>>> sesionesAbiertas() {
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(cajaServicio.listarSesionesAbiertas()));
    }

    @GetMapping("/historial")
    @PreAuthorize("hasRole('DUENO')")
    public ResponseEntity<ApiRespuesta<Page<SesionCajaRespuestaDTO>>> historial(
            @RequestParam(required = false) Long cajeraId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(
                        cajaServicio.listarHistorialFiltrado(cajeraId, desde, hasta, pageable)));
    }

    @GetMapping("/actual")
    public ResponseEntity<ApiRespuesta<SesionCajaRespuestaDTO>> actual() {
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(cajaServicio.obtenerSesionActual()));
    }

    /*@GetMapping("/historial")
    public ResponseEntity<ApiRespuesta<Page<SesionCajaRespuestaDTO>>> historial(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(cajaServicio.listarHistorial(pageable)));
    }*/

    @GetMapping("/{id}")
    public ResponseEntity<ApiRespuesta<SesionCajaRespuestaDTO>> obtenerPorId(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(cajaServicio.obtenerPorId(id)));
    }

    @PostMapping("/gastos")
    public ResponseEntity<ApiRespuesta<Void>> registrarGasto(
            @Valid @RequestBody GastoDTO solicitud) {
        cajaServicio.registrarGasto(solicitud);
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(null, "Gasto registrado correctamente"));
    }

    @PostMapping("/egresos")
    @PreAuthorize("hasRole('DUENO')")
    public ResponseEntity<ApiRespuesta<Void>> registrarEgreso(
            @Valid @RequestBody GastoDTO solicitud) {
        cajaServicio.registrarEgresoDueno(solicitud);
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(null, "Egreso registrado correctamente"));
    }
}
