package py.simulador.usuario.importacion;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import py.simulador.api.generated.model.UsuarioCreate;
import py.simulador.auth.PasswordResetService;
import py.simulador.common.BusinessValidationException;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Importación masiva de usuarios a partir de un CSV.
 *
 * <p>Diseño de transacciones (CRUCIAL para el éxito parcial): este servicio
 * <strong>NO</strong> es {@code @Transactional}. Cada fila se crea invocando
 * {@link UsuarioService#create(UsuarioCreate)}, que sí es {@code @Transactional}
 * (propagación REQUIRED por defecto). Como el método de importación corre sin
 * transacción activa, cada {@code create()} abre y confirma su <em>propia</em>
 * transacción independiente. Si una fila falla, solo se revierte esa fila y la
 * excepción se captura aquí; las demás filas ya quedaron confirmadas. Así el
 * éxito parcial funciona de verdad.
 *
 * <p>Se reutiliza por completo {@code UsuarioService.create()}, de modo que la
 * política de roles ({@code enforceRolCreationPolicy}), el hash BCrypt y la
 * unicidad de email se aplican igual que en la creación individual.
 */
@Service
public class UsuarioImportService {

    /** Máximo de filas de datos admitidas en un solo import. */
    static final int MAX_FILAS = 500;

    /** Rol por defecto cuando la columna {@code rol} está ausente o vacía. */
    private static final String ROL_DEFAULT = "JUGADOR";

    /** Validación básica de email (la validación estricta vive en el DTO/Bean Validation). */
    private static final Pattern EMAIL_RX =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioService usuarioService;
    private final PasswordResetService passwordResetService;

    public UsuarioImportService(UsuarioService usuarioService,
                                PasswordResetService passwordResetService) {
        this.usuarioService = usuarioService;
        this.passwordResetService = passwordResetService;
    }

    /**
     * Procesa el CSV y crea un usuario por fila de datos, de forma independiente.
     *
     * @throws BusinessValidationException si el archivo es inválido como un todo
     *         (vacío, sin cabecera, columnas obligatorias ausentes, o excede {@link #MAX_FILAS}).
     */
    public ImportResultDto importar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessValidationException("El archivo CSV esta vacio");
        }

        List<String[]> filas = leerCsv(file);
        if (filas.isEmpty()) {
            throw new BusinessValidationException("El archivo CSV no tiene cabecera ni datos");
        }

        ColumnIndex cols = ColumnIndex.fromHeader(filas.get(0));
        List<String[]> datos = filas.subList(1, filas.size());

        if (datos.size() > MAX_FILAS) {
            throw new BusinessValidationException(
                    "El CSV excede el maximo de " + MAX_FILAS + " filas (recibidas: " + datos.size() + ")");
        }

        List<ImportResultDto.Creado> creados = new ArrayList<>();
        List<ImportResultDto.ErrorFila> errores = new ArrayList<>();

        for (int i = 0; i < datos.size(); i++) {
            int fila = i + 1; // 1-based sobre filas de DATOS
            String[] campos = datos.get(i);
            String email = cols.email >= 0 && cols.email < campos.length ? campos[cols.email].trim() : "";
            String nombre = cols.nombre >= 0 && cols.nombre < campos.length ? campos[cols.nombre].trim() : "";
            String rol = cols.rol >= 0 && cols.rol < campos.length ? campos[cols.rol].trim() : "";
            if (rol.isBlank()) {
                rol = ROL_DEFAULT;
            }

            try {
                UsuarioEntity creado = procesarFila(email, nombre, rol);
                creados.add(new ImportResultDto.Creado(fila, email, creado.getId()));
                // Email de set-password (flujo de reset) para que el usuario pueda
                // iniciar sesion. Best-effort: el metodo se traga cualquier fallo,
                // asi que la fila sigue contando como "creada" aunque el email falle.
                passwordResetService.enviarSetPasswordInicial(creado);
            } catch (RuntimeException ex) {
                errores.add(new ImportResultDto.ErrorFila(fila, email, motivo(ex)));
            }
        }

        return new ImportResultDto(datos.size(), creados, errores);
    }

    /**
     * Valida una fila y delega en {@link UsuarioService#create(UsuarioCreate)},
     * que corre en su propia transacción. Cualquier fallo (email duplicado,
     * política de rol, rol desconocido) se propaga como excepción y se reporta
     * a nivel de fila sin abortar el lote.
     */
    private UsuarioEntity procesarFila(String email, String nombre, String rol) {
        if (email.isBlank()) {
            throw new BusinessValidationException("Falta el email");
        }
        if (!EMAIL_RX.matcher(email).matches()) {
            throw new BusinessValidationException("Email invalido");
        }
        if (nombre.isBlank()) {
            throw new BusinessValidationException("Falta el nombre_completo");
        }

        UsuarioCreate.RolCodigoEnum rolEnum;
        try {
            rolEnum = UsuarioCreate.RolCodigoEnum.fromValue(rol);
        } catch (IllegalArgumentException ex) {
            throw new BusinessValidationException("Rol desconocido: " + rol);
        }

        UsuarioCreate input = new UsuarioCreate()
                .email(email)
                .password(generarPasswordTemporal())
                .nombreCompleto(nombre)
                .rolCodigo(rolEnum);

        return usuarioService.create(input);
    }

    /**
     * Genera una contraseña temporal robusta (>= 8 chars). El CSV no incluye
     * contraseña; el usuario la define luego vía el flujo de invitación /
     * restablecimiento. Cumple el {@code @Size(min = 8)} de UsuarioCreate.
     */
    private String generarPasswordTemporal() {
        byte[] buf = new byte[18];
        RANDOM.nextBytes(buf);
        return "Tmp-" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String motivo(RuntimeException ex) {
        String msg = ex.getMessage();
        return (msg == null || msg.isBlank()) ? ex.getClass().getSimpleName() : msg;
    }

    private List<String[]> leerCsv(MultipartFile file) {
        List<String[]> filas = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            boolean primera = true;
            while ((linea = reader.readLine()) != null) {
                // Tolerar BOM al inicio del archivo
                if (primera) {
                    if (!linea.isEmpty() && linea.charAt(0) == '﻿') {
                        linea = linea.substring(1);
                    }
                    primera = false;
                }
                // Tolerar líneas en blanco (incluida una línea final vacía)
                if (linea.isBlank()) {
                    continue;
                }
                filas.add(splitCsvLine(linea));
            }
        } catch (IOException e) {
            throw new BusinessValidationException("No se pudo leer el archivo CSV");
        }
        return filas;
    }

    /** Split CSV sencillo: separador coma, sin soporte de comillas embebidas (no requerido por el formato). */
    private String[] splitCsvLine(String linea) {
        String[] parts = linea.split(",", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    /** Resuelve los índices de columnas a partir de la cabecera (tolerante a mayúsc/minúsc y orden). */
    private record ColumnIndex(int email, int nombre, int rol) {
        static ColumnIndex fromHeader(String[] header) {
            int email = -1, nombre = -1, rol = -1;
            for (int i = 0; i < header.length; i++) {
                String h = header[i].trim().toLowerCase();
                switch (h) {
                    case "email" -> email = i;
                    case "nombre_completo" -> nombre = i;
                    case "rol" -> rol = i;
                    default -> { /* columna ignorada */ }
                }
            }
            if (email < 0 || nombre < 0) {
                throw new BusinessValidationException(
                        "La cabecera del CSV debe incluir las columnas 'email' y 'nombre_completo'");
            }
            return new ColumnIndex(email, nombre, rol);
        }
    }
}
