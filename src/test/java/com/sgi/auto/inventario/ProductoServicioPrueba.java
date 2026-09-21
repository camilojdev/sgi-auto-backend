package com.sgi.auto.inventario;

import com.sgi.auto.compartido.ConflictoExcepcion;
import com.sgi.auto.compartido.RecursoNoEncontradoExcepcion;
import com.sgi.auto.compartido.ReglaNegocioExcepcion;
import com.sgi.auto.inventario.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductoServicio — pruebas unitarias")
class ProductoServicioPrueba {

    @Mock ProductoRepositorio productoRepositorio;
    @Mock MovimientoStockRepositorio movimientoStockRepositorio;
    @Mock CategoriaRepositorio categoriaRepositorio;
    @Mock ProveedorRepositorio proveedorRepositorio;
    @Mock ProductoMapper productoMapper;

    @InjectMocks ProductoServicio productoServicio;

    private Producto productoPrueba;
    private ProductoCrearDTO solicitudCrear;
    private ProductoRespuestaDTO respuestaPrueba;

    @BeforeEach
    void configurar() {
        productoPrueba = new Producto();
        productoPrueba.setId(1L);
        productoPrueba.setCodigo("PROD-001");
        productoPrueba.setNombre("Alternador 12V");
        productoPrueba.setStockActual(10);
        productoPrueba.setStockMinimo(3);
        productoPrueba.setEstaActivo(true);
        productoPrueba.setPrecioCompraConIva(new BigDecimal("50000"));
        productoPrueba.setPrecioCompraSinIva(new BigDecimal("50000"));
        productoPrueba.setPrecioVentaDetal(new BigDecimal("80000"));
        productoPrueba.setPrecioVentaMayor(new BigDecimal("80000"));

        solicitudCrear = new ProductoCrearDTO(
                "PROD-001",
                null, // numeroInterno
                "Alternador 12V",
                "Alternador para vehículos livianos",
                null, null, "unidad",
                new BigDecimal("50000"),
                new BigDecimal("80000"),
                10,  // stockActual
                3,   // stockMinimo
                true,
                null);

        respuestaPrueba = new ProductoRespuestaDTO(
                1L,
                "PROD-001",
                null,              // numeroInterno
                "Alternador 12V",
                "Alternador para vehículos livianos",
                null,              // categoriaId
                null,              // categoriaNombre
                null,              // proveedorNombre
                "unidad",          // unidadMedida
                new BigDecimal("50000"),   // precioCompraConIva
                new BigDecimal("50000"),   // precioCompraSinIva
                new BigDecimal("80000"),   // precioVentaDetal
                new BigDecimal("80000"),   // precioVentaMayor
                new BigDecimal("60.00"),   // margenGananciaPct
                10,                // stockActual
                3,                 // stockMinimo
                false,             // stockBajoMinimo
                true,              // mostrarEnListaPrecios
                true,              // estaActivo
                null,
                null);             // creadoEn
    }

    @Test
    @DisplayName("Crear producto exitosamente")
    void crearProducto_codigoNuevo_seGuarda() {
        when(productoRepositorio.existePorCodigo("PROD-001")).thenReturn(false);
        when(productoMapper.aEntidad(solicitudCrear)).thenReturn(productoPrueba);
        when(productoRepositorio.save(productoPrueba)).thenReturn(productoPrueba);
        when(productoMapper.aDTO(productoPrueba)).thenReturn(respuestaPrueba);

        ProductoRespuestaDTO resultado = productoServicio.crearProducto(solicitudCrear);

        assertThat(resultado.codigo()).isEqualTo("PROD-001");
        assertThat(resultado.nombre()).isEqualTo("Alternador 12V");
        verify(productoRepositorio).save(productoPrueba);
    }

    @Test
    @DisplayName("Crear producto con código duplicado lanza ConflictoExcepcion")
    void crearProducto_codigoDuplicado_lanzaConflicto() {
        when(productoRepositorio.existePorCodigo("PROD-001")).thenReturn(true);

        assertThatThrownBy(() -> productoServicio.crearProducto(solicitudCrear))
                .isInstanceOf(ConflictoExcepcion.class)
                .hasMessageContaining("PROD-001");

        verify(productoRepositorio, never()).save(any());
    }

    @Test
    @DisplayName("Ajuste positivo incrementa el stock y registra movimiento")
    void ajustarStock_positivo_incrementaStock() {
        when(productoRepositorio.findById(1L)).thenReturn(Optional.of(productoPrueba));
        when(productoRepositorio.save(productoPrueba)).thenReturn(productoPrueba);
        when(movimientoStockRepositorio.save(any())).thenReturn(new MovimientoStock());
        when(productoMapper.aDTO(productoPrueba)).thenReturn(respuestaPrueba);

        AjusteStockDTO ajuste = new AjusteStockDTO(5, "Ajuste por inventario físico");
        productoServicio.ajustarStock(1L, ajuste);

        assertThat(productoPrueba.getStockActual()).isEqualTo(15);
        verify(movimientoStockRepositorio).save(any(MovimientoStock.class));
    }

    @Test
    @DisplayName("Ajuste negativo que deja stock en negativo lanza ReglaNegocioExcepcion")
    void ajustarStock_negativoExcesivo_lanzaReglaNegocio() {
        when(productoRepositorio.findById(1L)).thenReturn(Optional.of(productoPrueba));

        AjusteStockDTO ajuste = new AjusteStockDTO(-50, "Ajuste erróneo");

        assertThatThrownBy(() -> productoServicio.ajustarStock(1L, ajuste))
                .isInstanceOf(ReglaNegocioExcepcion.class)
                .hasMessageContaining("negativo");

        verify(productoRepositorio, never()).save(any());
        verify(movimientoStockRepositorio, never()).save(any());
    }

    @Test
    @DisplayName("Buscar con término corto retorna lista vacía")
    void buscar_terminoCorto_retornaVacio() {
        List<ProductoRespuestaDTO> resultado = productoServicio.buscar("a");
        assertThat(resultado).isEmpty();
        verifyNoInteractions(productoRepositorio);
    }

    @Test
    @DisplayName("Obtener producto inexistente lanza RecursoNoEncontradoExcepcion")
    void obtenerPorId_noExiste_lanzaExcepcion() {
        when(productoRepositorio.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoServicio.obtenerPorId(99L))
                .isInstanceOf(RecursoNoEncontradoExcepcion.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("Crear categoría con nombre duplicado lanza ConflictoExcepcion")
    void crearCategoria_nombreDuplicado_lanzaConflicto() {
        when(categoriaRepositorio.existePorNombre("Eléctricos")).thenReturn(true);

        assertThatThrownBy(() ->
                productoServicio.crearCategoria("Eléctricos", "Repuestos eléctricos"))
                .isInstanceOf(ConflictoExcepcion.class)
                .hasMessageContaining("Eléctricos");

        verify(categoriaRepositorio, never()).save(any());
    }

    @Test
    @DisplayName("Listar productos con stock bajo mínimo")
    void listarConStockBajoMinimo_retornaProductosConStockBajo() {
        Producto productoStockBajo = new Producto();
        productoStockBajo.setStockActual(2);
        productoStockBajo.setStockMinimo(5);

        when(productoRepositorio.listarConStockBajoMinimo())
                .thenReturn(List.of(productoStockBajo));
        when(productoMapper.aDTO(productoStockBajo)).thenReturn(respuestaPrueba);

        List<ProductoRespuestaDTO> resultado =
                productoServicio.listarConStockBajoMinimo();

        assertThat(resultado).hasSize(1);
        verify(productoRepositorio).listarConStockBajoMinimo();
    }
}