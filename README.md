# TecnoMovil Data – Procesamiento funcional de datos de transporte

Solución del caso de estudio de la Unidad 2 (Programación funcional y manejo de repositorio), curso Programación Orientada a Objetos II, IU Digital de Antioquia.

La Alcaldía de TecnoValle quiere reemplazar el módulo imperativo que procesa los registros diarios del transporte urbano. Este proyecto es una versión funcional en Java del módulo de procesamiento y agregación: usa Streams, lambdas, funciones puras y colecciones inmutables sobre un conjunto grande de datos simulados.

## Estructura

```
tecnomovil-data/
├── src/tecnomovil/
│   ├── Registro.java              # record inmutable: idUsuario, ruta, estacion, accion, timestamp
│   ├── GeneradorDatos.java        # simula los registros del día (con semilla fija)
│   ├── AnalizadorTransporte.java  # las seis operaciones funcionales del caso
│   └── Main.java                  # ejecuta todo e imprime los resultados
├── .gitignore
└── README.md
```

## Requisitos

- JDK 17 o superior (se probó con JDK 21).

## Compilar y ejecutar

Desde la carpeta raíz del proyecto:

```bash
javac -d out src/tecnomovil/*.java
java -Dstdout.encoding=UTF-8 -cp out tecnomovil.Main
```

El parámetro `-Dstdout.encoding=UTF-8` evita que las tildes salgan como signos de interrogación en algunas terminales.

## Tareas del caso y dónde están

| Tarea | Método en `AnalizadorTransporte` | Idea principal |
|---|---|---|
| a) Afluencia por estación | `afluenciaPorEstacion` | `filter` de entradas + `groupingBy` y `counting` |
| b) Horas pico | `afluenciaPorHora`, `horasPico` | agrupar por hora del timestamp y tomar las mayores |
| c) Rutas más utilizadas | `usoPorRuta`, `rutasMasUtilizadas` | agrupar por ruta y ordenar por volumen |
| d) Patrones de viaje por usuario | `patronesDeViaje` | `groupingBy` por usuario con `mapping` a estación |
| e) Tiempo promedio entre estaciones | `tramos`, `tiempoPromedioEntreEstaciones` | se compara cada registro con el anterior del mismo usuario; `averagingDouble` por par origen-destino |
| f) Sobrecarga en rutas | `resumenDeRutas`, `rutasCriticas` | entradas por ruta y hora; si el máximo supera el umbral, la ruta es crítica |

## Cómo se cumplen las consideraciones técnicas

- **Funciones puras:** ningún método de `AnalizadorTransporte` lee ni escribe estado externo. El resultado depende solo de los argumentos.
- **Inmutabilidad:** `Registro` es un `record` y la lista de datos se crea con `toList()`, que devuelve una lista no modificable. `Main` lo comprueba intentando un `add` y capturando `UnsupportedOperationException`.
- **Lambdas y Streams:** no hay ciclos `for` ni variables acumuladoras. Los predicados y funciones que se repiten (`ES_ENTRADA`, `ES_SALIDA`, `HORA_DEL_DIA`) son lambdas guardadas en constantes.
- **Enfoque declarativo:** cada operación es un solo pipeline que dice qué se quiere obtener.
- **Paralelización:** `afluenciaPorEstacionParalela` hace lo mismo con `parallelStream()` y `groupingByConcurrent`. Como no hay estado compartido, el resultado es igual al secuencial. Con unos 600 mil registros el paralelo no siempre gana, porque repartir el trabajo también cuesta; la ventaja aparece con volúmenes más grandes o con operaciones más pesadas.
- **Sin efectos secundarios:** los métodos solo devuelven valores. La impresión por consola ocurre únicamente en `Main`.

## Datos simulados

`GeneradorDatos.generar(usuarios, semilla)` crea para cada usuario uno o dos viajes (aprox. 25 % hace transbordo). Un viaje son dos registros: `entrada` en una estación y `salida` en otra de la misma ruta. Las horas pico (7, 8, 17 y 18) pesan más, y las rutas T1 y T2 tienen más demanda. Con la misma semilla siempre salen los mismos datos.

Con los valores de `Main` (250 000 usuarios, semilla 2026) salen unos 625 mil registros, y con el umbral simulado de 8 000 entradas por hora las rutas T1 y T2 quedan marcadas como críticas.
