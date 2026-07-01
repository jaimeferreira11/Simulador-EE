package py.simulador.invitacion.importacion;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import py.simulador.common.BusinessValidationException;
import py.simulador.invitacion.InvitacionService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Invitación masiva de miembros a un equipo a partir de un CSV.
 *
 * <p>El modelo del producto es: los jugadores entran SOLO por invitación del
 * moderador (no hay auto-registro). Por eso "cargar miembros en lote" equivale a
 * "invitar en lote" a un equipo concreto, reutilizando por completo la lógica de
 * la invitación individual y TODAS sus validaciones.
 *
 * <p>Diseño de transacciones (CRUCIAL para el éxito parcial), idéntico a
 * {@code UsuarioImportService}: este servicio <strong>NO</strong> es
 * {@code @Transactional}. Cada fila se procesa invocando
 * {@link InvitacionService#invitar(Long, String, String, Long, boolean, Long)},
 * que sí es {@code @Transactional} (propagación REQUIRED por defecto). Como el
 * método de importación corre sin transacción activa, cada {@code invitar()} abre
 * y confirma su <em>propia</em> transacción independiente. Si una fila falla, solo
 * se revierte esa fila; la excepción se captura aquí y el lote continúa.
 *
 * <p>Se reutiliza {@code invitar(...)} sin reimplementar nada: el dedupe de
 * invitación pendiente (devuelve la existente), la validación de "ya pertenece a
 * otro equipo de la misma competencia", el token, el email y la notificación se
 * aplican exactamente igual que en la invitación individual. El área queda en
 * {@code null} y el flag de capitán en {@code false} (no vienen en el CSV).
 */
@Service
public class InvitacionImportService {

    /** Máximo de filas de datos admitidas en un solo import. */
    static final int MAX_FILAS = 500;

    /** Validación básica de email (la validación estricta vive en el alta individual / Bean Validation). */
    private static final Pattern EMAIL_RX =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final InvitacionService invitacionService;

    public InvitacionImportService(InvitacionService invitacionService) {
        this.invitacionService = invitacionService;
    }

    /**
     * Procesa el CSV e invita un miembro por fila de datos, de forma independiente.
     *
     * @param equipoId    equipo destino de las invitaciones
     * @param moderadorId usuario que crea las invitaciones (auditoría)
     * @param file        CSV con cabecera {@code email,nombre_completo}
     * @throws BusinessValidationException si el archivo es inválido como un todo
     *         (vacío, sin cabecera, columnas obligatorias ausentes, o excede {@link #MAX_FILAS}).
     */
    public InvitacionImportResultDto importar(Long equipoId, Long moderadorId, MultipartFile file) {
        // Autorización de propiedad: el moderador solo puede cargar a equipos de sus
        // competencias (ADMIN_PLATAFORMA omite). Se valida UNA vez aquí para que un
        // moderador ajeno reciba 403 global, en lugar de quedar atrapado por fila.
        invitacionService.verificarAccesoEquipo(equipoId, moderadorId);

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

        List<InvitacionImportResultDto.Invitado> invitados = new ArrayList<>();
        List<InvitacionImportResultDto.ErrorFila> errores = new ArrayList<>();

        for (int i = 0; i < datos.size(); i++) {
            int fila = i + 1; // 1-based sobre filas de DATOS
            String[] campos = datos.get(i);
            String email = cols.email >= 0 && cols.email < campos.length ? campos[cols.email].trim() : "";
            String nombre = cols.nombre >= 0 && cols.nombre < campos.length ? campos[cols.nombre].trim() : "";

            try {
                procesarFila(equipoId, moderadorId, email, nombre);
                // Una invitación pendiente preexistente se cuenta como invitada:
                // invitar(...) la devuelve idempotentemente sin crear duplicados.
                invitados.add(new InvitacionImportResultDto.Invitado(fila, email));
            } catch (RuntimeException ex) {
                errores.add(new InvitacionImportResultDto.ErrorFila(fila, email, motivo(ex)));
            }
        }

        return new InvitacionImportResultDto(datos.size(), invitados, errores);
    }

    /**
     * Valida una fila y delega en {@link InvitacionService#invitar}, que corre en su
     * propia transacción. Cualquier fallo (email inválido, jugador ya en otro equipo
     * de la misma competencia, etc.) se propaga como excepción y se reporta a nivel
     * de fila sin abortar el lote. Área = {@code null}, capitán = {@code false}.
     */
    private void procesarFila(Long equipoId, Long moderadorId, String email, String nombre) {
        if (email.isBlank()) {
            throw new BusinessValidationException("Falta el email");
        }
        if (!EMAIL_RX.matcher(email).matches()) {
            throw new BusinessValidationException("Email invalido");
        }
        if (nombre.isBlank()) {
            throw new BusinessValidationException("Falta el nombre_completo");
        }
        invitacionService.invitar(equipoId, email, nombre, null, false, moderadorId);
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
    private record ColumnIndex(int email, int nombre) {
        static ColumnIndex fromHeader(String[] header) {
            int email = -1, nombre = -1;
            for (int i = 0; i < header.length; i++) {
                String h = header[i].trim().toLowerCase();
                switch (h) {
                    case "email" -> email = i;
                    case "nombre_completo" -> nombre = i;
                    default -> { /* columna ignorada */ }
                }
            }
            if (email < 0 || nombre < 0) {
                throw new BusinessValidationException(
                        "La cabecera del CSV debe incluir las columnas 'email' y 'nombre_completo'");
            }
            return new ColumnIndex(email, nombre);
        }
    }
}
