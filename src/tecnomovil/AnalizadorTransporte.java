package tecnomovil;

import static java.util.stream.Collectors.averagingDouble;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.groupingByConcurrent;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Módulo funcional de TecnoMovil Data.
 *
 * Reglas que cumple todo lo que hay en esta clase:
 *  - Los métodos son funciones puras: el resultado depende solo de los
 *    argumentos y no se cambia ningún estado externo.
 *  - Las listas que llegan por parámetro nunca se modifican.
 *  - Todo se resuelve con pipelines de Streams y lambdas, sin ciclos ni
 *    variables acumuladoras.
 */
public final class AnalizadorTransporte {

    private AnalizadorTransporte() {
    }

    // ---------------------------------------------------------------
    // Funciones pequeñas que se reutilizan en varios pipelines
    // ---------------------------------------------------------------

    public static final Predicate<Registro> ES_ENTRADA = r -> "entrada".equals(r.accion());
    public static final Predicate<Registro> ES_SALIDA = r -> "salida".equals(r.accion());
    public static final Function<Registro, Integer> HORA_DEL_DIA = r -> r.timestamp().getHour();

    /** Un trayecto entre dos estaciones consecutivas de un mismo viaje. */
    public record Tramo(String origen, String destino, double minutos) {
    }

    /** Resumen de una ruta: cuánto se usó y si superó el umbral. */
    public record ResumenRuta(String ruta, long usos, long maxEntradasPorHora, boolean critica) {
    }

    // ---------------------------------------------------------------
    // a) Afluencia por estación
    // ---------------------------------------------------------------

    public static Map<String, Long> afluenciaPorEstacion(Collection<Registro> registros) {
        return registros.stream()
                .filter(ES_ENTRADA)
                .collect(groupingBy(Registro::estacion, counting()));
    }

    /** Misma operación en paralelo. Como no hay estado compartido, no hay condiciones de carrera. */
    public static Map<String, Long> afluenciaPorEstacionParalela(Collection<Registro> registros) {
        return registros.parallelStream()
                .filter(ES_ENTRADA)
                .collect(groupingByConcurrent(Registro::estacion, counting()));
    }

    // ---------------------------------------------------------------
    // b) Horas pico
    // ---------------------------------------------------------------

    public static Map<Integer, Long> afluenciaPorHora(Collection<Registro> registros) {
        return registros.stream()
                .filter(ES_ENTRADA)
                .collect(groupingBy(HORA_DEL_DIA, TreeMap::new, counting()));
    }

    public static List<Map.Entry<Integer, Long>> horasPico(Collection<Registro> registros, int cantidad) {
        return topN(afluenciaPorHora(registros), cantidad);
    }

    // ---------------------------------------------------------------
    // c) Rutas más utilizadas
    // ---------------------------------------------------------------

    public static Map<String, Long> usoPorRuta(Collection<Registro> registros) {
        return registros.stream()
                .filter(ES_ENTRADA)
                .collect(groupingBy(Registro::ruta, counting()));
    }

    public static List<Map.Entry<String, Long>> rutasMasUtilizadas(Collection<Registro> registros, int cantidad) {
        return topN(usoPorRuta(registros), cantidad);
    }

    // ---------------------------------------------------------------
    // d) Patrones de viaje por usuario
    // ---------------------------------------------------------------

    /**
     * Para cada usuario, las estaciones en el orden en que aparecen en los
     * registros. La lista de entrada ya viene ordenada por tiempo, y un
     * stream secuencial conserva ese orden dentro de cada grupo.
     */
    public static Map<String, List<String>> patronesDeViaje(List<Registro> registros) {
        return registros.stream()
                .collect(groupingBy(
                        Registro::idUsuario,
                        TreeMap::new,
                        mapping(Registro::estacion, toUnmodifiableList())));
    }

    // ---------------------------------------------------------------
    // e) Tiempo promedio entre estaciones
    // ---------------------------------------------------------------

    /** Todos los tramos entrada -> salida de todos los usuarios. */
    public static List<Tramo> tramos(Collection<Registro> registros) {
        return registros.stream()
                .collect(groupingBy(Registro::idUsuario))
                .values().stream()
                .flatMap(AnalizadorTransporte::tramosDeUsuario)
                .toList();
    }

    private static Stream<Tramo> tramosDeUsuario(List<Registro> delUsuario) {
        List<Registro> ordenados = delUsuario.stream()
                .sorted(Comparator.comparing(Registro::timestamp))
                .toList();

        // Se compara cada registro con el anterior: IntStream.range en lugar de un for.
        return IntStream.range(1, ordenados.size())
                .mapToObj(i -> crearTramo(ordenados.get(i - 1), ordenados.get(i)))
                .flatMap(Optional::stream);
    }

    private static Optional<Tramo> crearTramo(Registro anterior, Registro actual) {
        boolean esUnViaje = ES_ENTRADA.test(anterior)
                && ES_SALIDA.test(actual)
                && anterior.ruta().equals(actual.ruta())
                && !anterior.estacion().equals(actual.estacion());

        return esUnViaje
                ? Optional.of(new Tramo(
                        anterior.estacion(),
                        actual.estacion(),
                        Duration.between(anterior.timestamp(), actual.timestamp()).toSeconds() / 60.0))
                : Optional.empty();
    }

    /** Minutos promedio por par "origen -> destino". */
    public static Map<String, Double> tiempoPromedioEntreEstaciones(Collection<Registro> registros) {
        return tramos(registros).stream()
                .collect(groupingBy(
                        t -> t.origen() + " -> " + t.destino(),
                        TreeMap::new,
                        averagingDouble(Tramo::minutos)));
    }

    public static double tiempoPromedioGeneral(Collection<Registro> registros) {
        return tramos(registros).stream()
                .mapToDouble(Tramo::minutos)
                .average()
                .orElse(0.0);
    }

    // ---------------------------------------------------------------
    // f) Detección de sobrecarga en rutas
    // ---------------------------------------------------------------

    /**
     * La ocupación se simula con las entradas por hora: si en alguna hora una
     * ruta recibe más entradas que el umbral, se marca como crítica.
     */
    public static List<ResumenRuta> resumenDeRutas(Collection<Registro> registros, long umbralPorHora) {
        return registros.stream()
                .filter(ES_ENTRADA)
                .collect(groupingBy(Registro::ruta, groupingBy(HORA_DEL_DIA, counting())))
                .entrySet().stream()
                .map(e -> resumir(e.getKey(), e.getValue(), umbralPorHora))
                .sorted(Comparator.comparingLong(ResumenRuta::maxEntradasPorHora).reversed())
                .toList();
    }

    public static List<String> rutasCriticas(Collection<Registro> registros, long umbralPorHora) {
        return resumenDeRutas(registros, umbralPorHora).stream()
                .filter(ResumenRuta::critica)
                .map(ResumenRuta::ruta)
                .toList();
    }

    private static ResumenRuta resumir(String ruta, Map<Integer, Long> entradasPorHora, long umbral) {
        long usos = entradasPorHora.values().stream().mapToLong(Long::longValue).sum();
        long maximo = entradasPorHora.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        return new ResumenRuta(ruta, usos, maximo, maximo > umbral);
    }

    // ---------------------------------------------------------------
    // Utilidad: los N mayores de un mapa de conteos
    // ---------------------------------------------------------------

    public static <K> List<Map.Entry<K, Long>> topN(Map<K, Long> conteos, int cantidad) {
        return conteos.entrySet().stream()
                .sorted(Map.Entry.<K, Long>comparingByValue().reversed()
                        .thenComparing(e -> e.getKey().toString()))
                .limit(cantidad)
                .toList();
    }
}
