package tecnomovil;

import java.time.LocalDateTime;

/**
 * Un registro de validación del sistema de transporte.
 *
 * Es un record: sus campos son finales y no tiene setters, así que una vez
 * creado no se puede modificar. Eso nos da la inmutabilidad que pide el caso.
 */
public record Registro(
        String idUsuario,
        String ruta,
        String estacion,
        String accion,          // "entrada" o "salida"
        LocalDateTime timestamp) {
}
