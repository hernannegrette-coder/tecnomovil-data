package tecnomovil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Genera datos simulados para el caso de estudio.
 *
 * Cada usuario se genera solo a partir de su número y de la semilla, así que
 * el resultado es siempre el mismo para los mismos parámetros y cada usuario
 * se puede generar de forma independiente (apto para streams paralelos).
 */
public final class GeneradorDatos {

    private GeneradorDatos() {
    }

    /** Estaciones de cada ruta, en el orden en que las recorre el bus. */
    public static final Map<String, List<String>> RUTAS = Map.of(
            "T1", List.of("Terminal Norte", "Parque Central", "Hospital", "Universidad", "Plaza Mayor"),
            "T2", List.of("Plaza Mayor", "Mercado", "Estadio", "Parque Central", "Terminal Sur"),
            "T3", List.of("Terminal Sur", "Biblioteca", "Hospital", "Zona Industrial", "Aeropuerto"),
            "T4", List.of("Universidad", "Museo", "Mercado", "Estadio", "Terminal Norte"),
            "T5", List.of("Aeropuerto", "Zona Industrial", "Museo", "Biblioteca", "Plaza Mayor"));

    /** Las rutas T1 y T2 se repiten más veces para que tengan más demanda. */
    private static final List<String> RUTAS_PONDERADAS = List.of(
            "T1", "T1", "T1", "T1", "T2", "T2", "T2", "T3", "T3", "T4", "T5");

    /** Cada hora aparece tantas veces como pese; las horas pico pesan más. */
    private static final List<Integer> HORAS_PONDERADAS = IntStream.range(5, 22)
            .boxed()
            .flatMap(hora -> Stream.generate(() -> hora).limit(peso(hora)))
            .toList();

    private static int peso(int hora) {
        return switch (hora) {
            case 7, 8, 17, 18 -> 14;     // horas pico
            case 6, 9, 16, 19 -> 6;
            case 5, 10, 11, 12, 13, 14, 15, 20, 21 -> 3;
            default -> 1;
        };
    }

    /**
     * Devuelve una lista inmutable de registros ordenada por timestamp.
     *
     * @param usuarios cantidad de usuarios a simular
     * @param semilla  semilla para que los datos se puedan repetir
     */
    public static List<Registro> generar(int usuarios, long semilla) {
        LocalDate fecha = LocalDate.of(2026, 10, 31);
        return IntStream.range(0, usuarios)
                .boxed()
                .flatMap(i -> registrosDeUsuario(i, semilla, fecha))
                .sorted(Comparator.comparing(Registro::timestamp))
                .toList();
    }

    private static Stream<Registro> registrosDeUsuario(int numero, long semilla, LocalDate fecha) {
        Random azar = new Random(semilla + numero);
        String id = String.format("U%06d", numero);

        int hora = HORAS_PONDERADAS.get(azar.nextInt(HORAS_PONDERADAS.size()));
        LocalDateTime inicio = fecha.atTime(hora, azar.nextInt(60), azar.nextInt(60));

        boolean hayTransbordo = azar.nextInt(100) < 25;

        Stream<Registro> primerViaje = viaje(id, azar, inicio);
        Stream<Registro> segundoViaje = hayTransbordo
                ? viaje(id, azar, inicio.plusMinutes(50 + azar.nextInt(41)))
                : Stream.empty();

        return Stream.concat(primerViaje, segundoViaje);
    }

    /** Un viaje son dos registros: entrada en una estación y salida en otra de la misma ruta. */
    private static Stream<Registro> viaje(String id, Random azar, LocalDateTime inicio) {
        String ruta = RUTAS_PONDERADAS.get(azar.nextInt(RUTAS_PONDERADAS.size()));
        List<String> estaciones = RUTAS.get(ruta);

        int origen = azar.nextInt(estaciones.size());
        int destino = (origen + 1 + azar.nextInt(estaciones.size() - 1)) % estaciones.size();

        // Más estaciones de distancia, más minutos de viaje.
        long minutos = 4L * Math.abs(destino - origen) + 2 + azar.nextInt(4);

        return Stream.of(
                new Registro(id, ruta, estaciones.get(origen), "entrada", inicio),
                new Registro(id, ruta, estaciones.get(destino), "salida", inicio.plusMinutes(minutos)));
    }
}
