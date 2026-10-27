package ar.edu.unq.desapp.futbolmarket.persistence.repository.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import ar.edu.unq.desapp.futbolmarket.config.WhoScoredProperties;
import ar.edu.unq.desapp.futbolmarket.config.WhoScoredProperties.Snapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Metric;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotMatchEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotPlayerRow;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotRead;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotTeamEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSnapshot;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.sync.StatsSnapshotLineMapper;

class StatsSnapshotRepositoryTest {

    private static final String FILE_NAME = "whoscored-stats.jsonl";
    private static final byte JSON_OBJECT_START = '{';

    @TempDir
    private Path directory;

    @Test
    void leeElSnapshotDeEjemploConSusEntradas() throws IOException {
        Path file = copyOfSample();

        SnapshotRead read = repositoryFor(file).read();

        assertThat(read.status()).isEqualTo(SnapshotStatus.LOADED);
        StatsSnapshot snapshot = read.snapshot();
        assertThat(snapshot.teams()).containsExactly(
                new SnapshotTeamEntry(League.PREMIER, "67", "23"),
                new SnapshotTeamEntry(League.PREMIER, "341", "19"));
        assertThat(snapshot.matches()).containsExactly(
                new SnapshotMatchEntry(League.PREMIER, "537800", "1983600"),
                new SnapshotMatchEntry(League.PREMIER, "537809", "1983609"));
        assertThat(snapshot.stats()).singleElement().satisfies(stats -> {
            assertThat(stats.matchExternalId()).isEqualTo("537800");
            assertThat(stats.players()).hasSize(2);
            SnapshotPlayerRow first = stats.players().getFirst();
            assertThat(first.playerExternalId()).isEqualTo("3754");
            assertThat(first.whoscoredId()).isEqualTo("141469");
            assertThat(first.starter()).isTrue();
            assertThat(first.minutesPlayed()).isEqualTo(80);
            assertThat(first.counts().get(Metric.GOALS)).isEqualTo(2);
            assertThat(first.counts().get(Metric.FOULS_COMMITTED)).isEqualTo(1);
            assertThat(first.counts().get(Metric.RED_CARDS)).isZero();
            assertThat(first.rating()).isEqualTo(new BigDecimal("8.95"));
            assertThat(first.manOfTheMatch()).isTrue();
        });
    }

    @Test
    void unArchivoQueNoExisteEsUnSnapshotQueFalta() {
        SnapshotRead read = repositoryFor(directory.resolve(FILE_NAME)).read();

        assertThat(read.status()).isEqualTo(SnapshotStatus.MISSING);
        assertThat(read.snapshot()).isEqualTo(StatsSnapshot.empty());
    }

    @Test
    void unaLineaRotaEnElMedioHaceQueSeIgnoreEntero() throws IOException {
        List<String> lines = sampleLines();
        lines.set(3, "{\"type\":\"match\",\"league\":\"PREMIER\",");
        Path file = write(lines);

        SnapshotRead read = repositoryFor(file).read();

        assertThat(read.status()).isEqualTo(SnapshotStatus.UNREADABLE);
        assertThat(read.snapshot()).isEqualTo(StatsSnapshot.empty());
        assertThat(read.problem()).isNotBlank();
    }

    @Test
    void otroEncabezadoHaceQueSeIgnoreEntero() throws IOException {
        List<String> lines = sampleLines();
        lines.set(0, "{\"format\":\"otro-formato\",\"version\":1,\"playerColumns\":[]}");
        Path file = write(lines);

        SnapshotRead read = repositoryFor(file).read();

        assertThat(read.status()).isEqualTo(SnapshotStatus.UNREADABLE);
    }

    @Test
    void otraVersionHaceQueSeIgnoreEntero() throws IOException {
        List<String> lines = sampleLines();
        lines.set(0, lines.getFirst().replace("\"version\":1", "\"version\":2"));
        Path file = write(lines);

        SnapshotRead read = repositoryFor(file).read();

        assertThat(read.status()).isEqualTo(SnapshotStatus.UNREADABLE);
    }

    @Test
    void unaClaveRepetidaHaceQueSeIgnoreEntero() throws IOException {
        List<String> lines = sampleLines();
        lines.add(2, lines.get(1));
        Path file = write(lines);

        SnapshotRead read = repositoryFor(file).read();

        assertThat(read.status()).isEqualTo(SnapshotStatus.UNREADABLE);
        assertThat(read.problem()).isEqualTo("El snapshot tiene dos entradas team con el id 67.");
    }

    @Test
    void unaFilaDeJugadorConMenosColumnasHaceQueSeIgnoreEntero() throws IOException {
        List<String> lines = sampleLines();
        lines.set(5, lines.get(5).replace(",8.95,1]", "]"));
        Path file = write(lines);

        SnapshotRead read = repositoryFor(file).read();

        assertThat(read.status()).isEqualTo(SnapshotStatus.UNREADABLE);
    }

    @Test
    void leerYEscribirElEjemploDaElMismoArchivoByteAByteConLfYSinBom() throws IOException {
        Path source = copyOfSample();
        Path target = directory.resolve("salida").resolve(FILE_NAME);
        StatsSnapshot snapshot = repositoryFor(source).read().snapshot();

        repositoryFor(target).write(snapshot);

        byte[] written = Files.readAllBytes(target);
        assertThat(written).isEqualTo(Files.readAllBytes(source));
        assertThat(new String(written, StandardCharsets.UTF_8)).doesNotContain("\r");
        assertThat(written[0]).as("el archivo empieza por el encabezado, sin BOM").isEqualTo(JSON_OBJECT_START);
    }

    @Test
    void laEscrituraCreaLaCarpetaYNoDejaTemporales() throws IOException {
        Path folder = directory.resolve("snapshot").resolve("nueva");
        Path target = folder.resolve(FILE_NAME);

        repositoryFor(target).write(StatsSnapshot.empty());

        try (Stream<Path> files = Files.list(folder)) {
            assertThat(files).containsExactly(target);
        }
        assertThat(Files.readAllLines(target)).hasSize(1);
    }

    @Test
    void escribirPisaElArchivoVigente() throws IOException {
        Path target = copyOfSample();

        repositoryFor(target).write(StatsSnapshot.empty());

        assertThat(Files.readAllLines(target)).hasSize(1);
        assertThat(repositoryFor(target).read().snapshot()).isEqualTo(StatsSnapshot.empty());
    }

    private Path copyOfSample() throws IOException {
        Path file = directory.resolve(FILE_NAME);
        Files.write(file, new ClassPathResource("whoscored/snapshot-sample.jsonl").getContentAsByteArray());
        return file;
    }

    private static List<String> sampleLines() throws IOException {
        String content = new ClassPathResource("whoscored/snapshot-sample.jsonl")
                .getContentAsString(StandardCharsets.UTF_8);
        return new ArrayList<>(content.lines().toList());
    }

    private Path write(List<String> lines) throws IOException {
        Path file = directory.resolve(FILE_NAME);
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return file;
    }

    private static StatsSnapshotRepository repositoryFor(Path file) {
        return new StatsSnapshotRepository(properties(file), new StatsSnapshotLineMapper());
    }

    private static WhoScoredProperties properties(Path file) {
        return new WhoScoredProperties(URI.create("http://whoscored.invalid"), "agente", "text/html", "en-US",
                Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(4), Duration.ofSeconds(30),
                new Snapshot(file.toString()));
    }
}
