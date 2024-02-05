package com.tiendadbii.tiendadbii.controller;

import com.tiendadbii.tiendadbii.dto.CompraDto;
import com.tiendadbii.tiendadbii.dto.DetalleVentaDto;
import com.tiendadbii.tiendadbii.dto.VentaDto;
import com.tiendadbii.tiendadbii.model.entity.DetalleVenta;
import com.tiendadbii.tiendadbii.model.entity.Empleado;
import com.tiendadbii.tiendadbii.model.entity.Producto;
import com.tiendadbii.tiendadbii.model.entity.Venta;
import com.tiendadbii.tiendadbii.model.service.interfaces.IEmpleadoService;
import com.tiendadbii.tiendadbii.model.service.interfaces.IProductoService;
import com.tiendadbii.tiendadbii.model.service.interfaces.IVentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/venta")
@Validated
@Tag(name = "Venta", description = "API Simple operations")
@Log4j2
public class VentaApi {
  private final IVentaService ventaService;
  private final IEmpleadoService empleadoService;
  private final ModelMapper modelMapper;
  private final IProductoService productoService;

  @Operation(summary = "Find Venta with given ID", description = "Given an nroVenta, it will return Venta from DB",
    responses = {
      @ApiResponse(responseCode = "200", description = "Venta found successfully",
        content = {@Content(schema = @Schema(implementation = CompraDto.class))}),
      @ApiResponse(responseCode = "404", description = "Venta not found", content = @Content),
    })
  @GetMapping("/{nroVenta}")
  public ResponseEntity<VentaDto> findById(@PathVariable Integer nroVenta) {
    Venta venta = ventaService.findById(nroVenta);
    if (venta == null) {
      return ResponseEntity.notFound().build();
    }

    modelMapper.getConfiguration().setAmbiguityIgnored(true);
    modelMapper.createTypeMap(DetalleVenta.class, DetalleVentaDto.class).addMapping(srcEntity -> srcEntity.getProducto().getCodigoProducto(), DetalleVentaDto::setCodigoProducto);
    return ResponseEntity.ok().body(this.toDto(venta));
  }

  @Operation(summary = "List all registered Venta", description = "The serach is performed in DB without any restrictions")
  @GetMapping
  public ResponseEntity<List<VentaDto>> findAll() {
    modelMapper.getConfiguration().setAmbiguityIgnored(true);
    modelMapper.createTypeMap(DetalleVenta.class, DetalleVentaDto.class).addMapping(srcEntity -> srcEntity.getProducto().getCodigoProducto(), DetalleVentaDto::setCodigoProducto);

    return ResponseEntity.ok().body(ventaService.findAll().stream().map(this::toDto).toList());
  }

  @Operation(
    summary = "Create new Venta",
    description = """
      To create a new Venta, provide the necessary fields in the request body.\s
      If cliente.idCliente exists in the database, the sale will be saved with the customer data stored in the database, and all other fields in the request will be ignored.\s
      Otherwise if not exists, a new Cliente will be created.""",
    responses = {
      @ApiResponse(responseCode = "201", description = "Venta created succesfully, returned Venta",
        content = {@Content(schema = @Schema(implementation = VentaDto.class))}),
      @ApiResponse(responseCode = "400", description = "Bad request, invalid field"),
      @ApiResponse(responseCode = "404", description = "Not found, resource not found")
    })
  @PostMapping("/empleado/{idEmpleado}")
  public ResponseEntity<?> createVenta(@RequestBody @Valid VentaDto dto, @PathVariable Integer idEmpleado) {
    Empleado empleado = empleadoService.findById(idEmpleado);
    if (empleado == null)
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Empleado with idEmpleado: " + idEmpleado + " not found");
    for (DetalleVentaDto venta : dto.getListaDetalleVenta()) {
      if (venta.getCodigoProducto() == null)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Each DetalleVenta must have a codigoProducto");
    }


    Venta venta = modelMapper.map(dto, Venta.class);
    venta.setEmpleado(empleado);

    for (DetalleVentaDto detalleVentaDto : dto.getListaDetalleVenta()) {
      DetalleVenta detalleVenta = modelMapper.map(detalleVentaDto, DetalleVenta.class);

      Producto producto = productoService.findById(detalleVentaDto.getCodigoProducto());
      if (producto == null)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Producto with codigoProducto: " + detalleVentaDto.getCodigoProducto() + " not found");

      detalleVenta.setProducto(producto);
    }

    Venta ventaPersisted = ventaService.createNew(venta);

    modelMapper.getConfiguration().setAmbiguityIgnored(true);
    modelMapper.createTypeMap(DetalleVenta.class, DetalleVentaDto.class).addMapping(srcEntity -> srcEntity.getProducto().getCodigoProducto(), DetalleVentaDto::setCodigoProducto);
    VentaDto ventaDto = this.toDto(ventaPersisted);


    return ResponseEntity
      .status(HttpStatus.CREATED)
      .header("Location", "/api/venta/" + ventaDto.getNroVenta())
      .body(ventaDto);
  }

  private Venta toEntity(VentaDto dto) {
    Venta venta = modelMapper.map(dto, Venta.class);
    if (dto.getListaDetalleVenta() != null) {
      List<DetalleVenta> listaDetalleVenta = dto.getListaDetalleVenta().stream().map(detalleVentaDto -> {
        DetalleVenta detalleVenta = modelMapper.map(detalleVentaDto, DetalleVenta.class);
        detalleVenta.setProducto(productoService.findById(detalleVentaDto.getCodigoProducto()));

        return detalleVenta;
      }).toList();
      venta.setListaDetalleVenta(listaDetalleVenta);
    }

    return venta;
  }

  private VentaDto toDto(Venta entity) {
    VentaDto ventaDto = modelMapper.map(entity, VentaDto.class);
    if (entity.getListaDetalleVenta() != null) {
      List<DetalleVentaDto> listaDetalleVentaDto = entity.getListaDetalleVenta().stream().map(detalleVenta -> {
        detalleVenta.setVenta(null);
        return modelMapper.map(detalleVenta, DetalleVentaDto.class);
      }).toList();
      ventaDto.setListaDetalleVenta(listaDetalleVentaDto);
    }
    return ventaDto;
  }
}