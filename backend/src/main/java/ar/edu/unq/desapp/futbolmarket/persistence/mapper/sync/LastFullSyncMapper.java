package ar.edu.unq.desapp.futbolmarket.persistence.mapper.sync;

import ar.edu.unq.desapp.futbolmarket.modelo.sync.LastFullSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncSource;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.sync.LastFullSyncSQL;
import org.springframework.stereotype.Component;

@Component
public class LastFullSyncMapper {
    public LastFullSync toDomain(LastFullSyncSQL lastFullSync) {
        return new LastFullSync(SyncSource.valueOf(lastFullSync.getSource()), lastFullSync.getFinishedAt());
    }

    public LastFullSyncSQL toSQL(LastFullSync lastFullSync) {
        return new LastFullSyncSQL(lastFullSync.source().name(), lastFullSync.finishedAt());
    }
}
