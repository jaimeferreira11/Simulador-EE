package py.simulador.admin;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * DTOs para los ABMs del Admin de Plataforma.
 * Records inmutables — sin dependencia de modelos generados OpenAPI.
 */
public final class AdminDtos {

    private AdminDtos() {}

    // --- Common ---

    public record EstadoRequest(boolean activo) {}

    // =====================================================================
    // USUARIOS
    // =====================================================================

    public record UsuarioRow(
            Long id, String email, String nombreCompleto,
            String rol, boolean activo, OffsetDateTime createdAt) {}

    public record UsuarioDetail(
            Long id, String email, String nombreCompleto,
            String rol, boolean activo, boolean emailVerificado,
            OffsetDateTime createdAt, OffsetDateTime lastLoginAt) {}

    public record UsuarioCreateRequest(
            @NotBlank String email,
            @NotBlank @Size(min = 8) String password,
            @NotBlank String nombreCompleto,
            @NotBlank String rolCodigo) {}

    public record UsuarioUpdateRequest(
            String nombreCompleto,
            @Size(min = 8) String password,
            String rolCodigo) {}

    // =====================================================================
    // RUBROS
    // =====================================================================

    public record RubroRow(
            Long id, String codigo, String nombre,
            boolean activo, OffsetDateTime createdAt) {}

    public record RubroDetail(
            Long id, String codigo, String nombre,
            String descripcion, boolean activo, OffsetDateTime createdAt) {}

    public record RubroRequest(
            @NotBlank String codigo,
            @NotBlank String nombre,
            String descripcion) {}

    // =====================================================================
    // PARAMETROS MACRO
    // =====================================================================

    public record ParamMacroRow(
            Long id, String nombreSet, LocalDate vigenteDesde,
            boolean activo, OffsetDateTime createdAt) {}

    public record ParamMacroDetail(
            Long id, String nombreSet, LocalDate vigenteDesde,
            long salarioMinimoQ1, long salarioMinimoQ4,
            BigDecimal ipsPatronal, BigDecimal ipsTrabajador,
            BigDecimal aguinaldoFactor, BigDecimal tasaIre, BigDecimal ivaGeneral,
            boolean activo, OffsetDateTime createdAt) {}

    public record ParamMacroRequest(
            @NotBlank String nombreSet,
            LocalDate vigenteDesde,
            @PositiveOrZero long salarioMinimoQ1,
            @PositiveOrZero long salarioMinimoQ4,
            @NotNull BigDecimal ipsPatronal,
            @NotNull BigDecimal ipsTrabajador,
            @NotNull BigDecimal aguinaldoFactor,
            @NotNull BigDecimal tasaIre,
            @NotNull BigDecimal ivaGeneral) {}

    public record MacroTrimestreDto(
            Long id, int trimestre,
            @NotNull BigDecimal inflacionTrim,
            @NotNull BigDecimal tipoCambio,
            @NotNull BigDecimal tpmAnual) {}

    // =====================================================================
    // PARAMETROS RUBRO
    // =====================================================================

    public record ParamRubroRow(
            Long id, String codigo, Long rubroId, String rubroNombre,
            long demandaBaseTrim, long precioReferencia,
            boolean activo, OffsetDateTime createdAt) {}

    public record ParamRubroDetail(
            Long id, String codigo, Long rubroId, String rubroNombre,
            long demandaBaseTrim, long precioReferencia,
            BigDecimal elasticidadPrecio, BigDecimal elasticidadMarketing, BigDecimal elasticidadCalidad,
            BigDecimal pesoPrecio, BigDecimal pesoMarketing, BigDecimal pesoCalidad, BigDecimal pesoMarca,
            long costoUnitMp, BigDecimal pctMpImportada,
            long costosFijosTrim, BigDecimal depreciacionTrim, long costoExpansionCapacidad,
            long salarioPromedioSector, long productividadEmpleado,
            BigDecimal brandEquityInicial, BigDecimal decaimientoBe, BigDecimal spreadTasa,
            boolean activo, OffsetDateTime createdAt) {}

    public record ParamRubroRequest(
            @NotBlank String codigo,
            @NotNull Long rubroId,
            @Positive long demandaBaseTrim,
            @Positive long precioReferencia,
            @NotNull BigDecimal elasticidadPrecio,
            @NotNull BigDecimal elasticidadMarketing,
            @NotNull BigDecimal elasticidadCalidad,
            @NotNull BigDecimal pesoPrecio,
            @NotNull BigDecimal pesoMarketing,
            @NotNull BigDecimal pesoCalidad,
            @NotNull BigDecimal pesoMarca,
            @PositiveOrZero long costoUnitMp,
            @NotNull BigDecimal pctMpImportada,
            @PositiveOrZero long costosFijosTrim,
            @NotNull BigDecimal depreciacionTrim,
            @PositiveOrZero long costoExpansionCapacidad,
            @PositiveOrZero long salarioPromedioSector,
            @PositiveOrZero long productividadEmpleado,
            @NotNull BigDecimal brandEquityInicial,
            @NotNull BigDecimal decaimientoBe,
            @NotNull BigDecimal spreadTasa) {}

    public record RubroTrimestreDto(
            Long id, int trimestre,
            @NotNull BigDecimal estacionalidad) {}

    // =====================================================================
    // EVENTOS CATALOGO
    // =====================================================================

    public record EventoRow(
            Long id, String codigo, String nombre, String severidad,
            String tipoEfecto, BigDecimal magnitudDefault, short duracionQ,
            Long rubroId, String rubroNombre, boolean activo) {}

    public record EventoDetail(
            Long id, String codigo, String nombre, String descripcion,
            String severidad, String tipoEfecto,
            BigDecimal magnitudDefault, short duracionQ,
            boolean requiereAnuncioPrevio,
            BigDecimal overridePesoPrecio, BigDecimal overridePesoMarketing,
            BigDecimal overridePesoCalidad, BigDecimal overridePesoMarca,
            Long rubroId, String rubroNombre, boolean activo) {}

    public record EventoRequest(
            @NotBlank String codigo,
            @NotBlank String nombre,
            String descripcion,
            @NotBlank String severidad,
            @NotBlank String tipoEfecto,
            @NotNull BigDecimal magnitudDefault,
            @Positive short duracionQ,
            boolean requiereAnuncioPrevio,
            BigDecimal overridePesoPrecio,
            BigDecimal overridePesoMarketing,
            BigDecimal overridePesoCalidad,
            BigDecimal overridePesoMarca,
            Long rubroId) {}

    // =====================================================================
    // ENTIDADES
    // =====================================================================

    public record EntidadRow(
            Long id, String nombre, String tipo,
            String contactoEmail, boolean activa, OffsetDateTime createdAt) {}

    public record EntidadDetail(
            Long id, String nombre, String tipo, String descripcion,
            String contactoNombre, String contactoEmail,
            boolean activa, OffsetDateTime createdAt) {}

    public record EntidadRequest(
            @NotBlank String nombre,
            @NotBlank String tipo,
            String descripcion,
            String contactoNombre,
            @Email String contactoEmail) {}
}
