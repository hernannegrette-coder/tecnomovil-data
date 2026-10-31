package tecnomovil;

import java.util.List;
import java.util.Map;

/**
 * Programa de demostración del módulo funcional de TecnoMovil Data.
 */
public class Main {

    private static final int USUARIOS = 250_000;
    private static final long SEMILLA = 2026L;

    /** Capacidad simulada: entradas por hora que una ruta soporta sin saturarse. */
    private static final long UMBRAL_POR_HORA = 8_000L;

    public static void main(String[] args) {
        List<Registro> registros = GeneradorDatos.generar(USUARIOS, SEMILLA);

        System.out.println("=== TecnoMovil Data: procesamiento funcional ===");
        System.out.printf("Usuarios simulados: %,d%n", USUARIOS);
        System.out.printf("Registros generados: %,d%n%n", registros.size());

        demostrarInmutabilidad(registros);

        // a) Afluencia por estación
        System.out.println("a) Afluencia por estación (entradas del día)");
        AnalizadorTransporte.topN(AnalizadorTransporte.afluenciaPorEstacion(registros), Integer.MAX_VALUE)
                .forEach(e -> System.out.printf("   %-16s %,10d%n", e.getKey(), e.getValue()));

        // b) Horas pico
        System.out.println("\nb) Horas pico (las 4 de mayor flujo)");
        AnalizadorTransporte.horasPico(registros, 4)
                .forEach(e -> System.out.printf("   %02d:00 - %02d:59 %,10d entradas%n",
                        e.getKey(), e.getKey(), e.getValue()));

        // c) Rutas más utilizadas
        System.out.println("\nc) Rutas más utilizadas");
        AnalizadorTransporte.rutasMasUtilizadas(registros, 5)
                .forEach(e -> System.out.printf("   %-4s %,10d usos%n", e.getKey(), e.getValue()));

        // d) Patrones de viaje por usuario (se muestran solo los primeros 5)
        System.out.println("\nd) Patrones de viaje por usuario (primeros 5)");
        AnalizadorTransporte.patronesDeViaje(registros).entrySet().stream()
                .limit(5)
                .forEach(e -> System.out.println("   " + e.getKey() + " -> " + e.getValue()));

        // e) Tiempo promedio entre estaciones
        System.out.println("\ne) Tiempo promedio entre estaciones (primeros 8 pares)");
        AnalizadorTransporte.tiempoPromedioEntreEstaciones(registros).entrySet().stream()
                .limit(8)
                .forEach(e -> System.out.printf("   %-34s %5.1f min%n", e.getKey(), e.getValue()));
        System.out.printf("   Promedio general de un tramo: %.1f min%n",
                AnalizadorTransporte.tiempoPromedioGeneral(registros));

        // f) Rutas críticas
        System.out.printf("%nf) Detección de sobrecarga (umbral: %,d entradas por hora)%n", UMBRAL_POR_HORA);
        AnalizadorTransporte.resumenDeRutas(registros, UMBRAL_POR_HORA)
                .forEach(r -> System.out.printf("   %-4s pico de %,7d entradas/hora -> %s%n",
                        r.ruta(), r.maxEntradasPorHora(), r.critica() ? "CRÍTICA" : "normal"));

        compararSecuencialConParalelo(registros);
    }

    /** Las colecciones no se pueden cambiar: cualquier intento lanza una excepción. */
    private static void demostrarInmutabilidad(List<Registro> registros) {
        try {
            registros.add(registros.get(0));
            System.out.println("ERROR: la lista se pudo modificar");
        } catch (UnsupportedOperationException ex) {
            System.out.println("La lista de registros es inmutable (add lanzó UnsupportedOperationException).\n");
        }
    }

    /**
     * Misma operación con stream secuencial y paralelo. Como la función no
     * tiene efectos secundarios, los dos resultados tienen que ser iguales.
     * Los tiempos cambian de una máquina a otra.
     */
    private static void compararSecuencialConParalelo(List<Registro> registros) {
        // Una pasada previa para que la JVM se caliente antes de medir.
        AnalizadorTransporte.afluenciaPorEstacion(registros);
        AnalizadorTransporte.afluenciaPorEstacionParalela(registros);

        long t0 = System.nanoTime();
        Map<String, Long> secuencial = AnalizadorTransporte.afluenciaPorEstacion(registros);
        long t1 = System.nanoTime();
        Map<String, Long> paralelo = AnalizadorTransporte.afluenciaPorEstacionParalela(registros);
        long t2 = System.nanoTime();

        System.out.println("\nComparación secuencial vs paralelo (afluencia por estación)");
        System.out.printf("   Secuencial: %6.1f ms%n", (t1 - t0) / 1_000_000.0);
        System.out.printf("   Paralelo:   %6.1f ms%n", (t2 - t1) / 1_000_000.0);
        System.out.println("   Resultados iguales: " + secuencial.equals(paralelo));
    }
}
