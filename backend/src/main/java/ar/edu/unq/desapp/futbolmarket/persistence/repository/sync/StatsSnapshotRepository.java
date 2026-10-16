package ar.edu.unq.desapp.futbolmarket.persistence.repository.sync;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.springframework.stereotype.Repository;

import ar.edu.unq.desapp.futbolmarket.config.WhoScoredProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotRead;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSnapshot;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.sync.StatsSnapshotLineMapper;
import tools.jackson.core.JacksonException;

/**
 * El snapshot de estadísticas en su archivo, con la ruta de
 * {@code futbolmarket.whoscored.snapshot.path} (research D20 y D21).
 *
 * <p>Se lee entero antes de usarlo: un archivo con una línea que no se puede leer se ignora entero,
 * nunca se carga a medias (FR-027). Se escribe a un temporal en la misma carpeta y se mueve sobre el
 * vigente, así nunca queda un archivo a medio escribir.</p>
 */
@Repository
public class StatsSnapshotRepository {
    private static final String LINE_END = "\n";
    private static final String TEMPORARY_SUFFIX = ".tmp";
    private static final String HEADER_PROBLEM = "el encabezado no es el del formato vigente";

    private final Path path;
    private final StatsSnapshotLineMapper lineMapper;

    public StatsSnapshotRepository(WhoScoredProperties properties, StatsSnapshotLineMapper lineMapper) {
        this.path = Path.of(properties.snapshot().path());
        this.lineMapper = lineMapper;
    }

    public SnapshotRead read() {
        if (!Files.exists(path)) {
            return SnapshotRead.missing();
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lineMapper.isHeader(lines.getFirst())) {
                return SnapshotRead.unreadable(HEADER_PROBLEM);
            }
            return SnapshotRead.loaded(lineMapper.fromLines(lines.subList(1, lines.size())));
        } catch (IOException e) {
            return SnapshotRead.unreadable("no se pudo leer el archivo: " + e.getMessage());
        } catch (JacksonException e) {
            return SnapshotRead.unreadable("hay una línea que no se puede leer: " + e.getOriginalMessage());
        } catch (CatalogInvariantException e) {
            return SnapshotRead.unreadable(e.getMessage());
        }
    }

    /**
     * Escribe en UTF-8 sin BOM, con LF al final de cada línea, y crea la carpeta si falta.
     */
    public void write(StatsSnapshot snapshot) {
        String content = String.join(LINE_END, lineMapper.toLines(snapshot)) + LINE_END;
        Path directory = path.toAbsolutePath().getParent();
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, path.getFileName().toString(), TEMPORARY_SUFFIX);
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteIfLeft(temporary);
        }
    }

    private static void deleteIfLeft(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
